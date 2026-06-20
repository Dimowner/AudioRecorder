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

@SuppressWarnings("TooManyFunctions")
@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun getRecordById(recordId: Long): RecordEntity?

    @Query("SELECT * FROM records WHERE id IN (:recordIds)")
    fun getRecordsByIds(recordIds: List<Long>): List<RecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecords(records: List<RecordEntity>)

    @Update
    fun updateRecord(record: RecordEntity): Int // Returns the number of updated rows

    @Update
    fun updateRecords(records: List<RecordEntity>): Int // Returns the total number of updated rows

    @Delete
    fun deleteRecord(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :recordId")
    fun deleteRecordById(recordId: Long)

    @Query("DELETE FROM records")
    fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM records WHERE isMovedToRecycle = 0")
    fun getRecordsCount(): Int

    @Query("SELECT SUM(duration) AS total_duration FROM records WHERE isMovedToRecycle = 0")
    fun getRecordTotalDuration(): Long

    @Query("SELECT * FROM records WHERE isMovedToRecycle = 0 ORDER BY added DESC LIMIT :pageSize OFFSET :offset")
    fun getRecordsByPage(pageSize: Int, offset: Int): List<RecordEntity>

    @Query("SELECT * FROM records WHERE isMovedToRecycle = 0 ORDER BY added DESC")
    fun getAllRecords(): List<RecordEntity>

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT id FROM records WHERE isMovedToRecycle = 0 ORDER BY added DESC")
    fun getAllRecordIds(): List<Long>

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT * FROM records WHERE path = :path AND isMovedToRecycle = 0 LIMIT 1")
    fun findRecordByPath(path: String): RecordEntity?

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT * FROM records WHERE path LIKE '%' || :path || '%' AND isMovedToRecycle = 0")
    fun findRecordsByPathLike(path: String): List<RecordEntity>

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT COUNT(*) FROM records WHERE path LIKE '%' || :path || '%' AND isMovedToRecycle = 0")
    fun countRecordsByPathLike(path: String): Int

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT * FROM records WHERE isBookmarked = 1 AND isMovedToRecycle = 0 ORDER BY created DESC")
    fun getBookmarkedRecords(): List<RecordEntity>

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT duration FROM records WHERE isMovedToRecycle = 0")
    fun getRecordsDurations(): List<Long>

    @Query("SELECT * FROM records WHERE isMovedToRecycle = 1 ORDER BY removed DESC")
    fun getMovedToRecycleRecords(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE isMovedToRecycle = 1 ORDER BY removed DESC LIMIT :pageSize OFFSET :offset")
    fun getMovedToRecycleRecordsByPage(pageSize: Int, offset: Int): List<RecordEntity>

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT id FROM records WHERE isMovedToRecycle = 1 ORDER BY removed DESC")
    fun getMovedToRecycleRecordIds(): List<Long>

    @Query("SELECT COUNT(*) FROM records WHERE isMovedToRecycle = 1")
    fun getMovedToRecycleRecordsCount(): Int

    @Deprecated("Used only for legacy app v1")
    @Query("SELECT * FROM records WHERE id = :recordId AND isMovedToRecycle = 1")
    fun getTrashRecordById(recordId: Long): RecordEntity?

    @Deprecated("Used only for legacy app v1")
    @Query("DELETE FROM records WHERE isMovedToRecycle = 1")
    fun deleteAllTrashRecords()

    @RawQuery
    fun getRecordsRewQuery(query: SupportSQLiteQuery): List<RecordEntity>

    @Query("SELECT DISTINCT format FROM records WHERE isMovedToRecycle = 0 AND format != '' ORDER BY format ASC")
    fun getDistinctFormats(): List<String>

    @Query("SELECT DISTINCT sampleRate FROM records WHERE isMovedToRecycle = 0 AND sampleRate > 0 ORDER BY sampleRate ASC")
    fun getDistinctSampleRates(): List<Int>

    @Query("SELECT DISTINCT channelCount FROM records WHERE isMovedToRecycle = 0 AND channelCount > 0 ORDER BY channelCount ASC")
    fun getDistinctChannelCounts(): List<Int>

    @Query("SELECT DISTINCT bitrate FROM records WHERE isMovedToRecycle = 0 AND bitrate > 0 ORDER BY bitrate ASC")
    fun getDistinctBitrates(): List<Int>

    /**
     * Returns records that appear to be broken due to an interrupted recording.
     * A broken record has duration=0 (meaning handleRecordingStopped never ran)
     * and is not moved to recycle.
     */
    @Query("SELECT * FROM records WHERE duration = 0 AND isMovedToRecycle = 0")
    fun getBrokenRecords(): List<RecordEntity>
}
