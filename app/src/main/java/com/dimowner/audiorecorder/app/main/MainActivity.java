/*
 * Copyright 2018 Dmytro Ponomarenko
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
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.IntArrayList;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.DecodeService;
import com.dimowner.audiorecorder.app.DecodeServiceListener;
import com.dimowner.audiorecorder.app.DownloadService;
import com.dimowner.audiorecorder.app.PlaybackService;
import com.dimowner.audiorecorder.app.RecordingService;
import com.dimowner.audiorecorder.app.info.ActivityInformation;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.records.RecordsActivity;
import com.dimowner.audiorecorder.app.settings.SettingsActivity;
import com.dimowner.audiorecorder.app.welcome.WelcomeActivity;
import com.dimowner.audiorecorder.app.widget.RecordingWaveformView;
import com.dimowner.audiorecorder.app.widget.WaveformViewNew;
import com.dimowner.audiorecorder.audio.AudioDecoder;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.AnimationUtil;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class MainActivity extends Activity implements MainContract.View, View.OnClickListener {

// TODO: Fix WaveForm blinking when seek
// TODO: Fix waveform when long record (there is no waveform)
// TODO: Ability to search by record name in list
// TODO: Ability to scroll up from the bottom of the list
// TODO: Stop infinite loop when pause WAV recording
// TODO: Report some sensitive error to Crashlytics manually.
//	TODO: Bluetooth micro support
//	TODO: Mp3 support
//	TODO: Add Noise gate

	public static final int REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101;
	public static final int REQ_CODE_RECORD_AUDIO = 303;
	public static final int REQ_CODE_WRITE_EXTERNAL_STORAGE = 404;
	public static final int REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT = 405;
	public static final int REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK = 406;
	public static final int REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD = 407;
	public static final int REQ_CODE_IMPORT_AUDIO = 11;

	private WaveformViewNew waveformView;
	private RecordingWaveformView recordingWaveformView;
	private TextView txtProgress;
	private TextView txtDuration;
	private TextView txtZeroTime;
	private TextView txtName;
	private TextView txtRecordInfo;
	private ImageButton btnPlay;
	private ImageButton btnStop;
	private ImageButton btnRecord;
	private ImageButton btnDelete;
	private ImageButton btnRecordingStop;
	private ImageButton btnShare;
	private ImageButton btnImport;
	private ProgressBar progressBar;
	private SeekBar playProgress;
	private LinearLayout pnlImportProgress;
	private LinearLayout pnlRecordProcessing;
	private ImageView ivPlaceholder;

	private MainContract.UserActionsListener presenter;
	private ColorMap colorMap;
	private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;

	private final ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			DecodeService.LocalBinder binder = (DecodeService.LocalBinder) service;
			DecodeService decodeService = binder.getService();
			decodeService.setDecodeListener(new DecodeServiceListener() {
				@Override
				public void onStartProcessing() {
					runOnUiThread(MainActivity.this::showRecordProcessing);
				}

				@Override
				public void onFinishProcessing() {
					runOnUiThread(() -> {
						hideRecordProcessing();
						presenter.loadActiveRecord();
					});
				}
			});
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			hideRecordProcessing();
		}

		@Override
		public void onBindingDied(ComponentName name) {
			hideRecordProcessing();
		}
	};

	private float space = 75;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, MainActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		waveformView = findViewById(R.id.record);
		recordingWaveformView = findViewById(R.id.recording_view);
		txtProgress = findViewById(R.id.txt_progress);
		txtDuration = findViewById(R.id.txt_duration);
		txtZeroTime = findViewById(R.id.txt_zero_time);
		txtName = findViewById(R.id.txt_name);
		txtRecordInfo = findViewById(R.id.txt_record_info);
		btnPlay = findViewById(R.id.btn_play);
		btnRecord = findViewById(R.id.btn_record);
		btnRecordingStop = findViewById(R.id.btn_record_stop);
		btnDelete = findViewById(R.id.btn_record_delete);
		btnStop = findViewById(R.id.btn_stop);
		ImageButton btnRecordsList = findViewById(R.id.btn_records_list);
		ImageButton btnSettings = findViewById(R.id.btn_settings);
		btnShare = findViewById(R.id.btn_share);
		btnImport = findViewById(R.id.btn_import);
		progressBar = findViewById(R.id.progress);
		playProgress = findViewById(R.id.play_progress);
		pnlImportProgress = findViewById(R.id.pnl_import_progress);
		pnlRecordProcessing = findViewById(R.id.pnl_record_processing);
		ivPlaceholder = findViewById(R.id.placeholder);
		ivPlaceholder.setImageResource(R.drawable.waveform);

		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));

		btnDelete.setVisibility(View.INVISIBLE);
		btnDelete.setEnabled(false);
		btnRecordingStop.setVisibility(View.INVISIBLE);
		btnRecordingStop.setEnabled(false);

		btnPlay.setOnClickListener(this);
		btnRecord.setOnClickListener(this);
		btnRecordingStop.setOnClickListener(this);
		btnDelete.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnRecordsList.setOnClickListener(this);
		btnSettings.setOnClickListener(this);
		btnShare.setOnClickListener(this);
		btnImport.setOnClickListener(this);
		txtName.setOnClickListener(this);
		space = getResources().getDimension(R.dimen.spacing_xnormal);

		playProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					int val = (int)AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000);
					waveformView.seekPx(val);
					//TODO: Find a better way to convert px to mills here
					presenter.seekPlayback(waveformView.pxToMill(val));
				}
			}

			@Override public void onStartTrackingTouch(SeekBar seekBar) {
				presenter.disablePlaybackProgressListener();
			}

			@Override public void onStopTrackingTouch(SeekBar seekBar) {
				presenter.enablePlaybackProgressListener();
			}
		});

		presenter = ARApplication.getInjector().provideMainPresenter();

		waveformView.setOnSeekListener(new WaveformViewNew.OnSeekListener() {
			@Override
			public void onStartSeek() {
				presenter.disablePlaybackProgressListener();
			}

			@Override
			public void onSeek(int px, long mills) {
				presenter.enablePlaybackProgressListener();
				//TODO: Find a better way to convert px to mills here
				presenter.seekPlayback(waveformView.pxToMill(px));

				int length = waveformView.getWaveformLength();
				if (length > 0) {
					playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / length);
				}
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
			}
			@Override
			public void onSeeking(int px, long mills) {
				int length = waveformView.getWaveformLength();
				if (length > 0) {
					playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / length);
				}
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
			}
		});
		onThemeColorChangeListener = colorKey -> {
			setTheme(colorMap.getAppThemeResource());
			recreate();
		};
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);

		//Check start recording shortcut
		if ("android.intent.action.ACTION_RUN".equals(getIntent().getAction())) {
			if (checkRecordPermission2()) {
				if (checkStoragePermission2()) {
					//Start or stop recording
					presenter.startRecording(getApplicationContext());
				}
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			//This is needed for scoped storage support
			presenter.storeInPrivateDir(getApplicationContext());
		}
		presenter.checkFirstRun();
		presenter.setAudioRecorder(ARApplication.getInjector().provideAudioRecorder());
		presenter.updateRecordingDir(getApplicationContext());
		presenter.loadActiveRecord();

		Intent intent = new Intent(this, DecodeService.class);
		bindService(intent, connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unbindService(connection);
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener);
	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.btn_play) {
			//This method Starts or Pause playback.
			if (FileUtil.isFileInExternalStorage(getApplicationContext(), presenter.getActiveRecordPath())) {
				if (checkStoragePermissionPlayback()) {
					presenter.startPlayback();
				}
			} else {
				presenter.startPlayback();
			}
		} else if (id == R.id.btn_record) {
			if (checkRecordPermission2()) {
				if (checkStoragePermission2()) {
					//Start or stop recording
					presenter.startRecording(getApplicationContext());
				}
			}
		} else if (id == R.id.btn_record_stop) {
			presenter.stopRecording(false);
		} else if (id == R.id.btn_record_delete) {
			presenter.cancelRecording();
		} else if (id == R.id.btn_stop) {
			presenter.stopPlayback();
		} else if (id == R.id.btn_records_list) {
			startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
		} else if (id == R.id.btn_settings) {
			startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
		} else if (id == R.id.btn_share) {
			showMenu(view);
		} else if (id == R.id.btn_import) {
			if (checkStoragePermissionImport()) {
				startFileSelector();
			}
		} else if (id == R.id.txt_name) {
			presenter.onRenameRecordClick();
		}
	}

	private void startFileSelector() {
		Intent intent_upload = new Intent();
		intent_upload.setType("audio/*");
		intent_upload.addCategory(Intent.CATEGORY_OPENABLE);
//		intent_upload.setAction(Intent.ACTION_GET_CONTENT);
		intent_upload.setAction(Intent.ACTION_OPEN_DOCUMENT);
		try {
			startActivityForResult(intent_upload, REQ_CODE_IMPORT_AUDIO);
		} catch (ActivityNotFoundException e) {
			Timber.e(e);
			showError(R.string.cant_import_files);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_CODE_IMPORT_AUDIO && resultCode == RESULT_OK){
			presenter.importAudioFile(getApplicationContext(), data.getData());
		}
	}

	@Override
	public void keepScreenOn(boolean on) {
		if (on) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	@Override
	public void showProgress() {
		progressBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideProgress() {
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
	public void showMessage(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showRecordingStart() {
		txtName.setClickable(false);
		txtName.setFocusable(false);
		txtName.setCompoundDrawables(null, null, null, null);
		txtName.setVisibility(View.VISIBLE);
		txtName.setText(R.string.recording_progress);
		txtZeroTime.setVisibility(View.INVISIBLE);
		txtDuration.setVisibility(View.INVISIBLE);
		btnRecord.setImageResource(R.drawable.ic_pause_circle_filled);
		btnPlay.setEnabled(false);
		btnImport.setEnabled(false);
		btnShare.setEnabled(false);
		btnPlay.setVisibility(View.GONE);
		btnImport.setVisibility(View.GONE);
		btnShare.setVisibility(View.GONE);
		btnDelete.setVisibility(View.VISIBLE);
		btnDelete.setEnabled(true);
		btnRecordingStop.setVisibility(View.VISIBLE);
		btnRecordingStop.setEnabled(true);
		playProgress.setProgress(0);
		playProgress.setEnabled(false);
		txtDuration.setText(R.string.zero_time);
		waveformView.setVisibility(View.GONE);
		recordingWaveformView.setVisibility(View.VISIBLE);
		ivPlaceholder.setVisibility(View.GONE);
	}

	@Override
	public void showRecordingStop() {
		txtName.setClickable(true);
		txtName.setFocusable(true);
//		txtName.setText("");
		txtZeroTime.setVisibility(View.VISIBLE);
		txtDuration.setVisibility(View.VISIBLE);
		txtName.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_pencil_small), null);
//		txtName.setVisibility(View.INVISIBLE);
		btnRecord.setImageResource(R.drawable.ic_record);
		btnPlay.setEnabled(true);
		btnImport.setEnabled(true);
		btnShare.setEnabled(true);
		btnPlay.setVisibility(View.VISIBLE);
		btnImport.setVisibility(View.VISIBLE);
		btnShare.setVisibility(View.VISIBLE);
		playProgress.setEnabled(true);
		btnDelete.setVisibility(View.INVISIBLE);
		btnDelete.setEnabled(false);
		btnRecordingStop.setVisibility(View.INVISIBLE);
		btnRecordingStop.setEnabled(false);
		waveformView.setVisibility(View.VISIBLE);
		recordingWaveformView.setVisibility(View.GONE);
		recordingWaveformView.reset();
		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));
	}

	@Override
	public void showRecordingPause() {
		txtName.setClickable(false);
		txtName.setFocusable(false);
		txtName.setCompoundDrawables(null, null, null, null);
		txtName.setText(R.string.recording_paused);
		txtName.setVisibility(View.VISIBLE);
		btnPlay.setEnabled(false);
		btnImport.setEnabled(false);
		btnShare.setEnabled(false);
		btnPlay.setVisibility(View.GONE);
		btnImport.setVisibility(View.GONE);
		btnShare.setVisibility(View.GONE);
		btnRecord.setImageResource(R.drawable.ic_record_rec);
		btnDelete.setVisibility(View.VISIBLE);
		btnDelete.setEnabled(true);
		btnRecordingStop.setVisibility(View.VISIBLE);
		btnRecordingStop.setEnabled(true);
		playProgress.setEnabled(false);
		ivPlaceholder.setVisibility(View.GONE);
		recordingWaveformView.setVisibility(View.VISIBLE);
	}

	@Override
	public void showRecordingResume() {
		txtName.setClickable(false);
		txtName.setFocusable(false);
		txtName.setCompoundDrawables(null, null, null, null);
		txtName.setVisibility(View.VISIBLE);
		txtName.setText(R.string.recording_progress);
		txtZeroTime.setVisibility(View.INVISIBLE);
		txtDuration.setVisibility(View.INVISIBLE);
		btnRecord.setImageResource(R.drawable.ic_pause_circle_filled);
		btnPlay.setEnabled(false);
		btnImport.setEnabled(false);
		btnShare.setEnabled(false);
		btnPlay.setVisibility(View.GONE);
		btnImport.setVisibility(View.GONE);
		btnShare.setVisibility(View.GONE);
		btnDelete.setVisibility(View.VISIBLE);
		btnDelete.setEnabled(true);
		btnRecordingStop.setVisibility(View.VISIBLE);
		btnRecordingStop.setEnabled(true);
		playProgress.setProgress(0);
		playProgress.setEnabled(false);
		txtDuration.setText(R.string.zero_time);
		ivPlaceholder.setVisibility(View.GONE);
	}

	@Override
	public void askRecordingNewName(long id, File file,  boolean showCheckbox) {
		setRecordName(id, file, showCheckbox);
	}

	@Override
	public void onRecordingProgress(long mills, int amp) {
		runOnUiThread(() ->{
			txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
			recordingWaveformView.addRecordAmp(amp, mills);
		});
	}

	@Override
	public void startWelcomeScreen() {
		startActivity(WelcomeActivity.getStartIntent(getApplicationContext()));
		finish();
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
	public void startPlaybackService(final String name) {
		PlaybackService.startServiceForeground(getApplicationContext(), name);
	}

	@Override
	public void showPlayStart(boolean animate) {
		btnRecord.setEnabled(false);
		if (animate) {
			AnimationUtil.viewAnimationX(btnPlay, -space, new Animator.AnimatorListener() {
				@Override public void onAnimationStart(Animator animation) { }
				@Override public void onAnimationEnd(Animator animation) {
					btnStop.setVisibility(View.VISIBLE);
					btnPlay.setImageResource(R.drawable.ic_pause);
				}
				@Override public void onAnimationCancel(Animator animation) { }
				@Override public void onAnimationRepeat(Animator animation) { }
			});
		} else {
			btnPlay.setTranslationX(-space);
			btnStop.setVisibility(View.VISIBLE);
			btnPlay.setImageResource(R.drawable.ic_pause);
		}
	}

	@Override
	public void showPlayPause() {
		btnStop.setVisibility(View.VISIBLE);
		btnPlay.setTranslationX(-space);
		btnPlay.setImageResource(R.drawable.ic_play);
	}

	@Override
	public void showPlayStop() {
		btnPlay.setImageResource(R.drawable.ic_play);
		waveformView.moveToStart();
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
	public void showWaveForm(int[] waveForm, long duration, long playbackMills) {
		if (waveForm.length > 0) {
			btnPlay.setVisibility(View.VISIBLE);
			txtDuration.setVisibility(View.VISIBLE);
			txtZeroTime.setVisibility(View.VISIBLE);
			ivPlaceholder.setVisibility(View.GONE);
			waveformView.setVisibility(View.VISIBLE);
		} else {
			btnPlay.setVisibility(View.INVISIBLE);
			txtDuration.setVisibility(View.INVISIBLE);
			txtZeroTime.setVisibility(View.INVISIBLE);
			ivPlaceholder.setVisibility(View.VISIBLE);
			waveformView.setVisibility(View.INVISIBLE);
		}
		waveformView.setWaveform(waveForm, duration/1000, playbackMills);
	}

	@Override
	public void waveFormToStart() {
		waveformView.seekPx(0);
	}

	@Override
	public void showDuration(final String duration) {
		txtDuration.setText(duration);
	}

	@Override
	public void showRecordingProgress(String progress) {
		txtProgress.setText(progress);
	}

	@Override
	public void showName(String name) {
		if (name == null || name.isEmpty()) {
			txtName.setVisibility(View.INVISIBLE);
		} else {
			txtName.setVisibility(View.VISIBLE);
		}
		txtName.setText(name);
	}

	@Override
	public void showInformation(String info) {
		runOnUiThread(() -> txtRecordInfo.setText(info));
	}

	@Override
	public void decodeRecord(int id) {
		DecodeService.Companion.startNotification(getApplicationContext(), id);
	}

	@Override
	public void askDeleteRecord(String name) {
		AndroidUtils.showDialogYesNo(
				MainActivity.this,
				R.drawable.ic_delete_forever_dark,
				getString(R.string.warning),
				getString(R.string.delete_record, name),
				v -> presenter.deleteActiveRecord(false)
		);
	}

	@Override
	public void askDeleteRecordForever() {
		AndroidUtils.showDialogYesNo(
				MainActivity.this,
				R.drawable.ic_delete_forever_dark,
				getString(R.string.warning),
				getString(R.string.delete_this_record),
				v -> presenter.stopRecording(true)
		);
	}

	@Override
	public void showRecordInfo(RecordInfo info) {
		startActivity(ActivityInformation.getStartIntent(getApplicationContext(), info));
	}

	@Override
	public void updateRecordingView(IntArrayList data, long durationMills) {
		if (data != null) {
			recordingWaveformView.setRecordingData(data, durationMills);
		}
	}

	@Override
	public void showRecordsLostMessage(List<Record> list) {
		AndroidUtils.showLostRecordsDialog(this, list);
	}

	@Override
	public void shareRecord(Record record) {
		AndroidUtils.shareAudioFile(getApplicationContext(), record.getPath(), record.getName(), record.getFormat());
	}

	@Override
	public void openFile(Record record) {
		AndroidUtils.openAudioFile(getApplicationContext(), record.getPath(), record.getName());
	}

	@Override
	public void downloadRecord(Record record) {
		if (checkStoragePermissionDownload()) {
			DownloadService.startNotification(getApplicationContext(), record.getPath());
		}
	}

	@Override
	public void onPlayProgress(final long mills, int percent) {
		playProgress.setProgress(percent);
		waveformView.setPlayback(mills);
		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
	}

	@Override
	public void showImportStart() {
		btnImport.setVisibility(View.INVISIBLE);
		pnlImportProgress.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideImportProgress() {
		pnlImportProgress.setVisibility(View.INVISIBLE);
		btnImport.setVisibility(View.VISIBLE);
	}

	@Override
	public void showOptionsMenu() {
		btnShare.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideOptionsMenu() {
		btnShare.setVisibility(View.INVISIBLE);
	}

	@Override
	public void showRecordProcessing() {
		pnlRecordProcessing.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideRecordProcessing() {
		pnlRecordProcessing.setVisibility(View.INVISIBLE);
	}

	private void showMenu(View v) {
		PopupMenu popup = new PopupMenu(v.getContext(), v);
		popup.setOnMenuItemClickListener(item -> {
			int id = item.getItemId();
			if (id == R.id.menu_share) {
				presenter.onShareRecordClick();
			} else if (id == R.id.menu_info) {
				presenter.onRecordInfo();
			} else if (id == R.id.menu_rename) {
				presenter.onRenameRecordClick();
			} else if (id == R.id.menu_open_with) {
				presenter.onOpenFileClick();
			} else if (id == R.id.menu_download) {
				presenter.onDownloadClick();
			} else if (id == R.id.menu_delete) {
				presenter.onDeleteClick();
			}
			return false;
		});
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.menu_more, popup.getMenu());
		AndroidUtils.insertMenuItemIcons(v.getContext(), popup);
		popup.show();
	}

	public void setRecordName(final long recordId, File file, boolean showCheckbox) {
		final RecordInfo info = AudioDecoder.readRecordInfo(file);
		AndroidUtils.showRenameDialog(this, info.getName(), showCheckbox, newName -> {
			if (!info.getName().equalsIgnoreCase(newName)) {
				presenter.renameRecord(recordId, newName, info.getFormat());
			}
		}, v -> {}, (buttonView, isChecked) -> presenter.setAskToRename(!isChecked));
	}

	private boolean checkStoragePermissionDownload() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						new String[]{
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE},
						REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD);
				return false;
			}
		}
		return true;
	}

	private boolean checkStoragePermissionImport() {
		if (presenter.isStorePublic()) {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(
							new String[]{
									Manifest.permission.WRITE_EXTERNAL_STORAGE,
									Manifest.permission.READ_EXTERNAL_STORAGE},
							REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT);
					return false;
				}
			}
		}
		return true;
	}

	private boolean checkStoragePermissionPlayback() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						new String[]{
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE},
						REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK);
				return false;
			}
		}
		return true;
	}

	private boolean checkRecordPermission2() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
				return false;
			}
		}
		return true;
	}

	private boolean checkStoragePermission2() {
		if (presenter.isStorePublic()) {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					AndroidUtils.showDialog(this, R.string.warning, R.string.need_write_permission,
							v -> requestPermissions(
									new String[]{
											Manifest.permission.WRITE_EXTERNAL_STORAGE,
											Manifest.permission.READ_EXTERNAL_STORAGE},
									REQ_CODE_WRITE_EXTERNAL_STORAGE), null
//							new View.OnClickListener() {
//								@Override
//								public void onClick(View v) {
//									presenter.setStoragePrivate(getApplicationContext());
//									presenter.startRecording();
//								}
//							}
					);
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED
					&& grantResults[1] == PackageManager.PERMISSION_GRANTED
					&& grantResults[2] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording(getApplicationContext());
		} else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (checkStoragePermission2()) {
				presenter.startRecording(getApplicationContext());
			}
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			if (checkRecordPermission2()) {
				presenter.startRecording(getApplicationContext());
			}
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_IMPORT && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			startFileSelector();
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_DOWNLOAD && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.onDownloadClick();
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_PLAYBACK && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.startPlayback();
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0
				&& (grantResults[0] == PackageManager.PERMISSION_DENIED
				|| grantResults[1] == PackageManager.PERMISSION_DENIED)) {
			presenter.setStoragePrivate(getApplicationContext());
			presenter.startRecording(getApplicationContext());
		}
	}
}
