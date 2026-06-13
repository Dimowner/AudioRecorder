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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.InfoItem
import com.dimowner.audiorecorder.v2.app.TEST_WAVEFORM_DATA
import com.dimowner.audiorecorder.v2.app.TitleBar
import com.dimowner.audiorecorder.v2.app.components.MAX_CONTENT_WIDTH_NARROW
import com.dimowner.audiorecorder.v2.app.info.widget.WaveformStaticWidget

@Composable
fun RecordInfoScreen(
    onPopBackStack: () -> Unit,
    recordInfo: RecordInfoState?,
    onSaveDescription: (description: String) -> Unit = {},
    isSaving: Boolean = false,
) {
    RecordInfoScreenContent(
        onPopBackStack = onPopBackStack,
        recordInfo = recordInfo,
        onSaveDescription = onSaveDescription,
        isSaving = isSaving,
    )
}

@Composable
internal fun RecordInfoScreenContent(
    onPopBackStack: () -> Unit,
    recordInfo: RecordInfoState?,
    onSaveDescription: (description: String) -> Unit = {},
    isSaving: Boolean = false,
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

                        // Description section
                        DescriptionEditor(
                            initialDescription = recordInfo.description,
                            isSaving = isSaving,
                            onSave = onSaveDescription,
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

@Composable
private fun DescriptionEditor(
    initialDescription: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(initialDescription) { mutableStateOf(initialDescription) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.rec_description),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.rec_description_hint)) },
            minLines = 3,
            maxLines = 6,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onSave(text) },
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.btn_save))
            }
            if (isSaving) {
                Spacer(modifier = Modifier.size(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
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
