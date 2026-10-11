package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AudioRecorderDelegate @Inject constructor(
    private val prefs: PrefsV2,
    private val m4aRecorder: M4aRecorderV2,
    private val threeGpRecorder: ThreeGpRecorderV2,
    private val wavRecorder: WavRecorderV2,
) {

    /** Includes route preparation, native startup, pauses and saving, before amplitude flags exist. */
    private val _captureSessionActive = MutableStateFlow(false)
    val captureSessionActive = _captureSessionActive.asStateFlow()
    var isCaptureSessionActive: Boolean
        get() = _captureSessionActive.value
        internal set(value) { _captureSessionActive.value = value }

    fun provideAudioRecorder(): RecorderV2 {
        return when (prefs.settingRecordingFormat) {
            RecordingFormat.M4a -> m4aRecorder
            RecordingFormat.Wav -> wavRecorder
            RecordingFormat.ThreeGp -> threeGpRecorder
        }
    }
}
