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
import android.os.SystemClock
import com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.RecorderInitException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

/**
 * Abstract base class for [MediaRecorder]-based recorder implementations.
 *
 * Subclasses must implement [configureRecorder] to apply the format/encoder settings
 * specific to their output format, and may override [recordingLogTag] to customise
 * the log prefix used in [startRecording].
 */
@SuppressWarnings("TooManyFunctions")
abstract class MediaRecorderBase(
    private val applicationContext: Context,
    private val coroutineScope: CoroutineScope,
) : RecorderV2 {

    private var timerProgress: Timer? = null
    private val amplitudesBuffer: IntArrayList = IntArrayList()
    @Volatile private var lastNonZeroAmplitude: Int = 0
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
    override fun subscribeRecorderEvents(): Flow<RecorderEvent> = _event

    /**
     * A short label used in the "Start Recording" log line so subclasses can differentiate
     * their log output (e.g. "3GP ").
     */
    protected open val recordingLogTag: String = ""

    /**
     * Called inside [startRecording] after the [MediaRecorder] instance is created and
     * [MediaRecorder.setAudioSource] has been applied.
     * Subclasses should set the output format, audio encoder, and any format-specific
     * parameters (channels, sample rate, bitrate…) here.
     */
    protected abstract fun configureRecorder(
        recorder: MediaRecorder,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
    )

    override fun startRecording(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioSource: Int,
    ): Boolean {
        Timber.d(
            "Start ${recordingLogTag}Recording outputFile: ${outputFile.absolutePath}" +
                " channelCount: $channelCount sampleRate: $sampleRate bitrate: $bitrate" +
                " maxRecordingDurationMills: $maxRecordingDurationMills audioSource: $audioSource"
        )
        if (_isRecording) {
            Timber.e("Recording is already in progress.")
            emitEvent(RecorderEvent.OnError(AlreadyRecordingException()))
            return false
        }
        amplitudesBuffer.clear()
        lastNonZeroAmplitude = 0
        return if (outputFile.exists() && outputFile.isFile) {
            recordFile = outputFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            this.mediaRecorder = recorder

            recorder.apply {
                setAudioSource(audioSource)
                configureRecorder(this, channelCount, sampleRate, bitrate)
                setMaxDuration(maxRecordingDurationMills)
                setOnInfoListener { _, what, _ -> handleRecorderInfo(what) }
                setOutputFile(outputFile.absolutePath)
            }

            try {
                recorder.prepare()
                recorder.start()
                scheduleRecordingTimeUpdate()
                scheduleRecordingTimeUpdateBuffered()
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
                updateTime = SystemClock.elapsedRealtime()
                scheduleRecordingTimeUpdate()
                scheduleRecordingTimeUpdateBuffered()
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
                    durationMills += SystemClock.elapsedRealtime() - updateTime
                    pauseRecordingTimer()
                    pauseRecordingTimerBuffered()
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
        stopRecordingTimerBuffered()
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
        amplitudesBuffer.clear()
        return isStopSucceed
    }

    private fun handleRecorderInfo(what: Int) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Timber.d("Max recording duration reached. Stop recording")
            if (stopRecording(skipStopRecordingEventEmit = true)) {
                emitEvent(RecorderEvent.OnMaxDurationReached)
            }
        }
    }

    protected fun emitEvent(event: RecorderEvent) {
        coroutineScope.launch {
            _event.emit(event)
        }
    }

    /**
     * Runnable that fires every [RECORDING_VISUALIZATION_INTERVAL_NEW] ms to push
     * duration + amplitude progress events.
     */
    private val recordingTimeUpdateRunnable = Runnable {
        val currentRecorder = mediaRecorder
        if (currentRecorder != null) {
            if (!isRecording) {
                //Set that recording is started only after receiving a valid amplitude value,
                //which indicates that recording has actually started.
                val amplitude = currentRecorder.maxAmplitude
                if (amplitude > 0) {
                    _isRecording = true
                    updateTime = SystemClock.elapsedRealtime()
                    amplitudesBuffer.add(amplitude)
                }
            } else if (isRecording && !isPaused) {
                try {
                    val amplitude = currentRecorder.maxAmplitude
                    amplitudesBuffer.add(amplitude)
                } catch (e: IllegalStateException) {
                    Timber.e(e, "Error reading amplitude or updating progress")
                }
            }
            scheduleRecordingTimeUpdate()
        }
    }

    private fun scheduleRecordingTimeUpdate() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
        handler.postDelayed(recordingTimeUpdateRunnable, (RECORDING_VISUALIZATION_INTERVAL_NEW/1.5).toLong())
    }

    private fun stopRecordingTimer() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
    }

    private fun pauseRecordingTimer() {
        handler.removeCallbacks(recordingTimeUpdateRunnable)
    }

    private fun scheduleRecordingTimeUpdateBuffered() {
        timerProgress = Timer()
        timerProgress?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    readBufferedProgress()
                } catch (e: java.lang.IllegalStateException) {
                    Timber.e(e)
                }
            }
        }, 1, RECORDING_VISUALIZATION_INTERVAL_NEW.toLong())
    }

    private fun stopRecordingTimerBuffered() {
        timerProgress?.cancel()
        timerProgress?.purge()
        updateTime = 0
    }

    private fun pauseRecordingTimerBuffered() {
        timerProgress?.cancel()
        timerProgress?.purge()
        updateTime = 0
    }

    private fun readBufferedProgress() {
        // Timer.cancel() doesn't prevent an already-scheduled task from running; skip stale
        // fires so a late progress event can't flip state back to RECORDING after stop.
        if (!_isRecording || _isPaused) return
        if (amplitudesBuffer.size() > 0) {
            val curTime = SystemClock.elapsedRealtime()
            durationMills += curTime - updateTime
            updateTime = curTime
            var amp = amplitudesBuffer.get(amplitudesBuffer.size() - 1)
            if (amp == 0) amp = lastNonZeroAmplitude
            else lastNonZeroAmplitude = amp
            amplitudesBuffer.clear()
            emitEvent(RecorderEvent.OnRecordingProgress(durationMills = durationMills, amplitude = amp))
        }
    }
}

