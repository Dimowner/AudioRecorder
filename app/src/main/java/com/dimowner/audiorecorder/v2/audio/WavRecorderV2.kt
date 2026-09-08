package com.dimowner.audiorecorder.v2.audio

import android.media.AudioFormat
import android.media.AudioRecord
import com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW
import com.dimowner.audiorecorder.audio.sumOfAmplitudes
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.RecorderInitException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WavRecorderV2 @Inject constructor(
    private val coroutineScope: CoroutineScope,
) : RecorderV2 {

    private var audioRecord: AudioRecord? = null
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
    private var maxDurationMills: Int = Int.MAX_VALUE

    private var timerProgress: Timer? = null
    private val amplitudesBuffer: IntArrayList = IntArrayList()
    @Volatile private var lastNonZeroAmplitude: Int = 0
    @Volatile private var lastEmittedDurationMills: Long = -1L

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
        audioInput: AudioInput,
    ): Boolean {
        Timber.d(
            "WavRecorderV2.startRecording outputFile: ${outputFile.absolutePath} channelCount: $channelCount" +
                    " sampleRate: $sampleRate bitrate: $bitrate maxRecordingDurationMills: $maxRecordingDurationMills" +
                    " audioInput: $audioInput"
        )
        if (_isRecording) {
            Timber.e("Recording is already in progress.")
            emitEvent(RecorderEvent.OnError(AlreadyRecordingException()))
            return false
        }
        amplitudesBuffer.clear()
        lastNonZeroAmplitude = 0
        lastEmittedDurationMills = -1L
        if (!outputFile.exists() || !outputFile.isFile) {
            emitEvent(RecorderEvent.OnError(InvalidOutputFile()))
            return false
        }

        sampleRateConfig = sampleRate
        channelCountConfig = channelCount
        maxDurationMills = maxRecordingDurationMills

        val channelConfig = if (channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val bitsPerSample = 16
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Timber.e("Invalid buffer size: $bufferSize")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }

        // Read in ~20 ms chunks so durationMills advances every ~20 ms and progress events
        // are emitted at least that often. frameSize ensures alignment; coerceAtMost keeps
        // the chunk within the AudioRecord internal buffer to avoid read errors.
        val frameSize = channelCount * (bitsPerSample / 8)
        val readChunkSize = ((sampleRate * RECORDING_VISUALIZATION_INTERVAL_NEW / 1000) * frameSize)
            .coerceAtLeast(frameSize)
            .coerceAtMost(bufferSize)

        val recorder = try {
            AudioRecordFactory.create(
                audioInput, sampleRate, channelConfig, audioEncoding, bufferSize
            )
        } catch (e: SecurityException) {
            Timber.e(e, "AudioRecord creation failed due to missing permission")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "AudioRecord creation failed")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        } catch (e: UnsupportedOperationException) {
            // AudioRecord.Builder rejects a system-playback configuration the device cannot honour.
            Timber.e(e, "AudioRecord creation failed for $audioInput")
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord initialization failed")
            recorder.release()
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }

        audioRecord = recorder

        // Write a placeholder 44-byte WAV header; it will be overwritten with real values after recording.
        try {
            FileOutputStream(outputFile).use { fos ->
                fos.write(ByteArray(44))
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to write placeholder WAV header")
            recorder.release()
            audioRecord = null
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }

        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            Timber.e(e, "startRecording() failed")
            recorder.release()
            audioRecord = null
            emitEvent(RecorderEvent.OnError(RecorderInitException()))
            return false
        }

        _isRecording = true
        _isPaused = false
        durationMills = 0
        emitEvent(RecorderEvent.OnStartRecording)
        scheduleRecordingTimeUpdateBuffered()

        // Launch a coroutine to read audio data in the background
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            var fos: FileOutputStream? = null
            var totalBytesWritten = 0L
            val bytesPerSecond = sampleRate * channelCount * (bitsPerSample / 8)
            var maxDurationReached = false

            try {
                fos = FileOutputStream(outputFile, true) // append after the placeholder header
                while (isActive && _isRecording) {
                    if (_isPaused) {
                        // Read and discard PCM data to prevent accumulating stale audio during pause
                        val readResult = recorder.read(buffer, 0, readChunkSize)
                        if (readResult < 0) {
                            Timber.e("AudioRecord read error during pause: $readResult")
                            break
                        }
                        continue
                    }
                    val readResult = recorder.read(buffer, 0, readChunkSize)
                    if (readResult > 0) {
                        fos.write(buffer, 0, readResult)
                        totalBytesWritten += readResult

                        // Calculate duration from bytes written
                        durationMills = (totalBytesWritten * 1000L) / bytesPerSecond

                        // Each read covers ~RECORDING_VISUALIZATION_INTERVAL_NEW ms, so always update amplitude
                        val amplitude = calculateAmplitude(buffer, readResult)
                        synchronized(amplitudesBuffer) { amplitudesBuffer.add(amplitude) }

                        // Check max duration
                        if (maxDurationMills > 0 && durationMills >= maxDurationMills) {
                            Timber.d("Max recording duration reached. Stop recording")
                            // Signal the loop to stop; the hardware is torn down in the finally
                            // below. OnStopRecording and OnMaxDurationReached are both emitted
                            // after the WAV header is written in-place, so consumers always see
                            // a complete file.
                            maxDurationReached = true
                            _isRecording = false
                            _isPaused = false
                            break
                        }
                    } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                        Timber.e("AudioRecord read error: ERROR_INVALID_OPERATION")
                        break
                    } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                        Timber.e("AudioRecord read error: ERROR_BAD_VALUE")
                        break
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Error writing PCM data")
                emitEvent(RecorderEvent.OnError(RecorderInitException()))
            } finally {
                try {
                    fos?.close()
                } catch (e: IOException) {
                    Timber.e(e, "Error closing output file stream")
                }
                // The hardware is released here, on this background thread, and never from
                // stopRecording(): AudioRecord.stop() is a synchronous binder call into
                // audioserver that does not return until the input stream has been torn down,
                // which takes seconds on some devices. The loop has already left `recorder`
                // alone by this point, so nothing can read from a released instance.
                // The timer is cancelled here as well because the max-duration and error paths
                // leave the loop without going through stopRecording(), which would leak it.
                stopRecordingTimer()
                stopHardware(recorder)
            }

            // Write the real WAV header in-place now that we know the final audio length.
            if (outputFile.exists()) {
                try {
                    val totalAudioLen = totalBytesWritten
                    val totalDataLen = totalAudioLen + 36
                    val byteRate = (sampleRateConfig * channelCountConfig * bitsPerSample / 8).toLong()

                    RandomAccessFile(outputFile, "rw").use { raf ->
                        raf.seek(0)
                        val headerStream = FileOutputStream(raf.fd)
                        writeWavHeader(
                            out = headerStream,
                            totalAudioLen = totalAudioLen,
                            totalDataLen = totalDataLen,
                            sampleRate = sampleRateConfig,
                            channels = channelCountConfig,
                            byteRate = byteRate,
                        )
                        headerStream.flush()
                    }

                    if (maxDurationReached) {
                        emitEvent(RecorderEvent.OnMaxDurationReached)
                    } else {
                        emitEvent(RecorderEvent.OnStopRecording)
                    }
                } catch (e: IOException) {
                    Timber.e(e, "Error writing WAV header")
                    emitEvent(RecorderEvent.OnError(RecorderInitException()))
                }
            }

            // Clean up state only after header write so nothing above reads stale nulls.
            durationMills = 0
        }
        return true
    }

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
        // Flip the flags only; the recording coroutine finishes its current read(), flushes the
        // PCM data, writes the WAV header in-place, emits OnStopRecording and releases the
        // hardware. No native call runs on this thread - this is reached from the main thread
        // (the stop button, the notification action and the MediaProjection callback), and
        // AudioRecord.stop() blocks there long enough to ANR.
        _isRecording = false
        _isPaused = false
        synchronized(amplitudesBuffer) { amplitudesBuffer.clear() }
        return true
    }

    /**
     * Stops and releases the [recorder] this recording coroutine owns. Invoked from the
     * coroutine's teardown. Releases the passed instance (not the field) so a rapid stop->start
     * that has already swapped in a new [AudioRecord] is not torn down by the previous run; the
     * field is only cleared if it still points at this recorder.
     */
    private fun stopHardware(recorder: AudioRecord) {
        try {
            recorder.stop()
        } catch (e: IllegalStateException) {
            Timber.e(e, "stopHardware() problems")
        } finally {
            recorder.release()
            if (audioRecord === recorder) audioRecord = null
        }
    }

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

    /**
     * Writes a 44-byte RIFF/WAV header to a FileOutputStream.
     * The header follows the standard little-endian format required for WAV files.
     *
     * @param out              The output stream to write the header to.
     * @param totalAudioLen    The total length of the raw audio data in bytes.
     * @param totalDataLen     The total data length (totalAudioLen + 36).
     * @param sampleRate       The sample rate in Hz.
     * @param channels         The number of audio channels (1 = mono, 2 = stereo).
     * @param byteRate         The byte rate (sampleRate * channels * bitsPerSample / 8).
     */
    @SuppressWarnings("MagicNumber", "LongParameterList")
    @Throws(IOException::class)
    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Long,
    ) {
        val header = createWavHeader(
            totalAudioLen = totalAudioLen,
            totalDataLen = totalDataLen,
            sampleRate = sampleRate,
            channels = channels,
            byteRate = byteRate,
        )
        out.write(header, 0, 44)
    }

    private fun scheduleRecordingTimeUpdateBuffered() {
        stopRecordingTimer()
        timerProgress = Timer()
        timerProgress?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    readBufferedProgress()
                } catch (e: java.lang.IllegalStateException) {
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
        // Skip if durationMills hasn't changed since the last emission — this prevents duplicate
        // events when the timer fires faster than the AudioRecord buffer fills (~40 ms/buffer).
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
}

fun createWavHeader(
    totalAudioLen: Long,
    totalDataLen: Long,
    sampleRate: Int,
    channels: Int,
    byteRate: Long,
): ByteArray {
    val bitsPerSample = 16
    val blockAlign = channels * bitsPerSample / 8

    val header = ByteArray(44)

    // RIFF chunk descriptor
    header[0] = 'R'.code.toByte()  // ChunkID
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    // ChunkSize = totalDataLen (little-endian)
    header[4] = (totalDataLen and 0xFFL).toByte()
    header[5] = ((totalDataLen shr 8) and 0xFFL).toByte()
    header[6] = ((totalDataLen shr 16) and 0xFFL).toByte()
    header[7] = ((totalDataLen shr 24) and 0xFFL).toByte()
    // Format
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()

    // "fmt " sub-chunk
    header[12] = 'f'.code.toByte() // Subchunk1ID
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    // Subchunk1Size = 16 for PCM (little-endian)
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0
    // AudioFormat = 1 (PCM) (little-endian)
    header[20] = 1
    header[21] = 0
    // NumChannels (little-endian)
    header[22] = (channels and 0xFF).toByte()
    header[23] = ((channels shr 8) and 0xFF).toByte()
    // SampleRate (little-endian)
    header[24] = (sampleRate and 0xFF).toByte()
    header[25] = ((sampleRate shr 8) and 0xFF).toByte()
    header[26] = ((sampleRate shr 16) and 0xFF).toByte()
    header[27] = ((sampleRate shr 24) and 0xFF).toByte()
    // ByteRate (little-endian)
    header[28] = (byteRate and 0xFFL).toByte()
    header[29] = ((byteRate shr 8) and 0xFFL).toByte()
    header[30] = ((byteRate shr 16) and 0xFFL).toByte()
    header[31] = ((byteRate shr 24) and 0xFFL).toByte()
    // BlockAlign (little-endian)
    header[32] = (blockAlign and 0xFF).toByte()
    header[33] = ((blockAlign shr 8) and 0xFF).toByte()
    // BitsPerSample (little-endian)
    header[34] = (bitsPerSample and 0xFF).toByte()
    header[35] = ((bitsPerSample shr 8) and 0xFF).toByte()

    // "data" sub-chunk
    header[36] = 'd'.code.toByte() // Subchunk2ID
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    // Subchunk2Size = totalAudioLen (little-endian)
    header[40] = (totalAudioLen and 0xFFL).toByte()
    header[41] = ((totalAudioLen shr 8) and 0xFFL).toByte()
    header[42] = ((totalAudioLen shr 16) and 0xFFL).toByte()
    header[43] = ((totalAudioLen shr 24) and 0xFFL).toByte()

    return header
}