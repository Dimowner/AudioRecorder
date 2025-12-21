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
import com.dimowner.audiorecorder.AppConstants.PREF_NAME
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.model.convertToBitRate
import com.dimowner.audiorecorder.v2.data.model.convertToChannelCount
import com.dimowner.audiorecorder.v2.data.model.convertToNameFormat
import com.dimowner.audiorecorder.v2.data.model.convertToRecordingFormat
import com.dimowner.audiorecorder.v2.data.model.convertToSampleRate
import com.dimowner.audiorecorder.v2.data.model.convertToSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_IS_FIRST_RUN
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_KEEP_SCREEN_ON
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_RECORD_COUNTER
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_BITRATE
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_CHANNEL_COUNT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_NAMING_FORMAT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_RECORDING_FORMAT
import com.dimowner.audiorecorder.AppConstants.PREF_KEY_SETTING_SAMPLE_RATE

/**
 * App V2 preferences implementation
 */
@Singleton
class PrefsV2Impl @Inject internal constructor(@ApplicationContext context: Context) : PrefsV2 {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override val isFirstRun: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_RUN, true)

    override fun confirmFirstRunExecuted() {
        sharedPreferences.edit {
            putBoolean(PREF_KEY_IS_FIRST_RUN, false) //Set to False, because next app start won't be first
        }
    }

    override var askToRenameAfterRecordingStopped: Boolean
        get() = sharedPreferences.getBoolean(
            PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED, DefaultValues.isAskToRename
        )
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED, value)
            }
        }
    override var activeRecordId: Long
        get() = sharedPreferences.getLong(PREF_KEY_ACTIVE_RECORD_ID, -1)
        set(value) {
            sharedPreferences.edit {
                putLong(PREF_KEY_ACTIVE_RECORD_ID, value)
            }
        }

    override var recordedRecordId: Long
        get() = sharedPreferences.getLong(PREF_KEY_RECORDED_RECORD_ID, -1)
        set(value) {
            sharedPreferences.edit {
                putLong(PREF_KEY_RECORDED_RECORD_ID, value)
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
        get() = sharedPreferences.getBoolean(PREF_KEY_KEEP_SCREEN_ON, DefaultValues.isKeepScreenOn)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_KEEP_SCREEN_ON, value)
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
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_DYNAMIC_THEME, DefaultValues.isDynamicTheme)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_DYNAMIC_THEME, value)
            }
        }

    override var isDarkTheme: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_DARK_THEME, DefaultValues.isDarkTheme)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_DARK_THEME, value)
            }
        }

    override var isAppV2: Boolean
        get() = sharedPreferences.getBoolean(PREF_KEY_IS_APP_V2, DefaultValues.isAppV2)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_KEY_IS_APP_V2, value)
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

    override fun resetRecordingSettings() {
        sharedPreferences.edit {
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
        }
    }

    companion object {
        private const val PREF_KEY_ASK_TO_RENAME_AFTER_RECORDING_STOPPED =
            "ask_to_rename_after_recording_stopped"
        private const val PREF_KEY_ACTIVE_RECORD_ID = "active_record_id"
        private const val PREF_KEY_RECORDED_RECORD_ID = "recorded_record_id"
        private const val PREF_KEY_RECORDS_SORT_ORDER = "pref_records_sort_order"
        private const val PREF_KEY_IS_DYNAMIC_THEME = "pref_is_dynamic_theme"
        private const val PREF_KEY_IS_DARK_THEME = "pref_is_dark_theme"
    }
}
