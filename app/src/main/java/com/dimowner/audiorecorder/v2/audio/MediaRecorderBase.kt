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
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.RecorderInitException
import com.dimowner.audiorecorder.exception.RecordingException
import com.dimowner.audiorecorder.exception.RecordingStopFailedException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

/** Passed to [MediaRecorder.setMaxDuration] to record without a platform side duration limit. */
private const val NO_MAX_DURATION = -1

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
    /** Where the blocking [MediaRecorder.stop] runs, see [stopRecording]. */
    private val stopDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecorderV2 {

    private var timerProgress: Timer? = null
    private val amplitudesBuffer: IntArrayList = IntArrayList()
    @Volatile private var lastNonZeroAmplitude: Int = 0

    // Written from the caller's background thread (start/stop) and read from the sampling
    // thread by recordingTimeUpdateRunnable, so all of them have to be volatile.
    @Volatile private var mediaRecorder: MediaRecorder? = null
    private var recordFile: File? = null

    // updateTime is written by the sampling thread and read by the timerProgress thread;
    // durationMills is written by timerProgress and read on pause/stop. Volatile publishes
    // those writes - it does not make `durationMills +=` atomic, which is fine because the
    // increment only ever runs on the timerProgress thread.
    @Volatile private var updateTime: Long = 0
    @Volatile private var durationMills: Long = 0

    // The maximum duration is enforced here rather than by MediaRecorder, see startRecording().
    @Volatile private var maxDurationMills: Int = 0

    @Volatile private var _isRecording: Boolean = false
    @Volatile private var _isPaused: Boolean = false
    override val isRecording: Boolean
        get() = _isRecording
    override val isPaused: Boolean
        get() = _isPaused

    /**
     * Dedicated thread the amplitude ticks run on, alive only for the duration of a recording.
     *
     * These ticks used to be posted to the main looper, which coupled amplitude sampling to UI
     * load. A janky frame delayed the tick, [readBufferedProgress] then found an empty
     * [amplitudesBuffer] and left [durationMills] frozen until the next successful read, and the
     * recording service back-fills such a gap by repeating a single amplitude value - which
     * draws as one wide flat block in the waveform. Sampling at audio priority off the main
     * thread keeps the interval steady regardless of what the UI is doing.
     */
    @Volatile private var samplingThread: HandlerThread? = null
    @Volatile private var handler: Handler? = null

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
        audioInput: AudioInput,
    ): Boolean {
        Timber.d(
            "Start ${recordingLogTag}Recording outputFile: ${outputFile.absolutePath}" +
                " channelCount: $channelCount sampleRate: $sampleRate bitrate: $bitrate" +
                " maxRecordingDurationMills: $maxRecordingDurationMills audioInput: $audioInput"
        )
        // System playback capture is configured with an AudioPlaybackCaptureConfiguration, which
        // only AudioRecord.Builder accepts - MediaRecorder has no equivalent. Refusing here beats
        // recording the microphone under a name the user did not ask for. Callers keep this from
        // happening by choosing an AudioRecord-backed recorder for that source.
        val micInput = audioInput as? AudioInput.Mic
        if (micInput == null) {
            Timber.e("MediaRecorder cannot capture system audio playback")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }
        // _isRecording only flips to true once the first valid amplitude arrives, so it is still
        // false while the recorder is starting up. Checking the recorder instance as well closes
        // that window: without it a second start would overwrite (and then release) a live
        // MediaRecorder that the amplitude ticks are still reading from.
        if (_isRecording || mediaRecorder != null) {
            Timber.e("Recording is already in progress.")
            emitEvent(RecorderEvent.OnError(AlreadyRecordingException()))
            return false
        }
        amplitudesBuffer.clear()
        lastNonZeroAmplitude = 0
        maxDurationMills = maxRecordingDurationMills
        return if (outputFile.exists() && outputFile.isFile) {
            recordFile = outputFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            this.mediaRecorder = recorder

            try {
                recorder.apply {
                    setAudioSource(micInput.audioSource)
                    configureRecorder(this, channelCount, sampleRate, bitrate)
                    // MPEG4Writer sizes the moov box it reserves up front from the duration
                    // limit, and with a limit of hours that reservation maxes out at ~405 KB of
                    // "free" padding that stays in every finished file - a few seconds of audio
                    // then weighs several hundred KB. Recording without a platform limit keeps
                    // the reservation at its 3 KB minimum; the limit is enforced in
                    // readBufferedProgress() instead, the same way WavRecorderV2 does it.
                    setMaxDuration(NO_MAX_DURATION)
                    setOnInfoListener { _, what, _ -> handleRecorderInfo(what) }
                    setOnErrorListener { _, what, extra -> handleRecorderError(what, extra) }
                    setOutputFile(outputFile.absolutePath)
                }
                recorder.prepare()
                recorder.start()
                _isPaused = false
                updateTime = SystemClock.elapsedRealtime()
                startSamplingThread()
                scheduleRecordingTimeUpdate()
                scheduleRecordingTimeUpdateBuffered()
                emitEvent(RecorderEvent.OnStartRecording)
                true
            } catch (e: IOException) {
                Timber.e(e, "prepare() failed")
                releaseRecorder()
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
                false
            } catch (e: IllegalStateException) {
                Timber.e(e, "Recorder setup or start() failed due to illegal state")
                releaseRecorder()
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
                false
            } catch (e: RuntimeException) {
                // MediaRecorder.start() throws a plain RuntimeException (not a subclass) when
                // the hardware source is unavailable or the codec rejects the configuration.
                Timber.e(e, "start() failed")
                releaseRecorder()
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
                _isPaused = false
                startSamplingThread()
                scheduleRecordingTimeUpdate()
                scheduleRecordingTimeUpdateBuffered()
                emitEvent(RecorderEvent.OnResumeRecording)
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
                    _isPaused = true
                    pauseRecordingTimer()
                    pauseRecordingTimerBuffered()
                    emitEvent(RecorderEvent.OnPauseRecording)
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
        return stopRecording(RecorderEvent.OnStopRecording)
    }

    /**
     * Ends the recording. Returns as soon as the state has been reset - the container is
     * finalised on a background thread, see [finishStop].
     *
     * @param completionEvent emitted once the container has been closed successfully. A failed
     * stop reports [RecordingStopFailedException] instead, whatever the caller asked for.
     */
    private fun stopRecording(completionEvent: RecorderEvent): Boolean {
        // A recorder that started but hasn't reported an amplitude yet still has _isRecording
        // false, and it must be released here - otherwise it would keep holding the microphone
        // and block every subsequent startRecording().
        val recorder = mediaRecorder
        if (!_isRecording && recorder == null) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }
        // Hand the instance to the teardown below right away, so a second stop (the notification
        // action racing the stop button, or a max-duration tick landing on top of either) finds
        // no recorder and cannot start a second teardown of the same one.
        mediaRecorder = null

        stopRecordingTimer()
        stopRecordingTimerBuffered()
        stopSamplingThread()

        // Reset all state
        durationMills = 0
        maxDurationMills = 0
        recordFile = null
        _isRecording = false
        _isPaused = false
        synchronized(amplitudesBuffer) { amplitudesBuffer.clear() }

        if (recorder == null) {
            // _isRecording is flipped by the sampling thread, which can land just after the
            // instance was released: there is nothing left to finalise and nothing to report.
            return false
        }

        // MediaRecorder.stop() finalises the container through the media server and does not
        // return until that is done, which is seconds rather than milliseconds when the writer
        // has a lot to flush or the media server is busy. This method is reached from the main
        // thread (the stop button and the notification action), so the blocking part runs on the
        // recorder scope instead; everything the caller observes has already been reset above.
        coroutineScope.launch(stopDispatcher) { finishStop(recorder, completionEvent) }
        return true
    }

    /** Closes the container and reports the outcome. Always runs off the caller's thread. */
    private fun finishStop(recorder: MediaRecorder, completionEvent: RecorderEvent) {
        var stopFailure: RuntimeException? = null
        try {
            recorder.setOnInfoListener(null)
            // stop() itself can trip the error callback (a media server that dies while the
            // container is being finalised). The outcome is already reported from here, so let
            // that callback find no listener rather than start a second teardown.
            recorder.setOnErrorListener(null)
            recorder.stop()
        } catch (e: RuntimeException) {
            // stop() reports everything as a RuntimeException: IllegalStateException when the
            // recorder was never fully prepared/started, and a plain RuntimeException("stop
            // failed.") when the writer could not finalise the container - a recording stopped
            // before a single frame was muxed, or a media server hiccup. Catching only the
            // subclass let the latter reach the caller, and stopRecording() used to run on the
            // main thread behind the stop button, so it crashed the app.
            Timber.e(e, "stopRecording() problems")
            stopFailure = e
        } finally {
            // Always release resources
            releaseRecorder(recorder)
        }

        if (stopFailure != null) {
            // The container was never closed, so what is on disk cannot be played as it stands.
            // Report it instead of a normal stop: the service then tries to recover the captured
            // audio rather than saving a record that refuses to open.
            emitEvent(RecorderEvent.OnError(RecordingStopFailedException()))
        } else {
            emitEvent(completionEvent)
        }
    }

    private fun handleRecorderInfo(what: Int) {
        when (what) {
            // The platform MPEG-4 writer holds its sample tables in memory and addresses the
            // file with 32-bit offsets, so it stops the recording by itself once the output
            // grows too large. Treating that like a max-duration hit rolls the session over
            // into the next numbered part; ignoring it (as before) left the recorder dead while
            // the service kept showing an active recording whose timer went on counting.
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                Timber.d("Recorder reported a limit reached (what: $what). Stop recording")
                stopRecording(RecorderEvent.OnMaxDurationReached)
            }
        }
    }

    private fun handleMaxDurationReached() {
        Timber.d("Max recording duration reached. Stop recording")
        stopRecording(RecorderEvent.OnMaxDurationReached)
    }

    /**
     * Handles a [MediaRecorder] runtime failure - a media-server death or an encoder error, both
     * of which get more likely the longer a session runs.
     *
     * Without this listener such a failure is completely silent: the recorder stops producing
     * audio, the file is never finalised, and the service keeps reporting an active recording
     * indefinitely. Stopping here finalises whatever was captured so it can still be saved, and
     * the error is reported as a [RecordingException] - not a [RecorderInitException], which
     * callers read as "start failed, discard the file".
     *
     * The failure is handed to [stopRecording] as its completion event rather than emitted next
     * to it: the service acts on the first event it sees, and it must not act before the
     * container has been closed. It also keeps a stop that then fails on its own reported as a
     * [RecordingStopFailedException], which is the only event that sends the service down the
     * recovery path for an unfinalised file.
     */
    private fun handleRecorderError(what: Int, extra: Int) {
        Timber.e("MediaRecorder error. what: $what extra: $extra")
        if (!stopRecording(RecorderEvent.OnError(RecordingException()))) {
            // Nothing was left to finalise - the recorder had already been torn down, and the
            // teardown that did it reports its own outcome.
            Timber.w("MediaRecorder error arrived with no recording in progress")
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
            if (!isPaused) {
                // The recorder can be released on another thread right after the null check
                // above (a failed start(), or stopRecording() racing with this tick), which
                // makes getMaxAmplitude() throw. Like stop(), it reports every failure as a
                // RuntimeException - IllegalStateException when the recorder was never
                // initialised, and a plain RuntimeException("getMaxAmplitude failed.") when the
                // native call fails (already released, or the media server died). Catching only
                // the subclass let the latter kill the sampling thread, and an uncaught
                // exception there takes down the process. Give up on the loop instead - the
                // next start/resume reschedules it.
                val amplitude = try {
                    currentRecorder.maxAmplitude
                } catch (e: RuntimeException) {
                    Timber.e(e, "Error reading amplitude, stopping progress updates")
                    return@Runnable
                }
                if (!isRecording) {
                    //Set that recording is started only after receiving a valid amplitude value,
                    //which indicates that recording has actually started.
                    if (amplitude > 0) {
                        _isRecording = true
                        synchronized(amplitudesBuffer) { amplitudesBuffer.add(amplitude) }
                    }
                } else {
                    synchronized(amplitudesBuffer) { amplitudesBuffer.add(amplitude) }
                }
                scheduleRecordingTimeUpdate()
            }
        }
    }

    /**
     * Stops the progress timers and releases [mediaRecorder]. Safe to call from any thread.
     *
     * The field is cleared *before* [MediaRecorder.release] so a tick that is already running on
     * the sampling thread cannot pick up an instance that is about to be released. It can still
     * be mid-read when we release, which is why the amplitude read is guarded as well.
     */
    private fun releaseRecorder() {
        val recorder = mediaRecorder
        mediaRecorder = null
        releaseRecorder(recorder)
    }

    /** Releases an instance the caller has already detached from [mediaRecorder]. */
    private fun releaseRecorder(recorder: MediaRecorder?) {
        stopSamplingThread()
        stopRecordingTimerBuffered()
        recorder?.release()
    }

    /**
     * Starts the amplitude sampling thread if it isn't running. Idempotent, so start and
     * resume can both call it. Synchronized because start/stop/release reach this class from
     * the service's IO scope and from MediaRecorder's own callback thread.
     */
    @Synchronized
    private fun startSamplingThread() {
        if (samplingThread != null) return
        val thread = HandlerThread("AmplitudeSampler", Process.THREAD_PRIORITY_AUDIO)
        thread.start()
        samplingThread = thread
        handler = Handler(thread.looper)
    }

    @Synchronized
    private fun stopSamplingThread() {
        handler?.removeCallbacks(recordingTimeUpdateRunnable)
        handler = null
        samplingThread?.quitSafely()
        samplingThread = null
    }

    private fun scheduleRecordingTimeUpdate() {
        val handler = this.handler ?: return
        handler.removeCallbacks(recordingTimeUpdateRunnable)
        handler.postDelayed(recordingTimeUpdateRunnable, (RECORDING_VISUALIZATION_INTERVAL_NEW/1.5).toLong())
    }

    private fun stopRecordingTimer() {
        handler?.removeCallbacks(recordingTimeUpdateRunnable)
    }

    private fun pauseRecordingTimer() {
        handler?.removeCallbacks(recordingTimeUpdateRunnable)
    }

    private fun scheduleRecordingTimeUpdateBuffered() {
        timerProgress?.cancel()
        timerProgress?.purge()
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
        if (mediaRecorder == null || _isPaused) return
        val curTime = SystemClock.elapsedRealtime()
        durationMills += curTime - updateTime
        updateTime = curTime

        if (_isRecording) {
            synchronized(amplitudesBuffer) {
                val bufferSize = amplitudesBuffer.size()
                if (bufferSize > 0) {
                    var amp = amplitudesBuffer.get(bufferSize - 1)
                    if (amp == 0) amp = lastNonZeroAmplitude
                    else lastNonZeroAmplitude = amp
                    amplitudesBuffer.clear()
                    emitEvent(RecorderEvent.OnRecordingProgress(durationMills = durationMills, amplitude = amp))
                }
            }
        }
        // Stopping outside the lock: stopRecording() clears the same buffer and cancels the timer
        // this call runs on.
        if (isMaxDurationReached()) {
            handleMaxDurationReached()
        }
    }

    private fun isMaxDurationReached(): Boolean =
        maxDurationMills > 0 && durationMills >= maxDurationMills
}
