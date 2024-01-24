package com.dimowner.audiorecorder.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetRecordById() {
        val record = RecordEntity(
            1,
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
            IntArray(10),
        )
        recordDao.insertRecord(record)

        val loaded = recordDao.getRecordById(1)
        assertEquals(record, loaded)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateRecord() = runBlocking {
        val record = RecordEntity(
            1,
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
            IntArray(10),
        )
        recordDao.insertRecord(record)

        val updatedRecord = record.copy(name = "Updated Record")
        recordDao.updateRecord(updatedRecord)

        val loaded = recordDao.getRecordById(1)
        assertEquals("Updated Record", loaded?.name)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteRecord() = runBlocking {
        val record = RecordEntity(
            1,
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
            IntArray(10),
        )
        recordDao.insertRecord(record)

        recordDao.deleteRecord(record)

        val loaded = recordDao.getRecordById(1)
        assertNull(loaded)
    }

    @Test
    fun testDeleteRecordById() {
        val record = RecordEntity(
            1,
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
            IntArray(10),
        )
        recordDao.insertRecord(record)

        recordDao.deleteRecordById(1)
        val loaded = recordDao.getRecordById(1)
        assertNull(loaded)
    }

    @Test
    fun testGetRecordsCount() {
        val record1 = RecordEntity(
            1,
            "Record 1",
            1000,
            123456789L,
            123456789L,
            0L,
            "path/to/record1",
            "mp3",
            1024,
            44100,
            2,
            128,
            false,
            false,
            IntArray(10),
        )
        val record2 = RecordEntity(
            2,
            "Record 2",
            2000,
            123456790L,
            123456790L,
            0L,
            "path/to/record2",
            "mp3",
            2048,
            44100,
            2,
            256,
            false,
            false,
            IntArray(10),
        )

        recordDao.insertRecord(record1)
        recordDao.insertRecord(record2)

        val count = recordDao.getRecordsCount()
        assertEquals(2, count)
    }

    @Test
    fun testDeleteAllRecords() {
        val record1 = RecordEntity(
            1,
            "Record 1",
            1000,
            123456789L,
            123456789L,
            0L,
            "path/to/record1",
            "mp3",
            1024,
            44100,
            2,
            128,
            false,
            false,
            IntArray(10),
        )
        val record2 = RecordEntity(
            2,
            "Record 2",
            2000,
            123456790L,
            123456790L,
            0L,
            "path/to/record2",
            "mp3",
            2048,
            44100,
            2,
            256,
            false,
            false,
            IntArray(10),
        )

        recordDao.insertRecord(record1)
        recordDao.insertRecord(record2)

        recordDao.deleteAllRecords()
        val count = recordDao.getRecordsCount()
        assertEquals(0, count)
    }

    @Test
    fun testGetRecordsByPage() {
        val records = Array(100) {
            RecordEntity(
                it + 1,
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
                IntArray(10),
            )
        }

        records.forEach {
            recordDao.insertRecord(it)
        }

        val pageSize = 20
        val offset = 40
        val recordsByPage = recordDao.getRecordsByPage(pageSize, offset)
        assertEquals(pageSize, recordsByPage.size)
        val expected = records.slice(offset until (offset + pageSize))
        assertEquals(expected, recordsByPage)
    }
}
