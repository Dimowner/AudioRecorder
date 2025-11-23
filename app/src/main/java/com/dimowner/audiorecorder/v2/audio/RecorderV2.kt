package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.exception.AppException;
import kotlinx.coroutines.flow.Flow

import java.io.File;

interface RecorderV2 {
    fun subscribeRecorderEvents(): Flow<RecorderEvent>
    fun startRecording(outputFile: File, channelCount: Int, sampleRate: Int, bitrate: Int): Boolean
    fun resumeRecording(): Boolean
    fun pauseRecording(): Boolean
    fun stopRecording(): Boolean
    val isRecording: Boolean
    val isPaused: Boolean
}

sealed class RecorderEvent {
    object OnStartRecording: RecorderEvent()
    object OnPauseRecording: RecorderEvent()
    object OnResumeRecording: RecorderEvent()
    data class OnRecordingProgress(val durationMills: Long, val amplitude: Int): RecorderEvent()
    object OnStopRecording: RecorderEvent()
    data class OnError(val exception: AppException): RecorderEvent()
}
