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

package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import com.dimowner.audiorecorder.util.BluetoothDeviceInfo
import com.dimowner.audiorecorder.v2.data.model.AudioSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothMicSelector(
    connectedDevices: List<BluetoothDeviceInfo>,
    selectedDevice: BluetoothDeviceInfo?,
    isEnabled: Boolean,
    onDeviceSelected: (BluetoothDeviceInfo?) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    val checkState = remember(isEnabled) { mutableStateOf(isEnabled) }
    val isAvailable = connectedDevices.isNotEmpty()
    
    Row(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(24.dp, 16.dp, 16.dp, 16.dp)
                .wrapContentSize(),
            painter = painterResource(id = R.drawable.ic_bluetooth),
            contentDescription = stringResource(R.string.bluetooth_microphone_available),
            tint = if (isAvailable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded.value,
            onExpandedChange = { 
                if (isAvailable) {
                    expanded.value = !expanded.value 
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(0.dp, 12.dp, 0.dp, 12.dp)
        ) {
            TextField(
                value = if (isAvailable) {
                    selectedDevice?.productName ?: connectedDevices.firstOrNull()?.productName ?: ""
                } else {
                    ""
                },
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value)
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = isAvailable,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Normal
                        )
                    )
                ),
                label = {
                    Text(
                        text = stringResource(R.string.bluetooth_microphone_available),
                        fontSize = 12.sp
                    )
                }
            )
            
            ExposedDropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false }
            ) {
                connectedDevices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = device.productName,
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            onDeviceSelected(device)
                            expanded.value = false
                        }
                    )
                }
            }
        }
        
        Switch(
            checked = checkState.value,
            onCheckedChange = {
                checkState.value = it
                onToggleEnabled(it)
            },
            enabled = isAvailable,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothMicSelectorUnavailablePreview() {
    BluetoothMicSelector(
        connectedDevices = emptyList(),
        selectedDevice = null,
        isEnabled = false,
        onDeviceSelected = {},
        onToggleEnabled = {},
    )
}

@Composable
fun AudioSourceSelector(
    selectedSource: AudioSource,
    options: List<AudioSource>,
    onSourceSelected: (AudioSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.TopStart)
    ) {
        // The DropdownMenu composable
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { audioSource ->
                DropdownMenuItem(
                    onClick = {
                        onSourceSelected(audioSource)
                        expanded.value = false
                    },
                    text = {
                        Text(
                            text = getAudioSourceDisplayName(audioSource),
                            fontSize = 20.sp,
                            fontFamily = FontFamily(
                                Font(
                                    DeviceFontFamilyName("sans-serif"),
                                    weight = FontWeight.Light
                                )
                            ),
                        )
                    }
                )
            }
        }
        
        val text = getAudioSourceDisplayName(selectedSource)
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(24.dp, 16.dp, 16.dp, 16.dp)
                    .wrapContentSize(),
                painter = painterResource(id = R.drawable.ic_audiotrack),
                contentDescription = stringResource(R.string.audio_source),
            )
            Column(
                modifier = Modifier
                    .padding(0.dp, 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.audio_source),
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Bold
                        )
                    ),
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = text,
                    fontSize = 20.sp,
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Light
                        )
                    ),
                )
            }
            Icon(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(0.dp, 0.dp, 12.dp, 0.dp),
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = text,
            )
        }
    }
}

@Composable
private fun getAudioSourceDisplayName(audioSource: AudioSource): String {
    return when (audioSource) {
        AudioSource.DEFAULT -> stringResource(R.string.audio_source_default)
        AudioSource.MIC -> stringResource(R.string.audio_source_mic)
        AudioSource.VOICE_COMMUNICATION -> stringResource(R.string.audio_source_voice_communication)
        AudioSource.UNPROCESSED -> stringResource(R.string.audio_source_unprocessed)
    }
}

@Preview(showBackground = true)
@Composable
fun AudioSourceSelectorPreview() {
    AudioSourceSelector(
        selectedSource = AudioSource.MIC,
        options = AudioSource.entries,
        onSourceSelected = {}
    )
}
