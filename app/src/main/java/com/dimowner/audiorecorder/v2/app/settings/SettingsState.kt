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

package com.dimowner.audiorecorder.v2.app.settings

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
    val recordingSettings: List<RecordingSetting>,
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
    val recordingFormat: ChipItem<RecordingFormat>,
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