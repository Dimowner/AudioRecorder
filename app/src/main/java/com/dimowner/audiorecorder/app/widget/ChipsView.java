/*
 * Copyright 2020 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain prevDegree copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.RippleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.core.content.ContextCompat;

/**
 * Created on 06.04.2020.
 * @author Dimowner
 */
public class ChipsView extends FrameLayout {

	private final int DRAWABLE_WIDTH;
	private static final int HEIGHT_ANIMATION_DURATION = 300;

	private int chipColor = Color.GREEN;
	private int rippleColor = Color.RED;
	private int chipRadius;
	private int borderColor = Color.WHITE;
	private int borderWidth;
	private int chipTextSize;
	private int chipPadding;
	private int drawablePadding;
	private int chipTextColor = Color.WHITE;
	private int chipTextSelectedColor = Color.WHITE;
	private int chipMargin;
	private int rowCount = 0;
	private float posY = 0;
	private boolean orderEffective = true;
	private boolean multiSelect = true;
	private boolean isEnabled = true;

	private ValueAnimator heightAnimator;

	{
		float DENSITY = AndroidUtils.getDisplayDensity();
		chipRadius = (int)(8*DENSITY);
		borderWidth = (int)(2*DENSITY);
		chipTextSize = (int)(12*DENSITY);
		chipPadding = (int)(8*DENSITY);
		drawablePadding = chipPadding/2;
		DRAWABLE_WIDTH = (int)(20*DENSITY);
		chipMargin = (int)(6*DENSITY);
	}

	private List<Chip> chips;
	private float WIDTH = 1;
	private float ROW_HEIGHT = 1;

	private OnCheckListener listener;

	public ChipsView(Context context) {
		super(context);
		applyAttributes(context, null, 0);
	}

	public ChipsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		applyAttributes(context, attrs, 0);
	}

	public ChipsView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		applyAttributes(context, attrs, defStyleAttr);
	}

	private void applyAttributes(Context context, AttributeSet attrs, int defStyleAttr) {
		chips = new ArrayList<>();
		// Load attributes
		final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ChipsView, defStyleAttr, 0);
		try {
			orderEffective = a.getBoolean(R.styleable.ChipsView_orderEffective, orderEffective);
			multiSelect = a.getBoolean(R.styleable.ChipsView_multiSelect, multiSelect);
			chipColor = a.getColor(R.styleable.ChipsView_chipColor, chipColor);
			rippleColor = a.getColor(R.styleable.ChipsView_rippleColor, rippleColor);
			borderColor = a.getColor(R.styleable.ChipsView_borderColor, borderColor);
			chipTextColor = a.getColor(R.styleable.ChipsView_chipTextColor, chipTextColor);
			chipTextSelectedColor = a.getColor(R.styleable.ChipsView_chipTextSelectedColor, chipTextSelectedColor);
			chipRadius = a.getDimensionPixelSize(R.styleable.ChipsView_chipRadius, chipRadius);
			borderWidth = a.getDimensionPixelSize(R.styleable.ChipsView_borderWidth, borderWidth);
			chipTextSize = a.getDimensionPixelSize(R.styleable.ChipsView_chipTextSize, chipTextSize);
			chipPadding = a.getDimensionPixelSize(R.styleable.ChipsView_chipPadding, chipPadding);
			drawablePadding = chipPadding/2;
			chipMargin = a.getDimensionPixelSize(R.styleable.ChipsView_chipMargin, chipMargin);
			ROW_HEIGHT = 2*chipPadding + DRAWABLE_WIDTH;
		} finally {
			a.recycle();
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		WIDTH = getWidth() - getPaddingStart() - getPaddingEnd();
	}

	@Override
	public void setVisibility(int visibility) {
		super.setVisibility(visibility);
		post(new Runnable() {
			@Override
			public void run() {
				updateChipPositions();
			}
		});
	}

	public TextView createChipView(int id, final String key, final Context context, final String name, final int color, boolean checked) {
		final TextView textView = new TextView(context);
		LayoutParams  lp = new LayoutParams(LayoutParams.WRAP_CONTENT, (int) ROW_HEIGHT);
		textView.setLayoutParams(lp);
		if (checked) {
			setSelected(context, textView, color);
		} else {
			setUnselected(textView, color);
		}
		setRipple(textView);
		textView.setText(name);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, chipTextSize);
		textView.setId(id);
		textView.setVisibility(INVISIBLE);
		textView.setTypeface(textView.getTypeface(), Typeface.NORMAL);
		textView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				int pos = findById(key);
				if (pos >= 0 && isEnabled) {
					if (chips.get(pos).isSelected()) {
						if (multiSelect) {
							setUnselected(chips.get(pos).getView(), color);
							if (listener != null) {
								listener.onCheck(key, name, false);
							}
						}
					} else {
						setSelected(context, chips.get(pos).getView(), color);
						if (!multiSelect) {
							unselectAll(pos);
						}
						if (listener != null) {
							listener.onCheck(key, name, true);
						}
					}
					chips.get(pos).setSelected(!chips.get(pos).isSelected());
				}
			}
		});
		return textView;
	}

	@Override
	public boolean isEnabled() {
		return isEnabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		isEnabled = enabled;
	}

	public void setSelected(String key) {
		int pos = findById(key);
		if (pos >= 0) {
			if (!multiSelect) {
				unselectAll(pos);
			}
			setSelected(getContext(), chips.get(pos).getView(), chips.get(pos).getColor());
			chips.get(pos).setSelected(true);
		}
	}

	public String getSelected() {
		for (int i = 0; i < chips.size(); i++) {
			if (chips.get(i).isSelected) {
				return chips.get(i).getKey();
			}
		}
		return null;
	}

	private void setSelected(Context context, TextView textView, int chipColor) {
		setChecked(textView, chipColor);
		textView.setCompoundDrawablesWithIntrinsicBounds(
				ContextCompat.getDrawable(context, R.drawable.ic_check_circle), null, null, null);
		textView.setCompoundDrawablePadding(drawablePadding);
		textView.setPadding(chipPadding, chipPadding, chipPadding, chipPadding);
		textView.setTextColor(chipTextSelectedColor);
	}

	private void setUnselected(TextView textView, int chipColor) {
		setUnchecked(textView, chipColor);
		textView.setTextColor(chipTextColor);
		textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
		textView.setPadding(chipPadding + DRAWABLE_WIDTH/2 + drawablePadding/2, chipPadding, chipPadding + DRAWABLE_WIDTH/2 + drawablePadding/2, chipPadding);
	}

	private void setChecked(View view, int chipColor) {
		view.setBackground(RippleUtils.createShape(chipColor, borderColor, borderWidth, chipRadius));
	}

	private void setUnchecked(View view, int chipColor) {
		view.setBackground(RippleUtils.createShape(chipColor, chipRadius));
	}

	private void setRipple(View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			view.setForeground(RippleUtils.createRippleMaskShape(rippleColor, chipRadius));
		}
	}

	public void unselectAll(int pos) {
		for (int i = 0; i < chips.size(); i++) {
			if (i != pos) {
				setUnselected(chips.get(i).getView(), chips.get(i).getColor());
				chips.get(i).setSelected(false);
			}
		}
	}

	public void setData(String[] names, String[] keys, int[] colors) {
		clearChips();
		if (names != null) {
			if (WIDTH <= 1) {
				WIDTH = AndroidUtils.getScreenWidth(getContext()) - getPaddingStart() - getPaddingEnd();
			}
			if (names.length >= 1) {
				for (int i = 0; i < names.length; i++) {
					if (colors != null) {
						chips.add(new Chip(keys[i], createChipView(i, keys[i], getContext(), names[i], colors[i], false), names[i], colors[i]));
					} else {
						chips.add(new Chip(keys[i], createChipView(i, keys[i], getContext(), names[i], chipColor, false), names[i], chipColor));
					}
					addView(chips.get(i).getView());
				}
				post(new Runnable() {
					@Override
					public void run() {
						updateChipPositions();
					}
				});
			}
		}
	}

	public void removeChip(String[] keys) {
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			int pos = findById(key);
			if (pos >= 0) {
				removeViewAt(pos);
				chips.remove(pos);
			}
		}
		updateChipPositions();
	}

	public void addChip(String[] keys, String[] names) {
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			String name = names[i];
			int pos = findById(key);
			if (pos == -1) {
				chips.add(new Chip(key, createChipView(chips.size(), key, getContext(), name, chipColor, false), name, chipColor));
				addView(chips.get(chips.size() - 1).getView());
			}
		}
		post(new Runnable() {
			@Override
			public void run() {
				updateChipPositions();
			}
		});
	}

	private void updateChipPositions() {
		//Calculate chips sizes.
//		Rect rect = new Rect();
		Chip chip;
		for (int i = 0; i < chips.size(); i++) {
			chip = chips.get(i);
//			chip.getView().getPaint().getTextBounds(chip.getText(), 0, chip.getText().length(), rect);
//			chip.setWidth(DRAWABLE_WIDTH + rect.width() + 2*chipPadding);
			chip.setWidth(chip.getView().getWidth());
			chip.setHeight((int)ROW_HEIGHT);

			chips.set(i, chip);
		}
		if (orderEffective) {
			Collections.sort(chips);
		}

		rowCount = 0;
		posY = 0;
		if (chips.size() > 0 && WIDTH >= chips.get(0).getWidth()) {
			List<Chip> temp = new ArrayList<>(chips);
			if (orderEffective) {
				calculatePositions(temp);
			} else {
				calculatePositionsDefault(temp);
			}

			for (int i = 0; i < chips.size(); i++) {
				chips.get(i).getView().setTranslationX(chips.get(i).getPosX());
				chips.get(i).getView().setTranslationY(chips.get(i).getPosY());
				chips.get(i).getView().setVisibility(VISIBLE);
			}
		}
		heightAnimator();
	}

	private void heightAnimator() {
		final ViewGroup.LayoutParams lp = getLayoutParams();
		if (heightAnimator != null && (heightAnimator.isStarted())) {
			heightAnimator.cancel();
		}
		int newHeight = (int)(ROW_HEIGHT + chipMargin)* rowCount - chipMargin + getPaddingTop() + getPaddingBottom();
		heightAnimator = ValueAnimator.ofInt(lp.height, newHeight);
		heightAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
		heightAnimator.setDuration(HEIGHT_ANIMATION_DURATION);
		heightAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				lp.height = (int)animation.getAnimatedValue();
				setLayoutParams(lp);
			}
		});
		heightAnimator.start();
	}

	private void calculatePositionsDefault(List<Chip> temp) {
		float posX = 0;
		rowCount++;
		if (temp.size() > 0) {
			float availableWidth = WIDTH;
			for (int i = 0; i < temp.size(); i++) {
				if (availableWidth < temp.get(i).getWidth()) {
					rowCount++;
					posY += ROW_HEIGHT + chipMargin;
					availableWidth = WIDTH;
					posX = 0;
				}
				chips.get(i).setPosX(posX);
				chips.get(i).setPosY(posY);

				float width = temp.get(i).getWidth() + chipMargin;
				posX += width;
				availableWidth -= width;
			}
			posY += ROW_HEIGHT + chipMargin;
		}
	}

	private void calculatePositions(List<Chip> temp) {
		float posX = 0;
		rowCount++;
		if (temp.size() > 0) {
			float availableWidth = WIDTH;
			for (int i = temp.size()-1; i >= 0; i--) {
				if (temp.get(0).getWidth() <= availableWidth) {
					if (temp.get(i).getWidth() <= availableWidth) {
						int pos = findById(temp.get(i).getKey());
						if (pos >= 0) {
							chips.get(pos).setPosX(posX);
							chips.get(pos).setPosY(posY);
						}

						float width = temp.get(i).getWidth() + chipMargin;
						posX += width;
						availableWidth -= width;

						temp.remove(i);
					}
				}
			}
			posY += ROW_HEIGHT + chipMargin;
			if (temp.size() > 0) {
				calculatePositions(temp);
			}
		}
	}

	private int findById(String key) {
		for (int i = 0; i < chips.size(); i++) {
			if (chips.get(i).getKey().equals(key)) {
				return i;
			}
		}
		return -1;
	}

	public void clearChips() {
		rowCount = 0;
		posY = 0;
		chips.clear();
		removeAllViews();
	}

	public void setOnChipCheckListener(OnCheckListener l) {
		this.listener = l;
	}

//	@Override
//	public Parcelable onSaveInstanceState() {
//		Parcelable superState = super.onSaveInstanceState();
//		SavedState ss = new SavedState(superState);
//
//		ss.WIDTH = WIDTH;
//		ss.chipState = chipState;
//		ss.names = names;
//		ss.colors = colors;
//		return ss;
//	}
//
//	@Override
//	public void onRestoreInstanceState(Parcelable state) {
//		SavedState ss = (SavedState) state;
//		super.onRestoreInstanceState(ss.getSuperState());
//		WIDTH = ss.WIDTH;
//		chipState = ss.chipState;
//		names = ss.names;
//		colors = ss.colors;
//		removeAllViews();
//		if (names != null && colors != null) {
//			if (names.length > 1) {
//				views.clear();
//				chipsWidth.clear();
//				totalWidth = 0;
//				chipsHeight = 0;
//				shift = 0;
//				for (int i = 0; i < names.length; i++) {
//					views.add(createChipView(i, getContext(), names[i], colors[i], chipState[i]));
//					addView(views.get(i));
//					updatePositions2(i);
//				}
//			} else {
//				clearChips();
//			}
//		}
//	}
//
//	static class SavedState extends View.BaseSavedState {
//		SavedState(Parcelable superState) {
//			super(superState);
//		}
//
//		private SavedState(Parcel in) {
//			super(in);
//			WIDTH = in.readFloat();
//			in.readBooleanArray(chipState);
//			in.writeStringArray(names);
//			in.writeIntArray(colors);
//		}
//
//		@Override
//		public void writeToParcel(Parcel out, int flags) {
//			super.writeToParcel(out, flags);
//			out.writeFloat(WIDTH);
//			out.writeBooleanArray(chipState);
//			out.writeStringArray(names);
//			out.writeIntArray(colors);
//		}
//
//		boolean[] chipState;
//		String[] names;
//		int[] colors;
//		float WIDTH;
//
//		public static final Parcelable.Creator<SavedState> CREATOR =
//				new Parcelable.Creator<SavedState>() {
//					@Override
//					public SavedState createFromParcel(Parcel in) {
//						return new SavedState(in);
//					}
//
//					@Override
//					public SavedState[] newArray(int size) {
//						return new SavedState[size];
//					}
//				};
//	}

	public interface OnCheckListener {
		void onCheck(String key, String name, boolean checked);
	}

	private static class Chip implements Comparable<Chip> {

		public Chip(String key, TextView view, String text, int color) {
			this.key = key;
			this.view = view;
			this.text = text;
			this.color = color;
		}

		private String key;
		private TextView view;
		private float posX;
		private float posY;
		private boolean isSelected;
		private String text;
		private int color;
		private int width;
		private int height;

		public TextView getView() {
			return view;
		}

		public void setView(TextView view) {
			this.view = view;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public float getPosX() {
			return posX;
		}

		public void setPosX(float posX) {
			this.posX = posX;
		}

		public float getPosY() {
			return posY;
		}

		public void setPosY(float posY) {
			this.posY = posY;
		}

		public boolean isSelected() {
			return isSelected;
		}

		public void setSelected(boolean selected) {
			isSelected = selected;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public int getColor() {
			return color;
		}

		public void setColor(int color) {
			this.color = color;
		}

		public int getWidth() {
			return width;
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getHeight() {
			return height;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		@Override
		public int compareTo(Chip o) {
			return Integer.compare(getWidth(), o.getWidth());
		}
	}
}
