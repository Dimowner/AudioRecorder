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

import com.crashlytics.android.Crashlytics;
import com.dimowner.audiorecorder.util.AppStartTracker;
import io.fabric.sdk.android.Fabric;
import java.util.Random;

import timber.log.Timber;

public class ARApplication extends Application {

	@SuppressLint("StaticFieldLeak")
	public static volatile Context applicationContext;
	public static volatile Handler applicationHandler;

	private int appThemeResource = 0;
	private int primaryColorRes = R.color.md_blue_700;

	private AppStartTracker startTracker = new AppStartTracker();

	public static AppStartTracker getAppStartTracker(Context context) {
		return ((ARApplication) context).getStartTracker();
	}

	public static int getAppThemeResource(Context context) {
		return ((ARApplication) context).getThemeResource();
	}

	public static int getPrimaryColorRes(Context context) {
		return ((ARApplication) context).getPrimaryColorRes();
	}

	private int getThemeResource() {
		return appThemeResource;
	}

	private int getPrimaryColorRes() {
		return primaryColorRes;
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
		Fabric.with(this, new Crashlytics());

		applicationContext = getApplicationContext();
		applicationHandler = new Handler(applicationContext.getMainLooper());

		selectRandomThemeColor();
	}

	private void selectRandomThemeColor() {
		switch (new Random().nextInt(7)) {
			case 1:
				appThemeResource = R.style.AppTheme_Brown;
				primaryColorRes = R.color.md_brown_700;
				break;
			case 2:
				appThemeResource = R.style.AppTheme_DeepOrange;
				primaryColorRes = R.color.md_deep_orange_800;
				break;
			case 3:
				appThemeResource = R.style.AppTheme_Pink;
				primaryColorRes = R.color.md_pink_800;
				break;
			case 4:
				appThemeResource = R.style.AppTheme_Purple;
				primaryColorRes = R.color.md_deep_purple_700;
				break;
			case 5:
				appThemeResource = R.style.AppTheme_Red;
				primaryColorRes = R.color.md_red_700;
				break;
			case 6:
				appThemeResource = R.style.AppTheme_Teal;
				primaryColorRes = R.color.md_teal_700;
			case 0:
			default:
				primaryColorRes = R.color.md_blue_700;
				appThemeResource = R.style.AppTheme;
		}
	}
}
