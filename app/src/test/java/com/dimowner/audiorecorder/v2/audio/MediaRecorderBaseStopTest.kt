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

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.exception.RecordingException
import com.dimowner.audiorecorder.exception.RecordingStopFailedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

/**
 * Covers how [MediaRecorderBase] ends a recording it did not end itself: a failing
 * [MediaRecorder.stop], and the two callbacks the platform can raise mid-session.
 *
 * The platform reports a writer that could not finalise the output file as a plain
 * `RuntimeException("stop failed.")`. The call is reached from the main thread behind the stop
 * button, so it neither runs there - it is handed to [MediaRecorderBase.stopDispatcher] - nor
 * lets the failure escape: the outcome comes back as an event.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = MediaRecorderTestApplication::class, sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRecorderBaseStopTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var outputFile: File

    /** The listeners [MediaRecorderBase] installs, so the tests can raise the callbacks. */
    private var infoListener: MediaRecorder.OnInfoListener? = null
    private var errorListener: MediaRecorder.OnErrorListener? = null

    /** Minimal concrete recorder: the format setup has no bearing on how a stop failure behaves. */
    private class TestRecorder(
        context: Context,
        scope: CoroutineScope,
        stopDispatcher: CoroutineDispatcher,
    ) : MediaRecorderBase(context, scope, stopDispatcher) {
        override fun configureRecorder(
            recorder: MediaRecorder,
            channelCount: Int,
            sampleRate: Int,
            bitrate: Int,
        ) = Unit
    }

    @Before
    fun setup() {
        outputFile = folder.newFile("record.m4a")
        mockkConstructor(MediaRecorder::class)
        every { anyConstructed<MediaRecorder>().setAudioSource(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setOutputFormat(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setAudioEncoder(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setMaxDuration(any()) } returns Unit
        every { anyConstructed<MediaRecorder>().setOutputFile(any<String>()) } returns Unit
        // Captured rather than ignored: finishStop() clears both listeners, so the argument is
        // nullable and the last value seen is what the recorder is actually running with.
        every { anyConstructed<MediaRecorder>().setOnInfoListener(any()) } answers {
            infoListener = firstArg<MediaRecorder.OnInfoListener?>()
        }
        every { anyConstructed<MediaRecorder>().setOnErrorListener(any()) } answers {
            errorListener = firstArg<MediaRecorder.OnErrorListener?>()
        }
        every { anyConstructed<MediaRecorder>().prepare() } returns Unit
        every { anyConstructed<MediaRecorder>().start() } returns Unit
        every { anyConstructed<MediaRecorder>().release() } returns Unit
        // Amplitudes stay at 0, so the recorder never leaves its "starting" phase and no
        // progress events dilute what the tests assert on.
        every { anyConstructed<MediaRecorder>().maxAmplitude } returns 0
    }

    @After
    fun tearDown() {
        infoListener = null
        errorListener = null
        unmockkConstructor(MediaRecorder::class)
    }

    @Test
    fun `stop failure is reported instead of crashing the caller`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } throws RuntimeException("stop failed.")
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val events = collectEvents(recorder)

        assertTrue(startRecording(recorder))
        // The stop is accepted right away; whether the container closed is reported as an event,
        // because the caller is no longer around when the blocking stop() finishes.
        assertTrue(recorder.stopRecording())
        advanceUntilIdle()

        assertEquals(
            listOf(RecorderEvent.OnStartRecording::class, RecorderEvent.OnError::class),
            events.map { it::class },
        )
        val error = events.last() as RecorderEvent.OnError
        assertTrue(error.exception is RecordingStopFailedException)
    }

    @Test
    fun `a clean stop still reports a normal stop`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val events = collectEvents(recorder)

        assertTrue(startRecording(recorder))
        assertTrue(recorder.stopRecording())
        advanceUntilIdle()

        assertEquals(
            listOf(RecorderEvent.OnStartRecording, RecorderEvent.OnStopRecording),
            events,
        )
    }

    /**
     * A second stop after a failed one must stay silent: the recorder is already released, and a
     * repeated failure would send the service after a record it has already dealt with.
     */
    @Test
    fun `stopping again after a failure reports nothing`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } throws RuntimeException("stop failed.")
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )

        assertTrue(startRecording(recorder))
        recorder.stopRecording()
        advanceUntilIdle()

        val events = collectEvents(recorder)
        assertFalse(recorder.stopRecording())
        advanceUntilIdle()

        assertTrue(events.isEmpty())
    }

    /**
     * The ANR this guards against: MediaRecorder.stop() finalises the container through the media
     * server and blocks for as long as that takes, and the stop button reaches stopRecording() on
     * the main thread. Nothing native may run before stopRecording() has returned.
     */
    @Test
    fun `stop button does not run the blocking stop on the caller's thread`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        // Standard, not unconfined: the teardown is queued rather than run eagerly, so what the
        // caller sees on return is exactly what the main thread would see.
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            StandardTestDispatcher(testScheduler),
        )

        assertTrue(startRecording(recorder))
        assertTrue(recorder.stopRecording())

        verify(exactly = 0) { anyConstructed<MediaRecorder>().stop() }
        // The recorder still reports itself as stopped, which is what the caller acts on.
        assertFalse(recorder.isRecording)

        advanceUntilIdle()
        verify(exactly = 1) { anyConstructed<MediaRecorder>().stop() }
    }

    /**
     * A media server death or an encoder failure leaves the recorder silently dead: no more
     * audio, an unfinalised file, and a service that still believes it is recording. The
     * failure has to come back as one event, and only once the container has been closed - the
     * service saves the captured audio on the strength of it.
     */
    @Test
    fun `a runtime failure closes the container and reports a recording error`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val events = collectEvents(recorder)

        assertTrue(startRecording(recorder))
        errorListener!!.onError(mockk(), MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0)
        advanceUntilIdle()

        verify(exactly = 1) { anyConstructed<MediaRecorder>().stop() }
        assertEquals(
            listOf(RecorderEvent.OnStartRecording::class, RecorderEvent.OnError::class),
            events.map { it::class },
        )
        val error = events.last() as RecorderEvent.OnError
        assertTrue(error.exception is RecordingException)
        assertFalse(recorder.isRecording)
    }

    /**
     * When the stop that follows the failure cannot close the container either, the unfinalised
     * file is what the user is left with - and only [RecordingStopFailedException] sends the
     * service after it, so that has to win over the error the failure would have reported.
     */
    @Test
    fun `a runtime failure whose stop also fails reports the stop failure`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } throws RuntimeException("stop failed.")
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val events = collectEvents(recorder)

        assertTrue(startRecording(recorder))
        errorListener!!.onError(mockk(), MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0)
        advanceUntilIdle()

        val error = events.last() as RecorderEvent.OnError
        assertTrue(error.exception is RecordingStopFailedException)
    }

    /** A failure arriving after the recording was already torn down has nothing left to report. */
    @Test
    fun `a runtime failure after the recorder was released reports nothing`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val listener = run {
            assertTrue(startRecording(recorder))
            errorListener!!
        }
        recorder.stopRecording()
        advanceUntilIdle()

        val events = collectEvents(recorder)
        listener.onError(mockk(), MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        verify(exactly = 1) { anyConstructed<MediaRecorder>().stop() }
    }

    /**
     * The MPEG-4 writer stops on its own once the output outgrows its 32-bit offsets, without
     * any file size limit having been set. Left unhandled the recorder is dead while the service
     * keeps counting, so it is treated as a max-duration hit and the session rolls over into the
     * next part.
     */
    @Test
    fun `the writer's own file size limit ends the recording like a max duration hit`() = runTest {
        every { anyConstructed<MediaRecorder>().stop() } returns Unit
        val recorder = TestRecorder(
            ApplicationProvider.getApplicationContext(),
            this,
            UnconfinedTestDispatcher(testScheduler),
        )
        val events = collectEvents(recorder)

        assertTrue(startRecording(recorder))
        infoListener!!.onInfo(mockk(), MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED, 0)
        advanceUntilIdle()

        assertEquals(
            listOf(RecorderEvent.OnStartRecording, RecorderEvent.OnMaxDurationReached),
            events,
        )
    }

    private fun startRecording(recorder: RecorderV2): Boolean = recorder.startRecording(
        outputFile = outputFile,
        channelCount = 1,
        sampleRate = 44100,
        bitrate = 128000,
        maxRecordingDurationMills = 0,
        audioInput = AudioInput.Mic(MediaRecorder.AudioSource.MIC),
    )

    private fun TestScope.collectEvents(recorder: RecorderV2): List<RecorderEvent> {
        val events = mutableListOf<RecorderEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.subscribeRecorderEvents().collect { events.add(it) }
        }
        return events
    }
}

class MediaRecorderTestApplication : Application() {
    override fun onTerminate() {
        // Do nothing - avoid calling Injector.closeTasks() in tests
    }
}
