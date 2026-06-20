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
import com.dimowner.audiorecorder.AppConstantsV2.RECORD_DESCRIPTION_MAX_LENGTH
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilter
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilterOptions
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer
import com.dimowner.audiorecorder.v2.audio.writeCommentTag
import com.dimowner.audiorecorder.v2.data.extensions.toRecordsSortColumnName
import com.dimowner.audiorecorder.v2.data.extensions.toSqlSortOrder
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@SuppressWarnings("TooGenericExceptionCaught")
@Singleton
class RecordsDataSourceImpl @Inject internal constructor(
    private val prefs: PrefsV2,
    private val recordDao: RecordDao,
    private val fileDataSource: FileDataSource,
    private val brokenRecordRestorer: BrokenRecordRestorer,
) : RecordsDataSource {

    override suspend fun getRecord(id: Long): Record? {
        return if (id >= 0) {
            recordDao.getRecordById(id)?.toRecord()
        } else {
            null
        }
    }

    override suspend fun getRecords(ids: List<Long>): List<Record> {
        val validIds = ids.filter { it >= 0 }
        return if (validIds.isNotEmpty()) {
            recordDao.getRecordsByIds(ids).map{ it.toRecord() }
        } else {
            emptyList()
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

    override suspend fun getMovedToRecycleRecords(): List<Record> {
        return recordDao.getMovedToRecycleRecords().map { it.toRecord() }
    }

    override suspend fun getMovedToRecycleRecords(page: Int, pageSize: Int): List<Record> {
        return recordDao.getMovedToRecycleRecordsByPage(pageSize, (page - 1) * pageSize).map { it.toRecord() }
    }

    override suspend fun getMovedToRecycleRecordsCount(): Int {
        return recordDao.getMovedToRecycleRecordsCount()
    }

    override suspend fun getRecords(
        page: Int,
        pageSize: Int,
        sortOrder: SortOrder,
        isBookmarked: Boolean,
        filter: RecordsFilter
    ): List<Record> {
        val args = mutableListOf<Any>()
        val sb = StringBuilder()
        sb.append("SELECT * FROM records")
        sb.append(" WHERE isMovedToRecycle = 0")
        if (isBookmarked) {
            sb.append(" AND isBookmarked = 1")
        }
        appendInClause(sb, args, "format", filter.formats)
        appendInClause(sb, args, "sampleRate", filter.sampleRates)
        appendInClause(sb, args, "channelCount", filter.channelCounts)
        appendInClause(sb, args, "bitrate", filter.bitrates)
        sb.append(" ORDER BY ${sortOrder.toRecordsSortColumnName()} ${sortOrder.toSqlSortOrder()}")
        sb.append(" LIMIT $pageSize")
        sb.append(" OFFSET " + ((page - 1) * pageSize))
        return recordDao.getRecordsRewQuery(SimpleSQLiteQuery(sb.toString(), args.toTypedArray()))
            .map { it.toRecord() }
    }

    /**
     * Appends an `AND column IN (?, ?, ...)` clause for the given [values], adding the bound
     * arguments to [args]. Empty value sets are ignored so the column is not filtered.
     */
    private fun appendInClause(
        sb: StringBuilder,
        args: MutableList<Any>,
        column: String,
        values: Collection<Any>
    ) {
        if (values.isEmpty()) return
        val placeholders = values.joinToString(separator = ", ") { "?" }
        sb.append(" AND $column IN ($placeholders)")
        args.addAll(values)
    }

    override suspend fun getFilterOptions(): RecordsFilterOptions {
        return RecordsFilterOptions(
            formats = recordDao.getDistinctFormats(),
            sampleRates = recordDao.getDistinctSampleRates(),
            channelCounts = recordDao.getDistinctChannelCounts(),
            bitrates = recordDao.getDistinctBitrates(),
        )
    }

    override suspend fun insertRecord(record: Record): Long {
        return recordDao.insertRecord(record.toRecordEntity())
    }

    override suspend fun updateRecord(record: Record): Boolean {
        return recordDao.updateRecord(record.toRecordEntity()) == 1
    }

    override suspend fun updateRecords(records: List<Record>): Int {
        return recordDao.updateRecords(records.map { it.toRecordEntity() })
    }

    override suspend fun renameRecord(record: Record, newName: String): Boolean {
        return try {
            val renamed = try {
                fileDataSource.renameFile(record.path, newName)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
            if (renamed == null) {
                // Step 1 failed — nothing to roll back.
                false
            } else {
                val isUpdated = try {
                    val updated = recordDao.updateRecord(
                        record.copy(
                            name = newName,
                            path = renamed.absolutePath
                        ).toRecordEntity()
                    )
                    if (updated == 0) {
                        throw Exception("No records updated")
                    }
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    // Step 2 failed — roll back the file rename.
                    try {
                        fileDataSource.renameFile(renamed.absolutePath, record.name)
                    } catch (re: Exception) {
                        Timber.e(re, "Failed to rollback file rename after DB update failure")
                    }
                    false
                }
                isUpdated
            }
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    override suspend fun updateRecordDescription(
        recordId: Long,
        description: String,
        writeToFile: Boolean,
    ): Boolean {
        return try {
            val record = getRecord(recordId)
            if (record != null) {
                val truncated = description.take(RECORD_DESCRIPTION_MAX_LENGTH)
                val updated = updateRecord(record.copy(description = truncated))
                if (updated) {
                    if (writeToFile) {
                        File(record.path).writeCommentTag(truncated)
                    } else {
                        File(record.path).writeCommentTag("")
                    }
                }
                updated
            } else {
                Timber.w("updateRecordDescription: record %s not found", recordId)
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "updateRecordDescription failed for record %s", recordId)
            false
        }
    }

    override suspend fun getRecordsCount(): Int {
        return recordDao.getRecordsCount()
    }

    override suspend fun getRecordTotalDuration(): Long {
        return recordDao.getRecordTotalDuration()
    }

    override suspend fun deleteRecordAndFileForever(id: Long): Boolean {
        return recordDao.getRecordById(id)?.let { recordToDelete ->
            return@let deleteRecordAndFileForever(recordToDelete)
        } ?: false
    }

    private fun deleteRecordAndFileForever(record: RecordEntity): Boolean {
        fun deleteFile(): Boolean {
            return try {
                fileDataSource.deleteRecordFile(record.path)
            } catch (e: Exception) {
                Timber.e(e)
                false
            }
        }

        return try {
            // Step 1 — delete record from database.
            val isRecordDeleted = try {
                recordDao.deleteRecordById(record.id)
                true
            } catch (e: Exception) {
                Timber.e(e)
                false
            }
            if (isRecordDeleted) {
                // Step 2 — delete the file from disk.
                if (!deleteFile()) {
                    //Retry deleting the file once more.
                    deleteFile()
                } else {
                    true
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    override suspend fun moveRecordToRecycle(id: Long): Boolean {
        return recordDao.getRecordById(id)?.let { recordToRecycle ->
            return@let recordDao.updateRecord(
                recordToRecycle.copy(isMovedToRecycle = true, removed = System.currentTimeMillis())
            ) == 1
        } ?: false
    }

    override suspend fun moveRecordsToRecycle(ids: List<Long>): Int {
        val recordsToRecycle = recordDao.getRecordsByIds(ids).map {
            it.copy(isMovedToRecycle = true, removed = System.currentTimeMillis())
        }
        return recordDao.updateRecords(recordsToRecycle)
    }

    override suspend fun restoreRecordFromRecycle(id: Long): Boolean {
        return recordDao.getRecordById(id)?.let { recordToRestore ->
            return@let recordDao.updateRecord(
                recordToRestore.copy(isMovedToRecycle = false, removed = Long.MAX_VALUE)
            ) == 1
        } ?: false
    }

    override suspend fun clearRecycle(): Boolean {
        val records = recordDao.getMovedToRecycleRecords()
        return if (records.isNotEmpty()) {
            var result = true
            for (recordToDelete in records) {
                if (!deleteRecordAndFileForever(recordToDelete)) {
                    result = false
                }
            }
            result
        } else {
            false
        }
    }


    override suspend fun deleteLostRecord(id: Long): Boolean {
        return try {
            // For lost records, the file doesn't exist, so we only need to delete from DB
            recordDao.deleteRecordById(id)
            if (prefs.activeRecordId == id) {
                prefs.activeRecordId = -1
            }
            true
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    override suspend fun getBrokenRecords(): List<Record> {
        return try {
            recordDao.getBrokenRecords()
                .map { it.toRecord() }
                .filter { record ->
                    // Only include records whose file exists on disk with non-zero size.
                    // If the file doesn't exist or is empty, the record data is truly lost.
                    val file = File(record.path)
                    file.exists() && file.length() > 0
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get broken records")
            emptyList()
        }
    }

    override suspend fun restoreBrokenRecord(recordId: Long): Boolean {
        return try {
            val record = recordDao.getRecordById(recordId)?.toRecord() ?: return false
            val file = File(record.path)
            if (!file.exists() || file.length() == 0L) {
                Timber.e("Cannot restore broken record: file does not exist or is empty: ${record.path}")
                return false
            }

            // Attempt to restore the file
            val restoreResult = brokenRecordRestorer.restoreFile(
                filePath = record.path,
                sampleRate = record.sampleRate,
                channelCount = record.channelCount,
                bitrate = record.bitrate,
            )

            when (restoreResult) {
                is BrokenRecordRestorer.RestoreResult.Success,
                is BrokenRecordRestorer.RestoreResult.AlreadyReadable -> {
                    // File is now readable — read its metadata
                    val info = AudioDecoder.readRecordInfo(file)
                    val updatedRecord = record.copy(
                        durationMills = if (info.duration >= 0) info.duration / 1000 else 0,
                        format = info.format,
                        size = info.size,
                        sampleRate = if (info.sampleRate > 0) info.sampleRate else record.sampleRate,
                        channelCount = if (info.channelCount > 0) info.channelCount else record.channelCount,
                        bitrate = if (info.bitrate > 0) info.bitrate else record.bitrate,
                    )
                    val success = recordDao.updateRecord(updatedRecord.toRecordEntity()) == 1
                    if (success) {
                        Timber.d("Broken record restored successfully: id=$recordId, duration=${updatedRecord.durationMills}ms")
                    }
                    success
                }
                is BrokenRecordRestorer.RestoreResult.Failed -> {
                    Timber.e("Failed to restore broken record: id=$recordId, error=${restoreResult.error}")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore broken record: id=$recordId")
            false
        }
    }
}
