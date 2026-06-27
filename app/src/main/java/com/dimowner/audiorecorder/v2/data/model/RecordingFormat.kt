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
package com.dimowner.audiorecorder.v2.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class RecordingFormat(val value: String, val index: Int) : Parcelable {
    M4a("m4a", 0), Wav("wav", 1), ThreeGp("3gp", 2);

    /** Recording capabilities (supported sample rates, bitrates and channel counts) of this format. */
    val config: FormatConfig
        get() = when (this) {
            M4a -> FormatConfig(
                format = this,
                supportedSampleRates = listOf(
                    SampleRate.SR8000,
                    SampleRate.SR16000,
                    SampleRate.SR22050,
                    SampleRate.SR32000,
                    SampleRate.SR44100,
                    SampleRate.SR48000
                ),
                supportedBitRates = listOf(
                    BitRate.BR48,
                    BitRate.BR96,
                    BitRate.BR128,
                    BitRate.BR192,
                    BitRate.BR256,
                    BitRate.BR288,
                ),
                supportedChannelCounts = listOf(ChannelCount.Mono, ChannelCount.Stereo)
            )
            Wav -> FormatConfig(
                format = this,
                supportedSampleRates = listOf(
                    SampleRate.SR8000,
                    SampleRate.SR16000,
                    SampleRate.SR22050,
                    SampleRate.SR32000,
                    SampleRate.SR44100,
                    SampleRate.SR48000
                ),
                supportedBitRates = emptyList(),
                supportedChannelCounts = listOf(ChannelCount.Mono, ChannelCount.Stereo)
            )
            ThreeGp -> FormatConfig(
                format = this,
                supportedSampleRates = listOf(SampleRate.SR8000, SampleRate.SR16000),
                supportedBitRates = emptyList(),
                supportedChannelCounts = listOf(ChannelCount.Mono),
            )
        }

    /** Whether this format is recorded with a user-configurable bitrate. */
    val hasBitrate: Boolean get() = config.hasBitrate
}

fun String.convertToRecordingFormat(): RecordingFormat? {
    return if (this.equals(RecordingFormat.M4a.value, true)) RecordingFormat.M4a
    else if (this.equals(RecordingFormat.Wav.value, true)) RecordingFormat.Wav
    else if (this.equals(RecordingFormat.ThreeGp.value, true)) RecordingFormat.ThreeGp
    else null
}
