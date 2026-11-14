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

import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How long [ReplayBufferRecorder] waits for its capture thread to finish after stopping it. */
private const val CAPTURE_THREAD_JOIN_TIMEOUT_MS = 1_000L

/** Size of one AudioRecord read: 100 ms of audio. */
private const val READ_CHUNK_BYTES = ReplayBufferConfig.BYTES_PER_SECOND / 10

/** AudioRecord's own buffer: half a second, so a slow disk write never makes it overflow. */
private const val MIN_RECORD_BUFFER_BYTES = ReplayBufferConfig.BYTES_PER_SECOND / 2

/**
 * Observable state of the instant replay buffer.
 *
 * @property isRunning a buffering session exists ([ReplayBufferService] is up); audio can be saved
 * @property isPausedForRecording a regular recording owns the microphone; capture resumes after it
 * @property isCapturing the microphone is actually being read right now
 * @property durationMinutes how much audio the current session keeps
 * @property isSaving a save is in progress
 */
data class ReplayBufferState(
    val isRunning: Boolean = false,
    val isPausedForRecording: Boolean = false,
    val isCapturing: Boolean = false,
    val durationMinutes: Int = ReplayBufferConfig.DEFAULT_DURATION_MINUTES,
    val isSaving: Boolean = false,
)

/**
 * Owns the instant replay capture: one [AudioRecord] at the fixed [ReplayBufferConfig] format
 * feeding a [PcmRingBuffer] from a dedicated thread.
 *
 * It is an app-wide singleton rather than part of [ReplayBufferService] so that
 * [AudioRecordingService] can hand the microphone over synchronously - [pauseForRecording]
 * returns only once the capture thread has let go of it - instead of racing an async intent
 * against the start of a recording.
 *
 * The "paused for recording" hold is tracked even while no session runs, so a buffer enabled
 * in the middle of a recording starts paused and picks up when that recording ends.
 */
@Singleton
class ReplayBufferRecorder @Inject constructor() {

    private val lock = Any()

    private val _state = MutableStateFlow(ReplayBufferState())
    val state: StateFlow<ReplayBufferState> = _state.asStateFlow()

    // All guarded by [lock].
    private var ringBuffer: PcmRingBuffer? = null
    private var audioSource: Int = 0
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var isHeldForRecording = false

    @Volatile private var keepCapturing = false

    /**
     * Starts a fresh session that keeps [durationMinutes] of audio in [bufferFile]. A session that
     * is already running is discarded first, so a new length always starts from an empty buffer.
     *
     * @param audioSource a [android.media.MediaRecorder.AudioSource] microphone constant
     * @return false if the buffer file or the microphone could not be opened
     */
    fun start(bufferFile: File, durationMinutes: Int, audioSource: Int): Boolean {
        synchronized(lock) {
            stopLocked()
            val minutes = ReplayBufferConfig.sanitizeMinutes(durationMinutes)
            val buffer = try {
                bufferFile.parentFile?.mkdirs()
                PcmRingBuffer(bufferFile, ReplayBufferConfig.bufferSizeBytes(minutes))
            } catch (e: IOException) {
                Timber.e(e, "ReplayBufferRecorder: can't create the buffer file")
                return false
            }
            ringBuffer = buffer
            this.audioSource = audioSource
            val capturing = if (isHeldForRecording) false else startCaptureLocked(buffer)
            if (!isHeldForRecording && !capturing) {
                ringBuffer = null
                buffer.closeAndDelete()
                return false
            }
            _state.value = ReplayBufferState(
                isRunning = true,
                isPausedForRecording = isHeldForRecording,
                isCapturing = capturing,
                durationMinutes = minutes,
            )
            return true
        }
    }

    /** Ends the session and deletes the buffered audio. */
    fun stop() {
        synchronized(lock) {
            stopLocked()
            _state.value = ReplayBufferState(isPausedForRecording = isHeldForRecording)
        }
    }

    /**
     * Releases the microphone for a regular recording. Blocks until capture has stopped (bounded
     * by [CAPTURE_THREAD_JOIN_TIMEOUT_MS]); the buffered audio is kept.
     */
    fun pauseForRecording() {
        synchronized(lock) {
            isHeldForRecording = true
            stopCaptureLocked()
            _state.update { it.copy(isPausedForRecording = true, isCapturing = false) }
        }
        Timber.d("ReplayBufferRecorder: paused for recording")
    }

    /** Takes the microphone back once the regular recording has finished. */
    fun resumeAfterRecording() {
        synchronized(lock) {
            if (!isHeldForRecording) return
            isHeldForRecording = false
            val buffer = ringBuffer
            val capturing = buffer != null && startCaptureLocked(buffer)
            _state.update { it.copy(isPausedForRecording = false, isCapturing = capturing) }
        }
        Timber.d("ReplayBufferRecorder: resumed after recording")
    }

    /** Bytes of audio currently buffered, 0 when no session runs. */
    fun bufferedBytes(): Long = synchronized(lock) { ringBuffer?.bufferedBytes ?: 0L }

    /**
     * Writes the buffered audio, oldest first, to [target] as a WAV file while capture continues.
     *
     * @return PCM bytes written; 0 when nothing was buffered or no session runs
     * @throws IOException when copying fails, including the session being stopped mid-copy
     */
    @Throws(IOException::class)
    fun snapshotToWav(target: File): Long {
        // The copy runs outside [lock] so pause/resume/stop are never blocked behind a long save;
        // the ring buffer itself is safe against a concurrent writer and throws once closed.
        val buffer = synchronized(lock) { ringBuffer } ?: return 0L
        return buffer.snapshotToWav(target)
    }

    internal fun setSaving(isSaving: Boolean) {
        _state.update { it.copy(isSaving = isSaving) }
    }

    private val _savedRecords = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Ids of records saved from the buffer, so an open home screen can show the new one. */
    val savedRecords: SharedFlow<Long> = _savedRecords.asSharedFlow()

    internal fun notifySaved(recordId: Long) {
        _savedRecords.tryEmit(recordId)
    }

    private fun stopLocked() {
        stopCaptureLocked()
        ringBuffer?.closeAndDelete()
        ringBuffer = null
    }

    /** Opens the microphone and starts the capture thread. Caller holds [lock]. */
    private fun startCaptureLocked(buffer: PcmRingBuffer): Boolean {
        if (captureThread?.isAlive == true) return true
        // A capture thread that ended on its own (read error) still holds its AudioRecord.
        stopCaptureLocked()
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(
            ReplayBufferConfig.SAMPLE_RATE, channelConfig, encoding
        )
        if (minBuffer == AudioRecord.ERROR_BAD_VALUE || minBuffer == AudioRecord.ERROR) {
            Timber.e("ReplayBufferRecorder: invalid min buffer size $minBuffer")
            return false
        }
        val record = try {
            AudioRecordFactory.create(
                input = AudioInput.Mic(audioSource),
                sampleRate = ReplayBufferConfig.SAMPLE_RATE,
                channelConfig = channelConfig,
                audioEncoding = encoding,
                bufferSize = maxOf(minBuffer, MIN_RECORD_BUFFER_BYTES),
            )
        } catch (e: SecurityException) {
            Timber.e(e, "ReplayBufferRecorder: RECORD_AUDIO not granted")
            return false
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "ReplayBufferRecorder: AudioRecord rejected the configuration")
            return false
        } catch (e: UnsupportedOperationException) {
            Timber.e(e, "ReplayBufferRecorder: AudioRecord configuration unsupported")
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("ReplayBufferRecorder: AudioRecord failed to initialize")
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            Timber.e(e, "ReplayBufferRecorder: startRecording failed")
            record.release()
            return false
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            // Another app holds the microphone exclusively (e.g. a call on some devices).
            Timber.e("ReplayBufferRecorder: microphone is busy")
            record.release()
            return false
        }
        keepCapturing = true
        audioRecord = record
        captureThread = Thread({ captureLoop(record, buffer) }, "ReplayBufferCapture").apply {
            start()
        }
        return true
    }

    /** Stops the capture thread and releases the microphone. Caller holds [lock]. */
    private fun stopCaptureLocked() {
        val record = audioRecord ?: return
        keepCapturing = false
        try {
            // Unblocks a read() in progress.
            record.stop()
        } catch (e: IllegalStateException) {
            Timber.w(e, "ReplayBufferRecorder: AudioRecord.stop failed")
        }
        captureThread?.join(CAPTURE_THREAD_JOIN_TIMEOUT_MS)
        record.release()
        audioRecord = null
        captureThread = null
        ringBuffer?.discardPartialFrame()
    }

    private fun captureLoop(record: AudioRecord, buffer: PcmRingBuffer) {
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (keepCapturing) {
            val read = record.read(chunk, 0, chunk.size)
            if (read > 0) {
                try {
                    buffer.write(chunk, 0, read)
                } catch (e: IOException) {
                    Timber.e(e, "ReplayBufferRecorder: buffer write failed")
                    break
                }
            } else if (read < 0) {
                if (keepCapturing) Timber.e("ReplayBufferRecorder: AudioRecord.read error $read")
                break
            }
        }
        if (keepCapturing) {
            // The loop ended on its own (read error, disk failure): reflect that capture stopped.
            _state.update { it.copy(isCapturing = false) }
        }
    }
}
