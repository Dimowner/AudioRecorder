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

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.app.formatDuration
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import timber.log.Timber

@Composable
internal fun SettingsScreen(
    onPopBackStack: () -> Unit,
    showDeletedRecordsScreen: () -> Unit,
    uiState: SettingsState,
    onAction: (SettingsScreenAction) -> Unit,
) {
    val context = LocalContext.current

    val openInfoDialog = remember { mutableStateOf(false) }
    val openWarningDialog = remember { mutableStateOf(false) }
    val infoText = remember { mutableStateOf("") }
    val warningText = remember { mutableStateOf("") }

    val isExpandedBitRatePanel = remember { mutableStateOf(true) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                Timber.d("SettingsScreen: onCreate")
                onAction(SettingsScreenAction.InitSettingsScreen)
            }
            Lifecycle.Event.ON_START -> {
                Timber.d("SettingsScreen: On Start")
            }

            Lifecycle.Event.ON_RESUME -> {
                Timber.d("SettingsScreen: On Resume")
            }

            Lifecycle.Event.ON_PAUSE -> {
                Timber.d("SettingsScreen: On Pause")
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.d("SettingsScreen: On Stop")
            }

            Lifecycle.Event.ON_DESTROY -> {
                Timber.d("SettingsScreen: On Destroy")
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                stringResource(R.string.settings),
                onBackPressed = {
                    onPopBackStack()
                })
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                SettingsItem(stringResource(R.string.trash), R.drawable.ic_delete) {
                    showDeletedRecordsScreen()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsItemCheckBox(
                        uiState.isDynamicColors,
                        stringResource(R.string.dynamic_theme_colors),
                        R.drawable.ic_palette_outline,
                        {
                            onAction(SettingsScreenAction.SetDynamicTheme(it))
                        })
                }
                SettingsItemCheckBox(
                    uiState.isDarkTheme,
                    stringResource(R.string.dark_theme),
                    R.drawable.ic_dark_mode,
                    {
                        onAction(SettingsScreenAction.SetDarkTheme(it))
                    })
                SettingsItemCheckBox(
                    uiState.isKeepScreenOn,
                    stringResource(R.string.keep_screen_on),
                    R.drawable.ic_lightbulb_on,
                    {
                        onAction(SettingsScreenAction.SetKeepScreenOn(it))
                    })
                SettingsItemCheckBox(
                    uiState.isShowRenameDialog,
                    stringResource(R.string.ask_to_rename),
                    R.drawable.ic_pencil,
                    {
                        onAction(SettingsScreenAction.SetShowRenamingDialog(it))
                    })
                DropDownSetting(
                    items = uiState.nameFormats,
                    selectedItem = uiState.selectedNameFormat,
                    onSelect = {
                        onAction(SettingsScreenAction.SetNameFormat(it))
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                ResetRecordingSettingsPanel(
                    stringResource(id = R.string.size_per_min, uiState.sizePerMin),
                    uiState.recordingSettingsText
                ) {
                    onAction(SettingsScreenAction.ResetRecordingSettings)
                }
                SettingSelector(
                    name = stringResource(id = R.string.recording_format),
                    chips = uiState.recordingSettings.map { it.recordingFormat },
                    onSelect = {
                        onAction(SettingsScreenAction.SelectRecordingFormat(it.value))
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_format)
                        openInfoDialog.value = true
                    }
                )
                val selectedFormat = uiState.recordingSettings.firstOrNull { it.recordingFormat.isSelected }
                SettingSelector(
                    name = stringResource(id = R.string.sample_rate),
                    chips = selectedFormat?.sampleRates ?: emptyList(),
                    onSelect = {
                        onAction(SettingsScreenAction.SelectSampleRate(it.value))
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_frequency)
                        openInfoDialog.value = true
                    }
                )
                if (isExpandedBitRatePanel.value != !selectedFormat?.bitRates.isNullOrEmpty()) {
                    isExpandedBitRatePanel.value = !selectedFormat?.bitRates.isNullOrEmpty()
                }
                AnimatedVisibility(visible = isExpandedBitRatePanel.value) {
                    SettingSelector(
                        name = stringResource(id = R.string.bitrate),
                        chips = selectedFormat?.bitRates ?: emptyList(),
                        onSelect = {
                            onAction(SettingsScreenAction.SelectBitrate(it.value))
                        },
                        onClickInfo = {
                            infoText.value = context.getString(R.string.info_bitrate)
                            openInfoDialog.value = true
                        }
                    )
                }
                SettingSelector(
                    name = stringResource(id = R.string.channels),
                    chips = selectedFormat?.channelCounts ?: emptyList(),
                    onSelect = {
                        onAction(SettingsScreenAction.SelectChannelCount(it.value))
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_channels)
                        openInfoDialog.value = true
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingsItem(stringResource(R.string.rate_app), R.drawable.ic_thumbs) {
                    rateApp(context)
                }
                SettingsItem(stringResource(R.string.request), R.drawable.ic_chat_bubble) {
                    requestFeature(context) {
                        warningText.value = it
                        openWarningDialog.value = true
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                InfoTextView(
                    stringResource(
                        id = R.string.total_record_count,
                        (uiState.totalRecordCount)
                    )
                )
                InfoTextView(
                    stringResource(
                        id = R.string.total_duration,
                        formatDuration(context.resources, (uiState.totalRecordDuration))
                    )
                )
                InfoTextView(
                    stringResource(
                        id = R.string.available_space,
                        (uiState.availableSpace)
                    )
                )
                AppInfoView(uiState.appName, uiState.appVersion)
                Spacer(modifier = Modifier.size(8.dp))
            }
            if (openInfoDialog.value) {
                SettingsInfoDialog(openInfoDialog, infoText.value)
            }
            if (openWarningDialog.value) {
                SettingsWarningDialog(openWarningDialog, warningText.value)
            }
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    SettingsScreen({}, {}, uiState = SettingsState(
        isDynamicColors = true,
        isDarkTheme = false,
        isKeepScreenOn = false,
        isShowRenameDialog = true,
        nameFormats = listOf(NameFormatItem(NameFormat.Record, "Name text")),
        selectedNameFormat = NameFormatItem(NameFormat.Record, "Name text"),
        recordingSettings = listOf(RecordingSetting(
                recordingFormat = ChipItem(id = 0, value = RecordingFormat.M4a, name = "M4a", isSelected = true),
                sampleRates = listOf(
                    ChipItem(id = 0, value = SampleRate.SR16000, name = "16 kHz", isSelected = false),
                    ChipItem(id = 0, value = SampleRate.SR22500, name = "22.5 kHz", isSelected = false),
                    ChipItem(id = 0, value = SampleRate.SR32000, name = "32 kHz", isSelected = false),
                    ChipItem(id = 1, value = SampleRate.SR44100, name = "44.1 kHz", isSelected = true),
                    ChipItem(id = 1, value = SampleRate.SR48000, name = "48 kHz", isSelected = false),
                ),
                bitRates = listOf(
                    ChipItem(id = 0, value = BitRate.BR48, name = "48 kbps", isSelected = false),
                    ChipItem(id = 0, value = BitRate.BR96, name = "96 kbps", isSelected = false),
                    ChipItem(id = 1, value = BitRate.BR128, name = "128 kbps", isSelected = true),
                    ChipItem(id = 1, value = BitRate.BR192, name = "192 kbps", isSelected = false),
                ),
                channelCounts = listOf(
                    ChipItem(id = 0, value = ChannelCount.Mono, name = "Mono", isSelected = false),
                    ChipItem(id = 1, value = ChannelCount.Stereo, name = "Stereo", isSelected = true),
                )
            ),
        ),
        sizePerMin = "10",
        recordingSettingsText = "recordingSettingsText",
        rateAppLink = "rateAppLink",
        feedbackEmail = "feedbackEmail",
        totalRecordCount = 10,
        totalRecordDuration = 1000500,
        availableSpace = 1010101010,
        appName = "App Name",
        appVersion = "1.0.0",
    ), {})
}