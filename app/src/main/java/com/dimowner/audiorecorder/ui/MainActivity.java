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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.ui.records.RecordsActivity;
import com.dimowner.audiorecorder.ui.settings.SettingsActivity;
import com.dimowner.audiorecorder.ui.widget.ScrubberView;
import com.dimowner.audiorecorder.ui.widget.WaveformView;
import com.dimowner.audiorecorder.util.TimeUtils;
import timber.log.Timber;

public class MainActivity extends Activity implements MainContract.View, View.OnClickListener {

// TODO: Show notification when recording
// TODO: Make Foreground service for playback
// TODO: Make Foreground service for recording
// TODO: Play selected record
// TODO: Fix playback after orientation change
// TODO: Show wave forms in records list
// TODO: Fix main screen panel
// TODO: Fix decrease size of RecyclerView or replace it by ListView
// TODO: Fix waveform adjustment
// TODO: Settings select Theme color
// TODO: Settings select Record quality
// TODO: Settings select Record stereo/mono
// TODO: Ability to import/export records
// TODO: Ability to share record
// TODO: Ability to rename record
// TODO: Welcome screen
// TODO: Guidelines
// TODO: Move Theme variables into separate class and put that class into Injector;

	public static final int REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101;
	public static final int REQ_CODE_RECORD_AUDIO = 303;
	public static final int REQ_CODE_WRITE_EXTERNAL_STORAGE = 404;

	private WaveformView waveformView;
	private TextView txtDuration;
	private TextView txtTotalDuration;
	private TextView txtRecordsCount;
	private TextView txtName;
	private ImageButton btnPlay;
	private ImageButton btnRecord;
	private ImageButton btnStop;
	private ImageButton btnRecordsList;
	private ImageButton btnSettings;
	private ScrubberView scrubberView;
	private ProgressBar progressBar;

	private boolean isForeground = false;
//	private boolean isLoaded = false;

	private MainContract.UserActionsListener presenter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(ARApplication.getAppThemeResource(getApplicationContext()));
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		waveformView = findViewById(R.id.record);
		txtDuration = findViewById(R.id.txt_duration);
		txtName = findViewById(R.id.txt_name);
		btnPlay = findViewById(R.id.btn_play);
		btnRecord = findViewById(R.id.btn_record);
		btnStop = findViewById(R.id.btn_stop);
		btnRecordsList = findViewById(R.id.btn_records_list);
		btnSettings = findViewById(R.id.btn_settings);
		progressBar = findViewById(R.id.progress);
		txtRecordsCount = findViewById(R.id.txt_records_count);
		txtTotalDuration= findViewById(R.id.txt_total_duration);

		scrubberView = findViewById(R.id.scrubber);

		btnPlay.setOnClickListener(this);
		btnRecord.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnRecordsList.setOnClickListener(this);
		btnSettings.setOnClickListener(this);

		presenter = ARApplication.getInjector().provideMainPresenter();
		showTotalRecordsDuration("0h:0m:0s");
		showRecordsCount(0);
//		presenter.bindView(this);
//		presenter.updateRecordingDir(getApplicationContext());
////		if (!isLoaded) {
//		presenter.loadActiveRecord();
////			isLoaded = true;
////		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.updateRecordingDir(getApplicationContext());
//		if (!isLoaded) {
			presenter.loadActiveRecord();
//			isLoaded = true;
//		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.btn_play:
				//This method Starts or Pause playback.
				presenter.startPlayback();
				break;
			case R.id.btn_record:
				if (checkRecordPermission()) {
					presenter.startRecording();
//					startForegroundService(new )
					Intent intent = new Intent(getApplicationContext(), RecordingService.class);
					if (isForeground) {
						intent.setAction(RecordingService.ACTION_STOP_FOREGROUND_SERVICE);
					} else {
						intent.setAction(RecordingService.ACTION_START_FOREGROUND_SERVICE);
					}
					isForeground = !isForeground;
					startService(intent);
				}
				break;
			case R.id.btn_stop:
				presenter.stopPlayback();
				presenter.stopRecording();
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
		waveformView.setVisibility(View.INVISIBLE);
		progressBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideProgress() {
		waveformView.setVisibility(View.VISIBLE);
		progressBar.setVisibility(View.GONE);
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
		btnRecord.setImageResource(R.drawable.ic_record_rec);
		btnPlay.setVisibility(View.INVISIBLE);
		btnStop.setVisibility(View.VISIBLE);
	}

	@Override
	public void showRecordingStop() {
		btnRecord.setImageResource(R.drawable.ic_record);
		presenter.loadActiveRecord();
		btnPlay.setVisibility(View.VISIBLE);
		btnStop.setVisibility(View.INVISIBLE);
	}

	@Override
	public void showPlayStart() {
		btnPlay.setImageResource(R.drawable.ic_pause);
		btnStop.setVisibility(View.VISIBLE);
		btnRecord.setEnabled(false);
	}

	@Override
	public void showPlayPause() {
		btnPlay.setImageResource(R.drawable.ic_play);
	}

	@Override
	public void showPlayStop() {
		btnPlay.setImageResource(R.drawable.ic_play);
		btnStop.setVisibility(View.INVISIBLE);
		scrubberView.setCurrentPosition(-1);
		btnRecord.setEnabled(true);
	}

	@Override
	public void showWaveForm(int[] waveForm) {
		waveformView.setWaveform(waveForm);
		btnPlay.setVisibility(View.VISIBLE);
	}

	@Override
	public void showDuration(final String duration) {
		txtDuration.setText(duration);
	}

	@Override
	public void showName(String name) {
		txtName.setText(name);
	}

	@Override
	public void showTotalRecordsDuration(String duration) {
		txtTotalDuration.setText(getResources().getString(R.string.total_duration, duration));
	}

	@Override
	public void showRecordsCount(int count) {
		txtRecordsCount.setText(getResources().getString(R.string.total_record_count, count));
	}

	@Override
	public void onPlayProgress(final long mills, final int px) {
//		runOnUiThread(new Runnable() {
//			@Override
//			public void run() {
				Timber.v("onPlayProgress: " + px);
				scrubberView.setCurrentPosition(px);
				txtDuration.setText(TimeUtils.formatTimeIntervalMinSecMills(mills));
//			}
//		});
	}

	private boolean checkRecordPermission() {
		if (presenter.isStorePublic()) {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL);
					return false;
				} else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CODE_WRITE_EXTERNAL_STORAGE);
					return false;
				} else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
					return false;
				}
			}
		} else {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED
					&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		} else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		}
	}
}
