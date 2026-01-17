/*
 * Copyright 2018 Dmytro Ponomarenko
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;

import timber.log.Timber;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.util.FileUtil;

public class FileRepositoryImpl implements FileRepository {

	private File recordDirectory;
	private final Prefs prefs;

	private volatile static FileRepositoryImpl instance;

	private FileRepositoryImpl(Context context, Prefs prefs) {
		this.prefs = prefs;
		updateRecordingDir(context, prefs);
	}

	public static FileRepositoryImpl getInstance(Context context, Prefs prefs) {
		if (instance == null) {
			synchronized (FileRepositoryImpl.class) {
				if (instance == null) {
					instance = new FileRepositoryImpl(context, prefs);
				}
			}
		}
		return instance;
	}

	@Override
	public File provideRecordFile() throws CantCreateFileException {
		prefs.incrementRecordCounter();
		File recordFile;
		String recordName = generateRecordName();
		String extension = getRecordingExtension();
		recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, extension));

		if (recordFile != null) {
			return recordFile;
		}
		throw new CantCreateFileException();
	}

	@Override
	public RecordingTarget provideRecordingTarget(Context context) throws CantCreateFileException {
		prefs.incrementRecordCounter();
		String recordName = generateRecordName();
		String extension = getRecordingExtension();
		String fileName = FileUtil.addExtension(recordName, extension);

		// Check if we should use SAF (SD card storage with SAF URI configured)
		if (isUsingSaf()) {
			return createSafTarget(context, fileName, extension);
		}

		// Fall back to regular file creation
		File recordFile = FileUtil.createFile(recordDirectory, fileName);
		if (recordFile != null) {
			return new RecordingTarget(recordFile);
		}
		throw new CantCreateFileException();
	}

	private RecordingTarget createSafTarget(Context context, String fileName, String extension) throws CantCreateFileException {
		Uri treeUri = getSafTreeUri();
		if (treeUri == null) {
			throw new CantCreateFileException();
		}

		try {
			ContentResolver resolver = context.getContentResolver();
			
			// Build the document ID for the tree
			String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
			
			// Get MIME type for the file
			String mimeType = getMimeType(extension);

			// Create the document URI for the parent
			Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);

			// Create the file using DocumentsContract
			Uri newFileUri = DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName);
			if (newFileUri == null) {
				Timber.e("Failed to create SAF file: %s", fileName);
				throw new CantCreateFileException();
			}

			// Build a display path for the database
			String displayPath = buildSafDisplayPath(treeUri, fileName);

			Timber.d("Created SAF file: %s (display: %s)", newFileUri, displayPath);
			return new RecordingTarget(newFileUri, displayPath);
		} catch (Exception e) {
			Timber.e(e, "Failed to create SAF recording target");
			throw new CantCreateFileException();
		}
	}

	private String getMimeType(String extension) {
		switch (extension.toLowerCase()) {
			case AppConstants.FORMAT_M4A:
				return "audio/mp4";
			case AppConstants.FORMAT_WAV:
				return "audio/wav";
			case AppConstants.FORMAT_3GP:
				return "audio/3gpp";
			default:
				return "audio/*";
		}
	}

	private String buildSafDisplayPath(Uri treeUri, String fileName) {
		// Try to extract a readable path from the tree URI
		// Tree URIs look like: content://com.android.externalstorage.documents/tree/6264-6362%3AAudioRecorder
		String path = treeUri.getPath();
		if (path != null && path.contains(":")) {
			// Extract the volume and path parts
			String[] parts = path.split(":");
			if (parts.length >= 2) {
				String treePart = parts[0];
				String folderPath = parts.length > 1 ? parts[1] : "";
				// Extract volume ID (like 6264-6362)
				if (treePart.contains("/")) {
					String volumeId = treePart.substring(treePart.lastIndexOf("/") + 1);
					return "/storage/" + volumeId + "/" + folderPath + "/" + fileName;
				}
			}
		}
		// Fallback: just use the URI string representation
		return treeUri.toString() + "/" + fileName;
	}

	private String generateRecordName() {
		switch (prefs.getSettingNamingFormat()) {
			default:
			case AppConstants.NAME_FORMAT_RECORD:
				return FileUtil.generateRecordNameCounted(prefs.getRecordCounter());
			case AppConstants.NAME_FORMAT_DATE:
				return FileUtil.generateRecordNameDateVariant();
			case AppConstants.NAME_FORMAT_DATE_US:
				return FileUtil.generateRecordNameDateUS();
			case AppConstants.NAME_FORMAT_DATE_ISO8601:
				return FileUtil.generateRecordNameDateISO8601();
			case AppConstants.NAME_FORMAT_TIMESTAMP:
				return FileUtil.generateRecordNameMills();
		}
	}

	private String getRecordingExtension() {
		switch (prefs.getSettingRecordingFormat()) {
			default:
			case AppConstants.FORMAT_M4A:
				return AppConstants.FORMAT_M4A;
			case AppConstants.FORMAT_WAV:
				return AppConstants.FORMAT_WAV;
			case AppConstants.FORMAT_3GP:
				return AppConstants.FORMAT_3GP;
		}
	}

	@Override
	public boolean isUsingSaf() {
		return prefs.getStorageLocation() == Prefs.STORAGE_SDCARD 
			&& prefs.getSafTreeUri() != null;
	}

	@Override
	public Uri getSafTreeUri() {
		String uriString = prefs.getSafTreeUri();
		if (uriString != null) {
			return Uri.parse(uriString);
		}
		return null;
	}

	@Override
	public File provideRecordFile(String name) throws CantCreateFileException {
		File recordFile = FileUtil.createFile(recordDirectory, name);
		if (recordFile != null) {
			return recordFile;
		}
		throw new CantCreateFileException();
	}

	@Override
	public File[] getPrivateDirFiles(Context context) {
		if (prefs.isStoreInSdCard() && recordDirectory != null) {
			File[] files = recordDirectory.listFiles();
			return files != null ? files : new File[] {};
		}
		try {
			return FileUtil.getPrivateRecordsDir(context).listFiles();
		} catch (FileNotFoundException e) {
			Timber.e(e);
			return new File[] {};
		}
	}

	@Override
	public File[] getPublicDirFiles() {
		File dir = resolvePublicDir();
		if (dir != null) {
			return dir.listFiles();
		} else {
			return new File[] {};
		}
	}

	@Override
	public File getPublicDir() {
		return resolvePublicDir();
	}

	@Override
	public File getPrivateDir(Context context) {
		if (prefs.isStoreInSdCard() && recordDirectory != null) {
			return recordDirectory;
		}
		try {
			return FileUtil.getPrivateRecordsDir(context);
		} catch (FileNotFoundException e) {
			Timber.e(e);
			return null;
		}
	}

//	@Override
//	public File getRecordFileByName(String name, String extension) {
//		File recordFile = new File(recordDirectory.getAbsolutePath() + File.separator + FileUtil.generateRecordNameCounted(prefs.getRecordCounter(), extension));
//		if (recordFile.exists() && recordFile.isFile()) {
//			return recordFile;
//		}
//		Timber.e("File %s was not found", recordFile.getAbsolutePath());
//		return null;
//	}

	@Override
	public File getRecordingDir() {
		return recordDirectory;
	}

	@Override
	public boolean deleteRecordFile(String path) {
		if (path != null) {
			return FileUtil.deleteFile(new File(path));
		}
		return false;
	}

	@Override
	public String markAsTrashRecord(String path) {
		String trashLocation = FileUtil.addExtension(path, AppConstants.TRASH_MARK_EXTENSION);
		if (FileUtil.renameFile(new File(path), new File(trashLocation))) {
			return trashLocation;
		}
		return null;
	}

	@Override
	public String unmarkTrashRecord(String path) {
		String restoredFile = FileUtil.removeFileExtension(path);
		if (FileUtil.renameFile(new File(path), new File(restoredFile))) {
			return restoredFile;
		}
		return null;
	}

	@Override
	public boolean deleteAllRecords() {
//		return FileUtil.deleteFile(recordDirectory);
		return false;
	}

	@Override
	public boolean renameFile(String path, String newName, String extension) {
		return FileUtil.renameFile(new File(path), newName, extension);
	}

	public void updateRecordingDir(Context context, Prefs prefs) {
		int storageLocation = prefs.getStorageLocation();

		switch (storageLocation) {
			case Prefs.STORAGE_SDCARD:
				File sdDir = resolveSdCardPublicDir(context);
				if (sdDir != null) {
					recordDirectory = sdDir;
					return;
				}
				Timber.w("SD card storage selected but unavailable. Falling back to internal storage.");
				// Fall through to internal storage
			case Prefs.STORAGE_INTERNAL:
			default:
				recordDirectory = resolvePublicDir();
				if (recordDirectory == null) {
					Timber.e("Failed to resolve public directory, using fallback");
					recordDirectory = FileUtil.getAppDir();
				}
				break;
			case Prefs.STORAGE_CUSTOM:
				recordDirectory = resolvePublicDir();
				if (recordDirectory == null) {
					Timber.e("Failed to resolve custom directory, falling back to internal");
					recordDirectory = FileUtil.getAppDir();
				}
				break;
		}

		// Final fallback - should never reach here
		if (recordDirectory == null) {
			recordDirectory = new File("/data/data/" + ARApplication.appPackage() + "/files");
		}
	}

	private File resolvePublicDir() {
		String customPath = prefs.getPublicDirectoryPath();
		if (!TextUtils.isEmpty(customPath)) {
			String normalized = customPath.trim();
			if (TextUtils.isEmpty(normalized)) {
				return resolveDefaultPublicDir();
			}
			File customDir = buildPublicDir(normalized);
			File ensuredCustom = FileUtil.createDir(customDir);
			if (ensuredCustom != null) {
				return ensuredCustom;
			}
			if (customDir.exists() && customDir.isDirectory()) {
				return customDir;
			}
			Timber.e("Failed to access custom public directory: %s", customDir.getAbsolutePath());
		}
		return resolveDefaultPublicDir();
	}

	private File resolveSdCardDir(Context context) {
		File musicDir = FileUtil.getSecondaryExternalMusicDir(context);
		if (musicDir != null) {
			try {
				return FileUtil.getPrivateRecordsDir(context, musicDir);
			} catch (FileNotFoundException e) {
				Timber.e(e);
			}
		}
		return null;
	}

	/**
	 * Resolve a PUBLIC directory on the SD card (survives uninstall).
	 */
	private File resolveSdCardPublicDir(Context context) {
		return FileUtil.getSecondaryExternalPublicDir(context, AppConstants.APPLICATION_NAME);
	}

	private File resolveDefaultPublicDir() {
		File defaultDir = FileUtil.getAppDir();
		if (defaultDir != null) {
			File ensuredDefault = FileUtil.createDir(defaultDir);
			if (ensuredDefault != null) {
				return ensuredDefault;
			}
			if (defaultDir.exists() && defaultDir.isDirectory()) {
				return defaultDir;
			}
		}
		return null;
	}

	private File buildPublicDir(String normalized) {
		File dir = new File(normalized);
		if (!dir.isAbsolute()) {
			dir = new File(Environment.getExternalStorageDirectory(), normalized);
		}
		return dir;
	}

	@Override
	public boolean hasAvailableSpace(Context context) throws IllegalArgumentException {
		long space;
		if (prefs.isStoreInSdCard() && recordDirectory != null) {
			space = FileUtil.getFree(recordDirectory);
		} else if (prefs.isStoreDirPublic()) {
//			TODO: deprecated fix this
			space = FileUtil.getAvailableExternalMemorySize();
		} else {
			space = FileUtil.getAvailableInternalMemorySize(context);
		}

		final long time = spaceToTimeSecs(space, prefs.getSettingRecordingFormat(),
				prefs.getSettingSampleRate(), prefs.getSettingBitrate(), prefs.getSettingChannelCount());
		return time > AppConstants.MIN_REMAIN_RECORDING_TIME;
	}

	private long spaceToTimeSecs(long spaceBytes, String recordingFormat, int sampleRate, int bitrate, int channels) {
		switch (recordingFormat) {
			case AppConstants.FORMAT_3GP:
				return 1000 * (spaceBytes/(AppConstants.RECORD_ENCODING_BITRATE_12000/8));
			case AppConstants.FORMAT_M4A:
				return 1000 * (spaceBytes/(bitrate/8));
			case AppConstants.FORMAT_WAV:
				return 1000 * (spaceBytes/(sampleRate * channels * 2));
			default:
				return 0;
		}
	}
}
