package com.dimowner.audiorecorder.v2.info

import android.os.Parcelable
import com.dimowner.audiorecorder.AppConstants
import kotlinx.parcelize.Parcelize

@Parcelize
class RecordInfoState(
    val name: String,
    val format: String,
    val duration: String,
    val size: String,
    val location: String,
    val created: String,
    val sampleRate: String,
    val channelCount: String,
    val bitrate: String,
) : Parcelable {

    val nameWithExtension: String
        get() = name + AppConstants.EXTENSION_SEPARATOR + format
}
