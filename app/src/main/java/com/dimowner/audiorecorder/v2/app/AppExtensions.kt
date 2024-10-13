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
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.settings.convertToText
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import java.util.Locale

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
    return when (recordingFormat) {
        RecordingFormat.M4a -> {
            "$recordingFormatText, $sampleRateText, $bitRateText, $channelCountText"
        }
        RecordingFormat.Wav,
        RecordingFormat.ThreeGp -> {
            "$recordingFormatText, $sampleRateText, $channelCountText"
        }
        else -> ""
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
