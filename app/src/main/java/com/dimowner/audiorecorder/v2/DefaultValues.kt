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

package com.dimowner.audiorecorder.v2

import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.SortOrder

object DefaultValues {
    const val IS_APP_V2: Boolean = false
    const val IS_LEGACY_APP_USER: Boolean = false
    const val IS_DARK_THEME: Boolean = true
    const val IS_DYNAMIC_THEME: Boolean = false
    const val IS_ASK_TO_RENAME: Boolean = true
    const val IS_KEEP_SCREEN_ON: Boolean = false
    const val IS_SAVE_DESCRIPTION_TO_FILE: Boolean = true

    val DefaultSampleRate: SampleRate = SampleRate.SR44100
    val DefaultBitRate: BitRate = BitRate.BR128
    val DefaultChannelCount: ChannelCount = ChannelCount.Stereo
    val DefaultAudioSource: AudioSource = AudioSource.DEFAULT

    val DefaultNameFormat: NameFormat = NameFormat.Record
    val DefaultRecordingFormat: RecordingFormat = RecordingFormat.M4a
    val DefaultSortOrder: SortOrder = SortOrder.DateAsc

    /** AMR-NB (Adaptive Multi-Rate Narrowband) bitrate (Bits per second) */
    const val MAX_3GP_BITRATE_NB: Int = 12000

    /** Adaptive Multi-Rate Wideband (AMR-WB) bitrate (Bits per second) */
    const val MAX_3GP_BITRATE_WB: Int = 24000

    val Default3GpSampleRate: SampleRate = SampleRate.SR16000
    val Default3GpChannelCount: ChannelCount = ChannelCount.Mono

    const val DEFAULT_RECORD_AUTHOR_NAME = "Audio Recorder"

    @Deprecated("Should not be used anymore")
    const val DELETED_RECORD_MARK = ".deleted"
}
