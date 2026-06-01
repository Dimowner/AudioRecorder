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

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var recordDao: RecordDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .build()
        recordDao = db.recordDao()
        //Set demo data
        val records = Array(100) {
            RecordEntity(
                it + 1L,
                "Record $it",
                1000L + it,
                123456789L + it,
                123456789L + it,
                0L,
                "path/to/record$it",
                "mp3",
                1024,
                44100,
                2,
                128,
                false,
                false,
                false,
                IntArray(10),
            )
        }

        records.forEach {
            recordDao.insertRecord(it)
        }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun test_auto_generated_primary_key() {
        // Create a sample RecordEntity
        val record = RecordEntity(
            name = "Sample Record",
            duration = 120000L,
            created = System.currentTimeMillis(),
            added = System.currentTimeMillis(),
            removed = System.currentTimeMillis(),
            path = "/path/to/record",
            format = "mp3",
            size = 1024L,
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128,
            isBookmarked = false,
            isWaveformProcessed = true,
            isMovedToRecycle = false,
            amps = intArrayOf(10, 20, 30)
        )

        // Verify that the initial ID is 0
        assertEquals(0, record.id)

        // Insert the record into your database
        val insertedId = recordDao.insertRecord(record)
        val insertedId2 = recordDao.insertRecord(record)
        val insertedId3 = recordDao.insertRecord(record)

        // Verify that the inserted ID is non-zero
        assertNotEquals(0, insertedId)
        assertNotEquals(0, insertedId2)
        assertNotEquals(0, insertedId3)

        // Fetch the record by ID (use your actual DAO here)
        val fetchedRecord1 = recordDao.getRecordById(insertedId)
        val fetchedRecord2 = recordDao.getRecordById(insertedId2)
        val fetchedRecord3 = recordDao.getRecordById(insertedId3)

        assertNotEquals(fetchedRecord1, fetchedRecord2)
        assertNotEquals(fetchedRecord1, fetchedRecord3)
        assertNotEquals(fetchedRecord2, fetchedRecord3)

        // Verify that the fetched record matches the original record
        assertEquals(record.copy(id = fetchedRecord1?.id ?: 0), fetchedRecord1)
        assertEquals(record.copy(id = fetchedRecord2?.id ?: 0), fetchedRecord2)
        assertEquals(record.copy(id = fetchedRecord3?.id ?: 0), fetchedRecord3)
    }

    @Test
    fun testInsertAndGetRecordById() {
        val record = RecordEntity(
            1001L,
            "Test Record",
            1000,
            123456789L,
            123456789L,
            0L,
            "path/to/record",
            "mp3",
            1024,
            44100,
            2,
            128,
            false,
            false,
            false,
            IntArray(10),
        )
        recordDao.insertRecord(record)

        val loaded = recordDao.getRecordById(1001L)
        assertEquals(record, loaded)
    }

    @Test
    fun testInsertRecordsAndGetRecords() {
        val record1 = RecordEntity(
            1001L,
            "Test Record",
            1000,
            123456789L,
            123456789L,
            0L,
            "path/to/record",
            "mp3",
            1024,
            44100,
            2,
            128,
            false,
            false,
            false,
            IntArray(10),
        )
        val record2 = RecordEntity(
            1002L,
            "Test Record2",
            2000,
            12345678910L,
            12345678911L,
            0L,
            "path/to/record2",
            "mp3",
            2048,
            44100,
            1,
            192,
            false,
            false,
            false,
            IntArray(20),
        )
        recordDao.insertRecords(listOf(record1, record2))

        val loaded = recordDao.getRecordsByIds(listOf(1001L, 1002L))
        assertEquals(2, loaded.size)
        assertEquals(record1, loaded[0])
        assertEquals(record2, loaded[1])
    }

    @Test
    fun testGetRecordsByIds() {
        //Test valid records request
        val records = recordDao.getRecordsByIds(listOf(2, 45, 91, 28))

        assertEquals(4, records.size)
        assertEquals("Record 1", records[0].name)
        assertEquals("Record 27", records[1].name)
        assertEquals("Record 44", records[2].name)
        assertEquals("Record 90", records[3].name)
        assertEquals(2, records[0].id)
        assertEquals(28, records[1].id)
        assertEquals(45, records[2].id)
        assertEquals(91, records[3].id)

        //Test invalid records request (all invalid ids)
        val invalidRecords = recordDao.getRecordsByIds(listOf(-1, -1000, 10101, 200))
        assertEquals(0, invalidRecords.size)

        //Test mixed records request (3 valid ids and 2 invalid)
        val mixedRecords = recordDao.getRecordsByIds(listOf(2, -1, 28, 200, 45))
        assertEquals(3, mixedRecords.size)
        assertEquals("Record 1", records[0].name)
        assertEquals("Record 27", records[1].name)
        assertEquals("Record 44", records[2].name)
        assertEquals(2, records[0].id)
        assertEquals(28, records[1].id)
        assertEquals(45, records[2].id)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateRecord() = runBlocking {
        val record = recordDao.getRecordById(1)

        record?.copy(name = "Updated Record")?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }

        val updated = recordDao.getRecordById(1)
        assertEquals("Updated Record", updated?.name)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateRecords() = runBlocking {
        val records = recordDao.getRecordsByIds(listOf(1, 2))

        val toUpdate = records.mapIndexed { index, record -> record.copy(name = "Updated record $index") }
        val updatedCount = recordDao.updateRecords(toUpdate)
        assertEquals(2, updatedCount)

        val updated1 = recordDao.getRecordById(1)
        assertEquals("Updated record 0", updated1?.name)
        val updated2 = recordDao.getRecordById(2)
        assertEquals("Updated record 1", updated2?.name)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteRecord() = runBlocking {

        recordDao.getRecordById(1)?.let {
            recordDao.deleteRecord(it)
        }

        val loaded = recordDao.getRecordById(1)
        assertNull(loaded)

        val record = RecordEntity(
            1001L,
            "Test Record",
            1000,
            123456789L,
            123456789L,
            0L,
            "path/to/record",
            "mp3",
            1024,
            44100,
            2,
            128,
            false,
            false,
            false,
            IntArray(10),
        )
        val result = recordDao.getRecordById(1001L)
        assertNull(result)
        //Delete not existing record silently skipped
        recordDao.deleteRecord(record)
    }

    @Test
    fun testDeleteRecordById() {
        val recordBefore = recordDao.getRecordById(1)
        assertNotNull(recordBefore)

        recordDao.deleteRecordById(1)
        val recordAfter = recordDao.getRecordById(1)
        assertNull(recordAfter)

        val result = recordDao.getRecordById(1001L)
        assertNull(result)
        //Delete not existing record silently skipped
        recordDao.deleteRecordById(1001L)
    }

    @Test
    fun testGetRecordsCount() {
        val count = recordDao.getRecordsCount()
        assertEquals(100, count)
    }

    @Test
    fun testGetRecordTotalDuration() {
        val duration = recordDao.getRecordTotalDuration()
        assertEquals(1000L*100+4950, duration)
        //4950 is sum of number sequence from 1 to 100 (1, 2, 3, 4, 5, 6...)
    }

    @Test
    fun testDeleteAllRecords() {
        val countBefore = recordDao.getRecordsCount()
        assertEquals(100, countBefore)

        recordDao.deleteAllRecords()
        val countAfter = recordDao.getRecordsCount()
        assertEquals(0, countAfter)
    }

    @Test
    fun testGetRecordsByPage() {
        val pageSize = 20
        val offset = 40
        val records = recordDao.getAllRecords()
        val recordsByPage = recordDao.getRecordsByPage(pageSize, offset)
        assertEquals(pageSize, recordsByPage.size)
        val expected = records.slice(offset until (offset + pageSize))
        assertEquals(expected, recordsByPage)
    }

    @Test
    fun test_getAllRecords() {
        val recordsAsc = recordDao.getAllRecords()

        assertEquals(100, recordsAsc.size)
        assertEquals("Record 99", recordsAsc[0].name)
        assertEquals("Record 93", recordsAsc[6].name)
        assertEquals("Record 50", recordsAsc[49].name)
        assertEquals("Record 6", recordsAsc[93].name)
        assertEquals("Record 0", recordsAsc[99].name)
    }

    @Test
    fun test_getMovedToRecycleRecords() {
        val records = recordDao.getMovedToRecycleRecords()
        assertEquals(0, records.size)

        val record1 = recordDao.getRecordById(1)
        val record50 = recordDao.getRecordById(50)
        val record93 = recordDao.getRecordById(93)

        record1?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        record50?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        record93?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        val records2 = recordDao.getMovedToRecycleRecords()
        assertEquals(3, records2.size)
        assertEquals("Record 0", records2[0].name)
        assertEquals("Record 49", records2[1].name)
        assertEquals("Record 92", records2[2].name)
        assertEquals(1, records2[0].id)
        assertEquals(50, records2[1].id)
        assertEquals(93, records2[2].id)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_firstPage() {
        // Move 25 records to recycle with distinct removed timestamps so ordering is deterministic
        for (i in 1..25) {
            val record = recordDao.getRecordById(i.toLong())
            record?.copy(isMovedToRecycle = true, removed = i.toLong() * 1000L)?.let {
                recordDao.updateRecord(it)
            }
        }

        // Page 1 — should return items with highest removed timestamp (ids 25..16)
        val page1 = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 0)
        assertEquals(10, page1.size)
        // Sorted by removed DESC: id 25 first, id 16 last
        assertEquals(25L, page1[0].id)
        assertEquals(16L, page1[9].id)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_secondPage() {
        for (i in 1..25) {
            val record = recordDao.getRecordById(i.toLong())
            record?.copy(isMovedToRecycle = true, removed = i.toLong() * 1000L)?.let {
                recordDao.updateRecord(it)
            }
        }

        // Page 2 — offset 10 → items 15..6
        val page2 = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 10)
        assertEquals(10, page2.size)
        assertEquals(15L, page2[0].id)
        assertEquals(6L, page2[9].id)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_partialLastPage() {
        for (i in 1..25) {
            val record = recordDao.getRecordById(i.toLong())
            record?.copy(isMovedToRecycle = true, removed = i.toLong() * 1000L)?.let {
                recordDao.updateRecord(it)
            }
        }

        // Page 3 — offset 20 → only 5 items left (ids 5..1)
        val page3 = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 20)
        assertEquals(5, page3.size)
        assertEquals(5L, page3[0].id)
        assertEquals(1L, page3[4].id)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_emptyWhenNoneInRecycle() {
        val page = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 0)
        assertEquals(0, page.size)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_offsetBeyondTotalReturnsEmpty() {
        for (i in 1..5) {
            val record = recordDao.getRecordById(i.toLong())
            record?.copy(isMovedToRecycle = true, removed = i.toLong() * 1000L)?.let {
                recordDao.updateRecord(it)
            }
        }

        val page = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 100)
        assertEquals(0, page.size)
    }

    @Test
    fun test_getMovedToRecycleRecordsByPage_doesNotIncludeNonRecycleRecords() {
        // Only move record 1 to recycle
        val record = recordDao.getRecordById(1L)
        record?.copy(isMovedToRecycle = true, removed = 5000L)?.let {
            recordDao.updateRecord(it)
        }

        val page = recordDao.getMovedToRecycleRecordsByPage(pageSize = 10, offset = 0)
        assertEquals(1, page.size)
        assertEquals(1L, page[0].id)
    }

    @Test
    fun test_getMovedToRecycleRecordsCount() {
        val count = recordDao.getMovedToRecycleRecordsCount()
        assertEquals(0, count)

        val record1 = recordDao.getRecordById(1)
        val record50 = recordDao.getRecordById(50)
        val record93 = recordDao.getRecordById(93)

        record1?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        val count2 = recordDao.getMovedToRecycleRecordsCount()
        assertEquals(1, count2)
        record50?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        val count3 = recordDao.getMovedToRecycleRecordsCount()
        assertEquals(2, count3)
        record93?.copy(isMovedToRecycle = true)?.let {
            val updated = recordDao.updateRecord(it)
            assertEquals(1, updated)
        }
        val count4 = recordDao.getMovedToRecycleRecordsCount()
        assertEquals(3, count4)
    }

    @Test
    fun test_getRecordsRewQuery() {
        val query1 = "SELECT * FROM records"

        val records1 = recordDao.getRecordsRewQuery(SimpleSQLiteQuery(query1))
        assertEquals(100, records1.size)
        assertEquals(1, records1[0].id)
        assertEquals(100, records1[99].id)

        val sortField = "added"
        val page = 2
        val pageSize = 5

        val query2 = "SELECT * FROM records" +
                " WHERE isBookmarked = 0" +
                " ORDER BY $sortField DESC" +
                " LIMIT $pageSize OFFSET ${(page - 1) * pageSize}"

        val records2 = recordDao.getRecordsRewQuery(SimpleSQLiteQuery(query2))

        assertEquals(5, records2.size)
        assertEquals("Record 94", records2[0].name)
        assertEquals("Record 93", records2[1].name)
        assertEquals("Record 92", records2[2].name)
        assertEquals("Record 91", records2[3].name)
        assertEquals("Record 90", records2[4].name)

        val bookmarkedRecord = recordDao.getRecordById(10)?.copy(id = 0, isBookmarked = true)
        if (bookmarkedRecord != null) {
            recordDao.insertRecord(bookmarkedRecord)
        }
        val query3 = "SELECT * FROM records" +
                " WHERE isBookmarked = 1"
        val records3 = recordDao.getRecordsRewQuery(SimpleSQLiteQuery(query3))

        assertEquals(1, records3.size)
        assertEquals(bookmarkedRecord?.copy(id = 101L), records3[0])
    }
}
