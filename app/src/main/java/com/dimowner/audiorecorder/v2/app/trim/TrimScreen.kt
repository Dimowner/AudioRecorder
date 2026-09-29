package com.dimowner.audiorecorder.v2.app.trim

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    onPopBackStack: () -> Unit,
    onRecordSelected: (Long) -> Unit,
    uiState: TrimState,
    onAction: (TrimAction) -> Unit,
    event: SharedFlow<TrimEvent?>,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        event.collectLatest { e ->
            when (e) {
                is TrimEvent.TrimApplied -> onPopBackStack()
                is TrimEvent.TrimSavedAsNew -> onRecordSelected(e.recordId)
                is TrimEvent.NavigateBack -> onPopBackStack()
                else -> {}
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short,
            )
            onAction(TrimAction.DismissError)
        }
    }

    if (uiState.showDialog) {
        AlertDialog(
            onDismissRequest = { onAction(TrimAction.DismissDialog) },
            title = { Text("Save trimmed recording") },
            text = { Text("Overwrite the original file, or save as a new recording?") },
            confirmButton = {
                TextButton(onClick = { onAction(TrimAction.SaveChoice(overwrite = true)) }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TrimAction.SaveChoice(overwrite = false)) }) {
                    Text("Save new")
                }
            },
        )
    }

    if (uiState.showExitDialog) {
        AlertDialog(
            onDismissRequest = { onAction(TrimAction.DismissExitDialog) },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to go back?") },
            confirmButton = {
                TextButton(onClick = { onAction(TrimAction.ConfirmExit) }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TrimAction.DismissExitDialog) }) {
                    Text("Cancel")
                }
            },
        )
    }

    BackHandler {
        onAction(TrimAction.BackPressed)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.recordInfo?.let { "${it.name}.${it.format}" }
                            ?: stringResource(R.string.trim),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(TrimAction.BackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.recordInfo != null) {
            val duration = uiState.recordInfo.duration.coerceAtLeast(1L)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // File info
                Text(
                    text = "${uiState.recordInfo.size / 1024} KB, ${uiState.recordInfo.format}, " +
                            "${uiState.recordInfo.bitrate / 1000} kbps, ${uiState.recordInfo.sampleRate / 1000} kHz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Waveform with playhead
                val playheadFraction = if (duration > 0) {
                    uiState.playProgressMills.toFloat() / duration
                } else {
                    0f
                }

                val waveformHandlePadding = 16.dp

                Spacer(modifier = Modifier.height(8.dp))

                // Draggable range bar
                val visualStartFraction = remember { mutableFloatStateOf(uiState.startMills.toFloat() / duration) }
                val visualEndFraction = remember { mutableFloatStateOf(uiState.endMills.toFloat() / duration) }

                LaunchedEffect(uiState.startMills) {
                    visualStartFraction.floatValue = uiState.startMills.toFloat() / duration
                }
                LaunchedEffect(uiState.endMills) {
                    visualEndFraction.floatValue = uiState.endMills.toFloat() / duration
                }

                TrimWaveform(
                    amps = uiState.recordInfo.amps,
                    startFraction = visualStartFraction.floatValue,
                    endFraction = visualEndFraction.floatValue,
                    playheadFraction = playheadFraction,
                    isPlaying = uiState.isPlaying,
                    onPositionTap = { fraction ->
                        val seekMills = (fraction * duration).toLong()
                        onAction(TrimAction.SeekPlayhead(seekMills))
                    },
                    height = 100.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = waveformHandlePadding),
                )

                Spacer(modifier = Modifier.height(8.dp))

                TrimRangeBar(
                    startFraction = visualStartFraction.floatValue,
                    endFraction = visualEndFraction.floatValue,
                    isPlaying = uiState.isPlaying,
                    onStartFractionChange = { visualStartFraction.floatValue = it },
                    onEndFractionChange = { visualEndFraction.floatValue = it },
                    onStartChange = { fraction ->
                        onAction(TrimAction.SetStartMills((fraction * duration).toLong()))
                    },
                    onEndChange = { fraction ->
                        onAction(TrimAction.SetEndMills((fraction * duration).toLong()))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = waveformHandlePadding)
                        .height(48.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Start controls: [-][time][+]
                // End controls: [-][time][+]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.trim_start),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallButton(
                                label = "\u2013",
                                onClick = { onAction(TrimAction.SetStartMills(uiState.startMills - 1000)) },
                                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                            )
                            TrimTimeCard(timeMs = uiState.startMills)
                            SmallButton(
                                label = "+",
                                onClick = { onAction(TrimAction.SetStartMills(uiState.startMills + 1000)) },
                                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .clickable { onAction(TrimAction.SetStartToPlayhead) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "set",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.trim_end),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SmallButton(
                                label = "\u2013",
                                onClick = { onAction(TrimAction.SetEndMills(uiState.endMills - 1000)) },
                                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                            )
                            TrimTimeCard(timeMs = uiState.endMills)
                            SmallButton(
                                label = "+",
                                onClick = { onAction(TrimAction.SetEndMills(uiState.endMills + 1000)) },
                                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .clickable { onAction(TrimAction.SetEndToPlayheadAndStop) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "set",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(12.dp))

                // Playback controls: [Play from start] [Play/Pause] [Last 5s]
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Start button — left aligned
                    Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 70.dp)) {
                        IconButton(
                            onClick = { onAction(TrimAction.PlayFromStart) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_previous),
                                contentDescription = null,
                            )
                        }
                    }

                    // Play button — center
                    IconButton(
                        onClick = { onAction(TrimAction.PlayPauseToggle) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    // Last 5s button — right aligned
                    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 70.dp)) {
                        IconButton(
                            onClick = { onAction(TrimAction.PlayLastFiveSeconds) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_last_5s),
                                contentDescription = null,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Transport clock
                val trimmedPosition = (uiState.playProgressMills - uiState.startMills).coerceAtLeast(0L)
                Text(
                    text = TimeUtils.formatTimeIntervalHourMinSec2(trimmedPosition),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 70.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Trimmed duration
                Text(
                    text = stringResource(
                        R.string.trim_duration,
                        TimeUtils.formatTimeIntervalHourMinSec2(uiState.endMills - uiState.startMills),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Apply trim button
                Button(
                    onClick = { onAction(TrimAction.ApplyTrim) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isTrimming && !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (uiState.isTrimming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.trim),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TrimWaveform(
    amps: IntArray,
    startFraction: Float,
    endFraction: Float,
    playheadFraction: Float,
    isPlaying: Boolean,
    onPositionTap: ((Float) -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (amps.isEmpty()) return

    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val activeColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error

    val tapModifier = if (onPositionTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val fraction = offset.x / size.width
                onPositionTap(fraction.coerceIn(0f, 1f))
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.height(height).then(tapModifier)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val half = canvasHeight / 2f
        val durationSample = amps.size
        if (durationSample == 0) return@Canvas
        val samplePerPx = durationSample / canvasWidth
        val widthPx = canvasWidth.toInt()

        val inactivePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            isAntiAlias = true
            color = mutedColor.toArgb()
        }

        val activePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
            color = activeColor.toArgb()
        }

        val activeStart = (startFraction * widthPx).toInt().coerceIn(0, widthPx)
        val activeEnd = (endFraction * widthPx).toInt().coerceIn(0, widthPx)

        // Helper to draw a waveform segment
        fun drawSegment(fromPx: Int, toPx: Int, paint: Paint) {
            val len = toPx - fromPx
            if (len <= 0) return
            val lines = FloatArray(len * 4)
            for (i in 0 until len) {
                val index = fromPx + i
                var sampleIndex = (index * samplePerPx).toInt()
                if (sampleIndex >= durationSample) sampleIndex = durationSample - 1
                val amp = amps[sampleIndex]
                val xPos = index.toFloat()
                lines[i * 4] = xPos
                lines[i * 4 + 1] = half + amp * half / AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE + 1
                lines[i * 4 + 2] = xPos
                lines[i * 4 + 3] = half - amp * half / AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE - 1
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawLines(lines, paint)
            }
        }

        // Draw inactive (before start)
        drawSegment(0, activeStart, inactivePaint)
        // Draw active (within trim range)
        drawSegment(activeStart, activeEnd, activePaint)
        // Draw inactive (after end)
        drawSegment(activeEnd, widthPx, inactivePaint)

        // Center line
        drawLine(
            color = mutedColor,
            start = Offset(0f, half),
            end = Offset(canvasWidth, half),
            strokeWidth = 0.5f,
        )

        // Playhead
        if (playheadFraction > 0f && playheadFraction <= 1f) {
            val playheadX = playheadFraction * canvasWidth
            drawLine(
                color = playheadColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, canvasHeight),
                strokeWidth = 2f,
            )
        }
    }
}

@Composable
private fun TrimRangeBar(
    startFraction: Float,
    endFraction: Float,
    isPlaying: Boolean,
    onStartFractionChange: (Float) -> Unit,
    onEndFractionChange: (Float) -> Unit,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleWidth = 14.dp
    val minGap = 0.02f

    val draggingStart = remember { mutableStateOf<Boolean?>(null) }
    val visualStart = remember { mutableFloatStateOf(startFraction) }
    val visualEnd = remember { mutableFloatStateOf(endFraction) }
    val currentIsPlaying = rememberUpdatedState(isPlaying)

    LaunchedEffect(startFraction) {
        if (draggingStart.value != true) visualStart.floatValue = startFraction
    }
    LaunchedEffect(endFraction) {
        if (draggingStart.value != false) visualEnd.floatValue = endFraction
    }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthDp = maxWidth
        val barWidthPx = remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val totalWidth = size.width.toFloat()
                            val touchFraction = (offset.x / totalWidth).coerceIn(0f, 1f)
                            val midpoint = (visualStart.floatValue + visualEnd.floatValue) / 2f
                            draggingStart.value = touchFraction < midpoint
                        },
                        onDragEnd = {
                            val wasStart = draggingStart.value
                            draggingStart.value = null
                            if (wasStart == true) {
                                onStartChange(visualStart.floatValue)
                            } else if (wasStart == false) {
                                onEndChange(visualEnd.floatValue)
                            }
                        },
                        onDragCancel = { draggingStart.value = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val totalWidthPx = barWidthPx.floatValue
                            if (totalWidthPx <= 0f) return@detectDragGestures

                            val delta = dragAmount.x / totalWidthPx
                            if (draggingStart.value == true) {
                                val newFraction = (visualStart.floatValue + delta).coerceIn(0f, visualEnd.floatValue - minGap)
                                visualStart.floatValue = newFraction
                                onStartFractionChange(newFraction)
                                if (!currentIsPlaying.value) onStartChange(newFraction)
                            } else if (draggingStart.value == false) {
                                val newFraction = (visualEnd.floatValue + delta).coerceIn(visualStart.floatValue + minGap, 1f)
                                visualEnd.floatValue = newFraction
                                onEndFractionChange(newFraction)
                                if (!currentIsPlaying.value) onEndChange(newFraction)
                            }
                        }
                    )
                }
                .onGloballyPositioned { coordinates ->
                    barWidthPx.floatValue = coordinates.size.width.toFloat()
                }
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Active selected range
            val highlightStartOffset = maxWidthDp * visualStart.floatValue
            val highlightEndOffset = maxWidthDp * visualEnd.floatValue
            val highlightWidth = highlightEndOffset - highlightStartOffset
            Box(
                modifier = Modifier
                    .width(highlightWidth)
                    .fillMaxHeight()
                    .offset(x = highlightStartOffset)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            )

            // Start handle — right edge at startFraction
            Box(
                modifier = Modifier
                    .width(handleWidth)
                    .fillMaxHeight()
                    .offset(x = maxWidthDp * visualStart.floatValue - handleWidth)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            )

            // End handle — left edge at endFraction
            Box(
                modifier = Modifier
                    .width(handleWidth)
                    .fillMaxHeight()
                    .offset(x = maxWidthDp * visualEnd.floatValue)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            )
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .width(36.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun TrimTimeCard(
    timeMs: Long,
) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = TimeUtils.formatTimeIntervalHourMinSec2(timeMs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
