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
enum class SampleRate(val value: Int, val index: Int): Parcelable {
    SR8000(value = 8000, index = 0),
    SR16000(value = 16000, index = 1),
    SR22500(value = 22500, index = 2),
    SR32000(value = 32000, index = 3),
    SR44100(value = 44100, index = 4),
    SR48000(value = 48000, index = 5),
}

fun Int.convertToSampleRate(): SampleRate? {
    return if (this == SampleRate.SR8000.value) SampleRate.SR8000
    else if (this == SampleRate.SR16000.value) SampleRate.SR16000
    else if (this == SampleRate.SR22500.value) SampleRate.SR22500
    else if (this == SampleRate.SR32000.value) SampleRate.SR32000
    else if (this == SampleRate.SR44100.value) SampleRate.SR44100
    else if (this == SampleRate.SR48000.value) SampleRate.SR48000
    else null
}
