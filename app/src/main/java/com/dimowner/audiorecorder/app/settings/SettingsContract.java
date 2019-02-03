package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.Contract;

public class SettingsContract {

	interface View extends Contract.View {

		void showStoreInPublicDir(boolean b);

		void showKeepScreenOn(boolean b);
		void showRecordInStereo(boolean b);

		void showRecordingBitrate(int bitrate);

		void showRecordingSampleRate(int rate);

		void showRecordingFormat(int format);

		void showAllRecordsDeleted();

		void showFailDeleteAllRecords();

		void showTotalRecordsDuration(String duration);
		void showRecordsCount(int count);
		void showAvailableSpace(String space);

		void showBitrateSelector();
		void hideBitrateSelector();
	}

	public interface UserActionsListener extends Contract.UserActionsListener<SettingsContract.View> {

		void loadSettings();

		void storeInPublicDir(boolean b);

		void keepScreenOn(boolean b);

		void recordInStereo(boolean stereo);

		void setRecordingBitrate(int bitrate);

		void setRecordingFormat(int format);

		void setSampleRate(int rate);

		void deleteAllRecords();
	}
}
