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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.dimowner.audiorecorder.AppConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import timber.log.Timber;

import static com.dimowner.audiorecorder.AppConstants.SUPPORTED_EXT;

public class FileUtil {

	private static final String LOG_TAG = "FileUtil";

	/** The default buffer size ({@value}) to use for
	 * {@link #copyLarge(InputStream, OutputStream)} */
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

	/** Represents the end-of-file (or stream).*/
	public static final int EOF = -1;


	private FileUtil() {
	}

	public static File getAppDir() {
		return getStorageDir(AppConstants.APPLICATION_NAME);
	}

	public static File getPrivateRecordsDir(Context context) throws FileNotFoundException {
		File dir = FileUtil.getPrivateMusicStorageDir(context, AppConstants.RECORDS_DIR);
		if (dir == null) {
			throw new FileNotFoundException();
		}
		return dir;
	}

//	public static String generateRecordNameDate(String extension) {
//		long time = System.currentTimeMillis();
//		return AppConstants.BASE_RECORD_NAME + time/100 + AppConstants.EXTENSION_SEPARATOR + extension;
//	}

	public static String generateRecordNameCounted(long counter) {
		return AppConstants.BASE_RECORD_NAME + counter;
	}

	public static String generateRecordNameDate() {
		return AppConstants.BASE_RECORD_NAME_SHORT + TimeUtils.formatDateForName(System.currentTimeMillis());
	}

	public static String generateRecordNameDateVariant() {
		return TimeUtils.formatDateForNameVariant(System.currentTimeMillis());
	}

	public static String generateRecordNameMills() {
		return String.valueOf(System.currentTimeMillis());
	}


	public static String addExtension(String name, String extension) {
		return name + AppConstants.EXTENSION_SEPARATOR + extension;
	}

	/**
	 * Remove file extension from file name;
	 * @param name File name with extension;
	 * @return File name without extension or unchanged String if extension was not identified.
	 */
	public static String removeFileExtension(String name) {
		if (name.contains(AppConstants.EXTENSION_SEPARATOR)) {
			int extIndex = name.lastIndexOf(AppConstants.EXTENSION_SEPARATOR);
			if (extIndex >= 0 && extIndex+1 < name.length()
					&& (isSupportedExtension(name.substring(extIndex + 1)) || isDelExtension(name.substring(extIndex + 1)))
			) {
				return name.substring(0, name.lastIndexOf(AppConstants.EXTENSION_SEPARATOR));
			}
		}
		return name;
	}

	public static boolean isSupportedExtension(String ext) {
		for (int i = 0; i < SUPPORTED_EXT.length; i++) {
			if (SUPPORTED_EXT[i].equalsIgnoreCase(ext)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDelExtension(String ext) {
		return AppConstants.TRASH_MARK_EXTENSION.equalsIgnoreCase(ext);
	}

	/**
	 *
	 * @param input the <code>InputStream</code> to read from
	 * @param output the <code>OutputStream</code> to write to
	 * @return the number of bytes copied
	 * @throws NullPointerException if the input or output is null
	 * @throws IOException          if an I/O error occurs
	 * */
	public static long copyLarge(final InputStream input, final OutputStream output)
			throws IOException {
		return copyLarge(input, output, new byte[DEFAULT_BUFFER_SIZE], null);
	}

	public static long copyLarge(final InputStream input, final OutputStream output, final byte[] buffer, final OnCopyListener listener)
			throws IOException {
		long count = 0;
		int n;
		long size = input.available();
		int percent = 0;
		long stepPercent = 0;
		boolean isCancel = false;
		while (EOF != (n = input.read(buffer)) && !isCancel) {
			output.write(buffer, 0, n);
			count += n;
			percent = (int)(100*(float)count/(float)size);
			if (listener != null) {
				isCancel = listener.isCancel();
				if (percent > stepPercent + 1 || count == size) {
					listener.onCopyProgress(percent, count, size);
					stepPercent = percent;
				}
			}
		}
		if (listener != null) {
			if (isCancel) {
				listener.onCanceled();
				return -1;
			} else {
				listener.onCopyFinish();
			}
		}
		return count;
	}

	/**
	 * Copy file.
	 * @param fileToCopy File to copy.
	 * @param newFile File in which will contain copied data.
	 * @return true if copy succeed, otherwise - false.
	 */
	public static boolean copyFile(FileDescriptor fileToCopy, File newFile) throws IOException {
		FileInputStream in = null;
		FileOutputStream out = null;
		try {
			in = new FileInputStream(fileToCopy);
			out = new FileOutputStream(newFile);

			if (copyLarge(in, out) > 0) {
				return true;
			}  else {
				Timber.e("Nothing was copied!");
				return false;
			}
		} catch (Exception e) {
			return false;
		} finally {
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * Copy file.
	 * @param fileToCopy File to copy.
	 * @param newFile File in which will contain copied data.
	 * @return true if copy succeed, otherwise - false.
	 */
	public static boolean copyFile(File fileToCopy, File newFile) throws IOException {
		Timber.v("copyFile toCOpy = " + fileToCopy.getAbsolutePath() + " newFile = " + newFile.getAbsolutePath());
		FileInputStream in = null;
		FileOutputStream out = null;
		try {
			in = new FileInputStream(fileToCopy);
			out = new FileOutputStream(newFile);

			if (copyLarge(in, out) > 0) {
				return true;
			}  else {
				Timber.e("Nothing was copied!");
				return false;
			}
		} catch (Exception e) {
			return false;
		} finally {
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * Copy file.
	 * @param fileToCopy File to copy.
	 * @param newFile File in which will contain copied data.
	 * @return true if copy succeed, otherwise - false.
	 */
	public static boolean copyFile(File fileToCopy, File newFile, OnCopyListener listener) {
		try (FileInputStream in = new FileInputStream(fileToCopy); FileOutputStream out = new FileOutputStream(newFile)) {
			if (copyLarge(in, out, new byte[DEFAULT_BUFFER_SIZE], listener) > 0) {
				return true;
			} else {
				Timber.e("Nothing was copied!");
				deleteFile(newFile);
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Get free space for specified file
	 * @param f Dir
	 * @return Available space for specified file in bytes
	 */
	public static long getFree(File f) {
		while (!f.exists()) {
			f = f.getParentFile();
			if (f == null)
				return 0;
		}
		StatFs fsi = new StatFs(f.getPath());
		if (Build.VERSION.SDK_INT >= 18) {
			return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
		} else {
			return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
		}
	}

	public static long getAvailableInternalMemorySize(Context context) throws IllegalArgumentException {
		File file = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
		if (file != null) {
			StatFs fsi = new StatFs(file.getAbsolutePath());
			if (Build.VERSION.SDK_INT >= 18) {
				return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
			} else {
				return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
			}
		} else {
			return 0;
		}
	}

	public static long getAvailableExternalMemorySize() throws IllegalArgumentException {
		if (externalMemoryAvailable()) {
			File path = Environment.getExternalStorageDirectory();
			StatFs fsi = new StatFs(path.getPath());
			if (Build.VERSION.SDK_INT >= 18) {
				return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
			} else {
				return fsi.getBlockSize() * (long) fsi.getAvailableBlocks();
			}
		} else {
			return 0;
		}
	}

	public static boolean externalMemoryAvailable() {
		return android.os.Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED);
	}

	/**
	 * Create file.
	 * If it is not exists, than create it.
	 * @param path Path to file.
	 * @param fileName File name.
	 */
	public static File createFile(File path, String fileName) {
		if (path != null) {
			createDir(path);
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
			} else {
				Log.e(LOG_TAG, "File already exists!! Please rename file!");
				Log.i(LOG_TAG, "Renaming file");
//				TODO: Find better way to rename file.
				return createFile(path, "1" + fileName);
			}
			if (!file.canWrite()) {
				Log.e(LOG_TAG, "The file can not be written.");
			}
			return file;
		} else {
			return null;
		}
	}

	public static File createDir(File dir) {
		if (dir != null) {
			if (!dir.exists()) {
				try {
					if (dir.mkdirs()) {
						Log.d(LOG_TAG, "Dirs are successfully created");
						return dir;
					} else {
						Log.e(LOG_TAG, "Dirs are NOT created! Please check permission write to external storage!");
					}
				} catch (Exception e) {
					Timber.e(e);
				}
			} else {
				Log.d(LOG_TAG, "Dir already exists");
				return dir;
			}
		}
		Log.e(LOG_TAG, "File is null or unable to create dirs");
		return null;
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
//				if (!file.exists() && !file.mkdirs()) {
//					Log.e(LOG_TAG, "Directory " + file.getAbsolutePath() + " was not created");
//				}
				createDir(file);
			} else {
				Log.e(LOG_TAG, "External storage are not readable or writable");
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

	public static boolean isFileInExternalStorage(Context context, String path) {
		String privateDir = "";
		try {
			privateDir = FileUtil.getPrivateRecordsDir(context).getAbsolutePath();
		} catch (FileNotFoundException e) {
			Timber.e(e);
		}
		return path == null || !path.contains(privateDir);
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

	public static boolean renameFile(File file, String newName, String extension) {
		if (!file.exists()) {
			return false;
		}
		Timber.v("old File: " + file.getAbsolutePath());
		File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + newName + AppConstants.EXTENSION_SEPARATOR + extension);
		Timber.v("new File: " + renamed.getAbsolutePath());

		if (!file.renameTo(renamed)) {
			if (!file.renameTo(renamed)) {
				return (file.renameTo(renamed));
			}
		}
		return true;
	}

	public static boolean renameFile(File file, File renamed) {
		if (!file.exists()) {
			return false;
		}
		Timber.v("old File: " + file.getAbsolutePath());
		Timber.v("new File: " + renamed.getAbsolutePath());

		if (!file.renameTo(renamed)) {
			if (!file.renameTo(renamed)) {
				return (file.renameTo(renamed));
			}
		}
		return true;
	}

	public static String removeUnallowedSignsFromName(String name) {
//		String str = name.replaceAll("[^a-zA-Z0-9\\.\\-\\_]", "_");
//		return str.trim();
		return name.trim();
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

	private static boolean isVirtualFile(Context context, Uri uri) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			if (!DocumentsContract.isDocumentUri(context, uri)) {
				return false;
			}
			Cursor cursor = context.getContentResolver().query(
					uri,
					new String[]{DocumentsContract.Document.COLUMN_FLAGS},
					null, null, null);
			int flags = 0;
			if (cursor.moveToFirst()) {
				flags = cursor.getInt(0);
			}
			cursor.close();
			return (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
		} else {
			return false;
		}
	}

	private static InputStream getInputStreamForVirtualFile(Context context, Uri uri, String mimeTypeFilter)
			throws IOException {

		ContentResolver resolver = context.getContentResolver();
		String[] openableMimeTypes = resolver.getStreamTypes(uri, mimeTypeFilter);
		if (openableMimeTypes == null || openableMimeTypes.length < 1) {
			throw new FileNotFoundException();
		}
		return resolver
				.openTypedAssetFileDescriptor(uri, openableMimeTypes[0], null)
				.createInputStream();
	}

	private static String getMimeType(String url) {
		String type = null;
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		}
		return type;
	}

	public static boolean saveFile(Context context, String name, Uri sourceuri, String destinationDir, String destFileName) {

		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		InputStream input = null;
		boolean hasError = false;

		try {
			if (isVirtualFile(context, sourceuri)) {
				input = getInputStreamForVirtualFile(context, sourceuri, getMimeType(name));
			} else {
				input = context.getContentResolver().openInputStream(sourceuri);
			}

			boolean directorySetupResult;
			File destDir = new File(destinationDir);
			if (!destDir.exists()) {
				directorySetupResult = destDir.mkdirs();
			} else if (!destDir.isDirectory()) {
				directorySetupResult = replaceFileWithDir(destinationDir);
			} else {
				directorySetupResult = true;
			}

			if (!directorySetupResult) {
				hasError = true;
			} else {
				String destination = destinationDir + File.separator + destFileName;
				int originalsize = input.available();

				bis = new BufferedInputStream(input);
				bos = new BufferedOutputStream(new FileOutputStream(destination));
				byte[] buf = new byte[originalsize];
				bis.read(buf);
				do {
					bos.write(buf);
				} while (bis.read(buf) != -1);
			}
		} catch (Exception e) {
			e.printStackTrace();
			hasError = true;
		} finally {
			try {
				if (bos != null) {
					bos.flush();
					bos.close();
				}
			} catch (Exception ignored) {
			}
		}

		return !hasError;
	}

	private static boolean replaceFileWithDir(String path) {
		File file = new File(path);
		if (!file.exists()) {
			if (file.mkdirs()) {
				return true;
			}
		} else if (file.delete()) {
			File folder = new File(path);
			if (folder.mkdirs()) {
				return true;
			}
		}
		return false;
	}
}
