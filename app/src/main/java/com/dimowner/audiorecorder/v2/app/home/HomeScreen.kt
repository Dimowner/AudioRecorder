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
            modifier = Modifier.fillMaxSize().padding(innerPadding)
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
                        contentDescription = "Image Description",
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
    val waveformData = intArrayOf(36,44,48,77,105,100,67,76,67,63,39,67,71,74,51,43,71,65,66,55,41,77,88,87,55,77,84,77,83,75,68,88,77,58,44,36,38,48,54,48,80,111,99,115,103,101,102,106,111,96,99,96,74,92,107,110,93,115,104,117,125,107,95,125,119,101,90,101,114,99,81,103,101,91,79,120,110,132,138,122,146,128,125,124,130,132,131,117,122,128,127,119,122,128,133,129,137,127,121,128,132,132,127,130,135,132,124,122,129,123,133,130,131,126,123,131,132,138,134,137,135,140,128,127,130,141,135,125,129,131,125,136,132,133,126,134,132,129,133,135,131,140,127,135,134,131,107,64,55,128,121,113,125,136,140,128,123,132,138,123,118,122,126,126,130,130,128,126,131,136,137,137,136,124,133,135,132,134,121,121,117,116,121,129,126,125,125,110,67,112,101,124,103,99,119,115,99,105,101,116,107,107,106,95,98,115,121,105,106,117,123,95,97,125,100,89,101,103,110,121,90,101,110,96,122,123,104,64,129,136,151,127,150,132,139,132,144,140,150,133,134,129,141,130,140,140,144,136,129,134,152,135,136,147,133,145,131,151,131,133,138,138,130,140,133,128,99,71,113,124,122,98,96,107,121,120,104,110,127,111,97,92,114,117,107,102,105,112,130,113,115,110,120,111,100,96,118,125,124,111,92,123,122,105,98,93,113,88,75,64,64,55,46,39,52,54,54,67,45,64,54,66,79,81,90,70,54,79,120,77,88,52,104,106,73,127,86,101,88,85,80,98,130,116,101,90,104,128,139,127,131,135,132,138,131,140,147,131,133,139,137,139,127,138,135,135,129,143,131,130,140,132,132,136,143,119,79,77,82,74,58,62,62,64,123,101,77,73,62,57,42,45,46,55,53,47,55,54,62,59,65,65,91,118,107,97,121,106,86,103,88,94,112,94,119,106,97,105,114,105,108,112,136,110,110,107,101,89,99,105,93,97,92,118,131,106,100,129,116,108,131,121,129,93,107,90,97,124,129,108,131,126,112,111,117,124,94,100,119,104,114,63,52,48,49,43,46,49,45,54,54,48,50,53,58,69,51,55,62,49,33,132,118,100,103,102,106,105,114,107,129,112,111,113,97,115,119,126,91,127,129,147,137,140,139,137,138,133,136,141,142,135,137,145,135,148,145,124,133,88,140,136,128,134,130,140,136,139,132,136,140,140,137,149,138,140,118,101,80,54,85,96,104,89,67,93,55,52,78,83,123,68,73,65,109,92,92,73,90,145,133,140,136,145,152,143,127,154,133,150,140,136,141,147,130,145,132,148,135,135,135,141,140,147,128,144,136,142,134,144,140,152,148,150,151,152,126,105,98,81,75,68,68,68,83,52,57,57,56,70,70,62,62,62,67,61,62,59,78,74)
    val durationMills = 437232L
    HomeScreen({}, {}, {}, uiState = HomeScreenState(
        waveformState = WaveformState(
            widthScale = calculateScale(durationMills, defaultWidthScale = 1.5f),
            durationMills = durationMills,
            playProgressMills = 60000L,
            waveformData = waveformData,
            durationSample = waveformData.size,
            gridStepMills = calculateGridStep(durationMills)
        ),
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