/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.app.info.RecordInfo
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.v2.app.settings.TestARApplication
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.Record
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * Covers [ReplayBufferSaver]: a save becomes a .wav record named by the user's format, inserted
 * into the records table and queued for waveform decoding; every failure leaves no stray file.
 *
 * The snapshot is written by a real [PcmRingBuffer]. Only what needs a device is faked: the
 * MediaExtractor-based [AudioDecoder.readRecordInfo] and starting [DecodeService].
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class ReplayBufferSaverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var recorder: ReplayBufferRecorder
    private lateinit var prefs: PrefsV2
    private lateinit var fileDataSource: FileDataSource
    private lateinit var recordsDataSource: RecordsDataSource
    private lateinit var context: Context
    private lateinit var saver: ReplayBufferSaver
    private lateinit var recordsDir: File
    private lateinit var ring: PcmRingBuffer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        recordsDir = tempFolder.newFolder("records")
        ring = PcmRingBuffer(File(tempFolder.root, "ring.pcm"), ReplayBufferConfig.bufferSizeBytes(5))

        recorder = mockk(relaxed = true)
        every { recorder.bufferedBytes() } answers { ring.bufferedBytes }
        every { recorder.snapshotToWav(any()) } answers { ring.snapshotToWav(firstArg()) }

        prefs = mockk(relaxed = true)
        every { prefs.settingNamingFormat } returns NameFormat.Timestamp
        every { prefs.recordAuthorName } returns "Tester"

        fileDataSource = mockk(relaxed = true)
        every { fileDataSource.getAvailableSpace() } returns 10L * 1024 * 1024 * 1024
        every { fileDataSource.createRecordFile(any()) } answers {
            File(recordsDir, firstArg<String>()).apply { createNewFile() }
        }

        recordsDataSource = mockk(relaxed = true)
        coEvery { recordsDataSource.insertRecord(any()) } returns 42L

        mockkStatic(AudioDecoder::class)
        every { AudioDecoder.readRecordInfo(any()) } answers {
            val file = firstArg<File>()
            val pcmBytes = file.length() - WAV_HEADER_SIZE
            RecordInfo(
                file.nameWithoutExtension, "wav",
                pcmBytes * 1_000_000 / ReplayBufferConfig.BYTES_PER_SECOND, // microseconds
                file.length(), file.absolutePath, file.lastModified(),
                ReplayBufferConfig.SAMPLE_RATE, ReplayBufferConfig.CHANNEL_COUNT, 256_000, false,
            )
        }
        mockkObject(DecodeService.Companion)
        every { DecodeService.startNotificationV2(any(), any(), any(), any()) } just runs

        saver = ReplayBufferSaver(recorder, prefs, fileDataSource, recordsDataSource, context)
    }

    @After
    fun tearDown() {
        ring.close()
        unmockkAll()
    }

    private fun bufferSeconds(seconds: Int) {
        ring.write(ByteArray(seconds * ReplayBufferConfig.BYTES_PER_SECOND) { (it % 251).toByte() })
    }

    private fun recordFiles() = recordsDir.listFiles().orEmpty().toList()

    @Test
    fun `save writes a wav record and queues waveform decoding`() = runBlocking {
        bufferSeconds(3)
        val inserted = slot<Record>()
        coEvery { recordsDataSource.insertRecord(capture(inserted)) } returns 42L

        val result = saver.save()

        assertTrue(result is ReplayBufferSaveResult.Saved)
        result as ReplayBufferSaveResult.Saved
        assertEquals(42L, result.recordId)

        val file = recordFiles().single()
        assertTrue(file.name.endsWith(".wav"))
        verify { fileDataSource.createRecordFile(match { it.endsWith(".wav") }) }
        val record = inserted.captured
        assertEquals(file.absolutePath, record.path)
        assertEquals(file.nameWithoutExtension, record.name)
        assertEquals(result.recordName, record.name)
        assertEquals("wav", record.format)
        assertEquals(16_000, record.sampleRate)
        assertEquals(1, record.channelCount)
        assertEquals(0, record.bitrate) // WAV has no bitrate setting
        assertEquals(3_000L, record.durationMills)
        assertFalse(record.isMovedToRecycle)

        // The saved file holds the buffered audio, oldest first.
        val pcm = file.readBytes().copyOfRange(WAV_HEADER_SIZE, (WAV_HEADER_SIZE + 3 * 32_000))
        assertEquals(3 * 32_000, pcm.size)
        assertEquals(0.toByte(), pcm[0])
        assertEquals((250).toByte(), pcm[250])

        verify { DecodeService.startNotificationV2(context, 42L, file.absolutePath, 3_000L) }
        verify { recorder.setSaving(true) }
        verify { recorder.setSaving(false) }
    }

    @Test
    fun `nothing buffered creates no file`() = runBlocking {
        assertEquals(ReplayBufferSaveResult.NothingBuffered, saver.save())
        verify(exactly = 0) { fileDataSource.createRecordFile(any()) }
        coVerify(exactly = 0) { recordsDataSource.insertRecord(any()) }
    }

    @Test
    fun `not enough space creates no file`() = runBlocking {
        bufferSeconds(2)
        every { fileDataSource.getAvailableSpace() } returns 1_000L
        assertEquals(ReplayBufferSaveResult.NotEnoughSpace, saver.save())
        verify(exactly = 0) { fileDataSource.createRecordFile(any()) }
    }

    @Test
    fun `a record file that can't be created fails cleanly`() = runBlocking {
        bufferSeconds(1)
        every { fileDataSource.createRecordFile(any()) } throws CantCreateFileException()
        assertEquals(ReplayBufferSaveResult.Failed, saver.save())
        coVerify(exactly = 0) { recordsDataSource.insertRecord(any()) }
    }

    @Test
    fun `a failed snapshot deletes the partial file`() = runBlocking {
        bufferSeconds(1)
        every { recorder.snapshotToWav(any()) } throws IOException("buffer closed")
        assertEquals(ReplayBufferSaveResult.Failed, saver.save())
        assertTrue(recordFiles().isEmpty())
        coVerify(exactly = 0) { recordsDataSource.insertRecord(any()) }
        verify { recorder.setSaving(false) }
    }

    @Test
    fun `a failed database insert deletes the file`() = runBlocking {
        bufferSeconds(1)
        coEvery { recordsDataSource.insertRecord(any()) } returns -1L
        assertEquals(ReplayBufferSaveResult.Failed, saver.save())
        assertTrue(recordFiles().isEmpty())
        verify(exactly = 0) { DecodeService.startNotificationV2(any(), any(), any(), any()) }
    }

    @Test
    fun `an unreadable saved file fails and is deleted`() = runBlocking {
        bufferSeconds(1)
        every { AudioDecoder.readRecordInfo(any()) } throws IllegalStateException("bad file")
        assertEquals(ReplayBufferSaveResult.Failed, saver.save())
        assertTrue(recordFiles().isEmpty())
    }

    @Test
    fun `saving twice makes two separate records`() = runBlocking {
        bufferSeconds(1)
        val first = saver.save() as ReplayBufferSaveResult.Saved
        bufferSeconds(1)
        val second = saver.save() as ReplayBufferSaveResult.Saved
        assertEquals(2, recordFiles().size)
        assertTrue(first.recordName != second.recordName || recordFiles().size == 2)
    }
}
