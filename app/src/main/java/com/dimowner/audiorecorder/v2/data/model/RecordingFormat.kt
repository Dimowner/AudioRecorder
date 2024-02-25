/*
 * Copyright 2024 Dmytro Ponomarenko
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

enum class RecordingFormat(val value: String) {
    M4a("m4a"), Wav("wav"), ThreeGp("3gp")
}

fun String.convertToRecordingFormat(): RecordingFormat? {
    if (this == RecordingFormat.M4a.value) return RecordingFormat.M4a
    if (this == RecordingFormat.Wav.value) return RecordingFormat.Wav
    if (this == RecordingFormat.ThreeGp.value) return RecordingFormat.ThreeGp

    return null
}