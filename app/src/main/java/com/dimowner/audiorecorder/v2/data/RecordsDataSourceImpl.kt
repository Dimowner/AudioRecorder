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

import androidx.sqlite.db.SimpleSQLiteQuery
import com.dimowner.audiorecorder.v2.data.extensions.toRecordsSortColumnName
import com.dimowner.audiorecorder.v2.data.extensions.toSqlSortOrder
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.AppDatabase
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordsDataSourceImpl @Inject internal constructor(
    private val prefs: PrefsV2,
    private val recordDao: RecordDao,
    private val appDatabase: AppDatabase,
    private val fileDataSource: FileDataSource,
): RecordsDataSource {

    override suspend fun getRecord(id: Long): Record? {
        return if (id >= 0) {
            recordDao.getRecordById(id)?.toRecord()
        } else {
            null
        }
    }

    override suspend fun getActiveRecord(): Record? {
        val id = prefs.activeRecordId
        return if (id >= 0) {
            recordDao.getRecordById(id)?.toRecord()
        } else {
            null
        }
    }

    override suspend fun getAllRecords(): List<Record> {
        return recordDao.getAllRecords().map { it.toRecord() }
    }

    override suspend fun getRecords(
        page: Int,
        pageSize: Int,
        sortOrder: SortOrder,
        isBookmarked: Boolean
    ): List<Record> {
        val sb = StringBuilder()
        sb.append("SELECT * FROM records")
        if (isBookmarked) {
            sb.append(" WHERE isBookmarked = 1")
        }
        sb.append(" ORDER BY ${sortOrder.toRecordsSortColumnName()} ${sortOrder.toSqlSortOrder()}")
        sb.append(" LIMIT $pageSize")
        sb.append(" OFFSET " + ((page - 1) * pageSize))
        return recordDao.getRecordsRewQuery(SimpleSQLiteQuery(sb.toString())).map { it.toRecord() }
    }

    override suspend fun insertRecord(record: Record): Long {
        return recordDao.insertRecord(record.toRecordEntity())
    }

    override suspend fun updateRecord(record: Record) {
        return recordDao.updateRecord(record.toRecordEntity())
    }

    override suspend fun renameRecord(record: Record, newName: String) {
        appDatabase.runInTransaction {
            val renamed = fileDataSource.renameFile(record.path, newName)
            if (renamed == null) {
                throw IOException("Failed to rename file")
            } else {
                recordDao.updateRecord(
                    record.copy(
                        name = newName,
                        path = renamed.absolutePath
                    ).toRecordEntity()
                )
            }
        }
    }

    override suspend fun getRecordsCount(): Int {
        return recordDao.getRecordsCount()
    }

    override suspend fun getRecordTotalDuration(): Long {
        return recordDao.getRecordTotalDuration()
    }

    override suspend fun deleteRecord(id: Long) {
        return recordDao.deleteRecordById(id)
    }
}
