/*
 * Copyright 2020 Dmytro Ponomarenko
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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils

private const val DEFAULT_GRID_STEP = 2000L //Milliseconds
private const val SHORT_RECORD = 18000 //Milliseconds
private const val DEFAULT_WIDTH_SCALE = 1.5 //Const val describes how many screens a record will take.
private const val ANIMATION_DURATION = 330 //mills.

class WaveformViewNew @JvmOverloads constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val GIRD_SUBLINE_HEIGHT = AndroidUtils.dpToPx(12)
	private val PADD = AndroidUtils.dpToPx(6)

	private val waveformPaint = Paint()
	private val linePaint = Paint()
	private val gridPaint = Paint()
	private val scrubberPaint = Paint()
	private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

	private var playProgressPx = 0
	private var playProgressMills = 0L

	private var textHeight = 0f
	private var textIndent = 0f
	private var prevScreenShiftPx = 0
	private var readPlayProgress = true
	private var screenShiftPx = 0
	private var waveformShiftPx = 0
	private var viewWidthPx = 0
	private var viewHeightPx = 0

	private var originalData: IntArray = IntArray(0)
	private var waveformData: IntArray = IntArray(0)
	lateinit var drawLinesArray: FloatArray

	private var showTimeline: Boolean = true

	/** 1 means that waveform will take whole view width. 2 mean that waveform will take double view width to draw.  */
	private var widthScale: Double = 1.0

	private var durationMills: Long = 0
	private var durationPx: Float = 0F
	private var durationSample: Int = 0
	private var millsPerPx: Float = 0F
	private var pxPerMill: Float = 0F
	private var pxPerSample: Float = 0F
	private var samplePerPx: Float = 0F
	private var samplePerMill: Float = 0F
	private var gridStepMills: Long = 4000

	private var onSeekListener: OnSeekListener? = null

	init {
		isFocusable = false

		waveformPaint.style = Paint.Style.STROKE
		waveformPaint.strokeWidth = 1.3f
		waveformPaint.isAntiAlias = true
		waveformPaint.color = ContextCompat.getColor(context, R.color.dark_white)

		linePaint.style = Paint.Style.STROKE
		linePaint.strokeWidth = AndroidUtils.dpToPx(1.5f)
		linePaint.isAntiAlias = true
		linePaint.color = ContextCompat.getColor(context, R.color.dark_white)

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

		playProgressPx = -1
		setOnTouchListener(object : OnTouchListener {
			var startX = 0F
			override fun onTouch(v: View?, event: MotionEvent): Boolean {
				when (event.action and MotionEvent.ACTION_MASK) {
					MotionEvent.ACTION_DOWN -> {
						readPlayProgress = false
						startX = event.x
						onSeekListener?.onStartSeek()
					}
					MotionEvent.ACTION_MOVE -> {
						var shift = (prevScreenShiftPx + event.x - startX).toInt()
						//Right waveform move edge
						if (shift <= -durationPx) {
							shift = -durationPx.toInt()
						}
						//Left waveform move edge
						if (shift > 0) {
							shift = 0
						}
						onSeekListener?.onSeeking(-screenShiftPx, pxToMill(-screenShiftPx))
						playProgressPx = -shift
						updateShifts(shift)
						invalidate()
					}
					MotionEvent.ACTION_UP -> {
						onSeekListener?.onSeek(-screenShiftPx, pxToMill(-screenShiftPx))
						prevScreenShiftPx = screenShiftPx
						readPlayProgress = true
						performClick()
					}
				}
				return true
			}
		})
	}

	fun showTimeline(show: Boolean) {
		showTimeline = show
		textIndent = if (show) { textHeight + PADD } else { 0f }
		invalidate()
	}

	fun setPlayback(mills: Long) {
		if (readPlayProgress) {
			playProgressPx = millsToPx(mills).toInt()
			updateShifts(-playProgressPx)
			prevScreenShiftPx = screenShiftPx
			invalidate()
		}
	}

	fun moveToStart() {
		val moveAnimator = ValueAnimator.ofInt(playProgressPx, 0)
		moveAnimator.interpolator = DecelerateInterpolator()
		moveAnimator.duration = ANIMATION_DURATION.toLong()
		moveAnimator.addUpdateListener { animation: ValueAnimator ->
			val moveValPx = animation.animatedValue as Int
			setPlayback(pxToMill(moveValPx))
		}
		moveAnimator.start()
	}

	/**
	 * Set new current play position in pixels.
	 * @param px value.
	 */
	fun seekPx(px: Int) {
		playProgressPx = px
		updateShifts(-playProgressPx)
		prevScreenShiftPx = screenShiftPx
		invalidate()
		onSeekListener?.onSeeking(-screenShiftPx, pxToMill(-screenShiftPx))
	}

	fun setWaveform(frameGains: IntArray, durationMills: Long, playbackMills: Long) {
		post {
			originalData = frameGains
			viewWidthPx = width
			viewHeightPx = height
			playProgressMills = playbackMills
			updateWaveform(frameGains, durationMills, playbackMills)
			requestLayout()
		}
	}

	private fun updateWaveform(frameGains: IntArray, durationMills: Long, playbackMills: Long) {
		drawLinesArray = FloatArray(viewWidthPx * 4)
		updateValues(frameGains.size, durationMills)
		if (viewHeightPx > 0 && viewWidthPx > 0) {
			adjustWaveformHeights(frameGains)
			setPlayback(playbackMills)
		}
	}

	private fun updateValues(size: Int, durationMills: Long) {
		this.widthScale = calculateScale(durationMills)
		this.durationMills = durationMills
		this.durationSample = size
		this.durationPx = when {
			viewWidthPx == 0 -> {
				return
			}
			else -> {
				(viewWidthPx * widthScale).toFloat()
			}
		}

		this.millsPerPx = durationMills.toFloat()/durationPx
		this.pxPerMill = durationPx/durationMills.toFloat()
		this.pxPerSample = durationPx/durationSample.toFloat()
		this.samplePerPx = durationSample.toFloat()/durationPx
		this.samplePerMill = durationSample.toFloat()/durationMills.toFloat()
		this.durationPx = millsToPx(durationMills)

		this.gridStepMills = calculateGridStep(durationMills)
	}

	private fun calculateScale(mills: Long): Double {
		return when {
			mills >= SHORT_RECORD -> {
				DEFAULT_WIDTH_SCALE
			}
			else -> {
				mills * (DEFAULT_WIDTH_SCALE/SHORT_RECORD)
			}
		}
	}

	private fun millsToPx(mills: Long): Float {
		return (mills * pxPerMill)
	}

	fun pxToMill(px: Int): Long {
		return (px * millsPerPx).toLong()
	}

	private fun pxToSample(px: Int): Int {
		return (px * samplePerPx).toInt()
	}

	private fun sampleToPx(sample: Int): Float {
		return sample * pxPerSample
	}

	fun getWaveformLength(): Int {
		return waveformData.size
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
		viewWidthPx = width
		viewHeightPx = height
		updateWaveform(originalData, durationMills, playProgressMills)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		drawGrid(canvas)
		drawWaveForm(canvas)
		//Draw waveform start indication
		canvas.drawLine(waveformShiftPx.toFloat(), textIndent, waveformShiftPx.toFloat(), height - textIndent, linePaint)
		//Draw waveform end indication
		canvas.drawLine(waveformShiftPx + sampleToPx(waveformData.size), textIndent,
				waveformShiftPx + sampleToPx(waveformData.size), height - textIndent, linePaint)
		//Draw scrubber
		canvas.drawLine(viewWidthPx / 2f, 0f, viewWidthPx / 2f, height.toFloat(), scrubberPaint)
	}

	private fun updateShifts(px: Int) {
		screenShiftPx = px
		waveformShiftPx = screenShiftPx + viewWidthPx / 2
	}

	private fun calculateGridStep(durationMills: Long): Long {
		var actualStepSec = (durationMills / 1000) / AppConstants.GRID_LINES_COUNT
		var k = 1
		while (actualStepSec > 239) {
			actualStepSec /= 2
			k *= 2
		}
		//Ranges can be better optimised
		val gridStep: Long = when (actualStepSec) {
			in 0..2 -> 2000
			in 3..6 -> 5000
			in 7..14 -> 10000
			in 15..24 -> 20000
			in 25..44 -> 30000
			in 45..74 -> 60000
			in 75..104 -> 90000
			in 105..149 -> 120000
			in 150..209 -> 180000
			in 210..269 -> 240000
			in 270..329 -> 300000
			in 330..419 -> 360000
			in 420..539 -> 480000
			in 540..659 -> 600000
			in 660..809 -> 720000
			in 810..1049 -> 900000
			in 1050..1349 -> 1200000
			in 1350..1649 -> 1500000
			in 1650..2099 -> 1800000
			in 2100..2699 -> 2400000
			in 2700..3299 -> 3000000
			in 3300..3899 -> 3600000
			else -> 4200000
		}
		return gridStep * k
	}

	private fun drawGrid(canvas: Canvas) {
		val subStepPx = millsToPx(gridStepMills / 2)
		val halfWidthMills = pxToMill(viewWidthPx / 2)
		val gridEndMills = durationMills + halfWidthMills.toInt() + gridStepMills
		val halfScreenStepCount = (halfWidthMills/gridStepMills).toInt()

		for (indexMills in -halfScreenStepCount*gridStepMills until gridEndMills step gridStepMills) {
			val sampleIndexPx = millsToPx(indexMills)
			val xPos = (waveformShiftPx + sampleIndexPx)
			if (xPos >= -gridStepMills && xPos <= viewWidthPx + gridStepMills) { // Draw only visible grid items +1
				//Draw grid lines
				//Draw main grid line
				canvas.drawLine(xPos, textIndent, xPos, height - textIndent, gridPaint)
				val xSubPos = xPos + subStepPx
				//Draw grid top sub-line
				canvas.drawLine(xSubPos, textIndent, xSubPos, GIRD_SUBLINE_HEIGHT + textIndent, gridPaint)
				//Draw grid bottom sub-line
				canvas.drawLine(xSubPos, height - GIRD_SUBLINE_HEIGHT - textIndent, xSubPos, height - textIndent, gridPaint)

				if (showTimeline) {
					//Draw timeline texts
					if (indexMills >= 0) {
						val text = TimeUtils.formatTimeIntervalHourMin(indexMills)
						//Bottom timeline text
						canvas.drawText(text, xPos, height - PADD, textPaint)
						//Top timeline text
						canvas.drawText(text, xPos, textHeight, textPaint)
					}
				}
			}
		}
	}

	private fun drawWaveForm(canvas: Canvas) {
		if (waveformData.isNotEmpty()) {
			clearDrawLines()
			val half = (height / 2).toFloat()
			var step = 0
			for (index in 0 until durationPx.toInt()) {
				var sampleIndex = pxToSample(index)
				if (sampleIndex >= waveformData.size) {
					sampleIndex = waveformData.size - 1
				}
				val xPos = (waveformShiftPx + index).toFloat()
				if (xPos >= 0 && xPos <= viewWidthPx && step + 3 < drawLinesArray.size) {  // Draw only visible part of waveform
					drawLinesArray[step] = xPos
					drawLinesArray[step + 1] = (half + waveformData[sampleIndex] + 1)
					drawLinesArray[step + 2] = xPos
					drawLinesArray[step + 3] = (half - waveformData[sampleIndex] - 1)
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

	/**
	 * Called once when a new sound file is added
	 */
	private fun adjustWaveformHeights(frameGains: IntArray) {
		val numFrames = frameGains.size

		//Find the highest gain
		var maxGain = 1.0f
		for (i in 0 until numFrames) {
			if (frameGains[i] > maxGain) {
				maxGain = frameGains[i].toFloat()
			}
		}
		// Make sure the range is no more than 0 - 255
		var scaleFactor = 1.0f
		if (maxGain > 255.0) {
			scaleFactor = 255 / maxGain
		}

		// Build histogram of 256 bins and figure out the new scaled max
		maxGain = 0.0f
		val gainHist = IntArray(256)
		for (i in 0 until numFrames) {
			var smoothedGain = (frameGains[i] * scaleFactor).toInt()
			if (smoothedGain < 0) smoothedGain = 0
			if (smoothedGain > 255) smoothedGain = 255
			if (smoothedGain > maxGain) maxGain = smoothedGain.toFloat()
			gainHist[smoothedGain]++
		}

		// Re-calibrate the min to be 5%
		var minGain = 0.0f
		var sum = 0
		while (minGain < 255 && sum < numFrames / 20) {
			sum += gainHist[minGain.toInt()]
			minGain++
		}

		// Re-calibrate the max to be 99%
		sum = 0
		while (maxGain > 2 && sum < numFrames / 100) {
			sum += gainHist[maxGain.toInt()]
			maxGain--
		}

		// Compute the heights
		val heights = FloatArray(numFrames)
		var range = maxGain - minGain
		if (range <= 0) {
			range = 1.0f
		}
		for (i in 0 until numFrames) {
			var value = (frameGains[i] * scaleFactor - minGain) / range
			if (value < 0.0) value = 0.0f
			if (value > 1.0) value = 1.0f
			heights[i] = value * value
		}
		val halfHeight = viewHeightPx / 2 - textIndent.toInt() - 1
		waveformData = IntArray(numFrames)
		for (i in 0 until numFrames) {
			waveformData[i] = (heights[i] * halfHeight).toInt()
		}
	}

	fun setOnSeekListener(onSeekListener: OnSeekListener?) {
		this.onSeekListener = onSeekListener
	}

	interface OnSeekListener {
		fun onStartSeek()
		fun onSeek(px: Int, mills: Long)
		fun onSeeking(px: Int, mills: Long)
	}
}
