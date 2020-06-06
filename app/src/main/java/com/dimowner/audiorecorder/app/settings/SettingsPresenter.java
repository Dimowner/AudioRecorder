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

package com.dimowner.audiorecorder.app.settings;

import android.content.Context;

import com.dimowner.audiorecorder.AppConstants;
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
import java.util.List;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private DecimalFormat decimalFormat = new DecimalFormat("#.#");

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;
	private final SettingsMapper settingsMapper;
	private final AppRecorder appRecorder;
	private AppRecorderCallback appRecorderCallback;

	public SettingsPresenter(final LocalRepository localRepository, final FileRepository fileRepository,
									 final BackgroundQueue recordingsTasks, final BackgroundQueue loadingTasks,
									 final Prefs prefs,  final SettingsMapper settingsMapper,  final AppRecorder appRecorder) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
		this.settingsMapper = settingsMapper;
		this.appRecorder = appRecorder;
	}

	@Override
	public void loadSettings() {
		if (view != null) {
			view.showProgress();
		}
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final List<Long> durations = localRepository.getRecordsDurations();
				long totalDuration = 0;
				for (int i = 0; i < durations.size(); i++) {
					totalDuration += durations.get(i);
				}
				final long finalTotalDuration = totalDuration;
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) {
							view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
							view.showRecordsCount(durations.size());
							updateAvailableSpace();
							view.hideProgress();
						}
					}
				});
			}
		});
		if (view != null) {
			view.updateRecordingInfo(prefs.getSettingRecordingFormat());

			view.showStoreInPublicDir(prefs.isStoreDirPublic());
			if (prefs.isStoreDirPublic()) {
				view.showRecordsLocation(fileRepository.getRecordingDir().getAbsolutePath());
			} else {
				view.hideRecordsLocation();
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
	public void storeInPublicDir(Context context, boolean b) {
		prefs.setStoreDirPublic(b);
		fileRepository.updateRecordingDir(context, prefs);
		if (b) {
			view.showRecordsLocation(fileRepository.getRecordingDir().getAbsolutePath());
		} else {
			view.hideRecordsLocation();
		}
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
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
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
			}
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
				@Override public void onRecordProcessing() { }
				@Override public void onRecordFinishProcessing() { }
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
				return 1000 * (spaceBytes/(sampleRate * channels * 2));
			default:
				return 0;
		}
	}

	private long sizePerMin(String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_M4A:
			case AppConstants.FORMAT_3GP:
				return 60 * (bitrate/8);
			case AppConstants.FORMAT_WAV:
				return 60 * (sampleRate * channels * 2);
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
