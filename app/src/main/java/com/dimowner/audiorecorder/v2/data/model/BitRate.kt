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

/**
 * Bitrate in Kbps (thousands bits per second)
 */
@SuppressWarnings("MagicNumber")
@Parcelize
enum class BitRate(val value: Int, val index: Int): Parcelable {
    BR48(48000, 0),
    BR96(96000, 1),
    BR128(128000, 2),
    BR192(192000, 3),
    BR256(256000, 4),
}

fun Int.convertToBitRate(): BitRate? {
    return if (this == BitRate.BR48.value) BitRate.BR48
    else if (this == BitRate.BR96.value) BitRate.BR96
    else if (this == BitRate.BR128.value) BitRate.BR128
    else if (this == BitRate.BR192.value) BitRate.BR192
    else if (this == BitRate.BR256.value) BitRate.BR256
    else null
}
