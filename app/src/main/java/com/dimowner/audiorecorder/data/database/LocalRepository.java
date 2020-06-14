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

import com.dimowner.audiorecorder.exception.FailedToRestoreRecord;

import java.io.IOException;
import java.util.List;

public interface LocalRepository {

	void open();

	void close();

	Record getRecord(int id);

	Record findRecordByPath(String path);

	Record getTrashRecord(int id);

	List<Record> getAllRecords();

	List<Integer> getAllItemsIds();

	List<Record> getRecords(int page);

	List<Record> getRecords(int page, int order);

	boolean deleteAllRecords();

	Record getLastRecord();

	Record insertRecord(Record record);

	boolean updateRecord(Record record);

	boolean updateTrashRecord(Record record);

	Record insertEmptyFile(String filePath) throws IOException;

	void deleteRecord(int id);

	void deleteRecordForever(int id);

	List<Long> getRecordsDurations();

	boolean addToBookmarks(int id);

	boolean removeFromBookmarks(int id);

	List<Record> getBookmarks();

	List<Record> getTrashRecords();

	List<Integer> getTrashRecordsIds();

	int getTrashRecordsCount();

	void restoreFromTrash(int id) throws FailedToRestoreRecord;

	boolean removeFromTrash(int id);

	boolean emptyTrash();

	void removeOutdatedTrashRecords();

	void setOnRecordsLostListener(OnRecordsLostListener listener);
}
