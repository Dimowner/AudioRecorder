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

import com.dimowner.audiorecorder.Contract;

public class SetupContract {

	interface View extends Contract.View {

		void showRecordingBitrate(int bitrate);

		void showSampleRate(int rate);

		void showChannelCount(int count);

		void showNamingFormat(String namingKey);

		void showRecordingFormat(String formatKey);

		void showBitrateSelector();

		void hideBitrateSelector();

		void showInformation(int infoResId);

		void showSizePerMin(String size);

		void updateRecordingInfo(String format);
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SetupContract.View> {

		void loadSettings();

		void setSettingRecordingBitrate(int bitrate);

		void setSettingSampleRate(int rate);

		void setSettingChannelCount(int count);

		void setSettingThemeColor(String colorKey);

		void setSettingNamingFormat(String namingKey);

		void setSettingRecordingFormat(String formatKey);

		void executeFirstRun();

		void resetSettings();
	}
}
