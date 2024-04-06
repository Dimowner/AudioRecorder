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

        // Insert the record into your database (use your actual DAO here)
        // For demonstration purposes, assume the DAO method is called insertRecord
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
    @Throws(Exception::class)
    fun testUpdateRecord() = runBlocking {
        val record = recordDao.getRecordById(1)

        record?.copy(
            name = "Updated Record")?.let {
            recordDao.updateRecord(it)
        }

        val updated = recordDao.getRecordById(1)
        assertEquals("Updated Record", updated?.name)
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
