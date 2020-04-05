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
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.util.AndroidUtils;

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
			view.showProgress();
		}
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) {
							view.hideProgress();
						}
					}
				});
			}
		});
		if (view != null) {
			view.showStoreInPublicDir(prefs.isStoreDirPublic());
			view.showRecordInStereo(prefs.getRecordChannelCount() == AppConstants.RECORD_AUDIO_STEREO);
			int format = prefs.getFormat();
			view.showRecordingFormat(format);
			if (format == AppConstants.RECORDING_FORMAT_WAV) {
				view.hideBitrateSelector();
			} else {
				view.showBitrateSelector();
			}
			view.showNamingFormat(prefs.getNamingFormat());
		}


		int pos;
		switch (prefs.getSampleRate()) {
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

		switch (prefs.getBitrate()) {
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
	public void recordInStereo(boolean stereo) {
		prefs.setRecordInStereo(stereo);
	}

	@Override
	public void setRecordingBitrate(int pos) {
		int rate;
		switch (pos) {
			case 0:
				rate = AppConstants.RECORD_ENCODING_BITRATE_24000;
				break;
			case 1:
			default:
				rate = AppConstants.RECORD_ENCODING_BITRATE_48000;
				break;
			case 2:
				rate = AppConstants.RECORD_ENCODING_BITRATE_96000;
				break;
			case 3:
				rate = AppConstants.RECORD_ENCODING_BITRATE_128000;
				break;
			case 4:
				rate = AppConstants.RECORD_ENCODING_BITRATE_192000;
				break;
		}
		prefs.setBitrate(rate);
	}

	@Override
	public void setRecordingFormat(int format) {
		prefs.setFormat(format);
		if (view != null) {
			if (format == AppConstants.RECORDING_FORMAT_WAV) {
				view.hideBitrateSelector();
			} else {
				view.showBitrateSelector();
			}
		}
	}

	@Override
	public void setNamingFormat(int format) {
		prefs.setNamingFormat(format);
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
		prefs.setSampleRate(rate);
	}

	@Override
	public void executeFirstRun() {
		if (prefs.isFirstRun()) {
			prefs.firstRunExecuted();
		}
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
}
