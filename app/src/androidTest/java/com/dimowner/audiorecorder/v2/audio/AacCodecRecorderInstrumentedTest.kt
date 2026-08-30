/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dimowner.audiorecorder.audio.AudioDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Proves that [AacCodecRecorderV2] records at the bitrate it is asked for.
 *
 * `MediaRecorder` cannot: `StagefrightRecorder` clips the request to the AAC `maxBitRate` of the
 * device media profile, which is still 96000 on many devices, so [recordsAtRequestedBitRate]
 * fails on the old pipeline and passes on this one.
 *
 * Runs on a device or emulator: `./gradlew connectedDebugConfigDebugAndroidTest`. On MIUI the
 * install needs Developer options -> "Install via USB" and "USB debugging (Security settings)".
 */
@RunWith(AndroidJUnit4::class)
class AacCodecRecorderInstrumentedTest {

    private lateinit var scope: CoroutineScope
    private lateinit var recorder: AacCodecRecorderV2
    private lateinit var outputFile: File
    private lateinit var events: CopyOnWriteArrayList<RecorderEvent>

    private var startedLatch = CountDownLatch(1)
    private var finishedLatch = CountDownLatch(1)

    @Before
    fun setup() {
        assumeTrue("RECORD_AUDIO is not granted", grantMicPermission())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        recorder = AacCodecRecorderV2(scope)
        outputFile = File(context.cacheDir, "codec-recorder-test.m4a").apply {
            delete()
            createNewFile()
        }
        events = CopyOnWriteArrayList()
        startedLatch = CountDownLatch(1)
        finishedLatch = CountDownLatch(1)
        scope.launch {
            recorder.subscribeRecorderEvents().collect { event ->
                events.add(event)
                when (event) {
                    is RecorderEvent.OnStartRecording -> startedLatch.countDown()
                    is RecorderEvent.OnStopRecording, is RecorderEvent.OnMaxDurationReached ->
                        finishedLatch.countDown()
                    else -> Unit
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::recorder.isInitialized && recorder.isRecording) recorder.stopRecording()
        if (::scope.isInitialized) scope.cancel()
        if (::outputFile.isInitialized) outputFile.delete()
    }

    // -------------------------------------------------------------------------
    // The point of the whole exercise
    // -------------------------------------------------------------------------

    @Test
    fun recordsAtRequestedBitRate() {
        startRecording(sampleRate = 48000, channelCount = 2, bitrate = 192_000)
        awaitStart()
        Thread.sleep(RECORDING_MILLS)
        stopAndAwait()

        val measured = measureBitRate(outputFile)
        assertTrue(
            "recorded at $measured bps, still clipped to the media profile cap",
            measured > 130_000
        )
        assertEquals(192_000.0, measured, 192_000 * 0.10)

        val format = trackFormat(outputFile)
        assertEquals(48000, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))

        // The app's own reader has to agree, since that is what ends up in the database.
        val info = AudioDecoder.readRecordInfo(outputFile)
        assertEquals(192_000, info.bitrate)
        assertEquals(RECORDING_MILLS.toDouble(), (info.duration / 1000).toDouble(), 1500.0)
    }

    @Test
    fun clampsBitratesTheFormatCannotCarryInsteadOfFailing() {
        // 8 kHz mono tops out at 6 * 8000 = 48 kbps whatever the encoder is asked for; the
        // recording must still succeed rather than leaving the user without a file.
        startRecording(sampleRate = 8000, channelCount = 1, bitrate = 288_000)
        awaitStart()
        Thread.sleep(RECORDING_MILLS)
        stopAndAwait()

        assertTrue(outputFile.length() > 0)
        val measured = measureBitRate(outputFile)
        assertTrue("recorded at $measured bps, above the AAC-LC ceiling", measured < 60_000)
        assertNoErrors()
    }

    // -------------------------------------------------------------------------
    // Behaviour the service depends on
    // -------------------------------------------------------------------------

    @Test
    fun pauseResumeProducesAGaplessTimeline() {
        startRecording(sampleRate = 44100, channelCount = 1, bitrate = 128_000)
        awaitStart()
        Thread.sleep(2000)
        recorder.pauseRecording()
        Thread.sleep(3000)
        recorder.resumeRecording()
        Thread.sleep(2000)
        stopAndAwait()

        // Only the ~4 s actually captured may appear, not the 3 s spent paused.
        val durationMills = trackFormat(outputFile).getLong(MediaFormat.KEY_DURATION) / 1000
        assertEquals(4000.0, durationMills.toDouble(), 1000.0)

        var previous = -1L
        var maxGap = 0L
        forEachSampleTime(outputFile) { sampleTime ->
            assertTrue("timestamps must increase", sampleTime > previous)
            if (previous >= 0) maxGap = maxOf(maxGap, sampleTime - previous)
            previous = sampleTime
        }
        assertTrue("gap of ${maxGap}us in the encoded timeline", maxGap < 30_000)
    }

    @Test
    fun maxDurationStopsAndTheRecorderIsImmediatelyRestartable() {
        recorder.startRecording(outputFile, 1, 44100, 128_000, 3000, MediaRecorder.AudioSource.MIC)
        awaitStart()
        assertTrue("max duration was not reached", finishedLatch.await(15, TimeUnit.SECONDS))
        assertTrue(events.any { it is RecorderEvent.OnMaxDurationReached })

        // AudioRecordingService starts the next part right here, so nothing may still be held.
        val nextFile = File(outputFile.parentFile, "codec-recorder-test-2.m4a").apply {
            delete()
            createNewFile()
        }
        try {
            assertTrue(
                recorder.startRecording(nextFile, 1, 44100, 128_000, 0, MediaRecorder.AudioSource.MIC)
            )
            Thread.sleep(1000)
            recorder.stopRecording()
            Thread.sleep(1000)
            assertTrue(nextFile.length() > 0)
        } finally {
            nextFile.delete()
        }
    }

    @Test
    fun tagsSurviveMuxerOutput() {
        startRecording(sampleRate = 44100, channelCount = 1, bitrate = 128_000)
        awaitStart()
        Thread.sleep(2000)
        stopAndAwait()

        outputFile.writeTags("Test record", "Audio Recorder")

        assertEquals("Audio Recorder", outputFile.readAuthorName())
        // The file has to stay decodable after the tag writer rewrote the container.
        assertTrue(AudioDecoder.readRecordInfo(outputFile).duration > 0)
    }

    @Test
    fun anInterruptedRecordingIsRestorable() {
        startRecording(sampleRate = 44100, channelCount = 1, bitrate = 128_000)
        awaitStart()
        Thread.sleep(3000)

        // Copy the file mid-recording: that copy has no moov, exactly like a file left behind by
        // a process death.
        val broken = File(outputFile.parentFile, "codec-recorder-broken.m4a")
        outputFile.copyTo(broken, overwrite = true)
        stopAndAwait()
        try {
            RandomAccessFile(broken, "rw").use { it.setLength(broken.length()) }
            val result = BrokenRecordRestorer().restoreFile(broken.absolutePath, 44100, 1, 128_000)
            assertTrue("restore failed: $result", result !is BrokenRecordRestorer.RestoreResult.Failed)
        } finally {
            broken.delete()
            File(broken.parentFile, "codec-recorder-broken_restored.m4a").delete()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun startRecording(sampleRate: Int, channelCount: Int, bitrate: Int) {
        assertTrue(
            "recorder did not start",
            recorder.startRecording(
                outputFile, channelCount, sampleRate, bitrate, 0, MediaRecorder.AudioSource.MIC
            )
        )
    }

    private fun awaitStart() {
        assertTrue("no audio was encoded", startedLatch.await(5, TimeUnit.SECONDS))
    }

    private fun stopAndAwait() {
        recorder.stopRecording()
        assertTrue("recording did not finish", finishedLatch.await(10, TimeUnit.SECONDS))
    }

    private fun assertNoErrors() {
        val errors = events.filterIsInstance<RecorderEvent.OnError>()
        assertTrue("unexpected errors: $errors", errors.isEmpty())
    }

    private fun trackFormat(file: File): MediaFormat {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            assertTrue("no track in the output file", extractor.trackCount > 0)
            return extractor.getTrackFormat(0)
        } finally {
            extractor.release()
        }
    }

    /** Bitrate as an outside observer sees it: bytes on disk over the container duration. */
    private fun measureBitRate(file: File): Double {
        val durationUs = trackFormat(file).getLong(MediaFormat.KEY_DURATION)
        assertTrue("the file has no duration", durationUs > 0)
        return file.length() * 8_000_000.0 / durationUs
    }

    private fun forEachSampleTime(file: File, block: (Long) -> Unit) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(0)
            while (extractor.sampleTime >= 0) {
                block(extractor.sampleTime)
                if (!extractor.advance()) break
            }
        } finally {
            extractor.release()
        }
    }

    private fun grantMicPermission(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.RECORD_AUDIO
            )
        }
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val RECORDING_MILLS = 10_000L
    }
}
