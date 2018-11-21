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

import com.dimowner.audiorecorder.BackgroundQueue;
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
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;

	private final AudioRecorder audioRecorder;
	private final AudioPlayer audioPlayer;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;

	private AudioPlayerContract.PlayerActions playListener;


	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
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
			public void onPauseRecord() {
				Timber.v("onPauseRecord");
			}

			@Override
			public void onRecordProgress(final long mills, int amplitude) {
				Timber.v("onRecordProgress time = %d, apm = %d", mills, amplitude);

				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(mills));
					}});
			}

			@Override
			public void onStopRecord(final File output) {
				Timber.v("onStopRecord file = %s", output.getAbsolutePath());
				view.showProgress();
				recordingTasks.postRunnable(new Runnable() {
					@Override
					public void run() {
						try {
							localRepository.insertFile(output.getAbsolutePath());
						} catch (IOException e) {
							Timber.e(e);
						}
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								view.hideProgress();
								view.showRecordingStop();
						}});
					}
				});
			}

			@Override
			public void onError(Exception throwable) {
				Timber.e(throwable);
				view.showError(ErrorParser.parseException(throwable));
			}
		});

		playListener = new AudioPlayerContract.PlayerActions() {

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
			public void onPlayProgress(final long mills) {
				Timber.v("onPlayProgress: " + mills);
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills));
					}});
			}

			@Override
			public void onStopPlay() {
				view.showPlayStop();
				Timber.d("onStopPlay");
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration / 1000));
					}});
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
		};
		this.audioPlayer = new AudioPlayer(playListener);
	}

	@Override
	public void bindView(MainContract.View view) {
		this.view = view;
		this.localRepository.open();
	}

	@Override
	public void unbindView() {
		this.localRepository.close();

		pausePlayback();
		//TODO: do not stop recording
		stopRecording();

		this.view = null;
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
		audioPlayer.release();
		audioRecorder.stopRecording();
		loadingTasks.close();
		recordingsTasks.close();
	}

	@Override
	public void startRecording() {
		Timber.v("startRecording");
		if (!audioRecorder.isRecording()) {
			try {
				audioRecorder.prepare(fileRepository.provideRecordFile().getAbsolutePath());
			} catch (CantCreateFileException e) {
				view.showError(ErrorParser.parseException(e));
			}
		} else {
			//TODO: pause recording
			audioRecorder.pauseRecording();
		}
	}

	@Override
	public void stopRecording() {
		Timber.v("stopRecording");
		if (audioRecorder.isRecording()) {
			audioRecorder.stopRecording();
		}
	}

	@Override
	public void startPlayback() {
		Timber.v("startPlayback");
		audioPlayer.playOrPause();
	}

	@Override
	public void pausePlayback() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause();
		}
	}

	@Override
	public void stopPlayback() {
		audioPlayer.stop();
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
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				//TODO: remove loading all records
				final Record record = localRepository.getLastRecord();
				final List<Long> durations = localRepository.getRecordsDurations();
				long totalDuration = 0;
				for (int i = 0; i < durations.size(); i++) {
					totalDuration +=durations.get(i);
				}
				if (record != null) {
					songDuration = record.getDuration();
					final long finalTotalDuration = totalDuration;
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							audioPlayer.setData(record.getPath());
							view.showWaveForm(record.getAmps());
							view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration / 1000));
							view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration/1000));
							view.showRecordsCount(durations.size());
							view.hideProgress();
						}
					});
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { view.hideProgress(); }});
				}
			}
		});
	}

	@Override
	public void updateRecordingDir(Context context) {
		this.fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public boolean isStorePublic() {
		return prefs.isStoreDirPublic();
	}
}
