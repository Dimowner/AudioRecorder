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
package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NameFormatTokenTest {

    private val locale = Locale.US

    /** 2024-03-07 14:05:09 local time, a Thursday, afternoon so that 12h and 24h hours differ. */
    private val timeMills = Calendar.getInstance().apply {
        clear()
        set(2024, Calendar.MARCH, 7, 14, 5, 9)
    }.timeInMillis

    private fun List<NameFormatToken>.render(counter: Long = 1) =
        formatRecordName(timeMills = timeMills, counter = counter, locale = locale)

    private fun legacyFormat(pattern: String) =
        SimpleDateFormat(pattern, locale).format(timeMills)

    @Test
    fun test_recordPreset_rendersCountedName() {
        assertEquals("Record-12", NameFormat.Record.presetTokens()!!.render(counter = 12))
    }

    @Test
    fun test_timestampPreset_rendersMills() {
        assertEquals(timeMills.toString(), NameFormat.Timestamp.presetTokens()!!.render())
    }

    /** The presets must keep producing exactly what FileUtil.generateRecordName* produced. */
    @Test
    fun test_datePresets_matchLegacyPatterns() {
        assertEquals(
            legacyFormat("dd.MM.yyyy HH.mm.ss"),
            NameFormat.Date.presetTokens()!!.render()
        )
        assertEquals(
            legacyFormat("MM-dd-yyyy hh.mm.ssaa"),
            NameFormat.DateUs.presetTokens()!!.render()
        )
        assertEquals(
            legacyFormat("yyyy-MM-dd HH.mm.ss"),
            NameFormat.DateIso8601.presetTokens()!!.render()
        )
    }

    @Test
    fun test_dateUsPreset_uses12HourClockWithAmPm() {
        assertEquals("03-07-2024 02.05.09PM", NameFormat.DateUs.presetTokens()!!.render())
    }

    @Test
    fun test_customPreset_hasNoTokens() {
        assertNull(NameFormat.Custom.presetTokens())
    }

    @Test
    fun test_serialize_roundTripsAllTokenTypes() {
        val tokens = NameFormatTokenType.entries.map { type ->
            when (type) {
                NameFormatTokenType.Text -> NameFormatToken(type, "My Record")
                NameFormatTokenType.Divider -> NameFormatToken(type, "-")
                else -> NameFormatToken(type)
            }
        }
        assertEquals(tokens, tokens.serialize().convertToNameFormatTokens())
    }

    @Test
    fun test_serialize_roundTripsTextContainingSeparators() {
        val tokens = listOf(
            NameFormatToken(NameFormatTokenType.Text, "a|b:c d%e+f"),
            NameFormatToken(NameFormatTokenType.Divider, " "),
            NameFormatToken(NameFormatTokenType.Counter),
        )
        assertEquals(tokens, tokens.serialize().convertToNameFormatTokens())
    }

    @Test
    fun test_serialize_emptyTokens() {
        assertEquals(emptyList<NameFormatToken>(), emptyList<NameFormatToken>().serialize().convertToNameFormatTokens())
    }

    @Test
    fun test_convertToNameFormatTokens_dropsUnknownTokens() {
        assertEquals(
            listOf(NameFormatToken(NameFormatTokenType.Counter)),
            "NOPE|CT|XX:value".convertToNameFormatTokens()
        )
    }

    @Test
    fun test_matchingPreset_findsPresets() {
        NameFormat.entries.filter { it != NameFormat.Custom }.forEach { preset ->
            assertEquals(preset, preset.presetTokens()!!.matchingPreset())
        }
    }

    @Test
    fun test_matchingPreset_nullForCustomAndEmptyTokens() {
        val custom = listOf(
            NameFormatToken(NameFormatTokenType.Text, "Meeting"),
            NameFormatToken(NameFormatTokenType.Divider, "_"),
            NameFormatToken(NameFormatTokenType.Counter),
        )
        assertNull(custom.matchingPreset())
        assertNull(emptyList<NameFormatToken>().matchingPreset())
    }

    @Test
    fun test_formatRecordName_customFormat() {
        val tokens = listOf(
            NameFormatToken(NameFormatTokenType.Text, "Meeting"),
            NameFormatToken(NameFormatTokenType.Divider, "_"),
            NameFormatToken(NameFormatTokenType.DayOfWeek),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Counter),
        )
        assertEquals("Meeting_Thu-7", tokens.render(counter = 7))
    }

    @Test
    fun test_monthName_rendersFullMonthName() {
        val tokens = listOf(NameFormatToken(NameFormatTokenType.MonthName))
        assertEquals("March", tokens.render())
    }

    @Test
    fun test_dividers_areOfferedAndRenderable() {
        assertTrue(NAME_FORMAT_DIVIDERS.contains("-"))
        val tokens = NAME_FORMAT_DIVIDERS.map { NameFormatToken(NameFormatTokenType.Divider, it) }
        assertEquals(NAME_FORMAT_DIVIDERS.joinToString(""), tokens.render())
    }
}
