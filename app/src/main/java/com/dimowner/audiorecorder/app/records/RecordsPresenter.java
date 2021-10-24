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

package com.dimowner.audiorecorder.app.records;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.audio.player.PlayerContractNew;
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

import androidx.annotation.NonNull;
import timber.log.Timber;

public class RecordsPresenter implements RecordsContract.UserActionsListener {

	private RecordsContract.View view;
	private final PlayerContractNew.Player audioPlayer;
	private final AppRecorder appRecorder;
	private PlayerContractNew.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	private Record activeRecord;
	private boolean showBookmarks = false;
	private boolean listenPlaybackProgress = true;

	public RecordsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks,
									PlayerContractNew.Player player, AppRecorder appRecorder, Prefs prefs) {
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
				@Override public void onRecordingStarted(File file) {}
				@Override public void onRecordingPaused() {}
				@Override public void onRecordingResumed() { }

				@Override public void onRecordingProgress(long mills, int amp) {}

				@Override
				public void onRecordingStopped(File file, Record rec) {
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
			this.playerCallback = new PlayerContractNew.PlayerCallback() {

				@Override
				public void onStartPlay() {
					if (view != null) {
						view.startPlaybackService();
						view.showPlayStart();
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null && listenPlaybackProgress) {
						Record rec = activeRecord;
						if (rec != null) {
							long duration = rec.getDuration()/1000;
							if (duration > 0) {
								view.onPlayProgress(mills, (int) (1000 * mills / duration));
							}
						}
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
				public void onError(@NonNull AppException throwable) {
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
		} else if (audioPlayer.isPaused()) {
			if (view != null) {
				view.showPlayerPanel();
				view.showPlayPause();
			}
		}
		if (view != null) {
			view.showSortType(prefs.getRecordsOrder());
		}

		this.localRepository.setOnRecordsLostListener(list -> view.showRecordsLostMessage(list));
	}

	@Override
	public void unbindView() {
		if (view != null) {
			audioPlayer.removePlayerCallback(playerCallback);
			appRecorder.removeRecordingCallback(appRecorderCallback);
			this.localRepository.setOnRecordsLostListener(null);
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
	public void onResumeView() {
		loadingTasks.postRunnable(() -> {
			final int count = localRepository.getTrashRecordsCount();
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					if (count > 0) {
						view.showTrashBtn();
					} else {
						view.hideTrashBtn();
					}
				}
			});
		});
	}

	@Override
	public void startPlayback() {
		if (!appRecorder.isRecording()) {
			if (activeRecord != null) {
				if (audioPlayer.isPlaying()) {
					audioPlayer.pause();
				} else if (audioPlayer.isPaused()) {
					audioPlayer.unpause();
				} else {
					audioPlayer.play(activeRecord.getPath());
				}
			}
		}
	}

	@Override
	public void pausePlayback() {
		audioPlayer.pause();
	}

	@Override
	public void seekPlayback(long mills) {
		audioPlayer.seek(mills);
	}

	@Override
	public void stopPlayback() {
		if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
			audioPlayer.stop();
		}
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
		final Record rec = activeRecord;
		if (rec != null && rec.getId() == id) {
			audioPlayer.stop();
		}
		recordingsTasks.postRunnable(() -> {
			localRepository.deleteRecord((int) id);
//				fileRepository.deleteRecordFile(path);
			if (rec != null && rec.getId() == id) {
				prefs.setActiveRecord(-1);
			}
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					view.showTrashBtn();
					view.onDeleteRecord(id);
					view.showMessage(R.string.record_moved_into_trash);
					if (rec != null && rec.getId() == id) {
						view.hidePlayPanel();
						activeRecord = null;
					}
				}
			});
		});
	}

	@Override
	public void deleteRecords(List<Long> ids) {
		recordingsTasks.postRunnable(() -> {
			for(Long id: ids) {
				localRepository.deleteRecord(id.intValue());
				AndroidUtils.runOnUIThread(() -> {
					if (view != null) {
						view.showTrashBtn();
						view.onDeleteRecord(id);
					}
				});
			}
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					view.cancelMultiSelect();
					view.showMessage(R.string.selected_records_moved_into_trash);
				}
			});
		});
	}

	@Override
	public void renameRecord(final long id, String n, final String extension) {
		if (id < 0 || n == null || n.isEmpty()) {
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					view.showError(R.string.error_failed_to_rename);
				}
			});
			return;
		}
		view.showProgress();
		final String name = FileUtil.removeUnallowedSignsFromName(n);
		recordingsTasks.postRunnable(() -> {
			Record rec2 = localRepository.getRecord((int)id);
			if (rec2 != null) {
				String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + extension;
				File file = new File(rec2.getPath());
				File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread(() -> {
						if (view != null) {
							view.showError(R.string.error_file_exists);
						}
					});
				} else {
					if (fileRepository.renameFile(rec2.getPath(), name, extension)) {
						Record renamedRecord = new Record(
								rec2.getId(),
								name,
								rec2.getDuration(),
								rec2.getCreated(),
								rec2.getAdded(),
								rec2.getRemoved(),
								renamed.getAbsolutePath(),
								rec2.getFormat(),
								rec2.getSize(),
								rec2.getSampleRate(),
								rec2.getChannelCount(),
								rec2.getBitrate(),
								rec2.isBookmarked(),
								rec2.isWaveformProcessed(),
								rec2.getAmps());
						if (localRepository.updateRecord(renamedRecord)) {
							AndroidUtils.runOnUIThread(() -> {
								if (view != null) {
									view.hideProgress();
									loadRecords();
								}
							});
						} else {
							AndroidUtils.runOnUIThread(() -> {
								if (view != null) {
									view.showError(R.string.error_failed_to_rename);
								}
							});
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
						AndroidUtils.runOnUIThread(() -> {
							if (view != null) {
								view.showError(R.string.error_failed_to_rename);
							}
						});
					}
				}
			} else {
				AndroidUtils.runOnUIThread(() -> {
					if (view != null) {
						view.showError(R.string.error_failed_to_rename);
					}
				});
			}
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					view.hideProgress();
				}
			});
		});
	}

	@Override
	public void loadRecords() {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(() -> {
				final int order = prefs.getRecordsOrder();
				final List<Record> recordList = localRepository.getRecords(0, order);
				final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
				activeRecord = rec;
				AndroidUtils.runOnUIThread(() -> {
					if (view != null) {
						view.showRecords(Mapper.recordsToListItems(recordList), order);
						if (audioPlayer.isPaused() || audioPlayer.isPlaying()) {
							if (rec != null) {
								if (audioPlayer.isPaused()) {
									long duration = rec.getDuration() / 1000;
									if (duration > 0) {
										long playProgressMills = audioPlayer.getPauseTime();
										view.onPlayProgress(playProgressMills, (int) (1000 * playProgressMills / duration));
										view.showWaveForm(rec.getAmps(), rec.getDuration(), playProgressMills);
									}
								} else {
									view.showWaveForm(rec.getAmps(), rec.getDuration(), 0);
								}
								view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
								view.showRecordName(rec.getName());
								if (rec.isBookmarked()) {
									view.bookmarksSelected();
								} else {
									view.bookmarksUnselected();
								}
								if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
									view.showActiveRecord(rec.getId());
								}
							}
						}

						view.hideProgress();
						view.hidePanelProgress();
						view.bookmarksUnselected();
						if (recordList.size() == 0) {
							view.showEmptyList();
						}
					}
				});
			});
		}
	}

	@Override
	public void updateRecordsOrder(int order) {
		prefs.setRecordOrder(order);
		if (view != null) {
			view.showSortType(order);
		}
		loadRecords();
	}

	@Override
	public void loadRecordsPage(final int page) {
		if (view != null && !showBookmarks) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(() -> {
				final int order = prefs.getRecordsOrder();
				final List<Record> recordList = localRepository.getRecords(page, order);
				AndroidUtils.runOnUIThread(() -> {
					if (view != null) {
						view.addRecords(Mapper.recordsToListItems(recordList), order);
						view.hideProgress();
						view.hidePanelProgress();
						view.bookmarksUnselected();
					}
				});
			});
		}
	}

	@Override
	public void decodeActiveRecord() {
		if (view != null && activeRecord != null) {
			view.decodeRecord(activeRecord.getId());
		}
	}

	public void loadBookmarks() {
		if (!showBookmarks) {
			loadRecords();
		} else {
			if (view != null) {
				view.showProgress();
				view.showPanelProgress();
				loadingTasks.postRunnable(() -> {
					final List<Record> recordList = localRepository.getBookmarks();
					AndroidUtils.runOnUIThread(() -> {
						if (view != null) {
							view.showRecords(Mapper.recordsToListItems(recordList), AppConstants.SORT_DATE);
							view.hideProgress();
							view.hidePanelProgress();
							view.bookmarksSelected();
							if (recordList.size() == 0) {
								view.showEmptyBookmarksList();
							}
						}
					});
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
		recordingsTasks.postRunnable(() -> {
			final Record rec = activeRecord;
			if (rec != null) {
				boolean success;
				if (rec.isBookmarked()) {
					success = localRepository.removeFromBookmarks(rec.getId());
				} else {
					success = localRepository.addToBookmarks(rec.getId());
				}
				if (success) {
					rec.setBookmark(!rec.isBookmarked());

					AndroidUtils.runOnUIThread(() -> {
						if (view != null) {
							if (rec.isBookmarked()) {
								view.addedToBookmarks(rec.getId(), true);
							} else {
								view.removedFromBookmarks(rec.getId(), true);
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void addToBookmark(final int id) {
		recordingsTasks.postRunnable(() -> {
			final Record r = localRepository.getRecord(id);
			if (r != null) {
				if (localRepository.addToBookmarks(r.getId())) {
					AndroidUtils.runOnUIThread(() -> {
						if (view != null) {
							view.addedToBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
						}
					});
				}
			}
		});
	}

	@Override
	public void removeFromBookmarks(final int id) {
		recordingsTasks.postRunnable(() -> {
			final Record r = localRepository.getRecord(id);
			if (r != null) {
				localRepository.removeFromBookmarks(r.getId());
				AndroidUtils.runOnUIThread(() -> {
					if (view != null) {
						view.removedFromBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
					}
				});
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
			loadingTasks.postRunnable(() -> {
				final Record rec = localRepository.getRecord((int) id);
				activeRecord = rec;
				if (rec != null) {
					AndroidUtils.runOnUIThread(() -> {
						if (view != null) {
							view.showWaveForm(rec.getAmps(), rec.getDuration(), 0);
							view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
							view.showRecordName(rec.getName());
							callback.onSuccess();
							if (rec.isBookmarked()) {
								view.addedToBookmarks(rec.getId(), true);
							} else {
								view.removedFromBookmarks(rec.getId(), true);
							}
							view.hidePanelProgress();
							view.showPlayerPanel();
						}
					});
				} else {
					AndroidUtils.runOnUIThread(() -> {
						callback.onError(new Exception("Record is NULL!"));
						if (view != null) {
							view.hidePanelProgress();
						}
					});
				}
			});
		}
	}

	@Override
	public void onRenameClick() {
		view.showRename(activeRecord);
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

	@Override
	public void onRecordInfo(RecordInfo info) {
		if (view != null) {
			view.showRecordInfo(info);
		}
	}

	@Override
	public void disablePlaybackProgressListener() {
		listenPlaybackProgress = false;
	}

	@Override
	public void enablePlaybackProgressListener() {
		listenPlaybackProgress = true;
	}
}
