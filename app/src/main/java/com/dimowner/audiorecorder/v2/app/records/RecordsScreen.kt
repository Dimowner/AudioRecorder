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

package com.dimowner.audiorecorder.v2.app.records

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.RenameAlertDialog
import com.dimowner.audiorecorder.v2.app.SaveAsDialog
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.settings.SettingsItem
import com.google.gson.Gson
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
internal fun RecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showDeletedRecordsScreen: () -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val uiState = viewModel.uiState.value

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("SettingsScreen: On Start")
                viewModel.init()
                scope.launch {
                    snackbarHostState.showSnackbar("Snackbar")
                }
            }
            else -> {}
        }
    }

    when (val event = viewModel.event.collectAsState(null).value) {
        is RecordsScreenEvent.RecordInformationEvent -> {
            val json = Uri.encode(Gson().toJson(event.recordInfo))
            Timber.v("ON EVENT: ShareRecord json = $json")
            showRecordInfoScreen(json)
        }
        else -> {
            Timber.v("ON EVENT: Unknown")
            //Do nothing
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecordsTopBar(
                    stringResource(id = R.string.records),
                    uiState.sortOrder.toText(context),
                    bookmarksSelected = uiState.bookmarksSelected,
                    onBackPressed = { onPopBackStack() },
                    onSortItemClick = { order ->
                        viewModel.updateListWithSortOrder(order)
                    },
                    onBookmarksClick = { bookmarksSelected ->
                        viewModel.updateListWithBookmarks(bookmarksSelected)
                    }
                )
                if (uiState.showDeletedRecordsButton) {
                    SettingsItem(stringResource(R.string.trash), R.drawable.ic_delete) {
                        showDeletedRecordsScreen()
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(uiState.records) { record ->
                        RecordListItem(
                            name = record.name,
                            details = record.details,
                            duration = record.duration,
                            isBookmarked = record.isBookmarked,
                            onClickItem = {},
                            onClickBookmark = { isBookmarked ->
                                viewModel.bookmarkRecord(record.recordId, isBookmarked)
                            },
                            onClickMenu = {
                                when (it) {
                                    RecordDropDownMenuItemId.SHARE -> viewModel.shareRecord(record.recordId)
                                    RecordDropDownMenuItemId.INFORMATION -> viewModel.showRecordInfo(
                                        record.recordId
                                    )

                                    RecordDropDownMenuItemId.RENAME -> {
                                        viewModel.onRenameRecordRequest(record)
                                    }

                                    RecordDropDownMenuItemId.OPEN_WITH -> {
                                        viewModel.openRecordWithAnotherApp(record.recordId)
                                    }

                                    RecordDropDownMenuItemId.SAVE_AS -> {
                                        viewModel.onSaveAsRequest(record)
                                    }

                                    RecordDropDownMenuItemId.DELETE -> {
                                        viewModel.onMoveToRecycleRecordRequest(record)
                                    }
                                }
                            },
                        )
                    }
                }
                if (uiState.showMoveToRecycleDialog) {
                    uiState.selectedRecord?.let { record ->
                        DeleteDialog(record.name, onAcceptClick = {
                            viewModel.moveRecordToRecycle(record.recordId)
                        }, onDismissClick = {
                            viewModel.onMoveToRecycleRecordFinish()
                        })
                    }
                } else if (uiState.showSaveAsDialog) {
                    uiState.selectedRecord?.let { record ->
                        SaveAsDialog(record.name, onAcceptClick = {
                            viewModel.saveRecordAs(record.recordId)
                        }, onDismissClick = {
                            viewModel.onSaveAsFinish()
                        })
                    }
                } else if (uiState.showRenameDialog) {
                    uiState.selectedRecord?.let { record ->
                        RenameAlertDialog(record.name, onAcceptClick = {
                            viewModel.renameRecord(record.recordId, it)
                        }, onDismissClick = {
                            viewModel.onRenameRecordFinish()
                        })
                    }
                }
            }
        }
    }
}
