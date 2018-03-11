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

package com.dimowner.audiorecorder.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {

	/** Application directory name. */
	public static final String APPLICATION_DIR = "MultiTrackRecorder";

	private static final String LOG_TAG = "FileUtil";

	private FileUtil() {
	}

	public static File getAppDir() {
		return getStorageDir(APPLICATION_DIR);
	}

	public static String generateRecordName() {
		long time = System.currentTimeMillis();
		return "audio_rec_" + time + ".m4a";
	}

	/**
	 * Create file.
	 * If it is not exists, than create it.
	 * @param path Path to file.
	 * @param fileName File name.
	 */
	public static File createFile(File path, String fileName) {
		if (path != null) {
			Log.d(LOG_TAG, "createFile path = " + path.getAbsolutePath() + " fileName = " + fileName);
			File file = new File(path, fileName);
			//Create file if need.
			if (!file.exists()) {
				try {
					if (file.createNewFile()) {
						Log.i(LOG_TAG, "The file was successfully created! - " + file.getAbsolutePath());
					} else {
						Log.i(LOG_TAG, "The file exist! - " + file.getAbsolutePath());
					}
				} catch (IOException e) {
					Log.e(LOG_TAG, "Failed to create the file.", e);
					return null;
				}
			}
			if (!file.canWrite()) {
				Log.e(LOG_TAG, "The file can not be written.");
			}
			return file;
		} else {
			return null;
		}
	}

	/**
	 * Write bitmap into file.
	 * @param file The file in which is recorded the image.
	 * @param bitmap The image that will be recorded in the file.
     * @param quality Saved image quality
	 * @return True if success, else false.
	 */
	public static boolean writeImage(File file, Bitmap bitmap, int quality) {
		if (!file.canWrite()) {
			Log.e(LOG_TAG, "The file can not be written.");
			return false;
		}
		if (bitmap == null) {
			Log.e(LOG_TAG, "Failed to write! bitmap is null.");
			return false;
		}
		try {
			FileOutputStream fos = new FileOutputStream(file);
			if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)) {
				fos.flush();
				fos.close();
				return true;
			}
			fos.close();
		} catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "File not found: " + e.getMessage());
		} catch (IOException e) {
			Log.e(LOG_TAG, "Error accessing file: " + e.getMessage());
		}
		return false;
	}

	/**
	 * Get public external storage directory
	 * @param dirName Directory name.
	 */
	public static File getStorageDir(String dirName) {
		if (dirName != null && !dirName.isEmpty()) {
			File file = new File(Environment.getExternalStorageDirectory(), dirName);
			if (isExternalStorageReadable() && isExternalStorageWritable()) {
				if (!file.exists() && !file.mkdirs()) {
					Log.e(LOG_TAG, "Directory " + file.getAbsolutePath() + " was not created");
				}
			}
			return file;
		} else {
			return null;
		}
	}

	/**
	 * Checks if external storage is available for read and write.
	 */
	public static boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	/**
	 * Checks if external storage is available to at least read.
	 */
	public static boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();
		return (Environment.MEDIA_MOUNTED.equals(state) ||
				Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
	}

	public static File getPublicMusicStorageDir(String albumName) {
		File file = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_MUSIC), albumName);
		if (!file.mkdirs()) {
			Log.e(LOG_TAG, "Directory not created");
		}
		return file;
	}

	public static File getPrivateMusicStorageDir(Context context, String albumName) {
		File file = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
		if (file != null) {
			File f = new File(file, albumName);
			if (!f.exists() && !f.mkdirs()) {
				Log.e(LOG_TAG, "Directory not created");
			} else {
				return f;
			}
		}
		return null;
	}

	/**
	 * Remove file or directory with all content
	 * @param file File or directory needed to delete.
	 */
	public static boolean deleteFile(File file) {
		if (deleteRecursivelyDirs(file)) {
			return true;
		}
		Log.e(LOG_TAG, "Failed to delete directory: " + file.getAbsolutePath());
		return false;
	}

	/**
	 * Recursively remove file or directory with children.
	 * @param file File to remove
	 */
	private static boolean deleteRecursivelyDirs(File file) {
		boolean ok = true;
		if (file != null && file.exists()) {
			if (file.isDirectory()) {
				String[] children = file.list();
				for (int i = 0; i < children.length; i++) {
					ok &= deleteRecursivelyDirs(new File(file, children[i]));
				}
			}
			if (ok && file.delete()) {
				Log.d(LOG_TAG, "File deleted: " + file.getAbsolutePath());
			}
		}
		return ok;
	}
}
