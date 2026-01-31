/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.v2.app.lostrecords

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.app.lostrecords.widget.LostRecordsListItemWidget
import com.google.gson.Gson
import timber.log.Timber

@Composable
internal fun LostRecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    uiState: LostRecordsScreenState,
    event: LostRecordsScreenEvent?,
    onAction: (LostRecordsScreenAction) -> Unit,
) {
    val showDeleteAllDialog = remember { mutableStateOf(false) }

    LaunchedEffect(key1 = event) {
        when (event) {
            is LostRecordsScreenEvent.RecordInformationEvent -> {
                val json = Uri.encode(Gson().toJson(event.recordInfo))
                Timber.v("ON EVENT: RecordInfo json = $json")
                showRecordInfoScreen(json)
            }
            else -> {
                Timber.v("ON EVENT: Unknown")
                //Do nothing
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TitleBar(
                    title = stringResource(id = R.string.lost_records),
                    onBackPressed = { onPopBackStack() },
                    actionButtonText = stringResource(id = R.string.delete_all2),
                    onActionClick = if (uiState.records.isNotEmpty()) {
                        { showDeleteAllDialog.value = true }
                    } else null
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color = MaterialTheme.colorScheme.inverseOnSurface)
                )
                Text(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    text = stringResource(R.string.records_were_removed),
                    fontSize = 16.sp
                )
                if (uiState.records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            modifier = Modifier.wrapContentSize(),
                            text = stringResource(id = R.string.no_records),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(uiState.records) { record ->
                            LostRecordsListItemWidget(
                                name = record.name,
                                duration = record.duration,
                                path = record.path,
                                size = record.size,
                                onClickItem = {
                                    onAction(LostRecordsScreenAction.ShowRecordInfo(record.recordId))
                                },
                                onClickDelete = {
                                    onAction(LostRecordsScreenAction.DeleteRecord(record.recordId))
                                },
                            )
                        }
                    }
                }
            }
            if (showDeleteAllDialog.value) {
                DeleteDialog(
                    dialogText = stringResource(id = R.string.delete_all_records),
                    onAcceptClick = {
                        onAction(LostRecordsScreenAction.DeleteAllRecords)
                        showDeleteAllDialog.value = false
                    },
                    onDismissClick = {
                        showDeleteAllDialog.value = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LostRecordsScreenPreview() {
    LostRecordsScreen(
        onPopBackStack = {},
        showRecordInfoScreen = {},
        uiState = LostRecordsScreenState(
            records = listOf(
                LostRecordListItem(
                    recordId = 0,
                    name = "Record Name 1",
                    duration = "5:21",
                    size = "3.11Mb",
                    path = "/storage/emulated/0/AudioRecorder/Recording_001.m4a"
                ),
                LostRecordListItem(
                    recordId = 1,
                    name = "Record Name 2",
                    duration = "2:43",
                    size = "1.25Mb",
                    path = "/storage/emulated/0/AudioRecorder/Recording_002.m4a"
                )
            )
        ),
        event = null,
        onAction = {}
    )
}

@Preview(showBackground = true)
@Composable
fun LostRecordsScreenEmptyPreview() {
    LostRecordsScreen(
        onPopBackStack = {},
        showRecordInfoScreen = {},
        uiState = LostRecordsScreenState(records = emptyList()),
        event = null,
        onAction = {}
    )
}
