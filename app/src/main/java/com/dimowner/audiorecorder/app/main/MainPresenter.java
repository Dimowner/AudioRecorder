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

import com.dimowner.audiorecorder.ARApplication;
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

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;
	private AppRecorder appRecorder;
	private final PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final BackgroundQueue importTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private Record record;
	private boolean isProcessing = false;

	/** Flag true defines that presenter called to show import progress when view was not bind.
	 * And after view bind we need to show import progress.*/
	private boolean showImportProgress = false;

	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								PlayerContract.Player audioPlayer,
								AppRecorder appRecorder,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks,
								final BackgroundQueue importTasks) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
		this.importTasks = importTasks;
		this.audioPlayer = audioPlayer;
		this.appRecorder = appRecorder;
	}

	@Override
	public void bindView(final MainContract.View v) {
		this.view = v;
		if (showImportProgress) {
			view.showImportStart();
			showImportProgress = false;
		}
		this.localRepository.open();

		if (appRecorder.isRecording()) {
			view.showRecordingStart();
			view.keepScreenOn(prefs.isKeepScreenOn());
			view.updateRecordingView(appRecorder.getRecordingData());
		} else {
			view.showRecordingStop();
			view.keepScreenOn(false);
		}
		if (appRecorder.isProcessing()) {
			view.showRecordProcessing();
		} else {
			view.hideRecordProcessing();
		}

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {
				@Override
				public void onRecordingStarted() {
					Timber.v("onStartRecord");
					view.showRecordingStart();
					view.keepScreenOn(prefs.isKeepScreenOn());
					view.showName("");
					view.startRecordingService();
				}

				@Override
				public void onRecordingPaused() {
					view.keepScreenOn(false);
				}

				@Override
				public void onRecordProcessing() {
					view.showProgress();
					view.showRecordProcessing();
				}

				@Override
				public void onRecordFinishProcessing() {
					view.hideRecordProcessing();
					loadActiveRecord();
				}

				@Override
				public void onRecordingStopped(long id, File file) {
					if (view != null) {
						view.keepScreenOn(false);
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
						view.showRecordingStop();
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
					view.showPlayStart(true);
					view.startPlaybackService(record.getName());
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null) {
									view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills,
											AndroidUtils.dpToPx(dpPerSecond)), (int)(1000 * mills/(songDuration/1000)));
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
			view.showPlayStart(false);
		} else {
			view.showPlayStop();
		}
	}

	@Override
	public void unbindView() {
		this.localRepository.close();
		audioPlayer.removePlayerCallback(playerCallback);
		appRecorder.removeRecordingCallback(appRecorderCallback);
		this.view.stopPlaybackService();
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
		Timber.v("startPlayback: rec: " + record.toString());
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
		audioPlayer.seek(AndroidUtils.convertPxToMills(px, AndroidUtils.dpToPx(dpPerSecond)));
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
								r.getAdded(), renamed.getAbsolutePath(), r.isBookmarked(),
								r.isWaveformProcessed(), r.getAmps());
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
					@Override public void run() {
						view.hideProgress();
					}});
			}});
	}

	@Override
	public void loadActiveRecord() {
		if (!appRecorder.isRecording()) {
			view.showProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					record = localRepository.getRecord((int) prefs.getActiveRecord());
					if (record != null) {
						songDuration = record.getDuration();
						dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(record.getAmps(), songDuration);
									view.showName(FileUtil.removeFileExtension(record.getName()));
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
									view.hideProgress();
								}
							}
						});
						if (!record.isWaveformProcessed() && !isProcessing) {
							try {
								view.showRecordProcessing();
								isProcessing = true;
								localRepository.updateWaveform(record.getId());
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override public void run() {
										view.hideRecordProcessing();
									}
								});
							} catch (IOException e) {
								Timber.e(e);
							}
							isProcessing = false;
						}
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(new int[]{}, 0);
									view.showName("");
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
									view.hideProgress();
								}
							}
						});
					}
				}
			});
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
		if (view != null) {
			view.showImportStart();
		} else {
			showImportProgress = true;
		}

		importTasks.postRunnable(new Runnable() {
			long id = -1;

			@Override
			public void run() {
				Timber.v("importAudioFile uri: " + uri.getPath());
				try {
					ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
					FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
					String name = extractFileName(context, uri);

					File newFile = fileRepository.provideRecordFile(name);

					Timber.v("COPY - Start!");
						if (FileUtil.copyFile(fileDescriptor, newFile)) {
							Timber.v("COPY - FINISH!");
							Timber.v("Copy file %s succeed!", newFile.getAbsolutePath());
							Timber.v("INSERT - START!");
							id = localRepository.insertFile(newFile.getAbsolutePath());
							Timber.v("INSERT - FINISH!");
							prefs.setActiveRecord(id);
						}
//					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							view.hideImportProgress();
							audioPlayer.stop();
							loadActiveRecord(); }
					});
				} catch (IOException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(R.string.error_unable_to_read_sound_file); }
					});
				} catch (final CantCreateFileException ex) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(ErrorParser.parseException(ex)); }
					});
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.hideImportProgress(); }});
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
