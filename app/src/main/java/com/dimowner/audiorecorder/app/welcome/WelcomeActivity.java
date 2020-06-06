/*
 * Copyright 2020 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.welcome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.InkPageIndicator;
import com.dimowner.audiorecorder.app.setup.SetupActivity;
import com.dimowner.audiorecorder.util.AndroidUtils;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Created on 04.04.2020.
 * @author Dimowner
 */
public class WelcomeActivity extends Activity implements WelcomeContract.View {

//	private ViewPagerPager pagerPager;

	private WelcomePagerAdapter adapter;

//	private ImageView itemImageFirst;
//	private ImageView itemImageSecond;
	private Button actionButton;

	private ViewPager2 pager;
//	private InkPageIndicator pageIndicator;
//	private ViewPager2.OnPageChangeCallback onPageChangeCallback;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, WelcomeActivity.class);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		setTheme(ARApplication.getInjector().provideColorMap().getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_welcome);

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

		actionButton = findViewById(R.id.btn_action);
		actionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
//				if (pager.getCurrentItem() == adapter.getItemCount() - 1) {
					startActivity(SetupActivity.getStartIntent(getApplicationContext()));
					finish();
//				} else {
//					pagerPager.advance();
//				}
			}
		});

		View space = findViewById(R.id.navigation_height);
		final ViewGroup.LayoutParams params = space.getLayoutParams();
		params.height = AndroidUtils.getNavigationBarHeight(getApplicationContext());
		space.setLayoutParams(params);

//		itemImageFirst = findViewById(R.id.item_image_first);
//		itemImageSecond = findViewById(R.id.item_image_second);
		pager = findViewById(R.id.pager);
//		pageIndicator = findViewById(R.id.pageIndicator);
		adapter = new WelcomePagerAdapter();
		pager.setAdapter(adapter);
//		pagerPager = new ViewPagerPager(pager);
//		pageIndicator.setViewPager(pager);
//		final int width = AndroidUtils.getScreenWidth(getApplicationContext());
//		onPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
//			@Override
//			public void onPageSelected(int position) {
//				pagerPager.stopTimer();
//				pagerPager.startTimer();
//				itemImageFirst.setTranslationY(0);
//				itemImageSecond.setTranslationX(width);
//
//				if (position == adapter.getItemCount() - 1) {
//					actionButton.setText(R.string.btn_get_started);
//				} else {
//					actionButton.setText(R.string.btn_next);
//				}
//			}
//
//			private int prevPos = -1; //This added to prevent update images every onPageScrolled call. It only needs when page changed.
//
//			@Override
//			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
//				if (prevPos != position) {
//					switch (position) {
//						case 0:
//							itemImageFirst.setImageResource(R.drawable.waveform);
//							itemImageSecond.setImageResource(R.drawable.waveform);
//							break;
//						case 1:
//							itemImageFirst.setImageResource(R.drawable.waveform);
//							itemImageSecond.setImageResource(R.drawable.waveform_flip);
//							break;
//						case 2:
//							itemImageFirst.setImageResource(R.drawable.waveform_flip);
//							itemImageSecond.setImageResource(R.drawable.waveform);
//							break;
//					}
//				}
//				itemImageFirst.setTranslationY(-positionOffsetPixels);
//				itemImageSecond.setTranslationX(width - positionOffsetPixels);
//				prevPos = position;
//			}
//		};
//		pager.registerOnPageChangeCallback(onPageChangeCallback);
	}

	@Override
	protected void onStart() {
		super.onStart();
//		pagerPager.startTimer();
	}

	@Override
	protected void onStop() {
		super.onStop();
//		pagerPager.stopTimer();
	}

	@Override
	public void showProgress() {

	}

	@Override
	public void hideProgress() {

	}

	@Override
	public void showError(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showError(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showMessage(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}
}
