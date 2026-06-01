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

enum class AudioSource(val value: Int) {
    DEFAULT(MediaRecorder.AudioSource.DEFAULT),
    MIC(MediaRecorder.AudioSource.MIC),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED);
    
    companion object {
        fun fromValue(value: Int): AudioSource {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}
