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

import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.FailedToRestoreRecord

/**
 * A [LocalRepository] delegate that transparently routes every call to either
 * [LocalRepositoryRoomImpl] (when [Prefs.isDatabaseMigratedToRoom] is `true`) or
 * [LocalRepositoryImpl] (legacy SQLite). The decision is re-evaluated on every call so that
 * the switch-over that happens during a migration session is picked up immediately without
 * needing to recreate the object.
 */
class LocalRepositoryDelegate(
    private val prefs: Prefs,
    private val legacyImpl: LocalRepositoryImpl,
    private val roomImpl: LocalRepositoryRoomImpl,
) : LocalRepository {

    /** Returns the active implementation based on the current migration state. */
    private val delegate: LocalRepository
        get() = if (prefs.isDatabaseMigratedToRoom()) roomImpl else legacyImpl

    // ── open / close ─────────────────────────────────────────────────────

    override fun open() = delegate.open()

    override fun close() = delegate.close()

    // ── Single record queries ─────────────────────────────────────────────

    override fun getRecord(id: Int): Record? = delegate.getRecord(id)

    override fun findRecordByPath(path: String): Record? = delegate.findRecordByPath(path)

    override fun findRecordsByPath(path: String): List<Record> = delegate.findRecordsByPath(path)

    override fun hasRecordsWithPath(path: String): Boolean = delegate.hasRecordsWithPath(path)

    override fun getTrashRecord(id: Int): Record? = delegate.getTrashRecord(id)

    // ── List queries ──────────────────────────────────────────────────────

    override fun getAllRecords(): List<Record> = delegate.getAllRecords()

    override fun getAllItemsIds(): List<Int> = delegate.getAllItemsIds()

    override fun getRecords(page: Int): List<Record> = delegate.getRecords(page)

    override fun getRecords(page: Int, order: Int): List<Record> = delegate.getRecords(page, order)

    override fun deleteAllRecords(): Boolean = delegate.deleteAllRecords()

    // ── Insert / Update ───────────────────────────────────────────────────

    override fun insertRecord(record: Record): Record? = delegate.insertRecord(record)

    override fun updateRecord(record: Record): Boolean = delegate.updateRecord(record)

    override fun updateTrashRecord(record: Record): Boolean = delegate.updateTrashRecord(record)

    @Throws(java.io.IOException::class)
    override fun insertEmptyFile(filePath: String): Record? = delegate.insertEmptyFile(filePath)

    // ── Delete / Trash ────────────────────────────────────────────────────

    override fun deleteRecord(id: Int): Boolean = delegate.deleteRecord(id)

    override fun deleteRecordForever(id: Int) = delegate.deleteRecordForever(id)

    override fun getRecordsDurations(): List<Long> = delegate.getRecordsDurations()

    // ── Bookmarks ─────────────────────────────────────────────────────────

    override fun addToBookmarks(id: Int): Boolean = delegate.addToBookmarks(id)

    override fun removeFromBookmarks(id: Int): Boolean = delegate.removeFromBookmarks(id)

    override fun getBookmarks(): List<Record> = delegate.getBookmarks()

    // ── Trash ─────────────────────────────────────────────────────────────

    override fun getTrashRecords(): List<Record> = delegate.getTrashRecords()

    override fun getTrashRecordsIds(): List<Int> = delegate.getTrashRecordsIds()

    override fun getTrashRecordsCount(): Int = delegate.getTrashRecordsCount()

    @Throws(FailedToRestoreRecord::class)
    override fun restoreFromTrash(id: Int) = delegate.restoreFromTrash(id)

    override fun removeFromTrash(id: Int): Boolean = delegate.removeFromTrash(id)

    override fun emptyTrash(): Boolean = delegate.emptyTrash()

    override fun removeOutdatedTrashRecords() = delegate.removeOutdatedTrashRecords()

    // ── Lost records listener ─────────────────────────────────────────────

    override fun setOnRecordsLostListener(listener: OnRecordsLostListener?) {
        // Register the listener on both implementations so it is already in place
        // whichever one becomes active after a migration.
        legacyImpl.setOnRecordsLostListener(listener)
        roomImpl.setOnRecordsLostListener(listener)
    }

    // ── Singleton ─────────────────────────────────────────────────────────

    companion object {

        @Volatile
        private var instance: LocalRepositoryDelegate? = null

        @JvmStatic
        fun getInstance(
            prefs: Prefs,
            localRepositoryImpl: LocalRepositoryImpl,
            localRepositoryRoomImpl: LocalRepositoryRoomImpl,
        ): LocalRepositoryDelegate {
            return instance ?: synchronized(this) {
                instance ?: LocalRepositoryDelegate(
                    prefs = prefs,
                    legacyImpl = localRepositoryImpl,
                    roomImpl = localRepositoryRoomImpl
                ).also { instance = it }
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

