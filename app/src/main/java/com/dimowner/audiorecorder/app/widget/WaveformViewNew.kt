/*
 * Copyright 2020 Dmitriy Ponomarenko
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
import com.dimowner.audiorecorder.IntArrayList
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import java.util.*

private const val DEFAULT_GRID_STEP = 2000L //Milliseconds
private const val SHORT_RECORD = 18000 //Milliseconds
private const val DEFAULT_WIDTH_SCALE = 1.5 //Const val describes how many screens a record will take.
private const val ANIMATION_DURATION = 330 //mills.

enum class WaveformMode {
	PLAYBACK,
	RECORDING
}

class WaveformViewNew @JvmOverloads constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val GIRD_SUBLINE_HEIGHT = AndroidUtils.dpToPx(12)
	private val PADD = AndroidUtils.dpToPx(6)

	private val waveformPaint = Paint()
	private val gridPaint = Paint()
	private val scrubberPaint = Paint()
	private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

	private var mode = WaveformMode.PLAYBACK
	private var playProgressPx = 0
	private var playProgressMills = 0L

	private var textHeight = 0f
	private var inset = 0f
	private var prevScreenShiftPx = 0
	private var readPlayProgress = true
	private var screenShiftPx = 0
	private var waveformShiftPx = 0
	private var viewWidthPx = 0
	private var viewHeightPx = 0

	private var originalData: IntArray = IntArray(0)
	private var waveformData: IntArray = IntArray(0)
	private val recordingData: MutableList<Int> = LinkedList<Int>()
	private var totalRecordingSize: Int = 0

	private var showTimeline: Boolean = true

	/** 1 means that waveform will take whole view width. 2 mean that waveform will take double view width to draw.  */
	private var widthScale: Double = 1.0

	private var durationMills: Long = 0
	private var durationPx: Float = 0F
	private var durationSample: Int = 0
	private var millsPerPx: Float = 0F
	private var millsPerSample: Float = 0F
	private var pxPerMill: Float = 0F
	private var pxPerSample: Float = 0F
	private var samplePerPx: Float = 0F
	private var samplePerMill: Float = 0F
	private var gridStepMills: Long = 4000

	private var onSeekListener: OnSeekListener? = null

	init {
		isFocusable = false

		waveformPaint.style = Paint.Style.STROKE
		waveformPaint.strokeWidth = AndroidUtils.dpToPx(1.2f)
		waveformPaint.isAntiAlias = true
		waveformPaint.color = ContextCompat.getColor(context, R.color.dark_white)

		scrubberPaint.isAntiAlias = false
		scrubberPaint.style = Paint.Style.STROKE
		scrubberPaint.strokeWidth = AndroidUtils.dpToPx(2)
		scrubberPaint.color = ContextCompat.getColor(context, R.color.md_yellow_A700)

		gridPaint.color = ContextCompat.getColor(context, R.color.md_grey_100_75)
		gridPaint.strokeWidth = AndroidUtils.dpToPx(1) / 2

		textHeight = context.resources.getDimension(R.dimen.text_normal)
		inset = textHeight + PADD
		textPaint.color = ContextCompat.getColor(context, R.color.md_grey_100)
		textPaint.strokeWidth = AndroidUtils.dpToPx(1)
		textPaint.textAlign = Paint.Align.CENTER
		textPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
		textPaint.textSize = textHeight

		playProgressPx = -1
		setOnTouchListener(object : OnTouchListener {
			var startX = 0F
			override fun onTouch(v: View?, event: MotionEvent): Boolean {
				if (mode != WaveformMode.RECORDING) {
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
				}
				return true
			}
		})
	}

	fun showTimeline(show: Boolean) {
		showTimeline = show
		inset = if (show) { textHeight + PADD } else { 0f }
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
		if (mode == WaveformMode.PLAYBACK) {
			val moveAnimator = ValueAnimator.ofInt(playProgressPx, 0)
			moveAnimator.interpolator = DecelerateInterpolator()
			moveAnimator.duration = ANIMATION_DURATION.toLong()
			moveAnimator.addUpdateListener { animation: ValueAnimator ->
				val moveValPx = animation.animatedValue as Int
				setPlayback(pxToMill(moveValPx))
			}
			moveAnimator.start()
		}
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
		updateValues(frameGains.size, durationMills)
		if (viewHeightPx > 0 && viewWidthPx > 0) {
			adjustWaveformHeights(frameGains)
			setPlayback(playbackMills)
		}
	}

	fun addRecordAmp(amp: Int, durationMills: Long) {
		recordingData.add(convertAmp(amp.toDouble()))
		totalRecordingSize++
		updateValues(totalRecordingSize, durationMills)
		if (recordingData.size > pxToSample(viewWidthPx / 2)) {
			recordingData.removeAt(0)
		}
		playProgressPx = millsToPx(durationMills).toInt()
		updateShifts(-playProgressPx)
		prevScreenShiftPx = screenShiftPx
		invalidate()
	}

	fun setRecordingData(data: IntArrayList, durationMills: Long) {
		mode = WaveformMode.RECORDING
		post {
			recordingData.clear()
			val count = pxToSample(viewWidthPx / 2)
			if (data.size() > count) {
				for (i in data.size() - count until data.size()) {
					recordingData.add(convertAmp(data[i].toDouble()))
				}
			} else {
				for (i in 0 until data.size()) {
					recordingData.add(convertAmp(data[i].toDouble()))
				}
			}
			totalRecordingSize = data.size()
			updateValues(totalRecordingSize, durationMills)
			playProgressPx = millsToPx(durationMills).toInt()
			updateShifts(-playProgressPx)
			prevScreenShiftPx = screenShiftPx
			requestLayout()
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
			mode == WaveformMode.RECORDING -> {
				(viewWidthPx / 2 * widthScale).toFloat()
			}
			else -> {
				(viewWidthPx * widthScale).toFloat()
			}
		}

		this.millsPerPx = durationMills.toFloat()/durationPx
		this.millsPerSample = durationMills.toFloat()/durationSample.toFloat()
		this.pxPerMill = durationPx/durationMills.toFloat()
		this.pxPerSample = durationPx/durationSample.toFloat()
		this.samplePerPx = durationSample.toFloat()/durationPx
		this.samplePerMill = durationSample.toFloat()/durationMills.toFloat()
		this.durationPx = millsToPx(durationMills)

		this.gridStepMills = calculateGridStep(durationMills)
	}

	private fun calculateScale(mills: Long): Double {
		return when {
			mode == WaveformMode.RECORDING -> {
				mills * (DEFAULT_WIDTH_SCALE/(SHORT_RECORD/2))
			}
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

	private fun millsToSample(mills: Long): Int {
		return (mills * samplePerMill).toInt()
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

	private fun sampleToMill(sample: Int): Int {
		return (sample * millsPerSample).toInt()
	}

	fun setModeRecording() {
		mode = WaveformMode.RECORDING
		waveformData = IntArray(0)
		recordingData.clear()
		totalRecordingSize = 0
		updateShifts(0)
		invalidate()
	}

	fun setModePlayback() {
		mode = WaveformMode.PLAYBACK
		updateShifts(0)
		invalidate()
	}

	fun getWaveformLength(): Int {
		return waveformData.size
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
		viewWidthPx = width
		viewHeightPx = height
		updateWaveform(originalData, durationMills, playProgressMills)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		drawGrid(canvas)
		when (mode) {
			WaveformMode.PLAYBACK -> {
				drawWaveForm(canvas)
				//Draw waveform start indication
				canvas.drawLine(waveformShiftPx.toFloat(), inset, waveformShiftPx.toFloat(), height - inset, waveformPaint)
				//Draw waveform end indication
				canvas.drawLine(waveformShiftPx + sampleToPx(waveformData.size), inset,
						waveformShiftPx + sampleToPx(waveformData.size), height - inset, waveformPaint)
			}
			WaveformMode.RECORDING -> drawRecordingWaveform(canvas)
//			WaveformMode.RECORDING -> drawWaveForm(canvas)
		}
		//Draw scrubber
		canvas.drawLine(viewWidthPx / 2f, 0f, viewWidthPx / 2f, height.toFloat(), scrubberPaint)
	}

	private fun updateShifts(px: Int) {
		screenShiftPx = px
		waveformShiftPx = screenShiftPx + viewWidthPx / 2
	}

	private fun calculateGridStep(durationMills: Long): Long {
		if (mode == WaveformMode.RECORDING) {
			return DEFAULT_GRID_STEP
		} else {
			var actualStepSec = (durationMills / 1000) / AppConstants.GRID_LINES_COUNT
			var k = 1
			while (actualStepSec > 239) {
				actualStepSec /= 2
				k *= 2
			}
			//Ranges can be better optimised
			val gridStep: Long = when (actualStepSec) {
				in 0..2 -> 2000
				in 3..7 -> 5000
				in 8..14 -> 10000
				in 15..24 -> 20000
				in 25..49 -> 30000
				in 50..89 -> 60000
				in 90..119 -> 90000
				in 120..179 -> 120000
				in 180..239 -> 180000
				else -> DEFAULT_GRID_STEP
			}
			return gridStep * k
		}
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
				canvas.drawLine(xPos, inset, xPos, height - inset, gridPaint)
				val xSubPos = xPos + subStepPx
				//Draw grid top sub-line
				canvas.drawLine(xSubPos, inset, xSubPos, GIRD_SUBLINE_HEIGHT + inset, gridPaint)
				//Draw grid bottom sub-line
				canvas.drawLine(xSubPos, height - GIRD_SUBLINE_HEIGHT - inset, xSubPos, height - inset, gridPaint)

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
			val half = (height / 2).toFloat()
			val lines = FloatArray(durationPx.toInt() * 4)
			var step = 0
			for (index in 0 until durationPx.toInt()) {
				var sampleIndex = pxToSample(index)
				if (sampleIndex >= waveformData.size) {
					sampleIndex = waveformData.size - 1
				}
				val xPos = (waveformShiftPx + index).toFloat()
				if (xPos >= 0 && xPos <= viewWidthPx) {  // Draw only visible part of waveform
					lines[step] = xPos
					lines[step + 1] = (half + waveformData[sampleIndex] + 1)
					lines[step + 2] = xPos
					lines[step + 3] = (half - waveformData[sampleIndex] - 1)
				}
				step += 4
			}
			canvas.drawLines(lines, 0, lines.size, waveformPaint)
		}
	}

	private fun drawRecordingWaveform(canvas: Canvas) {
		if (recordingData.isNotEmpty()) {
			val half = viewHeightPx / 2
			val lines = FloatArray(durationPx.toInt() * 4)
			var step = 0
			for (index in 0 until durationPx.toInt()) {
				var sampleIndex = pxToSample(index)
				if (sampleIndex >= recordingData.size) {
					sampleIndex = recordingData.size - 1
				}
				val xPos = (viewWidthPx / 2 - index).toFloat()
				if (xPos >= 0 && xPos <= viewWidthPx) {  // Draw only visible part of waveform
					lines[step] = xPos
					lines[step + 1] = (half + recordingData[recordingData.size - 1 - sampleIndex] + 1).toFloat()
					lines[step + 2] = xPos
					lines[step + 3] = (half - recordingData[recordingData.size - 1 - sampleIndex] - 1).toFloat()
					step += 4
				}
			}
			canvas.drawLines(lines, 0, lines.size, waveformPaint)
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
		val halfHeight = viewHeightPx / 2 - inset.toInt() - 1
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
