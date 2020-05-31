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

package com.dimowner.audiorecorder.data;

import android.content.Context;

import com.dimowner.audiorecorder.exception.CantCreateFileException;

import java.io.File;

public interface FileRepository {

	File provideRecordFile() throws CantCreateFileException;

	File provideRecordFile(String name) throws CantCreateFileException;

//	File getRecordFileByName(String name, String extension);

	File[] getPrivateDirFiles(Context context);

	File[] getPublicDirFiles();

	File getPublicDir();

	File getPrivateDir(Context context);

	File getRecordingDir();

	boolean deleteRecordFile(String path);

	String markAsTrashRecord(String path);

	String unmarkTrashRecord(String path);

	boolean deleteAllRecords();

	boolean renameFile(String path, String newName, String extension);

	void updateRecordingDir(Context context, Prefs prefs);

	boolean hasAvailableSpace(Context context) throws IllegalArgumentException;
}
