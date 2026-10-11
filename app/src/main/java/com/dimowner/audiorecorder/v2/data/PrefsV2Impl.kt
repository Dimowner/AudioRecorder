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

import android.content.Context
import android.content.SharedPreferences
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_IS_APP_V2
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_IS_LEGACY_APP_USER
import com.dimowner.audiorecorder.AppConstants.PREF_NAME
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceConfiguration
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.NameFormatToken
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.model.convertToBitRate
import com.dimowner.audiorecorder.v2.data.model.convertToChannelCount
import com.dimowner.audiorecorder.v2.data.model.convertToNameFormat
import com.dimowner.audiorecorder.v2.data.model.convertToNameFormatTokens
import com.dimowner.audiorecorder.v2.data.model.serialize
import com.dimowner.audiorecorder.v2.data.model.convertToRecordingFormat
import com.dimowner.audiorecorder.v2.data.model.convertToSampleRate
import com.dimowner.audiorecorder.v2.data.model.convertToSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_ACTIVE_RECORD
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_IS_FIRST_RUN
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_KEEP_SCREEN_ON
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_RECORD_COUNTER
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_BITRATE
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_CHANNEL_COUNT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_NAMING_FORMAT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_RECORDING_FORMAT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_SAMPLE_RATE
import com.dimowner.audiorecorder.AppConstantsV2

/**
 * App V2 preferences implementation
 */
@Singleton
class PrefsV2Impl @Inject internal constructor(@ApplicationContext context: Context) : PrefsV2 {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init { migrateBluetoothSource() }

    private fun migrateBluetoothSource() {
        if (sharedPreferences.contains(PREF_KEY_BLUETOOTH_AUDIO_SOURCE)) return
        // Snapshot the old shared source once. Subsequent phone/source edits must not leak into
        // the Bluetooth profile. Playback capture is selected exclusively by the external source.
        val oldSource = settingAudioSource
        val legacy = BluetoothVoiceConfiguration(oldSource, bluetoothCaptureRoute,
            readEnum(PREF_KEY_BLUETOOTH_AUDIO_MODE, BluetoothAudioMode.IN_COMMUNICATION),
            inputPreprocessingPolicy, pcmGainMode)
        val label = sharedPreferences.getString(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT, null)
        val oldDisabled = legacy == BluetoothVoiceConfiguration.STANDARD.copy(mode = BluetoothAudioMode.IN_COMMUNICATION) &&
            (label == null || label == BluetoothVoiceEnhancement.DISABLED.name)
        val hasLegacySettings = listOf(PREF_KEY_SETTING_AUDIO_SOURCE, PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT,
            PREF_KEY_BLUETOOTH_CAPTURE_ROUTE, PREF_KEY_BLUETOOTH_AUDIO_MODE,
            PREF_KEY_INPUT_PREPROCESSING_POLICY, PREF_KEY_PCM_GAIN_MODE).any(sharedPreferences::contains)
        sharedPreferences.edit {
            putInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, oldSource.takeUnless { it.isSystemAudio }?.value ?: AudioSource.DEFAULT.value)
            if (oldDisabled) putString(PREF_KEY_BLUETOOTH_AUDIO_MODE, BluetoothAudioMode.NORMAL.name)
            else if (hasLegacySettings && !sharedPreferences.contains(PREF_KEY_BLUETOOTH_AUDIO_MODE)) {
                // A custom setup could have relied on the previous implicit IN_COMMUNICATION.
                putString(PREF_KEY_BLUETOOTH_AUDIO_MODE, legacy.mode.name)
            }
        }
    }

    private val _isDarkThemeFlow = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_KEY_IS_DARK_THEME, DefaultValues.IS_DARK_THEME)
    )

    override val isDarkThemeFlow: StateFlow<Boolean> = _isDarkThemeFlow.asStateFlow()

    private val _isDynamicThemeFlow = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_KEY_IS_DYNAMIC_THEME, DefaultValues.IS_DYNAMIC_THEME)
    )

    override val isDynamicThemeFlow: StateFlow<Boolean> = _isDynamicThemeFlow.asStateFlow()

    override val isFirstRun: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_RUN, true)

    override fun confirmFirstRunExecuted() {
        sharedPreferences.edit {
            putBoolean(PREF_KEY_IS_FIRST_RUN, false) //Set to False, because next app start won't be first
        }
    }

    override var isLocalStorageInfoShown: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_LOCAL_STORAGE_INFO_SHOWN, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_LOCAL_STORAGE_INFO_SHOWN, value)
            }
        }

    override var askToRenameAfterRecordingStopped: Boolean
        get() = sharedPreferences.getBoolean(
            PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED, DefaultValues.IS_ASK_TO_RENAME
        )
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED, value)
            }
        }

    override var saveDescriptionToFile: Boolean
        get() = sharedPreferences.getBoolean(
            PREF_KEY_SAVE_DESCRIPTION_TO_FILE, DefaultValues.IS_SAVE_DESCRIPTION_TO_FILE
        )
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_SAVE_DESCRIPTION_TO_FILE, value)
            }
        }

    override var alwaysUseBluetoothMic: Boolean
        get() = sharedPreferences.getBoolean(
            PREF_KEY_ALWAYS_USE_BLUETOOTH_MIC, DefaultValues.IS_ALWAYS_USE_BLUETOOTH_MIC
        )
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_ALWAYS_USE_BLUETOOTH_MIC, value)
            }
        }

    override var activeRecordId: Long
        get() = sharedPreferences.getLong(PREF_KEY_ACTIVE_RECORD, -1)
        set(value) {
            sharedPreferences.edit {
                putLong(PREF_KEY_ACTIVE_RECORD, value)
            }
        }

    override var recordedRecordId: Long
        get() = sharedPreferences.getLong(PREF_KEY_RECORDED_RECORD_ID, -1)
        set(value) {
            sharedPreferences.edit {
                putLong(PREF_KEY_RECORDED_RECORD_ID, value)
            }
        }

    override var recordedRecordPartCounter: Int
        get() = sharedPreferences.getInt(PREF_KEY_RECORDED_RECORD_PART_COUNTER, 0)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_RECORDED_RECORD_PART_COUNTER, value)
            }
        }

    override var recordedRecordBaseName: String?
        get() = sharedPreferences.getString(PREF_KEY_RECORDED_RECORD_BASE_NAME, null)
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_RECORDED_RECORD_BASE_NAME, value)
            }
        }

    override val recordCounter: Long
        get() = sharedPreferences.getLong(PREF_KEY_RECORD_COUNTER, 1)

    override fun incrementRecordCounter() {
        sharedPreferences.edit {
            putLong(PREF_KEY_RECORD_COUNTER, recordCounter + 1)
        }
    }

    override var isKeepScreenOn: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_KEEP_SCREEN_ON, DefaultValues.IS_KEEP_SCREEN_ON)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_KEEP_SCREEN_ON, value)
            }
        }

    override var isFloatingRecorderOverlayEnabled: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_FLOATING_RECORDER_OVERLAY_ENABLED, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_FLOATING_RECORDER_OVERLAY_ENABLED, value)
            }
        }

    override var floatingRecorderOverlayX: Int
        get() = sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_X, -1)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_X, value)
            }
        }

    override var floatingRecorderOverlayY: Int
        get() = sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_Y, -1)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_Y, value)
            }
        }

    override var floatingRecorderOverlaySize: Int
        get() = sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_SIZE, -1)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_OVERLAY_SIZE, value)
            }
        }

    override var floatingRecorderRenameOverlayX: Int
        get() = sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_X, -1)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_X, value)
            }
        }

    override var floatingRecorderRenameOverlayY: Int
        get() = sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_Y, -1)
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_Y, value)
            }
        }

    override var floatingRecorderRenameSpeechMode: RenameSpeechMode
        get() = RenameSpeechMode.fromPersistedValue(
            sharedPreferences.getInt(PREF_KEY_FLOATING_RECORDER_RENAME_SPEECH_MODE, RenameSpeechMode.Append.persistedValue)
        )
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_FLOATING_RECORDER_RENAME_SPEECH_MODE, value.persistedValue)
            }
        }

    override var recordsSortOrder: SortOrder
        get() = sharedPreferences.getString(
            PREF_KEY_RECORDS_SORT_ORDER,
            SortOrder.DateAsc.toString()
        )?.convertToSortOrder() ?: SortOrder.DateAsc
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_RECORDS_SORT_ORDER, value.toString())
            }
        }

    override var isDynamicTheme: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_DYNAMIC_THEME, DefaultValues.IS_DYNAMIC_THEME)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_DYNAMIC_THEME, value)
            }
            _isDynamicThemeFlow.value = value
        }

    override var isDarkTheme: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_DARK_THEME, DefaultValues.IS_DARK_THEME)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_DARK_THEME, value)
            }
            _isDarkThemeFlow.value = value
        }

    override var isAppV2: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_APP_V2, DefaultValues.IS_APP_V2)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_APP_V2, value)
            }
        }

    override var isLegacyAppUser: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_LEGACY_APP_USER, DefaultValues.IS_LEGACY_APP_USER)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_LEGACY_APP_USER, value)
            }
        }

    override var settingNamingFormat: NameFormat
        get() = sharedPreferences.getString(
            PREF_KEY_SETTING_NAMING_FORMAT,
            DefaultValues.DefaultNameFormat.name
        )?.convertToNameFormat() ?: DefaultValues.DefaultNameFormat
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_SETTING_NAMING_FORMAT, value.name)
            }
        }

    override var customNameFormat: List<NameFormatToken>
        get() = sharedPreferences.getString(PREF_KEY_CUSTOM_NAME_FORMAT, null)
            ?.convertToNameFormatTokens() ?: emptyList()
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_CUSTOM_NAME_FORMAT, value.serialize())
            }
        }

    override var settingRecordingFormat: RecordingFormat
        get() = sharedPreferences.getString(
            PREF_KEY_SETTING_RECORDING_FORMAT,
            RecordingFormat.M4a.value
        )?.convertToRecordingFormat() ?: RecordingFormat.M4a
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_SETTING_RECORDING_FORMAT, value.value)
            }
        }

    override var settingSampleRate: SampleRate
        get() = sharedPreferences.getInt(
            PREF_KEY_SETTING_SAMPLE_RATE,
            DefaultValues.DefaultSampleRate.value
        ).convertToSampleRate() ?: DefaultValues.DefaultSampleRate
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_SETTING_SAMPLE_RATE, value.value)
            }
        }

    override var settingBitrate: BitRate
        get() = sharedPreferences.getInt(
            PREF_KEY_SETTING_BITRATE,
            DefaultValues.DefaultBitRate.value
        ).convertToBitRate() ?: DefaultValues.DefaultBitRate
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_SETTING_BITRATE, value.value)
            }
        }

    override var settingChannelCount: ChannelCount
        get() = sharedPreferences.getInt(
            PREF_KEY_SETTING_CHANNEL_COUNT,
            DefaultValues.DefaultChannelCount.value
        ).convertToChannelCount() ?: DefaultValues.DefaultChannelCount
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_SETTING_CHANNEL_COUNT, value.value)
            }
        }

    override var settingAudioSource: AudioSource
        get() = sharedPreferences.getInt(
            PREF_KEY_SETTING_AUDIO_SOURCE,
            DefaultValues.DefaultAudioSource.value
        ).let { AudioSource.fromValue(it) }
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_SETTING_AUDIO_SOURCE, value.value)
            }
        }

    override var bluetoothAudioSource: AudioSource
        get() = AudioSource.fromValue(sharedPreferences.getInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, AudioSource.DEFAULT.value))
            .takeUnless { it.isSystemAudio } ?: AudioSource.DEFAULT
        set(value) {
            require(!value.isSystemAudio) { "System playback is not a Bluetooth microphone source" }
            val changed = value != bluetoothAudioSource
            sharedPreferences.edit {
                putInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, value.value)
                if (changed) putString(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT, BluetoothVoiceEnhancement.CUSTOM.name)
            }
        }

    override var applyOnlyToBluetoothMic: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_APPLY_ONLY_TO_BLUETOOTH_MIC, true)
        set(value) { sharedPreferences.edit { putBoolean(PREF_KEY_APPLY_ONLY_TO_BLUETOOTH_MIC, value) } }

    override var bluetoothCaptureRoute: BluetoothCaptureRoute
        get() = readEnum(PREF_KEY_BLUETOOTH_CAPTURE_ROUTE, BluetoothCaptureRoute.STANDARD_SCO)
        set(value) = writeExperimentEnum(PREF_KEY_BLUETOOTH_CAPTURE_ROUTE, value, bluetoothCaptureRoute)

    override var bluetoothAudioMode: BluetoothAudioMode
        get() = readEnum(PREF_KEY_BLUETOOTH_AUDIO_MODE, BluetoothAudioMode.NORMAL)
        set(value) = writeExperimentEnum(PREF_KEY_BLUETOOTH_AUDIO_MODE, value, bluetoothAudioMode)

    override var inputPreprocessingPolicy: InputPreprocessingPolicy
        get() = readEnum(PREF_KEY_INPUT_PREPROCESSING_POLICY, InputPreprocessingPolicy.SYSTEM_DEFAULT)
        set(value) = writeExperimentEnum(PREF_KEY_INPUT_PREPROCESSING_POLICY, value, inputPreprocessingPolicy)

    override var pcmGainMode: PcmGainMode
        get() = readEnum(PREF_KEY_PCM_GAIN_MODE, PcmGainMode.OFF)
        set(value) = writeExperimentEnum(PREF_KEY_PCM_GAIN_MODE, value, pcmGainMode)

    override var bluetoothVoiceEnhancement: BluetoothVoiceEnhancement
        get() {
            val current = BluetoothVoiceConfiguration(bluetoothAudioSource, bluetoothCaptureRoute,
                bluetoothAudioMode, inputPreprocessingPolicy, pcmGainMode)
            val inferred = BluetoothVoiceEnhancement.fromConfiguration(current)
            val stored = readEnum(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT, inferred)
            // Explicit Custom is kept even when its values happen to equal a preset. Conversely,
            // a stale label restored from backup must never promise an unapplied fixed preset.
            return if (stored == BluetoothVoiceEnhancement.CUSTOM || stored.configuration == current) stored
                else BluetoothVoiceEnhancement.CUSTOM
        }
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT, value.name)
                value.configuration?.let { configuration ->
                    // A single editor publishes all five choices together. Calling the individual
                    // setters here would expose intermediate combinations and mark them Custom.
                    putInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, configuration.source.value)
                    putString(PREF_KEY_BLUETOOTH_CAPTURE_ROUTE, configuration.route.name)
                    putString(PREF_KEY_BLUETOOTH_AUDIO_MODE, configuration.mode.name)
                    putString(PREF_KEY_INPUT_PREPROCESSING_POLICY, configuration.preprocessing.name)
                    putString(PREF_KEY_PCM_GAIN_MODE, configuration.gain.name)
                }
            }
        }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val name = sharedPreferences.getString(key, null) ?: return default
        // Enum's name lookup is cached, unlike scanning entries on every preference read.
        // Unknown names can be left by newer app versions; fall back without rewriting them.
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    private fun writeExperimentEnum(key: String, value: Enum<*>, previous: Enum<*>) {
        // Ordinals are not stable when a future release inserts another experimental option.
        sharedPreferences.edit {
            putString(key, value.name)
            if (value != previous) putString(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT, BluetoothVoiceEnhancement.CUSTOM.name)
        }
    }

    override var maxRecordingDurationMills: Int
        get() = sharedPreferences.getInt(
            PREF_KEY_MAX_RECORDING_DURATION_MILLS,
            AppConstantsV2.DEFAULT_MAX_RECORDING_DURATION_MS
        )
        set(value) {
            sharedPreferences.edit {
                putInt(PREF_KEY_MAX_RECORDING_DURATION_MILLS, value)
            }
        }

    override var recordAuthorName: String
        get() = sharedPreferences.getString(
            PREF_KEY_RECORD_AUTHOR_NAME,
            DefaultValues.DEFAULT_RECORD_AUTHOR_NAME
        ) ?: DefaultValues.DEFAULT_RECORD_AUTHOR_NAME
        set(value) {
            sharedPreferences.edit {
                putString(PREF_KEY_RECORD_AUTHOR_NAME, value)
            }
        }

    override fun resetRecordingSettings() {
        sharedPreferences.edit {
            remove(PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT)
            remove(PREF_KEY_BLUETOOTH_CAPTURE_ROUTE)
            remove(PREF_KEY_BLUETOOTH_AUDIO_MODE)
            remove(PREF_KEY_INPUT_PREPROCESSING_POLICY)
            remove(PREF_KEY_PCM_GAIN_MODE)
            remove(PREF_KEY_SETTING_AUDIO_SOURCE)
            putInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, AudioSource.DEFAULT.value)
            remove(PREF_KEY_APPLY_ONLY_TO_BLUETOOTH_MIC)
            putString(
                PREF_KEY_SETTING_RECORDING_FORMAT,
                DefaultValues.DefaultRecordingFormat.value
            )
            putInt(
                PREF_KEY_SETTING_SAMPLE_RATE,
                DefaultValues.DefaultSampleRate.value
            )
            putInt(
                PREF_KEY_SETTING_BITRATE,
                DefaultValues.DefaultBitRate.value
            )
            putInt(
                PREF_KEY_SETTING_CHANNEL_COUNT,
                DefaultValues.DefaultChannelCount.value
            )
        }
    }

    override fun fullPreferenceReset() {
        sharedPreferences.edit {
            clear()
            putInt(PREF_KEY_BLUETOOTH_AUDIO_SOURCE, AudioSource.DEFAULT.value)
        }
    }

    companion object {
        private const val PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED =
            "ask_to_rename_after_recording_stopped"
        private const val PREF_KEY_RECORDED_RECORD_ID = "recorded_record_id"
        private const val PREF_KEY_RECORDED_RECORD_PART_COUNTER = "recorded_record_part_counter"
        private const val PREF_KEY_RECORDED_RECORD_BASE_NAME = "recorded_record_base_name"
        private const val PREF_KEY_RECORDS_SORT_ORDER = "pref_records_sort_order"
        private const val PREF_KEY_IS_DYNAMIC_THEME = "pref_is_dynamic_theme"
        private const val PREF_KEY_IS_DARK_THEME = "pref_is_dark_theme"
        private const val PREF_KEY_MAX_RECORDING_DURATION_MILLS = "pref_key_max_recording_duration_mills"
        private const val PREF_KEY_SETTING_AUDIO_SOURCE = "pref_key_setting_audio_source"
        private const val PREF_KEY_BLUETOOTH_AUDIO_SOURCE = "pref_key_bluetooth_audio_source"
        private const val PREF_KEY_APPLY_ONLY_TO_BLUETOOTH_MIC = "pref_key_apply_only_to_bluetooth_mic"
        private const val PREF_KEY_BLUETOOTH_CAPTURE_ROUTE = "pref_key_bluetooth_capture_route"
        private const val PREF_KEY_BLUETOOTH_AUDIO_MODE = "pref_key_bluetooth_audio_mode"
        private const val PREF_KEY_INPUT_PREPROCESSING_POLICY = "pref_key_input_preprocessing_policy"
        private const val PREF_KEY_PCM_GAIN_MODE = "pref_key_pcm_gain_mode"
        private const val PREF_KEY_BLUETOOTH_VOICE_ENHANCEMENT = "pref_key_bluetooth_voice_enhancement"
        private const val PREF_KEY_RECORD_AUTHOR_NAME = "pref_key_record_author_name"
        private const val PREF_KEY_FLOATING_RECORDER_OVERLAY_ENABLED =
            "pref_key_floating_recorder_overlay_enabled"
        private const val PREF_KEY_FLOATING_RECORDER_OVERLAY_X = "pref_key_floating_recorder_overlay_x"
        private const val PREF_KEY_FLOATING_RECORDER_OVERLAY_Y = "pref_key_floating_recorder_overlay_y"
        private const val PREF_KEY_FLOATING_RECORDER_OVERLAY_SIZE = "pref_key_floating_recorder_overlay_size"
        private const val PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_X =
            "pref_key_floating_recorder_rename_overlay_x"
        private const val PREF_KEY_FLOATING_RECORDER_RENAME_OVERLAY_Y =
            "pref_key_floating_recorder_rename_overlay_y"
        private const val PREF_KEY_FLOATING_RECORDER_RENAME_SPEECH_MODE =
            "pref_key_floating_recorder_rename_speech_mode"
        private const val PREF_KEY_SAVE_DESCRIPTION_TO_FILE = "pref_key_save_description_to_file"
        private const val PREF_KEY_IS_LOCAL_STORAGE_INFO_SHOWN = "pref_key_is_local_storage_info_shown"
        private const val PREF_KEY_CUSTOM_NAME_FORMAT = "pref_key_custom_name_format"
        private const val PREF_KEY_ALWAYS_USE_BLUETOOTH_MIC = "pref_key_always_use_bluetooth_mic"
    }
}
