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

import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilter
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilterOptions
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.SortOrder

interface RecordsDataSource {

    suspend fun getRecord(id: Long): Record?
    suspend fun getRecords(ids: List<Long>): List<Record>

    suspend fun getActiveRecord(): Record?

    suspend fun getAllRecords(): List<Record>
    suspend fun getMovedToRecycleRecords(): List<Record>
    suspend fun getMovedToRecycleRecords(page: Int, pageSize: Int): List<Record>
    suspend fun getMovedToRecycleRecordsCount(): Int

    suspend fun getRecords(
        page: Int,
        pageSize: Int,
        sortOrder: SortOrder = SortOrder.DateDesc,
        isBookmarked: Boolean = false,
        filter: RecordsFilter = RecordsFilter(),
    ): List<Record>

    /**
     * Returns the distinct filter values (formats, sample rates, channel counts, bitrates)
     * available among the records currently in the list (excluding the recycle bin).
     */
    suspend fun getFilterOptions(): RecordsFilterOptions

    suspend fun insertRecord(record: Record): Long

    suspend fun updateRecord(record: Record): Boolean

    suspend fun updateRecords(records: List<Record>): Int

    suspend fun renameRecord(record: Record, newName: String): Boolean

    /**
     * Persists a record's description to the database and, when [writeToFile] is true,
     * also writes it as the COMMENT tag in the audio file metadata.
     *
     * @param recordId The database id of the record to update.
     * @param description The new description text (may be blank to clear the note).
     * @param writeToFile When true, embed the description as a COMMENT tag in the audio file.
     * When false, the COMMENT tag is removed from the audio file and the description is saved
     * to the database only.
     * @return true if the database update succeeded.
     */
    suspend fun updateRecordDescription(
        recordId: Long,
        description: String,
        writeToFile: Boolean,
    ): Boolean

    suspend fun getRecordsCount(): Int

    suspend fun getRecordTotalDuration(): Long

    suspend fun deleteRecordAndFileForever(id: Long): Boolean

    suspend fun moveRecordToRecycle(id: Long): Boolean

    suspend fun moveRecordsToRecycle(ids: List<Long>): Int

    suspend fun restoreRecordFromRecycle(id: Long): Boolean

    suspend fun clearRecycle(): Boolean

    suspend fun deleteLostRecord(id: Long): Boolean

    /**
     * Returns records that appear to be broken due to an interrupted recording.
     * A broken record has duration=0 and size=0 in the database,
     * but its file exists on disk with non-zero size.
     */
    suspend fun getBrokenRecords(): List<Record>

    /**
     * Attempts to restore a broken record by fixing its audio file container
     * and updating the database with the recovered metadata.
     *
     * @return true if restoration was successful
     */
    suspend fun restoreBrokenRecord(recordId: Long): Boolean
}
