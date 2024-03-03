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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class RecordingFormat(val value: String, val index: Int) : Parcelable {
    M4a("m4a", 0), Wav("wav", 1), ThreeGp("3gp", 2)
}

fun String.convertToRecordingFormat(): RecordingFormat? {
    if (this.equals(RecordingFormat.M4a.value, true)) return RecordingFormat.M4a
    if (this.equals(RecordingFormat.Wav.value, true)) return RecordingFormat.Wav
    if (this.equals(RecordingFormat.ThreeGp.value, true)) return RecordingFormat.ThreeGp

    return null
}