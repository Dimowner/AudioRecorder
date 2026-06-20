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

package com.dimowner.audiorecorder.v2.app.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilter
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.roundToInt

private const val ANIMATION_DURATION = 500
private const val MAX_MOVE = 250

/**
 * Wraps [RecordsFilterPanel] in the same draggable card interaction as the playback
 * `TouchPanel`, mirrored for the top edge: the panel slides down from the top of the screen
 * to overlay the top bar and the list, and can be flung/dragged up to dismiss it. Releasing
 * after dragging up past half the panel height animates it off the top edge and calls
 * [onDismiss]; a smaller drag springs back to the resting position.
 */
@Composable
fun RecordsFilterTouchPanel(
    visible: Boolean,
    filter: RecordsFilter,
    filterOptions: RecordsFilterOptions,
    onFilterChange: (RecordsFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Current vertical offset of the card while dragging.
    val offsetY = remember { mutableFloatStateOf(0f) }
    val maxMove = with(density) { MAX_MOVE.dp.toPx() }
    // Elastic damping factor so the drag follows the finger with diminishing returns.
    val k = (maxMove / (Math.PI / 2f)).toFloat()
    // Resting position is flush with the top edge.
    val startY = 0f

    var cumulativeDrag = remember { 0f }
    val animatableY = remember { Animatable(startY) }
    val coroutineScope = rememberCoroutineScope()

    // Measured panel height, used to decide whether a drag is far enough to dismiss.
    val panelHeight = remember { mutableFloatStateOf(0f) }

    // Slides the card the rest of the way off the top edge and then dismisses it. The reset to
    // the resting position is delayed until after the exit animation has played, so the card
    // never briefly snaps back into view.
    fun animateDismiss() {
        coroutineScope.launch {
            animatableY.animateTo(
                -panelHeight.floatValue * 1.5f,
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            )
            onDismiss()
            delay(ANIMATION_DURATION.toLong())
            animatableY.snapTo(startY)
            offsetY.floatValue = startY
        }
    }

    // Springs the card back to its resting position when the drag was not far enough to dismiss.
    fun animateSpringBack() {
        coroutineScope.launch {
            animatableY.animateTo(
                startY,
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            )
        }
    }

    val dragModifier = Modifier
        .offset { IntOffset(0, animatableY.value.roundToInt()) }
        .onSizeChanged { panelHeight.floatValue = it.height.toFloat() }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    offsetY.floatValue = startY
                    cumulativeDrag = startY
                },
                onDragEnd = {
                    // Dragging up or down produces offset; dismiss past 0.3 of the height.
                    if (offsetY.floatValue.absoluteValue > panelHeight.floatValue * 0.3f) {
                        animateDismiss()
                    } else {
                        animateSpringBack()
                    }
                },
                onDragCancel = {
                    if (offsetY.floatValue.absoluteValue > panelHeight.floatValue * 0.3f) {
                        animateDismiss()
                    } else {
                        animateSpringBack()
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    cumulativeDrag += dragAmount.y
                    // Dragging up (negative) is the dismiss direction, so the card follows the
                    // finger 1:1 and the half-height dismiss threshold stays reachable. Dragging
                    // down past the resting position is damped with atan so it resists elastically.
                    offsetY.floatValue = if (cumulativeDrag < 0f) {
                        cumulativeDrag
                    } else {
                        k * atan(cumulativeDrag / k)
                    }
                    coroutineScope.launch {
                        animatableY.snapTo(offsetY.floatValue)
                    }
                }
            )
        }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
    ) {
        RecordsFilterPanel(
            filter = filter,
            filterOptions = filterOptions,
            onFilterChange = onFilterChange,
            onClear = onClear,
            onDismiss = onDismiss,
            modifier = modifier.then(dragModifier),
        )
    }
}

/**
 * A panel that slides in below the top bar and lets the user filter the records list by
 * format, sample rate, channel count and bitrate. Each dimension shows toggleable chips for
 * the distinct values present among the user's records. Selecting chips updates the [filter]
 * immediately through [onFilterChange]; multiple chips (within and across dimensions) can be
 * active at the same time.
 */
@Composable
fun RecordsFilterPanel(
    filter: RecordsFilter,
    filterOptions: RecordsFilterOptions,
    onFilterChange: (RecordsFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp
            ),
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.filter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (!filter.isEmpty) {
                        TextButton(onClick = onClear) {
                            Text(text = stringResource(id = R.string.filter_clear))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.filter_dismiss),
                        )
                    }
                }

                if (filterOptions.isEmpty) {
                    Text(
                        text = stringResource(id = R.string.filter_no_options),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                FilterChipsSection(
                    title = stringResource(id = R.string.rec_format),
                    options = filterOptions.formats,
                    selected = filter.formats,
                    label = { it.uppercase() },
                    onToggle = { value ->
                        onFilterChange(filter.copy(formats = filter.formats.toggle(value)))
                    },
                )
                FilterChipsSection(
                    title = stringResource(id = R.string.sample_rate),
                    options = filterOptions.sampleRates,
                    selected = filter.sampleRates,
                    label = { stringResource(id = R.string.value_khz, it / 1000) },
                    onToggle = { value ->
                        onFilterChange(filter.copy(sampleRates = filter.sampleRates.toggle(value)))
                    },
                )
                FilterChipsSection(
                    title = stringResource(id = R.string.channels),
                    options = filterOptions.channelCounts,
                    selected = filter.channelCounts,
                    label = { count ->
                        when (count) {
                            1 -> stringResource(id = R.string.mono)
                            2 -> stringResource(id = R.string.stereo)
                            else -> count.toString()
                        }
                    },
                    onToggle = { value ->
                        onFilterChange(filter.copy(channelCounts = filter.channelCounts.toggle(value)))
                    },
                )
                FilterChipsSection(
                    title = stringResource(id = R.string.bitrate),
                    options = filterOptions.bitrates,
                    selected = filter.bitrates,
                    label = { stringResource(id = R.string.value_kbps, it / 1000) },
                    onToggle = { value ->
                        onFilterChange(filter.copy(bitrates = filter.bitrates.toggle(value)))
                    },
                )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterChipsSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
) {
    if (options.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            val isSelected = selected.contains(option)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { Text(text = label(option)) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                } else null,
            )
        }
    }
}

/** Returns a new set with [value] removed if present, or added otherwise. */
private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (contains(value)) this - value else this + value
}

@Preview(showBackground = true)
@Composable
fun RecordsFilterPanelPreview() {
    RecordsFilterPanel(
        filter = RecordsFilter(
            formats = setOf("m4a"),
            sampleRates = setOf(44100),
        ),
        filterOptions = RecordsFilterOptions(
            formats = listOf("3gp", "m4a", "wav"),
            sampleRates = listOf(16000, 22050, 44100, 48000),
            channelCounts = listOf(1, 2),
            bitrates = listOf(96000, 128000, 192000),
        ),
        onFilterChange = {},
        onClear = {},
        onDismiss = {},
    )
}
