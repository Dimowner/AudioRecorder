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

package com.dimowner.audiorecorder.v2.app.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.RenameAlertDialog
import com.dimowner.audiorecorder.v2.app.SaveAsDialog
import com.dimowner.audiorecorder.v2.app.calculateGridStep
import com.dimowner.audiorecorder.v2.app.calculateScale
import com.dimowner.audiorecorder.v2.app.components.WaveformComposeView
import com.dimowner.audiorecorder.v2.app.components.WaveformState
import com.google.gson.Gson
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
internal fun HomeScreen(
    showRecordsScreen: () -> Unit,
    showSettingsScreen: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    uiState: HomeScreenState,
    event: HomeScreenEvent?,
    onAction: (HomeScreenAction) -> Unit
) {

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("HomeScreen: On Start")
                onAction(HomeScreenAction.InitHomeScreen)
            }
            else -> {}
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val showRenameDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showSaveAsDialog = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Handle the selected document URI here
        if (uri != null) {
            onAction(HomeScreenAction.ImportAudioFile(uri))
        }
    }

    val context = LocalContext.current

    LaunchedEffect(key1 = event) {
        when (event) {
            HomeScreenEvent.ShowImportErrorError -> {
                Timber.v("ON EVENT: ShowImportErrorError")
            }

            is HomeScreenEvent.RecordInformationEvent -> {
                val json = Uri.encode(Gson().toJson(event.recordInfo))
                Timber.v("ON EVENT: ShareRecord json = $json")
                showRecordInfoScreen(json)
            }
            is HomeScreenEvent.RecordMovedToTrashSnack -> {
                scope.launch {
                    val message = if (event.recordName != null) {
                        context.getString(R.string.msg_recording_moved_to_trash, event.recordName)
                    } else {
                        context.getString(R.string.msg_recording_canceled)
                    }
                    val result = snackbarHostState
                        .showSnackbar(
                            message = message,
                            actionLabel = context.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short
                        )
                    when (result) {
                        SnackbarResult.ActionPerformed -> {
                            onAction(HomeScreenAction.RestoreRecordFromTrash(event.recordId))
                        }
                        SnackbarResult.Dismissed -> {
                            /* Handle snackbar dismissed */
                        }
                    }
                }
            }
            is HomeScreenEvent.ShowInfoSnack -> {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                }
            }
            is HomeScreenEvent.ShowErrorSnack -> {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                }
            }

            else -> {
                Timber.v("ON EVENT: Unknown")
                //Do nothing
            }
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopAppBar(
                    onImportClick = {
                        launcher.launch("audio/*")
                    },
                    onHomeMenuItemClick = {
                        when (it) {
                            HomeDropDownMenuItemId.SHARE -> {
                                onAction(HomeScreenAction.ShareActiveRecord)
                            }

                            HomeDropDownMenuItemId.INFORMATION -> {
                                onAction(HomeScreenAction.ShowActiveRecordInfo)
                            }

                            HomeDropDownMenuItemId.RENAME -> {
                                showRenameDialog.value = true
                            }

                            HomeDropDownMenuItemId.OPEN_WITH -> {
                                onAction(HomeScreenAction.OpenActiveRecordWithAnotherApp)
                            }

                            HomeDropDownMenuItemId.SAVE_AS -> {
                                showSaveAsDialog.value = true
                            }

                            HomeDropDownMenuItemId.DELETE -> {
                                showDeleteDialog.value = true
                            }
                        }
                    },
                    showMenuButton = uiState.isContextMenuAvailable
                )
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                )
                if (uiState.isShowProgress) {
                    //Show nothing because of progress takes very short period of time
                } else if (uiState.isShowWaveform) {
                    WaveformComposeView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        state = uiState.waveformState,
                        showTimeline = true,
                        onSeekStart = {
                            onAction(HomeScreenAction.OnSeekStart)
                        },
                        onSeekProgress = { mills ->
                            onAction(HomeScreenAction.OnSeekProgress(mills))
                        },
                        onSeekEnd = { mills ->
                            onAction(HomeScreenAction.OnSeekEnd(mills))
                        }
                    )
                    PlayPanel(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .padding(8.dp, 8.dp),
                        showPause = uiState.showPause,
                        showStop = uiState.showStop,
                        onPlayClick = { onAction(HomeScreenAction.OnPlayClick) },
                        onStopClick = { onAction(HomeScreenAction.OnStopClick) },
                        onPauseClick = { onAction(HomeScreenAction.OnPauseClick) }
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.waveform),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.wrapContentSize(),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                )
                TimePanel(
                    uiState.recordName,
                    uiState.recordInfo,
                    uiState.time,
                    uiState.startTime,
                    uiState.endTime,
                    uiState.progress,
                    uiState.isShowWaveform,
                    onRenameClick = {},
                    onProgressChange = { onAction(HomeScreenAction.OnProgressBarStateChange(it)) }
                )
                BottomBar(
                    onSettingsClick = { showSettingsScreen() },
                    onRecordsListClick = { showRecordsScreen() },
                    onStartRecordingClick = { onAction(HomeScreenAction.OnStartRecordingClick) },
                    onPauseRecordingClick = { onAction(HomeScreenAction.OnPauseRecordingClick) },
                    onResumeRecordingClick = { onAction(HomeScreenAction.OnResumeRecordingClick) },
                    onStopRecordingClick = { onAction(HomeScreenAction.OnStopRecordingClick) },
                    onDeleteRecordingClick = { onAction(HomeScreenAction.OnDeleteRecordingProgressClick) },
                    bottomBarState = uiState.bottomBarState
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                )
                if (showDeleteDialog.value) {
                    DeleteDialog(
                        dialogText = stringResource(id = R.string.delete_record, uiState.recordName),
                        onAcceptClick = {
                            showDeleteDialog.value = false
                            onAction(HomeScreenAction.DeleteActiveRecord)
                        }, onDismissClick = {
                            showDeleteDialog.value = false
                        }
                    )
                } else if (showSaveAsDialog.value) {
                    SaveAsDialog(
                        dialogText = stringResource(
                            id = R.string.record_name_will_be_copied_into_downloads, uiState.recordName),
                        onAcceptClick = {
                            showSaveAsDialog.value = false
                            onAction(HomeScreenAction.SaveActiveRecordAs)
                        }, onDismissClick = {
                            showSaveAsDialog.value = false
                        }
                    )
                } else if (showRenameDialog.value) {
                    RenameAlertDialog(
                        uiState.recordName,
                        onAcceptClick = {
                            showRenameDialog.value = false
                            onAction(HomeScreenAction.RenameActiveRecord(it))
                        }, onDismissClick = {
                            showRenameDialog.value = false
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
@SuppressWarnings("MaxLineLength", "MagicNumber")
fun HomeScreenPreview() {
    val waveformData = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 234, 526, 0, 0, 0, 0, 8424, 4394, 7514, 23400, 13754, 10400, 21118, 12018, 24986, 6656, 7514, 10926, 32767, 24186, 21118, 16250, 10400, 7962, 5850, 4738, 4394, 26624, 10926, 5850, 4738, 5096, 4062, 11466, 10926, 14976, 22626, 21118, 30056, 21118, 32767, 23400, 21866, 21866, 23400, 27462, 25798, 24186, 14976, 18258, 11466, 15606, 23400, 29178, 29178, 26624, 16906, 12018, 16906, 12018, 14358, 7962, 22626, 22626, 14358, 6656, 4738, 3744, 2866, 4394, 3744, 4394, 3146, 2600, 416, 27462, 28314, 14976, 14976, 14976, 29178, 23400, 24186, 19662, 28314, 26624, 13162, 18258, 18258, 22626, 30946, 19662, 9886, 6246, 5096, 3744, 3744, 4394, 24186, 21118, 7078, 3146, 1878, 3438, 1878, 9386, 21118, 12584, 14976, 12584, 17576, 5850, 29178, 23400, 4738, 4062, 26624, 26624, 18954, 13754, 14358, 13162, 13754, 7962, 17576, 31850, 13754, 13162, 13754, 10400, 8898, 5850, 5466, 2866, 23400, 9386, 7514, 5850, 7962, 7514, 5466, 8424, 6656, 5850, 4394, 19662, 3438, 26624, 22626, 12018, 7514, 24986, 24186, 16250, 30946, 21118, 11466, 9886, 12584, 25798, 30056, 27462, 17576, 16906, 20384, 19662, 18258, 19662, 9386, 19662, 10400, 3744, 3146, 3744, 8424, 4062, 7078, 15606, 22626, 20384, 24186, 18258, 27462, 25798, 12584, 14358, 18954, 24986, 24986, 19662, 20384, 23400, 15606, 16906, 17576, 16906, 13162, 29178, 8898, 7514, 4738, 2600, 2106, 2866, 3438, 28314, 22626, 4394, 1462, 1098, 2866, 1878, 4394, 2600, 2346, 2346, 5466, 4738, 526, 29178, 20384, 19662, 6656, 28314, 21866, 20384, 11466, 21866, 15606, 18954, 18954, 16250, 32767, 22626, 9886, 18954, 16906, 13162, 8898, 6246, 5466, 30056, 7962, 5466, 5850, 23400, 17576, 29178, 10400, 14976, 22626, 19662, 20384, 14976, 32767, 13754, 13162, 13162, 32767, 20384, 8898, 7078, 16250, 18258, 15606, 13162, 14358, 29178, 15606, 5466, 4394, 2106, 162, 0, 0, 0, 786, 58, 58, 526, 104, 1462, 526, 786, 26, 0, 0, 0, 0, 29178, 17576, 24986, 29178, 28314, 23400, 20384, 30056, 27462, 31850, 32767, 27462, 32767, 25798, 32767, 30056, 29178, 23400, 22626, 31850, 14976, 16906, 30946, 25798, 27462, 22626, 15606, 29178, 13162, 23400, 21866, 24186, 21118, 24186, 28314, 29178, 30056, 16250, 18954, 16906, 29178, 30056, 27462, 20384, 29178, 25798, 12584, 21118, 20384, 31850, 21866, 21866, 26624, 18954, 14358, 21866, 24186, 25798, 27462, 21118, 22626, 21118, 24986, 13754, 13754, 30056, 22626, 10400, 27462, 30056, 24986, 29178, 20384, 23400, 28314, 29178, 29178, 17576, 20384, 23400, 27462, 13162, 24186, 20384, 31850, 25798, 25798, 14976, 24986, 22626, 24186, 23400, 30056, 30946, 17576, 16250, 13754, 16250, 24986, 24986, 17576, 29178, 20384, 30056, 19662, 18258, 24986, 30056, 18954, 24186, 30946, 32767, 32767, 27462, 30946, 13162, 24186, 21866, 31850, 30056, 24986, 30946, 26624, 22626, 21866, 25798, 28314, 30946, 32767, 30056, 30946, 29178, 23400, 28314, 30056, 30946, 26624, 30946, 29178, 21118, 29178, 21866, 29178, 28314, 21118, 31850, 32767, 29178, 31850, 30946, 31850, 25798, 26624, 30946, 32767, 32767, 30946, 28314, 28314, 32767, 30946, 28314, 28314, 31850, 30946, 30946, 30056, 21866, 18954, 30056, 19662, 31850, 29178, 31850, 22626, 25798, 28314, 26624, 29178, 25798, 28314, 28314, 29178, 28314, 27462, 27462, 25798, 22626, 18258, 29178, 23400, 32767, 21866, 18954, 24186, 19662, 14976, 17576, 23400, 23400, 24986, 27462, 26624, 19662, 25798, 21866, 30056, 30056, 30946, 32767, 18954, 28314, 10926, 30056, 24986, 31850, 25798, 29178, 27462, 22626, 24186, 24986, 25798, 17576, 28314, 27462, 29178, 30056, 20384, 18258, 21118, 24986, 24186, 25798, 29178, 29178, 27462, 22626, 16906, 23400, 21118, 27462, 26624, 26624, 31850, 27462, 23400, 26624, 21866, 20384, 25798, 29178, 20384, 13754, 9886, 9886, 10926, 29178, 25798, 24986, 22626, 14358, 1878, 0, 0, 0, 0, 0, 8898, 2600, 25798, 9886, 6656, 7962, 6246, 6246, 5096, 5850, 6656, 5096, 8424, 8424, 4062, 4394, 4394, 5850, 4394, 9886, 28314, 8424, 6246, 58, 6656, 3438, 0, 12018, 8898, 7514, 1878, 1098, 58, 0, 318, 318, 8424, 12018, 13162, 7962, 7514, 7514, 7514, 6246, 3146, 18954, 84)
    val durationMills = 58728L
    HomeScreen({}, {}, {}, uiState = HomeScreenState(
        waveformState = WaveformState(
            widthScale = calculateScale(durationMills, defaultWidthScale = 1.5f),
            durationMills = durationMills,
            progressMills = 111000L,
            waveformData = waveformData,
            durationSample = waveformData.size,
            gridStepMills = calculateGridStep(durationMills)
        ),
        progress = 0.4f,
        startTime = "00:00",
        endTime = "3:42",
        time = "1:51",
        recordName = "Test Record Name",
        recordInfo = "1.5 MB, mp4, 192 kbps, 48 kHz",
        isContextMenuAvailable = true,
        isStopRecordingButtonAvailable = true,
        isShowWaveform = true,
    ), null, {})
}

@Preview
@Composable
fun HomeScreenEmptyPreview() {
    HomeScreen({}, {}, {}, uiState = HomeScreenState(), null, {})
}

@Preview
@Composable
fun HomeScreenShowProgressPreview() {
    HomeScreen({}, {}, {}, uiState = HomeScreenState(
        isShowProgress = true
    ), null, {})
}

@Preview
@Composable
fun HomeScreenShowRecordingProgressPreview() {
    HomeScreen(
        {}, {}, {},
        uiState = HomeScreenState(
            isShowProgress = false,
            isShowWaveform = false,
            bottomBarState = BottomBarState.RECORDING,
            waveformState = WaveformState(),
            time = TimeUtils.formatTimeIntervalHourMinSec2(15000L),
            showPause = false,
            showStop = false,
            recordName = stringResource(R.string.recording_progress),
            recordInfo = "1.5 MB, mp4, 192 kbps, 48 kHz",
        ),
        null, {},
    )
}
