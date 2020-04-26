package com.dimowner.audiorecorder.app.welcome;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import androidx.viewpager2.widget.ViewPager2;

class ViewPagerPager {

	private static final int AUTO_ADVANCE_DELAY = 5000;

	private static final int PAGE_CHANGE_DURATION = 400;
	private static final int MULTI_PAGE_CHANGE_DURATION = 600;

	private ViewPager2 viewPager;
	private boolean isTouch = false;

	private Interpolator fastOutSlowIn;

	private Handler handler = new Handler();

	private Runnable advancePager = new Runnable() {
		@Override
		public void run() {
			advance();
		}
	};

	ViewPagerPager(ViewPager2 viewPager) {
		this.viewPager = viewPager;
		fastOutSlowIn = AnimationUtils.loadInterpolator(viewPager.getContext(), android.R.interpolator.fast_out_slow_in);
		//To prevent app crash do not auto scroll pager when user scrolling it manually.
		this.viewPager.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					isTouch = false;
				} else if (event.getAction() == MotionEvent.ACTION_DOWN) {
					isTouch = true;
				}
				return false;
			}
		});
	}

	void startTimer() {
		handler.postDelayed(advancePager, AUTO_ADVANCE_DELAY);
	}

	void stopTimer() {
		handler.removeCallbacks(advancePager);
	}

	void advance() {
		if (!isTouch && viewPager.getAdapter() != null) {
			if (viewPager.getWidth() <= 0) return;

			int current = viewPager.getCurrentItem();
			final int next = ((current + 1) % viewPager.getAdapter().getItemCount());
			int pages = next - current;

			final int dragDistance = pages * viewPager.getWidth();
			ValueAnimator animator = ValueAnimator.ofInt(0, dragDistance);
			animator.addListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {
					viewPager.beginFakeDrag();
				}

				@Override
				public void onAnimationEnd(Animator animation) {
					viewPager.endFakeDrag();
				}
				@Override public void onAnimationCancel(Animator animation) { }
				@Override public void onAnimationRepeat(Animator animation) { }
			});

			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

				int dragProgress = 0;
				int draggedPages = 0;
				int prevDragPoint = 0;

				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					if (!viewPager.isFakeDragging()) {
						// Sometimes onAnimationUpdate is called with initial value before
						// onAnimationStart is called.
						return;
					}
					int dragPoint = (int) animation.getAnimatedValue();
					viewPager.fakeDragBy(-(dragPoint - prevDragPoint));
					dragProgress = dragPoint;

					// Fake dragging doesn't let you drag more than one page width. If we want to do
					// this then need to end and start a new fake drag.
					int draggedPagesProgress = dragProgress / viewPager.getWidth();
					if (draggedPagesProgress != draggedPages) {
						viewPager.endFakeDrag();
						viewPager.beginFakeDrag();
						draggedPages = draggedPagesProgress;
					}
					prevDragPoint = dragPoint;
				}
			});
			animator.setDuration(pages == 1 ? PAGE_CHANGE_DURATION : MULTI_PAGE_CHANGE_DURATION);
			animator.setInterpolator(fastOutSlowIn);
			animator.start();
		}
	}
}
