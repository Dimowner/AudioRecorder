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

package com.dimowner.audiorecorder.v2.app.info.widget

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.v2.app.TEST_WAVEFORM_DATA

/**
 * A static (non-interactive) waveform visualization composable.
 * Draws the waveform using the same rendering approach as WaveformComposeView
 * but without grid, timeline, scrubber, or drag interaction.
 *
 * The entire waveform is scaled to fit the available width.
 */
@Composable
fun WaveformStaticWidget(
    amps: IntArray,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
) {
    if (amps.isEmpty()) return

    val waveformColor = MaterialTheme.colorScheme.primary.toArgb()
    val waveformPaint = remember(waveformColor) {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            isAntiAlias = true
            alpha = 255
            color = waveformColor
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val half = canvasHeight / 2f
        val durationSample = amps.size
        val samplePerPx = durationSample / canvasWidth
        val widthPx = canvasWidth.toInt()

        val drawLinesArray = FloatArray(widthPx * 4)
        var step = 0

        for (index in 0 until widthPx) {
            var sampleIndex = (index * samplePerPx).toInt()
            if (sampleIndex >= durationSample) {
                sampleIndex = durationSample - 1
            }
            val amp = amps[sampleIndex]
            val xPos = index.toFloat()
            drawLinesArray[step] = xPos
            drawLinesArray[step + 1] = half + amp * half / AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE + 1
            drawLinesArray[step + 2] = xPos
            drawLinesArray[step + 3] = half - amp * half / AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE - 1
            step += 4
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawLines(drawLinesArray, 0, drawLinesArray.size, waveformPaint)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WaveformStaticWidgetPreview() {
    WaveformStaticWidget(
        amps = TEST_WAVEFORM_DATA,
        modifier = Modifier.padding(16.dp),
    )
}
