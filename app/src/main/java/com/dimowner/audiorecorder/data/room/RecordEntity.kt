package com.dimowner.audiorecorder.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "records")
@TypeConverters(Converters::class)
data class RecordEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "created") val created: Long,
    @ColumnInfo(name = "added") val added: Long,
    @ColumnInfo(name = "removed") val removed: Long,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "sampleRate") val sampleRate: Int,
    @ColumnInfo(name = "channelCount") val channelCount: Int,
    @ColumnInfo(name = "bitrate") val bitrate: Int,
    @ColumnInfo(name = "isBookmarked") val isBookmarked: Boolean,
    @ColumnInfo(name = "isWaveformProcessed") val isWaveformProcessed: Boolean,
    @ColumnInfo(name = "amps") val amps: IntArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (duration != other.duration) return false
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
        return amps.contentEquals(other.amps)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + duration.hashCode()
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
        result = 31 * result + amps.contentHashCode()
        return result
    }
}
