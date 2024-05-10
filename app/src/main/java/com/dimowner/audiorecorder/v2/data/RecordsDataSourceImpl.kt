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
import com.dimowner.audiorecorder.v2.data.model.RecordEditOperation
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditEntity
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@SuppressWarnings("TooGenericExceptionCaught")
@Singleton
class RecordsDataSourceImpl @Inject internal constructor(
    private val prefs: PrefsV2,
    private val recordDao: RecordDao,
    private val recordEditDao: RecordEditDao,
    private val fileDataSource: FileDataSource,
) : RecordsDataSource {

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

    override suspend fun updateRecord(record: Record) {
        return recordDao.updateRecord(record.toRecordEntity())
    }

    override suspend fun renameRecord(record: Record, newName: String): Boolean {
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
            return@let moveRecordToRecycle(recordToRecycle)
        } ?: false
    }

    private fun moveRecordToRecycle(recordToRecycle: RecordEntity): Boolean {
        try {
            //Save edit operation. Start transaction
            val transactionId = recordEditDao.insertRecordsEditOperation(
                createMoveToRecycleEditOperation(recordToRecycle.id)
            )
            //The first step. Mark record file as deleted.
            val path = try {
                fileDataSource.markAsRecordDeleted(recordToRecycle.path)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
            val result = if (path != null) {
                //The second step. Update record in the database
                val isUpdated = try {
                    recordDao.updateRecord(
                        recordToRecycle.copy(
                            path = path,
                            isMovedToRecycle = true
                        )
                    )
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
                if (isUpdated) {
                    //The second step has succeed. Finish edit operation and return an success.
                    deleteEditRecordOperation(transactionId)
                    true
                } else {
                    //The second step has failed. Rollback the first step.
                    val unmarkPath = try {
                        fileDataSource.unmarkRecordAsDeleted(path)
                    } catch (e: Exception) {
                        Timber.e(e)
                        null
                    }
                    if (unmarkPath != null) {
                        //File rolled back successfully. Finish edit operation and return an error.
                        deleteEditRecordOperation(transactionId)
                    } else {
                        //Failed to rollback file. Keep edit operation in the database to repeat it later.
                    }
                    false
                }
            } else {
                //The first step has failed. Finish edit operation and return an error.
                //Rollback not needed.
                deleteEditRecordOperation(transactionId)
                false
            }
            return result
        } catch (e: Exception) {
            Timber.e(e)
            return false
        }
    }

    override suspend fun restoreRecordFromRecycle(id: Long): Boolean {
        return recordDao.getRecordById(id)?.let { recordToRestore ->
            return@let restoreRecordFromRecycle(recordToRestore)
        } ?: false
    }

    private fun restoreRecordFromRecycle(recordToRestore: RecordEntity): Boolean {
        try {
            //Save edit operation. Start transaction
            val transactionId = recordEditDao.insertRecordsEditOperation(
                createRestoreFromRecycleEditOperation(recordToRestore.id)
            )
            //The first step. Unmark record file as deleted.
            val path = try {
                fileDataSource.unmarkRecordAsDeleted(recordToRestore.path)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
            val result = if (path != null) {
                //The second step. Update record in the database
                val isUpdated = try {
                    recordDao.updateRecord(
                        recordToRestore.copy(
                            path = path,
                            isMovedToRecycle = false
                        )
                    )
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
                if (isUpdated) {
                    //The second step has succeed. Finish edit operation and return an success.
                    deleteEditRecordOperation(transactionId)
                    true
                } else {
                    //The second step has failed. Rollback the first step.
                    val unmarkPath = try {
                        fileDataSource.markAsRecordDeleted(path)
                    } catch (e: Exception) {
                        Timber.e(e)
                        null
                    }
                    if (unmarkPath != null) {
                        //File rolled back successfully. Finish edit operation and return an error.
                        deleteEditRecordOperation(transactionId)
                    } else {
                        //Failed to rollback file. Keep edit operation in the database to repeat it later.
                    }
                    false
                }
            } else {
                //The first step has failed. Finish edit operation and return an error.
                //Rollback not needed.
                deleteEditRecordOperation(transactionId)
                false
            }
            return result
        } catch (e: Exception) {
            Timber.e(e)
            return false
        }
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
}
