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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupMenu;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import timber.log.Timber;

/**
 * Android related utilities methods.
 */
public class AndroidUtils {

	//Prevent object instantiation
	private AndroidUtils() {}

	/**
	 * Convert density independent pixels value (dip) into pixels value (px).
	 * @param dp Value needed to convert
	 * @return Converted value in pixels.
	 */
	public static float dpToPx(int dp) {
		return dpToPx((float) dp);
	}

	/**
	 * Convert density independent pixels value (dip) into pixels value (px).
	 * @param dp Value needed to convert
	 * @return Converted value in pixels.
	 */
	public static float dpToPx(float dp) {
		return (dp * Resources.getSystem().getDisplayMetrics().density);
	}

	/**
	 * Convert pixels value (px) into density independent pixels (dip).
	 * @param px Value needed to convert
	 * @return Converted value in pixels.
	 */
	public static float pxToDp(int px) {
		return pxToDp((float) px);
	}

	/**
	 * Convert pixels value (px) into density independent pixels (dip).
	 * @param px Value needed to convert
	 * @return Converted value in pixels.
	 */
	public static float pxToDp(float px) {
		return (px / Resources.getSystem().getDisplayMetrics().density);
	}

	public static int screenWidth() {
		return Resources.getSystem().getDisplayMetrics().widthPixels;
	}

	public static int screenHeight() {
		return Resources.getSystem().getDisplayMetrics().heightPixels;
	}

	public static int convertMillsToPx(long mills, float pxPerSec) {
		// 1000 is 1 second evaluated in milliseconds
		return (int) (mills * pxPerSec / 1000);
	}

	public static int convertPxToMills(long px, float pxPerSecond) {
		return (int) (1000 * px / pxPerSecond);
	}

	// A method to find height of the status bar
	public static int getStatusBarHeight(Context context) {
		int result = 0;
		int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = context.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}

	public static void setTranslucent(Activity activity, boolean translucent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			Window w = activity.getWindow();
			if (translucent) {
				w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			} else {
				w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			}
		}
	}

	public static void runOnUIThread(Runnable runnable) {
		runOnUIThread(runnable, 0);
	}

	public static void runOnUIThread(Runnable runnable, long delay) {
		if (delay == 0) {
			ARApplication.applicationHandler.post(runnable);
		} else {
			ARApplication.applicationHandler.postDelayed(runnable, delay);
		}
	}

	public static void cancelRunOnUIThread(Runnable runnable) {
		ARApplication.applicationHandler.removeCallbacks(runnable);
	}

	/**
	 * Convert int array to byte array
	 */
	public static byte[] int2byte(int[] src) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(src.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
		intBuffer.put(src);
		return byteBuffer.array();
	}

	/**
	 * Convert byte array to int array
	 */
	public static int[] byte2int(byte[]src) {
		int dstLength = src.length >>> 2;
		int[]dst = new int[dstLength];

		for (int i=0; i<dstLength; i++) {
			int j = i << 2;
			int x = 0;
			x += (src[j++] & 0xff) << 0;
			x += (src[j++] & 0xff) << 8;
			x += (src[j++] & 0xff) << 16;
			x += (src[j++] & 0xff) << 24;
			dst[i] = x;
		}
		return dst;
	}

	public static int getScreenWidth(Context context) {
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size.x;
	}

	public static int getScreenHeight(Context context) {
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size.y;
	}

	/**
	 * Read sound file duration.
	 * @param file Sound file
	 * @return Duration in microseconds.
	 */
	public static long readRecordDuration(File file) {
		try {
			MediaExtractor extractor = new MediaExtractor();
			MediaFormat format = null;
			int i;

			extractor.setDataSource(file.getPath());
			int numTracks = extractor.getTrackCount();
			// find and select the first audio track present in the file.
			for (i = 0; i < numTracks; i++) {
				format = extractor.getTrackFormat(i);
				if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
					extractor.selectTrack(i);
					break;
				}
			}

			if (i == numTracks) {
				throw new IOException("No audio track found in " + file.toString());
			}
			if (format != null) {
				return format.getLong(MediaFormat.KEY_DURATION);
			}
		} catch (IOException e) {
			Timber.e(e);
		}
		return -1;
	}

	/**
	 * Moves icons from the PopupMenu MenuItems' icon fields into the menu title as a Spannable with the icon and title text.
	 */
	public static void insertMenuItemIcons(Context context, PopupMenu popupMenu) {
		Menu menu = popupMenu.getMenu();
		if (hasIcon(menu)) {
			for (int i = 0; i < menu.size(); i++) {
				insertMenuItemIcon(context, menu.getItem(i));
			}
		}
	}

	/**
	 * @return true if the menu has at least one MenuItem with an icon.
	 */
	private static boolean hasIcon(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			if (menu.getItem(i).getIcon() != null) return true;
		}
		return false;
	}

	/**
	 * Converts the given MenuItem title into a Spannable containing both its icon and title.
	 */
	private static void insertMenuItemIcon(Context context, MenuItem menuItem) {
		Drawable icon = menuItem.getIcon();

		// If there no icon, we insert a transparent one to keep the title aligned with the items
		// which do have icons.
		if (icon == null) icon = new ColorDrawable(Color.TRANSPARENT);

		int iconSize = context.getResources().getDimensionPixelSize(R.dimen.menu_item_icon_size);
		icon.setBounds(0, 0, iconSize, iconSize);
		ImageSpan imageSpan = new ImageSpan(icon);

		// Add a space placeholder for the icon, before the title.
		SpannableStringBuilder ssb = new SpannableStringBuilder("      " + menuItem.getTitle());

		// Replace the space placeholder with the icon.
		ssb.setSpan(imageSpan, 1, 2, 0);
		menuItem.setTitle(ssb);
		// Set the icon to null just in case, on some weird devices, they've customized Android to display
		// the icon in the menu... we don't want two icons to appear.
		menuItem.setIcon(null);
	}
}
