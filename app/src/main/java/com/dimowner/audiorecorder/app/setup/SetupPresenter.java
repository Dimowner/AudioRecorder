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
			view.updateRecordingInfo(prefs.getSettingRecordingFormat());
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
		showBitrateInfo(bitrate);
		updateSizePerMin();
	}

	private void showBitrateInfo(int bitrate) {
		int text = -1;
		switch (bitrate) {
			case AppConstants.RECORD_ENCODING_BITRATE_48000:
				text = R.string.info_bitrate_48;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_96000:
				text = R.string.info_bitrate_96;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_128000:
				text = R.string.info_bitrate_128;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_192000:
				text = R.string.info_bitrate_192;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_256000:
				text = R.string.info_bitrate_256;
				break;
		}
		if (view != null && text != -1) {
			view.showInformation(text);
		}
	}

	@Override
	public void setSettingSampleRate(int rate) {
		prefs.setSettingSampleRate(rate);
		showSampleRateInfo(rate);
		updateSizePerMin();
	}

	private void showSampleRateInfo(int sampleRate) {
		int text = -1;
		switch (sampleRate) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				text = R.string.info_sample_rate_8k;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				text = R.string.info_sample_rate_16k;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_22050:
				text = R.string.info_sample_rate_22k;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				text = R.string.info_sample_rate_32k;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
				text = R.string.info_sample_rate_44k;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				text = R.string.info_sample_rate_48k;
				break;
		}
		if (view != null && text != -1) {
			view.showInformation(text);
		}
	}

	@Override
	public void setSettingChannelCount(int count) {
		prefs.setSettingChannelCount(count);
		if (view != null) {
			view.showInformation(R.string.info_channels);
		}
		updateSizePerMin();
		switch (count) {
			case AppConstants.RECORD_AUDIO_STEREO:
				if (view != null) {
					view.showInformation(R.string.info_stereo);
				}
				break;
			case AppConstants.RECORD_AUDIO_MONO:
				if (view != null) {
					view.showInformation(R.string.info_mono);
				}
				break;
		}
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
		switch (formatKey) {
			case AppConstants.FORMAT_WAV:
				if (view != null) {
					view.showInformation(R.string.info_wav);
				}
				break;
			case AppConstants.FORMAT_M4A:
				if (view != null) {
					view.showInformation(R.string.info_m4a);
				}
				break;
			case AppConstants.FORMAT_3GP:
				if (view != null) {
					view.showInformation(R.string.info_3gp);
				}
				break;
		}
		if (view != null) {
			view.updateRecordingInfo(formatKey);
		}
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
			case AppConstants.FORMAT_3GP:
			case AppConstants.FORMAT_WAV:
				if (view != null) {
					view.hideBitrateSelector();
					view.showInformation(R.string.info_wav);
				}
				break;
			case AppConstants.FORMAT_M4A:
				if (view != null) {
					view.showInformation(R.string.info_m4a);
				}
			default:
				if (view != null) {
					view.showBitrateSelector();
				}
		}
	}

	private void updateSizePerMin() {
		String format = prefs.getSettingRecordingFormat();
		int sampleRate = prefs.getSettingSampleRate();
		if (format.equals(AppConstants.FORMAT_3GP)) {
			view.showSizePerMin(
					decimalFormat.format(
							sizePerMin(format, sampleRate, AppConstants.RECORD_ENCODING_BITRATE_12000,
									AppConstants.RECORD_AUDIO_MONO) / 1000000f
					)
			);
		} else {
			int bitrate = prefs.getSettingBitrate();
			int channelsCount = prefs.getSettingChannelCount();
			if (view != null) {
				view.showSizePerMin(decimalFormat.format(sizePerMin(format, sampleRate, bitrate, channelsCount) / 1000000f));
			}
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
}
