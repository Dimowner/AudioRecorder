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
import com.dimowner.audiorecorder.v2.data.model.isSystemAudioCaptureSupported
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
import com.dimowner.audiorecorder.v2.audio.CaptureDiagnostics
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode

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
    private val captureDiagnostics: CaptureDiagnostics = CaptureDiagnostics(),
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

    init {
        viewModelScope.launch {
            audioRecorderDelegate.captureSessionActive.collect {
                _state.value = _state.value.copy(isRecordingSettingEditable = !recordingSettingsLocked())
            }
        }
        viewModelScope.launch {
            captureDiagnostics.state.collect { evidence ->
                _state.value = _state.value.copy(captureDiagnostics = evidence.route + "\n\nCurrent / last recording:\n" + evidence.session)
            }
        }
    }

    private fun createDefaultState(context: Context): SettingsState {
        return SettingsState(
            isDynamicColors = prefs.isDynamicTheme,
            isDarkTheme = prefs.isDarkTheme,
            isAppV2 = prefs.isAppV2,
            isKeepScreenOn = prefs.isKeepScreenOn,
            isFloatingRecorderOverlayEnabled = prefs.isFloatingRecorderOverlayEnabled,
            isShowRenameDialog = prefs.askToRenameAfterRecordingStopped,
            isRecordingSettingEditable = !recordingSettingsLocked(),
            selectedNameFormat = prefs.settingNamingFormat.toNameFormatItem(prefs.customNameFormat),
            nameFormats = makeNameFormats(prefs.customNameFormat),
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
            selectedAudioSource = prefs.settingAudioSource,
            audioSourceOptions = supportedAudioSources(),
            bluetoothCaptureRoute = prefs.bluetoothCaptureRoute,
            bluetoothAudioSource = prefs.bluetoothAudioSource,
            applyOnlyToBluetoothMic = prefs.applyOnlyToBluetoothMic,
            bluetoothAudioMode = prefs.bluetoothAudioMode,
            inputPreprocessingPolicy = prefs.inputPreprocessingPolicy,
            pcmGainMode = prefs.pcmGainMode,
            bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement,
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
                    isRecordingSettingEditable = !recordingSettingsLocked(),
                    totalRecordCount = recordsCount,
                    totalRecordDuration = recordsDuration,
                    availableSpaceMills = availableTimeMills,
                    availableSpaceBytes = rawAvailableSpaceBytes,
                    // Load the selected audio source from preferences
                    selectedAudioSource = prefs.settingAudioSource,
                    audioSourceOptions = supportedAudioSources(),
                ).withBluetoothSettings()
            }
            recordsDataSource.removeOutdatedTrashRecords()
        }
    }

    fun setAudioSource(audioSource: AudioSource) {
        if (recordingSettingsLocked() || audioSource !in supportedAudioSources()) return
        prefs.settingAudioSource = audioSource
        _state.value = _state.value.copy(selectedAudioSource = audioSource,
            bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement)
        validateFormatForAudioSource(audioSource)
    }

    /** Refresh the bundle in one UI state update, matching the preference editor transaction. */
    private fun SettingsState.withBluetoothSettings(): SettingsState = copy(
        selectedAudioSource = prefs.settingAudioSource,
        bluetoothCaptureRoute = prefs.bluetoothCaptureRoute,
        bluetoothAudioSource = prefs.bluetoothAudioSource,
        applyOnlyToBluetoothMic = prefs.applyOnlyToBluetoothMic,
        bluetoothAudioMode = prefs.bluetoothAudioMode,
        inputPreprocessingPolicy = prefs.inputPreprocessingPolicy,
        pcmGainMode = prefs.pcmGainMode,
        bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement,
    )

    /**
     * System audio is captured with `AudioRecord`, and 3GP is the one format recorded through
     * `MediaRecorder` only, so the pair cannot be honoured. Switching the source moves the format
     * to the default rather than refusing the switch, because the source is what the user just
     * asked for.
     */
    private fun validateFormatForAudioSource(audioSource: AudioSource) {
        if (audioSource.isSystemAudio && prefs.settingRecordingFormat == RecordingFormat.ThreeGp) {
            selectRecordingFormat(DefaultValues.DefaultRecordingFormat)
        }
    }

    /**
     * The audio sources offered on this device. System audio needs Android 10 and the feature
     * flag; the microphone sources are always available.
     */
    private fun supportedAudioSources(): List<AudioSource> =
        AudioSource.entries.filter { !it.isSystemAudio || isSystemAudioCaptureSupported() }

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

    /** Re-reads the name format from preferences, where the name format constructor stores it. */
    private fun refreshNameFormat() {
        val customTokens = prefs.customNameFormat
        _state.value = _state.value.copy(
            selectedNameFormat = prefs.settingNamingFormat.toNameFormatItem(customTokens),
            nameFormats = makeNameFormats(customTokens),
        )
    }

    fun resetRecordingSettings() {
        if (recordingSettingsLocked()) return
        prefs.resetRecordingSettings()
        prefs.settingRecordingFormat = DefaultValues.DefaultRecordingFormat
        prefs.settingSampleRate = DefaultValues.DefaultSampleRate
        prefs.settingBitrate = DefaultValues.DefaultBitRate
        prefs.settingChannelCount = DefaultValues.DefaultChannelCount
        prefs.settingAudioSource = DefaultValues.DefaultAudioSource
        _state.value = _state.value.copy(
            selectedAudioSource = DefaultValues.DefaultAudioSource,
            bluetoothCaptureRoute = BluetoothCaptureRoute.STANDARD_SCO,
            bluetoothAudioSource = AudioSource.DEFAULT,
            applyOnlyToBluetoothMic = true,
            bluetoothAudioMode = BluetoothAudioMode.NORMAL,
            inputPreprocessingPolicy = InputPreprocessingPolicy.SYSTEM_DEFAULT,
            pcmGainMode = PcmGainMode.OFF,
            bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.DISABLED,
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
        if (recordingSettingsLocked()) return
        prefs.settingRecordingFormat = value
        // The mirror of validateFormatForAudioSource(): 3GP has no AudioRecord-backed recorder,
        // so picking it gives up system audio capture.
        if (value == RecordingFormat.ThreeGp && prefs.settingAudioSource.isSystemAudio) {
            prefs.settingAudioSource = DefaultValues.DefaultAudioSource
            _state.value = _state.value.copy(
                selectedAudioSource = DefaultValues.DefaultAudioSource,
                bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement,
            )
        }
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
        ).adjustSelectionToFormat(value)
        .recordingSettingsUpdated()
    }

    /**
     * Ensures the persisted sample rate, bitrate and channel count are all supported by the newly
     * selected [format]. Any unsupported selection is replaced with the format's default, the new
     * value is persisted, and the chips for that format are rebuilt so a valid chip stays selected.
     */
    private fun SettingsState.adjustSelectionToFormat(format: RecordingFormat): SettingsState {
        val config = format.config
        if (!config.isSampleRateSupported(prefs.settingSampleRate)) {
            prefs.settingSampleRate = format.defaultSampleRate()
        }
        if (!config.isChannelCountSupported(prefs.settingChannelCount)) {
            prefs.settingChannelCount = format.defaultChannelCount()
        }
        if (config.hasBitrate && !config.isBitRateSupported(prefs.settingBitrate)) {
            prefs.settingBitrate = DefaultValues.DefaultBitRate
        }
        return this.copy(
            recordingSettings = recordingSettings.map { recordingSetting ->
                if (recordingSetting.recordingFormat.value == format) {
                    recordingSetting.copy(
                        sampleRates = getSampleRates(format, prefs.settingSampleRate, sampleRateStrings),
                        bitRates = getBitRates(format, prefs.settingBitrate, bitRateStrings),
                        channelCounts = getChannelCounts(format, prefs.settingChannelCount, channelCountsStrings),
                    )
                } else {
                    recordingSetting
                }
            }
        )
    }

    fun selectSampleRate(value: SampleRate) {
        if (recordingSettingsLocked()) return
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
        if (recordingSettingsLocked()) return
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
        if (recordingSettingsLocked()) return
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
        if (recordingSettingsLocked()) return
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
        if (action is SettingsScreenAction.SetExperiment && recordingSettingsLocked()) return
        when (action) {
            is SettingsScreenAction.SetExperiment.Enhancement -> {
                prefs.bluetoothVoiceEnhancement = action.value
                _state.value = _state.value.withBluetoothSettings()
            }
            is SettingsScreenAction.SetExperiment.Source -> {
                if (action.value.isSystemAudio) return
                prefs.bluetoothAudioSource = action.value
                _state.value = _state.value.withBluetoothSettings()
            }
            is SettingsScreenAction.SetExperiment.Scope -> {
                prefs.applyOnlyToBluetoothMic = action.onlyBluetooth
                _state.value = _state.value.copy(applyOnlyToBluetoothMic = action.onlyBluetooth)
            }
            is SettingsScreenAction.SetExperiment.Route -> {
                prefs.bluetoothCaptureRoute = action.value
                _state.value = _state.value.copy(bluetoothCaptureRoute = action.value,
                    bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement)
            }
            is SettingsScreenAction.SetExperiment.Mode -> {
                prefs.bluetoothAudioMode = action.value
                _state.value = _state.value.copy(bluetoothAudioMode = action.value,
                    bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement)
            }
            is SettingsScreenAction.SetExperiment.Preprocessing -> {
                prefs.inputPreprocessingPolicy = action.value
                _state.value = _state.value.copy(inputPreprocessingPolicy = action.value,
                    bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement)
            }
            is SettingsScreenAction.SetExperiment.Gain -> {
                prefs.pcmGainMode = action.value
                _state.value = _state.value.copy(pcmGainMode = action.value,
                    bluetoothVoiceEnhancement = prefs.bluetoothVoiceEnhancement)
            }
            SettingsScreenAction.InitSettingsScreen -> initSettings()
            is SettingsScreenAction.SetDynamicTheme -> setDynamicTheme(action.value)
            is SettingsScreenAction.SetDarkTheme -> setDarkTheme(action.value)
            is SettingsScreenAction.SetKeepScreenOn -> setKeepScreenOn(action.value)
            is SettingsScreenAction.SetFloatingRecorderOverlayEnabled -> {
                setFloatingRecorderOverlayEnabled(action.value)
            }
            is SettingsScreenAction.SetShowRenamingDialog -> setShowRenamingDialog(action.value)
            is SettingsScreenAction.SetNameFormat -> setNameFormat(action.value)
            SettingsScreenAction.RefreshNameFormat -> refreshNameFormat()
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

    /** The floating recorder can start after Settings opens, so the cached UI flag is not enough. */
    private fun recordingSettingsLocked(): Boolean = audioRecorderDelegate.isCaptureSessionActive ||
        audioRecorderDelegate.provideAudioRecorder().isRecording

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
    sealed class SetExperiment : SettingsScreenAction() {
        data class Enhancement(val value: BluetoothVoiceEnhancement) : SetExperiment()
        data class Source(val value: AudioSource) : SetExperiment()
        data class Scope(val onlyBluetooth: Boolean) : SetExperiment()
        data class Route(val value: BluetoothCaptureRoute) : SetExperiment()
        data class Mode(val value: BluetoothAudioMode) : SetExperiment()
        data class Preprocessing(val value: InputPreprocessingPolicy) : SetExperiment()
        data class Gain(val value: PcmGainMode) : SetExperiment()
    }
    data object InitSettingsScreen : SettingsScreenAction()
    data class SetAppV2(val value: Boolean) : SettingsScreenAction()
    data class SetDynamicTheme(val value: Boolean) : SettingsScreenAction()
    data class SetDarkTheme(val value: Boolean) : SettingsScreenAction()
    data class SetKeepScreenOn(val value: Boolean) : SettingsScreenAction()
    data class SetFloatingRecorderOverlayEnabled(val value: Boolean) : SettingsScreenAction()
    data class SetShowRenamingDialog(val value: Boolean) : SettingsScreenAction()
    data class SetNameFormat(val value: NameFormatItem) : SettingsScreenAction()
    data object RefreshNameFormat : SettingsScreenAction()
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
