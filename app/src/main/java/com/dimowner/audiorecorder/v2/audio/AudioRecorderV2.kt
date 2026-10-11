package com.dimowner.audiorecorder.v2.audio

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderV2 @Inject constructor(
    @ApplicationContext applicationContext: Context,
    coroutineScope: CoroutineScope,
    diagnostics: CaptureDiagnostics = CaptureDiagnostics(),
) : MediaRecorderBase(applicationContext, coroutineScope, diagnostics = diagnostics) {

    override val recordingLogTag: String = "AAC "

    override fun configureRecorder(
        recorder: MediaRecorder,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
    ) {
        recorder.apply {
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(channelCount)
            setAudioSamplingRate(sampleRate)
            setAudioEncodingBitRate(bitrate)
        }
    }
}
