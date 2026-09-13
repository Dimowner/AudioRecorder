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

import android.media.MediaRecorder
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers how [M4aRecorderV2] routes between the codec pipeline and the MediaRecorder fallback.
 *
 * The distinction that matters: a codec pipeline that fails to come up must fall back silently,
 * because `AudioRecordingService` deletes the record and its file when it sees certain errors.
 */
class M4aRecorderV2Test {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var codecRecorder: AacCodecRecorderV2
    private lateinit var mediaRecorder: AudioRecorderV2
    private lateinit var outputFile: File

    private var failedConfig = ""

    @Before
    fun setup() {
        codecRecorder = mockk(relaxed = true)
        mediaRecorder = mockk(relaxed = true)
        every { codecRecorder.subscribeRecorderEvents() } returns emptyFlow()
        every { mediaRecorder.subscribeRecorderEvents() } returns emptyFlow()
        outputFile = folder.newFile("record.m4a")
    }

    private val micInput = AudioInput.Mic(MediaRecorder.AudioSource.DEFAULT)

    private fun CoroutineScope.createRecorder() =
        M4aRecorderV2(codecRecorder, mediaRecorder, this)

    private fun M4aRecorderV2.start(bitrate: Int = 192_000, sampleRate: Int = 48000, channelCount: Int = 2) =
        startRecording(outputFile, channelCount, sampleRate, bitrate, 0, micInput)

    private fun stubCodecStart(result: AacCodecRecorderV2.StartResult) {
        every {
            codecRecorder.startRecordingInternal(any(), any(), any(), any(), any(), any())
        } returns result
    }

    @Test
    fun `a working codec pipeline records without touching MediaRecorder`() = runTest {
        stubCodecStart(AacCodecRecorderV2.StartResult.Started)
        val recorder = createRecorder()

        assertTrue(recorder.start())

        verify(exactly = 0) { mediaRecorder.startRecording(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed codec pipeline falls back without reporting an error`() = runTest {
        stubCodecStart(AacCodecRecorderV2.StartResult.PipelineFailed("codec-configure", null))
        every { mediaRecorder.startRecording(any(), any(), any(), any(), any(), any()) } returns true
        val recorder = createRecorder()
        val events = mutableListOf<RecorderEvent>()
        val collector = launch { recorder.subscribeRecorderEvents().collect { events += it } }

        assertTrue(recorder.start())
        advanceUntilIdle()

        verify(exactly = 1) { mediaRecorder.startRecording(outputFile, 2, 48000, 192_000, 0, micInput) }
        assertTrue("the service would delete the record on an error event", events.isEmpty())
        collector.cancel()
    }

    @Test
    fun `a rejected request is reported and not retried on MediaRecorder`() = runTest {
        stubCodecStart(AacCodecRecorderV2.StartResult.Rejected(InvalidOutputFile()))
        val recorder = createRecorder()
        val events = mutableListOf<RecorderEvent>()
        val collector = launch { recorder.subscribeRecorderEvents().collect { events += it } }

        assertFalse(recorder.start())
        advanceUntilIdle()

        verify(exactly = 0) { mediaRecorder.startRecording(any(), any(), any(), any(), any(), any()) }
        assertEquals(1, events.size)
        assertTrue(events.first() is RecorderEvent.OnError)
        collector.cancel()
    }

    @Test
    fun `another configuration still tries the codec`() = runTest {
        stubCodecStart(AacCodecRecorderV2.StartResult.PipelineFailed("codec-start", null))
        every { mediaRecorder.startRecording(any(), any(), any(), any(), any(), any()) } returns true
        val recorder = createRecorder()
        recorder.start(bitrate = 288_000, sampleRate = 8000, channelCount = 1)

        stubCodecStart(AacCodecRecorderV2.StartResult.Started)
        assertTrue(recorder.start(bitrate = 192_000, sampleRate = 48000, channelCount = 2))

        assertEquals("a successful start clears the remembered failure", "", failedConfig)
        verify(exactly = 2) { codecRecorder.startRecordingInternal(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `transport controls follow the active backend`() = runTest {
        stubCodecStart(AacCodecRecorderV2.StartResult.PipelineFailed("muxer", null))
        every { mediaRecorder.startRecording(any(), any(), any(), any(), any(), any()) } returns true
        every { mediaRecorder.stopRecording() } returns true
        val recorder = createRecorder()
        recorder.start()

        recorder.pauseRecording()
        recorder.resumeRecording()
        assertTrue(recorder.stopRecording())

        verify { mediaRecorder.pauseRecording() }
        verify { mediaRecorder.resumeRecording() }
        verify { mediaRecorder.stopRecording() }
        verify(exactly = 0) { codecRecorder.stopRecording() }
    }
}
