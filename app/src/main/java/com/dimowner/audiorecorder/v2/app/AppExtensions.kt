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

package com.dimowner.audiorecorder.v2.app

import android.content.Context
import android.content.res.Resources
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.v2.app.settings.convertToText
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import java.util.Locale

fun calculateScale(
    mills: Long,
    shortRecordDuration: Long = AppConstantsV2.SHORT_RECORD,
    defaultWidthScale: Float = AppConstantsV2.DEFAULT_WIDTH_SCALE,
): Float {
    return when {
        mills >= shortRecordDuration -> {
            defaultWidthScale
        }
        else -> {
            mills * (defaultWidthScale / shortRecordDuration)
        }
    }
}

@SuppressWarnings("MagicNumber")
fun calculateGridStep(durationMills: Long): Long {
    var actualStepSec = (durationMills / 1000) / AppConstants.GRID_LINES_COUNT
    var k = 1
    while (actualStepSec > 239) {
        actualStepSec /= 2
        k *= 2
    }
    //Ranges can be better optimised
    val gridStep: Long = when (actualStepSec) {
        in 0..2 -> 2000
        in 3..6 -> 5000
        in 7..14 -> 10000
        in 15..24 -> 20000
        in 25..44 -> 30000
        in 45..74 -> 60000
        in 75..104 -> 90000
        in 105..149 -> 120000
        in 150..209 -> 180000
        in 210..269 -> 240000
        in 270..329 -> 300000
        in 330..419 -> 360000
        in 420..539 -> 480000
        in 540..659 -> 600000
        in 660..809 -> 720000
        in 810..1049 -> 900000
        in 1050..1349 -> 1200000
        in 1350..1649 -> 1500000
        in 1650..2099 -> 1800000
        in 2100..2699 -> 2400000
        in 2700..3299 -> 3000000
        in 3300..3899 -> 3600000
        else -> 4200000
    }
    return gridStep * k
}

/**
 * Readjust waveform amplitudes
 * @param [IntArray] of int values where each element represents an amplitude
 */
@SuppressWarnings("MagicNumber")
fun adjustWaveformHeights(frameGains: IntArray): IntArray {
    val numFrames = frameGains.size

    //Find the highest gain
    var maxGain = 1.0f
    for (i in 0 until numFrames) {
        if (frameGains[i] > maxGain) {
            maxGain = frameGains[i].toFloat()
        }
    }
    // Make sure the range is no more than 0 - 255
    var scaleFactor = 1.0f
    if (maxGain > 255.0) {
        scaleFactor = 255 / maxGain
    }

    // Build histogram of 256 bins and figure out the new scaled max
    maxGain = 0.0f
    val gainHist = IntArray(256)
    for (i in 0 until numFrames) {
        var smoothedGain = (frameGains[i] * scaleFactor).toInt()
        if (smoothedGain < 0) smoothedGain = 0
        if (smoothedGain > 255) smoothedGain = 255
        if (smoothedGain > maxGain) maxGain = smoothedGain.toFloat()
        gainHist[smoothedGain]++
    }

    // Re-calibrate the min to be 5%
    var minGain = 0.0f
    var sum = 0
    while (minGain < 255 && sum < numFrames / 20) {
        sum += gainHist[minGain.toInt()]
        minGain++
    }

    // Re-calibrate the max to be 99%
    sum = 0
    while (maxGain > 2 && sum < numFrames / 100) {
        sum += gainHist[maxGain.toInt()]
        maxGain--
    }

    // Compute the heights
    val heights = FloatArray(numFrames)
    var range = maxGain - minGain
    if (range <= 0) {
        range = 1.0f
    }
    for (i in 0 until numFrames) {
        var value = (frameGains[i] * scaleFactor - minGain) / range
        if (value < 0.0) value = 0.0f
        if (value > 1.0) value = 1.0f
        heights[i] = value * value
    }
    val scale = AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE
    val waveformData = IntArray(numFrames)
    for (i in 0 until numFrames) {
        waveformData[i] = (heights[i] * scale).toInt()
    }
    //Array of int values where each value between 0 to WAVEFORM_AMPLITUDE_MAX_VALUE
    return waveformData
}

fun formatRecordingFormat(
    formatStrings: Array<String>,
    recordingFormat: RecordingFormat?,
): String {
    return recordingFormat?.convertToText(formatStrings) ?: ""
}

fun formatSampleRate(
    sampleRateStrings: Array<String>,
    sampleRate: SampleRate?,
): String {
    return sampleRate?.convertToText(sampleRateStrings) ?: ""
}

fun formatBitRate(
    bitrateStrings: Array<String>,
    bitRate: BitRate?,
): String {
    return bitRate?.convertToText(bitrateStrings) ?: ""
}

fun formatChannelCount(
    channelCountStrings: Array<String>,
    channelCount: ChannelCount?,
): String {
    return channelCount?.convertToText(channelCountStrings) ?: ""
}

fun recordingSettingsCombinedText(
    recordingFormat: RecordingFormat?,
    recordingFormatText: String,
    sampleRateText: String,
    bitRateText: String,
    channelCountText: String
): String {
    return when {
        recordingFormat == null -> ""
        recordingFormat.config.hasBitrate -> {
            "$recordingFormatText, $sampleRateText, $bitRateText, $channelCountText"
        }
        else -> {
            "$recordingFormatText, $sampleRateText, $channelCountText"
        }
    }
}

fun recordInfoCombinedShortText(
    recordingFormat: String,
    recordSizeText: String,
    bitrateText: String,
    sampleRateText: String,
): String {
    return if (bitrateText.isNotEmpty()) {
        "$recordSizeText, $recordingFormat, $bitrateText, $sampleRateText"
    } else {
        "$recordSizeText, $recordingFormat, $sampleRateText"
    }
}

/**
 * Whether a record's description can be embedded into the audio file as a COMMENT tag.
 * 3GP containers don't support comment metadata, so embedding is unavailable for them.
 */
fun isDescriptionFileWriteSupported(format: String): Boolean {
    return !format.equals(RecordingFormat.ThreeGp.value, ignoreCase = true)
}

fun Record.toInfoCombinedText(context: Context): String {
    return recordInfoCombinedShortText(
        recordingFormat = this.format,
        recordSizeText = Formatter.formatShortFileSize(context, this.size),
        bitrateText = if (this.bitrate > 0) {
            context.getString(R.string.value_kbps, this.bitrate/1000)
        } else "",
        sampleRateText = context.getString(
            R.string.value_khz,
            this.sampleRate/1000
        ),
    )
}

@Composable
fun ComposableLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onEvent: (LifecycleOwner, Lifecycle.Event) -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, event ->
            onEvent(source, event)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun <viewModel : LifecycleObserver> viewModel.observeLifecycleEvents(lifecycle: Lifecycle) {
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(this@observeLifecycleEvents)
        onDispose {
            lifecycle.removeObserver(this@observeLifecycleEvents)
        }
    }
}

@SuppressWarnings("MagicNumber")
fun formatDuration(
    resources: Resources,
    durationMillis: Long,
): String {
    val totalSeconds = durationMillis / 1000
    val years = (totalSeconds / (365 * 24 * 60 * 60)).toInt()
    val days = ((totalSeconds % (365 * 24 * 60 * 60)) / (24 * 60 * 60)).toInt()
    val hours = (totalSeconds % (24 * 60 * 60)) / (60 * 60)
    val minutes = (totalSeconds % (60 * 60)) / 60
    val seconds = totalSeconds % 60

    val formattedParts = mutableListOf<String>()

    if (years > 0) formattedParts.add("$years${resources.getQuantityString(R.plurals.years, years)}")
    if (days > 0) formattedParts.add("$days${resources.getQuantityString(R.plurals.days, days)}")
    if (hours > 0) {
        formattedParts.add(String.format(Locale.getDefault(),"%02dh:%02dm:%02ds", hours, minutes, seconds))
    } else {
        formattedParts.add(String.format(Locale.getDefault(),"%02dm:%02ds", minutes, seconds))
    }
    return formattedParts.joinToString(" ")
}

/**
 * Permanently deletes all records from the recycle bin that have exceeded the
 * maximum retention duration defined by [AppConstants.RECORD_IN_TRASH_MAX_DURATION].
 *
 * @receiver The [RecordsDataSource] instance.
 */
suspend fun RecordsDataSource.removeOutdatedTrashRecords() {
    val currentTime = System.currentTimeMillis()
    this.getMovedToRecycleRecords().forEach { removedRecord ->
        if (currentTime > removedRecord.removed + AppConstants.RECORD_IN_TRASH_MAX_DURATION) {
            this.deleteRecordAndFileForever(removedRecord.id)
        }
    }
}

/**
 * Generates a new record name based on the specified [NameFormat].
 * This extension function creates a unique name for an audio recording according to
 * the format type. The record counter is always incremented regardless of the format used.
 *
 * The name format depends on the [NameFormat] type:
 * - [NameFormat.Record]: Numbered format (e.g., "Record-1")
 * - [NameFormat.Date]: Date-based format
 * - [NameFormat.DateUs]: US date format
 * - [NameFormat.DateIso8601]: ISO 8601 date format
 * - [NameFormat.Timestamp]: Unix timestamp format
 * @param prefs The preferences instance used to access and increment the record counter.
 * @return A formatted string to be used as the record name.
 */
fun NameFormat.getNewRecordName(prefs: PrefsV2): String {
    val recordName = when (this) {
        NameFormat.Record -> {
            FileUtil.generateRecordNameCounted(prefs.recordCounter)
        }
        NameFormat.Date -> FileUtil.generateRecordNameDateVariant()
        NameFormat.DateUs -> FileUtil.generateRecordNameDateUS()
        NameFormat.DateIso8601 -> FileUtil.generateRecordNameDateISO8601()
        NameFormat.Timestamp -> FileUtil.generateRecordNameMills()
    }
    prefs.incrementRecordCounter()

    return recordName
}
