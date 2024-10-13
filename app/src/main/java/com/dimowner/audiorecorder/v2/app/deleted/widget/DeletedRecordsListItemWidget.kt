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

package com.dimowner.audiorecorder.v2.app.deleted.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ConfirmationAlertDialog

@Composable
fun DeletedRecordsListItemWidget(
    name: String,
    details: String,
    onClickItem: () -> Unit,
    onClickRestore: () -> Unit,
    onClickDelete: () -> Unit,
) {
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showRestoreDialog = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clickable { onClickItem() }
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                modifier = Modifier
                    .padding(12.dp, 8.dp, 12.dp, 2.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                modifier = Modifier
                    .padding(12.dp, 2.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.End)
        ) {
            Button(
                modifier = Modifier.padding(4.dp).wrapContentSize(),
                onClick = { showRestoreDialog.value = true }
            ) {
                Text(
                    text = stringResource(id = R.string.restore),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            Button(
                modifier = Modifier.padding(4.dp).wrapContentSize(),
                onClick = { showDeleteDialog.value = true }
            ) {
                Text(
                    text = stringResource(id = R.string.delete),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color = MaterialTheme.colorScheme.inverseOnSurface)
        )
        if (showDeleteDialog.value) {
            ConfirmationAlertDialog(
                onDismissRequest = { showDeleteDialog.value = false },
                onConfirmation = {
                    onClickDelete()
                    showDeleteDialog.value = false
                },
                dialogTitle = stringResource(id = R.string.warning),
                dialogText = stringResource(id = R.string.delete_record_forever, name),
                painter = painterResource(id = R.drawable.ic_delete_forever),
                positiveButton = stringResource(id = R.string.btn_yes),
                negativeButton = stringResource(id = R.string.btn_no)
            )
        }
        if (showRestoreDialog.value) {
            ConfirmationAlertDialog(
                onDismissRequest = { showRestoreDialog.value = false },
                onConfirmation = {
                    onClickRestore()
                    showRestoreDialog.value = false
                },
                dialogTitle = stringResource(id = R.string.warning),
                dialogText = stringResource(id = R.string.restore_record, name),
                painter = painterResource(id = R.drawable.ic_restore_from_trash),
                positiveButton = stringResource(id = R.string.btn_yes),
                negativeButton = stringResource(id = R.string.btn_no)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeletedRecordsListItemWidgetPreview() {
    DeletedRecordsListItemWidget("Label", "Value", {}, {}, {})
}
