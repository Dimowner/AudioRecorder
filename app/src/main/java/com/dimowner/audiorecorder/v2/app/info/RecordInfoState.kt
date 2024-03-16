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
