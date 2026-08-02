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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.RecordsDropDownMenu
import com.dimowner.audiorecorder.v2.app.components.onDebounceClick
import com.dimowner.audiorecorder.v2.data.model.PlaybackSpeed
import com.dimowner.audiorecorder.v2.data.model.formatValue

private const val ANIMATION_DURATION = 300

// The play controls and the rate menu share one row, which on a narrow screen also holds the
// prev/next buttons, so the gap and the menu button are kept tight on purpose.
private val SPEED_MENU_GAP = 10.dp

// Fixed so the menu button does not resize as rates of different label lengths are picked, and so
// the row can reserve the same width on the other side of the play controls, see [PlayPanel].
private val SPEED_MENU_WIDTH = 64.dp

private val PLAY_BUTTON_SIZE = 42.dp
private val STOP_BUTTON_GAP = 4.dp

// The play and stop buttons sit in an area wide enough for both, whether or not stop is on screen.
// That way only the play button moves when stop appears, and the rate menu beside the area keeps
// its place.
private val PLAY_CONTROLS_WIDTH = PLAY_BUTTON_SIZE * 2 + STOP_BUTTON_GAP

@Composable
fun TopAppBar(
    onImportClick: () -> Unit,
    onHomeMenuItemClick: (HomeDropDownMenuItemId) -> Unit,
    showImportButton: Boolean = true,
    showMenuButton: Boolean = true
) {
    val expanded = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .height(60.dp)
            .fillMaxWidth()
            .padding(0.dp, 4.dp, 0.dp, 0.dp)
            .background(color = MaterialTheme.colorScheme.surface),
    ) {
        if (showImportButton) {
            IconButton(
                onClick = onImportClick,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_import),
                    contentDescription = stringResource(id = R.string.btn_import),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
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

        if (showMenuButton) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                RecordsDropDownMenu(
                    items = remember { getHomeDroDownMenuItems() },
                    onItemClick = { itemId ->
                        onHomeMenuItemClick(itemId)
                    },
                    expanded = expanded
                )
                IconButton(
                    onClick = { expanded.value = true },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = stringResource(id = R.string.dropdown_menu),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopAppBarPreview() {
    TopAppBar({}, {})
}

/**
 * Play controls with the playback rate menu after them, i.e. to the right of the stop button in an
 * LTR layout and to its left in an RTL one, as the row mirrors. The same width the menu takes is
 * reserved on the other side of the play controls, which keeps them centered in the row whether or
 * not the menu is on screen. The menu shares [showStop] with the stop button, so it is only on
 * screen while a record is playing or paused; it fades in and out without moving, while the play
 * button slides aside to make room for the stop button.
 */
@Composable
fun PlayPanel(
    modifier: Modifier,
    showStop: Boolean,
    showPause: Boolean,
    selectedSpeed: PlaybackSpeed,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onPlaybackSpeedClick: (PlaybackSpeed) -> Unit,
) {
    // The menu keeps its slot even while hidden, so nothing around it shifts.
    val speedMenuAlpha by animateFloatAsState(
        targetValue = if (showStop) 1f else 0f,
        animationSpec = tween(ANIMATION_DURATION),
        label = "playbackSpeedMenuAlpha",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.width(SPEED_MENU_WIDTH + SPEED_MENU_GAP))
        Box(
            modifier = Modifier.width(PLAY_CONTROLS_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = if (showPause) onPauseClick else onPlayClick,
                    modifier = Modifier
                        .size(PLAY_BUTTON_SIZE)
                        .align(Alignment.CenterVertically),
                ) {
                    val imageResourceId = if (showPause) {
                        R.drawable.ic_pause
                    } else {
                        R.drawable.ic_play
                    }
                    Icon(
                        painter = painterResource(id = imageResourceId),
                        contentDescription = stringResource(id = R.string.btn_play),
                    )
                }
                AnimatedVisibility(
                    visible = showStop,
                    enter = fadeIn(animationSpec = tween(ANIMATION_DURATION)),
                    exit = fadeOut(animationSpec = tween(ANIMATION_DURATION)),
                ) {
                    Row {
                        Spacer(modifier = Modifier.size(STOP_BUTTON_GAP))
                        IconButton(
                            onClick = onStopClick,
                            modifier = Modifier
                                .size(PLAY_BUTTON_SIZE)
                                .align(Alignment.CenterVertically),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stop),
                                contentDescription = stringResource(id = R.string.button_stop),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.size(SPEED_MENU_GAP))
        PlaybackSpeedMenu(
            selectedSpeed = selectedSpeed,
            onSpeedClick = onPlaybackSpeedClick,
            alpha = speedMenuAlpha,
            isShown = showStop,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlayPanelPreview() {
    PlayPanel(
        modifier = Modifier
            .wrapContentSize()
            .padding(8.dp, 8.dp),
        showPause = false,
        showStop = true,
        selectedSpeed = PlaybackSpeed.X1_5,
        onPlayClick = {},
        onStopClick = {},
        onPauseClick = {},
        onPlaybackSpeedClick = {},
    )
}

/**
 * Drop down menu listing every playback rate, with the selected one both on the button that opens
 * it and check marked in the list. The picked rate applies to the current playback as well as to
 * the tracks played later. The button takes the same space at any [alpha]; while [isShown] is false
 * it is faded out and takes no clicks and no accessibility focus.
 */
@Composable
private fun PlaybackSpeedMenu(
    selectedSpeed: PlaybackSpeed,
    onSpeedClick: (PlaybackSpeed) -> Unit,
    alpha: Float,
    isShown: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    // Playback can stop while the menu is open — from the notification, or by reaching the end of
    // the record. The button it hangs off is gone by then, so close it rather than leave it
    // floating over the panel.
    LaunchedEffect(isShown) {
        if (!isShown) expanded = false
    }
    val selectedLabel = stringResource(
        id = R.string.playback_speed_value,
        selectedSpeed.formatValue()
    )
    // "1x" on its own says nothing to a screen reader, so the button carries the full phrase.
    val description = stringResource(id = R.string.playback_speed_value_description, selectedLabel)
    Box(
        modifier = Modifier
            .alpha(alpha)
            .then(if (isShown) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        Row(
            modifier = Modifier
                .width(SPEED_MENU_WIDTH)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(
                    enabled = isShown,
                    onClick = { expanded = true },
                    role = Role.DropdownList,
                )
                .semantics { contentDescription = description }
                .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PlaybackSpeed.entries.forEach { speed ->
                val isSelected = speed == selectedSpeed
                val label = stringResource(id = R.string.playback_speed_value, speed.formatValue())
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = isSelected },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSpeedClick(speed)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacySlider(
    //Progress is value between 0 - 1f
    progress: Float = 0f,
    onProgressChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val trackHeight = 4.dp
    val thumbSize = DpSize(16.dp, 16.dp)
    val zeroThumbSize = DpSize(0.dp, 0.dp)

    Slider(
        interactionSource = interactionSource,
        modifier = Modifier
            .requiredSizeIn(minWidth = thumbSize.width, minHeight = trackHeight)
            .padding(8.dp, 0.dp),
        value = progress,
        enabled = enabled,
        onValueChange = { onProgressChange(it) },
        thumb = {
            val modifier = Modifier
                    .size(if (enabled) thumbSize else zeroThumbSize)
                    .shadow(1.dp, CircleShape, clip = false)
                    .indication(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 16.dp)
                    )
            SliderDefaults.Thumb(interactionSource = interactionSource, modifier = modifier)
        },
        track = {
            val modifier = Modifier
                .height(trackHeight)
                .padding(horizontal = if (enabled) 0.dp else 8.dp)

            SliderDefaults.Track(
                sliderState = it,
                modifier = modifier,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                drawStopIndicator = null
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
fun LegacySliderPreview() {
    LegacySlider(
        progress = 0.5f,
        onProgressChange = {}
    )
}

@Composable
fun BottomBar(
    onSettingsClick: () -> Unit,
    onRecordsListClick: () -> Unit,
    onStartRecordingClick: () -> Unit,
    onPauseRecordingClick: () -> Unit,
    onResumeRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onDeleteRecordingClick: () -> Unit,
    bottomBarState: BottomBarState
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(16.dp, 0.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(
            onClick = onDebounceClick(onSettingsClick),
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = stringResource(id = R.string.settings),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        when (bottomBarState) {
            BottomBarState.READY_TO_START_RECORDING -> {
                CircleButton(
                    modifier = Modifier.size(80.dp),
                    text = stringResource(R.string.button_record),
                    onClick = onDebounceClick(onStartRecordingClick),
                )
            }
            BottomBarState.RECORDING -> {
                RecordingProgressPanel(
                    modifier = Modifier,
                    onPauseRecordingClick = onDebounceClick(onPauseRecordingClick),
                    onStopRecordingClick = onDebounceClick(onStopRecordingClick),
                )
            }
            BottomBarState.PAUSED -> {
                RecordingPausePanel(
                    modifier = Modifier,
                    onResumeRecordingClick = onDebounceClick(onResumeRecordingClick),
                    onStopRecordingClick = onDebounceClick(onStopRecordingClick),
                    onDeleteRecordingClick = onDebounceClick(onDeleteRecordingClick),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onDebounceClick(onRecordsListClick),
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_list),
                contentDescription = stringResource(id = R.string.records),
            )
        }
    }
}

@Composable
fun CircleButton(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 13.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CircleButtonPreview() {
    CircleButton(
        modifier = Modifier.size(64.dp),
        text = "RECORD",
        onClick = {}
    )
}

@Composable
fun RecordingProgressPanel(
    modifier: Modifier,
    onPauseRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Spacer(modifier = Modifier.size(width = 62.dp, 54.dp))
        CircleButton(
            modifier = Modifier.size(80.dp),
            text = stringResource(R.string.button_pause),
            onClick = onDebounceClick(onPauseRecordingClick),
        )
        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onDebounceClick(onStopRecordingClick),
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = stringResource(R.string.button_stop)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingProgressPanelPreview() {
    RecordingProgressPanel(Modifier, {}, {})
}

@Composable
fun RecordingPausePanel(
    modifier: Modifier,
    onResumeRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onDeleteRecordingClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.size(94.dp, 48.dp),
            onClick = onDebounceClick(onDeleteRecordingClick),
            contentPadding = PaddingValues(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD01716),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp, // Removes the default shadow
                pressedElevation = 0.dp  // Prevents a shadow when pressed
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    text = stringResource(R.string.delete),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Icon(
                    modifier = Modifier.size(32.dp).padding(2.dp),
                    painter = painterResource(id = R.drawable.ic_delete_forever_36),
                    contentDescription = stringResource(id = R.string.delete),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        CircleButton(
            modifier = Modifier.size(80.dp),
            text = stringResource(R.string.button_resume),
            onClick = onDebounceClick(onResumeRecordingClick),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            modifier = Modifier.size(94.dp, 48.dp),
            onClick = onDebounceClick(onStopRecordingClick),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF48A54B),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(6.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp, // Removes the default shadow
                pressedElevation = 0.dp  // Prevents a shadow when pressed
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.size(32.dp).padding(2.dp),
                    painter = painterResource(id = R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.button_stop),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    text = stringResource(R.string.button_stop),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingPausePanelPreview() {
    RecordingPausePanel(Modifier, {}, {}, {})
}

@Preview(showBackground = true)
@Composable
fun BottomBarReadyPreview() {
    BottomBar({}, {}, {}, {}, {}, {}, {}, BottomBarState.READY_TO_START_RECORDING)
}

@Preview(showBackground = true)
@Composable
fun BottomBarRecordingPreview() {
    BottomBar({}, {}, {}, {}, {}, {}, {}, BottomBarState.RECORDING)
}

@Preview(showBackground = true)
@Composable
fun BottomBarPausedPreview() {
    BottomBar({}, {}, {}, {}, {}, {}, {}, BottomBarState.PAUSED)
}

@Composable
fun TimePanel(
    recordName: String,
    recordDescription: String,
    recordInfo: String,
    recordDuration: String,
    timeStart: String,
    timeEnd: String,
    progress: Float,
    isSliderEnabled: Boolean,
    isRenameAvailable: Boolean,
    onRenameClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    onProgressChange: (Float) -> Unit
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
            style = MaterialTheme.typography.titleLarge,
            fontSize = 60.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = isRenameAvailable) { onRenameClick() }
                .padding(8.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Center,
                text = recordName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                overflow = TextOverflow.Ellipsis
            )
            if (isRenameAvailable) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_pencil_small),
                    contentDescription = stringResource(id = R.string.rename),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        if (recordDescription.isNotEmpty() && isRenameAvailable) {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDescriptionClick() }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                textAlign = TextAlign.Center,
                text = recordDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(start = 12.dp),
                textAlign = TextAlign.Start,
                text = timeStart,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                textAlign = TextAlign.Center,
                text = recordInfo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(end = 12.dp),
                textAlign = TextAlign.Start,
                text = timeEnd,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
        LegacySlider(
            progress = progress,
            onProgressChange = onProgressChange,
            enabled = isSliderEnabled
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimePanelPreview() {
    TimePanel(
        recordName = "Record-14",
        recordDescription = "Some description text",
        recordInfo = "1.2Mb, M4a, 44.1kHz",
        recordDuration = "02:23",
        timeStart = "00:00",
        timeEnd = "05:32",
        progress = 0.3f,
        isSliderEnabled = true,
        isRenameAvailable = true,
        onRenameClick = {},
        onDescriptionClick = {},
        onProgressChange = {},
    )
}

@Preview(showBackground = true)
@Composable
fun TimePanelRecordingProgressPreview() {
    TimePanel(
        recordName = "Recording...",
        recordDescription = "",
        recordInfo = "1.2Mb, M4a, 44.1kHz",
        recordDuration = "02:23",
        timeStart = "",
        timeEnd = "",
        progress = 0.0f,
        isSliderEnabled = false,
        isRenameAvailable = true,
        onRenameClick = {},
        onDescriptionClick = {},
        onProgressChange = {},
    )
}
