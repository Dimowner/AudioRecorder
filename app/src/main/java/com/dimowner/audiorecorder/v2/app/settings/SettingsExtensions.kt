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

package com.dimowner.audiorecorder.v2.app.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import timber.log.Timber

fun makeNameFormats(): List<NameFormatItem> {
    return listOf(
        NameFormatItem(
            NameFormat.Record, FileUtil.generateRecordNameCounted(1) + ".m4a"
        ),
        NameFormatItem(
            NameFormat.Date, FileUtil.generateRecordNameDateVariant() + ".m4a"
        ),
        NameFormatItem(
            NameFormat.DateUs, FileUtil.generateRecordNameDateUS() + ".m4a"
        ),
        NameFormatItem(
            NameFormat.DateIso8601, FileUtil.generateRecordNameDateISO8601() + ".m4a"
        ),
        NameFormatItem(
            NameFormat.Timestamp, FileUtil.generateRecordNameMills() + ".m4a"
        ),
    )
}

fun NameFormat.toNameFormatItem(): NameFormatItem {
    val text = when (this) {
        NameFormat.Record -> FileUtil.generateRecordNameCounted(1) + ".m4a"
        NameFormat.Date -> FileUtil.generateRecordNameDateVariant() + ".m4a"
        NameFormat.DateUs -> FileUtil.generateRecordNameDateUS() + ".m4a"
        NameFormat.DateIso8601 -> FileUtil.generateRecordNameDateISO8601() + ".m4a"
        NameFormat.Timestamp -> FileUtil.generateRecordNameMills() + ".m4a"
    }
    return NameFormatItem(this, text)
}

private fun rateIntentForUrl(url: String, context: Context): Intent {
    val intent = Intent(
        Intent.ACTION_VIEW, Uri.parse(String.format("%s?id=%s", url, context.packageName))
    )
    var flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    flags = flags or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
    intent.addFlags(flags)
    return intent
}


fun rateApp(context: Context) {
    try {
        val rateIntent = rateIntentForUrl("market://details", context)
        context.startActivity(rateIntent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e)
        val rateIntent = rateIntentForUrl("https://play.google.com/store/apps/details", context)
        context.startActivity(rateIntent)
    }
}

fun requestFeature(context: Context, onError: (String) -> Unit) {
    val i = Intent(Intent.ACTION_SEND)
    i.setType("message/rfc822")
    i.putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.REQUESTS_RECEIVER))
    i.putExtra(
        Intent.EXTRA_SUBJECT,
        "[" + context.getString(R.string.app_name)
                + "] " + AndroidUtils.getAppVersion(context)
                + " - " + context.getString(R.string.request)
    )
    try {
        val chooser = Intent.createChooser(i, context.getString(R.string.send_email))
        chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (ex: ActivityNotFoundException) {
        Timber.e(ex)
        onError(context.getString(R.string.email_clients_not_found))
    }
}

/**
 * Converts a [Spanned] into an [AnnotatedString] trying to keep as much formatting as possible.
 *
 * Currently supports `bold`, `italic`, `underline` and `color`.
 */
fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val spanned = this@toAnnotatedString
    append(spanned.toString())
    getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
        }
    }
}

/**
 * Converts an HTML-formatted string resource to an AnnotatedString
 * that can be displayed with proper formatting in Compose Text.
 *
 * @param resId The string resource ID containing HTML formatting
 * @return AnnotatedString with preserved formatting (bold, italic, underline, etc.)
 */
@Composable
fun htmlStringResource(@StringRes resId: Int): AnnotatedString {
    val text = stringResource(resId)
    val spanned = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spanned.toAnnotatedString()
}

@Composable
fun htmlStringResources(@StringRes vararg resIds: Int): AnnotatedString {
    val text = resIds.map { stringResource(it) }.joinToString("<br/><br/>")
    val spanned = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spanned.toAnnotatedString()
}

fun RecordingFormat.convertToText(formatStrings: Array<String>): String {
    return formatStrings[this.index]
}

fun BitRate.convertToText(bitrateStrings: Array<String>): String {
    return bitrateStrings[this.index]
}

fun ChannelCount.convertToText(channelCountStrings: Array<String>): String {
    return channelCountStrings[this.index]
}

fun SampleRate.convertToText(channelCountStrings: Array<String>): String {
    return channelCountStrings[this.index]
}

@SuppressWarnings("MagicNumber")
fun sizeMbPerMin(
    recordingFormat: RecordingFormat?,
    sampleRate: SampleRate?,
    bitrate: BitRate?,
    channels: ChannelCount?
): Float {
    if (recordingFormat == null) return 0F
    return when (recordingFormat) {
        RecordingFormat.M4a -> {
            if (bitrate == null) {
                0F
            } else {
                (60F * (bitrate.value / 8F)) / 1000000f
            }
        }
        RecordingFormat.ThreeGp -> {
            val threeGpBitrate = recordingFormat.getThreeGpBitRateForSampleRate(sampleRate) ?: DefaultValues.MAX_3GP_BITRATE_WB
            (60F * (threeGpBitrate / 8F)) / 1000000f
        }
        RecordingFormat.Wav -> {
            if (sampleRate != null && channels != null) {
                (60F * (sampleRate.value * channels.value * 2F)) / 1000000f
            } else {
                0F
            }
        }
    }
}

@SuppressWarnings("MagicNumber")
fun spaceToRecordingTimeMills(
    spaceBytes: Long,
    recordingFormat: RecordingFormat?,
    sampleRate: SampleRate?,
    bitrate: BitRate?,
    channels: ChannelCount?
): Long {
    if (recordingFormat == null || spaceBytes <= 0L) return 0L
    return when (recordingFormat) {
        RecordingFormat.M4a -> {
            if (bitrate == null) 0L
            else 1000L * (spaceBytes / (bitrate.value / 8L))
        }
        RecordingFormat.ThreeGp -> {
            val threeGpBitrate = recordingFormat.getThreeGpBitRateForSampleRate(sampleRate) ?: DefaultValues.MAX_3GP_BITRATE_WB
            1000L * (spaceBytes / (threeGpBitrate / 8L))
        }
        RecordingFormat.Wav -> {
            if (sampleRate != null && channels != null) {
                1000L * (spaceBytes / (sampleRate.value.toLong() * channels.value * 2L))
            } else {
                0L
            }
        }
    }
}

/**
 * Returns the maximum AMR bitrate (in bits per second) to use when encoding a 3GP file at the
 * given [sampleRate].
 *
 * 3GP audio is encoded with the AMR codec, which has two operating modes tied directly to the
 * sample rate:
 * - **AMR-NB (Narrow-Band)** — 8 000 Hz → [DefaultValues.MAX_3GP_BITRATE_NB] (12 000 bps)
 * - **AMR-WB (Wide-Band)**   — 16 000 Hz → [DefaultValues.MAX_3GP_BITRATE_WB] (24 000 bps)
 *
 * The function returns `null` in two cases:
 * 1. The receiver is not [RecordingFormat.ThreeGp] — bitrate selection via this mapping is only
 *    meaningful for 3GP; other formats use their own bitrate settings.
 * 2. The supplied [sampleRate] is `null` or is not one of the two AMR-supported rates
 *    ([SampleRate.SR8000] / [SampleRate.SR16000]) — callers should fall back to a sensible
 *    default (e.g. [DefaultValues.MAX_3GP_BITRATE_WB]).
 *
 * @param sampleRate The target sample rate for the recording; may be `null`.
 * @return The recommended max bitrate in bps, or `null` if no mapping applies.
 */
fun RecordingFormat.getThreeGpBitRateForSampleRate(sampleRate: SampleRate?): Int? {
    return if (this == RecordingFormat.ThreeGp) {
        when (sampleRate) {
            SampleRate.SR8000 -> DefaultValues.MAX_3GP_BITRATE_NB
            SampleRate.SR16000 -> DefaultValues.MAX_3GP_BITRATE_WB
            else -> null
        }
    } else {
        null
    }
}

/**
 * Formats recording time in milliseconds as "Xd Xh Xm", "Xh Xm", or "Xm".
 * Days and hours are omitted when zero; minutes are always shown.
 * Unit abbreviations are resolved from string resources for proper localisation.
 */
@SuppressWarnings("MagicNumber")
fun formatAvailableRecordingTime(mills: Long, resources: Resources): String {
    val totalMinutes = mills / 1000 / 60
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> resources.getString(R.string.duration_days_hours_minutes, days, hours, minutes)
        hours > 0 -> resources.getString(R.string.duration_hours_and_minutes, hours, minutes)
        else -> resources.getString(R.string.duration_minutes, minutes)
    }
}

fun getChannelCounts(
    format: RecordingFormat,
    selected: ChannelCount?,
    strings: Array<String>
): List<ChipItem<ChannelCount>> {
    return format.config.supportedChannelCounts.map { channelCount ->
        ChipItem(
            id = channelCount.index,
            value = channelCount,
            name = strings[channelCount.index],
            isSelected = channelCount == selected
        )
    }
}

fun getBitRates(
    format: RecordingFormat,
    selected: BitRate?,
    strings: Array<String>
): List<ChipItem<BitRate>> {
    return format.config.supportedBitRates.map { bitRate ->
        ChipItem(
            id = bitRate.index,
            value = bitRate,
            name = strings[bitRate.index],
            isSelected = bitRate == selected
        )
    }
}

fun getSampleRates(
    format: RecordingFormat,
    selected: SampleRate?,
    strings: Array<String>
): List<ChipItem<SampleRate>> {
    return format.config.supportedSampleRates.map { sampleRate ->
        ChipItem(
            id = sampleRate.index,
            value = sampleRate,
            name = strings[sampleRate.index],
            isSelected = sampleRate == selected
        )
    }
}

/** Sample rate to fall back to when the current selection is not supported by [this] format. */
fun RecordingFormat.defaultSampleRate(): SampleRate = when (this) {
    RecordingFormat.ThreeGp -> DefaultValues.Default3GpSampleRate
    RecordingFormat.M4a,
    RecordingFormat.Wav -> DefaultValues.DefaultSampleRate
}

/** Channel count to fall back to when the current selection is not supported by [this] format. */
fun RecordingFormat.defaultChannelCount(): ChannelCount = when (this) {
    RecordingFormat.ThreeGp -> DefaultValues.Default3GpChannelCount
    RecordingFormat.M4a,
    RecordingFormat.Wav -> DefaultValues.DefaultChannelCount
}

/**
 * Determines if a duration strictly exceeds two hours (120 minutes).
 * @param hours The number of hours.
 * @param minutes The number of minutes.
 * @return `true` if the total duration is greater than 120 minutes; `false` if it is
 * 120 minutes (2h 0m) or less.
 */
fun isDurationLongerThanTwoHours(hours: Int, minutes: Int): Boolean {
    if (hours < 0 || minutes < 0) return false

    val durationMinutes = hours * 60 + minutes
    return durationMinutes > 120
}
