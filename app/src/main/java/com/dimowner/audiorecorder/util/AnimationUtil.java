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

package com.dimowner.audiorecorder.util;
import android.animation.Animator;
import android.annotation.TargetApi;
import android.view.View;
import android.view.animation.AnimationUtils;

public class AnimationUtil {

	private AnimationUtil() {}

	@TargetApi(21)
	public static void viewElevationAnimation(final View view, float val, Animator.AnimatorListener listener) {
		view.animate()
				.translationZ(val)
				.setDuration(250L)
				.setInterpolator(AnimationUtils.loadInterpolator(view.getContext(),
						android.R.interpolator.accelerate_decelerate))
				.setListener(listener)
				.start();
	}

	@TargetApi(21)
	public static void viewAnimationX(final View view, float val, Animator.AnimatorListener listener) {
		view.animate()
				.translationX(val)
				.setDuration(250L)
				.setInterpolator(AnimationUtils.loadInterpolator(view.getContext(),
						android.R.interpolator.accelerate_decelerate))
				.setListener(listener)
				.start();
	}

	@TargetApi(21)
	public static void viewAnimationY(final View view, float val, Animator.AnimatorListener listener) {
		view.animate()
				.translationY(val)
				.setDuration(250L)
				.setInterpolator(AnimationUtils.loadInterpolator(view.getContext(),
						android.R.interpolator.accelerate_decelerate))
				.setListener(listener)
				.start();
	}
}
