package com.dimowner.audiorecorder.v2.data.model

/** Stable names stored in preferences; Custom deliberately remains an explicit choice. */
enum class BluetoothVoiceEnhancement {
    DISABLED,
    HFP_PRESET,
    CUSTOM;

    internal val configuration: BluetoothVoiceConfiguration?
        get() = when (this) {
            DISABLED -> BluetoothVoiceConfiguration.STANDARD
            HFP_PRESET -> BluetoothVoiceConfiguration.HFP
            CUSTOM -> null
        }

    companion object {
        /** Older builds stored the individual choices only. Classify them without changing them. */
        internal fun fromConfiguration(configuration: BluetoothVoiceConfiguration): BluetoothVoiceEnhancement =
            when (configuration) {
                BluetoothVoiceConfiguration.STANDARD -> DISABLED
                BluetoothVoiceConfiguration.HFP -> HFP_PRESET
                else -> CUSTOM
            }
    }
}

/** One complete preset value, so source/routing/DSP choices cannot drift between implementations. */
internal data class BluetoothVoiceConfiguration(
    val source: AudioSource,
    val route: BluetoothCaptureRoute,
    val mode: BluetoothAudioMode,
    val preprocessing: InputPreprocessingPolicy,
    val gain: PcmGainMode,
) {
    companion object {
        val STANDARD = BluetoothVoiceConfiguration(AudioSource.DEFAULT, BluetoothCaptureRoute.STANDARD_SCO,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.SYSTEM_DEFAULT, PcmGainMode.OFF)
        val HFP = BluetoothVoiceConfiguration(AudioSource.VOICE_RECOGNITION, BluetoothCaptureRoute.HFP_VOICE_RECOGNITION,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
    }
}
