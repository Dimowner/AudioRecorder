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

package com.dimowner.audiorecorder.v2.app.deleted

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ConfirmationAlertDialog
import com.dimowner.audiorecorder.v2.app.ScrollableTitleBar
import com.dimowner.audiorecorder.v2.app.components.MAX_CONTENT_WIDTH_WIDE
import com.dimowner.audiorecorder.v2.app.deleted.widget.DeletedRecordsListItemWidget
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeletedRecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    uiState: DeletedRecordsScreenState,
    event: SharedFlow<DeletedRecordsScreenEvent?>,
    onAction: (DeletedRecordsScreenAction) -> Unit,
) {

    val showDeleteAllDialog = remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val recordRestoredMessage = stringResource(id = R.string.record_restored_successfully)
    val recordDeletedForeverMessage = stringResource(id = R.string.record_deleted_successfully)
    val errorRestoreMessage = stringResource(id = R.string.error_failed_to_restore)
    val errorDeleteMessage = stringResource(id = R.string.error_failed_to_delete)
    val errorDeleteAllMessage = stringResource(id = R.string.failed_to_delete_all_records)

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null &&
                    lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && uiState.hasMoreData) {
            onAction(DeletedRecordsScreenAction.LoadNextPage)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1 = event) {
        event.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { event ->
                when (event) {
                    is DeletedRecordsScreenEvent.RecordInformationEvent -> {
                        val json = Uri.encode(Gson().toJson(event.recordInfo))
                        Timber.v("ON EVENT: ShareRecord json = $json")
                        showRecordInfoScreen(json)
                    }

                    is DeletedRecordsScreenEvent.RecordRestoredEvent -> {
                        snackbarHostState.showSnackbar(recordRestoredMessage)
                    }

                    is DeletedRecordsScreenEvent.RecordDeletedForeverEvent -> {
                        snackbarHostState.showSnackbar(recordDeletedForeverMessage)
                    }

                    is DeletedRecordsScreenEvent.ErrorRestoreEvent -> {
                        snackbarHostState.showSnackbar(errorRestoreMessage)
                    }

                    is DeletedRecordsScreenEvent.ErrorDeleteEvent -> {
                        snackbarHostState.showSnackbar(errorDeleteMessage)
                    }

                    is DeletedRecordsScreenEvent.ErrorDeleteAllEvent -> {
                        snackbarHostState.showSnackbar(errorDeleteAllMessage)
                    }

                    else -> {
                        Timber.v("ON EVENT: Unknown")
                        //Do nothing
                    }
                }
            }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ScrollableTitleBar(
                title = stringResource(id = R.string.trash),
                onBackPressed = { onPopBackStack() },
                actionButtonText = stringResource(id = R.string.delete_all3),
                scrollBehavior = scrollBehavior,
                onActionClick = if (uiState.records.isNotEmpty()) {
                    { showDeleteAllDialog.value = true }
                } else null
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full-screen initial loading state
            if (uiState.isShowLoadingProgress && uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (!uiState.isShowLoadingProgress && uiState.records.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete_forever),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.trash),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.trash_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep list content readable on large screens instead of stretching edge-to-edge.
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = MAX_CONTENT_WIDTH_WIDE)
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item(key = "info_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete_forever),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    modifier = Modifier.wrapContentSize(),
                                    text = stringResource(id = R.string.trash_info),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        items(uiState.records, key = { it.recordId }) { record ->
                            DeletedRecordsListItemWidget(
                                modifier = Modifier.animateItem(),
                                name = record.name,
                                details = record.details,
                                duration = record.duration,
                                onClickItem = {
                                    onAction(DeletedRecordsScreenAction.ShowRecordInfo(record.recordId))
                                },
                                onClickRestore = {
                                    onAction(DeletedRecordsScreenAction.RestoreRecord(record.recordId))
                                },
                                onClickDelete = {
                                    onAction(DeletedRecordsScreenAction.DeleteForeverRecord(record.recordId))
                                },
                            )
                        }
                        if (uiState.isShowLoadingProgress) {
                            item(key = "loading_indicator") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showDeleteAllDialog.value) {
            ConfirmationAlertDialog(
                onDismissRequest = { showDeleteAllDialog.value = false },
                onConfirmation = {
                    onAction(DeletedRecordsScreenAction.DeleteAllRecordsFromRecycle)
                    showDeleteAllDialog.value = false
                },
                dialogTitle = stringResource(id = R.string.warning),
                dialogText = stringResource(id = R.string.delete_all_records2),
                painter = painterResource(id = R.drawable.ic_delete_forever),
                positiveButton = stringResource(id = R.string.btn_yes),
                negativeButton = stringResource(id = R.string.btn_no)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeletedRecordsScreenPreview() {
    DeletedRecordsScreen({}, {},
        uiState = DeletedRecordsScreenState(
            records = listOf(
                DeletedRecordListItem(
                    recordId = 0,
                    name = "Record Name 1",
                    details = "4.5 MB, mp3, 128 kbps, 32 kHz",
                    duration = "5:21",
                    isBookmarked = false
                ),
                DeletedRecordListItem(
                    recordId = 1,
                    name = "Record Name 2",
                    details = "9.2 MB, M4a, 192 kbps, 48 kHz",
                    duration = "2:43",
                    isBookmarked = true
                )
            )
        ), MutableSharedFlow(), {})
}

@Preview(showBackground = true, name = "Empty State")
@Composable
fun DeletedRecordsScreenEmptyPreview() {
    DeletedRecordsScreen(
        onPopBackStack = {},
        showRecordInfoScreen = {},
        uiState = DeletedRecordsScreenState(records = emptyList()),
        event = MutableSharedFlow(),
        onAction = {},
    )
}
