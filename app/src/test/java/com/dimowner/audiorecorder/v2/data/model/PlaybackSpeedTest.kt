package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaybackSpeedTest {

    // -------------------------------------------------------------------------
    // formatValue
    // -------------------------------------------------------------------------

    @Test
    fun `formatValue keeps one fraction digit in a latin locale`() {
        val locale = Locale.US
        assertEquals("0.5", PlaybackSpeed.X0_5.formatValue(locale))
        assertEquals("0.75", PlaybackSpeed.X0_75.formatValue(locale))
        assertEquals("1.5", PlaybackSpeed.X1_5.formatValue(locale))
        assertEquals("2.0", PlaybackSpeed.X2.formatValue(locale))
    }

    @Test
    fun `formatValue uses the locale decimal separator`() {
        assertEquals("0,5", PlaybackSpeed.X0_5.formatValue(Locale.GERMANY))
    }

    /**
     * Durations in the playback panel go through `String.format(Locale.getDefault(), ...)`, which
     * gives Arabic-Indic digits under an Arabic locale. The rate buttons sit right next to them,
     * so they have to use the same numbering system rather than hardcoded Latin digits.
     */
    @Test
    fun `formatValue uses arabic-indic digits in an arabic locale`() {
        val locale = Locale.forLanguageTag("ar")
        assertEquals("٠٫٥", PlaybackSpeed.X0_5.formatValue(locale))
        assertEquals("٢٫٠", PlaybackSpeed.X2.formatValue(locale))
        assertEquals(
            String.format(locale, "%02d:%02d", 1, 51).take(1),
            PlaybackSpeed.X0_5.formatValue(locale).take(1)
        )
    }

    // -------------------------------------------------------------------------
    // slower / faster
    // -------------------------------------------------------------------------

    @Test
    fun `slower and faster split the rates around the normal one`() {
        val speeds = PlaybackSpeed.entries
        assertEquals(listOf(PlaybackSpeed.X0_5, PlaybackSpeed.X0_75), speeds.slower())
        assertEquals(listOf(PlaybackSpeed.X1_5, PlaybackSpeed.X2), speeds.faster())
    }

    @Test
    fun `compact set keeps one rate on each side of the play controls`() {
        assertEquals(listOf(PlaybackSpeed.X0_5), PlaybackSpeed.compact.slower())
        assertEquals(listOf(PlaybackSpeed.X2), PlaybackSpeed.compact.faster())
    }
}
