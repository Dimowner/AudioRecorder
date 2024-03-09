package com.dimowner.audiorecorder.v2.data.model

data class Record(
    val id: Int,
    val name: String,
    val duration: Long,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Record

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
        if (isMovedToRecycle != other.isMovedToRecycle) return false
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
        result = 31 * result + isMovedToRecycle.hashCode()
        result = 31 * result + amps.contentHashCode()
        return result
    }
}