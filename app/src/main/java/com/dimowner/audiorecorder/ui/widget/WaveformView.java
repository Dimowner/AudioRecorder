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
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

import timber.log.Timber;

public class WaveformView extends View {

	private static final int PIXEL_PER_SECOND = (int) AndroidUtils.dpToPx(AppConstants.PIXELS_PER_SECOND);
	private static final float SMALL_LINE_HEIGHT = 30.0f;
	private static final float PADD = 15.0f;

	private Paint waveformPaint;
	private Paint gridPaint;
	private TextPaint textPaint;

	private Paint scrubberPaint;
//	private Paint selectPaint;


	private int[] waveformData;
	private long playProgress;

	private int[] waveForm;

	private boolean isInitialized;
	private float textHeight;
	private float inset;

	private int[] empty = new int[0];

	private volatile int shift = 0;
	private int prevShift = 0;
	private float startX = 0;
	private boolean readPlayProgress = true;
	private int viewWidth = 0;

	/**
	 * Values used to prevent call {@link #adjustWaveformHeights} before view is measured because
	 * in that method used measured height value which calculates in {@link #onMeasure(int, int)}
	 */
	private boolean isMeasured = false;

	public OnSeekListener onSeekListener;

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

		scrubberPaint = new Paint();
		scrubberPaint.setAntiAlias(false);
		scrubberPaint.setStyle(Paint.Style.STROKE);
		scrubberPaint.setStrokeWidth(AndroidUtils.dpToPx(2));
		scrubberPaint.setColor(context.getResources().getColor(R.color.colorAccent));

//		selectPaint = new Paint();
//		selectPaint.setStyle(Paint.Style.FILL);
//		selectPaint.setColor(context.getResources().getColor(R.color.text_disabled_light));

		gridPaint = new Paint();
		gridPaint.setColor(context.getResources().getColor(R.color.md_grey_100));
		gridPaint.setStrokeWidth(AndroidUtils.dpToPx(1)/2);

		textHeight = context.getResources().getDimension(R.dimen.text_normal);
		inset = textHeight + PADD;
		textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
		textPaint.setColor(context.getResources().getColor(R.color.md_grey_100));
		textPaint.setStrokeWidth(AndroidUtils.dpToPx(1));
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		textPaint.setTextSize(textHeight);

		playProgress = -1;
		waveForm = null;
		isInitialized = false;

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent motionEvent) {
				switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
					case MotionEvent.ACTION_DOWN:
						Timber.v("DOWN");
						startX = motionEvent.getX();
						readPlayProgress = false;
						break;
					case MotionEvent.ACTION_MOVE:
						shift = (int)(motionEvent.getX() - startX + prevShift);
						if (shift > viewWidth /2) { shift = viewWidth /2;}
//						Timber.v("MOVE: shift = " + shift + " x = " + motionEvent.getX()
//								+ " start = " + startX + " prec = " + prevShift + " half Width= " + viewWidth/2);
						invalidate();

						break;
					case MotionEvent.ACTION_UP:
						Timber.v("UP");

						if (onSeekListener != null) {
							onSeekListener.onSeek(-(shift - viewWidth/2));
						}
						readPlayProgress = true;
						prevShift = shift;
						performClick();
						break;
				}
				return true;
			}
		});
	}

	public void setPlayback(long px) {
		if (readPlayProgress) {
			playProgress = px;
//			Timber.v("setPlayback px: " + playProgress + " shift = " + shift + " half viewWidth: " + viewWidth/2);
			shift = viewWidth /2 + (int) (-playProgress);
//			Timber.v("setPlayback new shift = " + shift);
			prevShift = shift;
			invalidate();
		}
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
		// set the final measured viewWidth and height.
		int width = MeasureSpec.getSize(widthMeasureSpec);

		this.viewWidth = width;
		prevShift = width/2;
		shift = prevShift;

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
		Timber.v("DRAW: shift = " + shift + " start = " + startX + " prec = " + prevShift + " length = " + waveformData.length);
		if (waveformData == null) {
			return;
		}

		drawGrid(canvas);
		drawWaveForm(canvas);

		int measuredHeight = getMeasuredHeight();
//		if (playProgress >= 0) {
//			canvas.drawRect(0, 0, playProgress, getMeasuredHeight(), selectPaint);
//			canvas.drawLine(playProgress, 0, playProgress, getMeasuredHeight(), scrubberPaint);
			canvas.drawLine(viewWidth /2, 0, viewWidth /2, measuredHeight, scrubberPaint);
//		}
		float density = AndroidUtils.dpToPx(1);

		//Draw waveform start indication
		canvas.drawLine(shift, inset, shift, measuredHeight-inset, waveformPaint);

		//Draw waveform end indication
		canvas.drawLine(shift + waveformData.length * density, inset,
				shift + waveformData.length * density, measuredHeight-inset, waveformPaint);
	}

	private void drawGrid(Canvas canvas) {
		float height = (float) getHeight();
		int lineCount;
		if (waveformData.length > 2) {
			lineCount = (int) AndroidUtils.dpToPx(waveformData.length) / PIXEL_PER_SECOND;
		} else {
			lineCount = viewWidth /PIXEL_PER_SECOND;
		}
		for (float i = -10f; i < lineCount + 10; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND + shift;
			canvas.drawLine(xPos, height-inset, xPos, inset, gridPaint);

			xPos = (i+1) * PIXEL_PER_SECOND + shift;
			canvas.drawLine(xPos, height - inset, xPos, height - SMALL_LINE_HEIGHT - inset, gridPaint);

			canvas.drawLine(xPos, inset, xPos, SMALL_LINE_HEIGHT + inset, gridPaint);
		}

		for (float i = 0f; i < lineCount + 10; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND + shift;
			if (i >= 0) {
				String text = TimeUtils.formatTimeIntervalMinSec(((long) i) * 1000);
				//Bottom text
				canvas.drawText(text, xPos, height-PADD, textPaint);
				//Top text
				canvas.drawText(text, xPos, textHeight, textPaint);
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
		path.moveTo(shift, half);
		path.lineTo(shift, half);
		float dpi = AndroidUtils.dpToPx(1);
		for (int i = 1; i < width; i++) {
			path.lineTo(shift + i * dpi, half - waveformData[i]);
		}
		for (int i = width - 1; i >= 0; i--) {
			path.lineTo(shift + i * dpi, half + 1 + waveformData[i]);
		}
		path.lineTo(shift, half);
		path.close();
		canvas.drawPath(path, waveformPaint);
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

		//Find the highest gain
		double maxGain = 1.0;
		for (int i = 0; i < numFrames; i++) {
			if (smoothedGains[i] > maxGain) {
				maxGain = smoothedGains[i];
			}
		}
		// Make sure the range is no more than 0 - 255
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

	public void setOnSeekListener(OnSeekListener onSeekListener) {
		this.onSeekListener = onSeekListener;
	}

	public interface OnSeekListener {
		void onSeek(int px);
	}
}
