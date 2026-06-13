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
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.ScrollableTitleBar
import com.dimowner.audiorecorder.v2.app.components.AudioSourceSelector
import com.dimowner.audiorecorder.v2.app.components.MAX_CONTENT_WIDTH_NARROW
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import timber.log.Timber
import androidx.compose.ui.platform.LocalResources

@OptIn(ExperimentalMaterial3Api::class)
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
    val infoTextAnnotated = remember { mutableStateOf<AnnotatedString?>(null) }
    val warningText = remember { mutableStateOf("") }

    val appInfoTapCount = remember { mutableIntStateOf(0) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ScrollableTitleBar(
                title = stringResource(R.string.settings),
                onBackPressed = { onPopBackStack() },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep settings content readable on large screens instead of stretching edge-to-edge.
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = MAX_CONTENT_WIDTH_NARROW)
                    .fillMaxSize()
                    .padding(3.dp, 0.dp)
                    .verticalScroll(rememberScrollState())
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
                AuthorNameSettingRow(
                    currentAuthorName = uiState.recordAuthorName,
                    onAction = onAction,
                )
                ResetRecordingSettingsPanel(
                    sizePerMin = stringResource(id = R.string.size_per_min, uiState.sizePerMin),
                    recordingSettingsText = uiState.recordingSettingsText,
                    onClick = {
                        onAction(SettingsScreenAction.ResetRecordingSettings)
                    },
                    enabled = uiState.isRecordingSettingEditable,
                )
                RecordSettingsPanel(
                    recordingSettings = uiState.recordingSettings,
                    enabled = uiState.isRecordingSettingEditable,
                    onAction = onAction,
                    onShowInfo = {
                        infoText.value = ""
                        infoTextAnnotated.value = it
                        openInfoDialog.value = true
                    },
                )
                Spacer(modifier = Modifier.size(8.dp))
                val infoAudioSource = htmlStringResource(R.string.info_audio_source_html)
                AudioSourceSelector(
                    selectedSource = uiState.selectedAudioSource,
                    options = uiState.audioSourceOptions,
                    onSourceSelected = { audioSource ->
                        onAction(SettingsScreenAction.SetAudioSource(audioSource))
                    },
                    onInfoClick = {
                        infoText.value = ""
                        infoTextAnnotated.value = infoAudioSource
                        openInfoDialog.value = true
                    },
                    enabled = uiState.isRecordingSettingEditable
                )
                Spacer(modifier = Modifier.size(8.dp))
                MaxDurationSettingRow(
                    currentValue = uiState.maxRecordingDurationMinutes,
                    onAction = onAction
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
                        formatAvailableRecordingTime(uiState.totalRecordDuration, LocalResources.current)
                    )
                )
                InfoTextView(
                    stringResource(
                        id = R.string.available_space,
                        "${formatAvailableRecordingTime(uiState.availableSpaceMills, LocalResources.current)} (${Formatter.formatShortFileSize(context, uiState.availableSpaceBytes)})"
                    )
                )
                if (uiState.isLegacyAppUser) {
                    Spacer(modifier = Modifier.size(8.dp))
                    LegacyAppSwitchPanel(
                        onSwitch = { onAction(SettingsScreenAction.SetAppV2(false)) }
                    )
                }
                AppInfoView(uiState.appName, uiState.appVersion) {
                    //Click 10 times to show 'Switch to Legacy App' button
                    if (!uiState.isLegacyAppUser) {
                        appInfoTapCount.intValue++
                        val count = appInfoTapCount.intValue
                        when {
                            count >= 10 -> {
                                onAction(SettingsScreenAction.UnlockLegacyAppSwitch)
                                appInfoTapCount.intValue = 0
                            }
                            count >= 5 -> {
                                val remaining = 10 - count
                                Toast.makeText(context, "$remaining", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            if (openInfoDialog.value) {
                val annotated = infoTextAnnotated.value
                if (annotated != null) {
                    SettingsInfoDialog(openInfoDialog, annotated)
                } else {
                    SettingsInfoDialog(openInfoDialog, infoText.value)
                }
            }
            if (openWarningDialog.value) {
                SettingsWarningDialog(openWarningDialog, warningText.value)
            }
        }
    }
}

@Composable
internal fun MaxDurationSettingRow(
    currentValue: Int,
    onAction: (SettingsScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showDialog = remember { mutableStateOf(false) }

    // Convert minutes to hours and minutes for display and dialog
    val hours = currentValue / 60
    val minutes = currentValue % 60

    MaxDurationSettingItem(
        currentHours = hours,
        currentMinutes = minutes,
        onClick = { showDialog.value = true },
        modifier = modifier,
    )

    if (showDialog.value) {
        DurationPickerDialog(
            currentHours = hours,
            currentMinutes = minutes,
            onDismiss = { showDialog.value = false },
            onConfirm = { newHours, newMinutes ->
                val totalMinutes = (newHours * 60) + newMinutes
                onAction(SettingsScreenAction.SetMaxRecordingDuration(totalMinutes))
                showDialog.value = false
            }
        )
    }
}

@Composable
internal fun AuthorNameSettingRow(
    currentAuthorName: String,
    onAction: (SettingsScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showDialog = remember { mutableStateOf(false) }
    val showInfoDialog = remember { mutableStateOf(false) }

    AuthorNameSettingItem(
        currentAuthorName = currentAuthorName,
        onClick = { showDialog.value = true },
        onClickInfo = { showInfoDialog.value = true },
        modifier = modifier,
    )

    if (showDialog.value) {
        AuthorNameEditDialog(
            currentName = currentAuthorName,
            onDismiss = { showDialog.value = false },
            onConfirm = { newName ->
                onAction(SettingsScreenAction.SetRecordAuthorName(newName))
                showDialog.value = false
            }
        )
    }

    if (showInfoDialog.value) {
        SettingsInfoDialog(showInfoDialog, stringResource(R.string.info_record_author_name))
    }
}

@Composable
fun MaxDurationSettingItem(
    currentHours: Int,
    currentMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentSize(),
            painter = painterResource(id = R.drawable.ic_access_time),
            contentDescription = stringResource(R.string.recording_duration),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
        ) {
            Text(
                text = stringResource(R.string.recording_duration),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.recording_duration_subtitle),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Duration badge/chip
        Card(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 8.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = formatDurationDisplay(currentHours, currentMinutes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Formats a duration specified in hours and minutes into a human-readable string.
 *
 * @param hours The number of hours (0-23)
 * @param minutes The number of minutes (0-59)
 * @return A formatted string like "2h 30m", "5h 0m", "45m", or "0m"
 *
 * Examples:
 * - formatDurationDisplay(2, 30) returns "2h 30m"
 * - formatDurationDisplay(5, 0) returns "5h 0m"
 * - formatDurationDisplay(0, 45) returns "45m"
 * - formatDurationDisplay(0, 0) returns "0m"
 */
@Composable
fun formatDurationDisplay(hours: Int, minutes: Int): String {
    return when {
        hours > 0 && minutes > 0 -> {
            stringResource(R.string.duration_hours_and_minutes, hours, minutes)
        }
        hours > 0 -> {
            stringResource(R.string.duration_hours_and_minutes, hours, minutes)
        }
        minutes > 0 -> {
            stringResource(R.string.duration_minutes, minutes)
        }
        else -> {
            stringResource(R.string.duration_minutes, 0)
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    SettingsScreen({}, {}, uiState = SettingsState(
        isDynamicColors = true,
        isDarkTheme = false,
        isAppV2 = false,
        isKeepScreenOn = false,
        isShowRenameDialog = true,
        isRecordingSettingEditable = true,
        nameFormats = listOf(NameFormatItem(NameFormat.Record, "Name text")),
        selectedNameFormat = NameFormatItem(NameFormat.Record, "Name text"),
        recordingSettings = listOf(RecordingSetting(
                recordingFormat = ChipItem(id = 0, value = RecordingFormat.M4a, name = "M4a", isSelected = true),
                sampleRates = listOf(
                    ChipItem(id = 0, value = SampleRate.SR16000, name = "16 kHz", isSelected = false),
                    ChipItem(id = 0, value = SampleRate.SR22050, name = "22.05 kHz", isSelected = false),
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
        totalRecordCount = 10,
        totalRecordDuration = 1000500,
        availableSpaceMills = 1010101010,
        availableSpaceBytes = 2020202020,
        appName = "App Name",
        appVersion = "1.0.0",
        maxRecordingDurationMinutes = 120,
        recordAuthorName = "Author name",
        isLegacyAppUser = true,
    ), {})
}
