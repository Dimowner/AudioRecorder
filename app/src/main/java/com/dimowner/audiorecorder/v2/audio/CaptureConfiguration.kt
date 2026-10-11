package com.dimowner.audiorecorder.v2.audio

import android.media.projection.MediaProjection
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat

/** Preflight decisions are shared by service entry points and backend fallback. */
internal object CaptureConfiguration {
    fun resolveInput(source: AudioSource, settings: MicrophoneCaptureSettings, useBluetooth: Boolean,
        systemCaptureSupported: Boolean, projection: MediaProjection?): Result<AudioInput> {
        if (!source.isSystemAudio) {
            val effectiveSource = if (useBluetooth) settings.bluetoothSource else source
            if (effectiveSource.isSystemAudio) return Result.failure(IllegalArgumentException("System audio is not a Bluetooth microphone source."))
            val applyProcessing = useBluetooth || !settings.onlyBluetooth
            return Result.success(AudioInput.Mic(effectiveSource.value,
                if (applyProcessing) settings.preprocessing else InputPreprocessingPolicy.SYSTEM_DEFAULT,
                if (applyProcessing) settings.gain else PcmGainMode.OFF,
                audioMode = settings.mode.takeIf { applyProcessing }, requestedSettings = settings))
        }
        if (!systemCaptureSupported) return Result.failure(IllegalArgumentException("System audio requires Android 10 or newer; microphone fallback is not allowed."))
        if (projection == null) return Result.failure(IllegalArgumentException("System-audio consent is missing or expired. Grant capture consent again; microphone fallback is not allowed."))
        return Result.success(AudioInput.SystemPlayback(projection))
    }

    fun modeProblem(input: AudioInput.Mic, actualMode: Int?): String? =
        input.audioMode?.takeIf { it.value != actualMode }?.let {
            "Requested Android audio mode $it (${it.value}); observed mode=${actualMode ?: "unknown"}. Capture stopped; no mode substitution."
        }

    fun mediaRecorderProblem(input: AudioInput): String? = when {
        input !is AudioInput.Mic -> "MediaRecorder cannot capture system audio. Choose WAV or direct M4A."
        input.preprocessing != InputPreprocessingPolicy.SYSTEM_DEFAULT || input.gain != PcmGainMode.OFF ->
            "MediaRecorder cannot apply selected preprocessing ${input.preprocessing} / gain ${input.gain}. Choose WAV or direct M4A, or explicitly select SYSTEM_DEFAULT and OFF."
        else -> null
    }

    fun configurationProblem(format: RecordingFormat, sampleRate: Int, channelCount: Int, input: AudioInput): String? = when {
        format.config.supportedSampleRates.none { it.value == sampleRate } -> "${format.value} does not support selected sample rate $sampleRate Hz."
        format.config.supportedChannelCounts.none { it.value == channelCount } -> "${format.value} does not support selected channel count $channelCount."
        format == RecordingFormat.ThreeGp -> mediaRecorderProblem(input)
        else -> null
    }

    fun supportsAacBitrate(requested: Int, sampleRate: Int, channelCount: Int, codecMaximum: Int): Boolean =
        requested > 0 && requested.toLong() <= minOf(codecMaximum.toLong(), 6L * sampleRate * channelCount)
}
