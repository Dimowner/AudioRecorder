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
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	private Record record;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private boolean showBookmarks = false;

	public RecordsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks,
									PlayerContract.Player player, AppRecorder appRecorder, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingsTasks;
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
								if (view != null && record != null) {
									view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills,
											AndroidUtils.dpToPx(dpPerSecond)), (int)(1000 * mills/(record.getDuration()/1000)));
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
			if (record != null) {
				if (!audioPlayer.isPlaying()) {
					audioPlayer.setData(record.getPath());
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
		audioPlayer.stop();
		recordingsTasks.postRunnable(new Runnable() {
			@Override public void run() {
				if (record != null) {
					localRepository.deleteRecord(record.getId());
					fileRepository.deleteRecordFile(record.getPath());
					prefs.setActiveRecord(-1);
					final long id = record.getId();
					record = null;
					dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onDeleteRecord(id);
							}
						}
					});
				}
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
						record = new Record(r.getId(), nameWithExt, r.getDuration(), r.getCreated(),
								r.getAdded(), renamed.getAbsolutePath(), r.isBookmarked(),
								r.isWaveformProcessed(), r.getAmps());
						if (localRepository.updateRecord(record)) {
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
	public void loadRecords() {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final List<Record> recordList = localRepository.getRecords(0);
					record = localRepository.getRecord((int) prefs.getActiveRecord());
					if (record != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) record.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showRecords(Mapper.recordsToListItems(recordList));
								if (record != null) {
									view.showWaveForm(record.getAmps(), record.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(record.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(record.getName()));
									if (record.isBookmarked()) {
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
	public void loadRecordsPage(final int page) {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final List<Record> recordList = localRepository.getRecords(page);
					record = localRepository.getRecord((int) prefs.getActiveRecord());
					if (record != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) record.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.addRecords(Mapper.recordsToListItems(recordList));
								if (record != null) {
									view.showWaveForm(record.getAmps(), record.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(record.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(record.getName()));
									if (record.isBookmarked()) {
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
						record = localRepository.getRecord((int) prefs.getActiveRecord());
						if (record != null) {
							dpPerSecond = ARApplication.getDpPerSecond((float) record.getDuration() / 1000000f);
						}
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showRecords(Mapper.recordsToListItems(recordList));
									if (record != null) {
										view.showWaveForm(record.getAmps(), record.getDuration());
										view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(record.getDuration() / 1000));
										view.showRecordName(FileUtil.removeFileExtension(record.getName()));
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
				if (record.isBookmarked()) {
					localRepository.removeFromBookmarks(record.getId());
				} else {
					localRepository.addToBookmarks(record.getId());
				}
				record.setBookmark(!record.isBookmarked());

				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							if (record.isBookmarked()) {
								view.addedToBookmarks(record.getId(), true);
							} else {
								view.removedFromBookmarks(record.getId(), true);
							}
						}
					}
				});
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
								view.addedToBookmarks(r.getId(), r.getId() == record.getId());
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
							if (view != null && record != null) {
								view.removedFromBookmarks(r.getId(), r.getId() == record.getId());
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
					record = localRepository.getRecord((int) id);
					if (record != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) record.getDuration()/1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(record.getAmps(), record.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(record.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(record.getName()));
									callback.onSuccess();
									if (record.isBookmarked()) {
										view.addedToBookmarks(record.getId(), true);
									} else {
										view.removedFromBookmarks(record.getId(), true);
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
		if (record != null) {
			return record.getPath();
		} else {
			return null;
		}
	}

	@Override
	public String getRecordName() {
		if (record != null) {
			return record.getName();
		} else {
			return "Record";
		}
	}
}
