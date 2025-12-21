package com.dimowner.audiorecorder.v2.app.components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.calculateGridStep
import com.dimowner.audiorecorder.v2.app.calculateScale

private val GIRD_SUBLINE_HEIGHT: Float = AndroidUtils.dpToPx(12)
private val PADD: Float = AndroidUtils.dpToPx(6)

@Composable
fun WaveformComposeView(
    modifier: Modifier,
    state: WaveformState,
    showTimeline: Boolean,
    onSeekStart: () -> Unit,
    onSeekEnd: (mills: Long) -> Unit,
    onSeekProgress: (mills: Long) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewState = remember {
        mutableStateOf(WaveformViewState(drawLinesArray = floatArrayOf()))
    }
    val waveformColor =  MaterialTheme.colorScheme.primary.toArgb()
    val gridColor =  MaterialTheme.colorScheme.secondary.toArgb()
    val lineColor =  MaterialTheme.colorScheme.inverseSurface.toArgb()
    val textColor =  MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val paintState = remember {
        mutableStateOf(
            PaintState(
                waveformPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.3f
                    isAntiAlias = true
                    alpha = 255
                    color = waveformColor
                },
                linePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = AndroidUtils.dpToPx(1.5f)
                    isAntiAlias = true
                    color = lineColor
                },
                gridPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    color = gridColor
                    strokeWidth = AndroidUtils.dpToPx(1) / 2
                },
                scrubberPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = AndroidUtils.dpToPx(2f)
                    isAntiAlias = false
                    color = ContextCompat.getColor(context, R.color.md_yellow_A700)
                },
                textPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = AndroidUtils.dpToPx(1f)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    color = textColor
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    textSize = viewState.value.textHeight
                },
            )
        )
    }

    Canvas(modifier = modifier
        .onSizeChanged {
            val durationPx = it.width * state.widthScale
            val millsPerPx = state.durationMills / durationPx
            val pxPerMill = durationPx / state.durationMills
            val pxPerSample = durationPx / state.durationSample
            val samplePerPx = state.durationSample / durationPx
            val textHeight = with(density) { 14.sp.toPx() }
            val waveformShiftPx = updateShift(
                viewState.value, it,
                -(state.progressMills * pxPerMill).toInt()+it.width/2
            )

            viewState.value = viewState.value.copy(
                textIndent = if (showTimeline) textHeight + PADD else 0f,
                waveformShiftPx = waveformShiftPx,
                durationPx = durationPx,
                millsPerPx = millsPerPx,
                pxPerMill = pxPerMill,
                pxPerSample = pxPerSample,
                samplePerPx = samplePerPx,
                drawLinesArray = FloatArray(it.width * 4),
                textHeight = textHeight
            )
        }
        .pointerInput(Unit) {
            if (!state.isRecording) {
                detectDragGestures(
                    onDragStart = {
                        onSeekStart()
                    },
                    onDrag = { change, dragAmount ->
                        val shift = updateShift(
                            viewState.value, size,
                            (viewState.value.waveformShiftPx + dragAmount.x).toInt()
                        )
                        val half = size.width / 2
                        viewState.value = viewState.value.copy(
                            waveformShiftPx = shift
                        )
                        onSeekProgress(((-shift + half) * viewState.value.millsPerPx).toLong())
                    },
                    onDragEnd = {
                        val shift = viewState.value.waveformShiftPx.toInt()
                        val half = size.width / 2
                        onSeekEnd(((-shift + half) * viewState.value.millsPerPx).toLong())
                    },
                )
            }
        }
    ) {
        drawIntoCanvas { canvas ->
            drawGrid(canvas, size, viewState.value, state, showTimeline, paintState.value)
            drawStartAndEnd(canvas, size, viewState.value, state, paintState.value)
            drawWaveform(canvas, size, viewState.value, state, paintState.value)
            //Draw scrubber
            canvas.nativeCanvas.drawLine(
                size.width / 2f,
                0f,
                size.width / 2f,
                size.height,
                paintState.value.scrubberPaint
            )
        }
    }
}

private fun drawStartAndEnd(
    canvas: Canvas,
    size: Size,
    viewState: WaveformViewState,
    state: WaveformState,
    paintState: PaintState
) {
    //Draw waveform start indication
    canvas.nativeCanvas.drawLine(
        viewState.waveformShiftPx,
        viewState.textIndent,
        viewState.waveformShiftPx,
        size.height - viewState.textIndent,
        paintState.linePaint
    )
    //Draw waveform end indication
    canvas.nativeCanvas.drawLine(
        viewState.waveformShiftPx + state.waveformData.size * viewState.pxPerSample,
        viewState.textIndent,
        viewState.waveformShiftPx + state.waveformData.size * viewState.pxPerSample,
        size.height - viewState.textIndent,
        paintState.linePaint
    )
}

private fun drawGrid(
    canvas: Canvas,
    size: Size,
    viewState: WaveformViewState,
    state: WaveformState,
    showTimeline: Boolean,
    paintState: PaintState
) {
    val subStepPx = (state.gridStepMills / 2) * viewState.pxPerMill
    val halfWidthMills = (size.width / 2) * viewState.millsPerPx
    val gridEndMills = state.durationMills + halfWidthMills.toInt() + state.gridStepMills
    val halfScreenStepCount = (halfWidthMills/state.gridStepMills).toInt()

    for (indexMills in -halfScreenStepCount*state.gridStepMills until gridEndMills step state.gridStepMills) {
        val sampleIndexPx = indexMills * viewState.pxPerMill
        val xPos = (viewState.waveformShiftPx + sampleIndexPx)
        if (xPos >= -state.gridStepMills && xPos <= size.width + state.gridStepMills) {
            //Draw grid lines
            //Draw main grid line
            canvas.nativeCanvas.drawLine(
                xPos,
                viewState.textIndent,
                xPos,
                size.height - viewState.textIndent,
                paintState.gridPaint
            )
            val xSubPos = xPos + subStepPx
            //Draw grid top sub-line
            canvas.nativeCanvas.drawLine(
                xSubPos,
                viewState.textIndent,
                xSubPos,
                GIRD_SUBLINE_HEIGHT + viewState.textIndent,
                paintState.gridPaint
            )
            //Draw grid bottom sub-line
            canvas.nativeCanvas.drawLine(
                xSubPos,
                size.height - GIRD_SUBLINE_HEIGHT - viewState.textIndent,
                xSubPos,
                size.height - viewState.textIndent,
                paintState.gridPaint
            )

            if (showTimeline) {
                //Draw timeline texts
                if (indexMills >= 0) {
                    val text = TimeUtils.formatTimeIntervalHourMin(indexMills)
                    //Bottom timeline text
                    canvas.nativeCanvas.drawText(text, xPos, size.height - PADD, paintState.textPaint)
                    //Top timeline text
                    canvas.nativeCanvas.drawText(text, xPos, viewState.textHeight, paintState.textPaint)
                }
            }
        }
    }
}

private fun drawWaveform(
    canvas: Canvas,
    size: Size,
    viewState: WaveformViewState,
    state: WaveformState,
    paintState: PaintState
) {
    if (state.waveformData.isNotEmpty()) {
        for (i in viewState.drawLinesArray.indices) {
            viewState.drawLinesArray[i] = 0f
        }
        val half = size.height / 2
        val textIndent = viewState.textIndent
        var step = 0
        for (index in 0 until viewState.durationPx.toInt()) {
            var sampleIndex = (index * viewState.samplePerPx).toInt()
            if (sampleIndex >= state.waveformData.size) {
                sampleIndex = state.waveformData.size - 1
            }
            val xPos = viewState.waveformShiftPx + index
            if (xPos >= 0 && xPos <= size.width && step + 3 < viewState.drawLinesArray.size) {
                viewState.drawLinesArray[step] = xPos
                viewState.drawLinesArray[step + 1] = (half + state.waveformData[sampleIndex]*(half-textIndent)/AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE + 1)
                viewState.drawLinesArray[step + 2] = xPos
                viewState.drawLinesArray[step + 3] = (half - state.waveformData[sampleIndex]*(half-textIndent)/AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE - 1)
                step += 4
            }
        }
        canvas.nativeCanvas.drawLines(viewState.drawLinesArray, 0,
            viewState.drawLinesArray.size, paintState.waveformPaint)
    }
}

private fun updateShift(
    viewState: WaveformViewState,
    size: IntSize,
    px: Int
): Float {
    var shift = px.toFloat()
    val half = size.width/2
    if (shift <= -viewState.durationPx+half) {
        shift = -viewState.durationPx+half
    }
    if (shift > half) {
        shift = half.toFloat()
    }
    return shift
}

@Preview(showBackground = true)
@Composable
fun WaveformComposeViewPreview() {
    val waveformData = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 234, 526, 0, 0, 0, 0, 8424, 4394, 7514, 23400, 13754, 10400, 21118, 12018, 24986, 6656, 7514, 10926, 32767, 24186, 21118, 16250, 10400, 7962, 5850, 4738, 4394, 26624, 10926, 5850, 4738, 5096, 4062, 11466, 10926, 14976, 22626, 21118, 30056, 21118, 32767, 23400, 21866, 21866, 23400, 27462, 25798, 24186, 14976, 18258, 11466, 15606, 23400, 29178, 29178, 26624, 16906, 12018, 16906, 12018, 14358, 7962, 22626, 22626, 14358, 6656, 4738, 3744, 2866, 4394, 3744, 4394, 3146, 2600, 416, 27462, 28314, 14976, 14976, 14976, 29178, 23400, 24186, 19662, 28314, 26624, 13162, 18258, 18258, 22626, 30946, 19662, 9886, 6246, 5096, 3744, 3744, 4394, 24186, 21118, 7078, 3146, 1878, 3438, 1878, 9386, 21118, 12584, 14976, 12584, 17576, 5850, 29178, 23400, 4738, 4062, 26624, 26624, 18954, 13754, 14358, 13162, 13754, 7962, 17576, 31850, 13754, 13162, 13754, 10400, 8898, 5850, 5466, 2866, 23400, 9386, 7514, 5850, 7962, 7514, 5466, 8424, 6656, 5850, 4394, 19662, 3438, 26624, 22626, 12018, 7514, 24986, 24186, 16250, 30946, 21118, 11466, 9886, 12584, 25798, 30056, 27462, 17576, 16906, 20384, 19662, 18258, 19662, 9386, 19662, 10400, 3744, 3146, 3744, 8424, 4062, 7078, 15606, 22626, 20384, 24186, 18258, 27462, 25798, 12584, 14358, 18954, 24986, 24986, 19662, 20384, 23400, 15606, 16906, 17576, 16906, 13162, 29178, 8898, 7514, 4738, 2600, 2106, 2866, 3438, 28314, 22626, 4394, 1462, 1098, 2866, 1878, 4394, 2600, 2346, 2346, 5466, 4738, 526, 29178, 20384, 19662, 6656, 28314, 21866, 20384, 11466, 21866, 15606, 18954, 18954, 16250, 32767, 22626, 9886, 18954, 16906, 13162, 8898, 6246, 5466, 30056, 7962, 5466, 5850, 23400, 17576, 29178, 10400, 14976, 22626, 19662, 20384, 14976, 32767, 13754, 13162, 13162, 32767, 20384, 8898, 7078, 16250, 18258, 15606, 13162, 14358, 29178, 15606, 5466, 4394, 2106, 162, 0, 0, 0, 786, 58, 58, 526, 104, 1462, 526, 786, 26, 0, 0, 0, 0, 29178, 17576, 24986, 29178, 28314, 23400, 20384, 30056, 27462, 31850, 32767, 27462, 32767, 25798, 32767, 30056, 29178, 23400, 22626, 31850, 14976, 16906, 30946, 25798, 27462, 22626, 15606, 29178, 13162, 23400, 21866, 24186, 21118, 24186, 28314, 29178, 30056, 16250, 18954, 16906, 29178, 30056, 27462, 20384, 29178, 25798, 12584, 21118, 20384, 31850, 21866, 21866, 26624, 18954, 14358, 21866, 24186, 25798, 27462, 21118, 22626, 21118, 24986, 13754, 13754, 30056, 22626, 10400, 27462, 30056, 24986, 29178, 20384, 23400, 28314, 29178, 29178, 17576, 20384, 23400, 27462, 13162, 24186, 20384, 31850, 25798, 25798, 14976, 24986, 22626, 24186, 23400, 30056, 30946, 17576, 16250, 13754, 16250, 24986, 24986, 17576, 29178, 20384, 30056, 19662, 18258, 24986, 30056, 18954, 24186, 30946, 32767, 32767, 27462, 30946, 13162, 24186, 21866, 31850, 30056, 24986, 30946, 26624, 22626, 21866, 25798, 28314, 30946, 32767, 30056, 30946, 29178, 23400, 28314, 30056, 30946, 26624, 30946, 29178, 21118, 29178, 21866, 29178, 28314, 21118, 31850, 32767, 29178, 31850, 30946, 31850, 25798, 26624, 30946, 32767, 32767, 30946, 28314, 28314, 32767, 30946, 28314, 28314, 31850, 30946, 30946, 30056, 21866, 18954, 30056, 19662, 31850, 29178, 31850, 22626, 25798, 28314, 26624, 29178, 25798, 28314, 28314, 29178, 28314, 27462, 27462, 25798, 22626, 18258, 29178, 23400, 32767, 21866, 18954, 24186, 19662, 14976, 17576, 23400, 23400, 24986, 27462, 26624, 19662, 25798, 21866, 30056, 30056, 30946, 32767, 18954, 28314, 10926, 30056, 24986, 31850, 25798, 29178, 27462, 22626, 24186, 24986, 25798, 17576, 28314, 27462, 29178, 30056, 20384, 18258, 21118, 24986, 24186, 25798, 29178, 29178, 27462, 22626, 16906, 23400, 21118, 27462, 26624, 26624, 31850, 27462, 23400, 26624, 21866, 20384, 25798, 29178, 20384, 13754, 9886, 9886, 10926, 29178, 25798, 24986, 22626, 14358, 1878, 0, 0, 0, 0, 0, 8898, 2600, 25798, 9886, 6656, 7962, 6246, 6246, 5096, 5850, 6656, 5096, 8424, 8424, 4062, 4394, 4394, 5850, 4394, 9886, 28314, 8424, 6246, 58, 6656, 3438, 0, 12018, 8898, 7514, 1878, 1098, 58, 0, 318, 318, 8424, 12018, 13162, 7962, 7514, 7514, 7514, 6246, 3146, 18954, 84)
    val durationMills = 58728L
    WaveformComposeView(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        state = WaveformState(
            widthScale = calculateScale(durationMills),
            durationMills = durationMills,
            waveformData = waveformData,
            durationSample = waveformData.size,
            gridStepMills = calculateGridStep(durationMills)
        ),
        showTimeline = true,
        onSeekStart = {},
        onSeekProgress = { mills ->
        },
        onSeekEnd = { mills ->
        }
    )
}

@Preview(showBackground = true)
@Composable
fun WaveformComposeViewRecordingPreview() {
    val waveformData = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 234, 526, 0, 0, 0, 0, 8424, 4394, 7514, 23400, 13754, 10400, 21118, 12018, 24986, 6656, 7514, 10926, 32767, 24186, 21118, 16250, 10400, 7962, 5850, 4738, 4394, 26624, 10926, 5850, 4738, 5096, 4062, 11466, 10926, 14976, 22626, 21118, 30056, 21118, 32767, 23400, 21866, 21866, 23400, 27462, 25798, 24186, 14976, 18258, 11466, 15606, 23400, 29178, 29178, 26624, 16906, 12018, 16906, 12018, 14358, 7962, 22626, 22626, 14358, 6656, 4738, 3744, 2866, 4394, 3744, 4394, 3146, 2600, 416, 27462, 28314, 14976, 14976, 14976, 29178, 23400, 24186, 19662, 28314, 26624, 13162, 18258, 18258, 22626, 30946, 19662, 9886, 6246, 5096, 3744, 3744, 4394, 24186, 21118, 7078, 3146, 1878, 3438, 1878, 9386, 21118, 12584, 14976, 12584, 17576, 5850, 29178, 23400, 4738, 4062, 26624, 26624, 18954, 13754, 14358, 13162, 13754, 7962, 17576, 31850, 13754, 13162, 13754, 10400, 8898, 5850, 5466, 2866, 23400, 9386, 7514, 5850, 7962, 7514, 5466, 8424, 6656, 5850, 4394, 19662, 3438, 26624, 22626, 12018, 7514, 24986, 24186, 16250, 30946, 21118, 11466, 9886, 12584, 25798, 30056, 27462, 17576, 16906, 20384, 19662, 18258, 19662, 9386, 19662, 10400, 3744, 3146, 3744, 8424, 4062, 7078, 15606, 22626, 20384, 24186, 18258, 27462, 25798, 12584, 14358, 18954, 24986, 24986, 19662, 20384, 23400, 15606, 16906, 17576, 16906, 13162, 29178, 8898, 7514, 4738, 2600, 2106, 2866, 3438, 28314, 22626, 4394, 1462, 1098, 2866, 1878, 4394, 2600, 2346, 2346, 5466, 4738, 526, 29178, 20384, 19662, 6656, 28314, 21866, 20384, 11466, 21866, 15606, 18954, 18954, 16250, 32767, 22626, 9886, 18954, 16906, 13162, 8898, 6246, 5466, 30056, 7962, 5466, 5850, 23400, 17576, 29178, 10400, 14976, 22626, 19662, 20384, 14976, 32767, 13754, 13162, 13162, 32767, 20384, 8898, 7078, 16250, 18258, 15606, 13162, 14358, 29178, 15606, 5466, 4394, 2106, 162, 0, 0, 0, 786, 58, 58, 526, 104, 1462, 526, 786, 26, 0, 0, 0, 0, 29178, 17576, 24986, 29178, 28314, 23400, 20384, 30056, 27462, 31850, 32767, 27462, 32767, 25798, 32767, 30056, 29178, 23400, 22626, 31850, 14976, 16906, 30946, 25798, 27462, 22626, 15606, 29178, 13162, 23400, 21866, 24186, 21118, 24186, 28314, 29178, 30056, 16250, 18954, 16906, 29178, 30056, 27462, 20384, 29178, 25798, 12584, 21118, 20384, 31850, 21866, 21866, 26624, 18954, 14358, 21866, 24186, 25798, 27462, 21118, 22626, 21118, 24986, 13754, 13754, 30056, 22626, 10400, 27462, 30056, 24986, 29178, 20384, 23400, 28314, 29178, 29178, 17576, 20384, 23400, 27462, 13162, 24186, 20384, 31850, 25798, 25798, 14976, 24986, 22626, 24186, 23400, 30056, 30946, 17576, 16250, 13754, 16250, 24986, 24986, 17576, 29178, 20384, 30056, 19662, 18258, 24986, 30056, 18954, 24186, 30946, 32767, 32767, 27462, 30946, 13162, 24186, 21866, 31850, 30056, 24986, 30946, 26624, 22626, 21866, 25798, 28314, 30946, 32767, 30056, 30946, 29178, 23400, 28314, 30056, 30946, 26624, 30946, 29178, 21118, 29178, 21866, 29178, 28314, 21118, 31850, 32767, 29178, 31850, 30946, 31850, 25798, 26624, 30946, 32767, 32767, 30946, 28314, 28314, 32767, 30946, 28314, 28314, 31850, 30946, 30946, 30056, 21866, 18954, 30056, 19662, 31850, 29178, 31850, 22626, 25798, 28314, 26624, 29178, 25798, 28314, 28314, 29178, 28314, 27462, 27462, 25798, 22626, 18258, 29178, 23400, 32767, 21866, 18954, 24186, 19662, 14976, 17576, 23400, 23400, 24986, 27462, 26624, 19662, 25798, 21866, 30056, 30056, 30946, 32767, 18954, 28314, 10926, 30056, 24986, 31850, 25798, 29178, 27462, 22626, 24186, 24986, 25798, 17576, 28314, 27462, 29178, 30056, 20384, 18258, 21118, 24986, 24186, 25798, 29178, 29178, 27462, 22626, 16906, 23400, 21118, 27462, 26624, 26624, 31850, 27462, 23400, 26624, 21866, 20384, 25798, 29178, 20384, 13754, 9886, 9886, 10926, 29178, 25798, 24986, 22626, 14358, 1878, 0, 0, 0, 0, 0, 8898, 2600, 25798, 9886, 6656, 7962, 6246, 6246, 5096, 5850, 6656, 5096, 8424, 8424, 4062, 4394, 4394, 5850, 4394, 9886, 28314, 8424, 6246, 58, 6656, 3438, 0, 12018, 8898, 7514, 1878, 1098, 58, 0, 318, 318, 8424, 12018, 13162, 7962, 7514, 7514, 7514, 6246, 3146, 18954, 84)
    val durationMills = 58728L
    WaveformComposeView(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        state = WaveformState(
            widthScale = durationMills * (AppConstantsV2.DEFAULT_WIDTH_SCALE / AppConstantsV2.SHORT_RECORD), //TODO: fix
            progressMills = durationMills,
            durationMills = durationMills,
            waveformData = waveformData,
            durationSample = waveformData.size,
            gridStepMills = 2000
        ),
        showTimeline = true,
        onSeekStart = {},
        onSeekProgress = { mills ->
        },
        onSeekEnd = { mills ->
        }
    )
}

data class PaintState(
    val waveformPaint: Paint = Paint(),
    val linePaint: Paint = Paint(),
    val gridPaint: Paint = Paint(),
    val scrubberPaint: Paint = Paint(),
    val textPaint: Paint = TextPaint(),
)

data class WaveformViewState(
    val waveformShiftPx: Float = 0F,
    val textHeight: Float = AndroidUtils.dpToPx(14),
    val textIndent: Float = textHeight + PADD,

    val drawLinesArray: FloatArray,
    val durationPx: Float = 0F,
    val millsPerPx: Float = 0F,
    val pxPerMill: Float = 0F,
    val pxPerSample: Float = 0F,
    val samplePerPx: Float = 0F,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformViewState) return false

        if (waveformShiftPx != other.waveformShiftPx) return false
        if (textHeight != other.textHeight) return false
        if (textIndent != other.textIndent) return false
        if (!drawLinesArray.contentEquals(other.drawLinesArray)) return false
        if (durationPx != other.durationPx) return false
        if (millsPerPx != other.millsPerPx) return false
        if (pxPerMill != other.pxPerMill) return false
        if (pxPerSample != other.pxPerSample) return false
        if (samplePerPx != other.samplePerPx) return false

        return true
    }

    override fun hashCode(): Int {
        var result = waveformShiftPx.hashCode()
        result = 31 * result + textHeight.hashCode()
        result = 31 * result + textIndent.hashCode()
        result = 31 * result + drawLinesArray.contentHashCode()
        result = 31 * result + durationPx.hashCode()
        result = 31 * result + millsPerPx.hashCode()
        result = 31 * result + pxPerMill.hashCode()
        result = 31 * result + pxPerSample.hashCode()
        result = 31 * result + samplePerPx.hashCode()
        return result
    }
}

data class WaveformState(
    val durationMills: Long = 0L,
    val progressMills: Long = 0L,
    /** Waveform data where 1 element is a sample and value of the element is amplitude (value between 0-1000). */
    val waveformData: IntArray = intArrayOf(),
    /** If true, view in Recording mode, otherwise view in Playback mode. Playback mode by default. */
    val isRecording: Boolean = false,

    /** 1 means that waveform will take whole view width. 2 means that waveform will take double view width to draw.  */
    val widthScale: Float = 1.5f,
    val durationSample: Int = 0,
    val gridStepMills: Long = 4000,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformState) return false

        if (durationMills != other.durationMills) return false
        if (progressMills != other.progressMills) return false
        if (!waveformData.contentEquals(other.waveformData)) return false
        if (widthScale != other.widthScale) return false
        if (isRecording != other.isRecording) return false
        if (durationSample != other.durationSample) return false
        if (gridStepMills != other.gridStepMills) return false

        return true
    }

    override fun hashCode(): Int {
        var result = durationMills.hashCode()
        result = 31 * result + progressMills.hashCode()
        result = 31 * result + waveformData.contentHashCode()
        result = 31 * result + widthScale.hashCode()
        result = 31 * result + isRecording.hashCode()
        result = 31 * result + durationSample
        result = 31 * result + gridStepMills.hashCode()
        return result
    }
}
