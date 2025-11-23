package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderDelegate @Inject constructor(
    private val prefs: PrefsV2,
    private val audioRecorder: AudioRecorderV2,
) {

    fun provideAudioRecorder(): RecorderV2 {
        return when (prefs.settingRecordingFormat) {
            RecordingFormat.M4a -> audioRecorder
            RecordingFormat.Wav -> TODO("Not implemented")
            RecordingFormat.ThreeGp -> TODO("Not implemented")
        }
    }
}
