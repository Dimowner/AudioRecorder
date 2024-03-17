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

import com.dimowner.audiorecorder.v2.app.settings.convertToText
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate

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
    sampleRateText: String,
): String {
    return "$recordSizeText, $recordingFormat, $sampleRateText"
}
