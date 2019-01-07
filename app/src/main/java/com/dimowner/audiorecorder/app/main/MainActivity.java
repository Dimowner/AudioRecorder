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

package com.dimowner.audiorecorder.app.main;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.PlaybackService;
import com.dimowner.audiorecorder.app.RecordingService;
import com.dimowner.audiorecorder.app.records.RecordsActivity;
import com.dimowner.audiorecorder.app.settings.SettingsActivity;
import com.dimowner.audiorecorder.app.widget.WaveformView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.AnimationUtil;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends Activity implements MainContract.View, View.OnClickListener {

//	TODO: show importing progress
//	TODO: Fix view == null in presenter
// TODO: Store fixed length of waveform in database
// TODO: Record name max length
// TODO: Add waveform type field in database
// TODO: Show total records count, total duration, available space in app settings
// TODO: Show Record info
// TODO: Fix waveform adjustment
// TODO: Settings select Theme waveformColorRes
// TODO: Settings select Record quality
// TODO: Settings select Record stereo/mono
// TODO: Ability to import/export records
// TODO: Ability to share record
// TODO: Ability to rename record
// TODO: Ability to delete record by swipe left
// TODO: Ability to scroll up from the bottom of the list
// TODO: Ability to search by record name in list
// TODO: Add ViewPager to swipe to Settings or Records list
// TODO: Add pagination for records list
// TODO: Welcome screen
// TODO: Guidelines
// TODO: Check how work max recording duration
// TODO: Move into 1 class same logic for Recording and Playback services
// TODO: Double-tap on waveform to rewind 10sec

	public static final int REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101;
	public static final int REQ_CODE_RECORD_AUDIO = 303;
	public static final int REQ_CODE_WRITE_EXTERNAL_STORAGE = 404;
	public static final int REQ_CODE_READ_EXTERNAL_STORAGE = 405;
	public static final int REQ_CODE_IMPORT_AUDIO = 11;

	private WaveformView waveformView;
	private TextView txtProgress;
	private TextView txtDuration;
	private TextView txtTotalDuration;
	private TextView txtRecordsCount;
	private TextView txtName;
	private ImageButton btnPlay;
	private ImageButton btnStop;
	private ImageButton btnRecord;
	private ImageButton btnShare;
	private ImageButton btnRecordsList;
	private ImageButton btnSettings;
	private ImageButton btnImport;
	private ProgressBar progressBar;
	private ProgressBar playProgress;

	private MainContract.UserActionsListener presenter;
	private ColorMap colorMap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		waveformView = findViewById(R.id.record);
		txtProgress = findViewById(R.id.txt_progress);
		txtDuration = findViewById(R.id.txt_duration);
		txtName = findViewById(R.id.txt_name);
		btnPlay = findViewById(R.id.btn_play);
		btnRecord = findViewById(R.id.btn_record);
		btnStop = findViewById(R.id.btn_stop);
		btnRecordsList = findViewById(R.id.btn_records_list);
		btnSettings = findViewById(R.id.btn_settings);
		btnShare = findViewById(R.id.btn_share);
		btnImport = findViewById(R.id.btn_import);
		progressBar = findViewById(R.id.progress);
		playProgress = findViewById(R.id.play_progress);

		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));

		txtRecordsCount = findViewById(R.id.txt_records_count);
		txtTotalDuration= findViewById(R.id.txt_total_duration);

		btnPlay.setOnClickListener(this);
		btnRecord.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnRecordsList.setOnClickListener(this);
		btnSettings.setOnClickListener(this);
		btnShare.setOnClickListener(this);
		btnImport.setOnClickListener(this);
		txtName.setOnClickListener(this);

		presenter = ARApplication.getInjector().provideMainPresenter();
		showTotalRecordsDuration("0h:0m:0s");
		showRecordsCount(0);

		waveformView.setOnSeekListener(new WaveformView.OnSeekListener() {
			@Override
			public void onSeek(int px) {
				Timber.v("onSeek: " + px);
				presenter.seekPlayback(px);
			}
			@Override
			public void onSeeking(int px) {
				if (waveformView.getWaveformLength() > 0) {
					playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / waveformView.getWaveformLength());
				}
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2((long) AndroidUtils.convertPxToMills(px)));
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.updateRecordingDir(getApplicationContext());
		presenter.loadActiveRecord();
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
				}
				break;
			case R.id.btn_stop:
				presenter.stopPlayback();
//				presenter.stopRecording();
				break;
			case R.id.btn_records_list:
				startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btn_settings:
				startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btn_share:
				String sharePath = presenter.getActiveRecordPath();
				if (sharePath != null) {
					Uri photoURI = FileProvider.getUriForFile(
							getApplicationContext(),
							getApplicationContext().getApplicationContext().getPackageName() + ".app_file_provider",
							new File(sharePath)
					);
					Intent share = new Intent(Intent.ACTION_SEND);
					share.setType("audio/*");
					share.putExtra(Intent.EXTRA_STREAM, photoURI);
					share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					startActivity(Intent.createChooser(share, getResources().getString(R.string.share_record, presenter.getActiveRecordName())));
				} else {
					Timber.e("There no active record selected!");
				}
				break;
			case R.id.btn_import:
				if (checkStoragePermission()) {
					startFileSelector();
				}
				break;
			case R.id.txt_name:
				setRecordName(presenter.getActiveRecordId(), new File(presenter.getActiveRecordPath()));
				break;
		}
	}

	private void startFileSelector() {
		Intent intent_upload = new Intent();
		intent_upload.setType("audio/*");
		intent_upload.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(intent_upload, REQ_CODE_IMPORT_AUDIO);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_CODE_IMPORT_AUDIO && resultCode == RESULT_OK){
			presenter.importAudioFile(getApplicationContext(), data.getData());
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
		btnPlay.setEnabled(false);
		waveformView.showRecording();
	}

	@Override
	public void showRecordingStop() {
		btnRecord.setImageResource(R.drawable.ic_record);
		btnPlay.setEnabled(true);
		waveformView.hideRecording();
		waveformView.clearRecordingData();
	}

	@Override
	public void askRecordingNewName(long id, File file) {
		setRecordName(id, file);
	}

	@Override
	public void onRecordingProgress(long mills, int amp) {
		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
		waveformView.addRecordAmp(amp);
	}

	@Override
	public void startRecordingService() {
		Intent intent = new Intent(getApplicationContext(), RecordingService.class);
		intent.setAction(RecordingService.ACTION_START_RECORDING_SERVICE);
		startService(intent);
	}

	@Override
	public void stopRecordingService() {
		Intent intent = new Intent(getApplicationContext(), RecordingService.class);
		intent.setAction(RecordingService.ACTION_STOP_RECORDING_SERVICE);
		startService(intent);
	}

	@Override
	public void startPlaybackService(String name) {
		Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_START_PLAYBACK_SERVICE);
		intent.putExtra(PlaybackService.EXTRAS_KEY_RECORD_NAME, name);
		startService(intent);
	}

	@Override
	public void stopPlaybackService() {
		Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_STOP_PLAYBACK_SERVICE);
		startService(intent);
	}

	@Override
	public void showPlayStart() {
		btnPlay.setImageResource(R.drawable.ic_pause);
		btnRecord.setEnabled(false);
		AnimationUtil.viewAnimationX(btnPlay, -75f, new Animator.AnimatorListener() {
			@Override public void onAnimationStart(Animator animation) { }
			@Override public void onAnimationEnd(Animator animation) {
				btnStop.setVisibility(View.VISIBLE);
			}
			@Override public void onAnimationCancel(Animator animation) { }
			@Override public void onAnimationRepeat(Animator animation) { }
		});
	}

	@Override
	public void showPlayPause() {
		btnPlay.setImageResource(R.drawable.ic_play);
	}

	@Override
	public void showPlayStop() {
		btnPlay.setImageResource(R.drawable.ic_play);
		waveformView.setPlayback(-1);
		btnRecord.setEnabled(true);
		playProgress.setProgress(0);
		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
		AnimationUtil.viewAnimationX(btnPlay, 0f, new Animator.AnimatorListener() {
			@Override public void onAnimationStart(Animator animation) { }
			@Override public void onAnimationEnd(Animator animation) {
				btnStop.setVisibility(View.GONE);
			}
			@Override public void onAnimationCancel(Animator animation) { }
			@Override public void onAnimationRepeat(Animator animation) { }
		});
	}

	@Override
	public void showWaveForm(int[] waveForm) {
		waveformView.setWaveform(waveForm);
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
	public void updateRecordingView(List<Integer> data) {
		waveformView.setRecordingData(data);
	}

	@Override
	public void onPlayProgress(final long mills, final int px, int percent) {
		Timber.v("onPlayProgress: " + px + " percent = " + percent);
		playProgress.setProgress(percent);
		waveformView.setPlayback(px);
		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
	}

	public void setRecordName(final long recordId, File file) {
		//Create dialog layout programmatically.
		LinearLayout container = new LinearLayout(getApplicationContext());
		container.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		container.setLayoutParams(containerLp);

		final EditText editText = new EditText(getApplicationContext());
		ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		editText.setLayoutParams(lp);
		editText.setTextColor(getResources().getColor(R.color.text_primary_light));
		editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_medium));

		int pad = (int) getResources().getDimension(R.dimen.spacing_normal);
		ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(editText.getLayoutParams());
		params.setMargins(pad, pad, pad, pad);
		editText.setLayoutParams(params);
		container.addView(editText);

		final String fileName = FileUtil.removeFileExtension(file.getName());
		editText.setText(fileName);

		AlertDialog alertDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.record_name)
				.setView(container)
				.setPositiveButton(R.string.btn_save, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						String newName = editText.getText().toString();
						if (!fileName.equalsIgnoreCase(newName)) {
							presenter.renameRecord(recordId, newName);
						}
						hideKeyboard();
						dialog.dismiss();
					}
				})
				.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						hideKeyboard();
						dialog.dismiss();
					}
				})
				.create();
		alertDialog.show();
		editText.requestFocus();
		editText.setSelection(editText.getText().length());
		showKeyboard();
	}

	/** Show soft keyboard for a dialog. */
	public void showKeyboard(){
		InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
	}

	/** Hide soft keyboard after a dialog. */
	public void hideKeyboard(){
		InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
	}

	private boolean checkStoragePermission() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CODE_READ_EXTERNAL_STORAGE);
				return false;
			}
		}
		return true;
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
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE && grantResults.length > 0) {
			startFileSelector();
		}
	}
}
