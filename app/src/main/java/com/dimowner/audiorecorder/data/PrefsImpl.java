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
		editor.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, true);
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

	@Override
	public void setAppThemeColor(int colorMapPosition) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_THEME_COLORMAP_POSITION, colorMapPosition);
		editor.apply();
	}

	@Override
	public int getThemeColor() {
		return sharedPreferences.getInt(PREF_KEY_THEME_COLORMAP_POSITION, 0);
	}

	@Override
	public void setRecordInStereo(boolean stereo) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_RECORD_CHANNEL_COUNT, stereo ? AppConstants.RECORD_AUDIO_STEREO : AppConstants.RECORD_AUDIO_MONO);
		editor.apply();
	}

	@Override
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

	@Override
	public void setFormat(int f) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_FORMAT, f);
		editor.apply();
	}

	@Override
	public int getFormat() {
		return sharedPreferences.getInt(PREF_KEY_FORMAT, AppConstants.RECORDING_FORMAT_M4A);
	}

	@Override
	public void setBitrate(int q) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_BITRATE, q);
		editor.apply();
	}

	@Override
	public int getBitrate() {
		return sharedPreferences.getInt(PREF_KEY_BITRATE, AppConstants.RECORD_ENCODING_BITRATE_128000);
	}

	@Override
	public void setSampleRate(int rate) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_SAMPLE_RATE, rate);
		editor.apply();
	}

	@Override
	public int getSampleRate() {
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

	@Override
	public void setNamingFormat(int format) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PREF_KEY_NAMING_FORMAT, format);
		editor.apply();
	}

	@Override
	public int getNamingFormat() {
		return sharedPreferences.getInt(PREF_KEY_NAMING_FORMAT, AppConstants.NAMING_COUNTED);
	}
}
