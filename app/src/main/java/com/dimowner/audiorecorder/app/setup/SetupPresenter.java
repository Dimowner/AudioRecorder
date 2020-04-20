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

package com.dimowner.audiorecorder.app.setup;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.Prefs;

public class SetupPresenter implements SetupContract.UserActionsListener {

	private SetupContract.View view;

	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;

	public SetupPresenter(final BackgroundQueue loadingTasks, Prefs prefs) {
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
	}

	@Override
	public void loadSettings() {
		if (view != null) {
			view.showChannelCount(prefs.getRecordChannelCount());
			String recordingFormatKey = prefs.getSettingRecordingFormat();
			view.showRecordingFormat(recordingFormatKey);
			updateRecordingFormat(recordingFormatKey);
			view.showNamingFormat(prefs.getSettingNamingFormat());
			view.showRecordingBitrate(prefs.getSettingBitrate());
			view.showSampleRate(prefs.getSettingSampleRate());
		}
	}

	@Override
	public void setSettingRecordingBitrate(int bitrate) {
		prefs.setSettingBitrate(bitrate);
	}

	@Override
	public void setSampleRate(int pos) {
		int rate;
		switch (pos) {
			case 0:
				rate = AppConstants.RECORD_SAMPLE_RATE_8000;
				break;
			case 1:
				rate = AppConstants.RECORD_SAMPLE_RATE_16000;
				break;
			case 2:
				rate = AppConstants.RECORD_SAMPLE_RATE_32000;
				break;
			case 4:
				rate = AppConstants.RECORD_SAMPLE_RATE_48000;
				break;
			case 3:
			default:
				rate = AppConstants.RECORD_SAMPLE_RATE_44100;
		}
		prefs.setSettingSampleRate(rate);
	}

	@Override
	public void setSettingSampleRate(int rate) {
		prefs.setSettingSampleRate(rate);
	}

	@Override
	public void setSettingChannelCount(int count) {
		prefs.setSettingChannelCount(count);
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
	}

	@Override
	public void executeFirstRun() {
		if (prefs.isFirstRun()) {
			prefs.firstRunExecuted();
		}
	}

	@Override
	public void resetSettings() {
		prefs.resetSettings();
	}

	@Override
	public void bindView(SetupContract.View view) {
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
