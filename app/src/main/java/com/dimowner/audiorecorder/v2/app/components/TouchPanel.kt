package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.util.equalsDelta
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.roundToInt

private const val ANIMATION_DURATION = 500
private const val MAX_MOVE = 250
private const val PLAY_PANEL_HEIGHT_DP = 300

@Composable
fun TouchPanel(
    showRecordPlaybackPanel: Boolean,
    uiHomeState: HomeScreenState,
    isBookmarked: Boolean = false,
    onProgressChange: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekProgress: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onBookmarkClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    // State to keep track of the Card position
    val offsetY = remember { mutableFloatStateOf(0f) }
    val maxMove = with(density) { MAX_MOVE.dp.toPx() }
    val k = (maxMove / (Math.PI / 2f)).toFloat()

    val startY = with(density) { 12.dp.toPx() }

    var cumulativeDrag = remember { 0f }
    val animatableY = remember { Animatable(startY) }

    // Get a CoroutineScope tied to the Composable
    val coroutineScope = rememberCoroutineScope()

    // Define a threshold for Y coordinate movement
    val playPanelHeight = remember { mutableFloatStateOf(with(density) { PLAY_PANEL_HEIGHT_DP.dp.toPx() }) }

    // Modifier to make the text draggable
    val modifier = Modifier
        .offset { IntOffset(0, animatableY.value.roundToInt()) }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    offsetY.floatValue = startY
                    cumulativeDrag = startY
                },
                onDragEnd = {
                    // Animate back to start position
                    if (offsetY.floatValue.absoluteValue > playPanelHeight.floatValue * 0.5) {
                        coroutineScope.launch {
                            animatableY.animateTo(
                                playPanelHeight.floatValue * 1.5f,
                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
                            ) {
                                val height = playPanelHeight.floatValue * 1.5f

                                if (animatableY.value.equalsDelta(height)) {
                                    coroutineScope.launch {
                                        delay(600L)
                                        animatableY.snapTo(startY)
                                    }
                                }
                            }
                            offsetY.floatValue = startY
                            onStopClick()
                        }
                    } else {
                        coroutineScope.launch {
                            animatableY.animateTo(
                                startY,
                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
                            )
                        }
                    }
                },
                onDragCancel = {
                    if (offsetY.floatValue.absoluteValue > playPanelHeight.floatValue * 0.5) {
                        coroutineScope.launch {
                            animatableY.animateTo(
                                playPanelHeight.floatValue * 1.5f,
                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
                            )
                            offsetY.floatValue = startY
                            onStopClick()
                        }
                    } else {
                        // Animate back to start position
                        coroutineScope.launch {
                            animatableY.animateTo(
                                startY,
                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
                            )
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    cumulativeDrag += dragAmount.y
                    offsetY.floatValue = cumulativeDrag
                    offsetY.floatValue = k * atan(offsetY.floatValue / k)
                    coroutineScope.launch {
                        animatableY.snapTo(offsetY.floatValue)
                    }
                }
            )
        }

    AnimatedVisibility(
        visible = showRecordPlaybackPanel,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Card(
            modifier = modifier
                .wrapContentSize()
                .onSizeChanged {
                    playPanelHeight.floatValue = it.height.toFloat()
                },
        ) {
            RecordPlaybackPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                uiState = uiHomeState,
                isBookmarked = isBookmarked,
                onProgressChange = onProgressChange,
                onSeekStart = onSeekStart,
                onSeekProgress = onSeekProgress,
                onSeekEnd = onSeekEnd,
                onPlayClick = onPlayClick,
                onStopClick = onStopClick,
                onPauseClick = onPauseClick,
                onBookmarkClick = onBookmarkClick,
                onPrevClick = onPrevClick,
                onNextClick = onNextClick,
            )
        }
    }
}