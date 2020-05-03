/*
 * Copyright 2018 Dmitriy Ponomarenko
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

	boolean isFirstRun();
	void firstRunExecuted();

	boolean isStoreDirPublic();
	void setStoreDirPublic(boolean b);

	boolean isAskToRenameAfterStopRecording();
	boolean hasAskToRenameAfterStopRecordingSetting();
	void setAskToRenameAfterStopRecording(boolean b);

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
