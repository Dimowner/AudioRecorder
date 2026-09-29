package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.exception.RecordingException
import kotlinx.coroutines.flow.Flow

import java.io.File;

interface RecorderV2 {
    fun subscribeRecorderEvents(): Flow<RecorderEvent>
    fun startRecording(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
        maxRecordingDurationMills: Int,
        audioInput: AudioInput,
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

/**
 * The events a recording session ends with, in the order the service has to see them.
 *
 * A recorder emits this sequence exactly once, and only after the hardware is released and the
 * container is closed: the service finalises the record on the first terminal event it sees -
 * reading the file back, rewriting its tags and stopping itself - so an event sent while the
 * recorder is still writing hands it a file that is still growing.
 *
 * A failure that captured audio reports [RecorderEvent.OnStopRecording] first, so the user keeps
 * what was recorded, and the error after it. A failure with nothing on disk reports only the
 * error, which leaves the service to drop the empty record along with its file.
 */
internal fun recordingCompletionEvents(
    failed: Boolean,
    capturedAudio: Boolean,
    maxDurationReached: Boolean,
): List<RecorderEvent> = when {
    failed && capturedAudio -> listOf(
        RecorderEvent.OnStopRecording,
        RecorderEvent.OnError(RecordingException()),
    )
    failed -> listOf(RecorderEvent.OnError(RecordingException()))
    maxDurationReached -> listOf(RecorderEvent.OnMaxDurationReached)
    else -> listOf(RecorderEvent.OnStopRecording)
}
