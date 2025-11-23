package com.dimowner.audiorecorder.v2.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
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

@Singleton
class AudioRecorderV2 @Inject constructor(
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

    override fun startRecording(outputFile: File, channelCount: Int, sampleRate: Int, bitrate: Int): Boolean {
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
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(channelCount)
                setAudioSamplingRate(sampleRate)
                setAudioEncodingBitRate(bitrate)
                setMaxDuration(-1) // Unlimited duration
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
        if (!_isRecording) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }

        stopRecordingTimer()
        val isStopSucceed = try {
            mediaRecorder?.let {
                it.stop()
                true
            }?: false
        } catch (e: IllegalStateException) {
            // This can happen if start() failed and stop() is called, or if the recorder
            // was never fully prepared/started.
            Timber.e(e, "stopRecording() problems")
            false
        } finally {
            // Always release resources
            mediaRecorder?.release()
        }

        emitEvent(RecorderEvent.OnStopRecording)

        // Reset all state
        durationMills = 0
        recordFile = null
        _isRecording = false
        _isPaused = false
        mediaRecorder = null
        return isStopSucceed
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
        // Remove any pending messages before scheduling a new one
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
