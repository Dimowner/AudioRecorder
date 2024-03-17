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

package com.dimowner.audiorecorder.v2.data

import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.room.RecordEntity

fun RecordEntity.toRecord(): Record {
    return Record(
        id = id,
        name = name,
        durationMills = duration,
        created = created,
        added = added,
        removed = removed,
        path = path,
        format = format,
        size = size,
        sampleRate = sampleRate,
        channelCount = channelCount,
        bitrate = bitrate,
        isBookmarked = isBookmarked,
        isWaveformProcessed = isWaveformProcessed,
        isMovedToRecycle = isMovedToRecycle,
        amps = amps,
    )
}

fun Record.toRecordEntity(): RecordEntity {
    return RecordEntity(
        id = this.id,
        name = this.name,
        duration = this.durationMills,
        created = this.created,
        added = this.added,
        removed = this.removed,
        path = this.path,
        format = this.format,
        size = this.size,
        sampleRate = this.sampleRate,
        channelCount = this.channelCount,
        bitrate = this.bitrate,
        isBookmarked = this.isBookmarked,
        isWaveformProcessed = this.isWaveformProcessed,
        isMovedToRecycle = this.isMovedToRecycle,
        amps = this.amps,
    )
}
