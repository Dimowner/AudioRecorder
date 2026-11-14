/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.app.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.audio.ReplayBufferConfig

/**
 * Instant replay settings: an on/off switch and a length picker that shows how much storage
 * each length takes.
 *
 * Turning it on first asks for RECORD_AUDIO (required) and, on Android 13+, POST_NOTIFICATIONS
 * (optional: without it the buffer still runs and can be saved from the home screen, it just has
 * no visible notification). The view model is told to enable only once the microphone is granted.
 */
@Composable
internal fun ReplayBufferSettings(
    isEnabled: Boolean,
    minutes: Int,
    onAction: (SettingsScreenAction) -> Unit,
) {
    val context = LocalContext.current
    val msgPermissionDenied = stringResource(R.string.msg_permission_microphone_denied)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            context.isGranted(Manifest.permission.RECORD_AUDIO)
        if (micGranted) {
            onAction(SettingsScreenAction.SetReplayBufferEnabled(true))
        } else {
            Toast.makeText(context, msgPermissionDenied, Toast.LENGTH_LONG).show()
        }
    }

    SettingsItemCheckBox(
        checked = isEnabled,
        label = stringResource(R.string.replay_buffer_title),
        description = stringResource(R.string.replay_buffer_description),
        iconRes = R.drawable.ic_history,
        onCheckedChange = { checked ->
            if (!checked) {
                onAction(SettingsScreenAction.SetReplayBufferEnabled(false))
            } else {
                val missing = replayBufferPermissions().filterNot { context.isGranted(it) }
                if (missing.isEmpty()) {
                    onAction(SettingsScreenAction.SetReplayBufferEnabled(true))
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }
        },
    )
    ReplayBufferDurationRow(
        minutes = minutes,
        onSelect = { onAction(SettingsScreenAction.SetReplayBufferDuration(it)) },
    )
}

private fun replayBufferPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        listOf(Manifest.permission.RECORD_AUDIO)
    }

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun formatBufferSize(minutes: Int): String =
    Formatter.formatShortFileSize(LocalContext.current, ReplayBufferConfig.bufferSizeBytes(minutes))

@Composable
private fun ReplayBufferDurationRow(
    minutes: Int,
    onSelect: (Int) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { showDialog.value = true }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentSize(),
            painter = painterResource(id = R.drawable.ic_access_time),
            contentDescription = stringResource(R.string.replay_buffer_duration),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.replay_buffer_duration),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.replay_buffer_duration_subtitle, formatBufferSize(minutes)),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Card(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 8.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.duration_minutes, minutes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
    if (showDialog.value) {
        ReplayBufferDurationDialog(
            selected = minutes,
            onDismiss = { showDialog.value = false },
            onSelect = {
                showDialog.value = false
                onSelect(it)
            },
        )
    }
}

@Composable
private fun ReplayBufferDurationDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.replay_buffer_duration)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ReplayBufferConfig.DURATION_OPTIONS_MINUTES.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = stringResource(
                                R.string.replay_buffer_option, option, formatBufferSize(option)
                            ),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ReplayBufferSettingsPreview() {
    ReplayBufferSettings(isEnabled = true, minutes = 10, onAction = {})
}
