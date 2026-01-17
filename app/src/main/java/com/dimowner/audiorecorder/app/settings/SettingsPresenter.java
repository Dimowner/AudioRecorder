/*
 * Copyright 2020 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.app.settings;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderCallback;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private final DecimalFormat decimalFormat;

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;
	private final SettingsMapper settingsMapper;
	private final AppRecorder appRecorder;
	private final Context appContext;
	private AppRecorderCallback appRecorderCallback;

	public SettingsPresenter(final Context context, final LocalRepository localRepository, final FileRepository fileRepository,
					 final BackgroundQueue recordingsTasks, final BackgroundQueue loadingTasks,
					 final Prefs prefs,  final SettingsMapper settingsMapper,  final AppRecorder appRecorder) {
		this.appContext = context.getApplicationContext();
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
		this.settingsMapper = settingsMapper;
		this.appRecorder = appRecorder;

		DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.getDefault());
		formatSymbols.setDecimalSeparator('.');
		decimalFormat = new DecimalFormat("#.#", formatSymbols);
	}

	@Override
	public void loadSettings() {
		if (view != null) {
			view.showProgress();
		}
		loadingTasks.postRunnable(() -> {
			final List<Long> durations = localRepository.getRecordsDurations();
			long totalDuration = 0;
			for (int i = 0; i < durations.size(); i++) {
				totalDuration += durations.get(i);
			}
			final long finalTotalDuration = totalDuration;
			AndroidUtils.runOnUIThread(() -> {
				if (view != null) {
					view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
					view.showRecordsCount(durations.size());
					updateAvailableSpace();
					view.hideProgress();
				}
			});

//			Public storage no longer available we can not migrate with target API 30.
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//				boolean isPublicStorageMigrated = !prefs.isPublicStorageMigrated()
//						&& localRepository.hasRecordsWithPath(fileRepository.getPublicDir().getAbsolutePath());
//				AndroidUtils.runOnUIThread(() -> {
//					if (view != null) {
//						view.showMigratePublicStorage(isPublicStorageMigrated);
//					}
//				});
//			}
		});
		if (view != null) {
			view.updateRecordingInfo(prefs.getSettingRecordingFormat());

			// Show current storage location in the spinner
			view.showStorageLocation(prefs.getStorageLocation());

			File currentDir = fileRepository.getRecordingDir();
			if (currentDir != null) {
				view.showRecordsLocation(currentDir.getAbsolutePath());
			}

			view.showAskToRenameAfterRecordingStop(prefs.isAskToRenameAfterStopRecording());
			view.showKeepScreenOn(prefs.isKeepScreenOn());
			view.showChannelCount(prefs.getSettingChannelCount());
			String recordingFormatKey = prefs.getSettingRecordingFormat();
			view.showRecordingFormat(recordingFormatKey);
			updateRecordingFormat(recordingFormatKey);
			view.showNamingFormat(prefs.getSettingNamingFormat());
			view.showRecordingBitrate(prefs.getSettingBitrate());
			view.showRecordingSampleRate(prefs.getSettingSampleRate());
		}
	}

	@Override
	public void setStorageLocation(Context context, int location) {
		prefs.setStorageLocation(location);
		fileRepository.updateRecordingDir(context, prefs);
		if (view != null) {
			File dir = fileRepository.getRecordingDir();
			if (dir != null) {
				view.showRecordsLocation(dir.getAbsolutePath());
			} else {
				view.showError(R.string.error_unable_to_use_directory);
			}
		}
		updateAvailableSpace();
	}

	@Override
	public void storeInPublicDir(Context context, boolean b) {
		if (b) {
			prefs.setStoreInSdCard(false);
		}
		prefs.setStoreDirPublic(b);
		fileRepository.updateRecordingDir(context, prefs);
		if (view != null) {
			view.showStoreInPublicDir(b);
			File dir = fileRepository.getRecordingDir();
			if (dir != null) {
				view.showRecordsLocation(dir.getAbsolutePath());
			} else if (b) {
				view.showError(R.string.error_unable_to_use_directory);
			}
			view.showSdCardStorage(isSdCardAvailable(), prefs.isStoreInSdCard(), getSdCardDefaultPath());
			view.showDirectorySetting(prefs.isShowDirectorySetting());
		}
		updateAvailableSpace();
	}

	@Override
	public void setCustomPublicDir(Context context, String path) {
		String normalized = path == null ? "" : path.trim();
		if (TextUtils.isEmpty(normalized)) {
			prefs.setPublicDirectoryPath(null);
			fileRepository.updateRecordingDir(context, prefs);
			if (view != null) {
				File dir = fileRepository.getRecordingDir();
				if (dir != null) {
					view.showRecordsLocation(dir.getAbsolutePath());
					view.showMessage(R.string.records_directory_reset);
					updateAvailableSpace();
				}
			}
			return;
		}

		File customDir = buildPublicDir(normalized);
		File ensured = FileUtil.createDir(customDir);
		File targetDir = ensured != null ? ensured : customDir;
		if (!targetDir.exists() || !targetDir.isDirectory()) {
			if (view != null) {
				view.showError(R.string.error_unable_to_use_directory);
			}
			return;
		}

		prefs.setPublicDirectoryPath(targetDir.getAbsolutePath());
		fileRepository.updateRecordingDir(context, prefs);
		File resolvedDir = fileRepository.getRecordingDir();
		if (view != null) {
			if (resolvedDir != null && targetDir.getAbsolutePath().equals(resolvedDir.getAbsolutePath())) {
				view.showRecordsLocation(resolvedDir.getAbsolutePath());
				updateAvailableSpace();
			} else {
				view.showError(R.string.error_unable_to_use_directory);
			}
		}
	}

	@Override
	public void storeInSdCard(Context context, boolean useSdCard) {
		boolean available = isSdCardAvailable();
		if (useSdCard && !available) {
			if (view != null) {
				view.showError(R.string.sd_card_not_available);
				view.showSdCardStorage(false, false, "");
			}
			return;
		}

		prefs.setStoreInSdCard(useSdCard);
		if (useSdCard) {
			prefs.setStoreDirPublic(false);
		}

		fileRepository.updateRecordingDir(context, prefs);
		File dir = fileRepository.getRecordingDir();
		if (view != null) {
			view.showStoreInPublicDir(prefs.isStoreDirPublic());
			if (dir != null) {
				view.showRecordsLocation(dir.getAbsolutePath());
			} else if (useSdCard) {
				view.showError(R.string.error_unable_to_use_directory);
			}
			String location = dir != null ? dir.getAbsolutePath() : getSdCardDefaultPath();
			view.showSdCardStorage(available, useSdCard, location);
			view.showDirectorySetting(prefs.isShowDirectorySetting());
		}
		updateAvailableSpace();
	}

	@Override
	public void setSafTreeUri(Context context, String uriString) {
		prefs.setSafTreeUri(uriString);
	}

	@Override
	public void keepScreenOn(boolean keep) {
		prefs.setKeepScreenOn(keep);
	}

	@Override
	public void askToRenameAfterRecordingStop(boolean b) {
		prefs.setAskToRenameAfterStopRecording(b);
	}

	@Override
	public void setSettingRecordingBitrate(int bitrate) {
		prefs.setSettingBitrate(bitrate);
		updateAvailableSpace();
	}

	@Override
	public void setSettingSampleRate(int rate) {
		prefs.setSettingSampleRate(rate);
		updateAvailableSpace();
	}

	@Override
	public void setSettingChannelCount(int count) {
		prefs.setSettingChannelCount(count);
		updateAvailableSpace();
	}

	@Override
	public void setSettingThemeColor(String colorKey) {
		prefs.setSettingThemeColor(colorKey);
	}

	@Override
	public void setSettingNamingFormat(String namingKey) {
		prefs.setSettingNamingFormat(namingKey);
	}

	@Override
	public void setSettingRecordingFormat(String formatKey) {
		prefs.setSettingRecordingFormat(formatKey);
		if (view != null) {
			view.updateRecordingInfo(formatKey);
			view.showChannelCount(prefs.getSettingChannelCount());
		}
		updateRecordingFormat(formatKey);
		updateAvailableSpace();
	}

	@Override
	public void deleteAllRecords() {
		recordingsTasks.postRunnable(() -> {
//				List<Record> records  = localRepository.getAllRecords();
//				for (int i = 0; i < records.size(); i++) {
//					fileRepository.deleteRecordFile(records.get(i).getPath());
//				}
//				boolean b2 = localRepository.deleteAllRecords();
//				prefs.setActiveRecord(-1);
//				if (b2) {
//					AndroidUtils.runOnUIThread(new Runnable() {
//						@Override
//						public void run() {
//							if (view != null) {
//								view.showAllRecordsDeleted();
//							}
//						}});
//				} else {
//					AndroidUtils.runOnUIThread(new Runnable() {
//						@Override
//						public void run() {
//							if (view != null) {
//								view.showFailDeleteAllRecords();
//							}
//						}});
//				}
		});
	}

	@Override
	public void bindView(final SettingsContract.View view) {
		this.view = view;

		if (appRecorder.isRecording()) {
			view.disableAudioSettings();
			appRecorderCallback = new AppRecorderCallback() {
				@Override public void onRecordingStarted(File file) { }
				@Override public void onRecordingPaused() { }
				@Override public void onRecordingResumed() { }
				@Override public void onRecordingStopped(File file, Record record) {
						view.enableAudioSettings();
				}
				@Override public void onRecordingProgress(long mills, int amp) { }
				@Override public void onError(AppException throwable) { }
			};
			appRecorder.addRecordingCallback(appRecorderCallback);
		} else {
			view.enableAudioSettings();
		}
	}

	@Override
	public void unbindView() {
		if (view != null) {
			this.view = null;
			appRecorder.removeRecordingCallback(appRecorderCallback);
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	@Override
	public void resetSettings() {
		prefs.resetSettings();
	}

	@Override
	public void onRecordsLocationClick() {
		if (view != null) {
			view.openRecordsLocation(fileRepository.getRecordingDir());
		}
	}

	private File buildPublicDir(String input) {
		File dir = new File(input);
		if (!dir.isAbsolute()) {
			dir = new File(Environment.getExternalStorageDirectory(), input);
		}
		return dir;
	}

	private boolean isSdCardAvailable() {
		File[] dirs = ContextCompat.getExternalFilesDirs(appContext, Environment.DIRECTORY_MUSIC);
		if (dirs != null) {
			for (int i = 1; i < dirs.length; i++) {
				if (dirs[i] != null) {
					return true;
				}
			}
		}
		return false;
	}

	private String getSdCardDefaultPath() {
		File musicDir = FileUtil.getSecondaryExternalMusicDir(appContext);
		if (musicDir != null) {
			File records = new File(musicDir, AppConstants.RECORDS_DIR);
			return records.getAbsolutePath();
		}
		return "";
	}

	private void updateAvailableSpace() {
		final long space = FileUtil.getFree(fileRepository.getRecordingDir());
		String format = prefs.getSettingRecordingFormat();
		int sampleRate = prefs.getSettingSampleRate();
		if (format.equals(AppConstants.FORMAT_3GP)) {
			if (view != null) {
				view.showAvailableSpace(
						TimeUtils.formatTimeIntervalHourMinSec(
								spaceToTimeSecs(space, format, sampleRate,
										AppConstants.RECORD_ENCODING_BITRATE_12000, AppConstants.RECORD_AUDIO_MONO)
						)
				);
				view.showSizePerMin(
						decimalFormat.format(
								sizePerMin(format, sampleRate, AppConstants.RECORD_ENCODING_BITRATE_12000,
										AppConstants.RECORD_AUDIO_MONO) / 1000000f
						)
				);
				view.showInformation(settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
						+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
						+ settingsMapper.formatBitrate(AppConstants.RECORD_ENCODING_BITRATE_12000 / 1000) + AppConstants.SEPARATOR
						+ settingsMapper.convertChannelsToString(AppConstants.RECORD_AUDIO_MONO));
			}
		} else {
			int bitrate = prefs.getSettingBitrate();
			int channelsCount = prefs.getSettingChannelCount();
			final long time = spaceToTimeSecs(space, format, sampleRate, bitrate, channelsCount);
			if (view != null) {
				view.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(time));
				view.showSizePerMin(decimalFormat.format(sizePerMin(format, sampleRate, bitrate, channelsCount) / 1000000f));
				switch (format) {
					default:
					case AppConstants.FORMAT_M4A:
						view.showInformation(settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
								+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
								+ settingsMapper.convertBitratesToString(bitrate) + AppConstants.SEPARATOR
								+ settingsMapper.convertChannelsToString(channelsCount));
						break;
					case AppConstants.FORMAT_WAV:
						view.showInformation(settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
								+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
								+ settingsMapper.convertChannelsToString(channelsCount));
						break;
				}
			}
		}
	}

	private long spaceToTimeSecs(long spaceBytes, String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_M4A:
			case AppConstants.FORMAT_3GP:
				return 1000 * (spaceBytes/(bitrate/8));
			case AppConstants.FORMAT_WAV:
				return 1000 * (spaceBytes/((long) sampleRate * channels * 2));
			default:
				return 0;
		}
	}

	private long sizePerMin(String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_M4A:
			case AppConstants.FORMAT_3GP:
				return 60L * (bitrate/8);
			case AppConstants.FORMAT_WAV:
				return 60 * ((long) sampleRate * channels * 2);
			default:
				return 0;
		}
	}

	private void updateRecordingFormat(String formatKey) {
		switch (formatKey) {
			case AppConstants.FORMAT_WAV:
			case AppConstants.FORMAT_3GP:
				view.hideBitrateSelector();
				break;
			case AppConstants.FORMAT_M4A:
			default:
				view.showBitrateSelector();
		}
	}
}
