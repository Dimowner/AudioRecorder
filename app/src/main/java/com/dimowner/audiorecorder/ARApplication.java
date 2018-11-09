/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Handler;

import com.dimowner.audiorecorder.util.AppStartTracker;
import java.util.Random;

import timber.log.Timber;

public class ARApplication extends Application {

	@SuppressLint("StaticFieldLeak")
	public static volatile Context applicationContext;
	public static volatile Handler applicationHandler;

	private int appThemeResource = 0;

	private AppStartTracker startTracker = new AppStartTracker();

	public static AppStartTracker getAppStartTracker(Context context) {
		return ((ARApplication) context).getStartTracker();
	}

	public static int getAppThemeResource(Context context) {
		return ((ARApplication) context).getThemeResource();
	}

	private int getThemeResource() {
		return appThemeResource;
	}

	private AppStartTracker getStartTracker() {
		return startTracker;
	}

	@Override
	public void onCreate() {
		if (BuildConfig.DEBUG) {
			//Timber initialization
			Timber.plant(new Timber.DebugTree() {
				@Override
				protected String createStackElementTag(StackTraceElement element) {
					return super.createStackElementTag(element) + ":" + element.getLineNumber();
				}
			});
		}
		startTracker.appOnCreate();
		super.onCreate();

		applicationContext = getApplicationContext();
		applicationHandler = new Handler(applicationContext.getMainLooper());
		appThemeResource = selectRandomThemeColor();
	}

	private int selectRandomThemeColor() {
		switch (new Random().nextInt(7)) {
			case 0:
				return R.style.AppTheme;
			case 1:
				return R.style.AppTheme_Brown;
			case 2:
				return R.style.AppTheme_DeepOrange;
			case 3:
				return R.style.AppTheme_Pink;
			case 4:
				return R.style.AppTheme_Purple;
			case 5:
				return R.style.AppTheme_Red;
			case 6:
				return R.style.AppTheme_Teal;

				default: return R.style.AppTheme;
		}
	}
}
