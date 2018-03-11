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

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

public class GridView extends View {

	private static final float TOP_OFFSET = 20.0f;

	private static final int PIXEL_PER_SECOND = (int) AndroidUtils.dpToPx(AppConstants.PIXELS_PER_SECOND);

	private Paint paint;

	public GridView(Context context) {
		super(context);
		init(context);
	}

	public GridView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public GridView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		paint = new Paint();
		paint.setColor(context.getResources().getColor(R.color.white));
		paint.setStrokeWidth(AndroidUtils.dpToPx(1));
		paint.setTextAlign(Paint.Align.CENTER);
		paint.setTextSize(context.getResources().getDimension(R.dimen.text_small));
	}

	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		drawGrid(canvas, paint);
	}

	private void drawGrid(Canvas canvas, Paint paint) {
		float height = (float) getHeight();
		int lineCount = getWidth()/PIXEL_PER_SECOND;
		for (float i = 1.0f; i < lineCount + 2; i++) {
			//Draw seconds marks
			float xPos = i * PIXEL_PER_SECOND;
			canvas.drawLine(xPos, height, xPos, height - TOP_OFFSET, paint);

			if (i > 1) {
				//Draw seconds text
				long second = ((long) i - 1) * 1000;
				String text = TimeUtils.formatTimeIntervalMinSec(second);
				float x = xPos - PIXEL_PER_SECOND;
				float y = height - TOP_OFFSET - 10.0f;
				canvas.drawText(text, x, y, paint);
			}
		}
	}
}
