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

package com.dimowner.audiorecorder.app.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;

public class SimpleWaveformView extends View {

	private static int waveformColorRes;

	private Paint waveformPaint;

	private int[] waveformData;

	private int[] waveForm;

	private boolean isInitialized;

	private int[] empty = new int[0];

	/**
	 * Values used to prevent call {@link #adjustWaveformHeights} before view is measured because
	 * in that method used measured height value which calculates in {@link #onMeasure(int, int)}
	 */
	private boolean isMeasured = false;

	public SimpleWaveformView(Context context) {
		super(context);
		init(context);
	}

	public SimpleWaveformView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public SimpleWaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}


	private void init(Context context) {

		setFocusable(false);

		waveformPaint = new Paint();
		waveformPaint.setStyle(Paint.Style.STROKE);
		waveformPaint.setStrokeWidth(AndroidUtils.dpToPx(1));
		waveformPaint.setStrokeJoin(Paint.Join.ROUND);
		waveformPaint.setAntiAlias(true);
		waveformPaint.setColor(context.getResources().getColor(waveformColorRes));

		waveForm = null;
		isInitialized = false;
	}

	public static void setWaveformColorRes(int waveformColorRes) {
		SimpleWaveformView.waveformColorRes = waveformColorRes;
	}

	public void setWaveform(int[] frameGains) {
		if (frameGains != null) {
			this.waveForm = frameGains;
			this.waveformData = frameGains;
			if (isMeasured) {
				adjustWaveformHeights(waveForm);
			}
		} else {
			if (isMeasured) {
				adjustWaveformHeights(empty);
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
		drawWaveForm(canvas);
	}

	private void drawWaveForm(Canvas canvas) {
		int width = waveformData.length;
		int half = getMeasuredHeight() / 2;

		if (width > getMeasuredWidth()) {
			width = getMeasuredWidth();
		}

//		Path path = new Path();
//		path.moveTo(0, half);
//		path.lineTo(0, half);
//		float dpi = AndroidUtils.dpToPx(1);
//		for (int i = 1; i < width; i++) {
//			path.lineTo(i * dpi, half - waveformData[i]);
//		}
//		for (int i = width - 1; i >= 0; i--) {
//			path.lineTo(i * dpi, half + 1 + waveformData[i]);
//		}
//		path.lineTo(0, half);
//		path.close();
//		canvas.drawPath(path, waveformPaint);

		float dpi = AndroidUtils.dpToPx(1);
		float[] lines = new float[width*4+4];
		int step = 0;
		for (int i = 0; i < width; i++) {
			lines[step] = i*dpi;
			lines[step+1] = half + waveformData[i];
			lines[step+2] = i*dpi;
			lines[step+3] = half - waveformData[i];
			step +=4;
		}
		//Horizontal zero line
		lines[step] = 0;
		lines[step+1] = half;
		lines[step+2] = width*dpi;
		lines[step+3] = half;
		canvas.drawLines(lines, 0, lines.length, waveformPaint);
	}

	/**
	 * Called once when a new sound file is added
	 */
	private void adjustWaveformHeights(int[] frameGains) {
		int numFrames = frameGains.length;
		//One frame corresponds one pixel on screen
		int[] smoothedGains = frameGains;
//		double[] smoothedGains = new double[numFrames];
//		if (numFrames == 1) {
//			smoothedGains[0] = frameGains[0];
//		} else if (numFrames == 2) {
//			smoothedGains[0] = frameGains[0];
//			smoothedGains[1] = frameGains[1];
//		} else if (numFrames > 2) {
//			smoothedGains[0] = (
//					(frameGains[0] / 2.0) +
//							(frameGains[1] / 2.0));
//			for (int i = 1; i < numFrames - 1; i++) {
//				smoothedGains[i] = (
//						(frameGains[i - 1] / 3.0) +
//								(frameGains[i] / 3.0) +
//								(frameGains[i + 1] / 3.0));
//			}
//			smoothedGains[numFrames - 1] = (
//					(frameGains[numFrames - 2] / 2.0) +
//							(frameGains[numFrames - 1] / 2.0));
//		}

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

		int halfHeight = (getMeasuredHeight() / 2);// - (int)inset - 1;

		waveformData = new int[numFrames];
		for (int i = 0; i < numFrames; i++) {
			waveformData[i] = (int) (heights[i] * (halfHeight));
		}

		isInitialized = true;
	}
}
