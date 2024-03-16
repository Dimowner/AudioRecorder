package com.dimowner.audiorecorder.v2.app.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
                Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
        }
    }
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

fun recordingSettingsCombinedText(
    formatStrings: Array<String>,
    sampleRateStrings: Array<String>,
    bitrateStrings: Array<String>,
    channelCountStrings: Array<String>,
    recordingFormat: RecordingFormat?,
    sampleRate: SampleRate?,
    bitRate: BitRate?,
    channelCount: ChannelCount?
): String {
    return when (recordingFormat) {
        RecordingFormat.M4a -> {
            recordingFormat.convertToText(formatStrings) + ", " +
            sampleRate?.convertToText(sampleRateStrings) + ", " +
            bitRate?.convertToText(bitrateStrings) + ", " +
            channelCount?.convertToText(channelCountStrings)
        }
        RecordingFormat.ThreeGp -> {
            recordingFormat.convertToText(formatStrings) + ", " +
            sampleRate?.convertToText(sampleRateStrings) + ", " +
            channelCount?.convertToText(channelCountStrings)
        }
        RecordingFormat.Wav -> {
            recordingFormat.convertToText(formatStrings) + ", " +
            sampleRate?.convertToText(sampleRateStrings) + ", " +
            channelCount?.convertToText(channelCountStrings)
        }
        else -> ""
    }
}

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
        RecordingFormat.ThreeGp -> (60F * (DefaultValues.Default3GpBitRate / 8F)) / 1000000f
        RecordingFormat.Wav -> {
            if (sampleRate != null && channels != null) {
                (60F * (sampleRate.value * channels.value * 2F)) / 1000000f
            } else {
                0F
            }
        }
    }
}

fun getChannelCounts(
    format: RecordingFormat,
    selected: ChannelCount?,
    strings: Array<String>
): List<ChipItem<ChannelCount>> {
    return when (format) {
        RecordingFormat.M4a,
        RecordingFormat.Wav -> {
            ChannelCount.entries.toList().mapIndexed { i, channelCount ->
                ChipItem(
                    id = i,
                    value = channelCount,
                    name = strings[i],
                    isSelected = channelCount == selected
                )
            }
        }
        RecordingFormat.ThreeGp -> {
            listOf(
                ChipItem(
                    id = 0,
                    value = ChannelCount.Mono,
                    name = strings[1],
                    isSelected = ChannelCount.Mono == selected
                )
            )
        }
    }
}

fun getBitRates(
    format: RecordingFormat,
    selected: BitRate?,
    strings: Array<String>
): List<ChipItem<BitRate>> {
    return when (format) {
        RecordingFormat.M4a -> {
            BitRate.entries.toList().mapIndexed { i, bitRate ->
                ChipItem(
                    id = i,
                    value = bitRate,
                    name = strings[i],
                    isSelected = bitRate == selected
                )
            }
        }
        RecordingFormat.Wav,
        RecordingFormat.ThreeGp -> listOf()
    }
}

fun getSampleRates(
    format: RecordingFormat,
    selected: SampleRate?,
    strings: Array<String>
): List<ChipItem<SampleRate>> {
    return when (format) {
        RecordingFormat.M4a,
        RecordingFormat.Wav-> {
            SampleRate.entries.toList().mapIndexed { i, sampleRate ->
                ChipItem(
                    id = i,
                    value = sampleRate,
                    name = strings[i],
                    isSelected = sampleRate == selected
                )
            }
        }
        RecordingFormat.ThreeGp -> listOf(
            ChipItem(
                id = 0,
                value = SampleRate.SR8000,
                name = strings[0],
                isSelected = SampleRate.SR8000 == selected
            ),
            ChipItem(
                id = 1,
                value = SampleRate.SR16000,
                name = strings[1],
                isSelected = SampleRate.SR16000 == selected
            )
        )
    }
}
