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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.IntArrayList;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WaveformView extends View {

	private static final int DEFAULT_PIXEL_PER_SECOND = (int) AndroidUtils.dpToPx(AppConstants.SHORT_RECORD_DP_PER_SECOND);
	private static final float SMALL_LINE_HEIGHT = AndroidUtils.dpToPx(12);
	private static final float PADD = AndroidUtils.dpToPx(6);
	private static final int VIEW_DRAW_EDGE = 0;
	private static final int ANIMATION_DURATION = 330; //mills.

	private float pxPerSecond = DEFAULT_PIXEL_PER_SECOND;

	private Paint waveformPaint;
	private Paint gridPaint;
	private Paint scrubberPaint;
	private TextPaint textPaint;
	private Path path = new Path();

	private int[] waveformData;
	private int playProgressPx;

	private int[] waveForm;

	private List<Integer> recordingData;
	private long totalRecordingSize;
	private boolean showRecording = false;

	private boolean isInitialized;
	private float textHeight;
	private float inset;

	private final int[] empty = new int[0];

	private int prevScreenShift = 0;
	private float startX = 0;
	private boolean readPlayProgress = true;
	private int screenShift = 0;
	private int waveformShift = 0;
	private int viewWidth = 0;

	/** Defines which grid drawn for short waveform or for long. */
	private boolean isShortWaveForm = true;

	/**
	 * Values used to prevent call {@link #adjustWaveformHeights} before view is measured because
	 * in that method used measured height value which calculates in {@link #onMeasure(int, int)}
	 */
	private boolean isMeasured = false;

	private OnSeekListener onSeekListener;

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

		recordingData = new LinkedList<>();
		totalRecordingSize = 0;
		path = new Path();

		waveformPaint = new Paint();
		waveformPaint.setStyle(Paint.Style.STROKE);
		waveformPaint.setStrokeWidth(AndroidUtils.dpToPx(1.2f));
		waveformPaint.setAntiAlias(true);
		waveformPaint.setColor(context.getResources().getColor(R.color.dark_white));

		scrubberPaint = new Paint();
		scrubberPaint.setAntiAlias(false);
		scrubberPaint.setStyle(Paint.Style.STROKE);
		scrubberPaint.setStrokeWidth(AndroidUtils.dpToPx(2));
		scrubberPaint.setColor(context.getResources().getColor(R.color.md_yellow_A700));

		gridPaint = new Paint();
		gridPaint.setColor(context.getResources().getColor(R.color.md_grey_100_75));
		gridPaint.setStrokeWidth(AndroidUtils.dpToPx(1)/2);

		textHeight = context.getResources().getDimension(R.dimen.text_normal);
		inset = textHeight + PADD;
		textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
		textPaint.setColor(context.getResources().getColor(R.color.md_grey_100));
		textPaint.setStrokeWidth(AndroidUtils.dpToPx(1));
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		textPaint.setTextSize(textHeight);

		playProgressPx = -1;
		waveForm = null;
		isInitialized = false;

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent motionEvent) {
				if (!showRecording) {
					switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
						case MotionEvent.ACTION_DOWN:
							readPlayProgress = false;
							startX = motionEvent.getX();
							if (onSeekListener != null) {
								onSeekListener.onStartSeek();
							}
							break;
						case MotionEvent.ACTION_MOVE:
							int shift = (int) (prevScreenShift + (motionEvent.getX()) - startX);
							//Right waveform move edge
							if (shift <= -AndroidUtils.dpToPx(waveformData.length)) {
								shift = (int) -AndroidUtils.dpToPx(waveformData.length);
							}
							//Left waveform move edge
							if (shift > 0) {
								shift = 0;
							}
							if (onSeekListener != null) {
								onSeekListener.onSeeking(-screenShift, AndroidUtils.convertPxToMills(-screenShift, pxPerSecond));
							}
							playProgressPx = -shift;
							updateShifts(shift);
							invalidate();
							break;
						case MotionEvent.ACTION_UP:
							if (onSeekListener != null) {
								onSeekListener.onSeek(-screenShift, AndroidUtils.convertPxToMills(-screenShift, pxPerSecond));
							}
							prevScreenShift = screenShift;
							readPlayProgress = true;
							performClick();
							break;
					}
				}
				return true;
			}
		});
	}

	public void setPlayback(int px) {
		if (readPlayProgress) {
			playProgressPx = px;
			updateShifts(-playProgressPx);
			prevScreenShift = screenShift;
			invalidate();
		}
	}

	public void moveToStart() {
		final ValueAnimator moveAnimator;
		moveAnimator = ValueAnimator.ofInt(playProgressPx, 0);
		moveAnimator.setInterpolator(new DecelerateInterpolator());
		moveAnimator.setDuration(ANIMATION_DURATION);
		moveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				int moveVal = (int)animation.getAnimatedValue();
				setPlayback(moveVal);
			}
		});
		moveAnimator.start();
	}

	/**
	 * Rewinds current play position. (Current position + mills)
	 * @param mills time interval.
	 */
	public void rewindMills(long mills) {
		playProgressPx += AndroidUtils.convertMillsToPx(mills, pxPerSecond);
		updateShifts(-playProgressPx);
		prevScreenShift = screenShift;
		invalidate();
		if (onSeekListener != null) {
			onSeekListener.onSeeking(-screenShift, AndroidUtils.convertPxToMills(-screenShift, pxPerSecond));
		}
	}

	/**
	 * Set new current play position in pixels.
	 * @param px value.
	 */
	public void seekPx(int px) {
		playProgressPx = px;
		updateShifts(-playProgressPx);
		prevScreenShift = screenShift;
		invalidate();
		if (onSeekListener != null) {
			onSeekListener.onSeeking(-screenShift, AndroidUtils.convertPxToMills(-screenShift, pxPerSecond));
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

	public void setPxPerSecond(float pxPerSecond) {
		isShortWaveForm = pxPerSecond == DEFAULT_PIXEL_PER_SECOND;
		this.pxPerSecond = pxPerSecond;
	}

	public void showRecording() {
		updateShifts((int) -AndroidUtils.dpToPx(totalRecordingSize));
		pxPerSecond = (int) AndroidUtils.dpToPx(AppConstants.SHORT_RECORD_DP_PER_SECOND);
		isShortWaveForm = true;
		showRecording = true;
		invalidate();
	}

	public void hideRecording() {
		showRecording = false;
		updateShifts(0);
		clearRecordingData();
	}

	public int getWaveformLength() {
		if (waveformData != null) {
			return waveformData.length;
		}
		return 0;
	}

	public void addRecordAmp(int amp) {
		if (amp < 0) {
			amp = 0;
		}
		totalRecordingSize++;
		updateShifts((int) -AndroidUtils.dpToPx(totalRecordingSize));
		recordingData.add(convertAmp(amp));
		if (recordingData.size() > AndroidUtils.pxToDp(viewWidth/2)) {
			recordingData.remove(0);
		}
		invalidate();
	}

	public void setRecordingData(final IntArrayList data) {
		post(new Runnable() {
			@Override
			public void run() {
				if (data != null) {
					recordingData.clear();
					int count = (int)AndroidUtils.pxToDp(viewWidth/2);
					if (data.size() > count) {
						for (int i = data.size() - count; i < data.size(); i++) {
							recordingData.add(convertAmp(data.get(i)));
						}
					} else {
						for (int i = 0; i < data.size(); i++) {
							recordingData.add(convertAmp(data.get(i)));
						}
					}
					totalRecordingSize = data.size();
					updateShifts((int) -AndroidUtils.dpToPx(totalRecordingSize));
					invalidate();
				}
			}
		});
	}

	/**
	 * Convert dB amp value to view amp.
	 */
	private int convertAmp(double amp) {
		return (int)(amp*((float)(getMeasuredHeight()/2)/32767));
	}

	public void clearRecordingData() {
		recordingData = new ArrayList<>();
		totalRecordingSize = 0;
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

		screenShift = -playProgressPx;
		waveformShift = screenShift + viewWidth/2;
		prevScreenShift = screenShift;

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
		if (waveformData == null && recordingData.size() == 0) {
			return;
		}

		int measuredHeight = getMeasuredHeight();

		if (isShortWaveForm) {
			drawGrid(canvas);
		} else {
			drawGrid2(canvas);
		}
		if (showRecording) {
			drawRecordingWaveform2(canvas);
		} else {
			drawWaveForm2(canvas);

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

	/**
	 * Draws grid for short waveform shorter than {@link AppConstants#LONG_RECORD_THRESHOLD_SECONDS} value.
	 * Also for recording.
	 */
	private void drawGrid(Canvas canvas) {
		float height = (float) getHeight();
		int count = 3 + viewWidth/ DEFAULT_PIXEL_PER_SECOND;
		int gridShift = waveformShift%(DEFAULT_PIXEL_PER_SECOND *2);

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * DEFAULT_PIXEL_PER_SECOND + gridShift;
			canvas.drawLine(xPos, height-inset, xPos, inset, gridPaint);

			xPos = (i+1) * DEFAULT_PIXEL_PER_SECOND + gridShift;
			canvas.drawLine(xPos, height - inset, xPos, height - SMALL_LINE_HEIGHT - inset, gridPaint);

			canvas.drawLine(xPos, inset, xPos, SMALL_LINE_HEIGHT + inset, gridPaint);
		}

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * DEFAULT_PIXEL_PER_SECOND + gridShift;
			long mills = (long)((-waveformShift/(DEFAULT_PIXEL_PER_SECOND) + gridShift/ DEFAULT_PIXEL_PER_SECOND + i)  * 1000);
			if (mills >= 0) {
				String text = TimeUtils.formatTimeIntervalMinSec(mills);
				//Bottom text
				canvas.drawText(text, xPos, height - PADD, textPaint);
				//Top text
				canvas.drawText(text, xPos, textHeight, textPaint);
			}
		}
	}

	/**
	 * Draws grid for long waveform longer than {@link AppConstants#LONG_RECORD_THRESHOLD_SECONDS} value.
	 */
	private void drawGrid2(Canvas canvas) {
		float height = (float) getHeight();
		int markCount = AppConstants.GRID_LINES_COUNT;

		int count = 3 + markCount;
		int pxPerMark = viewWidth/markCount;
		float secPerMark = pxPerMark/pxPerSecond;

		int gridShift = (waveformShift%(pxPerMark*2));

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * pxPerMark + gridShift;
			canvas.drawLine(xPos, height-inset, xPos, inset, gridPaint);

			xPos = (i+1) * pxPerMark + gridShift;
			canvas.drawLine(xPos, height - inset, xPos, height - SMALL_LINE_HEIGHT - inset, gridPaint);

			canvas.drawLine(xPos, inset, xPos, SMALL_LINE_HEIGHT + inset, gridPaint);
		}

		for (float i = -2f; i < count; i+=2) {
			//Draw seconds marks
			float xPos = i * pxPerMark + gridShift;
			long mills = (long)((-waveformShift/pxPerSecond + gridShift/pxPerSecond + secPerMark * i)  * 1000);
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

			path.reset();

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

	private void drawWaveForm2(Canvas canvas) {
		int width = waveformData.length;
		int half = getMeasuredHeight() / 2;

		if (width > getMeasuredWidth()) {
			width = getMeasuredWidth();
		}

		float dpi = AndroidUtils.dpToPx(1);
		float[] lines = new float[width*4];
		int step = 0;
		for (int i = 0; i < width; i++) {
			lines[step] = waveformShift + i*dpi;
			lines[step+1] = half + waveformData[i]+1;
			lines[step+2] = waveformShift + i*dpi;
			lines[step+3] = half - waveformData[i]-1;
			step +=4;
		}
		canvas.drawLines(lines, 0, lines.length, waveformPaint);
	}

	private void drawRecordingWaveform(Canvas canvas) {
		if (recordingData.size() > 0) {
			int half = getMeasuredHeight() / 2;
			path.reset();
			float xPos = waveformShift;
			if (xPos < VIEW_DRAW_EDGE) { xPos = VIEW_DRAW_EDGE; }
			path.moveTo(xPos, half);
			path.lineTo(xPos, half);
			float dpi = AndroidUtils.dpToPx(1);
			int startPos = 1;
			if (waveformShift < 0) {
				startPos = (int)(waveformShift * dpi);
			}
			for (int i = startPos; i < recordingData.size(); i++) {
				xPos = waveformShift + i * dpi;
				if (xPos > VIEW_DRAW_EDGE && xPos < viewWidth - VIEW_DRAW_EDGE) {
					path.lineTo(xPos, half - recordingData.get(i));
				}
			}
			for (int i = recordingData.size() - 1; i >= startPos; i--) {
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

	private void drawRecordingWaveform2(Canvas canvas) {
		if (recordingData.size() > 0) {
			int width = recordingData.size();
			int half = getMeasuredHeight() / 2;

			float dpi = AndroidUtils.dpToPx(1);
			float[] lines = new float[width * 4];
			int step = 0;
			for (int i = 0; i < width; i++) {
				lines[step] = (float) viewWidth/2 - i * dpi;
				lines[step + 1] = half + recordingData.get(recordingData.size()-1 - i) + 1;
				lines[step + 2] = (float) viewWidth/2 - i * dpi;
				lines[step + 3] = half - recordingData.get(recordingData.size()-1 - i) - 1;
				step += 4;
			}
			canvas.drawLines(lines, 0, lines.length, waveformPaint);
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

//	public void onSaveState(Bundle b) {
//		b.putIntArray("waveformData", waveformData);
//		b.putLong("playProgressPx", playProgressPx);
//		b.putIntArray("waveForm", waveForm);
//		b.putIntegerArrayList("recordingData", (ArrayList<Integer>) recordingData);
//		b.putBoolean("showRecording", showRecording);
//	}
//
//	public void onRestoreState(Bundle b) {
//		waveformData = b.getIntArray("waveformData");
//		playProgressPx = b.getLong("playProgressPx");
//		waveForm = b.getIntArray("waveForm");
//		recordingData = b.getIntegerArrayList("recordingData");
//		showRecording = b.getBoolean("showRecording");
//	}

	public void setOnSeekListener(OnSeekListener onSeekListener) {
		this.onSeekListener = onSeekListener;
	}

	public interface OnSeekListener {
		void onStartSeek();
		void onSeek(int px, long mills);
		void onSeeking(int px, long mills);
	}
}
