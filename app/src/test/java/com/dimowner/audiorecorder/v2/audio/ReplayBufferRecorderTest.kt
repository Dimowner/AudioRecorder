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

import android.media.MediaRecorder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.v2.app.settings.TestARApplication
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
import org.robolectric.shadows.ShadowAudioRecord
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the real [ReplayBufferRecorder] - its AudioRecord, capture thread and ring buffer - with
 * a fake microphone ([ShadowAudioRecord] source) that produces a byte counter, so the order and
 * continuity of what ends up in a saved WAV can be checked.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class ReplayBufferRecorderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Next byte the fake microphone will produce; every byte is the previous one plus 1. */
    private val micCounter = AtomicInteger(0)
    private val micReads = AtomicInteger(0)

    private lateinit var recorder: ReplayBufferRecorder
    private lateinit var bufferFile: File

    @Before
    fun setUp() {
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInByteArray(
                audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int, isBlocking: Boolean,
            ): Int {
                // ~100 ms of audio per read at 10x real time, like a fast but paced microphone.
                Thread.sleep(10)
                for (i in 0 until sizeInBytes) {
                    audioData[offsetInBytes + i] = micCounter.getAndIncrement().toByte()
                }
                micReads.incrementAndGet()
                return sizeInBytes
            }
        })
        recorder = ReplayBufferRecorder()
        bufferFile = File(tempFolder.root, "replay/replay_buffer.pcm")
    }

    @After
    fun tearDown() {
        recorder.stop()
        ShadowAudioRecord.clearSource()
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting")
            Thread.sleep(5)
        }
    }

    private fun start(minutes: Int = 5) =
        recorder.start(bufferFile, minutes, MediaRecorder.AudioSource.MIC)

    /** Saves a snapshot and returns its PCM bytes. */
    private fun save(): ByteArray {
        val out = tempFolder.newFile()
        val dataLen = recorder.snapshotToWav(out)
        val bytes = out.readBytes()
        assertEquals(WAV_HEADER_SIZE + dataLen, bytes.size.toLong())
        return bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size)
    }

    private fun assertContinuous(pcm: ByteArray) {
        for (i in 1 until pcm.size) {
            assertEquals("discontinuity at byte $i", (pcm[i - 1] + 1).toByte(), pcm[i])
        }
    }

    @Test
    fun `start captures the microphone into the buffer`() {
        assertTrue(start())
        val state = recorder.state.value
        assertTrue(state.isRunning)
        assertTrue(state.isCapturing)
        assertFalse(state.isPausedForRecording)
        assertEquals(5, state.durationMinutes)
        assertTrue(bufferFile.exists())

        waitUntil { recorder.bufferedBytes() >= 3 * 3200 }
        val pcm = save()
        assertTrue(pcm.size >= 3 * 3200)
        assertEquals(0, pcm.size % ReplayBufferConfig.FRAME_SIZE)
        assertEquals(0.toByte(), pcm[0]) // oldest first: nothing has wrapped yet
        assertContinuous(pcm)
    }

    @Test
    fun `saving does not stop capture`() {
        assertTrue(start())
        waitUntil { recorder.bufferedBytes() > 0 }
        save()
        val afterSave = recorder.bufferedBytes()
        waitUntil { recorder.bufferedBytes() > afterSave }
        assertTrue(recorder.state.value.isCapturing)
    }

    @Test
    fun `pausing for a recording releases the mic and keeps the audio`() {
        assertTrue(start())
        waitUntil { recorder.bufferedBytes() >= 2 * 3200 }

        recorder.pauseForRecording()
        val state = recorder.state.value
        assertTrue(state.isRunning)
        assertTrue(state.isPausedForRecording)
        assertFalse(state.isCapturing)

        val held = recorder.bufferedBytes()
        val reads = micReads.get()
        Thread.sleep(150)
        assertEquals("buffer grew while paused", held, recorder.bufferedBytes())
        assertEquals("mic was read while paused", reads, micReads.get())
        assertEquals(held, save().size.toLong())
    }

    @Test
    fun `resuming after a recording appends to the audio kept before it`() {
        assertTrue(start())
        waitUntil { recorder.bufferedBytes() >= 2 * 3200 }
        recorder.pauseForRecording()
        val held = recorder.bufferedBytes()

        recorder.resumeAfterRecording()
        assertTrue(recorder.state.value.isCapturing)
        assertFalse(recorder.state.value.isPausedForRecording)
        waitUntil { recorder.bufferedBytes() >= held + 2 * 3200 }

        val pcm = save()
        // The fake mic's counter does not advance while paused, so pre- and post-recording
        // audio join seamlessly: one continuous run proves nothing was lost or reordered.
        assertEquals(0.toByte(), pcm[0])
        assertContinuous(pcm)
    }

    @Test
    fun `a buffer enabled during a recording starts paused`() {
        recorder.pauseForRecording()
        assertTrue(start())
        assertTrue(recorder.state.value.isRunning)
        assertTrue(recorder.state.value.isPausedForRecording)
        assertFalse(recorder.state.value.isCapturing)
        Thread.sleep(50)
        assertEquals(0, micReads.get())

        recorder.resumeAfterRecording()
        waitUntil { recorder.bufferedBytes() > 0 }
    }

    @Test
    fun `stop deletes the buffered audio`() {
        assertTrue(start())
        waitUntil { recorder.bufferedBytes() > 0 }

        recorder.stop()
        assertFalse(recorder.state.value.isRunning)
        assertFalse(bufferFile.exists())
        assertEquals(0L, recorder.bufferedBytes())
        assertEquals(0L, recorder.snapshotToWav(tempFolder.newFile()))
    }

    @Test
    fun `restarting with a new length starts from an empty buffer`() {
        assertTrue(start(minutes = 5))
        waitUntil { recorder.bufferedBytes() >= 2 * 3200 }

        assertTrue(start(minutes = 10))
        assertEquals(10, recorder.state.value.durationMinutes)
        val pcm = save()
        assertTrue(pcm.size < 2 * 3200 || pcm[0] != 0.toByte())
    }

    @Test
    fun `resume without a running buffer only clears the hold`() {
        recorder.pauseForRecording()
        recorder.resumeAfterRecording()
        val state = recorder.state.value
        assertFalse(state.isRunning)
        assertFalse(state.isPausedForRecording)
        assertFalse(state.isCapturing)
    }
}
