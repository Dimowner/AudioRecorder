/*
 * Copyright 2021 Dmytro Ponomarenko
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
package com.dimowner.audiorecorder.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import java.util.*
import kotlin.math.ceil

private const val DEFAULT_GRID_STEP = 2000L //Milliseconds

class RecordingWaveformView @JvmOverloads constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val DEFAULT_PIXEL_PER_SECOND = AndroidUtils.dpToPx(AppConstants.SHORT_RECORD_DP_PER_SECOND)
	private val GIRD_SUBLINE_HEIGHT = AndroidUtils.dpToPx(12)
	private val PADD = AndroidUtils.dpToPx(6)

	private val waveformPaint = Paint()
	private val gridPaint = Paint()
	private val scrubberPaint = Paint()
	private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

	private var textHeight = 0f
	private var textIndent = 0f

	private var viewWidthPx = 0
	private var viewHeightPx = 0

	private val recordingData: MutableList<Int> = LinkedList<Int>()
	lateinit var drawLinesArray: FloatArray
	private var totalRecordingSize: Int = 0

	private var showTimeline: Boolean = true

	private var durationMills: Long = 0
	private var durationPx: Double = 0.0
	private var millsPerPx: Double = 0.0
	private var pxPerMill: Double = 0.0
	private var samplePerPx: Double = 0.0
	private var samplePerMill: Double = 0.0
	private var gridStepMills: Long = DEFAULT_GRID_STEP

	init {
		isFocusable = false

		waveformPaint.style = Paint.Style.STROKE
		waveformPaint.strokeWidth = AndroidUtils.dpToPx(1)
		waveformPaint.isAntiAlias = true
		waveformPaint.color = ContextCompat.getColor(context, R.color.dark_white)

		scrubberPaint.isAntiAlias = false
		scrubberPaint.style = Paint.Style.STROKE
		scrubberPaint.strokeWidth = AndroidUtils.dpToPx(2)
		scrubberPaint.color = ContextCompat.getColor(context, R.color.md_yellow_A700)

		gridPaint.color = ContextCompat.getColor(context, R.color.md_grey_100_75)
		gridPaint.strokeWidth = AndroidUtils.dpToPx(1) / 2

		textHeight = context.resources.getDimension(R.dimen.text_normal)
		textIndent = textHeight + PADD
		textPaint.color = ContextCompat.getColor(context, R.color.md_grey_100)
		textPaint.strokeWidth = AndroidUtils.dpToPx(1)
		textPaint.textAlign = Paint.Align.CENTER
		textPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
		textPaint.textSize = textHeight
	}

	fun addRecordAmp(amp: Int, mills: Long) {
		recordingData.add(convertAmp(amp.toDouble()))
		totalRecordingSize++
		updateValues(totalRecordingSize, mills)
		if (recordingData.size > pxToSample(viewWidthPx / 2)) {
			recordingData.removeAt(0)
		}
		invalidate()
	}

	fun setRecordingData(data: IntArrayList, durationMills: Long) {
		post {
			recordingData.clear()
			totalRecordingSize = data.size()
			updateValues(totalRecordingSize, durationMills)
			val count = pxToSample(viewWidthPx / 2).toInt()
			if (data.size() > count) {
				for (i in data.size() - count until data.size()) {
					recordingData.add(convertAmp(data[i].toDouble()))
				}
			} else {
				for (i in 0 until data.size()) {
					recordingData.add(convertAmp(data[i].toDouble()))
				}
			}
			requestLayout()
		}
	}

	private fun updateValues(size: Int, durationMills: Long) {
		this.durationMills = durationMills
		this.pxPerMill = DEFAULT_PIXEL_PER_SECOND/1000.0
		this.durationPx = durationMills*pxPerMill
		this.millsPerPx = 1/pxPerMill
		this.samplePerMill = size/durationMills.toDouble()
		this.samplePerPx = samplePerMill/pxPerMill
	}

	fun reset() {
		recordingData.clear()
		totalRecordingSize = 0

		durationMills = 0
		pxPerMill = 0.0
		millsPerPx = 0.0
		samplePerPx = 0.0
	}

	private fun millsToPx(mills: Long): Double {
		return (mills * pxPerMill)
	}

	private fun pxToMill(px: Int): Double {
		return (px * millsPerPx)
	}

	private fun pxToSample(px: Int): Double {
		return (px * samplePerPx)
	}

	/**
	 * Convert dB amp value to view amp.
	 */
	private fun convertAmp(amp: Double): Int {
		return (amp * ((viewHeightPx / 2).toFloat() / 32767)).toInt()
	}

	override fun setSelected(selected: Boolean) {
		super.setSelected(selected)
		if (selected) {
			waveformPaint.color = ContextCompat.getColor(context, R.color.md_grey_500)
		} else {
			waveformPaint.color = ContextCompat.getColor(context, R.color.md_grey_700)
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		if (viewWidthPx != width) {
			viewWidthPx = width
			viewHeightPx = height
			drawLinesArray = FloatArray(viewWidthPx / 2 * 4)
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		drawGrid(canvas)
		drawRecordingWaveform(canvas)
		//Draw scrubber
		canvas.drawLine(viewWidthPx / 2f, 0f, viewWidthPx / 2f, height.toFloat(), scrubberPaint)
	}

	private fun drawGrid(canvas: Canvas) {
		val subStepPx = millsToPx(gridStepMills / 2)
		val halfWidthMills = pxToMill(viewWidthPx / 2).toLong()
		val shiftDeltaMills = (-durationMills+halfWidthMills)%gridStepMills
		val end = ceil(pxToMill(viewWidthPx)/gridStepMills).toInt()

		for (index in -1 until end+1) {
			val indexMills = (index*gridStepMills + shiftDeltaMills)
			val xPos = millsToPx(indexMills).toFloat()
			//Draw grid lines
			//Draw main grid line
			canvas.drawLine(xPos, textIndent, xPos, height - textIndent, gridPaint)
			val xSubPos = xPos + subStepPx.toFloat()
			//Draw grid top sub-line
			canvas.drawLine(xSubPos, textIndent, xSubPos, GIRD_SUBLINE_HEIGHT + textIndent, gridPaint)
			//Draw grid bottom sub-line
			canvas.drawLine(xSubPos, height - GIRD_SUBLINE_HEIGHT - textIndent, xSubPos, height - textIndent, gridPaint)

			if (showTimeline) {
				val timeMills = indexMills+durationMills-halfWidthMills
				//Draw timeline texts
				if (timeMills >= 0) {
					val text = TimeUtils.formatTimeIntervalMinSec(timeMills)
					//Bottom timeline text
					canvas.drawText(text, xPos, height - PADD, textPaint)
					//Top timeline text
					canvas.drawText(text, xPos, textHeight, textPaint)
				}
			}
		}
	}

	private fun drawRecordingWaveform(canvas: Canvas) {
		if (recordingData.isNotEmpty()) {
			clearDrawLines()
			val half = viewHeightPx / 2
			val halfWidth = viewWidthPx / 2
			val endPx = if (durationPx < halfWidth) { durationPx.toInt() } else { halfWidth }
			var step = 0
			for (index in 0 until endPx ) {
				var sampleIndex = pxToSample(index).toInt()
				if (sampleIndex >= recordingData.size) {
					sampleIndex = recordingData.size - 1
				}
				val xPos = (viewWidthPx / 2 - index).toFloat()
				if (xPos >= 0 && xPos <= viewWidthPx && step + 3 < drawLinesArray.size) {  // Draw only visible part of waveform
					drawLinesArray[step] = xPos
					drawLinesArray[step + 1] = (half + recordingData[recordingData.size - 1 - sampleIndex] + 1).toFloat()
					drawLinesArray[step + 2] = xPos
					drawLinesArray[step + 3] = (half - recordingData[recordingData.size - 1 - sampleIndex] - 1).toFloat()
					step += 4
				}
			}
			canvas.drawLines(drawLinesArray, 0, drawLinesArray.size, waveformPaint)
		}
	}

	private fun clearDrawLines() {
		for (i in drawLinesArray.indices) {
			drawLinesArray[i] = 0f
		}
	}
}
