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
import android.os.Bundle;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class WaveformView extends View {

	private static final int PIXEL_PER_SECOND = (int) AndroidUtils.dpToPx(AppConstants.PIXELS_PER_SECOND);
	private static final float SMALL_LINE_HEIGHT = 30.0f;
	private static final float PADD = 15.0f;
	private static final int VIEW_DRAW_EDGE = 0;

	private Paint waveformPaint;
	private Paint gridPaint;
	private TextPaint textPaint;

	private Paint scrubberPaint;

	private int[] waveformData;
	private long playProgress;

	private int[] waveForm;

	private List<Integer> recordingData;
	private boolean showRecording = false;

	private boolean isInitialized;
	private float textHeight;
	private float inset;

	private int[] empty = new int[0];

	private int prevScreenShift = 0;
	private float startX = 0;
	private boolean readPlayProgress = true;
	private int screenShift = 0;
	private int waveformShift = 0;
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

		recordingData = new ArrayList<>();

		waveformPaint = new Paint();
		waveformPaint.setStyle(Paint.Style.FILL);
		waveformPaint.setAntiAlias(true);
		waveformPaint.setColor(context.getResources().getColor(R.color.dark_white));

		scrubberPaint = new Paint();
		scrubberPaint.setAntiAlias(false);
		scrubberPaint.setStyle(Paint.Style.STROKE);
		scrubberPaint.setStrokeWidth(AndroidUtils.dpToPx(2));
		scrubberPaint.setColor(context.getResources().getColor(R.color.md_yellow_A700));

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
						readPlayProgress = false;
						startX = motionEvent.getX();
						break;
					case MotionEvent.ACTION_MOVE:
//						Timber.v("MOVE: screenShift = " + screenShift + " waveShift = " + waveformShift + " x = " + motionEvent.getX()
//								+ " start = " + startX + " length px = " + AndroidUtils.dpToPx(waveformData.length));
						int shift = (int)(prevScreenShift + (motionEvent.getX()) - startX);
						//Right waveform move edge
						if (shift <= -AndroidUtils.dpToPx(waveformData.length)) {
							shift = (int)-AndroidUtils.dpToPx(waveformData.length);
						}
						//Left waveform move edge
						if (shift > 0) {
							shift = 0;
						}
						if (onSeekListener != null) {
							onSeekListener.onSeeking(-screenShift);
						}
						updateShifts(shift);
						invalidate();
						break;
					case MotionEvent.ACTION_UP:
						Timber.v("UP");
						if (onSeekListener != null) {
							onSeekListener.onSeek(-screenShift);
						}
						prevScreenShift = screenShift;
						readPlayProgress = true;
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
//			Timber.v("setPlayback px: " + playProgress + " shift = " + waveformShift + " screenShift: " + screenShift);
			updateShifts((int)-playProgress);
			prevScreenShift = screenShift;
//			Timber.v("setPlayback new shift = " + screenShift + " waveShift = " + waveformShift);
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

	public void showRecording() {
		updateShifts(0);
		showRecording = true;
	}

	public void hideRecording() {
		showRecording = false;
		updateShifts(0);
	}

	public int getWaveformLength() {
		return waveformData.length;
	}

	public void addRecordAmp(int amp) {
		if (amp < 0) {
			amp = 0;
		}
		updateShifts((int) -AndroidUtils.dpToPx(recordingData.size()));
		recordingData.add(convertAmp(amp));
		invalidate();
	}

	/**
	 * Convert dB amp value to view amp.
	 */
	private int convertAmp(double amp) {
		return (int)(amp*((float)(getMeasuredHeight()/2)/32767));
	}

	public void clearRecordingData() {
		recordingData.clear();
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
		screenShift = 0;
		waveformShift = viewWidth/2;

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
//		Timber.v("DRAW: screenShift" + screenShift + " waveShift = " + waveformShift + " start = " + startX + " length = " + waveformData.length);
		if (waveformData == null && recordingData.size() == 0) {
			return;
		}

		int measuredHeight = getMeasuredHeight();

		drawGrid(canvas);
		if (showRecording) {
			drawRecordingWaveform(canvas);
		} else {
			drawWaveForm(canvas);

			float density = AndroidUtils.dpToPx(1);

			//Draw waveform start indication
			canvas.drawLine(waveformShift, inset, waveformShift, measuredHeight-inset, waveformPaint);

			//Draw waveform end indication
			canvas.drawLine(waveformShift + waveformData.length * density, inset,
					waveformShift + waveformData.length * density, measuredHeight-inset, waveformPaint);

		}
		canvas.drawLine(viewWidth/2, 0, viewWidth/2, measuredHeight, scrubberPaint);
	}

	private void updateShifts(int px) {
		screenShift = px;
		waveformShift = screenShift + viewWidth/2;
	}

	private void drawGrid(Canvas canvas) {
		float height = (float) getHeight();
		int count = 3 + viewWidth/PIXEL_PER_SECOND;
		int gridShift = waveformShift%(PIXEL_PER_SECOND*2);

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND + gridShift;
			canvas.drawLine(xPos, height-inset, xPos, inset, gridPaint);

			xPos = (i+1) * PIXEL_PER_SECOND + gridShift;
			canvas.drawLine(xPos, height - inset, xPos, height - SMALL_LINE_HEIGHT - inset, gridPaint);

			canvas.drawLine(xPos, inset, xPos, SMALL_LINE_HEIGHT + inset, gridPaint);
		}

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND + gridShift;
			long mills = (long)((-waveformShift/(PIXEL_PER_SECOND) + gridShift/PIXEL_PER_SECOND + i)  * 1000);
			if (mills >= 0) {
				String text = TimeUtils.formatTimeIntervalMinSec(mills);
				//Bottom text
				canvas.drawText(text, xPos, height - PADD, textPaint);
				//Top text
				canvas.drawText(text, xPos, textHeight, textPaint);
			}
		}
	}

	private void drawWaveForm(Canvas canvas) {
		if (waveformData.length > 0) {
			int width = waveformData.length;
			int half = getMeasuredHeight() / 2;

			if (width > getMeasuredWidth()) {
				width = getMeasuredWidth();
			}

			Path path = new Path();

			float xPos = waveformShift;
			if (xPos < VIEW_DRAW_EDGE) { xPos = VIEW_DRAW_EDGE; }
			path.moveTo(xPos, half);
			path.lineTo(xPos, half);
			float dpi = AndroidUtils.dpToPx(1);
			for (int i = 1; i < width; i++) {
				xPos = waveformShift + i * dpi;
				if (xPos > VIEW_DRAW_EDGE && xPos < viewWidth - VIEW_DRAW_EDGE) {
					path.lineTo(xPos, half - waveformData[i]);
				}
			}
			for (int i = width - 1; i >= 0; i--) {
				xPos = waveformShift + i * dpi;
				if (xPos > VIEW_DRAW_EDGE && xPos < viewWidth - VIEW_DRAW_EDGE) {
					path.lineTo(xPos, half + 1 + waveformData[i]);
				}
			}
			xPos = waveformShift;
			if (xPos < VIEW_DRAW_EDGE) { xPos = VIEW_DRAW_EDGE; }
			path.lineTo(xPos, half);
			path.close();
			canvas.drawPath(path, waveformPaint);
		}
	}

	private void drawRecordingWaveform(Canvas canvas) {
		if (recordingData.size() > 0) {
			int width = recordingData.size();
			int half = getMeasuredHeight() / 2;

			if (width > getMeasuredWidth()) {
				width = getMeasuredWidth();
			}

			Path path = new Path();

			float xPos = waveformShift;
			if (xPos < VIEW_DRAW_EDGE) { xPos = VIEW_DRAW_EDGE; }
			path.moveTo(xPos, half);
			path.lineTo(xPos, half);
			float dpi = AndroidUtils.dpToPx(1);
			for (int i = 1; i < width; i++) {
				xPos = waveformShift + i * dpi;
				if (xPos > VIEW_DRAW_EDGE && xPos < viewWidth - VIEW_DRAW_EDGE) {
					path.lineTo(xPos, half - recordingData.get(i));
				}
			}
			for (int i = width - 1; i >= 0; i--) {
				xPos = waveformShift + i * dpi;
				if (xPos > VIEW_DRAW_EDGE && xPos < viewWidth - VIEW_DRAW_EDGE) {
					path.lineTo(xPos, half + 1 + recordingData.get(i));
				}
			}
			xPos = waveformShift;
			if (xPos < VIEW_DRAW_EDGE) { xPos = VIEW_DRAW_EDGE; }
			path.lineTo(xPos, half);
			path.close();
			canvas.drawPath(path, waveformPaint);
		}
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

	public void onSaveState(Bundle b) {
		b.putIntArray("waveformData", waveformData);
		b.putLong("playProgress", playProgress);
		b.putIntArray("waveForm", waveForm);
		b.putIntegerArrayList("recordingData", (ArrayList<Integer>) recordingData);
		b.putBoolean("showRecording", showRecording);
	}

	public void onRestoreState(Bundle b) {
		waveformData = b.getIntArray("waveformData");
		playProgress = b.getLong("playProgress");
		waveForm = b.getIntArray("waveForm");
		recordingData = b.getIntegerArrayList("recordingData");
		showRecording = b.getBoolean("showRecording");
	}

	public void setOnSeekListener(OnSeekListener onSeekListener) {
		this.onSeekListener = onSeekListener;
	}

	public interface OnSeekListener {
		void onSeek(int px);
		void onSeeking(int px);
	}
}
