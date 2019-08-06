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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
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

// TODO: Fix WaveForm blinking when seek
// TODO: Show Record info
// TODO: Ability to delete record by swipe left
// TODO: Ability to scroll up from the bottom of the list
// TODO: Ability to search by record name in list
// TODO: Welcome screen
// TODO: Guidelines
// TODO: Check how work max recording duration
// TODO: Add scroll animation to start when stop playback

	public static final int REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL = 101;
	public static final int REQ_CODE_RECORD_AUDIO = 303;
	public static final int REQ_CODE_WRITE_EXTERNAL_STORAGE = 404;
	public static final int REQ_CODE_READ_EXTERNAL_STORAGE = 405;
	public static final int REQ_CODE_IMPORT_AUDIO = 11;

	private WaveformView waveformView;
	private TextView txtProgress;
	private TextView txtDuration;
	private TextView txtZeroTime;
	private TextView txtName;
	private ImageButton btnPlay;
	private ImageButton btnStop;
	private ImageButton btnRecord;
	private ImageButton btnShare;
	private ImageButton btnRecordsList;
	private ImageButton btnSettings;
	private ImageButton btnImport;
	private ProgressBar progressBar;
	private SeekBar playProgress;
	private LinearLayout pnlImportProgress;
	private LinearLayout pnlRecordProcessing;

	private MainContract.UserActionsListener presenter;
	private ServiceConnection serviceConnection;
	private PlaybackService playbackService;
	private boolean isBound = false;
	private ColorMap colorMap;
	private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		waveformView = findViewById(R.id.record);
		txtProgress = findViewById(R.id.txt_progress);
		txtDuration = findViewById(R.id.txt_duration);
		txtZeroTime = findViewById(R.id.txt_zero_time);
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
		pnlImportProgress = findViewById(R.id.pnl_import_progress);
		pnlRecordProcessing = findViewById(R.id.pnl_record_processing);

		txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(0));

		btnPlay.setOnClickListener(this);
		btnRecord.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnRecordsList.setOnClickListener(this);
		btnSettings.setOnClickListener(this);
		btnShare.setOnClickListener(this);
		btnImport.setOnClickListener(this);
		txtName.setOnClickListener(this);
		playProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					int val = (int)AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000);
					waveformView.seekPx(val);
					presenter.seekPlayback(val);
				}
			}
			@Override public void onStartTrackingTouch(SeekBar seekBar) { }
			@Override public void onStopTrackingTouch(SeekBar seekBar) { }
		});

		presenter = ARApplication.getInjector().provideMainPresenter();
		presenter.executeFirstRun();

		waveformView.setOnSeekListener(new WaveformView.OnSeekListener() {
			@Override
			public void onSeek(int px) {
				presenter.seekPlayback(px);
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
		onThemeColorChangeListener = new ColorMap.OnThemeColorChangeListener() {
			@Override
			public void onThemeColorChange(int pos) {
				setTheme(colorMap.getAppThemeResource());
				recreate();
			}
		};
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.setAudioRecorder(ARApplication.getInjector().provideAudioRecorder());
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
	protected void onDestroy() {
		super.onDestroy();
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener);
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
					//Start or stop recording
					presenter.startRecording();
				}
				break;
			case R.id.btn_stop:
				presenter.stopPlayback();
				break;
			case R.id.btn_records_list:
				startActivity(RecordsActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btn_settings:
				startActivity(SettingsActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btn_share:
//				AndroidUtils.shareAudioFile(getApplicationContext(), presenter.getActiveRecordPath(), presenter.getActiveRecordName());
				showMenu(view);
				break;
			case R.id.btn_import:
				if (checkStoragePermission()) {
					startFileSelector();
				}
				break;
			case R.id.txt_name:
				if (presenter.getActiveRecordId() != -1) {
					setRecordName(presenter.getActiveRecordId(), new File(presenter.getActiveRecordPath()));
				}
				break;
		}
	}

	private void startFileSelector() {
		Intent intent_upload = new Intent();
		intent_upload.setType("audio/*");
		intent_upload.addCategory(Intent.CATEGORY_OPENABLE);
//		intent_upload.setAction(Intent.ACTION_GET_CONTENT);
		intent_upload.setAction(Intent.ACTION_OPEN_DOCUMENT);
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
	public void keepScreenOn(boolean on) {
		if (on) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
	public void showMessage(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showRecordingStart() {
		btnRecord.setImageResource(R.drawable.ic_record_rec);
		btnPlay.setEnabled(false);
		btnImport.setEnabled(false);
		btnShare.setEnabled(false);
		playProgress.setProgress(0);
		playProgress.setEnabled(false);
		txtDuration.setText(R.string.zero_time);
		waveformView.showRecording();
	}

	@Override
	public void showRecordingStop() {
		btnRecord.setImageResource(R.drawable.ic_record);
		btnPlay.setEnabled(true);
		btnImport.setEnabled(true);
		btnShare.setEnabled(true);
		playProgress.setEnabled(true);
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
	public void startPlaybackService(final String name) {
		Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
		startService(intent);
		serviceConnection = new ServiceConnection() {
			@Override public void onServiceConnected(ComponentName n, IBinder service) {
				PlaybackService.PlaybackBinder pb = (PlaybackService.PlaybackBinder) service;
				playbackService = pb.getService();
				playbackService.startForeground(name);
				isBound = true;
			}
			@Override public void onServiceDisconnected(ComponentName n) {
				Timber.v("onServiceDisconnected name: %s", n);
				isBound = false;
			}
		};

		bindService(intent, serviceConnection, Context.BIND_IMPORTANT);
	}

	@Override
	public void stopPlaybackService() {
		if (isBound && serviceConnection != null) {
			unbindService(serviceConnection);
			isBound = false;
		}
	}

	@Override
	public void showPlayStart(boolean animate) {
		btnRecord.setEnabled(false);
		if (animate) {
			AnimationUtil.viewAnimationX(btnPlay, -75f, new Animator.AnimatorListener() {
				@Override public void onAnimationStart(Animator animation) { }
				@Override public void onAnimationEnd(Animator animation) {
					btnStop.setVisibility(View.VISIBLE);
					btnPlay.setImageResource(R.drawable.ic_pause);
				}
				@Override public void onAnimationCancel(Animator animation) { }
				@Override public void onAnimationRepeat(Animator animation) { }
			});
		} else {
			btnPlay.setTranslationX(-75f);
			btnStop.setVisibility(View.VISIBLE);
			btnPlay.setImageResource(R.drawable.ic_pause);
		}
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
	public void showWaveForm(int[] waveForm, long duration) {
		if (waveForm.length > 0) {
			btnPlay.setVisibility(View.VISIBLE);
			txtDuration.setVisibility(View.VISIBLE);
			txtZeroTime.setVisibility(View.VISIBLE);
		} else {
			btnPlay.setVisibility(View.INVISIBLE);
			txtDuration.setVisibility(View.INVISIBLE);
			txtZeroTime.setVisibility(View.INVISIBLE);
		}
		waveformView.setWaveform(waveForm);
		waveformView.setPxPerSecond(AndroidUtils.dpToPx(ARApplication.getDpPerSecond((float)duration/1000000f)));
	}

	@Override
	public void showDuration(final String duration) {
		txtDuration.setText(duration);
	}

	@Override
	public void showName(String name) {
		if (name == null || name.isEmpty()) {
			txtName.setVisibility(View.INVISIBLE);
		} else if (txtName.getVisibility() == View.INVISIBLE) {
			txtName.setVisibility(View.VISIBLE);
		}
		txtName.setText(name);
	}

	@Override
	public void updateRecordingView(List<Integer> data) {
		waveformView.setRecordingData(data);
	}

	@Override
	public void onPlayProgress(final long mills, final int px, int percent) {
		playProgress.setProgress(percent);
		waveformView.setPlayback(px);
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
	public void showRecordProcessing() {
		pnlRecordProcessing.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideRecordProcessing() {
		pnlRecordProcessing.setVisibility(View.INVISIBLE);
	}

	private void showMenu(View v) {
		PopupMenu popup = new PopupMenu(v.getContext(), v);
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
					case R.id.menu_share:
						AndroidUtils.shareAudioFile(getApplicationContext(), presenter.getActiveRecordPath(), presenter.getActiveRecordName());
						break;
//					case R.id.menu_info:
//						break;
					case R.id.menu_rename:
						setRecordName(presenter.getActiveRecordId(), new File(presenter.getActiveRecordPath()));
						break;
					case R.id.menu_open_with:
						AndroidUtils.openAudioFile(getApplicationContext(), presenter.getActiveRecordPath(), presenter.getActiveRecordName());
						break;
//					case R.id.menu_download:
//						presenter.copyToDownloads(item.getPath(), item.getName());
//						break;
					case R.id.menu_delete:
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle(R.string.warning)
								.setIcon(R.drawable.ic_delete_forever)
								.setMessage(R.string.delete_record)
								.setCancelable(false)
								.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										presenter.deleteActiveRecord();
										dialog.dismiss();
									}
								})
								.setNegativeButton(R.string.btn_no,
										new DialogInterface.OnClickListener() {
											public void onClick(DialogInterface dialog, int id) {
												dialog.dismiss();
											}
										});
						AlertDialog alert = builder.create();
						alert.show();
						break;
				}
				return false;
			}
		});
		MenuInflater inflater = popup.getMenuInflater();
		inflater.inflate(R.menu.menu_more, popup.getMenu());
		AndroidUtils.insertMenuItemIcons(v.getContext(), popup);
		popup.show();
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
		editText.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override public void afterTextChanged(Editable s) {
				if (s.length() > AppConstants.MAX_RECORD_NAME_LENGTH) {
					s.delete(s.length() - 1, s.length());
				}
			}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
		});
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
		if (presenter.isStorePublic()) {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_READ_EXTERNAL_STORAGE);
					return false;
				}
			}
		}
		return true;
	}

	private boolean checkRecordPermission() {
		if (presenter.isStorePublic()) {
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
							Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL);
					return false;
				} else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
						&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_CODE_WRITE_EXTERNAL_STORAGE);
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
	public void onRequestPermissionsResult(int requestCode,  @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQ_CODE_REC_AUDIO_AND_WRITE_EXTERNAL && grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED
					&& grantResults[1] == PackageManager.PERMISSION_GRANTED
					&& grantResults[2] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		} else if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.startRecording();
		} else if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			startFileSelector();
		} else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE
				&& grantResults[0] == PackageManager.PERMISSION_DENIED) {
			Timber.v("WRITE_EXTERNAL_STORAGE DENIED");
			//TODO: show message that app cant write into private directory and record will be written in private
		}
	}
}
