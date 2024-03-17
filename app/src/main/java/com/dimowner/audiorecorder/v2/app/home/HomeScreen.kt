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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import timber.log.Timber

@Composable
fun HomeScreen(
    showRecordsScreen: () -> Unit,
    showSettingsScreen: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.value

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Handle the selected document URI here
        if (uri != null) {
            viewModel.importAudioFile(context, uri)
        }
    }

    when(val event = viewModel.event.collectAsState(null).value) {
        HomeScreenEvent.ShowImportErrorError -> {
            Timber.v("ON EVENT: ShowImportErrorError")
        }
        is HomeScreenEvent.ShareRecord -> {
            val json = Uri.encode(Gson().toJson(event.recordInfo))
            Timber.v("ON EVENT: ShareRecord json = $json")
            showRecordInfoScreen(json)
        }
        else -> {
            Timber.v("ON EVENT: Unknown")
            //Do nothing
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                onImportClick = {
                    launcher.launch("audio/*")
                },
                onHomeMenuItemClick = {
                    when (it) {
                        HomeDropDownMenuItemId.SHARE -> viewModel.shareActiveRecord()
                        HomeDropDownMenuItemId.INFORMATION -> viewModel.showActiveRecordInfo()
                        HomeDropDownMenuItemId.RENAME -> viewModel.renameActiveRecord()
                        HomeDropDownMenuItemId.OPEN_WITH -> viewModel.openActiveRecordWithAnotherApp()
                        HomeDropDownMenuItemId.SAVE_AS -> viewModel.saveActiveRecordAs()
                        HomeDropDownMenuItemId.DELETE -> viewModel.deleteActiveRecord()
                    }
                })
            Spacer(modifier = Modifier
                .weight(1f)
                .wrapContentHeight())
            TimePanel(
                uiState.recordName,
                uiState.recordInfo,
                uiState.time,
                uiState.startTime,
                uiState.endTime,
                onRenameClick = {}
            )
            BottomBar(
                onSettingsClick = { showSettingsScreen() },
                onRecordsListClick = { showRecordsScreen() },
                onRecordingClick = {},
                onStopRecordingClick = {},
                onDeleteRecordingClick = {},
            )
            Spacer(modifier = Modifier
                .fillMaxWidth()
                .height(12.dp))
        }
    }
}

@Preview
@Composable
fun UserInputScreenPreview() {
    HomeScreen({}, {}, {})
}
