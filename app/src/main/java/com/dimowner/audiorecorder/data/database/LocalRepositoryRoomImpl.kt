/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.data.database

import androidx.sqlite.db.SimpleSQLiteQuery
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.BackgroundQueue
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.FailedToRestoreRecord
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Date

/**
 * Implementation of [LocalRepository] backed by Room database using [RecordDao].
 *
 * Unlike [LocalRepositoryImpl] which uses separate SQLite tables for records and trash,
 * this implementation uses a single "records" table with an [RecordEntity.isMovedToRecycle] flag
 * to distinguish active records from trashed ones (matching the v2 app approach).
 *
 * Soft-delete: [deleteRecord] sets `isMovedToRecycle = true` instead of moving data
 * between tables. No file renaming is performed on delete/restore — the file stays in place.
 */
class LocalRepositoryRoomImpl(
    private val recordDao: RecordDao,
    private val fileRepository: FileRepository,
    private val prefs: Prefs,
) : LocalRepository {

    @Volatile
    private var onLostRecordsListener: OnRecordsLostListener? = null

    // ── open / close ────────────────────────────────────────────────────
    // Room manages its own connection lifecycle; these are no-ops.

    override fun open() { /* no-op */ }

    override fun close() { /* no-op */ }

    // ── Single record queries ───────────────────────────────────────────

    override fun getRecord(id: Int): Record? {
        val entity = recordDao.getRecordById(id.toLong()) ?: return null
        if (entity.isMovedToRecycle) return null
        val record = entity.toV1Record()
        checkForLostRecords(listOf(record))
        return record
    }

    override fun findRecordByPath(path: String): Record? {
        return recordDao.findRecordByPath(path)?.toV1Record()
    }

    override fun findRecordsByPath(path: String): List<Record> {
        return recordDao.findRecordsByPathLike(path).map { it.toV1Record() }
    }

    override fun hasRecordsWithPath(path: String): Boolean {
        return recordDao.countRecordsByPathLike(path) > 0
    }

    override fun getTrashRecord(id: Int): Record? {
        return recordDao.getTrashRecordById(id.toLong())?.toV1Record()
    }

    // ── List queries ────────────────────────────────────────────────────

    override fun getAllRecords(): List<Record> {
        val list = recordDao.getAllRecords().map { it.toV1Record() }
        checkForLostRecords(list)
        return list
    }

    override fun getAllItemsIds(): List<Int> {
        return recordDao.getAllRecordIds().map { it.toInt() }
    }

    override fun getRecords(page: Int): List<Record> {
        val pageSize = AppConstants.DEFAULT_PER_PAGE
        val offset = (page - 1) * pageSize
        val list = recordDao.getRecordsByPage(pageSize, offset).map { it.toV1Record() }
        checkForLostRecords(list)
        return list
    }

    override fun getRecords(page: Int, order: Int): List<Record> {
        val orderClause = when (order) {
            AppConstants.SORT_NAME -> "name ASC"
            AppConstants.SORT_NAME_DESC -> "name DESC"
            AppConstants.SORT_DURATION -> "duration DESC"
            AppConstants.SORT_DURATION_DESC -> "duration ASC"
            AppConstants.SORT_DATE_DESC -> "added ASC"
            else /* AppConstants.SORT_DATE */ -> "added DESC"
        }
        val pageSize = AppConstants.DEFAULT_PER_PAGE
        val offset = (page - 1) * pageSize
        val sql = "SELECT * FROM records WHERE isMovedToRecycle = 0" +
                " ORDER BY $orderClause" +
                " LIMIT $pageSize OFFSET $offset"
        val list = recordDao.getRecordsRewQuery(SimpleSQLiteQuery(sql)).map { it.toV1Record() }
        checkForLostRecords(list)
        return list
    }

    override fun deleteAllRecords(): Boolean {
        // Matches the original implementation which is disabled.
        return false
    }

    // ── Insert / Update ─────────────────────────────────────────────────

    override fun insertRecord(record: Record): Record {
        val entity = record.toRecordEntity()
        val newId = recordDao.insertRecord(entity)
        return Record(
            newId.toInt(), record.name, record.duration, record.created,
            record.added, record.removed, record.path, record.format,
            record.size, record.sampleRate, record.channelCount, record.bitrate,
            record.isBookmarked, record.isWaveformProcessed, record.amps
        )
    }

    override fun updateRecord(record: Record): Boolean {
        return recordDao.updateRecord(record.toRecordEntity()) > 0
    }

    override fun updateTrashRecord(record: Record): Boolean {
        // In Room impl, trash records live in the same table.
        return recordDao.updateRecord(record.toRecordEntity(isMovedToRecycle = true)) > 0
    }

    override fun insertEmptyFile(filePath: String?): Record? {
        if (filePath.isNullOrEmpty()) {
            Timber.e("Unable to read sound file by specified path!")
            throw IOException("Unable to read sound file by specified path!")
        }
        val file = File(filePath)
        val record = Record(
            Record.NO_ID,
            FileUtil.removeFileExtension(file.name),
            0L, // duration
            file.lastModified(),
            Date().time,
            Long.MAX_VALUE,
            filePath,
            prefs.settingRecordingFormat,
            0L,
            prefs.settingSampleRate,
            prefs.settingChannelCount,
            prefs.settingBitrate,
            false,
            false,
            IntArray(ARApplication.longWaveformSampleCount)
        )
        return insertRecord(record)
    }

    // ── Delete / Trash ──────────────────────────────────────────────────

    /**
     * Soft-deletes a record by setting `isMovedToRecycle = true`.
     * Unlike [LocalRepositoryImpl], no file renaming is performed —
     * the file stays in place matching the v2 approach.
     */
    override fun deleteRecord(id: Int): Boolean {
        val entity = recordDao.getRecordById(id.toLong()) ?: return false
        if (entity.isMovedToRecycle) return false
        return recordDao.updateRecord(
            entity.copy(isMovedToRecycle = true, removed = System.currentTimeMillis())
        ) > 0
    }

    override fun deleteRecordForever(id: Int) {
        recordDao.deleteRecordById(id.toLong())
    }

    override fun getRecordsDurations(): List<Long> {
        return recordDao.getRecordsDurations()
    }

    // ── Bookmarks ───────────────────────────────────────────────────────

    override fun addToBookmarks(id: Int): Boolean {
        val entity = recordDao.getRecordById(id.toLong()) ?: return false
        return recordDao.updateRecord(entity.copy(isBookmarked = true)) > 0
    }

    override fun removeFromBookmarks(id: Int): Boolean {
        val entity = recordDao.getRecordById(id.toLong()) ?: return false
        return recordDao.updateRecord(entity.copy(isBookmarked = false)) > 0
    }

    override fun getBookmarks(): List<Record> {
        return recordDao.getBookmarkedRecords().map { it.toV1Record() }
    }

    // ── Trash ───────────────────────────────────────────────────────────

    override fun getTrashRecords(): List<Record> {
        return recordDao.getMovedToRecycleRecords().map { it.toV1Record() }
    }

    override fun getTrashRecordsIds(): List<Int> {
        return recordDao.getMovedToRecycleRecordIds().map { it.toInt() }
    }

    override fun getTrashRecordsCount(): Int {
        return recordDao.getMovedToRecycleRecordsCount()
    }

    /**
     * Restores a record from trash by clearing `isMovedToRecycle`.
     * No file renaming is performed — the file is assumed to still be in its original location.
     */
    @Throws(FailedToRestoreRecord::class)
    override fun restoreFromTrash(id: Int) {
        val entity = recordDao.getTrashRecordById(id.toLong())
            ?: throw FailedToRestoreRecord()
        val updated = recordDao.updateRecord(
            entity.copy(isMovedToRecycle = false, removed = Long.MAX_VALUE)
        )
        if (updated <= 0) {
            throw FailedToRestoreRecord()
        }
    }

    override fun removeFromTrash(id: Int): Boolean {
        val entity = recordDao.getTrashRecordById(id.toLong()) ?: return false
        recordDao.deleteRecord(entity)
        return true
    }

    override fun emptyTrash(): Boolean {
        return try {
            recordDao.deleteAllTrashRecords()
            true
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    override fun removeOutdatedTrashRecords() {
        val curTime = Date().time
        val trashRecords = recordDao.getMovedToRecycleRecords()
        for (record in trashRecords) {
            if (record.removed + AppConstants.RECORD_IN_TRASH_MAX_DURATION < curTime) {
                recordDao.deleteRecordById(record.id)
                fileRepository.deleteRecordFile(record.path)
            }
        }
    }

    // ── Lost records listener ───────────────────────────────────────────

    override fun setOnRecordsLostListener(listener: OnRecordsLostListener?) {
        this.onLostRecordsListener = listener
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun checkForLostRecords(list: List<Record>) {
        val lost = list.filter { !File(it.path).exists() }
        if (lost.isNotEmpty()) {
            onLostRecordsListener?.onLostRecords(lost)
        }
    }

    companion object {

        @Volatile
        private var instance: LocalRepositoryRoomImpl? = null


        @JvmStatic
        fun getInstance(
            recordDao: RecordDao,
            fileRepository: FileRepository,
            prefs: Prefs,
            loadingTasks: BackgroundQueue,
        ): LocalRepositoryRoomImpl {
            return instance ?: synchronized(this) {
                instance ?: LocalRepositoryRoomImpl(recordDao, fileRepository, prefs).also {
                    instance = it
                    loadingTasks.postRunnable {
                        it.removeOutdatedTrashRecords()
                    }
                }
            }
        }

        @JvmStatic
        fun clearInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Mapping extensions between v1 Record (Java) ↔ v2 RecordEntity (Room)
// ════════════════════════════════════════════════════════════════════════

/**
 * Converts a Room [RecordEntity] to a v1 [Record].
 *
 * Notable mappings:
 * - `id`: Long → Int (safe for typical record counts)
 * - `isBookmarked` → `bookmark`
 * - `amps`: IntArray is passed directly
 * - `removed`: kept as-is; [Long.MAX_VALUE] means "not removed"
 */
internal fun RecordEntity.toV1Record(): Record {
    return Record(
        id.toInt(),
        name,
        duration*1000, //Record V1 duration is in microseconds while RecordEntity duration is in milliseconds
        created,
        added,
        removed,
        path,
        format,
        size,
        sampleRate,
        channelCount,
        bitrate,
        isBookmarked,
        isWaveformProcessed,
        amps
    )
}

/**
 * Converts a v1 [Record] to a Room [RecordEntity].
 *
 * @param isMovedToRecycle Override for the recycle flag. Defaults to `false`.
 */
internal fun Record.toRecordEntity(isMovedToRecycle: Boolean = false): RecordEntity {
    return RecordEntity(
        id = if (id == Record.NO_ID) 0L else id.toLong(),
        name = name,
        duration = duration/1000, //Record V1 duration is in microseconds while RecordEntity duration is in milliseconds
        created = created,
        added = added,
        removed = removed,
        path = path,
        format = format ?: "",
        size = size,
        sampleRate = sampleRate,
        channelCount = channelCount,
        bitrate = bitrate,
        isBookmarked = isBookmarked,
        isWaveformProcessed = isWaveformProcessed,
        isMovedToRecycle = isMovedToRecycle,
        amps = amps ?: intArrayOf()
    )
}



