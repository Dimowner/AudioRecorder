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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ConfirmationAlertDialog

@Composable
fun DeletedRecordsListItemWidget(
    name: String,
    details: String,
    duration: String,
    onClickItem: () -> Unit,
    onClickRestore: () -> Unit,
    onClickDelete: () -> Unit,
) {
    val showDeleteDialog = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClickItem,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Name + details + duration
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = duration,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = details,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily(
                                Font(
                                    DeviceFontFamilyName("sans-serif"),
                                    weight = FontWeight.Light,
                                )
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Action buttons
                IconButton(
                    onClick = onClickRestore,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_restore_from_trash),
                        contentDescription = stringResource(id = R.string.restore),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { showDeleteDialog.value = true },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete_forever),
                        contentDescription = stringResource(id = R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }

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
            negativeButton = stringResource(id = R.string.btn_no),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeletedRecordsListItemWidgetPreview() {
    DeletedRecordsListItemWidget(
        name = "Recording_2024-01-15",
        details = "4.5 MB · mp3 · 128 kbps",
        duration = "5:21",
        onClickItem = {},
        onClickRestore = {},
        onClickDelete = {},
    )
}
