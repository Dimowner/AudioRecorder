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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
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
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;
	private AppRecorder appRecorder;
	private final PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;
	private Record record;

	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								PlayerContract.Player audioPlayer,
								AppRecorder appRecorder,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
		this.audioPlayer = audioPlayer;
		this.appRecorder = appRecorder;
	}

	@Override
	public void bindView(final MainContract.View v) {
		this.view = v;
		this.localRepository.open();

		if (appRecorder.isRecording()) {
			view.updateRecordingView(appRecorder.getRecordingData());
		} else {
			view.showRecordingStop();
		}

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {
				@Override
				public void onRecordingStarted() {
					Timber.v("onStartRecord");
					view.showRecordingStart();
					view.startRecordingService();
				}

				@Override
				public void onRecordingPaused() {
				}

				@Override
				public void onRecordProcessing() {
					view.showProgress();
				}

				@Override
				public void onRecordingStopped(long id, File file) {
					if (view != null) {
						view.stopRecordingService();
						view.hideProgress();
						view.showRecordingStop();
						loadActiveRecord();
						view.askRecordingNewName(id, file);
					}
				}

				@Override
				public void onRecordingProgress(final long mills, final int amp) {
					Timber.v("onRecordProgress time = %d, apm = %d", mills, amp);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onRecordingProgress(mills, amp);
							}
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
		appRecorder.addRecordingCallback(appRecorderCallback);

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
					view.startPlaybackService(record.getName());
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null) {
									view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills), (int)(1000 * mills/(songDuration/1000)));
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
		} else {
			view.showPlayStop();
		}
	}

	@Override
	public void unbindView() {
		this.localRepository.close();
		audioPlayer.removePlayerCallback(playerCallback);
		appRecorder.removeRecordingCallback(appRecorderCallback);
		this.view = null;
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
		audioPlayer.release();
		appRecorder.release();
		loadingTasks.close();
		recordingsTasks.close();
	}

	@Override
	public void startRecording() {
		Timber.v("startRecording");
		if (audioPlayer.isPlaying()) {
			audioPlayer.stop();
		}
		if (!appRecorder.isRecording()) {
			try {
				appRecorder.startRecording(fileRepository.provideRecordFile().getAbsolutePath());
			} catch (CantCreateFileException e) {
				view.showError(ErrorParser.parseException(e));
			}
		} else {
			appRecorder.pauseRecording();
		}
	}

	@Override
	public void stopRecording() {
		Timber.v("stopRecording");
		if (appRecorder.isRecording()) {
			appRecorder.stopRecording();
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
						record = new Record(r.getId(), nameWithExt, r.getDuration(), r.getCreated(),
								r.getAdded(), renamed.getAbsolutePath(), r.isBookmarked(), r.getAmps());
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
							view.showWaveForm(new int[] {});
							view.showName("");
							view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
							view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(0));
							view.hideProgress();
						}
					});
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

	@Override
	public String getActiveRecordPath() {
		if (record != null) {
			return record.getPath();
		} else {
			return null;
		}
	}

	@Override
	public String getActiveRecordName() {
		if (record != null) {
			return record.getName();
		} else {
			return null;
		}
	}

	@Override
	public int getActiveRecordId() {
		if (record != null) {
			return record.getId();
		} else {
			return -1;
		}
	}

	@Override
	public void importAudioFile(final Context context, final Uri uri) {
//		TODO: Show import progress
//		view.showProgress();
		recordingsTasks.postRunnable(new Runnable() {
			long id = -1;

			@Override
			public void run() {
				try {
					ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
					FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
					String name = extractFileName(context, uri);

					File newFile = fileRepository.provideRecordFile(name);
					if (FileUtil.copyFile(fileDescriptor, newFile)) {
						Timber.v("Copy file %s succeed!", newFile.getAbsolutePath());
						id = localRepository.insertFile(newFile.getAbsolutePath());
						prefs.setActiveRecord(id);
					}
				} catch (IOException e) {
					Timber.e(e);
					view.showError(R.string.error_unable_to_read_sound_file);
				} catch (CantCreateFileException ex) {
					view.showError(ErrorParser.parseException(ex));
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						loadActiveRecord();
					}
				});
			}
		});
	}

	private String extractFileName(Context context, Uri uri) {
		Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, null);
		try {
			if (cursor != null && cursor.moveToFirst()) {
				String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
//				TODO: find a better way to extract file extension.
				if (!name.contains(".")) {
					return name + ".m4a";
				}
				return name;
			}
		} finally {
			cursor.close();
		}
		return null;
	}
}
