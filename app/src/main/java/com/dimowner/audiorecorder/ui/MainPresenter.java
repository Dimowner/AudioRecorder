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

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;
	private MainContract.SimpleView simpleView;

	private final RecorderContract.Recorder audioRecorder;
	private final PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private RecorderContract.RecorderCallback recorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;
	private Record record;
	private boolean stopRecordingRemote = false;

	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								PlayerContract.Player audioPlayer,
								RecorderContract.Recorder audioRecorder,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
		this.audioPlayer = audioPlayer;
		this.audioRecorder = audioRecorder;
	}

	@Override
	public void bindView(final MainContract.View v) {
		this.view = v;
		this.localRepository.open();

		if (recorderCallback == null) {
			recorderCallback = new RecorderContract.RecorderCallback() {
				@Override
				public void onPrepareRecord() {
					Timber.v("onPrepareRecord");
					audioRecorder.startRecording();
				}

				@Override
				public void onStartRecord() {
					Timber.v("onStartRecord");
					view.showRecordingStart();
					view.startRecordingService();
				}

				@Override
				public void onPauseRecord() {
					Timber.v("onPauseRecord");
				}

				@Override
				public void onRecordProgress(final long mills, final int amplitude) {
					Timber.v("onRecordProgress time = %d, apm = %d", mills, amplitude);

					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onRecordingProgress(mills, amplitude);
							}
							if (simpleView != null) {
								simpleView.onRecordingProgress(mills, amplitude);
							}
						}
					});
				}

				@Override
				public void onStopRecord(final File output) {
//				Timber.v("onStopRecord file = %s", output.getAbsolutePath());
					if (view != null) {
						view.showProgress();
						view.stopRecordingService();
					}
					recordingsTasks.postRunnable(new Runnable() {

						long id = -1;

						@Override
						public void run() {

							try {
								id = localRepository.insertFile(output.getAbsolutePath());
								prefs.setActiveRecord(id);
							} catch (IOException e) {
								Timber.e(e);
							}
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									if (view != null) {
										view.hideProgress();
										view.showRecordingStop(id, output);
										if (!stopRecordingRemote) {
											view.askRecordingNewName(id, output);
										}
									}
									stopRecordingRemote = false;

								}
							});
						}
					});
				}

				@Override
				public void onError(AppException throwable) {
					Timber.e(throwable);
					if (view != null) {
						view.showError(ErrorParser.parseException(throwable));
					}
				}
			};
		}
		audioRecorder.setRecorderCallback(recorderCallback);

		if (playerCallback == null) {
			playerCallback = new PlayerContract.PlayerCallback() {
				@Override
				public void onPreparePlay() {
					Timber.d("onPreparePlay");
				}

				@Override
				public void onStartPlay() {
					Timber.d("onStartPlay");
					view.showPlayStart();
					view.startPlaybackService();
					if (simpleView != null) {
						simpleView.onStartPlayback();
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null) {
									view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills), (int)(1000 * mills/(songDuration/1000)));
									if (simpleView != null) {
										simpleView.onPlayProgress(mills);
									}
								}
							}});
					}
				}

				@Override
				public void onStopPlay() {
					if (view != null) {
						view.showPlayStop();
						Timber.d("onStopPlay");
						view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
						view.stopPlaybackService();
					}
				}

				@Override
				public void onPausePlay() {
					view.showPlayPause();
					Timber.d("onPausePlay");
					if (simpleView != null) {
						simpleView.onPausePlayback();
					}
				}

				@Override
				public void onSeek(long mills) {
					Timber.d("onSeek = " + mills);
				}

				@Override
				public void onError(AppException throwable) {
					Timber.e(throwable);
					if (view != null) {
						view.showError(ErrorParser.parseException(throwable));
					}
				}
			};
		}

		this.audioPlayer.addPlayerCallback(playerCallback);

		if (audioPlayer.isPlaying()) {
			view.showPlayStart();
		} else if (audioRecorder.isRecording()) {
			view.showRecordingStart();
		} else {
			view.showPlayStop();
		}

	}

	@Override
	public void unbindView() {
		this.localRepository.close();

		audioPlayer.removePlayerCallback(playerCallback);
		audioRecorder.setRecorderCallback(null);
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
	public void bindSimpleView(MainContract.SimpleView view) {
		this.simpleView = view;
	}

	@Override
	public void unbindSimpleView() {
		this.simpleView = null;
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
	public void stopRecordingRemote() {
		Timber.v("stopRecordingRemote");
		if (audioRecorder.isRecording()) {
			stopRecordingRemote = true;
			audioRecorder.stopRecording();
		}
	}

	@Override
	public void startPlayback() {
		Timber.v("startPlayback");
		if (record != null) {
			if (!audioPlayer.isPlaying()) {
				audioPlayer.setData(record.getPath());
			}
			audioPlayer.playOrPause();
		}
	}

	@Override
	public void pausePlayback() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause();
		}
	}

	@Override
	public void seekPlayback(int px) {
		audioPlayer.seek(px);
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
	public void renameRecord(final long id, final String n) {
		if (id < 0 || n == null || n.isEmpty()) {
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override public void run() { view.showError(R.string.error_failed_to_rename); }});
			return;
		}
		view.showProgress();
		final String name = FileUtil.removeUnallowedSignsFromName(n);
		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
//				TODO: This code need to be refactored!
				Record r = localRepository.getRecord((int)id);
				String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.RECORD_FILE_EXTENSION;
				File file = new File(r.getPath());
				File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { view.showError(R.string.error_file_exists); }});
				} else {
					if (fileRepository.renameFile(r.getPath(), name)) {
						record = new Record(r.getId(), nameWithExt, r.getDuration(), r.getCreated(), renamed.getAbsolutePath(), r.getAmps());
						if (localRepository.updateRecord(record)) {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									view.hideProgress();
									view.showName(name);
								}});
						} else {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() { view.showError(R.string.error_failed_to_rename); }});
							//Restore file name after fail update path in local database.
							if (renamed.exists()) {
								//Try to rename 3 times;
								if (!renamed.renameTo(file)) {
									if (!renamed.renameTo(file)) {
										renamed.renameTo(file);
									}
								}
							}
						}

					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() { view.showError(R.string.error_failed_to_rename); }});
					}
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() { view.hideProgress(); }});
			}});
	}

	@Override
	public void loadActiveRecord() {
		if (!audioRecorder.isRecording()) {
			view.showProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					record = localRepository.getRecord((int) prefs.getActiveRecord());
					final List<Long> durations = localRepository.getRecordsDurations();
					long totalDuration = 0;
					for (int i = 0; i < durations.size(); i++) {
						totalDuration += durations.get(i);
					}
					if (record != null) {
						songDuration = record.getDuration();
						final long finalTotalDuration = totalDuration;
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								view.showWaveForm(record.getAmps());
								view.showName(FileUtil.removeFileExtension(record.getName()));
								view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
								view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
								view.showRecordsCount(durations.size());
								view.hideProgress();
							}
						});
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								view.hideProgress();
							}
						});
					}
				}
			});
		} else {
			view.hideProgress();
		}
	}

	@Override
	public void updateRecordingDir(Context context) {
		this.fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public boolean isStorePublic() {
		return prefs.isStoreDirPublic();
	}

	@Override
	public String getRecordName() {
		Timber.v("getRecordName");
		if (record != null) {
			return record.getName();
		} else {
			return "Record";
		}
	}
}
