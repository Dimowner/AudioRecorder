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

package com.dimowner.audiorecorder.v2.app.lostrecords.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.DeleteDialog

@Composable
fun LostRecordsListItemWidget(
    name: String,
    duration: String,
    size: String,
    path: String,
    onClickItem: () -> Unit,
    onClickDelete: () -> Unit,
) {
    val showDeleteDialog = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clickable { onClickItem() }
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp, 8.dp, 0.dp, 8.dp)
                .weight(1f)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                modifier = Modifier
                    .padding(0.dp, 0.dp, 0.dp, 2.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier
                    .padding(0.dp, 2.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = "${stringResource(R.string.rec_duration)} $duration ${stringResource(R.string.rec_size)} $size",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
            Text(
                modifier = Modifier
                    .padding(0.dp, 2.dp, 0.dp, 0.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = path,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            modifier = Modifier
                .padding(4.dp)
                .wrapContentSize(),
            onClick = { showDeleteDialog.value = true }
        ) {
            Text(
                text = stringResource(id = R.string.delete),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Light,
            )
        }
        if (showDeleteDialog.value) {
            DeleteDialog(
                dialogText = stringResource(id = R.string.delete_record, name),
                onAcceptClick = {
                    onClickDelete()
                    showDeleteDialog.value = false
                },
                onDismissClick = {
                    showDeleteDialog.value = false
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LostRecordsListItemWidgetPreview() {
    LostRecordsListItemWidget(
        name = "Recording 001",
        duration = "5:21",
        size = "3.50Mb",
        path = "/storage/emulated/0/AudioRecorder/Recording_001.m4a",
        onClickItem = {},
        onClickDelete = {}
    )
}
