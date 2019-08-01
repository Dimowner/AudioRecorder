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
import android.database.SQLException;

import com.dimowner.audiorecorder.audio.SoundFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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
						new Date().getTime(),
						path,
						false,
						true,
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

	@Override
	public long insertFile(String path, long duration, int[] waveform) throws IOException {
		if (path != null && !path.isEmpty()) {
			File file = new File(path);
			Record record = new Record(
					Record.NO_ID,
					file.getName(),
					duration,
					file.lastModified(),
					new Date().getTime(),
					path,
					false,
					false,
					waveform);
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
		return Record.NO_ID;
	}

	@Override
	public boolean updateWaveform(int id) throws IOException, OutOfMemoryError {
		Record record = getRecord(id);
		String path = record.getPath();
		if (path != null && !path.isEmpty()) {
			final SoundFile soundFile = SoundFile.create(path);
			if (soundFile != null) {
				Record rec = new Record(
						record.getId(),
						record.getName(),
						record.getDuration(),
						record.getCreated(),
						record.getAdded(),
						record.getPath(),
						record.isBookmarked(),
						true,
						soundFile.getFrameGains());
				boolean b = updateRecord(rec);
				if (b) {
					return true;
				} else {
					Timber.e("Failed to update record id = %d in local database!", rec.getId());
				}
			} else {
				Timber.e("Unable to read sound file by specified path!");
				throw new IOException("Unable to read sound file by specified path!");
			}
		} else {
			Timber.e("File path is null or empty");
		}
		return false;
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
	public List<Record> getRecords(int page) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		List<Record> list = dataSource.getRecords(page);
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
				//If Audio file deleted then delete record from local database.
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

	@Override
	public boolean addToBookmarks(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Record r = dataSource.getItem(id);
		r.setBookmark(true);
		return (dataSource.updateItem(r) > 0);
	}

	@Override
	public boolean removeFromBookmarks(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Record r = dataSource.getItem(id);
		r.setBookmark(false);
		return (dataSource.updateItem(r) > 0);
	}

	@Override
	public List<Record> getBookmarks() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		List<Record> list = new ArrayList<>();
		Cursor c = dataSource.queryLocal("SELECT * FROM " + SQLiteHelper.TABLE_RECORDS +
				" WHERE " + SQLiteHelper.COLUMN_BOOKMARK + " = 1" +
				" ORDER BY " + SQLiteHelper.COLUMN_CREATION_DATE  + " DESC");

		if (c != null && c.moveToFirst()) {
			do {
				Record r = dataSource.recordToItem(c);
				if (isFileExists(r.getPath())) {
					list.add(r);
				} else {
					//If Audio file deleted then delete record from local database.
					dataSource.deleteItem(r.getId());
				}
			} while (c.moveToNext());
		} else {
			return new ArrayList<>();
		}
		Timber.v("Bookmarks: " + list.toString());
		return list;
	}

	@Override
	public boolean deleteAllRecords() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		try {
			dataSource.deleteAll();
			return true;
		} catch (SQLException e) {
			Timber.e(e);
			return false;
		}
	}
}
