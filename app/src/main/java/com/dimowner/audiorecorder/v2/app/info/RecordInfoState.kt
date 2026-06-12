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
import kotlinx.parcelize.RawValue

@Parcelize
data class RecordInfoState(
    val id: Long,
    val name: String,
    val format: String,
    val duration: Long,
    val size: Long,
    val location: String,
    val created: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Int,
    val amps: @RawValue IntArray,
    val authorName: String = "",
    val description: String = "",
) : Parcelable {

    val nameWithExtension: String
        get() = name + AppConstants.EXTENSION_SEPARATOR + format

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RecordInfoState
        if (id != other.id) return false
        if (name != other.name) return false
        if (format != other.format) return false
        if (duration != other.duration) return false
        if (size != other.size) return false
        if (location != other.location) return false
        if (created != other.created) return false
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        if (bitrate != other.bitrate) return false
        if (authorName != other.authorName) return false
        if (description != other.description) return false
        return amps.contentEquals(other.amps)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + created.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + bitrate
        result = 31 * result + authorName.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + amps.contentHashCode()
        return result
    }
}
