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
import com.dimowner.audiorecorder.v2.data.model.SortOrder

interface RecordsDataSource {

    suspend fun getRecord(id: Long): Record?

    suspend fun getActiveRecord(): Record?

    suspend fun getAllRecords(): List<Record>

    suspend fun getRecords(
        page: Int,
        pageSize: Int,
        sortOrder: SortOrder = SortOrder.DateDesc,
        isBookmarked: Boolean = false,
    ): List<Record>

    suspend fun insertRecord(record: Record): Long

    suspend fun updateRecord(record: Record)

    suspend fun renameRecord(record: Record, newName: String)

    suspend fun getRecordsCount(): Int

    suspend fun getRecordTotalDuration(): Long

    suspend fun deleteRecord(id: Long)
}
