/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dimowner.audiorecorder.AppConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PrefsImplTest {

    private lateinit var prefs: PrefsImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences before each test to ensure isolation
        context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = PrefsImpl.getInstance(context)
    }

    @After
    fun tearDown() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── First run ───────────────────────────────────────────────────────────────

    @Test
    fun isFirstRun_returnsTrueByDefault() {
        assertTrue(prefs.isFirstRun)
    }

    @Test
    fun firstRunExecuted_setsFirstRunToFalse() {
        prefs.firstRunExecuted()
        assertFalse(prefs.isFirstRun)
    }

    // ── Store dir public ────────────────────────────────────────────────────────

    @Test
    fun isStoreDirPublic_returnsFalseByDefault() {
        assertFalse(prefs.isStoreDirPublic)
    }

    @Test
    fun setStoreDirPublic_persistsValue() {
        prefs.setStoreDirPublic(true)
        assertTrue(prefs.isStoreDirPublic)

        prefs.setStoreDirPublic(false)
        assertFalse(prefs.isStoreDirPublic)
    }

    // ── Database migrated to Room ───────────────────────────────────────────────

    @Test
    fun isDatabaseMigratedToRoom_returnsFalseByDefault() {
        assertFalse(prefs.isDatabaseMigratedToRoom)
    }

    @Test
    fun setDatabaseMigratedToRoom_persistsValue() {
        prefs.setDatabaseMigratedToRoom(true)
        assertTrue(prefs.isDatabaseMigratedToRoom)

        prefs.setDatabaseMigratedToRoom(false)
        assertFalse(prefs.isDatabaseMigratedToRoom)
    }

    // ── Show directory setting ──────────────────────────────────────────────────

    @Test
    fun isShowDirectorySetting_returnsTrueByDefault() {
        assertTrue(prefs.isShowDirectorySetting)
    }

    // ── Ask to rename after stop recording ──────────────────────────────────────

    @Test
    fun isAskToRenameAfterStopRecording_returnsFalseWhenNotSet() {
        // When the key is not present, contains() returns false, so overall returns false
        assertFalse(prefs.isAskToRenameAfterStopRecording)
    }

    @Test
    fun hasAskToRenameAfterStopRecordingSetting_returnsFalseByDefault() {
        assertFalse(prefs.hasAskToRenameAfterStopRecordingSetting())
    }

    @Test
    fun setAskToRenameAfterStopRecording_persistsValue() {
        prefs.setAskToRenameAfterStopRecording(true)
        assertTrue(prefs.isAskToRenameAfterStopRecording)
        assertTrue(prefs.hasAskToRenameAfterStopRecordingSetting())

        prefs.setAskToRenameAfterStopRecording(false)
        assertFalse(prefs.isAskToRenameAfterStopRecording)
    }

    // ── Public storage migration ────────────────────────────────────────────────

    @Test
    fun isPublicStorageMigrated_returnsFalseByDefault() {
        assertFalse(prefs.isPublicStorageMigrated)
    }

    @Test
    fun setPublicStorageMigrated_persistsValue() {
        prefs.setPublicStorageMigrated(true)
        assertTrue(prefs.isPublicStorageMigrated)

        prefs.setPublicStorageMigrated(false)
        assertFalse(prefs.isPublicStorageMigrated)
    }

    @Test
    fun getLastPublicStorageMigrationAsked_returnsZeroByDefault() {
        assertEquals(0L, prefs.lastPublicStorageMigrationAsked)
    }

    @Test
    fun setLastPublicStorageMigrationAsked_persistsValue() {
        val timestamp = 1_700_000_000_000L
        prefs.setLastPublicStorageMigrationAsked(timestamp)
        assertEquals(timestamp, prefs.lastPublicStorageMigrationAsked)
    }

    // ── Active record ───────────────────────────────────────────────────────────

    @Test
    fun getActiveRecord_returnsMinusOneByDefault() {
        assertEquals(-1L, prefs.activeRecord)
    }

    @Test
    fun setActiveRecord_persistsValue() {
        prefs.setActiveRecord(42L)
        assertEquals(42L, prefs.activeRecord)
    }

    // ── Record counter ──────────────────────────────────────────────────────────

    @Test
    fun getRecordCounter_returnsZeroByDefault() {
        assertEquals(0L, prefs.recordCounter)
    }

    @Test
    fun incrementRecordCounter_incrementsByOne() {
        prefs.incrementRecordCounter()
        assertEquals(1L, prefs.recordCounter)

        prefs.incrementRecordCounter()
        assertEquals(2L, prefs.recordCounter)

        prefs.incrementRecordCounter()
        assertEquals(3L, prefs.recordCounter)
    }

    // ── Keep screen on ──────────────────────────────────────────────────────────

    @Test
    fun isKeepScreenOn_returnsFalseByDefault() {
        assertFalse(prefs.isKeepScreenOn)
    }

    @Test
    fun setKeepScreenOn_persistsValue() {
        prefs.setKeepScreenOn(true)
        assertTrue(prefs.isKeepScreenOn)

        prefs.setKeepScreenOn(false)
        assertFalse(prefs.isKeepScreenOn)
    }

    // ── Records order ───────────────────────────────────────────────────────────

    @Test
    fun getRecordsOrder_returnsSortDateByDefault() {
        assertEquals(AppConstants.SORT_DATE, prefs.recordsOrder)
    }

    @Test
    fun setRecordOrder_persistsValue() {
        prefs.setRecordOrder(AppConstants.SORT_NAME)
        assertEquals(AppConstants.SORT_NAME, prefs.recordsOrder)

        prefs.setRecordOrder(AppConstants.SORT_DURATION_DESC)
        assertEquals(AppConstants.SORT_DURATION_DESC, prefs.recordsOrder)
    }

    // ── Migrated DB3 ────────────────────────────────────────────────────────────

    @Test
    fun isMigratedDb3_returnsFalseByDefault() {
        assertFalse(prefs.isMigratedDb3)
    }

    @Test
    fun migrateDb3Finished_setsFlag() {
        prefs.migrateDb3Finished()
        assertTrue(prefs.isMigratedDb3)
    }

    // ── App V2 ──────────────────────────────────────────────────────────────────

    @Test
    fun isAppV2_returnsFalseByDefault() {
        assertFalse(prefs.isAppV2)
    }

    @Test
    fun setAppV2_persistsValue() {
        prefs.setAppV2(false)
        assertFalse(prefs.isAppV2)

        prefs.setAppV2(true)
        assertTrue(prefs.isAppV2)
    }

    // ── Legacy app user ─────────────────────────────────────────────────────────

    @Test
    fun isLegacyAppUser_returnsFalseByDefault() {
        assertFalse(prefs.isLegacyAppUser)
    }

    @Test
    fun setLegacyAppUser_persistsTrue() {
        prefs.setLegacyAppUser(true)
        assertTrue(prefs.isLegacyAppUser)
    }

    @Test
    fun setLegacyAppUser_persistsFalse() {
        prefs.setLegacyAppUser(true)
        prefs.setLegacyAppUser(false)
        assertFalse(prefs.isLegacyAppUser)
    }

    // ── Switch to V2 dialog dismissed time ─────────────────────────────────────

    @Test
    fun getSwitchToV2DialogDismissedTime_returnsZeroByDefault() {
        assertEquals(0L, prefs.switchToV2DialogDismissedTime)
    }

    @Test
    fun setSwitchToV2DialogDismissedTime_persistsValue() {
        val timestamp = 1_750_000_000_000L
        prefs.setSwitchToV2DialogDismissedTime(timestamp)
        assertEquals(timestamp, prefs.switchToV2DialogDismissedTime)
    }

    @Test
    fun setSwitchToV2DialogDismissedTime_canBeOverwritten() {
        prefs.setSwitchToV2DialogDismissedTime(1_000_000L)
        prefs.setSwitchToV2DialogDismissedTime(2_000_000L)
        assertEquals(2_000_000L, prefs.switchToV2DialogDismissedTime)
    }

    // ── Theme color ─────────────────────────────────────────────────────────────

    @Test
    fun getSettingThemeColor_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_THEME_COLOR, prefs.settingThemeColor)
    }

    @Test
    fun setSettingThemeColor_persistsValue() {
        prefs.setSettingThemeColor(AppConstants.THEME_PURPLE)
        assertEquals(AppConstants.THEME_PURPLE, prefs.settingThemeColor)

        prefs.setSettingThemeColor(AppConstants.THEME_RED)
        assertEquals(AppConstants.THEME_RED, prefs.settingThemeColor)
    }

    // ── Naming format ───────────────────────────────────────────────────────────

    @Test
    fun getSettingNamingFormat_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_NAME_FORMAT, prefs.settingNamingFormat)
    }

    @Test
    fun setSettingNamingFormat_persistsValue() {
        prefs.setSettingNamingFormat(AppConstants.NAME_FORMAT_DATE)
        assertEquals(AppConstants.NAME_FORMAT_DATE, prefs.settingNamingFormat)

        prefs.setSettingNamingFormat(AppConstants.NAME_FORMAT_TIMESTAMP)
        assertEquals(AppConstants.NAME_FORMAT_TIMESTAMP, prefs.settingNamingFormat)
    }

    // ── Recording format ────────────────────────────────────────────────────────

    @Test
    fun getSettingRecordingFormat_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_RECORDING_FORMAT, prefs.settingRecordingFormat)
    }

    @Test
    fun setSettingRecordingFormat_persistsValue() {
        prefs.setSettingRecordingFormat(AppConstants.FORMAT_WAV)
        assertEquals(AppConstants.FORMAT_WAV, prefs.settingRecordingFormat)

        prefs.setSettingRecordingFormat(AppConstants.FORMAT_M4A)
        assertEquals(AppConstants.FORMAT_M4A, prefs.settingRecordingFormat)
    }

    // ── Sample rate ─────────────────────────────────────────────────────────────

    @Test
    fun getSettingSampleRate_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_RECORD_SAMPLE_RATE, prefs.settingSampleRate)
    }

    @Test
    fun setSettingSampleRate_persistsValue() {
        prefs.setSettingSampleRate(AppConstants.RECORD_SAMPLE_RATE_16000)
        assertEquals(AppConstants.RECORD_SAMPLE_RATE_16000, prefs.settingSampleRate)

        prefs.setSettingSampleRate(AppConstants.RECORD_SAMPLE_RATE_48000)
        assertEquals(AppConstants.RECORD_SAMPLE_RATE_48000, prefs.settingSampleRate)
    }

    // ── Bitrate ─────────────────────────────────────────────────────────────────

    @Test
    fun getSettingBitrate_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_RECORD_ENCODING_BITRATE, prefs.settingBitrate)
    }

    @Test
    fun setSettingBitrate_persistsValue() {
        prefs.setSettingBitrate(AppConstants.RECORD_ENCODING_BITRATE_256000)
        assertEquals(AppConstants.RECORD_ENCODING_BITRATE_256000, prefs.settingBitrate)

        prefs.setSettingBitrate(AppConstants.RECORD_ENCODING_BITRATE_96000)
        assertEquals(AppConstants.RECORD_ENCODING_BITRATE_96000, prefs.settingBitrate)
    }

    // ── Channel count ───────────────────────────────────────────────────────────

    @Test
    fun getSettingChannelCount_returnsDefaultByDefault() {
        assertEquals(AppConstants.DEFAULT_CHANNEL_COUNT, prefs.settingChannelCount)
    }

    @Test
    fun setSettingChannelCount_persistsValue() {
        prefs.setSettingChannelCount(AppConstants.RECORD_AUDIO_MONO)
        assertEquals(AppConstants.RECORD_AUDIO_MONO, prefs.settingChannelCount)

        prefs.setSettingChannelCount(AppConstants.RECORD_AUDIO_STEREO)
        assertEquals(AppConstants.RECORD_AUDIO_STEREO, prefs.settingChannelCount)
    }

    // ── Reset settings ──────────────────────────────────────────────────────────

    @Test
    fun resetSettings_restoresRecordingDefaults() {
        // Set non-default values
        prefs.setSettingRecordingFormat(AppConstants.FORMAT_WAV)
        prefs.setSettingSampleRate(AppConstants.RECORD_SAMPLE_RATE_16000)
        prefs.setSettingBitrate(AppConstants.RECORD_ENCODING_BITRATE_256000)
        prefs.setSettingChannelCount(AppConstants.RECORD_AUDIO_MONO)

        prefs.resetSettings()

        assertEquals(AppConstants.DEFAULT_RECORDING_FORMAT, prefs.settingRecordingFormat)
        assertEquals(AppConstants.DEFAULT_RECORD_SAMPLE_RATE, prefs.settingSampleRate)
        assertEquals(AppConstants.DEFAULT_RECORD_ENCODING_BITRATE, prefs.settingBitrate)
        assertEquals(AppConstants.DEFAULT_CHANNEL_COUNT, prefs.settingChannelCount)
    }

    @Test
    fun resetSettings_doesNotAffectThemeOrNamingFormat() {
        prefs.setSettingThemeColor(AppConstants.THEME_PURPLE)
        prefs.setSettingNamingFormat(AppConstants.NAME_FORMAT_DATE)

        prefs.resetSettings()

        // Theme and naming format should NOT be reset (they are commented out in resetSettings)
        assertEquals(AppConstants.THEME_PURPLE, prefs.settingThemeColor)
        assertEquals(AppConstants.NAME_FORMAT_DATE, prefs.settingNamingFormat)
    }

    // ── Migrated settings ───────────────────────────────────────────────────────

    @Test
    fun isMigratedSettings_returnsFalseByDefault() {
        assertFalse(prefs.isMigratedSettings)
    }

    @Test
    fun migrateSettings_setsMigratedFlag() {
        assertFalse(prefs.isMigratedSettings)
        prefs.migrateSettings()
        assertTrue(prefs.isMigratedSettings)
    }

    @Test
    fun migrateSettings_writesDefaultValuesWhenNoOldPrefsExist() {
        prefs.migrateSettings()

        // Default old format=0 (M4A) → FORMAT_M4A
        assertEquals(AppConstants.FORMAT_M4A, prefs.settingRecordingFormat)
        // Default old naming=0 (COUNTED) → NAME_FORMAT_RECORD
        assertEquals(AppConstants.NAME_FORMAT_RECORD, prefs.settingNamingFormat)
        // Default old theme=0 → case 0 falls to case 9 → THEME_BLUE_GREY
        assertEquals(AppConstants.THEME_BLUE_GREY, prefs.settingThemeColor)
        // Default old sample rate = 44100
        assertEquals(AppConstants.RECORD_SAMPLE_RATE_44100, prefs.settingSampleRate)
        // Default old bitrate = 128000
        assertEquals(AppConstants.RECORD_ENCODING_BITRATE_128000, prefs.settingBitrate)
        // Default old channel count = STEREO
        assertEquals(AppConstants.RECORD_AUDIO_STEREO, prefs.settingChannelCount)
    }

    // ── firstRunExecuted sets multiple flags ────────────────────────────────────

    @Test
    fun firstRunExecuted_setsStoreDirPublicToFalse() {
        prefs.firstRunExecuted()
        assertFalse(prefs.isStoreDirPublic)
    }

    @Test
    fun firstRunExecuted_setsPublicStorageMigratedFlagImplicitly() {
        // firstRunExecuted sets IS_PUBLIC_STORAGE_MIGRATED to true
        prefs.firstRunExecuted()
        assertTrue(prefs.isPublicStorageMigrated)
    }

    @Test
    fun firstRunExecuted_setsMigratedFlagToTrue() {
        prefs.firstRunExecuted()
        assertTrue(prefs.isMigratedSettings)
    }
}

