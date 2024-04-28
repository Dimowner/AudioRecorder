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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ConfirmationAlertDialog
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.app.deleted.widget.DeletedRecordsListItemWidget
import com.google.gson.Gson
import timber.log.Timber

@Composable
internal fun DeletedRecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    uiState: DeletedRecordsScreenState,
    event: DeletedRecordsScreenEvent?,
    onAction: (DeletedRecordsScreenAction) -> Unit,
) {

    val showDeleteAllDialog = remember { mutableStateOf(false) }

    LaunchedEffect(key1 = event) {
        when (event) {
            is DeletedRecordsScreenEvent.RecordInformationEvent -> {
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

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                title = stringResource(id = R.string.trash),
                onBackPressed = { onPopBackStack() },
                actionButtonText = stringResource(id = R.string.delete_all2),
                onActionClick = if (uiState.records.isNotEmpty()) {
                    { showDeleteAllDialog.value = true }
                } else null
            )
            Text(
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .wrapContentSize(),
                text = stringResource(id = R.string.trash_info),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(color = MaterialTheme.colorScheme.inverseOnSurface)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(uiState.records) { record ->
                    DeletedRecordsListItemWidget(
                        name = record.name,
                        details = record.details,
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
                dialogText = stringResource(id = R.string.delete_all_records),
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
        ), null, {})
}
