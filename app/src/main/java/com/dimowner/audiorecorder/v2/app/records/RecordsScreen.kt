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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    uiState: RecordsScreenState,
    event: RecordsScreenEvent?,
    onAction: (RecordsScreenAction) -> Unit
) {

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("SettingsScreen: On Start")
                onAction(RecordsScreenAction.InitRecordsScreen)
                scope.launch {
                    snackbarHostState.showSnackbar("Snackbar")
                }
            }
            else -> {}
        }
    }
    LaunchedEffect(key1 = event) {
        when (event) {
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
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecordsTopBar(
                    stringResource(id = R.string.records),
                    uiState.sortOrder.toText(context),
                    bookmarksSelected = uiState.bookmarksSelected,
                    onBackPressed = { onPopBackStack() },
                    onSortItemClick = { order ->
                          onAction(RecordsScreenAction.UpdateListWithSortOrder(order))
                    },
                    onBookmarksClick = { bookmarksSelected ->
                        onAction(RecordsScreenAction.UpdateListWithBookmarks(bookmarksSelected))
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
                        RecordListItemView(
                            name = record.name,
                            details = record.details,
                            duration = record.duration,
                            isBookmarked = record.isBookmarked,
                            onClickItem = {
                                onAction(RecordsScreenAction.OnItemSelect(record.recordId))
                            },
                            onClickBookmark = { isBookmarked ->
                                onAction(RecordsScreenAction.BookmarkRecord(record.recordId, isBookmarked))
                            },
                            onClickMenu = {
                                when (it) {
                                    RecordDropDownMenuItemId.SHARE -> {
                                        onAction(RecordsScreenAction.ShareRecord(record.recordId))
                                    }
                                    RecordDropDownMenuItemId.INFORMATION -> {
                                        onAction(RecordsScreenAction.ShowRecordInfo(record.recordId))
                                    }

                                    RecordDropDownMenuItemId.RENAME -> {
                                        onAction(RecordsScreenAction.OnRenameRecordRequest(record))
                                    }

                                    RecordDropDownMenuItemId.OPEN_WITH -> {
                                        onAction(RecordsScreenAction.OpenRecordWithAnotherApp(record.recordId))
                                    }

                                    RecordDropDownMenuItemId.SAVE_AS -> {
                                        onAction(RecordsScreenAction.OnSaveAsRequest(record))
                                    }

                                    RecordDropDownMenuItemId.DELETE -> {
                                        onAction(RecordsScreenAction.OnMoveToRecycleRecordRequest(record))
                                    }
                                }
                            },
                        )
                    }
                }
                if (uiState.showMoveToRecycleDialog) {
                    uiState.selectedRecord?.let { record ->
                        DeleteDialog(record.name, onAcceptClick = {
                            onAction(RecordsScreenAction.MoveRecordToRecycle(record.recordId))
                        }, onDismissClick = {
                            onAction(RecordsScreenAction.OnMoveToRecycleRecordDismiss)
                        })
                    }
                } else if (uiState.showSaveAsDialog) {
                    uiState.selectedRecord?.let { record ->
                        SaveAsDialog(record.name, onAcceptClick = {
                            onAction(RecordsScreenAction.SaveRecordAs(record.recordId))
                        }, onDismissClick = {
                            onAction(RecordsScreenAction.OnSaveAsDismiss)
                        })
                    }
                } else if (uiState.showRenameDialog) {
                    uiState.selectedRecord?.let { record ->
                        RenameAlertDialog(record.name, onAcceptClick = {
                            onAction(RecordsScreenAction.RenameRecord(record.recordId, it))
                        }, onDismissClick = {
                            onAction(RecordsScreenAction.OnRenameRecordDismiss)
                        })
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenPreview() {
    RecordsScreen({}, {}, {},
        RecordsScreenState(
            records = listOf(
                RecordListItem(
                    recordId = 1,
                    name = "Test record 1",
                    details = "1.5 MB, mp4, 192 kbps, 48 kHz",
                    duration = "3:15",
                    isBookmarked = true
                ),
                RecordListItem(
                    recordId = 2,
                    name = "Test record 2",
                    details = "4.5 MB, mp3, 128 kbps, 32 kHz",
                    duration = "8:15",
                    isBookmarked = false
                )
            ),
            showDeletedRecordsButton = true,
            showRenameDialog = false,
            showMoveToRecycleDialog = false,
            showSaveAsDialog = false,
            selectedRecord = RecordListItem(
                recordId = 2,
                name = "Test record 2",
                details = "4.5 MB, mp3, 128 kbps, 32 kHz",
                duration = "8:15",
                isBookmarked = false
            )
        ),
        null, {})
}
