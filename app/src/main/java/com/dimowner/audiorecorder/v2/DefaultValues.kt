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

package com.dimowner.audiorecorder.v2

import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder

object DefaultValues {
    const val isDarkTheme: Boolean = false
    const val isDynamicTheme: Boolean = false
    const val isAskToRename: Boolean = true
    const val isKeepScreenOn: Boolean = false

    val DefaultSampleRate: SampleRate = SampleRate.SR44100
    val DefaultBitRate: BitRate = BitRate.BR128
    val DefaultChannelCount: ChannelCount = ChannelCount.Stereo

    val DefaultNameFormat: NameFormat = NameFormat.Record
    val DefaultRecordingFormat: RecordingFormat = RecordingFormat.M4a
    val DefaultSortOrder: SortOrder = SortOrder.DateAsc

    val Default3GpBitRate: Int = 12000 //TODO: Find a better solution for 3Gp bitrate
    val Default3GpSampleRate: SampleRate = SampleRate.SR16000
    val Default3GpChannelCount: ChannelCount = ChannelCount.Mono

    const val DELETED_RECORD_MARK = ".deleted"
}
