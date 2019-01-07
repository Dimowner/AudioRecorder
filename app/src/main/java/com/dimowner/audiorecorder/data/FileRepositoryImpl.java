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
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.util.FileUtil;

public class FileRepositoryImpl implements FileRepository {

	public static final String PRIVATE_DIR_NAME = "records";

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
		File recordFile = FileUtil.createFile(recordDirectory, FileUtil.generateRecordNameCounted(prefs.getRecordCounter()));
		if (recordFile != null) {
			Timber.v("provideRecordFile: %s", recordFile.getAbsolutePath() + " isExists = " + recordFile.exists() + " isDir = " + recordFile.isDirectory());
			return recordFile;
		}
		throw new CantCreateFileException();
	}

	@Override
	public File provideRecordFile(String name) throws CantCreateFileException {
		File recordFile = FileUtil.createFile(recordDirectory, name);
		if (recordFile != null) {
			Timber.v("provideRecordFile: %s", recordFile.getAbsolutePath() + " isExists = " + recordFile.exists() + " isDir = " + recordFile.isDirectory());
			return recordFile;
		}
		throw new CantCreateFileException();
	}

	@Override
	public File getRecordFileByName(String name) {
		File recordFile = new File(recordDirectory.getAbsolutePath() + File.separator + FileUtil.generateRecordNameCounted(prefs.getRecordCounter()));
		if (recordFile.exists() && recordFile.isFile()) {
			return recordFile;
		}
		Timber.e("File %s was not found", recordFile.getAbsolutePath());
		return null;
	}

	@Override
	public boolean deleteRecordFileByName(String name) {
		File recordFile = new File(recordDirectory.getAbsolutePath() + File.separator + FileUtil.generateRecordNameCounted(prefs.getRecordCounter()));
		return FileUtil.deleteFile(recordFile);
	}

	@Override
	public boolean deleteRecordFile(String path) {
		if (path != null) {
			return FileUtil.deleteFile(new File(path));
		}
		return false;
	}

	@Override
	public boolean deleteAllRecords() {
		return FileUtil.deleteFile(recordDirectory);
	}

	@Override
	public boolean renameFile(String path, String newName) {
		return FileUtil.renameFile(new File(path), newName);
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
		Timber.v("updateRecordingDir: " + recordDirectory.getAbsolutePath());
	}
}
