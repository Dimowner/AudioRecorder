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

import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import kotlinx.coroutines.flow.StateFlow

interface PrefsV2 {
    val isFirstRun: Boolean
    fun confirmFirstRunExecuted()

    var askToRenameAfterRecordingStopped: Boolean

    var activeRecordId: Long
    //Stores the last recorded record id. It is not gets cleared after recording stops.
    // It gets overwritten after new recording starts.
    var recordedRecordId: Long
    var recordedRecordPartCounter: Int
    var recordedRecordBaseName: String?

    val recordCounter: Long
    fun incrementRecordCounter()

    var isKeepScreenOn: Boolean

    var isFloatingRecorderOverlayEnabled: Boolean
    var floatingRecorderOverlayX: Int
    var floatingRecorderOverlayY: Int
    var floatingRecorderRenameOverlayX: Int
    var floatingRecorderRenameOverlayY: Int

    var recordsSortOrder: SortOrder

    var isDynamicTheme: Boolean
    val isDynamicThemeFlow: StateFlow<Boolean>
    var isDarkTheme: Boolean
    val isDarkThemeFlow: StateFlow<Boolean>
    var isAppV2: Boolean

    /**
     * Flag indicates that the user previously used the legacy V1 app and intentionally switched to V2.
     * Read-only in V2 — set by V1's SettingsPresenter.confirmSwitchAppV2().
     */
    var isLegacyAppUser: Boolean

    var settingNamingFormat: NameFormat
    var settingRecordingFormat: RecordingFormat
    var settingSampleRate: SampleRate
    var settingBitrate: BitRate
    var settingChannelCount: ChannelCount
    var settingAudioSource: AudioSource

    var maxRecordingDurationMills: Int

    var recordAuthorName: String

    fun resetRecordingSettings()

    fun fullPreferenceReset()
}
