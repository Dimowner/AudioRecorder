package com.dimowner.audiorecorder.ui.settings;

import com.dimowner.audiorecorder.Contract;

public class SettingsContract {

	interface View extends Contract.View {

		void showSelectedThemeColor(int colorRes);

		void showRecordingQuality(int quality);

		void showRecordingChannelsCount(int count);

		void showAllRecordsDeleted();

		void showFailDeleteAllRecords();
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SettingsContract.View> {

		void loadSettings();

		void setThemeColor(int colorRes);

		void setRecordingQuality(int quality);

		void setRecordingChannelCount(int count);

		void deleteAllRecords();
	}
}
