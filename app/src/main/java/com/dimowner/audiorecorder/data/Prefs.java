/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.data;

public interface Prefs {

	// Storage location constants - all use public (persistent) paths
	int STORAGE_INTERNAL = 0;  // /storage/emulated/0/AudioRecorder
	int STORAGE_SDCARD = 1;    // /storage/<sdcard>/AudioRecorder (via SAF)
	int STORAGE_CUSTOM = 2;    // User-specified path

	boolean isFirstRun();

	/**
	 * Get the SAF tree URI for SD card access.
	 * @return SAF tree URI string, or null if not set
	 */
	String getSafTreeUri();

	/**
	 * Set the SAF tree URI for SD card access.
	 * @param uri SAF tree URI string, or null to clear
	 */
	void setSafTreeUri(String uri);
	void firstRunExecuted();

	/**
	 * Get the storage location type.
	 * @return One of STORAGE_INTERNAL, STORAGE_SDCARD, or STORAGE_CUSTOM
	 */
	int getStorageLocation();

	/**
	 * Set the storage location type.
	 * @param location One of STORAGE_INTERNAL, STORAGE_SDCARD, or STORAGE_CUSTOM
	 */
	void setStorageLocation(int location);

	// Deprecated: use getStorageLocation() instead
	boolean isStoreDirPublic();
	void setStoreDirPublic(boolean b);

	// Deprecated: use getStorageLocation() instead
	boolean isStoreInSdCard();
	void setStoreInSdCard(boolean b);

	/**
	 * @return Absolute path to the custom public recordings directory or empty string when default should be used.
	 */
	String getPublicDirectoryPath();

	/**
	 * Persist absolute path to the custom public recordings directory. Passing {@code null} or empty string clears the custom value.
	 */
	void setPublicDirectoryPath(String path);

	//This is needed for scoped storage support
	boolean isShowDirectorySetting();

	boolean isAskToRenameAfterStopRecording();
	boolean hasAskToRenameAfterStopRecordingSetting();
	void setAskToRenameAfterStopRecording(boolean b);

	void setPublicStorageMigrated(boolean b);
	boolean isPublicStorageMigrated();
	long getLastPublicStorageMigrationAsked();
	void setLastPublicStorageMigrationAsked(long time);

	long getActiveRecord();
	void setActiveRecord(long id);

	long getRecordCounter();
	void incrementRecordCounter();

	void setKeepScreenOn(boolean on);
	boolean isKeepScreenOn();

	void setRecordOrder(int order);
	int getRecordsOrder();

	boolean isMigratedSettings();
	void migrateSettings();

	boolean isMigratedDb3();
	void migrateDb3Finished();

	void setSettingThemeColor(String colorKey);
	String getSettingThemeColor();

	void setSettingNamingFormat(String nameKay);
	String getSettingNamingFormat();

	void setSettingRecordingFormat(String formatKey);
	String getSettingRecordingFormat();

	void setSettingSampleRate(int sampleRate);
	int getSettingSampleRate();

	void setSettingBitrate(int rate);
	int getSettingBitrate();

	void setSettingChannelCount(int count);
	int getSettingChannelCount();

	void resetSettings();
}
