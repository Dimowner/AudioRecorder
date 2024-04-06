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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.RenameAlertDialog
import com.dimowner.audiorecorder.v2.app.SaveAsDialog
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.google.gson.Gson
import timber.log.Timber

@Composable
fun RecordsScreen(
    navController: NavHostController,
    showRecordInfoScreen: (String) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val uiState = viewModel.uiState.value

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

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecordsTopBar(
                stringResource(id = R.string.records),
                uiState.sortOrder.toText(context),
                bookmarksSelected = uiState.bookmarksSelected,
                onBackPressed = { navController.popBackStack() },
                onSortItemClick = { order ->
                    viewModel.updateListWithSortOrder(order)
                },
                onBookmarksClick = { bookmarksSelected ->
                    viewModel.updateListWithBookmarks(bookmarksSelected)
                }
            )
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
                                RecordDropDownMenuItemId.INFORMATION -> viewModel.showRecordInfo(record.recordId)
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
                                    viewModel.onDeleteRecordRequest(record)
                                }
                            }
                        },
                    )
                }
            }
            if (uiState.showDeleteDialog) {
                uiState.selectedRecord?.let { record ->
                    DeleteDialog(record.name, onAcceptClick = {
                        viewModel.deleteRecord(record.recordId)
                    }, onDismissClick = {
                        viewModel.onDeleteRecordFinish()
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
