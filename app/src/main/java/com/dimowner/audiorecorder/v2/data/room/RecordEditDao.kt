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
import androidx.room.Update

@Dao
interface RecordEditDao {

    @Query("SELECT * FROM record_edit ORDER BY created DESC")
    fun getAllRecordsEditOperations(): List<RecordEditEntity>

    @Query("SELECT * FROM record_edit WHERE id = :recordId")
    fun getRecordsEditOperationById(recordId: Long): RecordEditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecordsEditOperation(record: RecordEditEntity): Long

    @Update
    fun updateRecordsEditOperation(record: RecordEditEntity)

    @Delete
    fun deleteRecordsEditOperation(record: RecordEditEntity)

    @Query("DELETE FROM record_edit")
    fun deleteAllRecordsEditOperations()

    @Query("DELETE FROM record_edit WHERE id = :editOperationId")
    fun deleteRecordEditOperationById(editOperationId: Long)
}
