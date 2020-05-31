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

import android.content.Context;
import android.content.SharedPreferences;

import com.dimowner.audiorecorder.AppConstants;

/**
 * App preferences implementation
 */
public class PrefsImpl implements Prefs {

	private static final String PREF_NAME = "com.dimowner.audiorecorder.data.PrefsImpl";

	private static final String PREF_KEY_IS_FIRST_RUN = "is_first_run";
	private static final String PREF_KEY_IS_MIGRATED = "is_migrated";
	private static final String PREF_KEY_IS_MIGRATED_DB3 = "is_migrated_db3";
	private static final String PREF_KEY_IS_STORE_DIR_PUBLIC = "is_store_dir_public";
	private static final String PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING = "is_ask_rename_after_stop_recording";
	private static final String PREF_KEY_ACTIVE_RECORD = "active_record";
	private static final String PREF_KEY_RECORD_COUNTER = "record_counter";
	private static final String PREF_KEY_THEME_COLORMAP_POSITION = "theme_color";
	private static final String PREF_KEY_KEEP_SCREEN_ON = "keep_screen_on";
	private static final String PREF_KEY_FORMAT = "pref_format";
	private static final String PREF_KEY_BITRATE = "pref_bitrate";
	private static final String PREF_KEY_SAMPLE_RATE = "pref_sample_rate";
	private static final String PREF_KEY_RECORDS_ORDER = "pref_records_order";
	private static final String PREF_KEY_NAMING_FORMAT = "pref_naming_format";

	//Recording prefs.
	private static final String PREF_KEY_RECORD_CHANNEL_COUNT = "record_channel_count";

	private static final String PREF_KEY_SETTING_THEME_COLOR = "setting_theme_color";
	private static final String PREF_KEY_SETTING_RECORDING_FORMAT = "setting_recording_format";
	private static final String PREF_KEY_SETTING_BITRATE = "setting_bitrate";
	private static final String PREF_KEY_SETTING_SAMPLE_RATE = "setting_sample_rate";
	private static final String PREF_KEY_SETTING_NAMING_FORMAT = "setting_naming_format";
	private static final String PREF_KEY_SETTING_CHANNEL_COUNT = "setting_channel_count";

	private SharedPreferences sharedPreferences;

	private volatile static PrefsImpl instance;

	private PrefsImpl(Context context) {
		sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
	}

	public static PrefsImpl getInstance(Context context) {
		if (instance == null) {
			synchronized (PrefsImpl.class) {
				if (instance == null) {
					instance = new PrefsImpl(context);
				}
			}
		}
		return instance;
	}

	@Override
	public boolean isFirstRun() {
		return !sharedPreferences.contains(PREF_KEY_IS_FIRST_RUN) || sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_RUN, false);
	}

	@Override
	public void firstRunExecuted() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_FIRST_RUN, false);
		editor.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, false);
		editor.putBoolean(PREF_KEY_IS_MIGRATED, true);
		editor.apply();
//		setStoreDirPublic(true);
	}

	@Override
	public boolean isStoreDirPublic() {
		return sharedPreferences.contains(PREF_KEY_IS_STORE_DIR_PUBLIC) && sharedPreferences.getBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, true);
	}

	@Override
	public void setStoreDirPublic(boolean b) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, b);
		editor.apply();
	}

	@Override
	public boolean isAskToRenameAfterStopRecording() {
		return sharedPreferences.contains(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING) && sharedPreferences.getBoolean(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING, true);
	}

	@Override
	public boolean hasAskToRenameAfterStopRecordingSetting() {
		return sharedPreferences.contains(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING);
	}

	@Override
	public void setAskToRenameAfterStopRecording(boolean b) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_ASK_TO_RENAME_AFTER_STOP_RECORDING, b);
		editor.apply();
	}

	@Override
	public long getActiveRecord() {
		return sharedPreferences.getLong(PREF_KEY_ACTIVE_RECORD, -1);
	}

	@Override
	public void setActiveRecord(long id) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PREF_KEY_ACTIVE_RECORD, id);
		editor.apply();
	}

	@Override
	public long getRecordCounter() {
		return sharedPreferences.getLong(PREF_KEY_RECORD_COUNTER, 0);
	}

	@Override
	public void incrementRecordCounter() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PREF_KEY_RECORD_COUNTER, getRecordCounter()+1);
		editor.apply();
	}

	private int getThemeColor() {
		return sharedPreferences.getInt(PREF_KEY_THEME_COLORMAP_POSITION, 0);
	}

	public int getRecordChannelCount() {
		return sharedPreferences.getInt(PREF_KEY_RECORD_CHANNEL_COUNT, AppConstants.RECORD_AUDIO_STEREO);
	}

	@Override
	public void setKeepScreenOn(boolean on) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_KEEP_SCREEN_ON, on);
		editor.apply();
	}

	@Override
	public boolean isKeepScreenOn() {
		return sharedPreferences.getBoolean(PREF_KEY_KEEP_SCREEN_ON, false);
	}

	public int getFormat() {
		return sharedPreferences.getInt(PREF_KEY_FORMAT, AppConstants.RECORDING_FORMAT_M4A);
	}

	private int getBitrate() {
		return sharedPreferences.getInt(PREF_KEY_BITRATE, AppConstants.RECORD_ENCODING_BITRATE_128000);
	}

	private int getSampleRate() {
		return sharedPreferences.getInt(PREF_KEY_SAMPLE_RATE, AppConstants.RECORD_SAMPLE_RATE_44100);
	}

	@Override
	public void setRecordOrder(int order) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_RECORDS_ORDER, order);
		editor.apply();
	}

	@Override
	public int getRecordsOrder() {
		return sharedPreferences.getInt(PREF_KEY_RECORDS_ORDER, AppConstants.SORT_DATE);
	}

	public int getNamingFormat() {
		return sharedPreferences.getInt(PREF_KEY_NAMING_FORMAT, AppConstants.NAMING_COUNTED);
	}

	@Override
	public boolean isMigratedSettings() {
		return sharedPreferences.getBoolean(PREF_KEY_IS_MIGRATED, false);
	}

	@Override
	public void migrateSettings() {
		int color = getThemeColor();
		int nameFormat = getNamingFormat();
		int recordingFormat = getFormat();
		int sampleRate = getSampleRate();
		int bitrate = getBitrate();
		if (bitrate == AppConstants.RECORD_ENCODING_BITRATE_24000) {
			bitrate = AppConstants.RECORD_ENCODING_BITRATE_48000;
		}
		int channelCount = getRecordChannelCount();

		String colorKey;
		switch (color) {
			case 1:
				colorKey = AppConstants.THEME_BLACK;
				break;
			case 2:
				colorKey = AppConstants.THEME_TEAL;
				break;
			case 3:
				colorKey = AppConstants.THEME_BLUE;
				break;
			case 4:
				colorKey = AppConstants.THEME_PURPLE;
				break;
			case 5:
				colorKey = AppConstants.THEME_PINK;
				break;
			case 6:
				colorKey = AppConstants.THEME_ORANGE;
				break;
			case 7:
				colorKey = AppConstants.THEME_RED;
				break;
			case 8:
				colorKey = AppConstants.THEME_BROWN;
				break;
			case 0:
			case 9:
				colorKey = AppConstants.THEME_BLUE_GREY;
				break;
			default:
				colorKey = AppConstants.DEFAULT_THEME_COLOR;
		}

		String recordingFormatKey;
		switch (recordingFormat) {
			case AppConstants.RECORDING_FORMAT_WAV:
				recordingFormatKey = AppConstants.FORMAT_WAV;
				break;
			case AppConstants.RECORDING_FORMAT_M4A:
				recordingFormatKey = AppConstants.FORMAT_M4A;
				break;
			default:
				recordingFormatKey = AppConstants.DEFAULT_RECORDING_FORMAT;
		}
		String namingFormatKey;
		switch (nameFormat) {
			case AppConstants.NAMING_DATE:
				namingFormatKey = AppConstants.NAME_FORMAT_DATE;
				break;
			case AppConstants.NAMING_COUNTED:
				namingFormatKey = AppConstants.NAME_FORMAT_RECORD;
				break;
			default:
				namingFormatKey = AppConstants.DEFAULT_NAME_FORMAT;
		}

		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PREF_KEY_SETTING_THEME_COLOR, colorKey);
		editor.putString(PREF_KEY_SETTING_NAMING_FORMAT, namingFormatKey);
		editor.putString(PREF_KEY_SETTING_RECORDING_FORMAT, recordingFormatKey);
		editor.putInt(PREF_KEY_SETTING_SAMPLE_RATE, sampleRate);
		editor.putInt(PREF_KEY_SETTING_BITRATE, bitrate);
		editor.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, channelCount);
		editor.putBoolean(PREF_KEY_IS_MIGRATED, true);
		editor.apply();
	}

	@Override
	public boolean isMigratedDb3() {
		return sharedPreferences.getBoolean(PREF_KEY_IS_MIGRATED_DB3, false);
	}

	@Override
	public void migrateDb3Finished() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_MIGRATED_DB3, true);
		editor.apply();
	}

	@Override
	public void setSettingThemeColor(String colorKey) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PREF_KEY_SETTING_THEME_COLOR, colorKey);
		editor.apply();
	}

	@Override
	public String getSettingThemeColor() {
		return sharedPreferences.getString(PREF_KEY_SETTING_THEME_COLOR, AppConstants.DEFAULT_THEME_COLOR);
	}

	@Override
	public void setSettingNamingFormat(String nameKey) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PREF_KEY_SETTING_NAMING_FORMAT, nameKey);
		editor.apply();
	}

	@Override
	public String getSettingNamingFormat() {
		return sharedPreferences.getString(PREF_KEY_SETTING_NAMING_FORMAT, AppConstants.DEFAULT_NAME_FORMAT);
	}

	@Override
	public void setSettingRecordingFormat(String formatKey) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PREF_KEY_SETTING_RECORDING_FORMAT, formatKey);
		editor.apply();
	}

	@Override
	public String getSettingRecordingFormat() {
		return sharedPreferences.getString(PREF_KEY_SETTING_RECORDING_FORMAT, AppConstants.DEFAULT_RECORDING_FORMAT);
	}

	@Override
	public void setSettingSampleRate(int sampleRate) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_SETTING_SAMPLE_RATE, sampleRate);
		editor.apply();
	}

	@Override
	public int getSettingSampleRate() {
		return sharedPreferences.getInt(PREF_KEY_SETTING_SAMPLE_RATE, AppConstants.DEFAULT_RECORD_SAMPLE_RATE);
	}

	@Override
	public void setSettingBitrate(int rate) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_SETTING_BITRATE, rate);
		editor.apply();
	}

	@Override
	public int getSettingBitrate() {
		return sharedPreferences.getInt(PREF_KEY_SETTING_BITRATE, AppConstants.DEFAULT_RECORD_ENCODING_BITRATE);
	}

	@Override
	public void setSettingChannelCount(int count) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, count);
		editor.apply();
	}

	@Override
	public int getSettingChannelCount() {
		return sharedPreferences.getInt(PREF_KEY_SETTING_CHANNEL_COUNT, AppConstants.DEFAULT_CHANNEL_COUNT);
	}

	@Override
	public void resetSettings() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
//		editor.putString(PREF_KEY_SETTING_THEME_COLOR, AppConstants.DEFAULT_THEME_COLOR);
//		editor.putString(PREF_KEY_SETTING_NAMING_FORMAT, AppConstants.DEFAULT_NAME_FORMAT);
		editor.putString(PREF_KEY_SETTING_RECORDING_FORMAT, AppConstants.DEFAULT_RECORDING_FORMAT);
		editor.putInt(PREF_KEY_SETTING_SAMPLE_RATE, AppConstants.DEFAULT_RECORD_SAMPLE_RATE);
		editor.putInt(PREF_KEY_SETTING_BITRATE, AppConstants.DEFAULT_RECORD_ENCODING_BITRATE);
		editor.putInt(PREF_KEY_SETTING_CHANNEL_COUNT, AppConstants.DEFAULT_CHANNEL_COUNT);
		editor.apply();
	}
}
