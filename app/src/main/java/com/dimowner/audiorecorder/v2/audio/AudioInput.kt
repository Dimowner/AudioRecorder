package com.dimowner.audiorecorder.v2.audio

import android.media.projection.MediaProjection

/**
 * Where a recorder pulls its PCM from.
 *
 * This replaces the bare `audioSource: Int` the recorders used to take, because system playback
 * capture is not expressible as a [android.media.MediaRecorder.AudioSource] constant: it is
 * configured with an [android.media.AudioPlaybackCaptureConfiguration] built from a
 * [MediaProjection], and that only plugs into [android.media.AudioRecord.Builder]. Making the
 * distinction a type keeps the two paths apart at compile time, so the `MediaRecorder`-backed
 * recorders can reject what they cannot do instead of silently recording the microphone.
 */
sealed interface AudioInput {

    /** A platform capture source - the microphone, in one of its processing variants. */
    data class Mic(val audioSource: Int) : AudioInput

    /**
     * Audio other apps are playing, captured through the AudioPlaybackCapture API (API 29+).
     *
     * The [mediaProjection] is owned by the caller (the recording service): the recorder only
     * reads from it and never stops or releases it.
     */
    data class SystemPlayback(val mediaProjection: MediaProjection) : AudioInput
}
