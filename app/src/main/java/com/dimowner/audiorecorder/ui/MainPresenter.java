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

import android.content.Context;

import com.dimowner.audiorecorder.audio.player.AudioPlayerContract;
import com.dimowner.audiorecorder.audio.player.AudioPlayer;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorder;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorderContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;

	private final AudioRecorder audioRecorder;
	private final AudioPlayer audioPlayer;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;


	public MainPresenter(final Prefs prefs, final FileRepository fileRepository, final LocalRepository localRepository) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.audioRecorder = new AudioRecorder(new AudioRecorderContract.RecorderActions() {
			@Override
			public void onPrepareRecord() {
				Timber.v("onPrepareRecord");
				audioRecorder.startRecording();
			}

			@Override
			public void onStartRecord() {
				Timber.v("onStartRecord");
				view.showRecordingStart();
			}

			@Override
			public void onRecordProgress(long mills, int amplitude) {
				Timber.v("onRecordProgress time = %d, apm = %d", mills, amplitude);
				view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(mills));
			}

			@Override
			public void onStopRecord(File output) {
				Timber.v("onStopRecord file = %s", output.getAbsolutePath());
//				String prevRecord = prefs.getLastRecordedFile();
//				boolean b = fileRepository.deleteRecordFile(prevRecord);
//				Timber.v("Deletion is " + b);
//				prefs.saveLastRecordedFile(output.getAbsolutePath());
				localRepository.insertFile(output.getAbsolutePath());
				view.showRecordingStop();
				loadLastRecord();
			}

			@Override
			public void onError(Exception throwable) {
				Timber.e(throwable);
				view.showError(ErrorParser.parseException(throwable));
			}
		});

		this.audioPlayer = new AudioPlayer(new AudioPlayerContract.PlayerActions() {

			@Override
			public void onPreparePlay() {
				Timber.d("onPreparePlay");
				// Scroll to start position for the first playback time.
//				scrollToPlaybackPosition(0);
			}

			@Override
			public void onStartPlay() {
				Timber.d("onStartPlay");
//				runOnUiThread(() -> playbackView.setStartPosition(SimpleWaveformView.NO_PROGRESS));
				view.showPlayStart();
			}

			@Override
			public void onPlayProgress(long mills) {
				Timber.v("onPlayProgress: " + mills);
				view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills));
			}

			@Override
			public void onStopPlay() {
				view.showPlayStop();
				Timber.d("onStopPlay");
				view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration/1000));
			}

			@Override
			public void onPausePlay() {
				view.showPlayPause();
				Timber.d("onPausePlay");
			}

			@Override
			public void onSeek(long mills) {
				Timber.d("onSeek = " + mills);
			}

			@Override
			public void onError(Throwable throwable) {
				Timber.d("onPlayError");
			}
		});
	}

	@Override
	public void bindView(MainContract.View view) {
		this.view = view;
		this.localRepository.open();
	}

	@Override
	public void unbindView() {
		this.view = null;
		this.localRepository.close();

		audioPlayer.stopListenActions();
		audioPlayer.stop();

		audioRecorder.stopRecording();
	}

	@Override
	public void recordingClicked() {
		Timber.v("recordingClicked");
		if (audioRecorder.isRecording()) {
			audioRecorder.stopRecording();
		} else {
			try {
				audioRecorder.prepare(fileRepository.provideRecordFile().getAbsolutePath());
			} catch (CantCreateFileException e) {
				view.showError(ErrorParser.parseException(e));
			}
		}
	}

	@Override
	public void playClicked() {
		Timber.v("playClicked");
		audioPlayer.playOrPause();
	}

	@Override
	public void deleteAll() {
		Timber.v("deleteAll");
//		prefs.clearLastRecordFile();
//		loadRecords(null);
	}

	@Override
	public void loadLastRecord() {
		view.showProgress();
		final List<Record> recordList = localRepository.getAllRecords();
		if (recordList != null && recordList.size() > 0) {
			final Record record = recordList.get(recordList.size()-1);
			songDuration = record.getDuration();
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override
				public void run() {
					audioPlayer.setData(record.getPath());
					view.showWaveForm(record.getAmps());
					view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration/1000));
					view.hideProgress();
				}
			});
		} else {
			view.hideProgress();
		}

//		final String lastFile = prefs.getLastRecordedFile();
//		if (lastFile != null && !lastFile.isEmpty()) {
//			view.showProgress();
//			new Thread("SoundLoading") {
//				@Override
//				public void run() {
//					try {
//						final SoundFile soundFile = SoundFile.create(lastFile);
////						final int duration = readRecordingTrackDuration(context, lastFile);
//						songDuration = readRecordingTrackDuration(context, lastFile);
//						Timber.v("Duration = " + songDuration);
//
//						AndroidUtils.runOnUIThread(new Runnable() {
//							@Override
//							public void run() {
//								audioPlayer.setData(lastFile);
//								if (soundFile != null) {
//									view.showWaveForm(soundFile.getFrameGains());
//								}
//								view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration));
//								view.hideProgress();
//							}
//						});
//					} catch (IOException e) {
//						Timber.e(e);
//						AndroidUtils.runOnUIThread(new Runnable() {
//							@Override
//							public void run() {
//								view.showError("Couldn't load audio file!");
//								view.hideProgress();
//							}
//						});
//					}
//				}
//			}.start();
//		} else {
//			view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(0));
//			view.showWaveForm(null);
//		}
	}

	@Override
	public void updateRecordingDir(Context context) {
		File recDir;
		if (prefs.isStoreDirPublic()) {
			recDir = FileUtil.getAppDir();
		} else {
			try {
				recDir = FileUtil.getPrivateRecordsDir(context);
			} catch (FileNotFoundException e) {
				Timber.e(e);
				recDir = FileUtil.getAppDir();
			}
		}
		this.fileRepository.setRecordingDir(recDir);
	}

	@Override
	public boolean isStorePublic() {
		return prefs.isStoreDirPublic();
	}
}
