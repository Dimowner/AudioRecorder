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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import timber.log.Timber

@Composable
fun WelcomeSetupSettingsScreen(
    onPopBackStack: () -> Unit,
    onApplySettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state.observeAsState()

    val openInfoDialog = remember { mutableStateOf(false) }
    val infoText = remember { mutableStateOf("") }

    val isExpandedBitRatePanel = remember { mutableStateOf(true) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                Timber.d("SettingsScreen: onCreate")
                viewModel.initSettings()
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                stringResource(R.string.setup),
                onBackPressed = { onPopBackStack() })
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = true)
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsItemCheckBox(
                        state.value?.isDynamicColors ?: false,
                        stringResource(R.string.dynamic_theme_colors),
                        R.drawable.ic_palette_outline,
                        {
                            viewModel.setDynamicTheme(it)
                        })
                }
                SettingsItemCheckBox(
                    state.value?.isDarkTheme ?: false,
                    stringResource(R.string.dark_theme),
                    R.drawable.ic_dark_mode,
                    {
                        viewModel.setDarkTheme(it)
                    })
                DropDownSetting(
                    items = state.value?.nameFormats ?: emptyList(),
                    selectedItem = state.value?.selectedNameFormat,
                    onSelect = {
                        viewModel.setNameFormat(it)
                    }
                )
                SettingSelector(
                    name = stringResource(id = R.string.recording_format),
                    chips = state.value?.recordingSettings?.map { it.recordingFormat } ?: emptyList(),
                    onSelect = {
                        viewModel.selectRecordingFormat(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_format)
                        openInfoDialog.value = true
                    }
                )
                val selectedFormat = state.value?.recordingSettings?.firstOrNull { it.recordingFormat.isSelected }
                SettingSelector(
                    name = stringResource(id = R.string.sample_rate),
                    chips = selectedFormat?.sampleRates ?: emptyList(),
                    onSelect = {
                        viewModel.selectSampleRate(it.value)
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
                            viewModel.selectBitrate(it.value)
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
                        viewModel.selectChannelCount(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_channels)
                        openInfoDialog.value = true
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Column {
                Row {
                    Icon(
                        modifier = Modifier
                            .padding(4.dp)
                            .wrapContentSize()
                            .align(Alignment.CenterVertically),
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = stringResource(id = R.string.info)
                    )
                    Column {
                        Text(
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .padding(4.dp, 8.dp, 4.dp, 0.dp),
                            textAlign = TextAlign.Start,
                            text = stringResource(
                                id = R.string.size_per_min,
                                state.value?.sizePerMin ?: ""
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        )
                        val selectedFormat = state.value?.recordingSettings?.firstOrNull {
                            it.recordingFormat.isSelected
                        }
                        Text(
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .padding(4.dp, 4.dp, 4.dp, 0.dp),
                            textAlign = TextAlign.Start,
                            text = selectedFormat?.recordingFormat?.value?.toFormatInfo() ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        )
                    }
                }
                Row {
                    Button(
                        modifier = Modifier
                            .padding(8.dp)
                            .wrapContentHeight()
                            .weight(1F),
                        onClick = { viewModel.resetRecordingSettings() }
                    ) {
                        Text(
                            text = stringResource(id = R.string.btn_reset),
                            fontSize = 18.sp,
                        )
                    }
                    Button(
                        modifier = Modifier
                            .padding(8.dp)
                            .wrapContentHeight()
                            .weight(1F),
                        onClick = {
                            viewModel.executeFirstRun()
                            onApplySettings()
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.btn_apply),
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            if (openInfoDialog.value) {
                SettingsInfoDialog(openInfoDialog, infoText.value)
            }
        }
    }
}

@Composable
fun RecordingFormat.toFormatInfo(): String {
    return when (this) {
        RecordingFormat.M4a -> stringResource(id = R.string.info_m4a)
        RecordingFormat.Wav -> stringResource(id = R.string.info_wav)
        RecordingFormat.ThreeGp -> stringResource(id = R.string.info_3gp)
    }
}

@Preview
@Composable
fun WelcomeSetupSettingsScreenPreview() {
    WelcomeSetupSettingsScreen({}, {})
}
