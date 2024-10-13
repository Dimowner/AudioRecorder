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
enum class ChannelCount(val value: Int, val index: Int): Parcelable {
    Stereo(value = 2, index = 0),
    Mono(value = 1, index = 1),
}

fun Int.convertToChannelCount(): ChannelCount? {
    return if (this == ChannelCount.Mono.value) ChannelCount.Mono
    else if (this == ChannelCount.Stereo.value) ChannelCount.Stereo
    else null
}
