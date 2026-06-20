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

package com.dimowner.audiorecorder.v2.app.info

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.EditDescriptionDialog
import com.dimowner.audiorecorder.v2.app.InfoItem
import com.dimowner.audiorecorder.v2.app.isDescriptionFileWriteSupported
import com.dimowner.audiorecorder.v2.app.TEST_WAVEFORM_DATA
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.app.components.MAX_CONTENT_WIDTH_NARROW
import com.dimowner.audiorecorder.v2.app.info.widget.WaveformStaticWidget

@Composable
fun RecordInfoScreen(
    onPopBackStack: () -> Unit,
    recordInfo: RecordInfoState?,
    saveDescriptionToFile: Boolean = true,
    onSaveDescription: (description: String, writeToFile: Boolean) -> Unit = { _, _ -> },
) {
    RecordInfoScreenContent(
        onPopBackStack = onPopBackStack,
        recordInfo = recordInfo,
        saveDescriptionToFile = saveDescriptionToFile,
        onSaveDescription = onSaveDescription,
    )
}

@Composable
internal fun RecordInfoScreenContent(
    onPopBackStack: () -> Unit,
    recordInfo: RecordInfoState?,
    saveDescriptionToFile: Boolean = true,
    onSaveDescription: (description: String, writeToFile: Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Keep info content readable on large screens instead of stretching edge-to-edge.
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = MAX_CONTENT_WIDTH_NARROW)
                    .fillMaxSize()
            ) {
                TitleBar(
                    stringResource(id = R.string.info),
                    onBackPressed = { onPopBackStack() }
                )
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(weight = 1f, fill = false)
                ) {
                    Spacer(modifier = Modifier.size(8.dp))
                    if (recordInfo != null) {
                        if (recordInfo.amps.isNotEmpty()) {
                            WaveformStaticWidget(
                                amps = recordInfo.amps,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        InfoItem(stringResource(R.string.rec_name), recordInfo.name)

                        // Description section
                        DescriptionEditor(
                            description = recordInfo.description,
                            initialWriteToFile = saveDescriptionToFile,
                            isWriteToFileSupported = isDescriptionFileWriteSupported(recordInfo.format),
                            onSave = onSaveDescription,
                        )

                        InfoItem(stringResource(R.string.rec_format), recordInfo.format)
                        if (recordInfo.authorName.isNotBlank()) {
                            InfoItem(
                                stringResource(R.string.record_author),
                                recordInfo.authorName
                            )
                        }
                        if (recordInfo.bitrate > 0) {
                            InfoItem(
                                stringResource(R.string.bitrate),
                                stringResource(id = R.string.value_kbps, recordInfo.bitrate / 1000)
                            )
                        }
                        InfoItem(
                            stringResource(R.string.channels),
                            stringResource(
                                when (recordInfo.channelCount) {
                                    1 -> R.string.mono
                                    2 -> R.string.stereo
                                    else -> R.string.empty
                                }
                            )
                        )
                        InfoItem(
                            stringResource(R.string.sample_rate),
                            stringResource(id = R.string.value_khz, recordInfo.sampleRate / 1000)
                        )
                        if (recordInfo.duration > 0) {
                            InfoItem(
                                stringResource(R.string.rec_duration),
                                TimeUtils.formatTimeIntervalHourMinSec2(recordInfo.duration)
                            )
                        }
                        InfoItem(
                            stringResource(R.string.rec_size),
                            Formatter.formatShortFileSize(context, recordInfo.size)
                        )
                        InfoItem(stringResource(R.string.rec_location), recordInfo.location)
                        InfoItem(
                            stringResource(R.string.rec_created),
                            TimeUtils.formatDateTimeLocale(recordInfo.created)
                        )

                    } else {
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.CenterHorizontally),
                            text = stringResource(id = R.string.error_unknown),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

/**
 * Read-only description row with an edit affordance. Tapping the row (or the edit
 * icon) opens [EditDescriptionDialog] to update the note. When the description is
 * empty a muted placeholder is shown instead.
 */
@Composable
private fun DescriptionEditor(
    description: String,
    initialWriteToFile: Boolean,
    isWriteToFileSupported: Boolean,
    onSave: (description: String, writeToFile: Boolean) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable(onClick = { showDialog = true }),
                text = stringResource(R.string.rec_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Light,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(id = R.drawable.ic_pencil),
                contentDescription = stringResource(R.string.rec_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 2.dp, 16.dp, 8.dp),
            text = description.ifBlank { stringResource(R.string.rec_description_hint) },
            color = if (description.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
        )
    }

    if (showDialog) {
        EditDescriptionDialog(
            initialDescription = description,
            initialWriteToFile = initialWriteToFile,
            isWriteToFileSupported = isWriteToFileSupported,
            onAcceptClick = { newDescription, writeToFile ->
                showDialog = false
                onSave(newDescription, writeToFile)
            },
            onDismissClick = { showDialog = false },
        )
    }
}

@Preview
@Composable
fun RecordInfoScreenPreview() {
    RecordInfoScreenContent(
        onPopBackStack = {},
        recordInfo = RecordInfoState(
            id = 1L,
            name = "name666",
            format = "format777",
            duration = 150000000,
            size = 1500000,
            location = "location888",
            created = System.currentTimeMillis(),
            sampleRate = 44000,
            channelCount = 1,
            bitrate = 240000,
            amps = TEST_WAVEFORM_DATA,
            authorName = "John Doe",
            description = "A sample description for this recording.",
        ),
    )
}

@Preview
@Composable
fun RecordInfoScreenLoadingPreview() {
    RecordInfoScreenContent(
        onPopBackStack = {},
        recordInfo = RecordInfoState(
            id = 2L,
            name = "name666",
            format = "format777",
            duration = 150000000,
            size = 1500000,
            location = "location888",
            created = System.currentTimeMillis(),
            sampleRate = 44000,
            channelCount = 1,
            bitrate = 240000,
            amps = TEST_WAVEFORM_DATA,
        ),
    )
}
