/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.v2.data.model

import android.media.MediaRecorder
import android.os.Build

/**
 * Sentinel stored for [AudioSource.SYSTEM_AUDIO].
 *
 * Every other entry persists its [MediaRecorder.AudioSource] constant, and those are small
 * non-negative numbers, so a negative value cannot collide with one - now or when the platform
 * adds more. It must never reach [android.media.AudioRecord]; the recorders take an
 * [com.dimowner.audiorecorder.v2.audio.AudioInput] precisely so that it cannot.
 */
private const val SYSTEM_AUDIO_VALUE = -1000

enum class AudioSource(val value: Int) {
    DEFAULT(MediaRecorder.AudioSource.DEFAULT),
    MIC(MediaRecorder.AudioSource.MIC),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),

    /**
     * Audio played by other apps, captured through the AudioPlaybackCapture API rather than from
     * a microphone. Requires Android 10 (API 29) and a user-granted MediaProjection.
     */
    SYSTEM_AUDIO(SYSTEM_AUDIO_VALUE);

    /** Whether this source captures other apps' playback instead of a microphone. */
    val isSystemAudio: Boolean get() = this == SYSTEM_AUDIO

    companion object {
        fun fromValue(value: Int): AudioSource {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}

/**
 * `true` when this device can capture the audio other apps are playing.
 *
 * The AudioPlaybackCapture API arrived in Android 10 (API 29) and is available on every
 * device from that release, so unlike Opus encoding no codec probing is needed. What it
 * actually yields still depends on the playing app: only `MEDIA`, `GAME` and `UNKNOWN`
 * playback is capturable, and an app can opt out entirely with
 * `android:allowAudioPlaybackCapture="false"`, so a recording may legitimately come out
 * silent.
 */
fun isSystemAudioCaptureSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q