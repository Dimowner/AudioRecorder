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

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.audio.SoundFile;
import com.dimowner.audiorecorder.data.FileRepository;

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

	private RecordsDataSource dataSource;

	private TrashDataSource trashDataSource;

	private FileRepository fileRepository;

	private volatile static LocalRepositoryImpl instance;

	private OnRecordsLostListener onLostRecordsListener;

	private LocalRepositoryImpl(RecordsDataSource dataSource, TrashDataSource trashDataSource, FileRepository fileRepository) {
		this.dataSource = dataSource;
		this.trashDataSource = trashDataSource;
		this.fileRepository = fileRepository;
	}

	public static LocalRepositoryImpl getInstance(RecordsDataSource source, TrashDataSource trashSource, FileRepository fileRepository) {
		if (instance == null) {
			synchronized (LocalRepositoryImpl.class) {
				if (instance == null) {
					instance = new LocalRepositoryImpl(source, trashSource, fileRepository);
					instance.removeOutdatedTrashRecords();
				}
			}
		}
		return instance;
	}

	public void open() {
		dataSource.open();
		trashDataSource.open();
	}

	public void close() {
		dataSource.close();
		trashDataSource.close();
	}

	public Record getRecord(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Record r = dataSource.getItem(id);
		if (r != null) {
			List<Record> l = new ArrayList<>(1);
			l.add(r);
			checkForLostRecords(l);
			return r;
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
	public long insertFile(String path) throws IOException, OutOfMemoryError, IllegalStateException {
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
						0,
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
					0,
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
	public boolean updateWaveform(int id) throws IOException, OutOfMemoryError, IllegalStateException {
		Record record = getRecord(id);
		if (record != null) {
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
							record.getRemoved(),
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
		} else {
			Timber.e("Record is null!");
		}
		return false;
	}

	public List<Record> getAllRecords() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		List<Record> list = dataSource.getAll();
		checkForLostRecords(list);
		return list;
	}

	@Override
	public List<Record> getRecords(int page) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		List<Record> list = dataSource.getRecords(page);
		checkForLostRecords(list);
		return list;
	}

	@Override
	public List<Record> getRecords(int page, int order) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		String orderStr;
		switch (order) {
			case AppConstants.SORT_NAME:
				orderStr = SQLiteHelper.COLUMN_NAME + " ASC";
				break;
			case AppConstants.SORT_DURATION:
				orderStr = SQLiteHelper.COLUMN_DURATION + " DESC";
				break;
			case AppConstants.SORT_DATE:
			default:
				orderStr = SQLiteHelper.COLUMN_DATE_ADDED + " DESC";
		}
		List<Record> list = dataSource.getRecords(page, orderStr);
		checkForLostRecords(list);
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
				List<Record> l = new ArrayList<>(1);
				l.add(r);
				checkForLostRecords(l);
				return r;
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
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}
		Record recordToDelete = dataSource.getItem(id);
		if (recordToDelete != null) {
			String renamed = fileRepository.markAsTrashRecord(recordToDelete.getPath());
			recordToDelete.setPath(renamed);
			trashDataSource.insertItem(recordToDelete);
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
		if (r != null) {
			r.setBookmark(true);
			return (dataSource.updateItem(r) > 0);
		} else {
			return false;
		}
	}

	@Override
	public boolean removeFromBookmarks(int id) {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		Record r = dataSource.getItem(id);
		if (r != null) {
			r.setBookmark(false);
			return (dataSource.updateItem(r) > 0);
		} else {
			return false;
		}
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
					List<Record> l = new ArrayList<>(1);
					l.add(r);
					checkForLostRecords(l);
				}
			} while (c.moveToNext());
		} else {
			return new ArrayList<>();
		}
		Timber.v("Bookmarks: %s", list.toString());
		return list;
	}

	@Override
	public List<Record> getTrashRecords() {
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}
		return trashDataSource.getAll();
	}

	@Override
	public void restoreFromTrash(int id) {
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}

		Record recordToRestore = trashDataSource.getItem(id);
		String renamed = fileRepository.unmarkTrashRecord(recordToRestore.getPath());
		recordToRestore.setPath(renamed);
		insertRecord(recordToRestore);
		trashDataSource.deleteItem(id);
	}

	@Override
	public void removeFromTrash(int id) {
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}
		trashDataSource.deleteItem(id);
	}

	@Override
	public boolean emptyTrash() {
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}
		try {
			trashDataSource.deleteAll();
			return true;
		} catch (SQLException e) {
			Timber.e(e);
			return false;
		}
	}

	@Override
	public void removeOutdatedTrashRecords() {
		if (!trashDataSource.isOpen()) {
			trashDataSource.open();
		}
		long curTime = new Date().getTime();
		List<Record> list = trashDataSource.getAll();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getRemoved() + AppConstants.RECORD_IN_TRASH_MAX_DURATION < curTime) {
				trashDataSource.deleteItem(list.get(i).getId());
			}
		}
	}

	@Override
	public boolean deleteAllRecords() {
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		try {
//			dataSource.deleteAll();
			return true;
		} catch (SQLException e) {
			Timber.e(e);
			return false;
		}
	}

	private void checkForLostRecords(List<Record> list) {
		List<Record> lost = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			if (!isFileExists(list.get(i).getPath())) {
				lost.add(list.get(i));
			}
		}
		if (onLostRecordsListener != null && !lost.isEmpty()) {
			onLostRecordsListener.onLostRecords(lost);
		}
	}

	@Override
	public void setOnRecordsLostListener(OnRecordsLostListener onLostRecordsListener) {
		this.onLostRecordsListener = onLostRecordsListener;
	}
}
