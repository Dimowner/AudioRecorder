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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PrefsImplTest {

    private lateinit var prefs: PrefsV2Impl

    @Before
    fun createPrefs() {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = PrefsV2Impl(context)
    }

    @After
    fun resetPrefs() {
        prefs.fullPreferenceReset()
    }

    @Test
    fun test_fullPreferenceReset() {
        val id = 101

        prefs.confirmFirstRunExecuted()
        prefs.activeRecordId = id
        prefs.isDarkTheme = true
        prefs.settingSampleRate = SampleRate.SR16000
        prefs.settingNamingFormat = NameFormat.DateUs

        prefs.fullPreferenceReset()

        assertEquals(-1, prefs.activeRecordId)
        assertEquals(DefaultValues.isDarkTheme, prefs.isDarkTheme)
        assertEquals(DefaultValues.DefaultSampleRate, prefs.settingSampleRate)
        assertEquals(DefaultValues.DefaultNameFormat, prefs.settingNamingFormat)
    }

    @Test
    fun test_firstRun() {
        assertTrue(prefs.isFirstRun)

        prefs.confirmFirstRunExecuted()
        assertFalse(prefs.isFirstRun)
    }

    @Test
    fun test_askToRenameAfterRecordingStopped() {
        assertEquals(DefaultValues.isAskToRename, prefs.askToRenameAfterRecordingStopped)

        prefs.askToRenameAfterRecordingStopped = !DefaultValues.isAskToRename
        assertEquals(!DefaultValues.isAskToRename, prefs.askToRenameAfterRecordingStopped)
    }

    @Test
    fun test_activeRecordId() {
        assertEquals(-1, prefs.activeRecordId)

        prefs.activeRecordId = 303
        assertEquals(303L, prefs.activeRecordId)
    }

    @Test
    fun test_recordCounter() {
        assertEquals(1, prefs.recordCounter)

        prefs.incrementRecordCounter()
        assertEquals(2, prefs.recordCounter)
    }

    @Test
    fun test_isKeepScreenOn() {
        assertEquals(DefaultValues.isKeepScreenOn, prefs.isKeepScreenOn)

        prefs.isKeepScreenOn = !DefaultValues.isKeepScreenOn
        assertEquals(!DefaultValues.isKeepScreenOn, prefs.isKeepScreenOn)
    }

    @Test
    fun test_recordsSortOrder() {
        assertEquals(DefaultValues.DefaultSortOrder, prefs.recordsSortOrder)

        prefs.recordsSortOrder = SortOrder.NameDesc
        assertEquals(SortOrder.NameDesc, prefs.recordsSortOrder)
    }

    @Test
    fun test_isDynamicTheme() {
        assertEquals(DefaultValues.isDynamicTheme, prefs.isDynamicTheme)

        prefs.isDynamicTheme = !DefaultValues.isDynamicTheme
        assertEquals(!DefaultValues.isDynamicTheme, prefs.isDynamicTheme)
    }

    @Test
    fun test_isDarkTheme() {
        assertEquals(DefaultValues.isDarkTheme, prefs.isDarkTheme)

        prefs.isDarkTheme = !DefaultValues.isDarkTheme
        assertEquals(!DefaultValues.isDarkTheme, prefs.isDarkTheme)
    }

    @Test
    fun test_settingNamingFormat() {
        assertEquals(DefaultValues.DefaultNameFormat, prefs.settingNamingFormat)

        prefs.settingNamingFormat = NameFormat.DateUs
        assertEquals(NameFormat.DateUs, prefs.settingNamingFormat)
    }

    @Test
    fun test_settingRecordingFormat() {
        assertEquals(DefaultValues.DefaultRecordingFormat, prefs.settingRecordingFormat)

        prefs.settingRecordingFormat = RecordingFormat.ThreeGp
        assertEquals(RecordingFormat.ThreeGp, prefs.settingRecordingFormat)
    }

    @Test
    fun test_settingSampleRate() {
        assertEquals(DefaultValues.DefaultSampleRate, prefs.settingSampleRate)

        prefs.settingSampleRate = SampleRate.SR32000
        assertEquals(SampleRate.SR32000, prefs.settingSampleRate)
    }

    @Test
    fun test_settingBitrate() {
        assertEquals(DefaultValues.DefaultBitRate, prefs.settingBitrate)

        prefs.settingBitrate = BitRate.BR256
        assertEquals(BitRate.BR256, prefs.settingBitrate)
    }

    @Test
    fun test_settingChannelCount() {
        assertEquals(DefaultValues.DefaultChannelCount, prefs.settingChannelCount)

        prefs.settingChannelCount = ChannelCount.Mono
        assertEquals(ChannelCount.Mono, prefs.settingChannelCount)
    }

    @Test
    fun test_resetRecordingSettings() {
        prefs.settingRecordingFormat = RecordingFormat.ThreeGp
        prefs.settingSampleRate = SampleRate.SR32000
        prefs.settingBitrate = BitRate.BR256
        prefs.settingChannelCount = ChannelCount.Mono

        prefs.resetRecordingSettings()

        assertEquals(DefaultValues.DefaultRecordingFormat, prefs.settingRecordingFormat)
        assertEquals(DefaultValues.DefaultSampleRate, prefs.settingSampleRate)
        assertEquals(DefaultValues.DefaultBitRate, prefs.settingBitrate)
        assertEquals(DefaultValues.DefaultChannelCount, prefs.settingChannelCount)
    }
}
