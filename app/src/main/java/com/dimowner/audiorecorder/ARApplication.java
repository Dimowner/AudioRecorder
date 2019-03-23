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

import android.app.Application;
import android.os.Handler;

//import com.crashlytics.android.Crashlytics;
import com.dimowner.audiorecorder.util.AndroidUtils;
//import io.fabric.sdk.android.Fabric;

import timber.log.Timber;

public class ARApplication extends Application {

	private static String PACKAGE_NAME ;
	public static volatile Handler applicationHandler;

	/** Screen width in dp */
	private static float screenWidthDp = 0;

	public static Injector injector;

	private static boolean isRecording = false;

	public static Injector getInjector() {
		return injector;
	}

	public static String appPackage() {
		return PACKAGE_NAME;
	}

	/**
	 * Calculate density pixels per second for record duration.
	 * Used for visualisation waveform in view.
	 * @param durationSec record duration in seconds
	 */
	public static float getDpPerSecond(float durationSec) {
		if (durationSec > AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
			return AppConstants.WAVEFORM_WIDTH * screenWidthDp / durationSec;
		} else {
			return AppConstants.SHORT_RECORD_DP_PER_SECOND;
		}
	}

	public static int getLongWaveformSampleCount() {
		return (int)(AppConstants.WAVEFORM_WIDTH * screenWidthDp);
	}

	@Override
	public void onCreate() {
		if (BuildConfig.DEBUG) {
			//Timber initialization
			Timber.plant(new Timber.DebugTree() {
				@Override
				protected String createStackElementTag(StackTraceElement element) {
					return "AR-AR " + super.createStackElementTag(element) + ":" + element.getLineNumber();
				}
			});
		}

		super.onCreate();
//		Fabric.with(this, new Crashlytics());

		PACKAGE_NAME = getApplicationContext().getPackageName();
		applicationHandler = new Handler(getApplicationContext().getMainLooper());
		screenWidthDp = AndroidUtils.pxToDp(AndroidUtils.getScreenWidth(getApplicationContext()));
		injector = new Injector(getApplicationContext());
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		Timber.v("onTerminate");
		injector.releaseMainPresenter();
		injector.closeTasks();
	}

	public static boolean isRecording() {
		return isRecording;
	}

	public static void setRecording(boolean recording) {
		ARApplication.isRecording = recording;
	}
}
