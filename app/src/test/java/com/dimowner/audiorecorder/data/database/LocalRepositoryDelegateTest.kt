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
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNotSame
import junit.framework.TestCase.assertSame
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class LocalRepositoryDelegateTest {

    @MockK
    lateinit var prefs: Prefs

    @MockK
    lateinit var legacyImpl: LocalRepositoryImpl

    @MockK
    lateinit var roomImpl: LocalRepositoryRoomImpl

    private lateinit var delegate: LocalRepositoryDelegate

    private lateinit var testRecord: Record

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        testRecord = Record(
            101,
            "test_name",
            100L,
            100500L,
            500100L,
            0L,
            "test/path",
            "m4a",
            4096L,
            44100,
            2,
            128000,
            false,
            true,
            intArrayOf(1, 2, 3, 4)
        )

        delegate = LocalRepositoryDelegate(prefs, legacyImpl, roomImpl)
    }

    @After
    fun tearDown() {
        LocalRepositoryDelegate.clearInstance()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun useLegacy() = every { prefs.isDatabaseMigratedToRoom() } returns false
    private fun useRoom() = every { prefs.isDatabaseMigratedToRoom() } returns true

    // ── open / close ──────────────────────────────────────────────────────────

    @Test
    fun `open - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.open() } just runs

        delegate.open()

        verify(exactly = 1) { legacyImpl.open() }
        verify(exactly = 0) { roomImpl.open() }
    }

    @Test
    fun `open - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.open() } just runs

        delegate.open()

        verify(exactly = 1) { roomImpl.open() }
        verify(exactly = 0) { legacyImpl.open() }
    }

    @Test
    fun `close - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.close() } just runs

        delegate.close()

        verify(exactly = 1) { legacyImpl.close() }
        verify(exactly = 0) { roomImpl.close() }
    }

    @Test
    fun `close - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.close() } just runs

        delegate.close()

        verify(exactly = 1) { roomImpl.close() }
        verify(exactly = 0) { legacyImpl.close() }
    }

    // ── getRecord ─────────────────────────────────────────────────────────────

    @Test
    fun `getRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getRecord(101) } returns testRecord

        val result = delegate.getRecord(101)

        assertEquals(testRecord, result)
        verify(exactly = 1) { legacyImpl.getRecord(101) }
        verify(exactly = 0) { roomImpl.getRecord(any()) }
    }

    @Test
    fun `getRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getRecord(101) } returns testRecord

        val result = delegate.getRecord(101)

        assertEquals(testRecord, result)
        verify(exactly = 1) { roomImpl.getRecord(101) }
        verify(exactly = 0) { legacyImpl.getRecord(any()) }
    }

    @Test
    fun `getRecord - returns null when record not found`() {
        useRoom()
        every { roomImpl.getRecord(999) } returns null

        val result = delegate.getRecord(999)

        assertEquals(null, result)
    }

    // ── findRecordByPath ──────────────────────────────────────────────────────

    @Test
    fun `findRecordByPath - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.findRecordByPath("test/path") } returns testRecord

        val result = delegate.findRecordByPath("test/path")

        assertEquals(testRecord, result)
        verify(exactly = 1) { legacyImpl.findRecordByPath("test/path") }
        verify(exactly = 0) { roomImpl.findRecordByPath(any()) }
    }

    @Test
    fun `findRecordByPath - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.findRecordByPath("test/path") } returns testRecord

        val result = delegate.findRecordByPath("test/path")

        assertEquals(testRecord, result)
        verify(exactly = 1) { roomImpl.findRecordByPath("test/path") }
        verify(exactly = 0) { legacyImpl.findRecordByPath(any()) }
    }

    // ── findRecordsByPath ─────────────────────────────────────────────────────

    @Test
    fun `findRecordsByPath - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.findRecordsByPath("test/") } returns listOf(testRecord)

        val result = delegate.findRecordsByPath("test/")

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.findRecordsByPath("test/") }
        verify(exactly = 0) { roomImpl.findRecordsByPath(any()) }
    }

    @Test
    fun `findRecordsByPath - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.findRecordsByPath("test/") } returns listOf(testRecord)

        val result = delegate.findRecordsByPath("test/")

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { roomImpl.findRecordsByPath("test/") }
        verify(exactly = 0) { legacyImpl.findRecordsByPath(any()) }
    }

    // ── hasRecordsWithPath ────────────────────────────────────────────────────

    @Test
    fun `hasRecordsWithPath - routes to legacyImpl and returns true`() {
        useLegacy()
        every { legacyImpl.hasRecordsWithPath("test/path") } returns true

        assertTrue(delegate.hasRecordsWithPath("test/path"))
        verify(exactly = 1) { legacyImpl.hasRecordsWithPath("test/path") }
        verify(exactly = 0) { roomImpl.hasRecordsWithPath(any()) }
    }

    @Test
    fun `hasRecordsWithPath - routes to roomImpl and returns false`() {
        useRoom()
        every { roomImpl.hasRecordsWithPath("test/path") } returns false

        assertFalse(delegate.hasRecordsWithPath("test/path"))
        verify(exactly = 1) { roomImpl.hasRecordsWithPath("test/path") }
        verify(exactly = 0) { legacyImpl.hasRecordsWithPath(any()) }
    }

    // ── getTrashRecord ────────────────────────────────────────────────────────

    @Test
    fun `getTrashRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getTrashRecord(101) } returns testRecord

        val result = delegate.getTrashRecord(101)

        assertEquals(testRecord, result)
        verify(exactly = 1) { legacyImpl.getTrashRecord(101) }
        verify(exactly = 0) { roomImpl.getTrashRecord(any()) }
    }

    @Test
    fun `getTrashRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getTrashRecord(101) } returns testRecord

        val result = delegate.getTrashRecord(101)

        assertEquals(testRecord, result)
        verify(exactly = 1) { roomImpl.getTrashRecord(101) }
        verify(exactly = 0) { legacyImpl.getTrashRecord(any()) }
    }

    // ── getAllRecords ──────────────────────────────────────────────────────────

    @Test
    fun `getAllRecords - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getAllRecords() } returns listOf(testRecord)

        val result = delegate.getAllRecords()

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.getAllRecords() }
        verify(exactly = 0) { roomImpl.getAllRecords() }
    }

    @Test
    fun `getAllRecords - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getAllRecords() } returns listOf(testRecord)

        val result = delegate.getAllRecords()

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { roomImpl.getAllRecords() }
        verify(exactly = 0) { legacyImpl.getAllRecords() }
    }

    // ── getAllItemsIds ─────────────────────────────────────────────────────────

    @Test
    fun `getAllItemsIds - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getAllItemsIds() } returns listOf(1, 2, 3)

        val result = delegate.getAllItemsIds()

        assertEquals(listOf(1, 2, 3), result)
        verify(exactly = 1) { legacyImpl.getAllItemsIds() }
        verify(exactly = 0) { roomImpl.getAllItemsIds() }
    }

    @Test
    fun `getAllItemsIds - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getAllItemsIds() } returns listOf(4, 5, 6)

        val result = delegate.getAllItemsIds()

        assertEquals(listOf(4, 5, 6), result)
        verify(exactly = 1) { roomImpl.getAllItemsIds() }
        verify(exactly = 0) { legacyImpl.getAllItemsIds() }
    }

    // ── getRecords(page) ──────────────────────────────────────────────────────

    @Test
    fun `getRecords page - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getRecords(0) } returns listOf(testRecord)

        val result = delegate.getRecords(0)

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.getRecords(0) }
        verify(exactly = 0) { roomImpl.getRecords(any<Int>()) }
    }

    @Test
    fun `getRecords page - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getRecords(0) } returns listOf(testRecord)

        val result = delegate.getRecords(0)

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { roomImpl.getRecords(0) }
        verify(exactly = 0) { legacyImpl.getRecords(any<Int>()) }
    }

    // ── getRecords(page, order) ───────────────────────────────────────────────

    @Test
    fun `getRecords page order - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getRecords(0, 1) } returns listOf(testRecord)

        val result = delegate.getRecords(0, 1)

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.getRecords(0, 1) }
        verify(exactly = 0) { roomImpl.getRecords(any<Int>(), any<Int>()) }
    }

    @Test
    fun `getRecords page order - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getRecords(0, 1) } returns listOf(testRecord)

        val result = delegate.getRecords(0, 1)

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { roomImpl.getRecords(0, 1) }
        verify(exactly = 0) { legacyImpl.getRecords(any<Int>(), any<Int>()) }
    }

    // ── deleteAllRecords ──────────────────────────────────────────────────────

    @Test
    fun `deleteAllRecords - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.deleteAllRecords() } returns true

        assertTrue(delegate.deleteAllRecords())
        verify(exactly = 1) { legacyImpl.deleteAllRecords() }
        verify(exactly = 0) { roomImpl.deleteAllRecords() }
    }

    @Test
    fun `deleteAllRecords - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.deleteAllRecords() } returns false

        assertFalse(delegate.deleteAllRecords())
        verify(exactly = 1) { roomImpl.deleteAllRecords() }
        verify(exactly = 0) { legacyImpl.deleteAllRecords() }
    }

    // ── insertRecord ──────────────────────────────────────────────────────────

    @Test
    fun `insertRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.insertRecord(testRecord) } returns testRecord

        val result = delegate.insertRecord(testRecord)

        assertEquals(testRecord, result)
        verify(exactly = 1) { legacyImpl.insertRecord(testRecord) }
        verify(exactly = 0) { roomImpl.insertRecord(any()) }
    }

    @Test
    fun `insertRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.insertRecord(testRecord) } returns testRecord

        val result = delegate.insertRecord(testRecord)

        assertEquals(testRecord, result)
        verify(exactly = 1) { roomImpl.insertRecord(testRecord) }
        verify(exactly = 0) { legacyImpl.insertRecord(any()) }
    }

    // ── updateRecord ──────────────────────────────────────────────────────────

    @Test
    fun `updateRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.updateRecord(testRecord) } returns true

        assertTrue(delegate.updateRecord(testRecord))
        verify(exactly = 1) { legacyImpl.updateRecord(testRecord) }
        verify(exactly = 0) { roomImpl.updateRecord(any()) }
    }

    @Test
    fun `updateRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.updateRecord(testRecord) } returns true

        assertTrue(delegate.updateRecord(testRecord))
        verify(exactly = 1) { roomImpl.updateRecord(testRecord) }
        verify(exactly = 0) { legacyImpl.updateRecord(any()) }
    }

    // ── updateTrashRecord ─────────────────────────────────────────────────────

    @Test
    fun `updateTrashRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.updateTrashRecord(testRecord) } returns true

        assertTrue(delegate.updateTrashRecord(testRecord))
        verify(exactly = 1) { legacyImpl.updateTrashRecord(testRecord) }
        verify(exactly = 0) { roomImpl.updateTrashRecord(any()) }
    }

    @Test
    fun `updateTrashRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.updateTrashRecord(testRecord) } returns false

        assertFalse(delegate.updateTrashRecord(testRecord))
        verify(exactly = 1) { roomImpl.updateTrashRecord(testRecord) }
        verify(exactly = 0) { legacyImpl.updateTrashRecord(any()) }
    }

    // ── insertEmptyFile ───────────────────────────────────────────────────────

    @Test
    fun `insertEmptyFile - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.insertEmptyFile("test/path") } returns testRecord

        val result = delegate.insertEmptyFile("test/path")

        assertEquals(testRecord, result)
        verify(exactly = 1) { legacyImpl.insertEmptyFile("test/path") }
        verify(exactly = 0) { roomImpl.insertEmptyFile(any()) }
    }

    @Test
    fun `insertEmptyFile - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.insertEmptyFile("test/path") } returns testRecord

        val result = delegate.insertEmptyFile("test/path")

        assertEquals(testRecord, result)
        verify(exactly = 1) { roomImpl.insertEmptyFile("test/path") }
        verify(exactly = 0) { legacyImpl.insertEmptyFile(any()) }
    }

    // ── deleteRecord ──────────────────────────────────────────────────────────

    @Test
    fun `deleteRecord - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.deleteRecord(101) } returns true

        assertTrue(delegate.deleteRecord(101))
        verify(exactly = 1) { legacyImpl.deleteRecord(101) }
        verify(exactly = 0) { roomImpl.deleteRecord(any()) }
    }

    @Test
    fun `deleteRecord - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.deleteRecord(101) } returns false

        assertFalse(delegate.deleteRecord(101))
        verify(exactly = 1) { roomImpl.deleteRecord(101) }
        verify(exactly = 0) { legacyImpl.deleteRecord(any()) }
    }

    // ── deleteRecordForever ───────────────────────────────────────────────────

    @Test
    fun `deleteRecordForever - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.deleteRecordForever(101) } just runs

        delegate.deleteRecordForever(101)

        verify(exactly = 1) { legacyImpl.deleteRecordForever(101) }
        verify(exactly = 0) { roomImpl.deleteRecordForever(any()) }
    }

    @Test
    fun `deleteRecordForever - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.deleteRecordForever(101) } just runs

        delegate.deleteRecordForever(101)

        verify(exactly = 1) { roomImpl.deleteRecordForever(101) }
        verify(exactly = 0) { legacyImpl.deleteRecordForever(any()) }
    }

    // ── getRecordsDurations ───────────────────────────────────────────────────

    @Test
    fun `getRecordsDurations - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getRecordsDurations() } returns listOf(1000L, 2000L)

        val result = delegate.getRecordsDurations()

        assertEquals(listOf(1000L, 2000L), result)
        verify(exactly = 1) { legacyImpl.getRecordsDurations() }
        verify(exactly = 0) { roomImpl.getRecordsDurations() }
    }

    @Test
    fun `getRecordsDurations - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getRecordsDurations() } returns listOf(3000L)

        val result = delegate.getRecordsDurations()

        assertEquals(listOf(3000L), result)
        verify(exactly = 1) { roomImpl.getRecordsDurations() }
        verify(exactly = 0) { legacyImpl.getRecordsDurations() }
    }

    // ── addToBookmarks ────────────────────────────────────────────────────────

    @Test
    fun `addToBookmarks - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.addToBookmarks(101) } returns true

        assertTrue(delegate.addToBookmarks(101))
        verify(exactly = 1) { legacyImpl.addToBookmarks(101) }
        verify(exactly = 0) { roomImpl.addToBookmarks(any()) }
    }

    @Test
    fun `addToBookmarks - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.addToBookmarks(101) } returns true

        assertTrue(delegate.addToBookmarks(101))
        verify(exactly = 1) { roomImpl.addToBookmarks(101) }
        verify(exactly = 0) { legacyImpl.addToBookmarks(any()) }
    }

    // ── removeFromBookmarks ───────────────────────────────────────────────────

    @Test
    fun `removeFromBookmarks - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.removeFromBookmarks(101) } returns true

        assertTrue(delegate.removeFromBookmarks(101))
        verify(exactly = 1) { legacyImpl.removeFromBookmarks(101) }
        verify(exactly = 0) { roomImpl.removeFromBookmarks(any()) }
    }

    @Test
    fun `removeFromBookmarks - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.removeFromBookmarks(101) } returns false

        assertFalse(delegate.removeFromBookmarks(101))
        verify(exactly = 1) { roomImpl.removeFromBookmarks(101) }
        verify(exactly = 0) { legacyImpl.removeFromBookmarks(any()) }
    }

    // ── getBookmarks ──────────────────────────────────────────────────────────

    @Test
    fun `getBookmarks - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getBookmarks() } returns listOf(testRecord)

        val result = delegate.getBookmarks()

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.getBookmarks() }
        verify(exactly = 0) { roomImpl.getBookmarks() }
    }

    @Test
    fun `getBookmarks - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getBookmarks() } returns emptyList()

        val result = delegate.getBookmarks()

        assertTrue(result.isEmpty())
        verify(exactly = 1) { roomImpl.getBookmarks() }
        verify(exactly = 0) { legacyImpl.getBookmarks() }
    }

    // ── getTrashRecords ───────────────────────────────────────────────────────

    @Test
    fun `getTrashRecords - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getTrashRecords() } returns listOf(testRecord)

        val result = delegate.getTrashRecords()

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { legacyImpl.getTrashRecords() }
        verify(exactly = 0) { roomImpl.getTrashRecords() }
    }

    @Test
    fun `getTrashRecords - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getTrashRecords() } returns listOf(testRecord)

        val result = delegate.getTrashRecords()

        assertEquals(listOf(testRecord), result)
        verify(exactly = 1) { roomImpl.getTrashRecords() }
        verify(exactly = 0) { legacyImpl.getTrashRecords() }
    }

    // ── getTrashRecordsIds ────────────────────────────────────────────────────

    @Test
    fun `getTrashRecordsIds - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getTrashRecordsIds() } returns listOf(10, 20)

        val result = delegate.getTrashRecordsIds()

        assertEquals(listOf(10, 20), result)
        verify(exactly = 1) { legacyImpl.getTrashRecordsIds() }
        verify(exactly = 0) { roomImpl.getTrashRecordsIds() }
    }

    @Test
    fun `getTrashRecordsIds - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getTrashRecordsIds() } returns listOf(30)

        val result = delegate.getTrashRecordsIds()

        assertEquals(listOf(30), result)
        verify(exactly = 1) { roomImpl.getTrashRecordsIds() }
        verify(exactly = 0) { legacyImpl.getTrashRecordsIds() }
    }

    // ── getTrashRecordsCount ──────────────────────────────────────────────────

    @Test
    fun `getTrashRecordsCount - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.getTrashRecordsCount() } returns 5

        assertEquals(5, delegate.getTrashRecordsCount())
        verify(exactly = 1) { legacyImpl.getTrashRecordsCount() }
        verify(exactly = 0) { roomImpl.getTrashRecordsCount() }
    }

    @Test
    fun `getTrashRecordsCount - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.getTrashRecordsCount() } returns 0

        assertEquals(0, delegate.getTrashRecordsCount())
        verify(exactly = 1) { roomImpl.getTrashRecordsCount() }
        verify(exactly = 0) { legacyImpl.getTrashRecordsCount() }
    }

    // ── restoreFromTrash ──────────────────────────────────────────────────────

    @Test
    fun `restoreFromTrash - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.restoreFromTrash(101) } just runs

        delegate.restoreFromTrash(101)

        verify(exactly = 1) { legacyImpl.restoreFromTrash(101) }
        verify(exactly = 0) { roomImpl.restoreFromTrash(any()) }
    }

    @Test
    fun `restoreFromTrash - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.restoreFromTrash(101) } just runs

        delegate.restoreFromTrash(101)

        verify(exactly = 1) { roomImpl.restoreFromTrash(101) }
        verify(exactly = 0) { legacyImpl.restoreFromTrash(any()) }
    }

    @Test
    fun `restoreFromTrash - propagates FailedToRestoreRecord from legacyImpl`() {
        useLegacy()
        every { legacyImpl.restoreFromTrash(42) } throws FailedToRestoreRecord()

        assertThrows(FailedToRestoreRecord::class.java) {
            delegate.restoreFromTrash(42)
        }
    }

    @Test
    fun `restoreFromTrash - propagates FailedToRestoreRecord from roomImpl`() {
        useRoom()
        every { roomImpl.restoreFromTrash(42) } throws FailedToRestoreRecord()

        assertThrows(FailedToRestoreRecord::class.java) {
            delegate.restoreFromTrash(42)
        }
    }

    // ── removeFromTrash ───────────────────────────────────────────────────────

    @Test
    fun `removeFromTrash - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.removeFromTrash(101) } returns true

        assertTrue(delegate.removeFromTrash(101))
        verify(exactly = 1) { legacyImpl.removeFromTrash(101) }
        verify(exactly = 0) { roomImpl.removeFromTrash(any()) }
    }

    @Test
    fun `removeFromTrash - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.removeFromTrash(101) } returns false

        assertFalse(delegate.removeFromTrash(101))
        verify(exactly = 1) { roomImpl.removeFromTrash(101) }
        verify(exactly = 0) { legacyImpl.removeFromTrash(any()) }
    }

    // ── emptyTrash ────────────────────────────────────────────────────────────

    @Test
    fun `emptyTrash - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.emptyTrash() } returns true

        assertTrue(delegate.emptyTrash())
        verify(exactly = 1) { legacyImpl.emptyTrash() }
        verify(exactly = 0) { roomImpl.emptyTrash() }
    }

    @Test
    fun `emptyTrash - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.emptyTrash() } returns true

        assertTrue(delegate.emptyTrash())
        verify(exactly = 1) { roomImpl.emptyTrash() }
        verify(exactly = 0) { legacyImpl.emptyTrash() }
    }

    // ── removeOutdatedTrashRecords ────────────────────────────────────────────

    @Test
    fun `removeOutdatedTrashRecords - routes to legacyImpl when not migrated`() {
        useLegacy()
        every { legacyImpl.removeOutdatedTrashRecords() } just runs

        delegate.removeOutdatedTrashRecords()

        verify(exactly = 1) { legacyImpl.removeOutdatedTrashRecords() }
        verify(exactly = 0) { roomImpl.removeOutdatedTrashRecords() }
    }

    @Test
    fun `removeOutdatedTrashRecords - routes to roomImpl when migrated`() {
        useRoom()
        every { roomImpl.removeOutdatedTrashRecords() } just runs

        delegate.removeOutdatedTrashRecords()

        verify(exactly = 1) { roomImpl.removeOutdatedTrashRecords() }
        verify(exactly = 0) { legacyImpl.removeOutdatedTrashRecords() }
    }

    // ── setOnRecordsLostListener ──────────────────────────────────────────────

    @Test
    fun `setOnRecordsLostListener - always registers on both implementations`() {
        val listener = mockk<OnRecordsLostListener>()
        every { legacyImpl.setOnRecordsLostListener(listener) } just runs
        every { roomImpl.setOnRecordsLostListener(listener) } just runs

        // Neither migration state matters; both must be registered.
        delegate.setOnRecordsLostListener(listener)

        verify(exactly = 1) { legacyImpl.setOnRecordsLostListener(listener) }
        verify(exactly = 1) { roomImpl.setOnRecordsLostListener(listener) }
    }

    @Test
    fun `setOnRecordsLostListener null - always unregisters on both implementations`() {
        every { legacyImpl.setOnRecordsLostListener(null) } just runs
        every { roomImpl.setOnRecordsLostListener(null) } just runs

        delegate.setOnRecordsLostListener(null)

        verify(exactly = 1) { legacyImpl.setOnRecordsLostListener(null) }
        verify(exactly = 1) { roomImpl.setOnRecordsLostListener(null) }
    }

    // ── Dynamic switching ─────────────────────────────────────────────────────

    @Test
    fun `delegate selection is re-evaluated on every call - switches from legacy to room`() {
        // First call: not migrated → legacy
        every { prefs.isDatabaseMigratedToRoom() } returns false
        every { legacyImpl.getAllRecords() } returns listOf(testRecord)

        val firstResult = delegate.getAllRecords()

        assertEquals(listOf(testRecord), firstResult)
        verify(exactly = 1) { legacyImpl.getAllRecords() }
        verify(exactly = 0) { roomImpl.getAllRecords() }

        // Migration happens; second call → room
        every { prefs.isDatabaseMigratedToRoom() } returns true
        every { roomImpl.getAllRecords() } returns emptyList()

        val secondResult = delegate.getAllRecords()

        assertTrue(secondResult.isEmpty())
        verify(exactly = 1) { legacyImpl.getAllRecords() } // still only 1
        verify(exactly = 1) { roomImpl.getAllRecords() }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    @Test
    fun `getInstance - returns the same instance on repeated calls`() {
        val instance1 = LocalRepositoryDelegate.getInstance(prefs, legacyImpl, roomImpl)
        val instance2 = LocalRepositoryDelegate.getInstance(prefs, legacyImpl, roomImpl)

        assertSame(instance1, instance2)
    }

    @Test
    fun `clearInstance - resets singleton so next getInstance creates a fresh object`() {
        val instance1 = LocalRepositoryDelegate.getInstance(prefs, legacyImpl, roomImpl)
        LocalRepositoryDelegate.clearInstance()

        val legacyImpl2 = mockk<LocalRepositoryImpl>()
        val roomImpl2 = mockk<LocalRepositoryRoomImpl>()
        val instance2 = LocalRepositoryDelegate.getInstance(prefs, legacyImpl2, roomImpl2)

        assertNotNull(instance2)
        assertNotSame(instance1, instance2)
    }
}


