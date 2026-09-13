package com.dimowner.audiorecorder.v2.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Creates the [AudioRecord] that every AudioRecord-backed recorder captures from, so the
 * microphone and system-playback configurations live in one place instead of being repeated in
 * each recorder.
 *
 * Callers keep their own error handling: this throws the platform's exceptions unchanged rather
 * than swallowing them, because each recorder already maps a failed start onto its own event or
 * result type.
 */
internal object AudioRecordFactory {

    /**
     * Playback usages worth recording. `MEDIA` and `GAME` are the ones users mean by "system
     * sound"; `UNKNOWN` is included because apps that never set [AudioAttributes] explicitly end
     * up there and would otherwise be silently missing from the capture.
     *
     * Everything else is deliberately left out - `VOICE_COMMUNICATION` (calls and VoIP),
     * notifications and alarms - and the platform would refuse most of it anyway.
     */
    private val CAPTURED_USAGES = intArrayOf(
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_GAME,
        AudioAttributes.USAGE_UNKNOWN,
    )

    /**
     * @throws IllegalArgumentException when the platform rejects the requested configuration
     * @throws SecurityException when `RECORD_AUDIO` has not been granted
     * @throws UnsupportedOperationException when the configuration cannot be satisfied, including
     *   system playback capture requested below API 29
     */
    @SuppressLint("MissingPermission")
    fun create(
        input: AudioInput,
        sampleRate: Int,
        channelConfig: Int,
        audioEncoding: Int,
        bufferSize: Int,
    ): AudioRecord {
        return when (input) {
            is AudioInput.Mic -> AudioRecord(
                input.audioSource, sampleRate, channelConfig, audioEncoding, bufferSize
            )
            is AudioInput.SystemPlayback -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw UnsupportedOperationException(
                        "System audio capture requires Android 10 (API 29)"
                    )
                }
                createPlaybackCapture(input, sampleRate, channelConfig, audioEncoding, bufferSize)
            }
        }
    }

    // AudioRecord.Builder.build() carries @RequiresPermission(RECORD_AUDIO). The permission is
    // granted before the recording service is started, and every caller already handles the
    // SecurityException thrown when it is not.
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createPlaybackCapture(
        input: AudioInput.SystemPlayback,
        sampleRate: Int,
        channelConfig: Int,
        audioEncoding: Int,
        bufferSize: Int,
    ): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(input.mediaProjection)
            .apply { CAPTURED_USAGES.forEach { addMatchingUsage(it) } }
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
    }
}
