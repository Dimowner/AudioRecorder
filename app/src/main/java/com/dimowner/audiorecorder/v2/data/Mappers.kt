package com.dimowner.audiorecorder.v2.data

import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.room.RecordEntity

fun RecordEntity.toRecord(): Record {
    return Record(
        id = id,
        name = name,
        duration = duration,
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
        duration = this.duration,
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