/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

public class WaveformView extends View {

	private static final int PIXEL_PER_SECOND = (int) AndroidUtils.dpToPx(AppConstants.PIXELS_PER_SECOND);
	private static final float SMALL_LINE_HEIGHT = 30.0f;
	private static final float PADD = 15.0f;

	private Paint waveformPaint;
	private Paint playProgressPaint;
	private Paint gridPaint;
	private Paint textPaint;

	private int[] waveformData;
	private int playProgress;

	private int[] waveForm;

	private boolean isInitialized;
	private float textHeight;
	private float inset;

	private int[] empty = new int[0];

	/**
	 * Values used to prevent call {@link #adjustWaveformHeights} before view is measured because
	 * in that method used measured height value which calculates in {@link #onMeasure(int, int)}
	 */
	private boolean isMeasured = false;

	public WaveformView(Context context) {
		super(context);
		init(context);
	}

	public WaveformView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {

		setFocusable(false);

		waveformPaint = new Paint();
		waveformPaint.setStyle(Paint.Style.FILL);
		waveformPaint.setAntiAlias(true);
		waveformPaint.setColor(context.getResources().getColor(R.color.white));

		playProgressPaint = new Paint();
		playProgressPaint.setAntiAlias(false);
		playProgressPaint.setStrokeWidth(AndroidUtils.dpToPx(2));
		playProgressPaint.setColor(context.getResources().getColor(R.color.md_grey_600));

		gridPaint = new Paint();
		gridPaint.setColor(context.getResources().getColor(R.color.md_grey_100));
		gridPaint.setStrokeWidth(AndroidUtils.dpToPx(1));

		textHeight = context.getResources().getDimension(R.dimen.text_normal);
		inset = textHeight + PADD;
		textPaint = new Paint();
		textPaint.setColor(context.getResources().getColor(R.color.md_grey_100));
		textPaint.setStrokeWidth(AndroidUtils.dpToPx(1));
		textPaint.setTextAlign(Paint.Align.RIGHT);
		textPaint.setTextSize(textHeight);

		playProgress = -1;
		waveForm = null;
		isInitialized = false;
	}

	public void setWaveform(int[] frameGains) {
		if (frameGains != null) {
			this.waveForm = frameGains;
			if (isMeasured) {
				adjustWaveformHeights(waveForm);
			}
		} else {
			if (isMeasured) {
				adjustWaveformHeights(new int[0]);
			}
		}
		requestLayout();
	}

	@Override
	public void setSelected(boolean selected) {
		super.setSelected(selected);
		if (selected) {
			waveformPaint.setColor(getContext().getResources().getColor(R.color.md_grey_500));
		} else {
			waveformPaint.setColor(getContext().getResources().getColor(R.color.md_grey_700));
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (!isMeasured) isMeasured = true;
		// Reconcile the measured dimensions with the this view's constraints and
		// set the final measured width and height.
		int width = MeasureSpec.getSize(widthMeasureSpec);

//		int calculatedWidth = (int) AndroidUtils.dpToPx(numFrames) + 3;
//
//		if (calculatedWidth > width) {
//			width = calculatedWidth;
//		}

		setMeasuredDimension(
				resolveSize(width, widthMeasureSpec),
				heightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (isMeasured && !isInitialized) {
			if (waveForm != null) {
				adjustWaveformHeights(waveForm);
			} else {
				adjustWaveformHeights(empty);
			}
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (waveformData == null) {
			return;
		}

		drawGrid(canvas);
		drawWaveForm(canvas);

		int measuredHeight = getMeasuredHeight();
		if (playProgress >= 0) {
			//Draw play progress line
			canvas.drawLine(playProgress, inset, playProgress, measuredHeight-inset, playProgressPaint);
		}
		float density = AndroidUtils.dpToPx(1);

		//Draw waveform start indication
		canvas.drawLine(0, inset, 0, measuredHeight-inset, waveformPaint);

		//Draw waveform end indication
		canvas.drawLine(waveformData.length * density, inset,
				waveformData.length * density, measuredHeight-inset, waveformPaint);
	}

	private void drawGrid(Canvas canvas) {
		float height = (float) getHeight();
		int lineCount = getWidth()/PIXEL_PER_SECOND;
		for (float i = 0.0f; i < lineCount + 2; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND;
			canvas.drawLine(xPos, height-inset, xPos, inset, gridPaint);

			xPos = (i+1) * PIXEL_PER_SECOND;
			canvas.drawLine(xPos, height - inset, xPos, height - SMALL_LINE_HEIGHT - inset, gridPaint);

			canvas.drawLine(xPos, inset, xPos, SMALL_LINE_HEIGHT + inset, gridPaint);
		}

		//Draw text time lines
		textPaint.setTextAlign(Paint.Align.LEFT);
		//Bottom text
		canvas.drawText(":00", 0, height-PADD, textPaint);
		//Top text
		canvas.drawText(":00", 0, textHeight, textPaint);
		textPaint.setTextAlign(Paint.Align.CENTER);

		for (float i = 1f; i < lineCount + 2; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND;
			if (i > 1) {
				String text = TimeUtils.formatTimeIntervalMinSec(((long) i - 1) * 1000);
				//Bottom text
				canvas.drawText(text, xPos - PIXEL_PER_SECOND, height-PADD, textPaint);
				//Top text
				canvas.drawText(text, xPos - PIXEL_PER_SECOND, textHeight, textPaint);
			}

		}
	}

	private void drawWaveForm(Canvas canvas) {
		int width = waveformData.length;
		int half = getMeasuredHeight() / 2;

		if (width > getMeasuredWidth()) {
			width = getMeasuredWidth();
		}

		Path path = new Path();
		path.moveTo(0, half);
		path.lineTo(0, half);
		float dpi = AndroidUtils.dpToPx(1);
		for (int i = 1; i < width; i++) {
			path.lineTo(i * dpi, half - waveformData[i]);
		}
		for (int i = width - 1; i >= 0; i--) {
			path.lineTo(i * dpi, half + 1 + waveformData[i]);
		}
		path.lineTo(0, half);
		path.close();
		canvas.drawPath(path, waveformPaint);
	}

	/**
	 * Called once when a new sound file is added
	 */
	private void adjustWaveformHeights(int[] frameGains) {
		int numFrames = frameGains.length;
		//One frame corresponds one pixel on screen
		double[] smoothedGains = new double[numFrames];
		if (numFrames == 1) {
			smoothedGains[0] = frameGains[0];
		} else if (numFrames == 2) {
			smoothedGains[0] = frameGains[0];
			smoothedGains[1] = frameGains[1];
		} else if (numFrames > 2) {
			smoothedGains[0] = (
					(frameGains[0] / 2.0) +
							(frameGains[1] / 2.0));
			for (int i = 1; i < numFrames - 1; i++) {
				smoothedGains[i] = (
						(frameGains[i - 1] / 3.0) +
								(frameGains[i] / 3.0) +
								(frameGains[i + 1] / 3.0));
			}
			smoothedGains[numFrames - 1] = (
					(frameGains[numFrames - 2] / 2.0) +
							(frameGains[numFrames - 1] / 2.0));
		}

		// Make sure the range is no more than 0 - 255
		double maxGain = 1.0;
		for (int i = 0; i < numFrames; i++) {
			if (smoothedGains[i] > maxGain) {
				maxGain = smoothedGains[i];
			}
		}
		double scaleFactor = 1.0;
		if (maxGain > 255.0) {
			scaleFactor = 255 / maxGain;
		}

		// Build histogram of 256 bins and figure out the new scaled max
		maxGain = 0;
		int gainHist[] = new int[256];
		for (int i = 0; i < numFrames; i++) {
			int smoothedGain = (int) (smoothedGains[i] * scaleFactor);
			if (smoothedGain < 0)
				smoothedGain = 0;
			if (smoothedGain > 255)
				smoothedGain = 255;

			if (smoothedGain > maxGain)
				maxGain = smoothedGain;

			gainHist[smoothedGain]++;
		}

		// Re-calibrate the min to be 5%
		double minGain = 0;
		int sum = 0;
		while (minGain < 255 && sum < numFrames / 20) {
			sum += gainHist[(int) minGain];
			minGain++;
		}

		// Re-calibrate the max to be 99%
		sum = 0;
		while (maxGain > 2 && sum < numFrames / 100) {
			sum += gainHist[(int) maxGain];
			maxGain--;
		}

		// Compute the heights
		double[] heights = new double[numFrames];
		double range = maxGain - minGain;
		if (range <= 0) {
			range = 1;
		}
		for (int i = 0; i < numFrames; i++) {
			double value = (smoothedGains[i] * scaleFactor - minGain) / range;
			if (value < 0.0)
				value = 0.0;
			if (value > 1.0)
				value = 1.0;
			heights[i] = value * value;
		}

		int halfHeight = (getMeasuredHeight() / 2) - (int)inset - 1;

		waveformData = new int[numFrames];
		for (int i = 0; i < numFrames; i++) {
			waveformData[i] = (int) (heights[i] * (halfHeight));
		}

		isInitialized = true;
	}
}
