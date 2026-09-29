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

import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.RecorderInitException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The m4a recorder: [AacCodecRecorderV2] with [AudioRecorderV2] as a fallback.
 *
 * The codec pipeline is preferred because `MediaRecorder` silently clips the bitrate to the device
 * media profile (96 kbps on many devices), but it is the newer of the two paths, so any device
 * where it cannot start still records through `MediaRecorder`.
 *
 * This is a wrapper rather than a branch inside `AudioRecordingService` because the service
 * resolves its recorder once per start command and binds its event subscription to that instance.
 * Owning the event flow here lets the backend change without the service noticing.
 */
@Singleton
class M4aRecorderV2 @Inject constructor(
    private val codecRecorder: AacCodecRecorderV2,
    private val mediaRecorder: AudioRecorderV2,
    private val coroutineScope: CoroutineScope,
) : RecorderV2 {

    private data class StartParams(
        val outputFile: File,
        val channelCount: Int,
        val sampleRate: Int,
        val bitrate: Int,
        val maxRecordingDurationMills: Int,
        val audioInput: AudioInput,
    )

    @Volatile private var active: RecorderV2? = null
    @Volatile private var lastParams: StartParams? = null

    private val _event = MutableSharedFlow<RecorderEvent>()
    override fun subscribeRecorderEvents(): Flow<RecorderEvent> = _event

    init {
        // Both children are process-lifetime singletons, so relaying permanently is cheaper than
        // re-subscribing per recording - and it cannot miss the first event, which subscribing
        // right before a start could, since the children emit through their own coroutines.
        relay(codecRecorder)
        relay(mediaRecorder)
        codecRecorder.startFailureListener = { reason -> onAsyncStartFailure(reason) }
    }

    private fun relay(recorder: RecorderV2) {
        coroutineScope.launch {
            recorder.subscribeRecorderEvents().collect { event ->
                if (active === recorder) _event.emit(event)
            }
        }
    }

    override fun startRecording(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioInput: AudioInput,
    ): Boolean {
        val params = StartParams(
            outputFile, channelCount, sampleRate, bitrate, maxRecordingDurationMills, audioInput
        )
        lastParams = params
        active = codecRecorder
        return when (
            val result = codecRecorder.startRecordingInternal(
                outputFile, channelCount, sampleRate, bitrate, maxRecordingDurationMills, audioInput
            )
        ) {
            is AacCodecRecorderV2.StartResult.Started -> {
                true
            }
            // The request itself is not satisfiable - the microphone is busy, the file is
            // unusable - so MediaRecorder would fail the same way. Report it.
            is AacCodecRecorderV2.StartResult.Rejected -> {
                emitEvent(RecorderEvent.OnError(result.exception))
                false
            }
            is AacCodecRecorderV2.StartResult.PipelineFailed -> {
                Timber.w("MediaCodec pipeline failed at ${result.stage}, falling back to MediaRecorder")
                startWithMediaRecorder(params)
            }
        }
    }

    /**
     * Falls back to the `MediaRecorder` backend, unless the recording captures system playback -
     * `MediaRecorder` has no playback-capture equivalent, so falling back there would record the
     * microphone instead of what the user asked for. In that case the failure is surfaced.
     */
    private fun startWithMediaRecorder(params: StartParams): Boolean {
        if (params.audioInput !is AudioInput.Mic) {
            Timber.e("No MediaRecorder fallback for ${params.audioInput}; reporting the failure")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }
        active = mediaRecorder
        if (!resetOutputFile(params.outputFile)) {
            emitEvent(RecorderEvent.OnError(CantCreateFileException()))
            return false
        }
        return mediaRecorder.startRecording(
            params.outputFile,
            params.channelCount,
            params.sampleRate,
            params.bitrate,
            params.maxRecordingDurationMills,
            params.audioInput,
        )
    }

    /**
     * Gives MediaRecorder an empty file to append to: the MediaMuxer constructor truncates the
     * path and may already have written an ftyp box, and MediaRecorder requires the file to exist.
     */
    private fun resetOutputFile(outputFile: File): Boolean {
        return try {
            outputFile.delete()
            outputFile.createNewFile()
        } catch (e: IOException) {
            Timber.e(e, "Failed to reset the output file before falling back")
            false
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to reset the output file before falling back")
            false
        }
    }

    /**
     * A codec pipeline that started but produced nothing. No events reached the service, so the
     * same record can still be recorded by MediaRecorder.
     */
    private fun onAsyncStartFailure(reason: String) {
        val params = lastParams ?: return
        if (active !== codecRecorder) return
        coroutineScope.launch {
            Timber.w("MediaCodec pipeline produced no audio ($reason), falling back to MediaRecorder")
            startWithMediaRecorder(params)
        }
    }

    override fun resumeRecording(): Boolean = active?.resumeRecording() ?: false

    override fun pauseRecording(): Boolean = active?.pauseRecording() ?: false

    // `active` deliberately stays set after a stop so the events the backend emits while
    // finalising the file are still relayed. The next start reassigns it.
    override fun stopRecording(): Boolean = active?.stopRecording() ?: false

    override val isRecording: Boolean
        get() = active?.isRecording ?: false

    override val isPaused: Boolean
        get() = active?.isPaused ?: false

    private fun emitEvent(event: RecorderEvent) {
        coroutineScope.launch {
            _event.emit(event)
        }
    }
}
