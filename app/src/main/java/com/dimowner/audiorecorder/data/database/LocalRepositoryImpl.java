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

package com.dimowner.audiorecorder.data.database;

import android.database.Cursor;
import com.dimowner.audiorecorder.audio.SoundFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class LocalRepositoryImpl implements LocalRepository {

//**
// EXAMPLE
// 			LocalRepository rep = new LocalRepositoryImpl(new RecordsDataSource(getApplicationContext()));
//				rep.open();
//				rep.insertRecord(new Record(
//						Record.NO_ID,
//						"audio_rec_1542035885701.m4a",
//						new Date().getTime(),
//						"/storage/emulated/0/Android/data/com.dimowner.audiorecorder.debug/files/Music/records/audio_rec_1542035885701.m4a",
//						new int[] {10, 20, 30, 40, 50, 45, 81}
//						));
//
//				List<Record> recordList = rep.getAllRecords();
//				Timber.v("All records size: "+ recordList.size() + " 0 pos: " + recordList.get(0).toString());
//				rep.close();
//
// */

	public RecordsDataSource dataSource;

	private volatile static LocalRepositoryImpl instance;

	private LocalRepositoryImpl(RecordsDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public static LocalRepositoryImpl getInstance(RecordsDataSource source) {
		if (instance == null) {
			synchronized (LocalRepositoryImpl.class) {
				if (instance == null) {
					instance = new LocalRepositoryImpl(source);
				}
			}
		}
		return instance;
	}

	public void open() {
		dataSource.open();
	}

	public void close() {
		dataSource.close();
	}


	public Record getRecord(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Record r = dataSource.getItem(id);
		if (r != null) {
			if (isFileExists(r.getPath())) {
				return r;
			} else {
				//If Audio file deleted then delete record about it from local database.
				dataSource.deleteItem(r.getId());
				Timber.e("Audio file for this record is lost");
			}
		}
		return null;
	}

	public Record insertRecord(Record record) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		return dataSource.insertItem(record);
	}

	@Override
	public boolean updateRecord(Record record) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		//If updated record count is more than 0, then update is successful.
		return (dataSource.updateItem(record) > 0);
	}

	@Override
	public long insertFile(String path) throws IOException {
		if (path != null && !path.isEmpty()) {
			final SoundFile soundFile = SoundFile.create(path);
			if (soundFile != null) {
				File file = new File(path);
				Record record = new Record(
						Record.NO_ID,
						file.getName(),
						soundFile.getDuration(),
						file.lastModified(),
						path,
						soundFile.getFrameGains());
				Record r = insertRecord(record);
				if (r != null) {
					return r.getId();
				} else {
					Timber.e("Failed to insert record into local database!");
				}
			} else {
				Timber.e("Unable to read sound file by specified path!");
				throw new IOException("Unable to read sound file by specified path!");
			}
		} else {
			Timber.e("File path is null or empty");
		}
		return Record.NO_ID;
	}

	public List<Record> getAllRecords() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		List<Record> list = dataSource.getAll();
		//Remove not records with not existing audio files (which was lost or deleted)
		for (int i = 0; i < list.size(); i++) {
			if (!isFileExists(list.get(i).getPath())) {
				dataSource.deleteItem(list.get(i).getId());
			}
		}
		return list;
	}

	@Override
	public Record getLastRecord() {
		Timber.v("getActiveRecord");
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Cursor c = dataSource.queryLocal("SELECT * FROM " + SQLiteHelper.TABLE_RECORDS +
				" ORDER BY " + SQLiteHelper.COLUMN_ID + " DESC LIMIT 1");
		if (c != null && c.moveToFirst()) {
			Record r = dataSource.recordToItem(c);
			if (isFileExists(r.getPath())) {
				return r;
			} else {
				//If Audio file deleted then delete record about it from local database.
				dataSource.deleteItem(r.getId());
				return getLastRecord();
			}
		} else {
			return null;
		}
	}

	private boolean isFileExists(String path) {
		return new File(path).exists();
	}

	public void deleteRecord(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		dataSource.deleteItem(id);
	}

	@Override
	public List<Long> getRecordsDurations() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		return dataSource.getRecordsDurations();
	}
}
