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

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.RenameAlertDialog
import com.dimowner.audiorecorder.v2.app.SaveAsDialog
import com.dimowner.audiorecorder.v2.app.components.BluetoothMicSelector
import com.dimowner.audiorecorder.v2.app.components.KeepScreenOn
import com.dimowner.audiorecorder.v2.app.components.WaveformComposeView
import com.dimowner.audiorecorder.v2.app.components.WaveformState
import com.dimowner.audiorecorder.v2.app.getTestWaveformData
import com.dimowner.audiorecorder.v2.app.lostrecords.LostRecordsDialog
import com.dimowner.audiorecorder.v2.data.model.Record
import com.google.gson.Gson
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
internal fun HomeScreen(
    showRecordsScreen: () -> Unit,
    showSettingsScreen: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showLostRecordsScreen: (Record) -> Unit,
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

    val context = LocalContext.current

    // Permission launcher for audio recording
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - start recording immediately
            onAction(HomeScreenAction.OnStartRecordingClick)
        } else {
            // Permission denied - show snackbar
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.msg_permission_microphone_denied),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // Helper function to handle record button click with permission check
    val handleRecordButtonClick: () -> Unit = {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted - start recording
                onAction(HomeScreenAction.OnStartRecordingClick)
            }
            else -> {
                // Permission not granted - request it
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Handle the selected document URI here
        if (uri != null) {
            onAction(HomeScreenAction.ImportAudioFile(uri))
        }
    }

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
            is HomeScreenEvent.RecordMovedToRecycleSnack -> {
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
                            onAction(HomeScreenAction.RestoreRecordFromRecycle(event.recordId))
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
    KeepScreenOn(enabled = uiState.keepScreenOn)
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

                // Show Bluetooth and Audio Source settings when there are available BT devices.
                if (!uiState.isShowLoadingProgress
                    && uiState.connectedBluetoothDevices.isNotEmpty()
                ) {
                    BluetoothMicSelector(
                        connectedDevices = uiState.connectedBluetoothDevices,
                        selectedDevice = uiState.selectedBluetoothDevice,
                        isEnabled = uiState.isBluetoothMicEnabled,
                        onDeviceSelected = { device ->
                            onAction(HomeScreenAction.SelectBluetoothDevice(device))
                        },
                        onToggleEnabled = { enabled ->
                            onAction(HomeScreenAction.SetBluetoothMicEnabled(enabled))
                        }
                    )
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                )
                if (uiState.isShowLoadingProgress) {
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
                    !uiState.isRecording() && uiState.isShowWaveform,
                    onRenameClick = { showRenameDialog.value = true },
                    onProgressChange = { onAction(HomeScreenAction.OnProgressBarStateChange(it)) }
                )
                BottomBar(
                    onSettingsClick = { showSettingsScreen() },
                    onRecordsListClick = { showRecordsScreen() },
                    onStartRecordingClick = handleRecordButtonClick,
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
                        dialogText = stringResource(id = R.string.move_record_to_trash, uiState.recordName),
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
                if (uiState.showLostRecordsDialog) {
                    LostRecordsDialog(
                        onDismiss = {
                            onAction(HomeScreenAction.DismissLostRecordsDialog)
                        },
                        onDetailsClick = {
                            onAction(HomeScreenAction.DismissLostRecordsDialog)
                            uiState.lostRecord?.let {
                                showLostRecordsScreen(it)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(
        {}, {}, {}, {}, uiState = HomeScreenState(
        waveformState = getTestWaveformData(),
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
    HomeScreen({}, {}, {}, {}, uiState = HomeScreenState(), null, {})
}

@Preview
@Composable
fun HomeScreenShowProgressPreview() {
    HomeScreen({}, {}, {}, {}, uiState = HomeScreenState(
        isShowLoadingProgress = true
    ), null, {})
}

@Preview
@Composable
fun HomeScreenShowRecordingProgressPreview() {
    HomeScreen(
        {}, {}, {}, {},
        uiState = HomeScreenState(
            isShowLoadingProgress = false,
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
