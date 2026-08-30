package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderDelegate @Inject constructor(
    private val prefs: PrefsV2,
    private val m4aRecorder: M4aRecorderV2,
    private val threeGpRecorder: ThreeGpRecorderV2,
    private val wavRecorder: WavRecorderV2,
) {

    fun provideAudioRecorder(): RecorderV2 {
        return when (prefs.settingRecordingFormat) {
            RecordingFormat.M4a -> m4aRecorder
            RecordingFormat.Wav -> wavRecorder
            RecordingFormat.ThreeGp -> threeGpRecorder
        }
    }
}
