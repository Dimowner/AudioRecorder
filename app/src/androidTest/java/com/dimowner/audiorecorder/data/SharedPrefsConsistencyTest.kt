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
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that [PrefsImpl] (V1) and [PrefsV2Impl] (V2) share the same
 * SharedPreferences file so that flags written by one are immediately visible
 * to the other — a critical requirement for the V1↔V2 migration flow.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SharedPrefsConsistencyTest {

    private lateinit var v1Prefs: PrefsImpl
    private lateinit var v2Prefs: PrefsV2Impl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Clear the shared file before each test
        context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        v1Prefs = PrefsImpl.getInstance(context)
        v2Prefs = PrefsV2Impl(context)
    }

    @After
    fun tearDown() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── Shared file ─────────────────────────────────────────────────────────────

    /**
     * Both impls must use [AppConstants.PREF_NAME] as their SharedPreferences
     * file name. Writing any value through one impl and reading it via the raw
     * SharedPreferences handle (using the same name) proves they operate on the
     * same file.
     */
    @Test
    fun bothImpls_useTheSameSharedPreferencesFile() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val rawPrefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE)

        // Write via V1, verify in raw prefs
        v1Prefs.setLegacyAppUser(true)
        assertTrue(
            "V1 write should be visible in raw SharedPreferences under PREF_NAME",
            rawPrefs.getBoolean(AppConstants.PREF_KEY_IS_LEGACY_APP_USER, false)
        )

        rawPrefs.edit().clear().commit()

        // Write via V2, verify in raw prefs
        v2Prefs.isLegacyAppUser = true
        assertTrue(
            "V2 write should be visible in raw SharedPreferences under PREF_NAME",
            rawPrefs.getBoolean(AppConstants.PREF_KEY_IS_LEGACY_APP_USER, false)
        )
    }

    // ── isLegacyAppUser cross-read / cross-write ─────────────────────────────────

    /**
     * Normally V1's [PrefsImpl.setLegacyAppUser] is the primary writer, but V2
     * can also write the flag (via the 10-tap Easter egg).  Both directions must
     * be consistent.
     */
    @Test
    fun isLegacyAppUser_defaultFalse_inBothImpls() {
        assertFalse(v1Prefs.isLegacyAppUser)
        assertFalse(v2Prefs.isLegacyAppUser)
    }

    @Test
    fun isLegacyAppUser_writtenByV1_isReadableByV2() {
        v1Prefs.setLegacyAppUser(true)

        assertTrue(
            "After V1 sets isLegacyAppUser, V2 should read true",
            v2Prefs.isLegacyAppUser
        )
    }

    @Test
    fun isLegacyAppUser_writtenByV2_isReadableByV1() {
        v2Prefs.isLegacyAppUser = true

        assertTrue(
            "After V2 sets isLegacyAppUser, V1 should read true",
            v1Prefs.isLegacyAppUser
        )
    }

    @Test
    fun isLegacyAppUser_resetByV1_isReflectedInV2() {
        v1Prefs.setLegacyAppUser(true)
        assertTrue(v2Prefs.isLegacyAppUser)

        v1Prefs.setLegacyAppUser(false)
        assertFalse(
            "After V1 resets isLegacyAppUser to false, V2 should also read false",
            v2Prefs.isLegacyAppUser
        )
    }

    @Test
    fun isLegacyAppUser_resetByV2_isReflectedInV1() {
        v2Prefs.isLegacyAppUser = true
        assertTrue(v1Prefs.isLegacyAppUser)

        v2Prefs.isLegacyAppUser = false
        assertFalse(
            "After V2 resets isLegacyAppUser to false, V1 should also read false",
            v1Prefs.isLegacyAppUser
        )
    }
}

