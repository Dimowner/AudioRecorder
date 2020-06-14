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

import java.io.File;
import java.io.FileNotFoundException;

import timber.log.Timber;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.util.FileUtil;

public class FileRepositoryImpl implements FileRepository {

	private File recordDirectory;
	private Prefs prefs;

	private volatile static FileRepositoryImpl instance;

	private FileRepositoryImpl(Context context, Prefs prefs) {
		updateRecordingDir(context, prefs);
		this.prefs = prefs;
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
		String recordName;
		switch (prefs.getSettingNamingFormat()) {
			default:
			case AppConstants.NAME_FORMAT_RECORD:
				recordName = FileUtil.generateRecordNameCounted(prefs.getRecordCounter());
				break;
			case AppConstants.NAME_FORMAT_DATE:
				recordName = FileUtil.generateRecordNameDateVariant();
				break;
			case AppConstants.NAME_FORMAT_TIMESTAMP:
				recordName = FileUtil.generateRecordNameMills();
				break;
		}
		switch (prefs.getSettingRecordingFormat()) {
			default:
			case AppConstants.FORMAT_M4A:
				recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, AppConstants.FORMAT_M4A));
				break;
			case AppConstants.FORMAT_WAV:
				recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, AppConstants.FORMAT_WAV));
				break;
			case AppConstants.FORMAT_3GP:
				recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, AppConstants.FORMAT_3GP));
				break;
		}

//		if (prefs.getNamingFormat() == AppConstants.NAMING_COUNTED) {
//			recordName = FileUtil.generateRecordNameCounted(prefs.getRecordCounter());
//		} else {
//			recordName = FileUtil.generateRecordNameDate();
//		}
//		if (prefs.getSettingRecordingFormat() == AppConstants.RECORDING_FORMAT_WAV) {
//			recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, AppConstants.WAV_EXTENSION));
//		} else {
//			recordFile = FileUtil.createFile(recordDirectory, FileUtil.addExtension(recordName, AppConstants.M4A_EXTENSION));
//		}
		if (recordFile != null) {
			return recordFile;
		}
		throw new CantCreateFileException();
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
		try {
			return FileUtil.getPrivateRecordsDir(context).listFiles();
		} catch (FileNotFoundException e) {
			Timber.e(e);
			return new File[] {};
		}
	}

	@Override
	public File[] getPublicDirFiles() {
		File dir = FileUtil.getAppDir();
		if (dir != null) {
			return dir.listFiles();
		} else {
			return new File[] {};
		}
	}

	@Override
	public File getPublicDir() {
		return FileUtil.getAppDir();
	}

	@Override
	public File getPrivateDir(Context context) {
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
		if (prefs.isStoreDirPublic()) {
			recordDirectory = FileUtil.getAppDir();
			if (recordDirectory == null) {
				//Try to init private dir
				try {
					recordDirectory = FileUtil.getPrivateRecordsDir(context);
				} catch (FileNotFoundException e) {
					Timber.e(e);
					//If nothing helped then hardcode recording dir
					recordDirectory = new File("/data/data/" + ARApplication.appPackage() + "/files");
				}
			}
		} else {
			try {
				recordDirectory = FileUtil.getPrivateRecordsDir(context);
			} catch (FileNotFoundException e) {
				Timber.e(e);
				//Try to init public dir
				recordDirectory = FileUtil.getAppDir();
				if (recordDirectory == null) {
					//If nothing helped then hardcode recording dir
					recordDirectory = new File("/data/data/" + ARApplication.appPackage() + "/files");
				}
			}
		}
	}

	@Override
	public boolean hasAvailableSpace(Context context) throws IllegalArgumentException {
		long space;
		if (prefs.isStoreDirPublic()) {
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
