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

import java.io.File;
import timber.log.Timber;

import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.util.FileUtil;

public class FileRepositoryImpl implements FileRepository {

	public static final String PRIVATE_DIR_NAME = "records";

	private File recordDirectory;

	public FileRepositoryImpl(File recordsRid) {
		Timber.v("FileRepositoryImpl file: " + recordsRid);
		this.recordDirectory = recordsRid;
	}

	@Override
	public File provideRecordFile() throws CantCreateFileException {
		File recordFile = FileUtil.createFile(recordDirectory, FileUtil.generateRecordName());
		if (recordFile != null) {
			Timber.v("provideRecordFile: %s", recordFile.getAbsolutePath() + " isExists = " + recordFile.exists() + " isDir = " + recordFile.isDirectory());
			return recordFile;
		}
		throw new CantCreateFileException();
	}

	@Override
	public File getRecordFileByName(String name) {
		File recordFile = new File(recordDirectory.getAbsolutePath() + File.separator + FileUtil.generateRecordName());
		if (recordFile.exists() && recordFile.isFile()) {
			return recordFile;
		}
		Timber.e("File %s was not found", recordFile.getAbsolutePath());
		return null;
	}

	@Override
	public boolean deleteRecordFileByName(String name) {
		File recordFile = new File(recordDirectory.getAbsolutePath() + File.separator + FileUtil.generateRecordName());
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
	public void deleteAllRecords() {
		FileUtil.deleteFile(recordDirectory);
	}

	@Override
	public void setRecordingDir(File file) {
		this.recordDirectory = file;
	}
}
