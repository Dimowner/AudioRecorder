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

import timber.log.Timber;

public class Prefs {

	private static final String PREF_NAME = "com.dimowner.audiorecorder.data.Prefs";

	private static final String PREF_KEY_IS_FIRST_RUN = "is_first_run";
	private static final String PREF_KEY_LAST_RECORDED_FILE = "last_recorded_file";
	private static final String PREF_KEY_IS_STORE_DIR_PUBLIC = "is_store_dir_public";

	private SharedPreferences sharedPreferences;

	public Prefs(Context context) {
		sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
	}

	public boolean isFirstRun() {
		return !sharedPreferences.contains(PREF_KEY_IS_FIRST_RUN) || sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_RUN, false);
	}

	public void firstRunExecuted() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_FIRST_RUN, false);
		editor.apply();
	}

	public String getLastRecordedFile() {
		Timber.v("getLastRecordedFile: %s", sharedPreferences.getString(PREF_KEY_LAST_RECORDED_FILE, null));
		return sharedPreferences.getString(PREF_KEY_LAST_RECORDED_FILE, null);
	}

	public void saveLastRecordedFile(String str) {
		Timber.v("setLastRecordedFile: %s", str);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PREF_KEY_LAST_RECORDED_FILE, str);
		editor.apply();
	}

	public void clearLastRecordFile() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PREF_KEY_LAST_RECORDED_FILE);
		editor.apply();
	}

	public boolean isStoreDirPublic() {
		return sharedPreferences.contains(PREF_KEY_IS_STORE_DIR_PUBLIC) && sharedPreferences.getBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, false);
	}

	public void setStoreDirPublic(boolean b) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PREF_KEY_IS_STORE_DIR_PUBLIC, b);
		editor.apply();
	}
}
