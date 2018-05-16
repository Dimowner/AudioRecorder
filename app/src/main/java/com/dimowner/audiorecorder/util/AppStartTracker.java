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

package com.dimowner.audiorecorder.util;

import java.util.Date;

import timber.log.Timber;

/**
 * Class for evaluate start application and activity time.
 * Helps to see how long it takes from application onCreate to first activity onResume.
 * @author dimowner
 */
public class AppStartTracker {

//	private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

	private StartTimes startTimes = new StartTimes();

	public void appOnCreate() {
		long time = System.currentTimeMillis();
		startTimes.setAppOnCreate(time);
		Timber.v("diff =  0, time = %s - appOnCreate", time);
	}

	public void activityOnCreate() {
		long time = System.currentTimeMillis();
		startTimes.setActivityOnCreate(time);
		Timber.v("diff = %s, time = %s - activityOnCreate",
				(time - startTimes.getAppOnCreate()),
				time);
	}

	public void activityContentViewBefore() {
		long time = System.currentTimeMillis();
		startTimes.setActivityContentViewBefore(time);
		Timber.v("diff = %s, time = %s - activityContentViewBefore",
				(time - startTimes.getAppOnCreate()),
				time);
	}

	public void activityContentViewAfter() {
		long time = System.currentTimeMillis();
		startTimes.setActivityContentViewAfter(time);
		Timber.v("diff = %s, time = %s - activityContentViewAfter",
				(time - startTimes.getAppOnCreate()),
				time);
	}


	public void activityOnCreateEnd() {
		long time = System.currentTimeMillis();
		startTimes.setActivityOnCreateEnd(time);
		Timber.v("diff = %s, time = %s - activityOnCreateEnd ",
				(time - startTimes.getAppOnCreate()),
				time);
	}

	public void activityOnStart() {
		long time = System.currentTimeMillis();
		startTimes.setActivityOnStart(time);
		Timber.v("diff = %s, time = %s, - activityOnStart ",
				(time - startTimes.getAppOnCreate()),
				time);
	}

	public void activityOnResume() {
		long time = System.currentTimeMillis();
		startTimes.setActivityOnResume(time);
		Timber.v("diff = %s, time = %s - activityOnResume",
				(time - startTimes.getAppOnCreate()),
				time);
//				timeFormat.format(new Date(time)));
	}

	public String getResults() {
		return startTimes.toString();
	}

	public String getStartTime() {
		return (startTimes.getActivityOnResume() - startTimes.getAppOnCreate()) + " mills";
	}

	public class StartTimes {
		private long appOnCreate;
		private long activityOnCreate;
		private long activityContentViewBefore;
		private long activityContentViewAfter;
		private long activityOnCreateEnd;
		private long activityOnStart;
		private long activityOnResume;


		long getAppOnCreate() {
			return appOnCreate;
		}

		void setAppOnCreate(long appOnCreate) {
			this.appOnCreate = appOnCreate;
		}

		public long getActivityOnCreate() {
			return activityOnCreate;
		}

		void setActivityOnCreate(long activityOnCreate) {
			this.activityOnCreate = activityOnCreate;
		}

		public long getActivityContentViewBefore() {
			return activityContentViewBefore;
		}

		void setActivityContentViewBefore(long activityContentViewBefore) {
			this.activityContentViewBefore = activityContentViewBefore;
		}

		public long getActivityContentViewAfter() {
			return activityContentViewAfter;
		}

		void setActivityContentViewAfter(long activityContentViewAfter) {
			this.activityContentViewAfter = activityContentViewAfter;
		}

		public long getActivityOnCreateEnd() {
			return activityOnCreateEnd;
		}

		void setActivityOnCreateEnd(long activityOnCreateEnd) {
			this.activityOnCreateEnd = activityOnCreateEnd;
		}

		public long getActivityOnStart() {
			return activityOnStart;
		}

		void setActivityOnStart(long activityOnStart) {
			this.activityOnStart = activityOnStart;
		}

		public long getActivityOnResume() {
			return activityOnResume;
		}

		void setActivityOnResume(long activityOnResume) {
			this.activityOnResume = activityOnResume;
		}

		@Override
		public String toString() {
			return "StartTimes{" +
					"\n diff =  0, time = " + appOnCreate + " appOnCreate" +
					"\n diff = " + (activityOnCreate - appOnCreate) + ", time = " + appOnCreate + " activityOnCreate" +
					"\n diff = " + (activityContentViewBefore - appOnCreate) + ", time = " + activityContentViewBefore + " activityContentViewBefore" +
					"\n diff = " + (activityContentViewAfter - appOnCreate) + ", time = " + activityContentViewAfter + " activityContentViewAfter" +
					"\n diff = " + (activityOnCreateEnd - appOnCreate) + ", time = " + activityOnCreateEnd + " activityOnCreateEnd" +
					"\n diff = " + (activityOnStart - appOnCreate) + ", time = " + activityOnStart + " activityOnStart" +
					"\n diff = " + (activityOnResume - appOnCreate) + ", time = " + activityOnResume + " activityOnResume" +

//					",\n formatted: " + timeFormat.format(new Date(appOnCreate)) + " appOnCreate" +
//					",\n formatted: " + timeFormat.format(new Date(activityOnCreate)) + " activityOnCreate" +
//					",\n formatted: " + timeFormat.format(new Date(activityContentViewBefore)) + " activityContentViewBefore" +
//					",\n formatted: " + timeFormat.format(new Date(activityContentViewAfter)) + " activityContentViewAfter" +
//					",\n formatted: " + timeFormat.format(new Date(activityOnCreateEnd)) + " activityOnCreateEnd" +
//					",\n formatted: " + timeFormat.format(new Date(activityOnStart)) + " activityOnStart" +
//					",\n formatted: " + timeFormat.format(new Date(activityOnResume)) + " activityOnResume" +
					'}';
		}
	}
}
