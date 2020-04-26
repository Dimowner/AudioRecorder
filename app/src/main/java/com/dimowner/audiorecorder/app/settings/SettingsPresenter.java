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

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.List;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;

	public SettingsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									 BackgroundQueue recordingsTasks, final BackgroundQueue loadingTasks, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
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
			view.showStoreInPublicDir(prefs.isStoreDirPublic());
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


		int pos;
		switch (prefs.getSettingSampleRate()) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				pos = 0;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				pos = 1;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				pos = 2;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				pos = 4;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
			default:
				pos = 3;
		}
		if (view != null) {
			view.showRecordingSampleRate(pos);
		}

		switch (prefs.getSettingBitrate()) {
			case AppConstants.RECORD_ENCODING_BITRATE_24000:
				pos = 0;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_48000:
			default:
				pos = 1;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_96000:
				pos = 2;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_128000:
				pos = 3;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_192000:
				pos = 4;
				break;
		}
		if (view != null) {
			view.showRecordingBitrate(pos);
		}
	}

	@Override
	public void storeInPublicDir(boolean b) {
		prefs.setStoreDirPublic(b);
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
		view.showInformation(R.string.info_bitrate);
		updateAvailableSpace();
	}

	@Override
	public void setSettingSampleRate(int rate) {
		prefs.setSettingSampleRate(rate);
		view.showInformation(R.string.info_frequency);
		updateAvailableSpace();
	}

	@Override
	public void setSettingChannelCount(int count) {
		prefs.setSettingChannelCount(count);
		view.showInformation(R.string.info_channels);
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
		updateRecordingFormat(formatKey);
		view.showInformation(R.string.info_format);
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
	public void bindView(SettingsContract.View view) {
		this.view = view;
	}

	@Override
	public void unbindView() {
		if (view != null) {
			this.view = null;
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	private void updateAvailableSpace() {
		final long space = FileUtil.getFree(fileRepository.getRecordingDir());
		final long time = spaceToTimeSecs(space, prefs.getSettingRecordingFormat(),
				prefs.getSettingSampleRate(), prefs.getSettingBitrate(), prefs.getSettingChannelCount());
		if (view != null) {
			view.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(time));
		}
	}

	private long spaceToTimeSecs(long spaceBytes, String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_M4A:
				return 1000 * (spaceBytes/(bitrate/8));
			case AppConstants.FORMAT_WAV:
				return 1000 * (spaceBytes/(sampleRate * channels * 2));
			default:
				return 0;
		}
	}

	private void updateRecordingFormat(String formatKey) {
		switch (formatKey) {
			case AppConstants.FORMAT_WAV:
				view.hideBitrateSelector();
				view.showInformation(R.string.info_wav);
				break;
			case AppConstants.FORMAT_M4A:
				view.showInformation(R.string.info_m4a);
			case AppConstants.FORMAT_3GP:
			default:
				view.showBitrateSelector();
		}
	}
}
