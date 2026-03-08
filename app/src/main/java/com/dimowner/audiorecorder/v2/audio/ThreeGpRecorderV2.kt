/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import android.content.Context
import android.media.MediaRecorder
import com.dimowner.audiorecorder.AppConstants.RECORD_SAMPLE_RATE_8000
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreeGpRecorderV2 @Inject constructor(
    @param:ApplicationContext applicationContext: Context,
    coroutineScope: CoroutineScope,
) : MediaRecorderBase(applicationContext, coroutineScope) {

    override val recordingLogTag: String = "3GP "

    override fun configureRecorder(
        recorder: MediaRecorder,
        channelCount: Int,
        sampleRate: Int,
        bitrate: Int,
    ) {
        recorder.apply {
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            if (sampleRate > RECORD_SAMPLE_RATE_8000) {
                // AMR-WB records at 16000 Hz frequency, ~23 kbps bitrate
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
            } else {
                // AMR-NB records at 8000 Hz frequency, ~12 kbps bitrate
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
        }
    }
}
