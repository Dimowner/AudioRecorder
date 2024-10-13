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

package com.dimowner.audiorecorder.v2.app.info

import android.os.Parcelable
import com.dimowner.audiorecorder.AppConstants
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecordInfoState(
    val name: String,
    val format: String,
    val duration: Long,
    val size: Long,
    val location: String,
    val created: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Int,
) : Parcelable {

    val nameWithExtension: String
        get() = name + AppConstants.EXTENSION_SEPARATOR + format
}
