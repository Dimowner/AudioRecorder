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

package com.dimowner.audiorecorder.v2.data

import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder

interface PrefsV2 {
    val isFirstRun: Boolean
    fun confirmFirstRunExecuted()

    var askToRenameAfterRecordingStopped: Boolean

    var activeRecordId: Long

    val recordCounter: Long
    fun incrementRecordCounter()

    var isKeepScreenOn: Boolean

    var recordsSortOrder: SortOrder

    var isDynamicTheme: Boolean
    var isDarkTheme: Boolean

    var settingNamingFormat: NameFormat
    var settingRecordingFormat: RecordingFormat
    var settingSampleRate: SampleRate
    var settingBitrate: BitRate
    var settingChannelCount: ChannelCount

    fun resetRecordingSettings()

    fun fullPreferenceReset()
}
