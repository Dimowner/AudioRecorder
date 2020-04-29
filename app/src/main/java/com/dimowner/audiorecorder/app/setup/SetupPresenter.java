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
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.Prefs;

import java.text.DecimalFormat;

public class SetupPresenter implements SetupContract.UserActionsListener {

	private DecimalFormat decimalFormat = new DecimalFormat("#.#");

	private SetupContract.View view;

	private final Prefs prefs;

	public SetupPresenter(Prefs prefs) {
		this.prefs = prefs;
	}

	@Override
	public void loadSettings() {
		if (view != null) {
			view.showChannelCount(prefs.getSettingChannelCount());
			String recordingFormatKey = prefs.getSettingRecordingFormat();
			view.showRecordingFormat(recordingFormatKey);
			updateRecordingFormat(recordingFormatKey);
			view.showNamingFormat(prefs.getSettingNamingFormat());
			view.showRecordingBitrate(prefs.getSettingBitrate());
			view.showSampleRate(prefs.getSettingSampleRate());
			updateSizePerMin();
		}
	}

	@Override
	public void setSettingRecordingBitrate(int bitrate) {
		prefs.setSettingBitrate(bitrate);
		view.showInformation(R.string.info_bitrate);
		updateSizePerMin();
	}

	@Override
	public void setSettingSampleRate(int rate) {
		prefs.setSettingSampleRate(rate);
		view.showInformation(R.string.info_frequency);
		updateSizePerMin();
	}

	@Override
	public void setSettingChannelCount(int count) {
		prefs.setSettingChannelCount(count);
		view.showInformation(R.string.info_channels);
		updateSizePerMin();
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
		updateSizePerMin();
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

	private void updateSizePerMin() {
		String format = prefs.getSettingRecordingFormat();
		int sampleRate = prefs.getSettingSampleRate();
		int bitrate = prefs.getSettingBitrate();
		int channelsCount = prefs.getSettingChannelCount();
		if (view != null) {
			view.showSizePerMin(decimalFormat.format(sizePerMin(format, sampleRate, bitrate, channelsCount)/1000000f));
		}
	}

	private long sizePerMin(String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_M4A:
				return 60 * (bitrate/8);
			case AppConstants.FORMAT_WAV:
				return 60 * (sampleRate * channels * 2);
			default:
				return 0;
		}
	}
}
