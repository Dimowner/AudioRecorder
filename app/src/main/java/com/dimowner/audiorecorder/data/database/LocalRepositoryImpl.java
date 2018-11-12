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

import java.util.List;

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

	public LocalRepositoryImpl(RecordsDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void open() {
		dataSource.open();
	}

	public void close() {
		dataSource.close();
	}


	public Record getRecord(int id) {
//		TODO: make async
		if (dataSource.isOpen()) {
			return dataSource.getItem(id);
		}
		return null;
	}

	public void insertRecord(Record record) {
//		TODO: make async
		if (dataSource.isOpen()) {
			dataSource.insertItem(record);
		}
	}

	public List<Record> getAllRecords() {
		//		TODO: make async
		if (dataSource.isOpen()) {
			return dataSource.getAll();
		}
		return null;
	}

	public void deleteRecord(int id) {
		//		TODO: make async
		if (dataSource.isOpen()) {
			dataSource.deleteItem(id);
		}
	}
}
