package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.Contract;

public class SettingsContract {

	interface View extends Contract.View {

		void showSelectedThemeColor(int colorRes);

		void showRecordingQuality(int quality);

		void showRecordingChannelsCount(int count);

		void showAllRecordsDeleted();

		void showFailDeleteAllRecords();

		void showTotalRecordsDuration(String duration);
		void showRecordsCount(int count);
		void showAvailableSpace(String space);
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SettingsContract.View> {

		void loadSettings();

		void setThemeColor(int colorRes);

		void setRecordingQuality(int quality);

		void setRecordingChannelCount(int count);

		void deleteAllRecords();
	}
}
