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
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dimowner.audiorecorder.AppConstants.RECORD_SAMPLE_RATE_8000
import com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL
import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.RecorderInitException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@SuppressWarnings("TooManyFunctions")
@Singleton
class ThreeGpRecorderV2 @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val coroutineScope: CoroutineScope,
) : RecorderV2 {

    private var mediaRecorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var updateTime: Long = 0
    private var durationMills: Long = 0

    private var _isRecording: Boolean = false
    private var _isPaused: Boolean = false
    override val isRecording: Boolean
        get() = _isRecording
    override val isPaused: Boolean
        get() = _isPaused

    // Using Handler tied to the main Looper for UI thread synchronization and timing updates
    private val handler = Handler(Looper.getMainLooper())

    private val _event = MutableSharedFlow<RecorderEvent>()
    override fun subscribeRecorderEvents(): Flow<RecorderEvent> {
        return _event
    }

    override fun startRecording(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioSource: Int,
    ): Boolean {
        Timber.d("Start 3GP Recording outputFile: ${outputFile.absolutePath} channelCount: $channelCount" +
                " sampleRate: $sampleRate bitrate: $bitrate maxRecordingDurationMills: $maxRecordingDurationMills" +
                " audioSource: $audioSource")
        if (_isRecording) {
            Timber.e("Recording is already in progress.")
            emitEvent(RecorderEvent.OnError(AlreadyRecordingException()))
            return false
        }
        return if (outputFile.exists() && outputFile.isFile) {
            recordFile = outputFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                MediaRecorder()
            }
            this.mediaRecorder = recorder

            recorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                if (sampleRate > RECORD_SAMPLE_RATE_8000) {
                    // AMR-WB records at 16000 Hz frequency, ~23 kbps bitrate
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                } else {
                    // AMR-NB records at 8000 Hz frequency, ~12 kbps bitrate
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
                setMaxDuration(maxRecordingDurationMills)
                setOnInfoListener { _, what, _ ->
                    handleRecorderInfo(what)
                }
                setOutputFile(outputFile.absolutePath)
            }

            try {
                recorder.prepare()
                recorder.start()
                updateTime = System.currentTimeMillis()
                _isRecording = true
                scheduleRecordingTimeUpdate()
                emitEvent(RecorderEvent.OnStartRecording)
                _isPaused = false
                true
            } catch (e: IOException) {
                Timber.e(e, "prepare() failed")
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
                false
            } catch (e: IllegalStateException) {
                Timber.e(e, "start() failed due to illegal state")
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
                false
            }
        } else {
            emitEvent(RecorderEvent.OnError(InvalidOutputFile()))
            false
        }
    }

    override fun resumeRecording(): Boolean {
        if (!_isRecording || !_isPaused) return false

        return try {
            mediaRecorder?.let { recorder ->
                recorder.resume()
                updateTime = System.currentTimeMillis()
                scheduleRecordingTimeUpdate()
                emitEvent(RecorderEvent.OnResumeRecording)
                _isPaused = false
                true
            } ?: false
        } catch (e: IllegalStateException) {
            Timber.e(e, "resumeRecording() failed")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            false
        }
    }

    override fun pauseRecording(): Boolean {
        if (!_isRecording) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }
        return if (!_isPaused) {
            try {
                mediaRecorder?.let { recorder ->
                    recorder.pause()
                    durationMills += System.currentTimeMillis() - updateTime
                    pauseRecordingTimer()
                    emitEvent(RecorderEvent.OnPauseRecording)
                    _isPaused = true
                    true
                } ?: false
            } catch (e: IllegalStateException) {
                Timber.e(e, "pauseRecording() failed")
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
                false
            }
        } else {
            Timber.e("Recording has already paused")
            false
        }
    }

    override fun stopRecording(): Boolean {
        return stopRecording(skipStopRecordingEventEmit = false)
    }

    private fun stopRecording(skipStopRecordingEventEmit: Boolean): Boolean {
        if (!_isRecording) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }

        stopRecordingTimer()
        val isStopSucceed = try {
            mediaRecorder?.let {
                it.setOnInfoListener(null)
                it.stop()
                true
            } ?: false
        } catch (e: IllegalStateException) {
            // This can happen if start() failed and stop() is called, or if the recorder
            // was never fully prepared/started.
            Timber.e(e, "stopRecording() problems")
            false
        } finally {
            // Always release resources
            mediaRecorder?.release()
            mediaRecorder = null
        }

        if (!skipStopRecordingEventEmit) {
            emitEvent(RecorderEvent.OnStopRecording)
        }

        // Reset all state
        durationMills = 0
        recordFile = null
        _isRecording = false
        _isPaused = false
        return isStopSucceed
    }

    private fun handleRecorderInfo(what: Int) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Timber.d("Max recording duration reached. Stop recording")

            if (stopRecording(skipStopRecordingEventEmit = true)) {
                emitEvent(RecorderEvent.OnMaxDurationReached)
            }
        } else {
            //Do nothing
        }
    }

    private fun emitEvent(event: RecorderEvent) {
        coroutineScope.launch {
            _event.emit(event)
        }
    }

    /**
     * Runnable logic to update recording progress and amplitude.
     */
    private val recordingTimeUpdateRunnable = Runnable {
        if (isRecording && !isPaused) {
            val currentRecorder = mediaRecorder
            if (currentRecorder != null) {
                try {
                    val curTime = System.currentTimeMillis()
                    durationMills += curTime - updateTime
                    updateTime = curTime
                    val amplitude = currentRecorder.maxAmplitude
                    emitEvent(RecorderEvent.OnRecordingProgress(durationMills = durationMills, amplitude = amplitude))
                } catch (e: IllegalStateException) {
                    Timber.e(e, "Error reading amplitude or updating progress")
                }
                scheduleRecordingTimeUpdate()
            }
        }
    }

    private fun scheduleRecordingTimeUpdate() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
        handler.postDelayed(recordingTimeUpdateRunnable, RECORDING_VISUALIZATION_INTERVAL.toLong())
    }

    private fun stopRecordingTimer() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
        updateTime = 0
    }

    private fun pauseRecordingTimer() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
        updateTime = 0
    }
}



