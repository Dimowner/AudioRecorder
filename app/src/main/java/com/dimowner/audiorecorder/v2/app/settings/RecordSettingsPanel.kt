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

package com.dimowner.audiorecorder.v2.app.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate

@Composable
internal fun RecordSettingsPanel(
    recordingSettings: List<RecordingSetting>,
    enabled: Boolean,
    onAction: (SettingsScreenAction) -> Unit,
    onShowInfo: (AnnotatedString) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExpandedBitRatePanel = remember { mutableStateOf(true) }

    Column(modifier = modifier) {
        val infoFormat = htmlStringResources(
            R.string.info_format_m4a_html,
            R.string.info_format_wav_html,
            R.string.info_format_3gp_html
        )
        SettingSelector(
            name = stringResource(id = R.string.recording_format),
            chips = recordingSettings.map { it.recordingFormat },
            onSelect = {
                onAction(SettingsScreenAction.SelectRecordingFormat(it.value))
            },
            onClickInfo = {
                onShowInfo(infoFormat)
            },
            enabled = enabled
        )
        val selectedFormat =
            recordingSettings.firstOrNull { it.recordingFormat.isSelected }
        val infoFrequency = htmlStringResources(
            R.string.info_frequency_header_html,
            R.string.info_frequency_48khz_html,
            R.string.info_frequency_44_1khz_html,
            R.string.info_frequency_22khz_html,
            R.string.info_frequency_8khz_html
        )
        SettingSelector(
            name = stringResource(id = R.string.sample_rate),
            chips = selectedFormat?.sampleRates ?: emptyList(),
            onSelect = {
                onAction(SettingsScreenAction.SelectSampleRate(it.value))
            },
            onClickInfo = {
                onShowInfo(infoFrequency)
            },
            enabled = enabled
        )
        if (isExpandedBitRatePanel.value != !selectedFormat?.bitRates.isNullOrEmpty()) {
            isExpandedBitRatePanel.value = !selectedFormat?.bitRates.isNullOrEmpty()
        }
        AnimatedVisibility(visible = isExpandedBitRatePanel.value) {
            val infoBitrate = htmlStringResources(
                R.string.info_bitrate_header_html,
                R.string.info_bitrate_256kbps_html,
                R.string.info_bitrate_192kbps_html,
                R.string.info_bitrate_128kbps_html,
                R.string.info_bitrate_96kbps_html,
                R.string.info_bitrate_48kbps_html
            )
            SettingSelector(
                name = stringResource(id = R.string.bitrate),
                chips = selectedFormat?.bitRates ?: emptyList(),
                onSelect = {
                    onAction(SettingsScreenAction.SelectBitrate(it.value))
                },
                onClickInfo = {
                    onShowInfo(infoBitrate)
                },
                enabled = enabled
            )
        }
        val infoChannels = htmlStringResource(R.string.info_channels_html)
        SettingSelector(
            name = stringResource(id = R.string.channels),
            chips = selectedFormat?.channelCounts ?: emptyList(),
            onSelect = {
                onAction(SettingsScreenAction.SelectChannelCount(it.value))
            },
            onClickInfo = {
                onShowInfo(infoChannels)
            },
            enabled = enabled
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecordSettingsPanelPreview() {
    RecordSettingsPanel(
        recordingSettings = listOf(
            RecordingSetting(
                recordingFormat = ChipItem(id = 0, value = RecordingFormat.M4a, name = "M4a", isSelected = true),
                sampleRates = listOf(
                    ChipItem(id = 0, value = SampleRate.SR32000, name = "32 kHz", isSelected = false),
                    ChipItem(id = 1, value = SampleRate.SR44100, name = "44.1 kHz", isSelected = true),
                    ChipItem(id = 2, value = SampleRate.SR48000, name = "48 kHz", isSelected = false),
                ),
                bitRates = listOf(
                    ChipItem(id = 0, value = BitRate.BR96, name = "96 kbps", isSelected = false),
                    ChipItem(id = 1, value = BitRate.BR128, name = "128 kbps", isSelected = true),
                    ChipItem(id = 2, value = BitRate.BR192, name = "192 kbps", isSelected = false),
                ),
                channelCounts = listOf(
                    ChipItem(id = 0, value = ChannelCount.Mono, name = "Mono", isSelected = false),
                    ChipItem(id = 1, value = ChannelCount.Stereo, name = "Stereo", isSelected = true),
                )
            ),
        ),
        enabled = true,
        onAction = {},
        onShowInfo = {},
    )
}
