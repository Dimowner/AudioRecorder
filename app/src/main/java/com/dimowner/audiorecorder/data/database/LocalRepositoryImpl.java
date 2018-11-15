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

import com.dimowner.audiorecorder.audio.SoundFile;
import com.dimowner.audiorecorder.util.AndroidUtils;

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
//		TODO: make async
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		return dataSource.getItem(id);
	}

	public void insertRecord(Record record) {
//		TODO: make async
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		dataSource.insertItem(record);
	}

	@Override
	public void insertFile(final String lastFile, final OnCompleteListener listener) {
		if (lastFile != null && !lastFile.isEmpty()) {
			new Thread("SoundInsertion") {
				@Override
				public void run() {
					try {
						final SoundFile soundFile = SoundFile.create(lastFile);
						if (soundFile != null) {
							File file = new File(lastFile);
							Record record = new Record(
									Record.NO_ID,
									file.getName(),
									soundFile.getDuration(),
									file.lastModified(),
									lastFile,
									soundFile.getFrameGains());
							insertRecord(record);
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() { listener.onComplete(); }
							});

						} else {
							Timber.e("Unable to read sound file by specified path!");
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									listener.onError(new IOException("Unable to read sound file by specified path!"));
								}});
						}
					} catch (final IOException e) {
						Timber.e(e);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() { listener.onError(e);
						}});
					}
				}
			}.start();
		}
	}

	public List<Record> getAllRecords() {
		//		TODO: make async
		if (!dataSource.isOpen()) {
			dataSource.open();
		}
		return dataSource.getAll();
	}

	public void deleteRecord(int id) {
		//		TODO: make async
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
