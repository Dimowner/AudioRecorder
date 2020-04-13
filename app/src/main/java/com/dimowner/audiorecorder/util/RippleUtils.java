package com.dimowner.audiorecorder.util;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;

/**
 * Created on 11.04.2020.
 * @author Dimowner
 */
public class RippleUtils {

	private RippleUtils() {
	}

	public static RippleDrawable createRippleShape(int backgroundColor, int rippleColor, int borderColor,
																  int borderWidthPx, float cornerRadiusPx) {
		return new RippleDrawable(
				createColorStateList(rippleColor),
				createShape(backgroundColor, borderColor, borderWidthPx, cornerRadiusPx),
				null
		);
	}

	public static RippleDrawable createRippleShape(int backgroundColor, int rippleColor, float cornerRadiusPx) {
		return createRippleShape(backgroundColor, rippleColor, 0, 0, cornerRadiusPx);
	}

	public static GradientDrawable createShape(int backgroundColor, int borderColor,
															 int borderWidthPx, float cornerRadiusPx) {
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.RECTANGLE);
		if (cornerRadiusPx > 0) {
			shape.setCornerRadii(new float[]{cornerRadiusPx, cornerRadiusPx, cornerRadiusPx,
					cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx});
		}
		shape.setColor(backgroundColor);
		if (borderWidthPx > 0) {
			shape.setStroke(borderWidthPx, borderColor);
		}
		return shape;
	}

	public static GradientDrawable createShape(int backgroundColor, float cornerRadiusPx) {
		GradientDrawable shape = new GradientDrawable();
		shape.setShape(GradientDrawable.RECTANGLE);
		if (cornerRadiusPx > 0) {
			shape.setCornerRadii(new float[]{cornerRadiusPx, cornerRadiusPx, cornerRadiusPx,
					cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx});
		}
		shape.setColor(backgroundColor);
		return shape;
	}

	public static RippleDrawable createRippleMaskShape(int rippleColor, float cornerRadiusPx) {
		return new RippleDrawable(
				createColorStateList(rippleColor),
				null,
				createMaskShape(cornerRadiusPx));
	}

	public static ShapeDrawable createMaskShape(float cornerRadiusPx) {
		RoundRectShape rectShape = new RoundRectShape(new float[]{
				cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx,
				cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx,
		}, null, null);
		return new ShapeDrawable(rectShape);
	}

	public static ColorStateList createColorStateList(int pressedColor) {
		return new ColorStateList(new int[][]{ new int[]{}}, new int[]{pressedColor});
	}

	public static RippleDrawable getBackgroundDrawable(int pressedColor, Drawable backgroundDrawable) {
		return new RippleDrawable(createColorStateList(pressedColor), backgroundDrawable, null);
	}

	public static ColorDrawable getColorDrawableFromColor(int color) {
		return new ColorDrawable(color);
	}
}
