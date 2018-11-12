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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
//import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.FileRepositoryImpl;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.exception.FileRepositoryInitializationFailed;
import com.dimowner.audiorecorder.ui.records.RecordsActivity;
import com.dimowner.audiorecorder.ui.settings.SettingsActivity;
import com.dimowner.audiorecorder.ui.widget.ScrubberView;
import com.dimowner.audiorecorder.ui.widget.WaveformView;
import com.dimowner.audiorecorder.audio.SoundFile;
import com.dimowner.audiorecorder.util.TimeUtils;

import timber.log.Timber;

public class MainActivity extends Activity implements MainContract.View, View.OnClickListener {

//	TODO: make waveform look like in soundcloud app.

	public static final int REQ_CODE_RECORD_AUDIO = 303;

	private WaveformView waveformView;
	private TextView txtDuration;
	private ImageButton btnPlay;
	private ImageButton btnRecord;
	private ImageButton btnClear;
	private ImageButton btnRecordsList;
	private ImageButton btnSettings;
	private ScrubberView scrubberView;

//	private ProgressBar progressBar;

	private MainPresenter presenter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(ARApplication.getAppThemeResource(getApplicationContext()));
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		waveformView = findViewById(R.id.record);
		txtDuration = findViewById(R.id.txt_duration);
		btnPlay = findViewById(R.id.btn_play);
		btnRecord = findViewById(R.id.btn_record);
		btnClear = findViewById(R.id.btn_clear);
		btnRecordsList = findViewById(R.id.btn_records_list);
		btnSettings = findViewById(R.id.btn_settings);

		scrubberView = findViewById(R.id.scrubber);

//		progressBar = findViewById(R.id.progress);

		try {
			//Presenter initialization
			final Prefs prefs = new Prefs(getApplicationContext());
			FileRepository fileRepository = new FileRepositoryImpl(getApplicationContext());
			presenter = new MainPresenter(prefs, fileRepository);
			presenter.bindView(this);

			btnPlay.setOnClickListener(this);
			btnRecord.setOnClickListener(this);
			btnClear.setOnClickListener(this);
			btnRecordsList.setOnClickListener(this);
			btnSettings.setOnClickListener(this);
		} catch (FileRepositoryInitializationFailed e) {
			Timber.e(e);
			showError(ErrorParser.parseException(e));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		presenter.loadLastRecord(getApplicationContext());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.btn_play:
				presenter.playClicked();
				break;
			case R.id.btn_record:
				if (checkRecordPermission()) {
					presenter.recordingClicked();
				}
				break;
			case R.id.btn_clear:
				presenter.deleteAll();
				break;
			case R.id.btn_records_list:
				startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btn_settings:
				startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
				break;
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
				scrubberView.setCurrentPosition(-1);
			}
		});
	}

	@Override
	public void showSoundFile(SoundFile soundFile) {
		waveformView.setSoundFile(soundFile);
	}

	@Override
	public void showDuration(final String duration) {
//		txtDuration.setText(getString(R.string.duration, duration));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtDuration.setText(duration);
			}
		});
	}

	@Override
	public void onPlayProgress(final long mills, final int px) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Timber.v("onPlayProgress: " + px);
				scrubberView.setCurrentPosition(px);
				txtDuration.setText(TimeUtils.formatTimeIntervalMinSecMills(mills));
			}
		});
	}

	private boolean checkRecordPermission() {
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
}
