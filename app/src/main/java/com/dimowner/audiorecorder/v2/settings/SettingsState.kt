package com.dimowner.audiorecorder.v2.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class SettingsState(
    val isDynamicColors: Boolean,
    val isDarkTheme: Boolean,
    val isKeepScreenOn: Boolean,
    val isShowRenameDialog: Boolean,
    val namingFormat: String,
    val recordingSetting: RecordingSetting,
    val rateAppLink: String,
    val feedbackEmail: String,
    val totalRecordCount: Long,
    val totalRecordDuration: Long,
    val availableSpace: Long,
    val appName: String,
    val appVersion: String,
) : Parcelable

@Parcelize
data class RecordingSetting(
    val formatName: String,
    val recordingFormats: List<ChipItem>,
    val sampleRates: List<ChipItem>,
    val bitRates: List<ChipItem>,
    val channelCounts: List<ChipItem>,
    val selectedSampleRate: Int,
    val selectedBitRate: Int,
    val selectedChannelCount: Int,
) : Parcelable

@Parcelize
data class ChipItem(
    val id: Int,
    val value: Int,
    val name: String,
    val isSelected: Boolean
) : Parcelable
