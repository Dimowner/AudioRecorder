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
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.audio.sumOfAmplitudes
import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.RecorderInitException
import com.dimowner.audiorecorder.exception.RecordingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

/** Samples carried by one AAC-LC access unit. */
private const val AAC_FRAME_SAMPLES = 1024

/** AAC-LC packs at most 6144 bits per channel into every [AAC_FRAME_SAMPLES] sample frame. */
private const val AAC_LC_MAX_BITS_PER_SAMPLE = 6

private const val BITS_PER_SAMPLE = 16
private const val MICROS_PER_SECOND = 1_000_000L

/** The read loop encodes on its own thread, so give AudioRecord room beyond the bare minimum. */
private const val AUDIO_RECORD_BUFFER_FACTOR = 4

private const val INPUT_TIMEOUT_US = 10_000L
private const val DRAIN_TIMEOUT_US = 10_000L

/** How long the encoder may refuse input before the recording is treated as failed. */
private const val INPUT_STALL_LIMIT_MS = 2_000L

/** How long a started pipeline may produce nothing before the caller is told to fall back. */
private const val STARTUP_TIMEOUT_MS = 1_500L

private const val EOS_INPUT_TIMEOUT_MS = 500L
private const val EOS_DRAIN_TIMEOUT_MS = 2_000L

/** Presentation timestamp of the [frameIndex]-th AAC access unit at [sampleRate]. */
internal fun aacPtsUs(frameIndex: Long, sampleRate: Int): Long =
    frameIndex * AAC_FRAME_SAMPLES * MICROS_PER_SECOND / sampleRate

/** Duration covered by [framesFed] PCM sample frames at [sampleRate]. */
internal fun pcmDurationMills(framesFed: Long, sampleRate: Int): Long =
    framesFed * 1000L / sampleRate

/**
 * The requested bitrate reduced to what an AAC-LC encoder can actually produce: neither above the
 * encoder's own range nor above the format's `6 * sampleRate * channelCount` frame ceiling.
 */
internal fun clampAacBitRate(requested: Int, sampleRate: Int, channelCount: Int, codecUpper: Int): Int {
    val ceiling = AAC_LC_MAX_BITS_PER_SAMPLE * sampleRate * channelCount
    return minOf(requested, ceiling, codecUpper).coerceAtLeast(1)
}

/**
 * Records m4a with `AudioRecord` -> `MediaCodec` -> `MediaMuxer`.
 *
 * The reason this exists next to [AudioRecorderV2]: `MediaRecorder.setAudioEncodingBitRate()` is
 * only a request. `StagefrightRecorder` clips it to the `enc.aud.bps.max` of the device media
 * profile - still 96000 on many devices - so a 192 kbps recording quietly comes out at 96 kbps.
 * That clipping lives in `StagefrightRecorder` alone; driving the very same encoder through
 * `MediaCodec` is bound by `media_codecs.xml` instead, where the AAC encoder typically allows up
 * to 960 kbps.
 *
 * Structure follows [WavRecorderV2]: one IO coroutine owns the read loop, duration is derived from
 * the PCM actually captured rather than from wall clock, pausing keeps the hardware running and
 * discards reads, and the stop events are emitted only once the file is complete.
 *
 * Start failures are reported through [StartResult] rather than as events, because
 * `AudioRecordingService` deletes the record and its file when it sees certain errors. Only
 * [M4aRecorderV2] is meant to call [startRecordingInternal]; it decides whether to fall back to
 * `MediaRecorder` instead of surfacing the failure.
 */
@Singleton
@Suppress("TooManyFunctions")
class AacCodecRecorderV2 @Inject constructor(
    private val coroutineScope: CoroutineScope,
) : RecorderV2 {

    /** Outcome of the synchronous part of starting, which decides whether a fallback makes sense. */
    sealed interface StartResult {
        /** The pipeline is running. */
        object Started : StartResult

        /** The request itself is not satisfiable; `MediaRecorder` would fail the same way. */
        data class Rejected(val exception: AppException) : StartResult

        /** The codec pipeline failed to come up; another backend may still work. */
        data class PipelineFailed(val stage: String, val cause: Throwable?) : StartResult
    }

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var recordingJob: Job? = null

    @Volatile private var _isRecording: Boolean = false
    @Volatile private var _isPaused: Boolean = false

    override val isRecording: Boolean
        get() = _isRecording
    override val isPaused: Boolean
        get() = _isPaused

    @Volatile private var durationMills: Long = 0
    private var sampleRateConfig: Int = 44100
    private var channelCountConfig: Int = 1
    private var maxDurationMills: Int = 0

    private var timerProgress: Timer? = null
    private val amplitudesBuffer: IntArrayList = IntArrayList()
    @Volatile private var lastNonZeroAmplitude: Int = 0
    @Volatile private var lastEmittedDurationMills: Long = -1L

    /**
     * Invoked when a pipeline that started cleanly turns out to produce nothing (see
     * [STARTUP_TIMEOUT_MS]). No recorder event is emitted in that case, so the owner can still
     * switch backends without the service ever having seen this recording begin.
     */
    @Volatile var startFailureListener: ((String) -> Unit)? = null

    private val _event = MutableSharedFlow<RecorderEvent>()
    override fun subscribeRecorderEvents(): Flow<RecorderEvent> = _event

    override fun startRecording(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioSource: Int,
    ): Boolean {
        return when (
            val result = startRecordingInternal(
                outputFile, channelCount, sampleRate, bitrate, maxRecordingDurationMills, audioSource
            )
        ) {
            is StartResult.Started -> true
            is StartResult.Rejected -> {
                emitEvent(RecorderEvent.OnError(result.exception))
                false
            }
            // Deliberately silent: an error event here would make the service delete the record.
            is StartResult.PipelineFailed -> false
        }
    }

    @Suppress("LongParameterList", "ReturnCount", "LongMethod")
    fun startRecordingInternal(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioSource: Int,
    ): StartResult {
        Timber.d(
            "Start AAC codec recording outputFile: ${outputFile.absolutePath} channelCount:" +
                " $channelCount sampleRate: $sampleRate bitrate: $bitrate" +
                " maxRecordingDurationMills: $maxRecordingDurationMills audioSource: $audioSource"
        )
        if (_isRecording || codec != null) {
            Timber.e("Recording is already in progress.")
            return StartResult.Rejected(AlreadyRecordingException())
        }
        if (!outputFile.exists() || !outputFile.isFile) {
            return StartResult.Rejected(InvalidOutputFile())
        }

        amplitudesBuffer.clear()
        lastNonZeroAmplitude = 0
        lastEmittedDurationMills = -1L
        sampleRateConfig = sampleRate
        channelCountConfig = channelCount
        maxDurationMills = maxRecordingDurationMills

        val frameSize = channelCount * (BITS_PER_SAMPLE / 8)
        val channelConfig = if (channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Timber.e("Invalid buffer size: $minBufferSize")
            return StartResult.Rejected(RecorderInitException())
        }
        val bufferSize = minBufferSize * AUDIO_RECORD_BUFFER_FACTOR

        // Read in ~20 ms chunks so durationMills advances every ~20 ms; frameSize keeps the chunk
        // sample aligned, and it never exceeds the AudioRecord buffer.
        val readChunkSize = ((sampleRate * RECORDING_VISUALIZATION_INTERVAL_NEW / 1000) * frameSize)
            .coerceAtLeast(frameSize)
            .coerceAtMost(bufferSize)

        val recorder = try {
            AudioRecord(audioSource, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        } catch (e: SecurityException) {
            Timber.e(e, "AudioRecord creation failed due to missing permission")
            return StartResult.Rejected(RecorderInitException())
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "AudioRecord creation failed")
            return StartResult.Rejected(RecorderInitException())
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord initialization failed")
            recorder.release()
            return StartResult.Rejected(RecorderInitException())
        }
        audioRecord = recorder

        val encodingBitRate = clampAacBitRate(bitrate, sampleRate, channelCount, aacEncoderMaxBitRate())
        if (encodingBitRate != bitrate) {
            Timber.w("Bitrate $bitrate is out of range for this configuration, using $encodingBitRate")
        }
        val encoder = try {
            createEncoder(sampleRate, channelCount, encodingBitRate, readChunkSize)
        } catch (e: IOException) {
            return releaseAndFail("codec-create", e)
        } catch (e: IllegalArgumentException) {
            return releaseAndFail("codec-configure", e)
        } catch (e: MediaCodec.CodecException) {
            return releaseAndFail("codec-start", e)
        } catch (e: IllegalStateException) {
            return releaseAndFail("codec-start", e)
        }
        codec = encoder

        muxer = try {
            MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            return releaseAndFail("muxer", e)
        } catch (e: IllegalArgumentException) {
            return releaseAndFail("muxer", e)
        }

        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            Timber.e(e, "startRecording() failed")
            releaseEverything()
            return StartResult.Rejected(RecorderInitException())
        }

        _isRecording = true
        _isPaused = false
        durationMills = 0
        // OnStartRecording is emitted from the loop, once a frame has actually been muxed.
        scheduleRecordingTimeUpdateBuffered()
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            runRecordingLoop(outputFile, readChunkSize, frameSize)
        }
        return StartResult.Started
    }

    private fun createEncoder(
        sampleRate: Int,
        channelCount: Int,
        bitrate: Int,
        readChunkSize: Int,
    ): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, readChunkSize * 2)
            setInteger(
                MediaFormat.KEY_CHANNEL_MASK,
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            )
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        } catch (e: Exception) {
            encoder.release()
            throw e
        }
        return encoder
    }

    /** The most permissive AAC encoder bitrate this device advertises. */
    private fun aacEncoderMaxBitRate(): Int {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, true) }
                }
                .mapNotNull { info ->
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).audioCapabilities?.bitrateRange?.upper
                }
                .maxOrNull() ?: Int.MAX_VALUE
        } catch (e: IllegalArgumentException) {
            Timber.d("Can't read AAC encoder capabilities: ${e.message}")
            Int.MAX_VALUE
        }
    }

    private fun releaseAndFail(stage: String, cause: Throwable): StartResult {
        Timber.w(cause, "MediaCodec recording pipeline failed at stage: $stage")
        releaseEverything()
        return StartResult.PipelineFailed(stage, cause)
    }

    // -------------------------------------------------------------------------
    // Recording loop
    // -------------------------------------------------------------------------

    @Suppress("LongMethod", "NestedBlockDepth", "ComplexMethod")
    private fun CoroutineScope.runRecordingLoop(outputFile: File, readChunkSize: Int, frameSize: Int) {
        val session = MuxSession()
        val pcm = ByteArray(readChunkSize)
        val recorder = audioRecord
        val encoder = codec
        var framesFed = 0L
        var maxDurationReached = false
        var failure: Throwable? = null
        val startupDeadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS

        try {
            while (isActive && _isRecording) {
                if (recorder == null || encoder == null) break
                val read = recorder.read(pcm, 0, readChunkSize)
                if (_isPaused) {
                    // Discard the PCM captured while paused: not feeding it is what keeps the
                    // encoded timeline gapless, since timestamps follow the frames actually fed.
                    drainEncoder(encoder, session, endOfStream = false)
                    continue
                }
                when {
                    read > 0 -> {
                        synchronized(amplitudesBuffer) { amplitudesBuffer.add(calculateAmplitude(pcm, read)) }
                        framesFed = feedEncoder(encoder, pcm, read, framesFed, frameSize, session)
                        durationMills = pcmDurationMills(framesFed, sampleRateConfig)
                        drainEncoder(encoder, session, endOfStream = false)
                    }
                    read == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Timber.e("AudioRecord read error: ERROR_INVALID_OPERATION")
                        break
                    }
                    read == AudioRecord.ERROR_BAD_VALUE -> {
                        Timber.e("AudioRecord read error: ERROR_BAD_VALUE")
                        break
                    }
                }

                if (maxDurationMills > 0 && durationMills >= maxDurationMills) {
                    Timber.d("Max recording duration reached. Stop recording")
                    maxDurationReached = true
                    _isRecording = false
                    _isPaused = false
                    stopHardware()
                    break
                }
                if (session.muxedFrameCount == 0L && SystemClock.elapsedRealtime() > startupDeadline) {
                    Timber.w("Encoder produced no output within ${STARTUP_TIMEOUT_MS}ms")
                    abandonStartup(outputFile)
                    return
                }
            }
        } catch (e: MediaCodec.CodecException) {
            failure = e.takeIf { _isRecording }
        } catch (e: IllegalStateException) {
            // AudioRecord and MediaCodec both throw this when they are used after stopRecording()
            // released them, which is an ordinary stop rather than a failure.
            failure = e.takeIf { _isRecording }
        } catch (e: IOException) {
            failure = e.takeIf { _isRecording }
        }
        if (failure != null) {
            Timber.e(failure, "Recording failed, finishing the file with what was captured")
        }

        finishRecording(session, maxDurationReached, failure, outputFile)
    }

    /**
     * Hands [size] bytes of PCM to the encoder, splitting across input buffers when needed, and
     * returns the new total of sample frames fed. Each piece carries its own timestamp - encoders
     * that hand back smaller buffers than asked for would otherwise see repeated ones.
     */
    @Suppress("LongParameterList")
    private fun feedEncoder(
        encoder: MediaCodec,
        data: ByteArray,
        size: Int,
        framesFedSoFar: Long,
        frameSize: Int,
        session: MuxSession,
    ): Long {
        var offset = 0
        var framesFed = framesFedSoFar
        var starvedSince = 0L
        while (offset < size) {
            val index = encoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) {
                // The encoder has no room until its output is consumed.
                drainEncoder(encoder, session, endOfStream = false)
                val now = SystemClock.elapsedRealtime()
                if (starvedSince == 0L) {
                    starvedSince = now
                } else if (now - starvedSince > INPUT_STALL_LIMIT_MS) {
                    throw EncoderStalledException()
                }
                continue
            }
            starvedSince = 0L
            val buffer = encoder.getInputBuffer(index) ?: continue
            buffer.clear()
            val chunk = minOf(size - offset, buffer.remaining())
            buffer.put(data, offset, chunk)
            encoder.queueInputBuffer(index, 0, chunk, framesFed * MICROS_PER_SECOND / sampleRateConfig, 0)
            framesFed += chunk / frameSize
            offset += chunk
        }
        return framesFed
    }

    /**
     * Moves everything the encoder has ready into the muxer.
     *
     * Timestamps are recomputed from the muxed frame counter instead of being taken from the
     * encoder: every AAC-LC access unit is exactly [AAC_FRAME_SAMPLES] samples and paused audio is
     * never fed, so counting frames yields a contiguous, strictly increasing timeline - which is
     * what `MediaMuxer` demands and what some encoders fail to provide.
     */
    private fun drainEncoder(encoder: MediaCodec, session: MuxSession, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + EOS_DRAIN_TIMEOUT_MS
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) DRAIN_TIMEOUT_US else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || SystemClock.elapsedRealtime() > deadline) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!session.muxerStarted) {
                        val activeMuxer = muxer ?: return
                        session.trackIndex = activeMuxer.addTrack(encoder.outputFormat)
                        activeMuxer.start()
                        session.muxerStarted = true
                    }
                }
                index >= 0 -> {
                    writeSample(encoder, index, info, session)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun writeSample(encoder: MediaCodec, index: Int, info: MediaCodec.BufferInfo, session: MuxSession) {
        val buffer: ByteBuffer? = encoder.getOutputBuffer(index)
        // The codec config blob is already carried by the track format added above.
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            info.size = 0
        }
        if (buffer != null && info.size > 0 && session.muxerStarted) {
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            info.presentationTimeUs = aacPtsUs(session.muxedFrameCount, sampleRateConfig)
            info.flags = info.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            muxer?.writeSampleData(session.trackIndex, buffer, info)
            session.muxedFrameCount++
            if (session.muxedFrameCount == 1L) {
                emitEvent(RecorderEvent.OnStartRecording)
            }
        }
        encoder.releaseOutputBuffer(index, false)
    }

    /**
     * Tears down a pipeline that started but never produced audio, leaving no trace: no events are
     * emitted, so [M4aRecorderV2] can start another backend on the same record.
     */
    private fun abandonStartup(outputFile: File) {
        _isRecording = false
        _isPaused = false
        stopRecordingTimer()
        stopHardware()
        releaseCodecAndMuxer(muxerStarted = false)
        durationMills = 0
        runCatching { outputFile.writeBytes(ByteArray(0)) }
        startFailureListener?.invoke("no-output")
    }

    /**
     * Signals end of stream, drains what is left, closes the container and only then reports the
     * outcome - a consumer must never see a stop event before the file is complete.
     */
    private fun finishRecording(
        session: MuxSession,
        maxDurationReached: Boolean,
        failure: Throwable?,
        outputFile: File,
    ) {
        stopRecordingTimer()
        stopHardware()
        val encoder = codec
        if (encoder != null && failure == null) {
            runCatching { signalEndOfStream(encoder, session) }
                .onFailure { Timber.e(it, "Failed to flush the encoder") }
        }
        releaseCodecAndMuxer(session.muxerStarted)

        _isRecording = false
        _isPaused = false
        durationMills = 0

        when {
            session.muxedFrameCount == 0L -> {
                // Nothing was ever written: the file is an unusable stub, so let the service drop
                // the empty record along with it.
                runCatching { outputFile.delete() }
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
            }
            failure != null -> {
                // Keep what was captured: stop first so the record is persisted, then report.
                emitEvent(RecorderEvent.OnStopRecording)
                emitEvent(RecorderEvent.OnError(RecordingException()))
            }
            maxDurationReached -> emitEvent(RecorderEvent.OnMaxDurationReached)
            else -> emitEvent(RecorderEvent.OnStopRecording)
        }
    }

    private fun signalEndOfStream(encoder: MediaCodec, session: MuxSession) {
        val deadline = SystemClock.elapsedRealtime() + EOS_INPUT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val index = encoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index >= 0) {
                encoder.queueInputBuffer(
                    index, 0, 0,
                    aacPtsUs(session.muxedFrameCount, sampleRateConfig),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                break
            }
            drainEncoder(encoder, session, endOfStream = false)
        }
        drainEncoder(encoder, session, endOfStream = true)
    }

    // -------------------------------------------------------------------------
    // Transport controls
    // -------------------------------------------------------------------------

    override fun resumeRecording(): Boolean {
        if (!_isRecording || !_isPaused) return false
        _isPaused = false
        emitEvent(RecorderEvent.OnResumeRecording)
        scheduleRecordingTimeUpdateBuffered()
        return true
    }

    override fun pauseRecording(): Boolean {
        pauseRecordingTimer()
        if (!_isRecording) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }
        if (_isPaused) {
            Timber.e("Recording has already paused")
            return false
        }
        _isPaused = true
        emitEvent(RecorderEvent.OnPauseRecording)
        return true
    }

    override fun stopRecording(): Boolean {
        stopRecordingTimer()
        if (!_isRecording) {
            Timber.e("Recording has already stopped or hasn't started")
            return false
        }
        _isRecording = false
        _isPaused = false
        synchronized(amplitudesBuffer) { amplitudesBuffer.clear() }
        // The recording coroutine finishes its current read, flushes the encoder, closes the
        // container and only then emits OnStopRecording.
        return stopHardware()
    }

    /** Stops and releases [audioRecord]. Safe to call from any thread. */
    private fun stopHardware(): Boolean {
        return try {
            audioRecord?.let {
                it.stop()
                it.release()
                true
            } ?: false
        } catch (e: IllegalStateException) {
            Timber.e(e, "stopHardware() problems")
            audioRecord?.release()
            false
        } finally {
            audioRecord = null
        }
    }

    private fun releaseCodecAndMuxer(muxerStarted: Boolean) {
        val encoder = codec
        codec = null
        try {
            encoder?.stop()
        } catch (e: IllegalStateException) {
            Timber.e(e, "codec.stop() problems")
        } finally {
            encoder?.release()
        }

        val activeMuxer = muxer
        muxer = null
        try {
            // stop() on a muxer that was never started throws.
            if (muxerStarted) activeMuxer?.stop()
        } catch (e: IllegalStateException) {
            Timber.e(e, "muxer.stop() problems")
        } finally {
            activeMuxer?.release()
        }
    }

    private fun releaseEverything() {
        stopHardware()
        releaseCodecAndMuxer(muxerStarted = false)
    }

    // -------------------------------------------------------------------------
    // Progress
    // -------------------------------------------------------------------------

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int): Int {
        if (bytesRead <= 0) return 0
        val sum = buffer.sumOfAmplitudes(bytesRead)
        return (sum / (bytesRead / 16 + 1)).toInt()
    }

    private fun emitEvent(event: RecorderEvent) {
        coroutineScope.launch {
            _event.emit(event)
        }
    }

    private fun scheduleRecordingTimeUpdateBuffered() {
        stopRecordingTimer()
        timerProgress = Timer()
        timerProgress?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    readBufferedProgress()
                } catch (e: IllegalStateException) {
                    Timber.e(e)
                }
            }
        }, 0, RECORDING_VISUALIZATION_INTERVAL_NEW.toLong())
    }

    private fun stopRecordingTimer() {
        timerProgress?.cancel()
        timerProgress?.purge()
        timerProgress = null
    }

    private fun pauseRecordingTimer() {
        timerProgress?.cancel()
        timerProgress?.purge()
        timerProgress = null
    }

    private fun readBufferedProgress() {
        // Timer.cancel() doesn't prevent an already-scheduled task from running; skip stale
        // fires so a late progress event can't flip state back to RECORDING after stop.
        if (!_isRecording || _isPaused) return
        val currentDuration = durationMills
        // Skip if durationMills hasn't changed since the last emission - this prevents duplicate
        // events when the timer fires faster than the AudioRecord buffer fills.
        if (currentDuration == lastEmittedDurationMills) return
        synchronized(amplitudesBuffer) {
            val bufferSize = amplitudesBuffer.size()
            if (bufferSize > 0) {
                lastEmittedDurationMills = currentDuration
                var amp = amplitudesBuffer.get(bufferSize - 1)
                if (amp == 0) amp = lastNonZeroAmplitude
                else lastNonZeroAmplitude = amp
                amplitudesBuffer.clear()
                amplitudesBuffer.add(amp)
                emitEvent(RecorderEvent.OnRecordingProgress(durationMills = currentDuration, amplitude = amp))
            }
        }
    }

    /** Mutable state of one muxing session, kept together so the loop can pass it around. */
    private class MuxSession {
        var trackIndex: Int = -1
        var muxerStarted: Boolean = false
        var muxedFrameCount: Long = 0
    }

    private class EncoderStalledException : IOException("The encoder stopped accepting input")
}
