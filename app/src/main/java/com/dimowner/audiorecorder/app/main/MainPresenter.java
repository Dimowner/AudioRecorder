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
import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.settings.SettingsMapper;
import com.dimowner.audiorecorder.audio.AudioDecoder;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.OnRecordsLostListener;
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
import java.util.Date;
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
	private final BackgroundQueue importTasks;
	private final BackgroundQueue processingTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private final SettingsMapper settingsMapper;
	private long songDuration = 0;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private Record record;
	private boolean deleteRecord = false;
	private boolean listenPlaybackProgress = true;

	/** Flag true defines that presenter called to show import progress when view was not bind.
	 * And after view bind we need to show import progress.*/
	private boolean showImportProgress = false;

	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								PlayerContract.Player audioPlayer,
								AppRecorder appRecorder,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks,
								final BackgroundQueue processingTasks,
								final BackgroundQueue importTasks,
								SettingsMapper settingsMapper) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
		this.importTasks = importTasks;
		this.processingTasks = processingTasks;
		this.audioPlayer = audioPlayer;
		this.appRecorder = appRecorder;
		this.settingsMapper = settingsMapper;
	}

	@Override
	public void bindView(final MainContract.View v) {
		this.view = v;
		if (showImportProgress) {
			view.showImportStart();
		} else {
			view.hideImportProgress();
		}

		if (!prefs.isMigratedDb3()) {
			migrateDb3();
		}
		if (!prefs.hasAskToRenameAfterStopRecordingSetting()) {
			prefs.setAskToRenameAfterStopRecording(true);
		}

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {

				private File recFile = null;

				@Override
				public void onRecordingStarted(final File file) {
					recFile = file;
					if (view != null) {
						view.showRecordingStart();
						view.keepScreenOn(prefs.isKeepScreenOn());
						view.startRecordingService();
					}
					updateInformation(
							prefs.getSettingRecordingFormat(),
							prefs.getSettingSampleRate(),
							0
					);
				}

				@Override
				public void onRecordingPaused() {
					if (view != null) {
						view.keepScreenOn(false);
						view.showRecordingPause();
					}
					if (deleteRecord) {
						if (view != null) {
							view.askDeleteRecordForever();
							deleteRecord = false;
						}
					}
				}

				@Override
				public void onRecordProcessing() {
					if (view != null) {
						view.showRecordProcessing();
					}
				}

				@Override
				public void onRecordFinishProcessing() {
					if (view != null) {
						view.hideRecordProcessing();
					}
					loadActiveRecord();
				}

				@Override
				public void onRecordingStopped(final File file, final Record rec) {
					recFile = null;
					if (deleteRecord) {
						deleteActiveRecord(true);
						deleteRecord = false;
					} else {
						if (view != null) {
							if (prefs.isAskToRenameAfterStopRecording()) {
								view.askRecordingNewName(rec.getId(), file, true, true);
							}
						}
						record = rec;
						songDuration = rec.getDuration();
						dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
						if (view != null) {
							view.showWaveForm(rec.getAmps(), songDuration);
							view.showName(rec.getName());
							view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
							view.showOptionsMenu();
						}
						updateInformation(rec.getFormat(), rec.getSampleRate(), rec.getSize());
					}
					if (view != null) {
						view.keepScreenOn(false);
						view.stopRecordingService();
						view.hideProgress();
						view.showRecordingStop();
					}
				}

				@Override
				public void onRecordingProgress(final long mills, final int amp) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onRecordingProgress(mills, amp);
								if (recFile != null && mills % 1000 == 0) { //Update record info every second when recording.
									updateInformation(
											prefs.getSettingRecordingFormat(),
											prefs.getSettingSampleRate(),
											recFile.length()
									);
								}
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
					if (record != null) {
						view.startPlaybackService(record.getName());
					}
				}

				@Override
				public void onStartPlay() {
					if (view != null) {
						view.showPlayStart(true);
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null && listenPlaybackProgress) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null) {
									long duration = songDuration/1000;
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
						audioPlayer.seek(0);
						view.showPlayStop();
						view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
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

		this.audioPlayer.addPlayerCallback(playerCallback);

		if (audioPlayer.isPlaying()) {
			view.showPlayStart(false);
		} else if (audioPlayer.isPause()) {
			if (view != null) {
				long duration = songDuration/1000;
				if (duration > 0) {
					long playProgressMills = audioPlayer.getPauseTime();
					view.onPlayProgress(playProgressMills, AndroidUtils.convertMillsToPx(playProgressMills,
							AndroidUtils.dpToPx(dpPerSecond)), (int) (1000 * playProgressMills / duration));
				}
				view.showPlayPause();
			}
		} else {
			audioPlayer.seek(0);
			view.showPlayStop();
		}

		if (appRecorder.isPaused()) {
			view.keepScreenOn(false);
			view.showRecordingPause();
			view.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration()));
			view.updateRecordingView(appRecorder.getRecordingData());
		} else if (appRecorder.isRecording()) {
			view.showRecordingStart();
			view.showRecordingProgress(TimeUtils.formatTimeIntervalHourMinSec2(appRecorder.getRecordingDuration()));
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
		updateInformation(
				prefs.getSettingRecordingFormat(),
				prefs.getSettingSampleRate(),
				0
		);

		this.localRepository.setOnRecordsLostListener(new OnRecordsLostListener() {
			@Override
			public void onLostRecords(List<Record> list) {
				view.showRecordsLostMessage(list);
			}
		});
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
		localRepository.close();
		audioPlayer.release();
		appRecorder.release();
		loadingTasks.close();
		recordingsTasks.close();
	}

	@Override
	public void checkFirstRun() {
		if (prefs.isFirstRun()) {
			if (view != null) {
				view.startWelcomeScreen();
			}
		}
	}

	@Override
	public void setAudioRecorder(RecorderContract.Recorder recorder) {
		appRecorder.setRecorder(recorder);
	}

	@Override
	public void startRecording(Context context) {
		try {
			if (fileRepository.hasAvailableSpace(context)) {
				if (audioPlayer.isPlaying()) {
					audioPlayer.stop();
				}
				if (appRecorder.isPaused()) {
					appRecorder.resumeRecording();
				} else if (!appRecorder.isRecording()) {
					try {
						final String path = fileRepository.provideRecordFile().getAbsolutePath();
						recordingsTasks.postRunnable(new Runnable() {
							@Override
							public void run() {
								try {
									record = localRepository.insertEmptyFile(path);
									prefs.setActiveRecord(record.getId());
									AndroidUtils.runOnUIThread(new Runnable() {
										@Override
										public void run() {
											appRecorder.startRecording(
													path,
													prefs.getSettingChannelCount(),
													prefs.getSettingSampleRate(),
													prefs.getSettingBitrate()
											);
										}
									});
								} catch (IOException | OutOfMemoryError | IllegalStateException e) {
									Timber.e(e);
								}
							}
						});
					} catch (CantCreateFileException e) {
						if (view != null) {
							view.showError(ErrorParser.parseException(e));
						}
					}
				} else {
					appRecorder.pauseRecording();
				}
			} else {
				view.showError(R.string.error_no_available_space);
			}
		} catch (IllegalArgumentException e) {
			view.showError(R.string.error_failed_access_to_storage);
		}
	}

	@Override
	public void stopRecording(boolean delete) {
		if (appRecorder.isRecording()) {
			deleteRecord = delete;
			if (view != null) {
				view.showProgress();
				view.waveFormToStart();
			}
			audioPlayer.seek(0);
			appRecorder.stopRecording();
		}
	}

	@Override
	public void cancelRecording() {
		deleteRecord = true;
		appRecorder.pauseRecording();
	}

	@Override
	public void startPlayback() {
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
	public void renameRecord(final long id, final String newName, final String extension, final boolean needDecode) {
		if (id < 0 || newName == null || newName.isEmpty()) {
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override public void run() {
					if (view != null) {
						view.showError(R.string.error_failed_to_rename);
					}
				}});
			return;
		}
		if (view != null) {
			view.showProgress();
		}
		final String name = FileUtil.removeUnallowedSignsFromName(newName);
		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
				final Record record = localRepository.getRecord((int)id);
				if (record != null) {
					String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + extension;
					File file = new File(record.getPath());
					File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

					if (renamed.exists()) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showError(R.string.error_file_exists);
								}
							}
						});
					} else {
						if (fileRepository.renameFile(record.getPath(), name, extension)) {
							MainPresenter.this.record = new Record(
									record.getId(),
									name,
									record.getDuration(),
									record.getCreated(),
									record.getAdded(),
									record.getRemoved(),
									renamed.getAbsolutePath(),
									record.getFormat(),
									record.getSize(),
									record.getSampleRate(),
									record.getChannelCount(),
									record.getBitrate(),
									record.isBookmarked(),
									record.isWaveformProcessed(),
									record.getAmps());
							if (localRepository.updateRecord(MainPresenter.this.record)) {
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
										if (view != null) {
											view.hideProgress();
											view.showName(name);
											if (needDecode && record.getDuration()/1000 < AppConstants.DECODE_DURATION) {
												appRecorder.decodeRecordWaveform(MainPresenter.this.record);
											}
										}
									}
								});
							} else {
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
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
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									if (view != null) {
										view.showError(R.string.error_failed_to_rename);
									}
								}
							});
						}
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.hideProgress();
							}
						}
					});
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							if (view != null) {
								view.showError(R.string.error_failed_to_rename);
							}
						}});
				}
			}
		});
	}

	@Override
	public void decodeRecord(long id) {
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
				if (rec != null && rec.getDuration()/1000 < AppConstants.DECODE_DURATION) {
					appRecorder.decodeRecordWaveform(rec);
				}
			}
		});
	}

	@Override
	public void loadActiveRecord() {
		if (!appRecorder.isRecording()) {
			if (view != null) {
				view.showProgress();
			}
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
					record = rec;
					if (rec != null) {
						songDuration = rec.getDuration();
						dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(rec.getAmps(), songDuration);
									view.showName(rec.getName());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
									view.showOptionsMenu();
									view.hideProgress();
									updateInformation(rec.getFormat(), rec.getSampleRate(), rec.getSize());
								}
							}
						});
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.hideProgress();
									view.showWaveForm(new int[]{}, 0);
									view.showName("");
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
									view.hideOptionsMenu();
								}
							}
						});
					}
				}
			});
		}
	}

	@Override
	public void dontAskRename() {
		prefs.setAskToRenameAfterStopRecording(false);
	}

	@Override
	public void updateRecordingDir(Context context) {
		fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public void setStoragePrivate(Context context) {
		prefs.setStoreDirPublic(false);
		fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public void onShareRecordClick() {
		if (view != null && record != null) {
			view.shareRecord(record);
		}
	}

	@Override
	public void onRenameRecordClick() {
		if (view != null && record != null) {
			view.askRecordingNewName(record.getId(), new File(record.getPath()), false, false);
		}
	}

	@Override
	public void onOpenFileClick() {
		if (view != null && record != null) {
			view.openFile(record);
		}
	}

	@Override
	public void onDownloadClick() {
		if (view != null && record != null) {
			view.downloadRecord(record);
		}
	}

	@Override
	public void onDeleteClick() {
		if (view != null && record != null) {
			view.askDeleteRecord(record.getName());
		}
	}

	private void updateInformation(String format, int sampleRate, long size) {
		if (format.equals(AppConstants.FORMAT_3GP)) {
			if (view != null) {
				view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
						+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
						+ settingsMapper.convertSampleRateToString(sampleRate)
				);
			}
		} else {
			if (view != null) {
				switch (format) {
					case AppConstants.FORMAT_M4A:
					case AppConstants.FORMAT_WAV:
						view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
								+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
								+ settingsMapper.convertSampleRateToString(sampleRate)
						);
						break;
					default:
						view.showInformation(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
								+ format + AppConstants.SEPARATOR
								+ settingsMapper.convertSampleRateToString(sampleRate)
						);
				}
			}
		}
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
	public void deleteActiveRecord(final boolean forever) {
		final Record rec = record;
		if (rec != null) {
			audioPlayer.stop();
			recordingsTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					if (forever) {
						localRepository.deleteRecordForever(rec.getId());
						fileRepository.deleteRecordFile(rec.getPath());
					} else {
						localRepository.deleteRecord(rec.getId());
					}
					prefs.setActiveRecord(-1);
					dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showWaveForm(new int[]{}, 0);
								view.showName("");
								view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
								if (!forever) {
									view.showMessage(R.string.record_moved_into_trash);
								}
								view.hideOptionsMenu();
								view.onPlayProgress(0, 0, 0);
								view.hideProgress();
								record = null;
								updateInformation(
										prefs.getSettingRecordingFormat(),
										prefs.getSettingSampleRate(),
										0
								);
							}
						}
					});
				}
			});
		}
	}

	@Override
	public void onRecordInfo() {
		Record rec = record;
		if (rec != null) {
			view.showRecordInfo(Mapper.toRecordInfo(rec));
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

	@Override
	public void importAudioFile(final Context context, final Uri uri) {
		if (view != null) {
			view.showImportStart();
		}
		showImportProgress = true;

		importTasks.postRunnable(new Runnable() {
			long id = -1;

			@Override
			public void run() {
				try {
					ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
					FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
					String name = extractFileName(context, uri);

					File newFile = fileRepository.provideRecordFile(name);
					if (FileUtil.copyFile(fileDescriptor, newFile)) {
						RecordInfo info = AudioDecoder.readRecordInfo(newFile);

						//Do 2 step import: 1) Import record with empty waveform. 2) Process and update waveform in background.
						Record r = new Record(
								Record.NO_ID,
								FileUtil.removeFileExtension(newFile.getName()),
								info.getDuration() >= 0 ? info.getDuration() : 0,
								newFile.lastModified(),
								new Date().getTime(),
								Long.MAX_VALUE,
								newFile.getAbsolutePath(),
								info.getFormat(),
								info.getSize(),
								info.getSampleRate(),
								info.getChannelCount(),
								info.getBitrate(),
								false,
								false,
								new int[ARApplication.getLongWaveformSampleCount()]);
						record = localRepository.insertRecord(r);
						final Record rec = record;
						if (rec != null) {
							id = rec.getId();
							prefs.setActiveRecord(id);
							songDuration = info.getDuration();
							dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									if (view != null) {
										audioPlayer.stop();
										view.showWaveForm(rec.getAmps(), songDuration);
										view.showName(rec.getName());
										view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
										view.hideProgress();
										view.hideImportProgress();
										view.showOptionsMenu();
										updateInformation(rec.getFormat(), rec.getSampleRate(), rec.getSize());
									}
								}
							});
							if (rec.getDuration()/1000 < AppConstants.DECODE_DURATION) {
								appRecorder.decodeRecordWaveform(rec);
							}
						}
					}
				} catch (SecurityException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(R.string.error_permission_denied); }
					});
				} catch (IOException | OutOfMemoryError | IllegalStateException e) {
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
						if (view != null) { view.hideImportProgress(); }
					}});
				showImportProgress = false;
			}
		});
	}

	private void migrateDb3() {
		processingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				//Update records table.
				List<Integer> ids = localRepository.getAllItemsIds();
				Record rec;
				for (int i = 0; i < ids.size(); i++) {
					if (ids.get(i) != null) {
						rec = localRepository.getRecord(ids.get(i));
						if (rec != null) {
							RecordInfo info = AudioDecoder.readRecordInfo(new File(rec.getPath()));
							localRepository.updateRecord(new Record(
									rec.getId(),
									FileUtil.removeFileExtension(rec.getName()),
									rec.getDuration(),
									rec.getCreated(),
									rec.getAdded(),
									rec.getRemoved(),
									rec.getPath(),
									info.getFormat(),
									info.getSize(),
									info.getSampleRate(),
									info.getChannelCount(),
									info.getBitrate(),
									rec.isBookmarked(),
									rec.isWaveformProcessed(),
									rec.getAmps()));
						}
					}
				}
				//Update trash records table.
				List<Integer> trashIds = localRepository.getTrashRecordsIds();
				Record trashRecord;
				for (int i = 0; i < trashIds.size(); i++) {
					if (trashIds.get(i) != null) {
						trashRecord = localRepository.getTrashRecord(trashIds.get(i));
						if (trashRecord != null) {
							RecordInfo info = AudioDecoder.readRecordInfo(new File(trashRecord.getPath()));
							localRepository.updateTrashRecord(new Record(
									trashRecord.getId(),
									FileUtil.removeFileExtension(trashRecord.getName()),
									trashRecord.getDuration(),
									trashRecord.getCreated(),
									trashRecord.getAdded(),
									trashRecord.getRemoved(),
									trashRecord.getPath(),
									info.getFormat(),
									info.getSize(),
									info.getSampleRate(),
									info.getChannelCount(),
									info.getBitrate(),
									trashRecord.isBookmarked(),
									trashRecord.isWaveformProcessed(),
									trashRecord.getAmps()));
						}
					}
				}
				prefs.migrateDb3Finished();
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
			if (cursor != null) {
				cursor.close();
			}
		}
		return null;
	}
}
