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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.RecordsDropDownMenu

@Composable
fun TopAppBar(
    onImportClick: () -> Unit,
    onHomeMenuItemClick: (HomeDropDownMenuItemId) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(60.dp)
            .fillMaxWidth()
            .padding(0.dp, 4.dp, 0.dp, 0.dp)
            .background(color = MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        FilledIconButton(
            onClick = onImportClick,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_import),
                contentDescription = stringResource(id = R.string.btn_import),
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.app_name),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Medium
                )
            ),
        )

        Box {
            RecordsDropDownMenu(
                items = remember { getHomeDroDownMenuItems() },
                onItemClick = { itemId ->
                    onHomeMenuItemClick(itemId)
                },
                expanded = expanded
            )
            FilledIconButton(
                onClick = { expanded.value = true },
                modifier = Modifier.padding(8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopAppBarPreview() {
    TopAppBar({}, {})
}

@Composable
fun BottomBar(
    onSettingsClick: () -> Unit,
    onRecordsListClick: () -> Unit,
    onRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onDeleteRecordingClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(16.dp, 0.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        FilledIconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = stringResource(id = R.string.settings),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FilledIconButton(
            onClick = onDeleteRecordingClick,
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_forever_36),
                contentDescription = stringResource(id = R.string.delete),
            )
        }
        FilledIconButton(
            onClick = onRecordingClick,
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
//                modifier = Modifier.size(90.dp),
                painter = painterResource(id = R.drawable.ic_record),
                contentDescription = "Record", //TODO: Use string resource
            )
        }
        FilledIconButton(
            onClick = onStopRecordingClick,
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = "Stop recording", //TODO: Use string resource
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FilledIconButton(
            onClick = onRecordsListClick,
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_list),
                contentDescription = stringResource(id = R.string.records),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BottomBarPreview() {
    BottomBar({}, {}, {}, {}, {})
}

@Composable
fun TimePanel(
    recordName: String,
    recordInfo: String,
    recordDuration: String,
    timeStart: String,
    timeEnd: String,
    onRenameClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize(),
            textAlign = TextAlign.Center,
            text = recordDuration,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(0.dp, 0.dp, 0.dp, 4.dp),
            textAlign = TextAlign.Center,
            text = recordName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal
        )
        Row {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(4.dp, 0.dp),
                textAlign = TextAlign.Start,
                text = timeStart,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                textAlign = TextAlign.Center,
                text = recordInfo,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(4.dp, 0.dp),
                textAlign = TextAlign.Start,
                text = timeEnd,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Slider(
            value = 0.5f,
            onValueChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimePanelPreview() {
    TimePanel("Record-14", "1.2Mb, M4a, 44.1kHz", "02:23","00:00", "05:32", {})
}
