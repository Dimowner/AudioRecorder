package com.dimowner.audiorecorder.v2.audio

import android.net.Uri
import com.dimowner.audiorecorder.exception.AppException;
import kotlinx.coroutines.flow.Flow

import java.io.File;

/**
 * Destination the recorder writes into: a plain file in the app-private storage, or a SAF
 * document in the user-selected public directory written through a file descriptor
 * (no storage permission required).
 */
sealed class RecordingOutput {
    data class OutputFile(val file: File) : RecordingOutput()
    data class OutputDocument(val uri: Uri) : RecordingOutput()

    fun describe(): String {
        return when (this) {
            is OutputFile -> file.absolutePath
            is OutputDocument -> uri.toString()
        }
    }
}

interface RecorderV2 {
    fun subscribeRecorderEvents(): Flow<RecorderEvent>
    fun startRecording(
        output: RecordingOutput,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioSource: Int,
    ): Boolean
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
    object OnMaxDurationReached: RecorderEvent()
    data class OnError(val exception: AppException): RecorderEvent()
}
