package com.dimowner.audiorecorder.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun getRecordById(recordId: Int): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: RecordEntity)

    @Update
    fun updateRecord(record: RecordEntity)

    @Delete
    fun deleteRecord(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :recordId")
    fun deleteRecordById(recordId: Int)

    @Query("DELETE FROM records")
    fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM records")
    fun getRecordsCount(): Int

    @Query("SELECT * FROM records ORDER BY id LIMIT :pageSize OFFSET :offset")
    fun getRecordsByPage(pageSize: Int, offset: Int): List<RecordEntity>
}
