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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dimowner.audiorecorder.v2.app.TitleBar

@Composable
fun RecordInfoScreen(
    onPopBackStack: () -> Unit,
    recordInfo: RecordInfoState?
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    InfoItem(stringResource(R.string.rec_name), recordInfo.name)
                    InfoItem(stringResource(R.string.rec_format), recordInfo.format)
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
                        modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally),
                        text = stringResource(id = R.string.error_unknown),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Preview
@Composable
fun RecordInfoScreenPreview() {
    RecordInfoScreen({}, null)
}
