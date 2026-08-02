package com.dimowner.audiorecorder.v2.data.model

import com.dimowner.audiorecorder.audio.player.NORMAL_PLAYBACK_SPEED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PlaybackSpeedTest {

    // -------------------------------------------------------------------------
    // formatValue
    // -------------------------------------------------------------------------

    @Test
    fun `formatValue drops trailing zeros in a latin locale`() {
        val locale = Locale.US
        assertEquals("0.5", PlaybackSpeed.X0_5.formatValue(locale))
        assertEquals("0.75", PlaybackSpeed.X0_75.formatValue(locale))
        assertEquals("1", PlaybackSpeed.X1.formatValue(locale))
        assertEquals("1.25", PlaybackSpeed.X1_25.formatValue(locale))
        assertEquals("1.5", PlaybackSpeed.X1_5.formatValue(locale))
        assertEquals("1.75", PlaybackSpeed.X1_75.formatValue(locale))
        assertEquals("2", PlaybackSpeed.X2.formatValue(locale))
    }

    @Test
    fun `formatValue uses the locale decimal separator`() {
        assertEquals("0,5", PlaybackSpeed.X0_5.formatValue(Locale.GERMANY))
    }

    /**
     * Durations in the playback panel go through `String.format(Locale.getDefault(), ...)`, which
     * gives Arabic-Indic digits under an Arabic locale. The speed menu sits right next to them,
     * so it has to use the same numbering system rather than hardcoded Latin digits.
     */
    @Test
    fun `formatValue uses arabic-indic digits in an arabic locale`() {
        val locale = Locale.forLanguageTag("ar")
        assertEquals("٠٫٥", PlaybackSpeed.X0_5.formatValue(locale))
        assertEquals("٢", PlaybackSpeed.X2.formatValue(locale))
        assertEquals(
            String.format(locale, "%02d:%02d", 1, 51).take(1),
            PlaybackSpeed.X0_5.formatValue(locale).take(1)
        )
    }

    // -------------------------------------------------------------------------
    // entries
    // -------------------------------------------------------------------------

    @Test
    fun `the menu lists every rate in ascending order`() {
        assertEquals(
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
            PlaybackSpeed.entries.map { it.value }
        )
    }

    @Test
    fun `the normal rate is the one the player runs at by default`() {
        assertEquals(NORMAL_PLAYBACK_SPEED, PlaybackSpeed.NORMAL.value, 0.0f)
    }

    // -------------------------------------------------------------------------
    // convertToPlaybackSpeed
    // -------------------------------------------------------------------------

    @Test
    fun `convertToPlaybackSpeed maps player rates back to menu entries`() {
        assertEquals(PlaybackSpeed.X1_25, 1.25f.convertToPlaybackSpeed())
        assertEquals(PlaybackSpeed.X1, NORMAL_PLAYBACK_SPEED.convertToPlaybackSpeed())
        assertNull(3.0f.convertToPlaybackSpeed())
    }
}
