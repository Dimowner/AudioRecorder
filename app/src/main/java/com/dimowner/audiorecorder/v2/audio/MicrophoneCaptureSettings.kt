package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode

/** Snapshot before any suspending startup work; scope never changes an in-flight recording. */
data class MicrophoneCaptureSettings(
    val bluetoothSource: AudioSource = AudioSource.DEFAULT,
    val route: BluetoothCaptureRoute = BluetoothCaptureRoute.STANDARD_SCO,
    val mode: BluetoothAudioMode = BluetoothAudioMode.NORMAL,
    val preprocessing: InputPreprocessingPolicy = InputPreprocessingPolicy.SYSTEM_DEFAULT,
    val gain: PcmGainMode = PcmGainMode.OFF,
    val onlyBluetooth: Boolean = true,
) {
    companion object {
        fun from(prefs: PrefsV2) = MicrophoneCaptureSettings(prefs.bluetoothAudioSource,
            prefs.bluetoothCaptureRoute, prefs.bluetoothAudioMode, prefs.inputPreprocessingPolicy,
            prefs.pcmGainMode, prefs.applyOnlyToBluetoothMic)
    }
}
