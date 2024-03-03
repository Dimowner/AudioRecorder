package com.dimowner.audiorecorder.v2.settings

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prefs: PrefsV2,
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
            recordingSetting = RecordingSetting(
                recordingFormats = RecordingFormat.entries.toList().mapIndexed { index, format ->
                    ChipItem(
                        id = index,
                        value = format,
                        name = formatsStrings[index],
                        isSelected = format == selectedFormat
                    )
                },
                sampleRates = SampleRate.entries.toList().mapIndexed { index, format ->
                    ChipItem(
                        id = index,
                        value = format,
                        name = sampleRateStrings[index],
                        isSelected = format == selectedSampleRate
                    )
                },
                bitRates = BitRate.entries.toList().mapIndexed { index, format ->
                    ChipItem(
                        id = index,
                        value = format,
                        name = bitRateStrings[index],
                        isSelected = format == selectedBitRate
                    )
                },
                channelCounts = ChannelCount.entries.toList().mapIndexed { index, format ->
                    ChipItem(
                        id = index,
                        value = format,
                        name = channelCountsStrings[index],
                        isSelected = format == selectedChannelCount
                    )
                },
            ),
            sizePerMin = decimalFormat.format(
                sizePerMin(
                    selectedFormat,
                    selectedSampleRate,
                    selectedBitRate,
                    selectedChannelCount
                )
            ),
            recordingSettingsText = recordingSettingsCombinedText(
                formatsStrings, sampleRateStrings, bitRateStrings, channelCountsStrings,
                selectedFormat, selectedSampleRate, selectedBitRate, selectedChannelCount
            ),
            rateAppLink = "link",
            feedbackEmail = "email",
            totalRecordCount = 2,
            totalRecordDuration = 10000,
            availableSpace = 1024 * 1024 * 100,
            appName = "App Name",
            appVersion = "App version 100.0.0",
        )
    )

    val state: LiveData<SettingsState> = _state.asLiveData()

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
            it.copy(recordingSetting = it.recordingSetting.copy(
                recordingFormats = it.recordingSetting.recordingFormats.map { item ->
                    item.updateSelected(DefaultValues.DefaultRecordingFormat)
                },
                sampleRates = it.recordingSetting.sampleRates.map { item ->
                    item.updateSelected(DefaultValues.DefaultSampleRate)
                },
                bitRates = it.recordingSetting.bitRates.map { item ->
                    item.updateSelected(DefaultValues.DefaultBitRate)
                },
                channelCounts = it.recordingSetting.channelCounts.map { item ->
                    item.updateSelected(DefaultValues.DefaultChannelCount)
                }
            )).recordingSettingsUpdated()
        }
    }

    fun selectRecordingFormat(value: RecordingFormat) {
        prefs.settingRecordingFormat = value
        _state.update {
            it.copy(recordingSetting = it.recordingSetting.copy(
                recordingFormats = it.recordingSetting.recordingFormats.map { item ->
                    item.updateSelected(value)
                }
            )).recordingSettingsUpdated()
        }
    }

    fun selectSampleRate(value: SampleRate) {
        prefs.settingSampleRate = value
        _state.update {
            it.copy(recordingSetting = it.recordingSetting.copy(
                sampleRates = it.recordingSetting.sampleRates.map { item ->
                    item.updateSelected(value)
                }
            )).recordingSettingsUpdated()
        }
    }

    fun selectBitrate(value: BitRate) {
        prefs.settingBitrate = value
        _state.update {
            it.copy(recordingSetting = it.recordingSetting.copy(
                bitRates = it.recordingSetting.bitRates.map { item ->
                    item.updateSelected(value)
                }
            )).recordingSettingsUpdated()
        }
    }

    fun selectChannelCount(value: ChannelCount) {
        prefs.settingChannelCount = value
        _state.update {
            it.copy(recordingSetting = it.recordingSetting.copy(
                channelCounts = it.recordingSetting.channelCounts.map { item ->
                    item.updateSelected(value)
                }
            )).recordingSettingsUpdated()
        }
    }

    private fun SettingsState.recordingSettingsUpdated(): SettingsState {
        val settings = this.recordingSetting
        return this.copy(
            sizePerMin = decimalFormat.format(
                sizePerMin(
                    settings.recordingFormats.first { it.isSelected }.value,
                    settings.sampleRates.first { it.isSelected }.value,
                    settings.bitRates.first { it.isSelected }.value,
                    settings.channelCounts.first { it.isSelected }.value,
                )
            ),
            recordingSettingsText = recordingSettingsCombinedText(
                formatsStrings,
                sampleRateStrings,
                bitRateStrings,
                channelCountsStrings,
                settings.recordingFormats.first { it.isSelected }.value,
                settings.sampleRates.first { it.isSelected }.value,
                settings.bitRates.first { it.isSelected }.value,
                settings.channelCounts.first { it.isSelected }.value,
            ),
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