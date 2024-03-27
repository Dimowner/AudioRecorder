/*
* Copyright 2024 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.v2.data

import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordsDataSourceImpl @Inject internal constructor(
    private val prefs: PrefsV2,
    private val recordDao: RecordDao,
): RecordsDataSource {
    override suspend fun getActiveRecord(): Record? {
        val id = prefs.activeRecordId
        return if (id >= 0) {
            recordDao.getRecordById(id)?.toRecord()
        } else {
            null
        }
    }

    override suspend fun getActiveRecords(): List<Record> {
        return recordDao.getAllRecords().map { it.toRecord() }
    }

    override suspend fun insertRecord(record: Record): Long {
        return recordDao.insertRecord(record.toRecordEntity())
    }

    override suspend fun getRecordsCount(): Int {
        return recordDao.getRecordsCount()
    }

    override suspend fun getRecordTotalDuration(): Long {
        return recordDao.getRecordTotalDuration()
    }

    override suspend fun deleteRecord(id: Int) {
        return recordDao.deleteRecordById(id)
    }
}
