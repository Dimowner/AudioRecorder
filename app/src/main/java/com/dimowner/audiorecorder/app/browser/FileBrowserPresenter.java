/*
 * Copyright 2020 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.browser;

import android.content.Context;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.audio.AudioDecoder;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

/**
 * Created on 30.05.2020.
 * @author Dimowner
 */
public class FileBrowserPresenter implements FileBrowserContract.UserActionsListener {

	public static final int TAB_PRIVATE_DIR = 1;
	public static final int TAB_PUBLIC_DIR = 2;

	private FileBrowserContract.View view;
	private AppRecorder appRecorder;
	private Prefs prefs;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue importTasks;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final LocalRepository localRepository;
	private final FileRepository fileRepository;
	private int selectedTab;

	public FileBrowserPresenter(Prefs prefs, AppRecorder appRecorder, BackgroundQueue importTasks, BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks,
										 LocalRepository localRepository, FileRepository fileRepository) {
		this.prefs = prefs;
		this.appRecorder = appRecorder;
		this.importTasks = importTasks;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingsTasks;
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;

		if (prefs.isStoreDirPublic()) {
			selectedTab = TAB_PUBLIC_DIR;
		} else {
			selectedTab = TAB_PRIVATE_DIR;
		}
	}

	@Override
	public void bindView(FileBrowserContract.View v) {
		this.view = v;

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {

				@Override
				public void onRecordingStarted(final File file) {
				}

				@Override
				public void onRecordingPaused() {
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
				}

				@Override
				public void onRecordingStopped(final File file, final Record rec) {
				}

				@Override
				public void onRecordingProgress(final long mills, final int amp) {
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
	}

	@Override
	public void unbindView() {
		this.localRepository.setOnRecordsLostListener(null);
		this.view = null;
	}

	@Override
	public void clear() {
		unbindView();
	}

	@Override
	public void selectPrivateDir(Context context) {
		if (selectedTab != TAB_PRIVATE_DIR) {
			selectedTab = TAB_PRIVATE_DIR;
			loadFiles(context);
		}
	}

	@Override
	public void selectPublicDir(Context context) {
		if (selectedTab != TAB_PUBLIC_DIR) {
			selectedTab = TAB_PUBLIC_DIR;
			loadFiles(context);
		}
	}

	@Override
	public void loadFiles(final Context context) {
		updatePath(context);
		if (view != null) {
			view.showProgress();
		}
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				File[] files;
				if (selectedTab == TAB_PRIVATE_DIR) {
					files = fileRepository.getPrivateDirFiles(context);
				} else {
					files = fileRepository.getPublicDirFiles();
				}
				final List<RecordInfo> items = new ArrayList<>();
				if (files != null) {
					for (int i = 0; i < files.length; i++) {
						Record rec = localRepository.findRecordByPath(files[i].getAbsolutePath());
						RecordInfo r = AudioDecoder.readRecordInfo(files[i]);
						r.setInDatabase(rec != null);
						items.add(r);
					}
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.hideProgress();
							if (items.isEmpty()) {
								view.showEmpty();
							} else {
								view.showFileItems(items);
								view.hideEmpty();
							}
						}
					}
				});
			}
		});
	}

	@Override
	public void onRecordInfo(RecordInfo info) {
		if (view != null) {
			view.showRecordInfo(info);
		}
	}

	@Override
	public void deleteRecord(final RecordInfo record) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (fileRepository.deleteRecordFile(record.getLocation())) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onDeletedRecord(record.getLocation());
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void importAudioFile(final Context context, final RecordInfo info) {
		if (view != null) {
			view.showImportStart();
		}

		importTasks.postRunnable(new Runnable() {
			long id = -1;

			@Override
			public void run() {
				try {
					File file = new File(info.getLocation());

					//Do 2 step import: 1) Import record with empty waveform. 2) Process and update waveform in background.
					Record r = new Record(
							Record.NO_ID,
							info.getName(),
							info.getDuration() >= 0 ? info.getDuration() : 0,
							file.lastModified(),
							new Date().getTime(),
							Long.MAX_VALUE,
							info.getLocation(),
							info.getFormat(),
							info.getSize(),
							info.getSampleRate(),
							info.getChannelCount(),
							info.getBitrate(),
							false,
							false,
							new int[ARApplication.getLongWaveformSampleCount()]);
					final Record rec = localRepository.insertRecord(r);
					if (rec != null) {
						id = rec.getId();
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.hideImportProgress();
									view.onImportedRecord(info.getLocation());
								}
							}
						});
						if (rec.getDuration() / 1000 < AppConstants.DECODE_DURATION) {
							appRecorder.decodeRecordWaveform(rec);
						}
					}
				} catch (SecurityException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) view.showError(R.string.error_permission_denied);
						}
					});
				} catch (OutOfMemoryError | IllegalStateException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) view.showError(R.string.error_unable_to_read_sound_file);
						}
					});
				}
			}
		});
	}

	private void updatePath(Context context) {
		if (view != null) {
			if (selectedTab == TAB_PRIVATE_DIR) {
				view.updatePath(fileRepository.getPrivateDir(context).getAbsolutePath());
				view.showSelectedPrivateDir();
			} else {
				view.updatePath(fileRepository.getPublicDir().getAbsolutePath());
				view.showSelectedPublicDir();
			}
		}
	}
}
