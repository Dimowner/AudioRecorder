package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.Contract;

public class SettingsContract {

	interface View extends Contract.View {

		void showStoreInPublicDir(boolean b);

		void showKeepScreenOn(boolean b);
		void showRecordInStereo(boolean b);

		void showRecordingQuality(int quality);

		void showAllRecordsDeleted();

		void showFailDeleteAllRecords();

		void showTotalRecordsDuration(String duration);
		void showRecordsCount(int count);
		void showAvailableSpace(String space);
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SettingsContract.View> {

		void loadSettings();

		void storeInPublicDir(boolean b);

		void keepScreenOn(boolean b);

		void recordInStereo(boolean stereo);

		void setRecordingQuality(int quality);

		void deleteAllRecords();
	}
}
