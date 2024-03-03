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
enum class BitRate(val value: Int, val index: Int): Parcelable {
    BR48(48000, 0),
    BR96(96000, 1),
    BR128(128000, 2),
    BR192(192000, 3),
    BR256(256000, 4),
}

fun Int.convertToBitRate(): BitRate? {
    if (this == BitRate.BR48.value) return BitRate.BR48
    if (this == BitRate.BR96.value) return BitRate.BR96
    if (this == BitRate.BR128.value) return BitRate.BR128
    if (this == BitRate.BR192.value) return BitRate.BR192
    if (this == BitRate.BR256.value) return BitRate.BR256

    return null
}
