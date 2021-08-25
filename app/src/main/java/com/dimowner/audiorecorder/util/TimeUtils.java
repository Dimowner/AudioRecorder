/*
 * Copyright 2018 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.util;

import android.content.Context;
import android.text.format.DateFormat;

import com.dimowner.audiorecorder.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimeUtils {

//	/** Date format: May 16, 03:30 PM */
//	private static SimpleDateFormat dateTimeFormat12H = new SimpleDateFormat("MMM dd, hh:mm aa", Locale.US);
//
//	/** Date format: May 16, 15:30 */
//	private static SimpleDateFormat dateTimeFormat24H = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
//
//	/** Date format: May 16 */
//	private static final SimpleDateFormat dateFormat24H = new SimpleDateFormat("MMM dd", Locale.getDefault());
//
//	/** Date format: 22.11.2018, 11:30 */
//	private static final SimpleDateFormat dateTimeFormatEU = new SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault());
//
//	/** Date format: 11/22/2018, 11:30 */
//	private static final SimpleDateFormat dateTimeFormatUS = new SimpleDateFormat("MM/dd/yyyy, HH:mm", Locale.US);
//
//	/** Date format: 2019.09.22 11:30 */
//	private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss", Locale.getDefault());

	/** Date format: 22.11.2018 11:30 */
	private static SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH.mm.ss", Locale.getDefault());

	/** Date format: 11-22-2018 11:30AM */
	private static SimpleDateFormat dateTimeFormatUS = new SimpleDateFormat("MM-dd-yyyy hh.mm.ssaa", Locale.getDefault());

	/** Date format: 2021-08-25 11:50 */
	private static SimpleDateFormat dateTimeFormatISO8601 = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault());

//	/** Time format: 11:30 */
//	private static final SimpleDateFormat timeFormatEU = new SimpleDateFormat("HH:mm", Locale.FRANCE);
//
//	/** Time format: 22.11.2018 */
//	private static final SimpleDateFormat dateFormatEU = new SimpleDateFormat("dd.MM.yyyy", Locale.FRANCE);
//
//	/** Time format: 22.11.2018 */
//	private static final SimpleDateFormat dateFormatUS = new SimpleDateFormat("MM/dd/yyyy", Locale.FRANCE);

	private TimeUtils() {
	}

	public static String formatTimeIntervalMinSec(long length) {
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
//		if (length < 0) {
//			length = -length;
//			long numMinutes = timeUnit.toMinutes(length);
//			long numSeconds = timeUnit.toSeconds(length);
//			return "-" + String.format(Locale.getDefault(), "%02d:%02d", numMinutes, numSeconds % 60);
//		} else {
			long numMinutes = timeUnit.toMinutes(length);
			long numSeconds = timeUnit.toSeconds(length);
			return String.format(Locale.getDefault(), "%02d:%02d", numMinutes, numSeconds % 60);
//		}
	}

	public static String formatTimeIntervalHourMin(long length) {
		if (length >= 60 * 60 * 1000) { //60*60*1000 = 1hour in mills
			TimeUnit timeUnit = TimeUnit.MILLISECONDS;
			String numHour = String.valueOf(timeUnit.toHours(length));
			long numMinutes = timeUnit.toMinutes(length);
			return String.format(Locale.getDefault(), "%sh:%02d", numHour, numMinutes % 60);
		} else {
			return TimeUtils.formatTimeIntervalMinSec(length);
		}
	}

	public static String formatTimeIntervalHourMinSec2(long length) {
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
		long numHour = timeUnit.toHours(length);
		long numMinutes = timeUnit.toMinutes(length);
		long numSeconds = timeUnit.toSeconds(length);
		if (numHour == 0) {
			return String.format(Locale.getDefault(), "%02d:%02d", numMinutes, numSeconds % 60);
		} else {
			return String.format(Locale.getDefault(), "%02d:%02d:%02d", numHour, numMinutes % 60, numSeconds % 60);
		}
	}

	public static String formatTimeIntervalMinSecMills(long mills) {
		long min = mills / (60 * 1000);
		long sec = (mills / 1000) % 60;
		long m = (mills / 10) % 100;
		return String.format(Locale.getDefault(), "%02d:%02d:%02d", min, sec, m);
	}

	public static String formatTimeIntervalHourMinSec(long mills) {
		long hour = mills / (60 * 60 * 1000);
		long min = mills / (60 * 1000) % 60;
		long sec = (mills / 1000) % 60;
		if (hour == 0) {
			if (min == 0) {
				return String.format(Locale.getDefault(), "%02ds", sec);
			} else {
				return String.format(Locale.getDefault(), "%02dm:%02ds", min, sec);
			}
		} else {
			return String.format(Locale.getDefault(), "%02dh:%02dm:%02ds", hour, min, sec);
		}
	}

//	public static String formatDateSmart(long time, Context ctx) {
//		if (time <= 0) {
//			return "Wrong date!";
//		}
//		Calendar today = Calendar.getInstance();
//		Calendar date = Calendar.getInstance();
//		date.setTimeInMillis(time);
//		if (isSameYear(today, date)) {
//			if (isSameDay(today, date)) {
//				return ctx.getResources().getString(R.string.today);
//			} else {
//				today.add(Calendar.DAY_OF_YEAR, -1); //Make yesterday
//				//Check is yesterday
//				if (isSameDay(today, date)) {
//					return ctx.getResources().getString(R.string.yesterday);
//				} else {
//					return dateFormat24H.format(new Date(time));
////					return dateFormatEU.format(new Date(time));
//				}
//			}
//		} else {
//			return dateFormatEU.format(new Date(time));
//		}
//	}

	public static String formatDateSmartLocale(long time, Context ctx) {
		if (time <= 0) {
			return "Wrong date!";
		}
		Calendar today = Calendar.getInstance();
		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(time);
		if (isSameYear(today, date)) {
			if (isSameDay(today, date)) {
				return ctx.getResources().getString(R.string.today);
			} else {
				today.add(Calendar.DAY_OF_YEAR, -1); //Make yesterday
				//Check is yesterday
				if (isSameDay(today, date)) {
					return ctx.getResources().getString(R.string.yesterday);
				} else {
					return formatDayMonthLocale(time);
				}
			}
		} else {
			return formatDateLocale(time);
		}
	}

	/**
	 * <p>Checks if two calendars represent the same day ignoring time.</p>
	 * @param cal1  the first calendar, not altered, not null
	 * @param cal2  the second calendar, not altered, not null
	 * @return true if they represent the same day
	 * @throws IllegalArgumentException if either calendar is <code>null</code>
	 */
	public static boolean isSameDay(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			throw new IllegalArgumentException("The dates must not be null");
		}
		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
				cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
				cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
	}

	/**
	 * <p>Checks if two calendars represent the same year ignoring time.</p>
	 * @param cal1  the first calendar, not altered, not null
	 * @param cal2  the second calendar, not altered, not null
	 * @return true if they represent the same day
	 * @throws IllegalArgumentException if either calendar is <code>null</code>
	 */
	public static boolean isSameYear(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			throw new IllegalArgumentException("The dates must not be null");
		}
		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
				cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR));
	}

	public static String formatDateForNameVariant(long time) {
		return dateTimeFormat.format(new Date(time));
	}

	public static String formatDateForNameUS(long time) {
		return dateTimeFormatUS.format(new Date(time));
	}

	public static String formatDateForNameISO8601(long time) {
		return dateTimeFormatISO8601.format(new Date(time));
	}

	public static String formatDateTimeLocale(long time) {
		java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(
				java.text.DateFormat.SHORT,
				java.text.DateFormat.SHORT,
				Locale.getDefault()
		);
		return format.format(new Date(time));
	}

	public static String formatDateLocale(long time) {
		String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy");
		SimpleDateFormat dayMonth = new SimpleDateFormat(pattern, Locale.getDefault());
		return dayMonth.format(new Date(time));
	}

	public static String formatDayMonthLocale(long time) {
		String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMM");
		SimpleDateFormat dayMonth = new SimpleDateFormat(pattern, Locale.getDefault());
		return dayMonth.format(new Date(time));
	}
}
