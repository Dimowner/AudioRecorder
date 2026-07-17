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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single building block of a record name format.
 *
 * [key] is persisted in preferences, so existing values must never change.
 */
enum class NameFormatTokenType(val key: String) {
    Timestamp("TS"),
    Year("YR"),
    Month("MO"),
    MonthName("MNM"),
    Day("DY"),
    DayOfWeek("DW"),
    Hour24("H24"),
    Hour12("H12"),
    AmPm("AP"),
    Minute("MN"),
    Second("SC"),
    Counter("CT"),
    Text("TX"),
    Divider("DV");

    /**
     * [SimpleDateFormat] pattern this token renders with, or `null` for tokens that are not
     * derived from the recording time ([Timestamp], [Counter], [Text], [Divider]).
     */
    fun datePattern(): String? = when (this) {
        Year -> "yyyy"
        Month -> "MM"
        MonthName -> "MMMM"
        Day -> "dd"
        DayOfWeek -> "EEE"
        Hour24 -> "HH"
        Hour12 -> "hh"
        AmPm -> "aa"
        Minute -> "mm"
        Second -> "ss"
        Timestamp, Counter, Text, Divider -> null
    }

    companion object {
        fun fromKey(key: String): NameFormatTokenType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One element of a constructed name format. [value] carries the typed text for
 * [NameFormatTokenType.Text] and the symbol for [NameFormatTokenType.Divider]; it is empty for
 * every other type.
 */
@Parcelize
data class NameFormatToken(
    val type: NameFormatTokenType,
    val value: String = "",
) : Parcelable

/** Divider symbols offered by the name format constructor. */
val NAME_FORMAT_DIVIDERS = listOf("-", "_", ".", ",", " ", "(", ")", "#")

private const val TOKEN_SEPARATOR = "|"
private const val VALUE_SEPARATOR = ":"
private const val ENCODING = "UTF-8"

/**
 * Serializes tokens into a single string suitable for storing in preferences, e.g.
 * `TX:Record|DV:-|CT`. Token values are URL encoded so that they cannot collide with the
 * separators.
 */
fun List<NameFormatToken>.serialize(): String {
    return joinToString(TOKEN_SEPARATOR) { token ->
        if (token.value.isEmpty()) {
            token.type.key
        } else {
            token.type.key + VALUE_SEPARATOR + URLEncoder.encode(token.value, ENCODING)
        }
    }
}

/**
 * Parses a string produced by [serialize]. Unknown or malformed tokens are dropped, so a format
 * written by a newer app version degrades instead of crashing.
 */
fun String.convertToNameFormatTokens(): List<NameFormatToken> {
    if (isEmpty()) return emptyList()
    return split(TOKEN_SEPARATOR).mapNotNull { part ->
        val key = part.substringBefore(VALUE_SEPARATOR)
        val type = NameFormatTokenType.fromKey(key) ?: return@mapNotNull null
        val value = if (part.contains(VALUE_SEPARATOR)) {
            runCatching { URLDecoder.decode(part.substringAfter(VALUE_SEPARATOR), ENCODING) }
                .getOrDefault("")
        } else {
            ""
        }
        NameFormatToken(type, value)
    }
}

/**
 * Renders the tokens into a record name.
 *
 * @param timeMills Recording time the date and time tokens are rendered from.
 * @param counter Value for [NameFormatTokenType.Counter].
 * @param locale Locale used to render the date and time tokens.
 */
fun List<NameFormatToken>.formatRecordName(
    timeMills: Long = System.currentTimeMillis(),
    counter: Long = 1,
    locale: Locale = Locale.getDefault(),
): String {
    val date = Date(timeMills)
    return joinToString("") { token ->
        when (token.type) {
            NameFormatTokenType.Timestamp -> timeMills.toString()
            NameFormatTokenType.Counter -> counter.toString()
            NameFormatTokenType.Text, NameFormatTokenType.Divider -> token.value
            else -> token.type.datePattern()
                ?.let { SimpleDateFormat(it, locale).format(date) }
                .orEmpty()
        }
    }
}

/**
 * The tokens a preset is built from, or `null` for [NameFormat.Custom], which has no fixed tokens.
 * Each preset renders exactly like its `FileUtil.generateRecordName*` counterpart.
 */
fun NameFormat.presetTokens(): List<NameFormatToken>? {
    return when (this) {
        //Record-1
        NameFormat.Record -> listOf(
            NameFormatToken(NameFormatTokenType.Text, "Record"),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Counter),
        )
        //1751328000000
        NameFormat.Timestamp -> listOf(NameFormatToken(NameFormatTokenType.Timestamp))
        //dd.MM.yyyy HH.mm.ss
        NameFormat.Date -> listOf(
            NameFormatToken(NameFormatTokenType.Day),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Month),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Year),
            NameFormatToken(NameFormatTokenType.Divider, " "),
            NameFormatToken(NameFormatTokenType.Hour24),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Minute),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Second),
        )
        //MM-dd-yyyy hh.mm.ssaa
        NameFormat.DateUs -> listOf(
            NameFormatToken(NameFormatTokenType.Month),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Day),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Year),
            NameFormatToken(NameFormatTokenType.Divider, " "),
            NameFormatToken(NameFormatTokenType.Hour12),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Minute),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Second),
            NameFormatToken(NameFormatTokenType.AmPm),
        )
        //yyyy-MM-dd HH.mm.ss
        NameFormat.DateIso8601 -> listOf(
            NameFormatToken(NameFormatTokenType.Year),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Month),
            NameFormatToken(NameFormatTokenType.Divider, "-"),
            NameFormatToken(NameFormatTokenType.Day),
            NameFormatToken(NameFormatTokenType.Divider, " "),
            NameFormatToken(NameFormatTokenType.Hour24),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Minute),
            NameFormatToken(NameFormatTokenType.Divider, "."),
            NameFormatToken(NameFormatTokenType.Second),
        )
        NameFormat.Custom -> null
    }
}

/**
 * The preset that produces exactly these tokens, or `null` when the tokens do not match any preset
 * and therefore have to be stored as [NameFormat.Custom].
 */
fun List<NameFormatToken>.matchingPreset(): NameFormat? {
    if (isEmpty()) return null
    return NameFormat.entries.firstOrNull { it.presetTokens() == this }
}
