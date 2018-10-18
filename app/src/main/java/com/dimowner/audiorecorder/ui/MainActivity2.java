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

package com.dimowner.audiorecorder.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.FileRepositoryImpl;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.exception.FileRepositoryInitializationFailed;
import com.dimowner.audiorecorder.ui.widget.GridView;
import com.dimowner.audiorecorder.ui.widget.WaveformView;
import com.dimowner.audiorecorder.audio.SoundFile;
import com.dimowner.audiorecorder.util.AppStartTracker;

import java.util.Random;

import timber.log.Timber;

public class MainActivity2 extends Activity implements MainContract.View {

//	TODO: make waveform look like in soundcloud app.

	public static final int REQ_CODE_RECORD_AUDIO = 303;

	private AppStartTracker tracker;

	private WaveformView waveformView;
	private TextView txtDuration;
	private ImageButton btnPlay;
	private ImageButton btnRecord;
	private ImageButton btnClear;
//	private ProgressBar progressBar;

	private MainPresenter presenter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		tracker = ARApplication.getAppStartTracker(getApplicationContext());
		tracker.activityOnCreate();

		applyColoredTheme(new Random().nextInt(7));
		super.onCreate(savedInstanceState);
		tracker.activityContentViewBefore();

		setContentView(generateView());
		tracker.activityContentViewAfter();

		try {
			//Presenter initialization
			final Prefs prefs = new Prefs(getApplicationContext());
			FileRepository fileRepository = new FileRepositoryImpl(getApplicationContext());
			presenter = new MainPresenter(prefs, fileRepository);
			presenter.bindView(this);

			btnRecord.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkRrecordPermission()) {
						presenter.recordingClicked();
					}
				}
			});
			btnPlay.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					presenter.playClicked();
				}
			});
			btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					presenter.deleteAll();
				}
			});
		} catch (FileRepositoryInitializationFailed e) {
			Timber.e(e);
			showError(ErrorParser.parseException(e));
		}

		tracker.activityOnCreateEnd();
	}

	public View generateView() {
		Resources res = getResources();
		Context ctx = getApplicationContext();

		//Root layout init
		LinearLayout root = new LinearLayout(ctx);
		root.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		root.setLayoutParams(llp);

		HorizontalScrollView horizontalScroll = new HorizontalScrollView(ctx);
		LinearLayout.LayoutParams hsLP = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
		horizontalScroll.setLayoutParams(hsLP);

		horizontalScroll.setClipChildren(false);
		horizontalScroll.setClipToPadding(false);
		horizontalScroll.setFillViewport(true);

		LinearLayout studioLayout = new LinearLayout(ctx);
		studioLayout.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams studioLP = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		studioLayout.setLayoutParams(studioLP);

		studioLayout.setGravity(Gravity.CENTER_HORIZONTAL);
		studioLayout.setClipChildren(false);
		studioLayout.setClipToPadding(false);

		GridView gridView = new GridView(ctx);
		LinearLayout.LayoutParams gridLP = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int)res.getDimension(R.dimen.grid_view_height));
		gridView.setLayoutParams(gridLP);

		waveformView = new WaveformView(ctx);
		LinearLayout.LayoutParams waveLP = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int)res.getDimension(R.dimen.waveform_height));
		waveformView.setLayoutParams(waveLP);

		// Creating a new TextView
		txtDuration = new TextView(ctx);
		txtDuration.setTextColor(res.getColor(R.color.text_primary_light));
		txtDuration.setTypeface(null, Typeface.BOLD);
		txtDuration.setTextSize(res.getDimension(R.dimen.text_xlarge));
		txtDuration.setText("00:00:00");
		txtDuration.setGravity(Gravity.CENTER_HORIZONTAL);

		//Control panel layout init
		LinearLayout controlPanel = new LinearLayout(ctx);
		controlPanel.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		controlPanel.setGravity(Gravity.CENTER_HORIZONTAL);
		controlPanel.setLayoutParams(cplp);

		//Buttons init
		// Defining the layout parameters of the Button
		LinearLayout.LayoutParams btnLP = new LinearLayout.LayoutParams(
				(int)res.getDimension(R.dimen.bottom_pnl_btn_size),
				(int)res.getDimension(R.dimen.bottom_pnl_btn_size));
		int margin = (int)res.getDimension(R.dimen.spacing_small);
		btnLP.setMargins(margin, margin, margin, margin);

		btnPlay = new ImageButton(ctx);
		btnPlay.setAdjustViewBounds(true);
		btnPlay.setClickable(true);
		btnPlay.setFocusable(true);
		btnPlay.setScaleType(ImageView.ScaleType.CENTER);
		btnPlay.setImageResource(R.drawable.play);
		btnPlay.setBackgroundResource(R.drawable.button_selector);
		btnPlay.setLayoutParams(btnLP);

		btnRecord = new ImageButton(ctx);
		btnRecord.setAdjustViewBounds(true);
		btnRecord.setClickable(true);
		btnRecord.setFocusable(true);
		btnRecord.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		btnRecord.setImageResource(R.drawable.record);
		btnRecord.setBackgroundResource(R.drawable.button_selector);
		btnRecord.setLayoutParams(btnLP);

		btnClear = new ImageButton(ctx);
		btnClear.setAdjustViewBounds(true);
		btnClear.setClickable(true);
		btnClear.setFocusable(true);
		btnClear.setScaleType(ImageView.ScaleType.CENTER);
		btnClear.setImageResource(R.drawable.delete_forever);
		btnClear.setBackgroundResource(R.drawable.button_selector);
		btnClear.setLayoutParams(btnLP);

//		progressBar = new ProgressBar(ctx);
		LinearLayout.LayoutParams progressLP = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int)res.getDimension(R.dimen.bottom_pnl_btn_size));
//		progressBar.setVisibility(View.GONE);
//		progressBar.setLayoutParams(progressLP);

		studioLayout.addView(gridView);
		studioLayout.addView(waveformView);
		horizontalScroll.addView(studioLayout);
		root.addView(horizontalScroll);
		root.addView(txtDuration);
		controlPanel.addView(btnClear);
		controlPanel.addView(btnPlay);
		controlPanel.addView(btnRecord);
//		controlPanel.addView(progressBar);
		root.addView(controlPanel);

		return root;
	}

	@Override
	protected void onStart() {
		super.onStart();
		tracker.activityOnStart();
	}

	@Override
	protected void onResume() {
		super.onResume();

		presenter.loadLastRecord(getApplicationContext());
		tracker.activityOnResume();
//		Timber.v(tracker.getResults());
		if (!tracker.isRun()) {
			Toast.makeText(getApplicationContext(), tracker.getStartTime(), Toast.LENGTH_LONG).show();
		}
		tracker.setRun();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	public void showProgress() {
		btnClear.setVisibility(View.GONE);
		btnPlay.setVisibility(View.GONE);
		btnRecord.setVisibility(View.GONE);
//		progressBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideProgress() {
		btnClear.setVisibility(View.VISIBLE);
		btnPlay.setVisibility(View.VISIBLE);
		btnRecord.setVisibility(View.VISIBLE);
//		progressBar.setVisibility(View.GONE);
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
	public void showRecordingStart() {
		btnRecord.setImageResource(R.drawable.record_rec);
	}

	@Override
	public void showRecordingStop() {
		btnRecord.setImageResource(R.drawable.record);
		presenter.loadLastRecord(getApplicationContext());
	}

	@Override
	public void showPlayStart() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				btnPlay.setImageResource(R.drawable.pause);
			}
		});
	}

	@Override
	public void showPlayPause() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				btnPlay.setImageResource(R.drawable.play);
			}
		});
	}

	@Override
	public void showPlayStop() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				btnPlay.setImageResource(R.drawable.play);
			}
		});
	}

	@Override
	public void showSoundFile(SoundFile soundFile) {
		waveformView.setSoundFile(soundFile);
	}

	@Override
	public void showDuration(String duration) {
		txtDuration.setText(duration);
	}

	@Override
	public void onPlayProgress(int px) {

	}

	private boolean checkRrecordPermission() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
				return false;
			}
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			presenter.recordingClicked();
		}
	}

	private void applyColoredTheme(int r) {
		Timber.v("applyColoredTheme r = %d", r);
		switch (r) {
			case 0:
				setTheme(R.style.AppTheme);
				break;
			case 1:
				setTheme(R.style.AppTheme_Brown);
				break;
			case 2:
				setTheme(R.style.AppTheme_DeepOrange);
				break;
			case 3:
				setTheme(R.style.AppTheme_Pink);
				break;
			case 4:
				setTheme(R.style.AppTheme_Purple);
				break;
			case 5:
				setTheme(R.style.AppTheme_Red);
				break;
			case 6:
				setTheme(R.style.AppTheme_Teal);
				break;
		}
	}
}

