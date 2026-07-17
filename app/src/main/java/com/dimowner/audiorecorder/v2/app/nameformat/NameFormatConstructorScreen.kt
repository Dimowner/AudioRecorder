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

package com.dimowner.audiorecorder.v2.app.nameformat

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ScrollableTitleBar
import com.dimowner.audiorecorder.v2.app.components.MAX_CONTENT_WIDTH_NARROW
import com.dimowner.audiorecorder.v2.data.model.NAME_FORMAT_DIVIDERS
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.NameFormatToken
import com.dimowner.audiorecorder.v2.data.model.NameFormatTokenType

/** Date and time elements, in the order they are offered to the user. */
private val DATE_TIME_TOKEN_TYPES = listOf(
    NameFormatTokenType.Timestamp,
    NameFormatTokenType.Year,
    NameFormatTokenType.Month,
    NameFormatTokenType.MonthName,
    NameFormatTokenType.Day,
    NameFormatTokenType.DayOfWeek,
    NameFormatTokenType.Hour24,
    NameFormatTokenType.Hour12,
    NameFormatTokenType.AmPm,
    NameFormatTokenType.Minute,
    NameFormatTokenType.Second,
)

private val PRESETS = listOf(
    NameFormat.Record,
    NameFormat.Timestamp,
    NameFormat.Date,
    NameFormat.DateUs,
    NameFormat.DateIso8601,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NameFormatConstructorScreen(
    onPopBackStack: () -> Unit,
    uiState: NameFormatConstructorState,
    onAction: (NameFormatConstructorAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showTextDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ScrollableTitleBar(
                title = stringResource(R.string.name_format),
                onBackPressed = onPopBackStack,
                scrollBehavior = scrollBehavior,
                actionButtonText = stringResource(R.string.btn_save),
                onActionClick = {
                    onAction(NameFormatConstructorAction.Save)
                    onPopBackStack()
                }.takeIf { uiState.tokens.isNotEmpty() },
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = MAX_CONTENT_WIDTH_NARROW)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                NameFormatPreview(preview = uiState.preview)
                ConstructedFormatPanel(
                    tokens = uiState.tokens,
                    onRemoveToken = { onAction(NameFormatConstructorAction.RemoveToken(it)) },
                    onMoveToken = { from, to ->
                        onAction(NameFormatConstructorAction.MoveToken(from, to))
                    },
                    onClear = { onAction(NameFormatConstructorAction.ClearTokens) },
                )
                TokenSection(title = stringResource(R.string.name_format_date_and_time)) {
                    DATE_TIME_TOKEN_TYPES.forEach { type ->
                        AddTokenChip(label = stringResource(type.labelRes())) {
                            onAction(NameFormatConstructorAction.AddToken(NameFormatToken(type)))
                        }
                    }
                }
                TokenSection(title = stringResource(R.string.name_format_other)) {
                    AddTokenChip(
                        label = stringResource(NameFormatTokenType.Counter.labelRes())
                    ) {
                        onAction(
                            NameFormatConstructorAction.AddToken(
                                NameFormatToken(NameFormatTokenType.Counter)
                            )
                        )
                    }
                    AddTokenChip(
                        label = stringResource(NameFormatTokenType.Text.labelRes())
                    ) {
                        showTextDialog = true
                    }
                }
                TokenSection(title = stringResource(R.string.name_format_dividers)) {
                    NAME_FORMAT_DIVIDERS.forEach { divider ->
                        AddTokenChip(label = divider.dividerLabel()) {
                            onAction(
                                NameFormatConstructorAction.AddToken(
                                    NameFormatToken(NameFormatTokenType.Divider, divider)
                                )
                            )
                        }
                    }
                }
                PresetsSection(
                    selectedPreset = uiState.selectedPreset,
                    onSelectPreset = { onAction(NameFormatConstructorAction.ApplyPreset(it)) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTextDialog) {
        TextTokenDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text ->
                showTextDialog = false
                onAction(
                    NameFormatConstructorAction.AddToken(
                        NameFormatToken(NameFormatTokenType.Text, text)
                    )
                )
            },
        )
    }
}

/** Shows the record name the constructed format produces for the next recording. */
@Composable
private fun NameFormatPreview(preview: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.name_format_preview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview.ifEmpty { stringResource(R.string.name_format_preview_empty) },
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (preview.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** The elements the format is currently built from, in render order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConstructedFormatPanel(
    tokens: List<NameFormatToken>,
    onRemoveToken: (Int) -> Unit,
    onMoveToken: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.name_format_your_format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (tokens.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text(text = stringResource(R.string.name_format_clear))
            }
        }
    }
    if (tokens.isEmpty()) {
        Text(
            text = stringResource(R.string.name_format_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        ReorderableTokenFlow(
            tokens = tokens,
            onRemoveToken = onRemoveToken,
            onMoveToken = onMoveToken,
        )
    }
}

/**
 * The constructed tokens as chips that can be reordered by long-pressing a chip and dragging it
 * onto another position. The dragged chip floats under the finger while the remaining chips shift
 * live to reveal where it will land; releasing drops it into that slot.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableTokenFlow(
    tokens: List<NameFormatToken>,
    onRemoveToken: (Int) -> Unit,
    onMoveToken: (Int, Int) -> Unit,
) {
    // Slot bounds of every chip, in the FlowRow's coordinate space, used to hit-test the finger
    // against the target position while dragging.
    val chipBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    // The finger position in the FlowRow's coordinate space, tracked across the whole drag.
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val chipShape = MaterialTheme.shapes.small

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tokens.forEachIndexed { index, token ->
            val isDragging = draggingIndex == index
            InputChip(
                selected = isDragging,
                onClick = { onRemoveToken(index) },
                label = { Text(text = token.chipLabel()) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.name_format_remove_element),
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .onGloballyPositioned { chipBounds[index] = it.boundsInParent() }
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset {
                        if (draggingIndex == index) {
                            val center = chipBounds[index]?.center ?: Offset.Zero
                            val translation = pointerPosition - center
                            IntOffset(translation.x.roundToInt(), translation.y.roundToInt())
                        } else {
                            IntOffset.Zero
                        }
                    }
                    .then(
                        if (isDragging) Modifier.shadow(6.dp, chipShape) else Modifier
                    )
                    .pointerInput(tokens.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggingIndex = index
                                pointerPosition = (chipBounds[index]?.topLeft ?: Offset.Zero) + offset
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                pointerPosition += dragAmount
                                val target = chipBounds.entries
                                    .filter { it.key in tokens.indices }
                                    .minByOrNull {
                                        (it.value.center - pointerPosition).getDistanceSquared()
                                    }
                                    ?.key
                                if (target != null && target != from) {
                                    onMoveToken(from, target)
                                    draggingIndex = target
                                }
                            },
                            onDragEnd = { draggingIndex = null },
                            onDragCancel = { draggingIndex = null },
                        )
                    },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun AddTokenChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/** Presets, so that the built-in formats can be loaded into the constructor and tweaked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsSection(
    selectedPreset: NameFormat?,
    onSelectPreset: (NameFormat) -> Unit,
) {
    Text(
        text = stringResource(R.string.name_format_presets),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PRESETS.forEach { preset ->
            FilterChip(
                selected = preset == selectedPreset,
                onClick = { onSelectPreset(preset) },
                label = { Text(text = stringResource(preset.labelRes())) },
            )
        }
    }
}

@Composable
private fun TextTokenDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.name_format_add_text),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= MAX_NAME_FORMAT_TEXT_LENGTH) text = it
                    },
                    label = { Text(stringResource(R.string.name_format_text_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = { onConfirm(text) },
                        enabled = text.isNotBlank(),
                    ) {
                        Text(stringResource(id = R.string.btn_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun NameFormatToken.chipLabel(): String {
    return when (type) {
        NameFormatTokenType.Text -> value
        NameFormatTokenType.Divider -> value.dividerLabel()
        else -> stringResource(type.labelRes())
    }
}

/** Whitespace dividers need a visible stand-in on the chip. */
@Composable
private fun String.dividerLabel(): String {
    return if (isBlank()) stringResource(R.string.name_format_divider_space) else this
}

private fun NameFormatTokenType.labelRes(): Int = when (this) {
    NameFormatTokenType.Timestamp -> R.string.name_format_timestamp
    NameFormatTokenType.Year -> R.string.name_format_year
    NameFormatTokenType.Month -> R.string.name_format_month
    NameFormatTokenType.MonthName -> R.string.name_format_month_name
    NameFormatTokenType.Day -> R.string.name_format_day
    NameFormatTokenType.DayOfWeek -> R.string.name_format_day_of_week
    NameFormatTokenType.Hour24 -> R.string.name_format_hour_24
    NameFormatTokenType.Hour12 -> R.string.name_format_hour_12
    NameFormatTokenType.AmPm -> R.string.name_format_am_pm
    NameFormatTokenType.Minute -> R.string.name_format_minute
    NameFormatTokenType.Second -> R.string.name_format_second
    NameFormatTokenType.Counter -> R.string.name_format_counter
    NameFormatTokenType.Text -> R.string.name_format_text
    NameFormatTokenType.Divider -> R.string.name_format_dividers
}

private fun NameFormat.labelRes(): Int = when (this) {
    NameFormat.Record -> R.string.name_format_preset_record
    NameFormat.Timestamp -> R.string.name_format_preset_timestamp
    NameFormat.Date -> R.string.name_format_preset_date
    NameFormat.DateUs -> R.string.name_format_preset_date_us
    NameFormat.DateIso8601 -> R.string.name_format_preset_date_iso8601
    NameFormat.Custom -> R.string.name_format_preset_custom
}

@Preview(showBackground = true)
@Composable
private fun NameFormatConstructorScreenPreview() {
    NameFormatConstructorScreen(
        onPopBackStack = {},
        uiState = NameFormatConstructorState(
            tokens = listOf(
                NameFormatToken(NameFormatTokenType.Text, "Record"),
                NameFormatToken(NameFormatTokenType.Divider, "-"),
                NameFormatToken(NameFormatTokenType.Counter),
            ),
            preview = "Record-12.m4a",
            selectedPreset = NameFormat.Record,
        ),
        onAction = {},
    )
}
