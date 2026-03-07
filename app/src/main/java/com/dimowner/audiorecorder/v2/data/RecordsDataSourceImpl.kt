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
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer
import com.dimowner.audiorecorder.v2.data.extensions.toRecordsSortColumnName
import com.dimowner.audiorecorder.v2.data.extensions.toSqlSortOrder
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordEditOperation
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditEntity
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
    private val recordEditDao: RecordEditDao,
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

    override suspend fun getMovedToRecycleRecordsCount(): Int {
        return recordDao.getMovedToRecycleRecordsCount()
    }

    override suspend fun getRecords(
        page: Int,
        pageSize: Int,
        sortOrder: SortOrder,
        isBookmarked: Boolean
    ): List<Record> {
        val sb = StringBuilder()
        sb.append("SELECT * FROM records")
        sb.append(" WHERE isMovedToRecycle = 0")
        if (isBookmarked) {
            sb.append(" AND isBookmarked = 1")
        }
        sb.append(" ORDER BY ${sortOrder.toRecordsSortColumnName()} ${sortOrder.toSqlSortOrder()}")
        sb.append(" LIMIT $pageSize")
        sb.append(" OFFSET " + ((page - 1) * pageSize))
        return recordDao.getRecordsRewQuery(SimpleSQLiteQuery(sb.toString())).map { it.toRecord() }
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
        //TODO: this function requires improvements
        try {
            val transactionId = recordEditDao.insertRecordsEditOperation(
                createRenameEditOperation(record.id, newName)
            )
            val renamed = try {
                fileDataSource.renameFile(record.path, newName)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
            val result = if (renamed == null) {
                //The first step has failed. Finish edit operation and return an error.
                deleteEditRecordOperation(transactionId)
                false
            } else {
                val isUpdated = try {
                    //Perform the step 2
                    recordDao.updateRecord(
                        record.copy(
                            name = newName,
                            path = renamed.absolutePath
                        ).toRecordEntity()
                    )
                    deleteEditRecordOperation(transactionId)
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    //The second step has failed. Rollback the first step - rename file back.
                    val rolledBack = try {
                        fileDataSource.renameFile(renamed.absolutePath, record.name)
                    } catch (e: Exception) {
                        Timber.e(e)
                        null
                    }
                    if (rolledBack != null) {
                        //File name rolled back successfully. Finish edit operation and return an error.
                        deleteEditRecordOperation(transactionId)
                    } else {
                        //Failed to rollback file. Keep edit operation in the database to repeat it later.
                    }
                    false
                }
                isUpdated
            }
            return result
        } catch (e: Exception) {
            Timber.e(e)
            return false
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
        try {
            val transactionId = recordEditDao.insertRecordsEditOperation(
                createDeleteForeverEditOperation(record.id)
            )

            //The first step - delete record from database
            val isRecordDeleted = try {
                recordDao.deleteRecordById(record.id)
                true
            } catch (e: Exception) {
                Timber.e(e)
                false
            }
            val result = if (isRecordDeleted) {
                //The second step - delete record file
                val isFileDeleted = try {
                    fileDataSource.deleteRecordFile(record.path)
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
                if (isFileDeleted) {
                    //The second step has succeed. Finish edit operation and return an success.
                    deleteEditRecordOperation(transactionId)
                    true
                } else {
                    //Failed to delete file. Keep edit operation in the database to repeat it later.
                    false
                }
            } else {
                //The first step has failed. Finish edit operation and return an error.
                deleteEditRecordOperation(transactionId)
                false
            }
            return result
        } catch (e: Exception) {
            Timber.e(e)
            return false
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

//    @Deprecated("Too complex logic. We don't need to mark record file as deleted")
//    internal fun moveRecordToRecycle(recordToRecycle: RecordEntity): Boolean {
//        try {
//            //Save edit operation. Start transaction
//            val transactionId = recordEditDao.insertRecordsEditOperation(
//                createMoveToRecycleEditOperation(recordToRecycle.id)
//            )
//            //The first step. Mark record file as deleted.
//            val path = try {
//                fileDataSource.markAsRecordDeleted(recordToRecycle.path)
//            } catch (e: Exception) {
//                Timber.e(e)
//                null
//            }
//            val result = if (path != null) {
//                //The second step. Update record in the database
//                val isUpdated = try {
//                    recordDao.updateRecord(
//                        recordToRecycle.copy(
//                            path = path,
//                            isMovedToRecycle = true,
//                            removed = System.currentTimeMillis(),
//                        )
//                    )
//                    true
//                } catch (e: Exception) {
//                    Timber.e(e)
//                    false
//                }
//                if (isUpdated) {
//                    //The second step has succeed. Finish edit operation and return an success.
//                    deleteEditRecordOperation(transactionId)
//                    true
//                } else {
//                    //The second step has failed. Rollback the first step.
//                    val unmarkPath = try {
//                        fileDataSource.unmarkRecordAsDeleted(path)
//                    } catch (e: Exception) {
//                        Timber.e(e)
//                        null
//                    }
//                    if (unmarkPath != null) {
//                        //File rolled back successfully. Finish edit operation and return an error.
//                        deleteEditRecordOperation(transactionId)
//                    } else {
//                        //Failed to rollback file. Keep edit operation in the database to repeat it later.
//                    }
//                    false
//                }
//            } else {
//                //The first step has failed. Finish edit operation and return an error.
//                //Rollback not needed.
//                deleteEditRecordOperation(transactionId)
//                false
//            }
//            return result
//        } catch (e: Exception) {
//            Timber.e(e)
//            return false
//        }
//    }

    override suspend fun restoreRecordFromRecycle(id: Long): Boolean {
        return recordDao.getRecordById(id)?.let { recordToRestore ->
            return@let recordDao.updateRecord(
                recordToRestore.copy(isMovedToRecycle = false, removed = -1)
            ) == 1
        } ?: false
    }

//    @Deprecated("Too complex logic. We don't need to mark record file as deleted")
//    private fun restoreRecordFromRecycle(recordToRestore: RecordEntity): Boolean {
//        try {
//            //Save edit operation. Start transaction
//            val transactionId = recordEditDao.insertRecordsEditOperation(
//                createRestoreFromRecycleEditOperation(recordToRestore.id)
//            )
//            //The first step. Unmark record file as deleted.
//            val path = try {
//                fileDataSource.unmarkRecordAsDeleted(recordToRestore.path)
//            } catch (e: Exception) {
//                Timber.e(e)
//                null
//            }
//            val result = if (path != null) {
//                //The second step. Update record in the database
//                val isUpdated = try {
//                    recordDao.updateRecord(
//                        recordToRestore.copy(
//                            path = path,
//                            isMovedToRecycle = false
//                        )
//                    )
//                    true
//                } catch (e: Exception) {
//                    Timber.e(e)
//                    false
//                }
//                if (isUpdated) {
//                    //The second step has succeed. Finish edit operation and return an success.
//                    deleteEditRecordOperation(transactionId)
//                    true
//                } else {
//                    //The second step has failed. Rollback the first step.
//                    val unmarkPath = try {
//                        fileDataSource.markAsRecordDeleted(path)
//                    } catch (e: Exception) {
//                        Timber.e(e)
//                        null
//                    }
//                    if (unmarkPath != null) {
//                        //File rolled back successfully. Finish edit operation and return an error.
//                        deleteEditRecordOperation(transactionId)
//                    } else {
//                        //Failed to rollback file. Keep edit operation in the database to repeat it later.
//                    }
//                    false
//                }
//            } else {
//                //The first step has failed. Finish edit operation and return an error.
//                //Rollback not needed.
//                deleteEditRecordOperation(transactionId)
//                false
//            }
//            return result
//        } catch (e: Exception) {
//            Timber.e(e)
//            return false
//        }
//    }

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

    private fun createRenameEditOperation(recordId: Long, renameName: String): RecordEditEntity {
        return RecordEditEntity(
            recordId = recordId,
            editOperation = RecordEditOperation.Rename,
            renameName = renameName,
            created = System.currentTimeMillis(),
            retryCount = 0,
        )
    }

    private fun createMoveToRecycleEditOperation(recordId: Long): RecordEditEntity {
        return RecordEditEntity(
            recordId = recordId,
            editOperation = RecordEditOperation.MoveToRecycle,
            renameName = null,
            created = System.currentTimeMillis(),
            retryCount = 0,
        )
    }

    private fun createRestoreFromRecycleEditOperation(recordId: Long): RecordEditEntity {
        return RecordEditEntity(
            recordId = recordId,
            editOperation = RecordEditOperation.RestoreFromRecycle,
            renameName = null,
            created = System.currentTimeMillis(),
            retryCount = 0,
        )
    }

    private fun createDeleteForeverEditOperation(recordId: Long): RecordEditEntity {
        return RecordEditEntity(
            recordId = recordId,
            editOperation = RecordEditOperation.DeleteForever,
            renameName = null,
            created = System.currentTimeMillis(),
            retryCount = 0,
        )
    }

    private fun deleteEditRecordOperation(id: Long) {
        try {
            recordEditDao.deleteRecordEditOperationById(id)
        } catch (e: Exception) {
            Timber.e(e)
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
//            val restoreResult = brokenRecordRestorer.restoreFile(record.path)
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
