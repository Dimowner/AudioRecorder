package com.dimowner.audiorecorder.v2.settings

import android.os.Parcelable
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsState(
    val isDynamicColors: Boolean,
    val isDarkTheme: Boolean,
    val isKeepScreenOn: Boolean,
    val isShowRenameDialog: Boolean,
    val nameFormats: List<NameFormatItem>,
    val selectedNameFormat: NameFormatItem,
    val recordingSetting: RecordingSetting,
    val sizePerMin: String,
    val recordingSettingsText: String,
    val rateAppLink: String,
    val feedbackEmail: String,
    val totalRecordCount: Int,
    val totalRecordDuration: Long,
    val availableSpace: Long,
    val appName: String,
    val appVersion: String,
) : Parcelable

@Parcelize
data class RecordingSetting(
    val recordingFormats: List<ChipItem<RecordingFormat>>,
    val sampleRates: List<ChipItem<SampleRate>>,
    val bitRates: List<ChipItem<BitRate>>,
    val channelCounts: List<ChipItem<ChannelCount>>,
) : Parcelable

@Parcelize
data class ChipItem<T:Parcelable>(
    val id: Int,
    val value: T,
    val name: String,
    val isSelected: Boolean
) : Parcelable

@Parcelize
data class NameFormatItem(
    val nameFormat: NameFormat,
    val nameText: String,
) : Parcelable