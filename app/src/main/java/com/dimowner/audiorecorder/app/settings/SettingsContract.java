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

import com.dimowner.audiorecorder.Contract;

import java.io.File;

public class SettingsContract {

	interface View extends Contract.View {

		void showStorageLocation(int location);

		void showStoreInPublicDir(boolean b);

		void showDirectorySetting(boolean b);

		void showMigratePublicStorage(boolean b);

		void showKeepScreenOn(boolean b);

		void showAskToRenameAfterRecordingStop(boolean b);

		void showRecordingBitrate(int bitrate);

		void showRecordingSampleRate(int rate);

		void showRecordingFormat(String formatKey);

		void showNamingFormat(String namingKey);

		void showChannelCount(int count);

		void showAllRecordsDeleted();

		void showFailDeleteAllRecords();

		void showTotalRecordsDuration(String duration);
		void showRecordsCount(int count);
		void showAvailableSpace(String space);

		void showBitrateSelector();
		void hideBitrateSelector();

		void showDialogPublicDirInfo();

		void showDialogPrivateDirInfo();

		void updateRecordingInfo(String format);

		void showSizePerMin(String size);
		void showInformation(String info);

		void showRecordsLocation(String location);
		void hideRecordsLocation();
		void openRecordsLocation(File file);
		void showSdCardStorage(boolean available, boolean checked, String location);
		void hideSdCardStorage();

		void enableAudioSettings();
		void disableAudioSettings();
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SettingsContract.View> {

		void loadSettings();

		void setStorageLocation(Context context, int location);

		void storeInPublicDir(Context context, boolean b);

		void setCustomPublicDir(Context context, String path);

		void storeInSdCard(Context context, boolean b);

		/**
		 * Set the SAF tree URI for SD card access.
		 */
		void setSafTreeUri(Context context, String uriString);

		void keepScreenOn(boolean b);

		void askToRenameAfterRecordingStop(boolean b);

		void setSettingRecordingBitrate(int bitrate);

		void setSettingSampleRate(int rate);

		void setSettingChannelCount(int count);

		void setSettingThemeColor(String colorKey);

		void setSettingNamingFormat(String namingKey);

		void setSettingRecordingFormat(String formatKey);

		void deleteAllRecords();

		void resetSettings();

		void onRecordsLocationClick();
	}
}
