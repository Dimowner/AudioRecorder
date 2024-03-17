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

data class Record(
    val id: Int,
    val name: String,
    val durationMills: Long,
    val created: Long,
    val added: Long,
    val removed: Long,
    var path: String,
    val format: String,
    val size: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Int,
    val isBookmarked: Boolean,
    val isWaveformProcessed: Boolean,
    val isMovedToRecycle: Boolean,
    val amps: IntArray,
) {

    @SuppressWarnings("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Record

        if (id != other.id) return false
        if (name != other.name) return false
        if (durationMills != other.durationMills) return false
        if (created != other.created) return false
        if (added != other.added) return false
        if (removed != other.removed) return false
        if (path != other.path) return false
        if (format != other.format) return false
        if (size != other.size) return false
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        if (bitrate != other.bitrate) return false
        if (isBookmarked != other.isBookmarked) return false
        if (isWaveformProcessed != other.isWaveformProcessed) return false
        if (isMovedToRecycle != other.isMovedToRecycle) return false
        return amps.contentEquals(other.amps)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + durationMills.hashCode()
        result = 31 * result + created.hashCode()
        result = 31 * result + added.hashCode()
        result = 31 * result + removed.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + bitrate
        result = 31 * result + isBookmarked.hashCode()
        result = 31 * result + isWaveformProcessed.hashCode()
        result = 31 * result + isMovedToRecycle.hashCode()
        result = 31 * result + amps.contentHashCode()
        return result
    }
}
