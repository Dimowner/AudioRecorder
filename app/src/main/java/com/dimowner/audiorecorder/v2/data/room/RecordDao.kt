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

package com.dimowner.audiorecorder.v2.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun getRecordById(recordId: Long): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: RecordEntity): Long

    @Update
    fun updateRecord(record: RecordEntity)

    @Delete
    fun deleteRecord(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :recordId")
    fun deleteRecordById(recordId: Long)

    @Query("DELETE FROM records")
    fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM records")
    fun getRecordsCount(): Int

    @Query("SELECT SUM(duration) AS total_duration FROM records;")
    fun getRecordTotalDuration(): Long

    @Query("SELECT * FROM records ORDER BY added DESC LIMIT :pageSize OFFSET :offset")
    fun getRecordsByPage(pageSize: Int, offset: Int): List<RecordEntity>

    @Query("SELECT * FROM records ORDER BY added DESC")
    fun getAllRecords(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE isMovedToRecycle = 1 ORDER BY removed DESC")
    fun getMovedToRecycleRecords(): List<RecordEntity>

    @RawQuery
    fun getRecordsRewQuery(query: SupportSQLiteQuery): List<RecordEntity>
}
