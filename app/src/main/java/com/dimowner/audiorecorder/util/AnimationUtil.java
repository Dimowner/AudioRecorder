package com.dimowner.audiorecorder.util;
import android.annotation.TargetApi;
import android.view.View;
import android.view.animation.AnimationUtils;

public class AnimationUtil {

	private AnimationUtil() {}

	@TargetApi(21)
	public static void viewElevationAnimation(View view, float val) {
		view.animate()
				.translationZ(val)
				.setDuration(100L)
				.setInterpolator(AnimationUtils.loadInterpolator(view.getContext(),
						android.R.interpolator.accelerate_decelerate))
				.start();
	}
}
