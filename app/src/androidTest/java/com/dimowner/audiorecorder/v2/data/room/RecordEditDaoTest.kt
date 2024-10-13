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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.v2.data.model.RecordEditOperation
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
class RecordEditDaoTest {

    private lateinit var recordEditDao: RecordEditDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .build()
        recordEditDao = db.recordEditDao()
        //Set demo data
        val recordOperation1 = RecordEditEntity(
            1L,
            101L,
            RecordEditOperation.Rename,
            "renameName1",
            123456789L,
            0,
        )
        val recordOperation2 = RecordEditEntity(
            2L,
            102L,
            RecordEditOperation.MoveToRecycle,
            null,
            223456789L,
            0,
        )
        val recordOperation3 = RecordEditEntity(
            3L,
            103L,
            RecordEditOperation.RestoreFromRecycle,
            null,
            323456789L,
            0,
        )
        val recordOperation4 = RecordEditEntity(
            4L,
            104L,
            RecordEditOperation.DeleteForever,
            null,
            423456789L,
            0,
        )
        recordEditDao.insertRecordsEditOperation(recordOperation1)
        recordEditDao.insertRecordsEditOperation(recordOperation2)
        recordEditDao.insertRecordsEditOperation(recordOperation3)
        recordEditDao.insertRecordsEditOperation(recordOperation4)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun test_auto_generated_primary_key() {
        // Create a sample RecordEditEntity
        val recordOperation = RecordEditEntity(
            recordId = 110L,
            editOperation = RecordEditOperation.Rename,
            renameName = "renameName1",
            created = 123456789L,
            retryCount = 0,
        )

        // Verify that the initial ID is 0
        assertEquals(0, recordOperation.id)

        // Insert the record into your database (use your actual DAO here)
        // For demonstration purposes, assume the DAO method is called insertRecord
        val insertedId = recordEditDao.insertRecordsEditOperation(recordOperation)
        val insertedId2 = recordEditDao.insertRecordsEditOperation(recordOperation)
        val insertedId3 = recordEditDao.insertRecordsEditOperation(recordOperation)

        // Verify that the inserted ID is non-zero
        assertNotEquals(0, insertedId)
        assertNotEquals(0, insertedId2)
        assertNotEquals(0, insertedId3)

        // Fetch the record by ID (use your actual DAO here)
        val fetchedRecord1 = recordEditDao.getRecordsEditOperationById(insertedId)
        val fetchedRecord2 = recordEditDao.getRecordsEditOperationById(insertedId2)
        val fetchedRecord3 = recordEditDao.getRecordsEditOperationById(insertedId3)

        assertNotEquals(fetchedRecord1, fetchedRecord2)
        assertNotEquals(fetchedRecord1, fetchedRecord3)
        assertNotEquals(fetchedRecord2, fetchedRecord3)

        // Verify that the fetched record matches the original record
        assertEquals(recordOperation.copy(id = fetchedRecord1?.id ?: 0), fetchedRecord1)
        assertEquals(recordOperation.copy(id = fetchedRecord2?.id ?: 0), fetchedRecord2)
        assertEquals(recordOperation.copy(id = fetchedRecord3?.id ?: 0), fetchedRecord3)
    }

    @Test
    fun testInsertAndGetRecordEditOperationById() {
        val recordOperation = RecordEditEntity(
            10L,
            110L,
            RecordEditOperation.Rename,
            "renameName1",
            1023456789L,
            0,
        )
        recordEditDao.insertRecordsEditOperation(recordOperation)

        val loaded = recordEditDao.getRecordsEditOperationById(10L)
        assertEquals(recordOperation, loaded)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateRecordEditOperation() = runBlocking {
        val record = recordEditDao.getRecordsEditOperationById(1)

        record?.copy(
            editOperation = RecordEditOperation.MoveToRecycle)?.let {
            recordEditDao.updateRecordsEditOperation(it)
        }

        val updated = recordEditDao.getRecordsEditOperationById(1)
        assertEquals(RecordEditOperation.MoveToRecycle, updated?.editOperation)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteRecordEditOperation() = runBlocking {

        recordEditDao.getRecordsEditOperationById(1)?.let {
            recordEditDao.deleteRecordsEditOperation(it)
        }

        val loaded = recordEditDao.getRecordsEditOperationById(1)
        assertNull(loaded)

        val recordOperation = RecordEditEntity(
            10L,
            110L,
            RecordEditOperation.Rename,
            "renameName1",
            1234567890L,
            0,
        )
        //Delete not existing record silently skipped
        recordEditDao.deleteRecordsEditOperation(recordOperation)
    }

    @Test
    fun testDeleteRecordEditOperationById() {
        val recordBefore = recordEditDao.getRecordsEditOperationById(1)
        assertNotNull(recordBefore)

        recordEditDao.deleteRecordEditOperationById(1)
        val recordAfter = recordEditDao.getRecordsEditOperationById(1)
        assertNull(recordAfter)
    }

    @Test
    fun testDeleteAllRecords() {
        val countBefore = recordEditDao.getAllRecordsEditOperations().size
        assertEquals(4, countBefore)

        recordEditDao.deleteAllRecordsEditOperations()
        val countAfter = recordEditDao.getAllRecordsEditOperations().size
        assertEquals(0, countAfter)
    }

    @Test
    fun test_getAllRecords() {
        val recordsAsc = recordEditDao.getAllRecordsEditOperations()

        assertEquals(4, recordsAsc.size)
        assertEquals(RecordEditOperation.Rename, recordsAsc[3].editOperation)
        assertEquals(RecordEditOperation.MoveToRecycle, recordsAsc[2].editOperation)
        assertEquals(RecordEditOperation.RestoreFromRecycle, recordsAsc[1].editOperation)
        assertEquals(RecordEditOperation.DeleteForever, recordsAsc[0].editOperation)
    }
}
