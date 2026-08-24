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

/**
 * Describes the recording capabilities of a single [RecordingFormat].
 *
 * Each format only supports a specific subset of sample rates, bitrates and channel counts.
 * Centralising that knowledge here keeps the settings screen, record creation and record
 * update logic free of per-format `when` branches:
 *  - The settings screen builds its chips directly from the supported lists.
 *  - Record create/update logic relies on [hasBitrate] to decide whether a bitrate is relevant.
 *
 * @property supportedSampleRates Sample rates the format can be recorded with (never empty).
 * @property supportedBitRates Bitrates the format can be recorded with. Empty for formats whose
 * bitrate is not user-configurable (e.g. WAV is uncompressed, 3GP derives it from the sample rate).
 * @property supportedChannelCounts Channel counts the format can be recorded with (never empty).
 */
@Parcelize
data class FormatConfig(
    val format: RecordingFormat,
    val supportedSampleRates: List<SampleRate>,
    val supportedBitRates: List<BitRate>,
    val supportedChannelCounts: List<ChannelCount>,
) : Parcelable {

    /** Whether this format is recorded with a user-configurable bitrate. */
    val hasBitrate: Boolean get() = supportedBitRates.isNotEmpty()

    fun isSampleRateSupported(sampleRate: SampleRate): Boolean =
        sampleRate in supportedSampleRates

    fun isBitRateSupported(bitRate: BitRate): Boolean =
        bitRate in supportedBitRates

    fun isChannelCountSupported(channelCount: ChannelCount): Boolean =
        channelCount in supportedChannelCounts

    /**
     * The supported bitrates the device can actually record with, i.e. those at or below
     * [maxBitRate] (see `DeviceRecordingCapabilities`).
     *
     * The lowest supported bitrate is always kept, even when it is above the limit: a settings
     * screen with no bitrate to choose from would be worse than one offering a value the device
     * may still clip.
     */
    fun bitRatesUpTo(maxBitRate: Int): List<BitRate> {
        if (supportedBitRates.isEmpty()) return emptyList()
        return supportedBitRates.filter { it.value <= maxBitRate }
            .ifEmpty { listOf(supportedBitRates.minBy { it.value }) }
    }
}