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
import com.dimowner.audiorecorder.AppConstantsV2.RECORDING_GRID_STEP
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.TEST_WAVEFORM_DATA
import com.dimowner.audiorecorder.v2.app.TEST_WAVEFORM_DATA_DURATION_MILLS
import com.dimowner.audiorecorder.v2.app.getTestWaveformData

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
                    style = Paint.Style.FILL
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
                durationPx, it,
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
                            viewState.value.durationPx, size,
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
        viewState.waveformShiftPx + state.durationSample * viewState.pxPerSample,
        viewState.textIndent,
        viewState.waveformShiftPx + state.durationSample * viewState.pxPerSample,
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
            if (sampleIndex >= state.durationSample) {
                sampleIndex = state.durationSample - 1
            }
            val xPos = viewState.waveformShiftPx + index
            if (xPos >= 0 && xPos <= size.width && step + 3 < viewState.drawLinesArray.size) {
                // Adjust sample index by the buffer offset (used during RECORDING ONLY when
                // waveformData is a sliding window over the full sample timeline).
                val bufferIndex = sampleIndex - state.waveformDataOffset
                val amp = if (bufferIndex in state.waveformData.indices) {
                    state.waveformData[bufferIndex]
                } else {
                    0
                }
                viewState.drawLinesArray[step] = xPos
                viewState.drawLinesArray[step + 1] = (half + amp*(half-textIndent)/AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE + 1)
                viewState.drawLinesArray[step + 2] = xPos
                viewState.drawLinesArray[step + 3] = (half - amp*(half-textIndent)/AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE - 1)
                step += 4
            }
        }
        canvas.nativeCanvas.drawLines(viewState.drawLinesArray, 0,
            viewState.drawLinesArray.size, paintState.waveformPaint)
    }
}

private fun updateShift(
    durationPx: Float,
    size: IntSize,
    px: Int
): Float {
    var shift = px.toFloat()
    val half = size.width/2
    if (shift <= -durationPx+half) {
        shift = -durationPx+half
    }
    if (shift > half) {
        shift = half.toFloat()
    }
    return shift
}

@Preview(showBackground = true)
@Composable
fun WaveformComposeViewPreview() {
    WaveformComposeView(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        state = getTestWaveformData(),
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
    val scale = TEST_WAVEFORM_DATA_DURATION_MILLS * (AppConstantsV2.DEFAULT_WIDTH_SCALE / AppConstantsV2.SHORT_RECORD)
    WaveformComposeView(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        state = WaveformState(
            widthScale = scale,
            progressMills = TEST_WAVEFORM_DATA_DURATION_MILLS,
            durationMills = TEST_WAVEFORM_DATA_DURATION_MILLS,
            waveformData = TEST_WAVEFORM_DATA,
            durationSample = TEST_WAVEFORM_DATA.size,
            gridStepMills = RECORDING_GRID_STEP
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
    /**
     * Offset of the first element in [waveformData] relative to the total sample timeline.
     * Used during recording when [waveformData] is a sliding window buffer: the view only
     * holds the last N samples, while [durationSample] reflects the total number of samples
     * recorded so far. The drawing code skips pixel positions whose sample index falls before
     * this offset, ensuring the waveform scrolls in sync with the grid.
     */
    val waveformDataOffset: Int = 0,
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
        if (waveformDataOffset != other.waveformDataOffset) return false

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
        result = 31 * result + waveformDataOffset.hashCode()
        return result
    }
}
