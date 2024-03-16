package com.dimowner.audiorecorder.v2.app.info

import com.dimowner.audiorecorder.v2.data.model.Record

fun Record.toRecordInfoState(): RecordInfoState {
    return RecordInfoState(
        name = this.name,
        format = this.format,
        duration = this.size,
        size = this.size,
        location = this.path,
        created = this.created,
        sampleRate = this.sampleRate,
        channelCount = this.channelCount,
        bitrate = this.bitrate,
    )
}