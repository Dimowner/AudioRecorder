package com.dimowner.audiorecorder.v2.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.AppConstants.PREF_NAME
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class PrefsV2ImplTest {

    private lateinit var context: Context
    private lateinit var prefs: PrefsV2Impl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = PrefsV2Impl(context)
    }

    @Test
    fun `floating recorder overlay is disabled by default`() {
        assertFalse(prefs.isFloatingRecorderOverlayEnabled)
    }

    @Test
    fun `floating recorder overlay enabled value persists`() {
        prefs.isFloatingRecorderOverlayEnabled = true

        val reloadedPrefs = PrefsV2Impl(context)

        assertTrue(reloadedPrefs.isFloatingRecorderOverlayEnabled)
    }

    @Test
    fun `floating recorder overlay position defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderOverlayX)
        assertEquals(-1, prefs.floatingRecorderOverlayY)
    }

    @Test
    fun `floating recorder overlay size defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderOverlaySize)
    }

    @Test
    fun `floating recorder rename overlay position defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderRenameOverlayX)
        assertEquals(-1, prefs.floatingRecorderRenameOverlayY)
    }

    @Test
    fun `floating recorder rename speech mode defaults to append`() {
        assertEquals(RenameSpeechMode.Append, prefs.floatingRecorderRenameSpeechMode)
    }

    @Test
    fun `floating recorder overlay position persists`() {
        prefs.floatingRecorderOverlayX = 42
        prefs.floatingRecorderOverlayY = 84

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(42, reloadedPrefs.floatingRecorderOverlayX)
        assertEquals(84, reloadedPrefs.floatingRecorderOverlayY)
    }

    @Test
    fun `floating recorder overlay size persists`() {
        prefs.floatingRecorderOverlaySize = 112

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(112, reloadedPrefs.floatingRecorderOverlaySize)
    }

    @Test
    fun `floating recorder rename overlay position persists`() {
        prefs.floatingRecorderRenameOverlayX = 123
        prefs.floatingRecorderRenameOverlayY = 456

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(123, reloadedPrefs.floatingRecorderRenameOverlayX)
        assertEquals(456, reloadedPrefs.floatingRecorderRenameOverlayY)
    }

    @Test
    fun `floating recorder rename speech mode persists replace`() {
        prefs.floatingRecorderRenameSpeechMode = RenameSpeechMode.Replace

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(RenameSpeechMode.Replace, reloadedPrefs.floatingRecorderRenameSpeechMode)
    }

    @Test
    fun `floating recorder rename speech mode persists append to audio note`() {
        prefs.floatingRecorderRenameSpeechMode = RenameSpeechMode.AppendToAudioNote

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(RenameSpeechMode.AppendToAudioNote, reloadedPrefs.floatingRecorderRenameSpeechMode)
    }
}
