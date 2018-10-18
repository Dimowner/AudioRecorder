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
import android.util.AttributeSet;
import android.view.View;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;

/**
 * Playback progress indicator view.
 */
public class ScrubberView extends View {

	private int currentPosition;
	private Paint scrubberPaint;
	private Paint selectPaint;

	public ScrubberView(Context context) {
		super(context);
		init(context);
	}

	public ScrubberView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public ScrubberView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		currentPosition = -1;
		scrubberPaint = new Paint();
		scrubberPaint.setAntiAlias(false);
		scrubberPaint.setStyle(Paint.Style.STROKE);
		scrubberPaint.setStrokeWidth(AndroidUtils.dpToPx(2));
		scrubberPaint.setColor(context.getResources().getColor(R.color.white));

		selectPaint = new Paint();
		selectPaint.setStyle(Paint.Style.FILL);
		selectPaint.setColor(context.getResources().getColor(R.color.text_disabled_light));

		setWillNotDraw(false);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (currentPosition >= 0) {
			canvas.drawRect(0, 0, currentPosition, getMeasuredHeight(), selectPaint);
			canvas.drawLine(currentPosition, 0, currentPosition, getMeasuredHeight(), scrubberPaint);
		}
	}

	public int getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(int pos) {
		this.currentPosition = pos;
		invalidate();
	}
}
