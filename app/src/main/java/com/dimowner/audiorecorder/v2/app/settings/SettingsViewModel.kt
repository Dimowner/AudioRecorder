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

import android.content.Context
import android.os.Parcelable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.app.formatBitRate
import com.dimowner.audiorecorder.v2.app.formatChannelCount
import com.dimowner.audiorecorder.v2.app.formatRecordingFormat
import com.dimowner.audiorecorder.v2.app.formatSampleRate
import com.dimowner.audiorecorder.v2.app.recordingSettingsCombinedText
import com.dimowner.audiorecorder.v2.app.removeOutdatedTrashRecords
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val prefs: PrefsV2,
    private val recordsDataSource: RecordsDataSource,
    private val fileDataSource: FileDataSource,
    private val audioPlayer: PlayerContractNew.Player,
    private val audioRecorderDelegate: AudioRecorderDelegate,
    private val analyticsTracker: AnalyticsTracker,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val decimalFormat: DecimalFormat

    private val selectedFormat = prefs.settingRecordingFormat
    private val selectedSampleRate = prefs.settingSampleRate
    private val selectedBitRate = prefs.settingBitrate
    private val selectedChannelCount = prefs.settingChannelCount

    private val formatsStrings = context.resources.getStringArray(R.array.formats2)
    private val sampleRateStrings = context.resources.getStringArray(R.array.sample_rates2)
    private val bitRateStrings = context.resources.getStringArray(R.array.bit_rates2)
    private val channelCountsStrings = context.resources.getStringArray(R.array.channels)

    init {
        val formatSymbols = DecimalFormatSymbols(Locale.getDefault())
        formatSymbols.decimalSeparator = '.'
        decimalFormat = DecimalFormat("#.#", formatSymbols)
    }

    private val _state: MutableState<SettingsState> = mutableStateOf(createDefaultState(context))

    val state: State<SettingsState> = _state

    private fun createDefaultState(context: Context): SettingsState {
        return SettingsState(
            isDynamicColors = prefs.isDynamicTheme,
            isDarkTheme = prefs.isDarkTheme,
            isAppV2 = prefs.isAppV2,
            isKeepScreenOn = prefs.isKeepScreenOn,
            isFloatingRecorderOverlayEnabled = prefs.isFloatingRecorderOverlayEnabled,
            isShowRenameDialog = prefs.askToRenameAfterRecordingStopped,
            isRecordingSettingEditable = true,
            selectedNameFormat = prefs.settingNamingFormat.toNameFormatItem(),
            nameFormats = makeNameFormats(),
            recordingSettings = RecordingFormat.entries.toList().mapIndexed { index, format ->
                RecordingSetting(
                    recordingFormat = ChipItem(
                        id = index,
                        value = format,
                        name = formatsStrings[index],
                        isSelected = format == selectedFormat
                    ),
                    sampleRates = getSampleRates(
                        selectedFormat,
                        selectedSampleRate,
                        sampleRateStrings
                    ),
                    bitRates = getBitRates(
                        selectedFormat,
                        selectedBitRate,
                        bitRateStrings
                    ),
                    channelCounts = getChannelCounts(
                        selectedFormat,
                        selectedChannelCount,
                        channelCountsStrings
                    ),
                )
            },
            sizePerMin = decimalFormat.format(
                sizeMbPerMin(
                    selectedFormat,
                    selectedSampleRate,
                    selectedBitRate,
                    selectedChannelCount
                )
            ),
            recordingSettingsText = recordingSettingsCombinedText(
                selectedFormat,
                formatRecordingFormat(formatsStrings, selectedFormat),
                formatSampleRate(sampleRateStrings, selectedSampleRate),
                formatBitRate(bitRateStrings, selectedBitRate),
                formatChannelCount(channelCountsStrings, selectedChannelCount),
            ),
            totalRecordCount = 0,
            totalRecordDuration = 0,
            availableSpaceMills = 0,
            availableSpaceBytes = 0,
            appName = context.getString(R.string.app_name),
            appVersion = context.getString(R.string.version, AndroidUtils.getAppVersion(context)),
            maxRecordingDurationMinutes = prefs.maxRecordingDurationMills / 60000,
            recordAuthorName = prefs.recordAuthorName,
            isLegacyAppUser = prefs.isLegacyAppUser,
        )
    }

    fun initSettings() {
        viewModelScope.launch(ioDispatcher) {
            val recordsCount = recordsDataSource.getRecordsCount()
            val recordsDuration = recordsDataSource.getRecordTotalDuration()
            val rawAvailableSpaceBytes = fileDataSource.getAvailableSpace()
            val settings = _state.value.recordingSettings.firstOrNull { it.recordingFormat.isSelected }
            val availableTimeMills = spaceToRecordingTimeMills(
                rawAvailableSpaceBytes,
                settings?.recordingFormat?.value,
                settings?.sampleRates?.firstOrNull { it.isSelected }?.value,
                settings?.bitRates?.firstOrNull { it.isSelected }?.value,
                settings?.channelCounts?.firstOrNull { it.isSelected }?.value,
            )
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    isRecordingSettingEditable = !audioRecorderDelegate.provideAudioRecorder().isRecording,
                    totalRecordCount = recordsCount,
                    totalRecordDuration = recordsDuration,
                    availableSpaceMills = availableTimeMills,
                    availableSpaceBytes = rawAvailableSpaceBytes,
                    // Load the selected audio source from preferences
                    selectedAudioSource = prefs.settingAudioSource
                )
            }
            recordsDataSource.removeOutdatedTrashRecords()
        }
    }

    fun setAudioSource(audioSource: AudioSource) {
        _state.value = _state.value.copy(selectedAudioSource = audioSource)
        prefs.settingAudioSource = audioSource
    }

    fun executeFirstRun() {
        if (prefs.isFirstRun) {
            prefs.confirmFirstRunExecuted()
        }
    }

    fun handleUseAppV2(value: Boolean) {
        if (prefs.isAppV2 != value) {
            prefs.isAppV2 = value
            if (value) {
                analyticsTracker.trackSwitchToAppV2()
            } else {
                analyticsTracker.trackSwitchToLegacyApp()
            }
        }
        audioPlayer.stop()
    }

    fun setDarkTheme(value: Boolean) {
        if (prefs.isDarkTheme != value) {
            prefs.isDarkTheme = value
            _state.value = _state.value.copy(isDarkTheme = value)
        }
    }

    fun setDynamicTheme(value: Boolean) {
        if (prefs.isDynamicTheme != value) {
            prefs.isDynamicTheme = value
            _state.value = _state.value.copy(isDynamicColors = value)
        }
    }

    fun setKeepScreenOn(value: Boolean) {
        prefs.isKeepScreenOn = value
        _state.value = _state.value.copy(isKeepScreenOn = value)
    }

    fun setFloatingRecorderOverlayEnabled(value: Boolean) {
        prefs.isFloatingRecorderOverlayEnabled = value
        _state.value = _state.value.copy(isFloatingRecorderOverlayEnabled = value)
    }

    fun setShowRenamingDialog(value: Boolean) {
        prefs.askToRenameAfterRecordingStopped = value
        _state.value = _state.value.copy(isShowRenameDialog = value)
    }

    fun setNameFormat(value: NameFormatItem) {
        prefs.settingNamingFormat = value.nameFormat
        _state.value = _state.value.copy(selectedNameFormat = value)
    }

    fun resetRecordingSettings() {
        prefs.settingRecordingFormat = DefaultValues.DefaultRecordingFormat
        prefs.settingSampleRate = DefaultValues.DefaultSampleRate
        prefs.settingBitrate = DefaultValues.DefaultBitRate
        prefs.settingChannelCount = DefaultValues.DefaultChannelCount
        prefs.settingAudioSource = DefaultValues.DefaultAudioSource
        _state.value = _state.value.copy(
            recordingSettings = _state.value.recordingSettings.map { formatSetting ->
                RecordingSetting(
                    recordingFormat = formatSetting.recordingFormat.updateSelected(
                        DefaultValues.DefaultRecordingFormat
                    ),
                    sampleRates = getSampleRates(
                        DefaultValues.DefaultRecordingFormat,
                        DefaultValues.DefaultSampleRate,
                        sampleRateStrings
                    ),
                    bitRates = getBitRates(
                        DefaultValues.DefaultRecordingFormat,
                        DefaultValues.DefaultBitRate,
                        bitRateStrings
                    ),
                    channelCounts = getChannelCounts(
                        DefaultValues.DefaultRecordingFormat,
                        DefaultValues.DefaultChannelCount,
                        channelCountsStrings
                    ),
                )
            },
        ).recordingSettingsUpdated()
    }

    fun selectRecordingFormat(value: RecordingFormat) {
        prefs.settingRecordingFormat = value
        _state.value = _state.value.copy(
            recordingSettings = _state.value.recordingSettings.map { item ->
                item.copy(
                    recordingFormat = item.recordingFormat.updateSelected(value),
                    sampleRates = getSampleRates(
                        value,
                        prefs.settingSampleRate,
                        sampleRateStrings
                    ),
                    bitRates = getBitRates(
                        value,
                        prefs.settingBitrate,
                        bitRateStrings
                    ),
                    channelCounts = getChannelCounts(
                        value,
                        prefs.settingChannelCount,
                        channelCountsStrings
                    ),
                )
            }
        ).validate3GpSelectedAndAdjust(value)
        .recordingSettingsUpdated()
    }

    private fun SettingsState.validate3GpSelectedAndAdjust(format: RecordingFormat): SettingsState {
        return if (format == RecordingFormat.ThreeGp) {
            val formatSetting = this.recordingSettings.firstOrNull {
                it.recordingFormat.value == format
            }
            val hasSelectedSampleRate = formatSetting?.sampleRates?.any { it.isSelected } ?: false
            val hasSelectedChannelCount = formatSetting?.channelCounts?.any { it.isSelected } ?: false
            if (!hasSelectedSampleRate || !hasSelectedChannelCount) {
                this.copy(
                    recordingSettings = recordingSettings.map { recordingSetting ->
                        if (recordingSetting.recordingFormat.value == format) {
                            recordingSetting.copy(
                                sampleRates = if (hasSelectedSampleRate) {
                                    recordingSetting.sampleRates
                                } else {
                                    prefs.settingSampleRate = DefaultValues.Default3GpSampleRate
                                    recordingSetting.sampleRates.map {
                                        if (it.value == DefaultValues.Default3GpSampleRate) {
                                            it.copy(isSelected = true)
                                        } else {
                                            it
                                        }
                                    }
                                },
                                channelCounts = if (hasSelectedChannelCount) {
                                    recordingSetting.channelCounts
                                } else {
                                    prefs.settingChannelCount = DefaultValues.Default3GpChannelCount
                                    recordingSetting.channelCounts.map {
                                        if (it.value == DefaultValues.Default3GpChannelCount) {
                                            it.copy(isSelected = true)
                                        } else {
                                            it
                                        }
                                    }
                                }
                            )
                        } else {
                            recordingSetting
                        }
                    }
                )
            } else {
                this
            }
        } else {
            return this
        }
    }

    fun selectSampleRate(value: SampleRate) {
        prefs.settingSampleRate = value
        _state.value = _state.value.copy(
            recordingSettings = _state.value.recordingSettings.map { formatSetting ->
                if (formatSetting.recordingFormat.isSelected) {
                    formatSetting.copy(
                        sampleRates = formatSetting.sampleRates.map { item ->
                            item.updateSelected(value)
                        }
                    )
                } else {
                    formatSetting
                }
            }
        ).recordingSettingsUpdated()
    }

    fun selectBitrate(value: BitRate) {
        prefs.settingBitrate = value
        _state.value = _state.value.copy(
            recordingSettings = _state.value.recordingSettings.map { formatSetting ->
                if (formatSetting.recordingFormat.isSelected) {
                    formatSetting.copy(
                        bitRates = formatSetting.bitRates.map { item ->
                            item.updateSelected(value)
                        }
                    )
                } else {
                    formatSetting
                }
            }
        ).recordingSettingsUpdated()
    }

    fun selectChannelCount(value: ChannelCount) {
        prefs.settingChannelCount = value
        _state.value = _state.value.copy(
            recordingSettings = _state.value.recordingSettings.map { formatSetting ->
                if (formatSetting.recordingFormat.isSelected) {
                    formatSetting.copy(
                        channelCounts = formatSetting.channelCounts.map { item ->
                            item.updateSelected(value)
                        }
                    )
                } else {
                    formatSetting
                }
            }
        ).recordingSettingsUpdated()
    }

    fun setMaxRecordingDuration(durationMinutes: Int) {
        if (durationMinutes > 0) {
            prefs.maxRecordingDurationMills = durationMinutes * 60 * 1000
            _state.value = _state.value.copy(maxRecordingDurationMinutes = durationMinutes)
        }
    }

    fun setRecordAuthorName(name: String) {
        val trimmed = name.trim()
        prefs.recordAuthorName = trimmed
        _state.value = _state.value.copy(recordAuthorName = trimmed)
    }

    fun onAction(action: SettingsScreenAction) {
        when (action) {
            SettingsScreenAction.InitSettingsScreen -> initSettings()
            is SettingsScreenAction.SetDynamicTheme -> setDynamicTheme(action.value)
            is SettingsScreenAction.SetDarkTheme -> setDarkTheme(action.value)
            is SettingsScreenAction.SetKeepScreenOn -> setKeepScreenOn(action.value)
            is SettingsScreenAction.SetFloatingRecorderOverlayEnabled -> {
                setFloatingRecorderOverlayEnabled(action.value)
            }
            is SettingsScreenAction.SetShowRenamingDialog -> setShowRenamingDialog(action.value)
            is SettingsScreenAction.SetNameFormat -> setNameFormat(action.value)
            SettingsScreenAction.ResetRecordingSettings -> resetRecordingSettings()
            is SettingsScreenAction.SelectRecordingFormat -> selectRecordingFormat(action.value)
            is SettingsScreenAction.SelectSampleRate -> selectSampleRate(action.value)
            is SettingsScreenAction.SelectBitrate -> selectBitrate(action.value)
            is SettingsScreenAction.SelectChannelCount -> selectChannelCount(action.value)
            is SettingsScreenAction.SetMaxRecordingDuration -> setMaxRecordingDuration(action.durationMinutes)
            is SettingsScreenAction.SetAudioSource -> setAudioSource(action.audioSource)
            is SettingsScreenAction.SetRecordAuthorName -> setRecordAuthorName(action.name)
            SettingsScreenAction.ExecuteFirstRun -> executeFirstRun()
            is SettingsScreenAction.SetAppV2 -> handleUseAppV2(action.value)
            SettingsScreenAction.UnlockLegacyAppSwitch -> unlockLegacyAppSwitch()
        }
    }

    fun unlockLegacyAppSwitch() {
        if (!prefs.isLegacyAppUser) {
            prefs.isLegacyAppUser = true
            _state.value = _state.value.copy(isLegacyAppUser = true)
        }
    }

    private fun SettingsState.recordingSettingsUpdated(): SettingsState {
        val settings = this.recordingSettings.firstOrNull { it.recordingFormat.isSelected }
        return this.copy(
            sizePerMin = decimalFormat.format(
                sizeMbPerMin(
                    settings?.recordingFormat?.value,
                    settings?.sampleRates?.firstOrNull { it.isSelected }?.value,
                    settings?.bitRates?.firstOrNull { it.isSelected }?.value,
                    settings?.channelCounts?.firstOrNull { it.isSelected }?.value,
                )
            ),
            recordingSettingsText = recordingSettingsCombinedText(
                settings?.recordingFormat?.value,
                formatRecordingFormat(formatsStrings, settings?.recordingFormat?.value),
                formatSampleRate(sampleRateStrings, settings?.sampleRates?.firstOrNull { it.isSelected }?.value),
                formatBitRate(bitRateStrings, settings?.bitRates?.firstOrNull { it.isSelected }?.value),
                formatChannelCount(channelCountsStrings, settings?.channelCounts?.firstOrNull { it.isSelected }?.value),
            ),
            availableSpaceMills = spaceToRecordingTimeMills(
                this.availableSpaceBytes,
                settings?.recordingFormat?.value,
                settings?.sampleRates?.firstOrNull { it.isSelected }?.value,
                settings?.bitRates?.firstOrNull { it.isSelected }?.value,
                settings?.channelCounts?.firstOrNull { it.isSelected }?.value,
            ),
            availableSpaceBytes = this.availableSpaceBytes,
        )
    }

    private fun <T : Parcelable> ChipItem<T>.updateSelected(value: T): ChipItem<T> {
        return if (this.value == value) {
            this.copy(isSelected = true)
        } else {
            this.copy(isSelected = false)
        }
    }
}

internal sealed class SettingsScreenAction {
    data object InitSettingsScreen : SettingsScreenAction()
    data class SetAppV2(val value: Boolean) : SettingsScreenAction()
    data class SetDynamicTheme(val value: Boolean) : SettingsScreenAction()
    data class SetDarkTheme(val value: Boolean) : SettingsScreenAction()
    data class SetKeepScreenOn(val value: Boolean) : SettingsScreenAction()
    data class SetFloatingRecorderOverlayEnabled(val value: Boolean) : SettingsScreenAction()
    data class SetShowRenamingDialog(val value: Boolean) : SettingsScreenAction()
    data class SetNameFormat(val value: NameFormatItem) : SettingsScreenAction()
    data object ResetRecordingSettings : SettingsScreenAction()
    data class SelectRecordingFormat(val value: RecordingFormat) : SettingsScreenAction()
    data class SelectSampleRate(val value: SampleRate) : SettingsScreenAction()
    data class SelectBitrate(val value: BitRate) : SettingsScreenAction()
    data class SelectChannelCount(val value: ChannelCount) : SettingsScreenAction()
    data class SetMaxRecordingDuration(val durationMinutes: Int) : SettingsScreenAction()
    data class SetAudioSource(val audioSource: AudioSource) : SettingsScreenAction()
    data class SetRecordAuthorName(val name: String) : SettingsScreenAction()
    data object ExecuteFirstRun : SettingsScreenAction()
    data object UnlockLegacyAppSwitch : SettingsScreenAction()
}
