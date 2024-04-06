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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.app.formatBitRate
import com.dimowner.audiorecorder.v2.app.formatChannelCount
import com.dimowner.audiorecorder.v2.app.formatRecordingFormat
import com.dimowner.audiorecorder.v2.app.formatSampleRate
import com.dimowner.audiorecorder.v2.app.recordingSettingsCombinedText
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
//    savedStateHandle: SavedStateHandle,
    private val prefs: PrefsV2,
    private val recordsDataSource: RecordsDataSource,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

    private val _state: MutableStateFlow<SettingsState> = MutableStateFlow(
        //TODO: Move default state creation into a function
        SettingsState(
            isDynamicColors = prefs.isDynamicTheme,
            isDarkTheme = prefs.isDarkTheme,
            isKeepScreenOn = prefs.isKeepScreenOn,
            isShowRenameDialog = prefs.askToRenameAfterRecordingStopped,
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
            rateAppLink = "link",//TODO: Fix hardcoded value
            feedbackEmail = "email",//TODO: Fix hardcoded value
            totalRecordCount = 0,
            totalRecordDuration = 0,
            availableSpace = 0,
            appName = context.getString(R.string.app_name),
            appVersion = context.getString(R.string.version, AndroidUtils.getAppVersion(context)),
        )
    )

    val state: LiveData<SettingsState> = _state.asLiveData()

    fun initSettings() {
        viewModelScope.launch(ioDispatcher) {
            val recordsCount = recordsDataSource.getRecordsCount()
            val recordsDuration = recordsDataSource.getRecordTotalDuration()
            withContext(mainDispatcher) {
                _state.update {
                    it.copy(totalRecordCount = recordsCount, totalRecordDuration = recordsDuration)
                }
            }
        }
    }

    fun setDarkTheme(value: Boolean) {
        prefs.isDarkTheme = value
    }

    fun setDynamicTheme(value: Boolean) {
        prefs.isDynamicTheme = value
    }

    fun setKeepScreenOn(value: Boolean) {
        prefs.isKeepScreenOn = value
        _state.update {
            it.copy(isKeepScreenOn = value)
        }
    }

    fun setShowRenamingDialog(value: Boolean) {
        prefs.askToRenameAfterRecordingStopped = value
        _state.update {
            it.copy(isShowRenameDialog = value)
        }
    }

    fun setNameFormat(value: NameFormatItem) {
        prefs.settingNamingFormat = value.nameFormat
        _state.update {
            it.copy(selectedNameFormat = value)
        }
    }

    fun resetRecordingSettings() {
        prefs.settingRecordingFormat = DefaultValues.DefaultRecordingFormat
        prefs.settingSampleRate = DefaultValues.DefaultSampleRate
        prefs.settingBitrate = DefaultValues.DefaultBitRate
        prefs.settingChannelCount = DefaultValues.DefaultChannelCount
        _state.update {
            it.copy(recordingSettings = it.recordingSettings.map { formatSetting ->
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
    }

    fun selectRecordingFormat(value: RecordingFormat) {
        prefs.settingRecordingFormat = value
        _state.update { settingsState ->
            settingsState.copy(recordingSettings = settingsState.recordingSettings.map { item ->
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
            })
                .validate3GpSelectedAndAdjust(value)
                .recordingSettingsUpdated()
        }
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
        _state.update { settingsState ->
            settingsState.copy(
                recordingSettings = settingsState.recordingSettings.map { formatSetting ->
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
    }

    fun selectBitrate(value: BitRate) {
        prefs.settingBitrate = value
        _state.update { settingsState ->
            settingsState.copy(
                recordingSettings = settingsState.recordingSettings.map { formatSetting ->
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
    }

    fun selectChannelCount(value: ChannelCount) {
        prefs.settingChannelCount = value
        _state.update { settingsState ->
            settingsState.copy(
                recordingSettings = settingsState.recordingSettings.map { formatSetting ->
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
            )
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
