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

package com.dimowner.audiorecorder.app.records;

import android.os.Environment;
import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import timber.log.Timber;

public class RecordsPresenter implements RecordsContract.UserActionsListener {

	private RecordsContract.View view;
	private final PlayerContract.Player audioPlayer;
	private AppRecorder appRecorder;
	private PlayerContract.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final BackgroundQueue copyTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	private Record activeRecord;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private boolean showBookmarks = false;

	public RecordsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks, BackgroundQueue copyTasks,
									PlayerContract.Player player, AppRecorder appRecorder, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingsTasks;
		this.copyTasks = copyTasks;
		this.audioPlayer = player;
		this.appRecorder = appRecorder;
		this.playerCallback = null;
		this.prefs = prefs;
	}

	@Override
	public void bindView(final RecordsContract.View v) {
		this.view = v;

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {
				@Override public void onRecordingStarted() {}
				@Override public void onRecordingPaused() {}
				@Override public void onRecordProcessing() {}

				@Override
				public void onRecordFinishProcessing() {
					loadRecords();
				}

				@Override public void onRecordingProgress(long mills, int amp) {}

				@Override
				public void onRecordingStopped(long id, File file) {
					loadRecords();
				}

				@Override
				public void onError(AppException e) {
					view.showError(ErrorParser.parseException(e));
				}
			};
		}
		appRecorder.addRecordingCallback(appRecorderCallback);

		if (playerCallback == null) {
			this.playerCallback = new PlayerContract.PlayerCallback() {

				@Override
				public void onPreparePlay() {
					Timber.d("onPreparePlay");
				}

				@Override
				public void onStartPlay() {
					if (view != null) {
						view.showPlayStart();
						view.startPlaybackService();
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null && activeRecord != null) {
									long duration = activeRecord.getDuration()/1000;
									if (duration > 0) {
										view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills,
												AndroidUtils.dpToPx(dpPerSecond)), (int) (1000 * mills / duration));
									}
								}
							}});
					}
				}

				@Override
				public void onStopPlay() {
					if (view != null) {
						view.showPlayStop();
					}
				}

				@Override
				public void onPausePlay() {
					if (view != null) {
						view.showPlayPause();
					}
				}

				@Override
				public void onSeek(long mills) {
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
		audioPlayer.addPlayerCallback(playerCallback);
		if (audioPlayer.isPlaying()) {
			if (view != null) {
				view.showPlayerPanel();
				view.showPlayStart();
			}
		}
	}

	@Override
	public void unbindView() {
		if (view != null) {
			audioPlayer.removePlayerCallback(playerCallback);
			appRecorder.removeRecordingCallback(appRecorderCallback);
			this.view = null;
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	@Override
	public void startPlayback() {
		if (!appRecorder.isRecording()) {
			if (activeRecord != null) {
				if (!audioPlayer.isPlaying()) {
					audioPlayer.setData(activeRecord.getPath());
				}
				audioPlayer.playOrPause();
			}
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
	public void playNext() {
	}

	@Override
	public void playPrev() {
	}

	@Override
	public void deleteActiveRecord() {
		if (activeRecord != null) {
			deleteRecord(activeRecord.getId(), activeRecord.getPath());
		}
	}

	@Override
	public void deleteRecord(final long id, final String path) {
		if (activeRecord != null && activeRecord.getId() == id) {
			audioPlayer.stop();
		}
		recordingsTasks.postRunnable(new Runnable() {
			@Override public void run() {
				localRepository.deleteRecord((int)id);
				fileRepository.deleteRecordFile(path);
				if (activeRecord != null && activeRecord.getId() == id) {
					prefs.setActiveRecord(-1);
					dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.onDeleteRecord(id);
							if (activeRecord != null && activeRecord.getId() == id) {
								view.hidePlayPanel();
								view.showMessage(R.string.record_deleted_successfully);
								activeRecord = null;
							}
						}
					}
				});
			}
		});
	}

	@Override
	public void renameRecord(final long id, String n) {
		if (id < 0 || n == null || n.isEmpty()) {
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override public void run() {
					if (view != null) {
						view.showError(R.string.error_failed_to_rename);
					}
				}});
			return;
		}
		view.showProgress();
		final String name = FileUtil.removeUnallowedSignsFromName(n);
		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
//				TODO: This code need to be refactored!
				Record r = localRepository.getRecord((int)id);
//				String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.M4A_EXTENSION;
				String nameWithExt;
				if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
					nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.WAV_EXTENSION;
				} else {
					nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.M4A_EXTENSION;
				}
				File file = new File(r.getPath());
				File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							if (view != null) {
								view.showError(R.string.error_file_exists);
							}
						}});
				} else {
					String ext;
					if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
						ext = AppConstants.WAV_EXTENSION;
					} else {
						ext = AppConstants.M4A_EXTENSION;
					}
					if (fileRepository.renameFile(r.getPath(), name, ext)) {
						activeRecord = new Record(r.getId(), nameWithExt, r.getDuration(), r.getCreated(),
								r.getAdded(), renamed.getAbsolutePath(), r.isBookmarked(),
								r.isWaveformProcessed(), r.getAmps());
						if (localRepository.updateRecord(activeRecord)) {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									if (view != null) {
										view.hideProgress();
										view.showRecordName(name);
									}
								}});
						} else {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									if (view != null) {
										view.showError(R.string.error_failed_to_rename);
									}
								}});
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
							@Override public void run() {
								if (view != null) {
									view.showError(R.string.error_failed_to_rename);
								}
							}});
					}
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) {
							view.hideProgress();
						}
					}});
			}});
	}

	@Override
	public void copyToDownloads(final String path, final String name) {
		if (view != null) {
			//TODO: show copy progress
			copyTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					try {
						FileUtil.copyFile(new File(path), FileUtil.createFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name));
						//TODO: show success result
					} catch (IOException e) {
						Timber.v(e);
						//TODO: show copy error
					}
					//TODO:hide progress
				}
			});
		}
	}

	@Override
	public void loadRecords() {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final int order = prefs.getRecordsOrder();
					final List<Record> recordList = localRepository.getRecords(0, order);
					activeRecord = localRepository.getRecord((int) prefs.getActiveRecord());
					if (activeRecord != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) activeRecord.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showRecords(Mapper.recordsToListItems(recordList), order);
								if (activeRecord != null) {
									view.showWaveForm(activeRecord.getAmps(), activeRecord.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(activeRecord.getName()));
									if (activeRecord.isBookmarked()) {
										view.bookmarksSelected();
									} else {
										view.bookmarksUnselected();
									}
								}
								view.hideProgress();
								view.hidePanelProgress();
								view.bookmarksUnselected();
								if (recordList.size() == 0) {
									view.showEmptyList();
								}
							}
						}
					});
				}
			});
		}
	}

	@Override
	public void updateRecordsOrder(int order) {
		prefs.setRecordOrder(order);
		loadRecords();
	}

	@Override
	public void loadRecordsPage(final int page) {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final int order = prefs.getRecordsOrder();
					final List<Record> recordList = localRepository.getRecords(page, order);
					activeRecord = localRepository.getRecord((int) prefs.getActiveRecord());
					if (activeRecord != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) activeRecord.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.addRecords(Mapper.recordsToListItems(recordList), order);
								if (activeRecord != null) {
									view.showWaveForm(activeRecord.getAmps(), activeRecord.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(activeRecord.getName()));
									if (activeRecord.isBookmarked()) {
										view.bookmarksSelected();
									} else {
										view.bookmarksUnselected();
									}
								}
								view.hideProgress();
								view.hidePanelProgress();
								view.bookmarksUnselected();
							}
						}
					});
				}
			});
		}
	}

	public void loadBookmarks() {
		if (!showBookmarks) {
			loadRecords();
		} else {
			if (view != null) {
				view.showProgress();
				view.showPanelProgress();
				loadingTasks.postRunnable(new Runnable() {
					@Override
					public void run() {
						final List<Record> recordList = localRepository.getBookmarks();
						activeRecord = localRepository.getRecord((int) prefs.getActiveRecord());
						if (activeRecord != null) {
							dpPerSecond = ARApplication.getDpPerSecond((float) activeRecord.getDuration() / 1000000f);
						}
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showRecords(Mapper.recordsToListItems(recordList), AppConstants.ORDER_DATE);
									if (activeRecord != null) {
										view.showWaveForm(activeRecord.getAmps(), activeRecord.getDuration());
										view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.getDuration() / 1000));
										view.showRecordName(FileUtil.removeFileExtension(activeRecord.getName()));
									}
									view.hideProgress();
									view.hidePanelProgress();
									view.bookmarksSelected();
									if (recordList.size() == 0) {
										view.showEmptyBookmarksList();
									}
								}
							}
						});
					}
				});
			}
		}
	}

	@Override
	public void applyBookmarksFilter() {
		showBookmarks = !showBookmarks;
		loadBookmarks();
	}

	@Override
	public void checkBookmarkActiveRecord() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (activeRecord != null) {
					if (activeRecord.isBookmarked()) {
						localRepository.removeFromBookmarks(activeRecord.getId());
					} else {
						localRepository.addToBookmarks(activeRecord.getId());
					}
					activeRecord.setBookmark(!activeRecord.isBookmarked());

					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null && activeRecord != null) {
								if (activeRecord.isBookmarked()) {
									view.addedToBookmarks(activeRecord.getId(), true);
								} else {
									view.removedFromBookmarks(activeRecord.getId(), true);
								}
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void addToBookmark(final int id) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record r = localRepository.getRecord(id);
				if (r != null) {
					localRepository.addToBookmarks(r.getId());
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.addedToBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void removeFromBookmarks(final int id) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record r = localRepository.getRecord(id);
				if (r != null) {
					localRepository.removeFromBookmarks(r.getId());
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.removedFromBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void setActiveRecord(final long id, final RecordsContract.Callback callback) {
		if (id >= 0 && !appRecorder.isRecording()) {
			prefs.setActiveRecord(id);
			if (view != null) {
				view.showPanelProgress();
			}
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					activeRecord = localRepository.getRecord((int) id);
					if (activeRecord != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) activeRecord.getDuration()/1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null && activeRecord != null) {
									view.showWaveForm(activeRecord.getAmps(), activeRecord.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(activeRecord.getName()));
									callback.onSuccess();
									if (activeRecord.isBookmarked()) {
										view.addedToBookmarks(activeRecord.getId(), true);
									} else {
										view.removedFromBookmarks(activeRecord.getId(), true);
									}
									view.hidePanelProgress();
									view.showPlayerPanel();
								}
							}
						});
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								callback.onError(new Exception("Record is NULL!"));
								if (view != null) {
									view.hidePanelProgress();
								}
							}
						});
					}
				}
			});
		}
	}

	@Override
	public long getActiveRecordId() {
		return prefs.getActiveRecord();
	}

	@Override
	public String getActiveRecordPath() {
		if (activeRecord != null) {
			return activeRecord.getPath();
		} else {
			return null;
		}
	}

	@Override
	public String getRecordName() {
		if (activeRecord != null) {
			return activeRecord.getName();
		} else {
			return "Record";
		}
	}
}
