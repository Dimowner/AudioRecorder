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

import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer
import com.dimowner.audiorecorder.v2.data.model.RecordEditOperation
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditDao
import com.dimowner.audiorecorder.v2.data.room.RecordEditEntity
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.File

class RecordsDataSourceImplTest {

    @MockK
    lateinit var prefs: PrefsV2

    @MockK
    lateinit var recordDao: RecordDao

    @MockK
    lateinit var recordEditDao: RecordEditDao

    @MockK
    lateinit var fileDataSource: FileDataSource

    @MockK
    lateinit var brokenRecordRestorer: BrokenRecordRestorer

    private lateinit var recordsDataSourceImpl: RecordsDataSourceImpl

    private val testRecordEntity =  RecordEntity(
        id = 101,
        name = "name",
        duration = 100,
        created = 100500,
        added =  500100,
        removed = -1,
        path = "path",
        format = "format",
        size = 400,
        sampleRate = 32000,
        channelCount = 2,
        bitrate = 128000,
        isBookmarked = true,
        isWaveformProcessed = true,
        isMovedToRecycle = false,
        amps = intArrayOf(1, 2, 3, 4)
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        recordsDataSourceImpl = RecordsDataSourceImpl(
            prefs,
            recordDao,
            recordEditDao,
            fileDataSource,
            brokenRecordRestorer
        )
    }

    @Test
    fun test_getRecord() = runBlocking {
        val id = 101L

        every {  recordDao.getRecordById(id) } returns testRecordEntity


        val resultNull = recordsDataSourceImpl.getRecord(-1)
        assertNull(resultNull)

        val result = recordsDataSourceImpl.getRecord(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals("name", result?.name)
        assertEquals(100L, result?.durationMills)
        assertEquals(100500L, result?.created)
        assertEquals(500100L, result?.added)
        assertEquals(-1L, result?.removed)
        assertEquals("path", result?.path)
        assertEquals("format", result?.format)
        assertEquals(400L, result?.size)
        assertEquals(32000, result?.sampleRate)
        assertEquals(2, result?.channelCount)
        assertEquals(128000, result?.bitrate)
        assertEquals(true, result?.isBookmarked)
        assertEquals(true, result?.isWaveformProcessed)
        assertEquals(false, result?.isMovedToRecycle)
        assertEquals(intArrayOf(1, 2, 3, 4).size, result?.amps?.size)
    }

    @Test
    fun test_getRecords() = runBlocking {
        val id1 = 101L
        val id2 = 201L
        val recordName1 = "record 1"
        val recordName2 = "record 2"

        val testRecord1 = testRecordEntity.copy(id = id1, name = recordName1)
        val testRecord2 = testRecordEntity.copy(id = id2, name = recordName2)

        every { recordDao.getRecordsByIds(listOf(id1, id2)) } returns listOf(testRecord1, testRecord2)

        val emptyList = recordsDataSourceImpl.getRecords(listOf(-1))
        assertTrue(emptyList.isEmpty())

        val result = recordsDataSourceImpl.getRecords(listOf(id1, id2))

        assertEquals(2, result.size)
        assertEquals(id1, result[0].id)
        assertEquals(id2, result[1].id)
        assertEquals(recordName1, result[0].name)
        assertEquals(recordName2, result[1].name)
    }

    @Test
    fun test_updateRecord() = runBlocking {
        val id = 101L
        val recordName = "record 1"
        val record = testRecordEntity.copy(id = id, name = recordName).toRecord()

        every {  recordDao.updateRecord(record.toRecordEntity()) } returns 1

        val isUpdated = recordsDataSourceImpl.updateRecord(record)
        assertTrue(isUpdated)
    }

    @Test
    fun test_updateRecords() = runBlocking {
        val id1 = 101L
        val id2 = 201L
        val recordName1 = "record 1"
        val recordName2 = "record 2"

        val testRecord1 = testRecordEntity.copy(id = id1, name = recordName1).toRecord()
        val testRecord2 = testRecordEntity.copy(id = id2, name = recordName2).toRecord()

        every {  recordDao.updateRecords(
            listOf(testRecord1.toRecordEntity(), testRecord2.toRecordEntity()))
        } returns 2

        val updatedCount = recordsDataSourceImpl.updateRecords(listOf(testRecord1, testRecord2))
        assertEquals(2, updatedCount)
    }

    @Test
    fun test_getActiveRecord() = runBlocking {
        val id = 101L
        every { recordDao.getRecordById(id) } returns testRecordEntity
        every { prefs.activeRecordId } returns -1

        val resultNull = recordsDataSourceImpl.getActiveRecord()
        assertNull(resultNull)

        every { prefs.activeRecordId } returns id

        val result = recordsDataSourceImpl.getActiveRecord()

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals("name", result?.name)
        assertEquals(100L, result?.durationMills)
        assertEquals(100500L, result?.created)
        assertEquals(500100L, result?.added)
        assertEquals(-1L, result?.removed)
        assertEquals("path", result?.path)
        assertEquals("format", result?.format)
        assertEquals(400L, result?.size)
        assertEquals(32000, result?.sampleRate)
        assertEquals(2, result?.channelCount)
        assertEquals(128000, result?.bitrate)
        assertEquals(true, result?.isBookmarked)
        assertEquals(true, result?.isWaveformProcessed)
        assertEquals(false, result?.isMovedToRecycle)
        assertEquals(intArrayOf(1, 2, 3, 4).size, result?.amps?.size)
    }

    @Test
    fun test_renameRecord_success() = runBlocking {
        val record = testRecordEntity.toRecord()
        val newName = "record_new_name"
        val transactionId = 303L
        val renamedPath = "path/record_new_name"
        val renamedFile = mockk<File>()

        val recordEditOperation = slot<RecordEditEntity>()
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { fileDataSource.renameFile(record.path, newName) } returns renamedFile
        every { renamedFile.absolutePath } returns renamedPath
        every { recordDao.updateRecord(any()) } returns 1

        val result = recordsDataSourceImpl.renameRecord(record, newName)

        assertTrue(result)
        assertEquals(record.id, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.Rename, recordEditOperation.captured.editOperation)
        assertEquals(newName, recordEditOperation.captured.renameName)

        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            fileDataSource.renameFile(record.path, newName)
            //Perform Step 2
            recordDao.updateRecord(
                record.copy(
                    name = newName,
                    path = renamedPath
                ).toRecordEntity()
            )
            //Finish transaction if success
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
    }

    @Test
    fun test_renameRecord_step_1_failed() = runBlocking {
        val record = testRecordEntity.toRecord()
        val newName = "record_new_name"
        val transactionId = 303L

        val recordEditOperation = slot<RecordEditEntity>()
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { fileDataSource.renameFile(record.path, newName) } throws Exception("Failed to rename")

        val result = recordsDataSourceImpl.renameRecord(record, newName)

        assertFalse(result)
        assertEquals(record.id, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.Rename, recordEditOperation.captured.editOperation)
        assertEquals(newName, recordEditOperation.captured.renameName)

        //Verify Step 2 not performed
        verify(exactly = 0) {
            recordDao.updateRecord(any())
        }
        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            fileDataSource.renameFile(record.path, newName)
            //Finish transaction if success
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
    }

    @Test
    fun test_renameRecord_step_2_failed_and_rollback_success() = runBlocking {
        val record = testRecordEntity.toRecord()
        val newName = "record_new_name"
        val transactionId = 303L
        val renamedPath = "path/record_new_name"
        val renamedFile = mockk<File>()
        val rolledBackFile = mockk<File>()

        val recordEditOperation = slot<RecordEditEntity>()
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { fileDataSource.renameFile(record.path, newName) } returns renamedFile
        every { renamedFile.absolutePath } returns renamedPath
        every { rolledBackFile.absolutePath } returns record.path
        every { recordDao.updateRecord(any()) } throws Exception("Failed to update record")

        every { fileDataSource.renameFile(renamedPath, record.name) } returns rolledBackFile

        val result = recordsDataSourceImpl.renameRecord(record, newName)

        assertFalse(result)
        assertEquals(record.id, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.Rename, recordEditOperation.captured.editOperation)
        assertEquals(newName, recordEditOperation.captured.renameName)

        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            fileDataSource.renameFile(record.path, newName)
            //Perform Step 2
            recordDao.updateRecord(
                record.copy(
                    name = newName,
                    path = renamedPath
                ).toRecordEntity()
            )
            //Rollback step 1
            fileDataSource.renameFile(renamedPath, record.name)
            //Finish transaction if success
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
    }

    @Test
    fun test_renameRecord_step_2_failed_and_rollback_failed() = runBlocking {
        val record = testRecordEntity.toRecord()
        val newName = "record_new_name"

        val transactionId = 303L
        val renamedPath = "path/record_new_name"
        val renamedFile = mockk<File>()
        val rolledBackFile = mockk<File>()

        val recordEditOperation = slot<RecordEditEntity>()
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { fileDataSource.renameFile(record.path, newName) } returns renamedFile
        every { renamedFile.absolutePath } returns renamedPath
        every { rolledBackFile.absolutePath } returns record.path
        every { recordDao.updateRecord(any()) } throws Exception("Failed to update record")

        every { fileDataSource.renameFile(renamedPath, record.name) } returns null

        val result = recordsDataSourceImpl.renameRecord(record, newName)

        assertFalse(result)
        assertEquals(record.id, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.Rename, recordEditOperation.captured.editOperation)
        assertEquals(newName, recordEditOperation.captured.renameName)

        //Verify transaction not ended
        verify(exactly = 0) {
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            fileDataSource.renameFile(record.path, newName)
            //Perform Step 2
            recordDao.updateRecord(
                record.copy(
                    name = newName,
                    path = renamedPath
                ).toRecordEntity()
            )
            //Rollback step 1
            fileDataSource.renameFile(renamedPath, record.name)
        }
    }

    @Test
    fun test_deleteRecordAndFileForever_success() = runBlocking {
        val recordId = 101L
        val transactionId = 303L

        val recordEditOperation = slot<RecordEditEntity>()

        every { recordDao.getRecordById(recordId) } returns testRecordEntity
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { recordDao.deleteRecordById(recordId) } returns Unit
        every { fileDataSource.deleteRecordFile(testRecordEntity.path) } returns true

        val result = recordsDataSourceImpl.deleteRecordAndFileForever(recordId)

        assertTrue(result)
        assertEquals(recordId, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.DeleteForever, recordEditOperation.captured.editOperation)
        assertNull(recordEditOperation.captured.renameName)

        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            recordDao.deleteRecordById(recordId)
            //Perform Step 2
            fileDataSource.deleteRecordFile(testRecordEntity.path)
            //Finish transaction if success
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
    }

    @Test
    fun test_deleteRecordAndFileForever_step_1_failed() = runBlocking {
        val recordId = 101L
        val transactionId = 303L

        val recordEditOperation = slot<RecordEditEntity>()

        every { recordDao.getRecordById(recordId) } returns testRecordEntity
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { recordDao.deleteRecordById(recordId) } throws Exception("Failed to delete")

        val result = recordsDataSourceImpl.deleteRecordAndFileForever(recordId)

        assertFalse(result)
        assertEquals(recordId, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.DeleteForever, recordEditOperation.captured.editOperation)
        assertNull(recordEditOperation.captured.renameName)

        //Verify step 2 never performed
        verify(exactly = 0) {
            fileDataSource.deleteRecordFile(testRecordEntity.path)
        }
        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            recordDao.deleteRecordById(recordId)
            //Finish transaction if success
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
    }

    @Test
    fun test_deleteRecordAndFileForever_step_2_failed() = runBlocking {
        val recordId = 101L
        val transactionId = 303L

        val recordEditOperation = slot<RecordEditEntity>()

        every { recordDao.getRecordById(recordId) } returns testRecordEntity
        every { recordEditDao.insertRecordsEditOperation(capture(recordEditOperation)) } returns transactionId
        every { recordDao.deleteRecordById(recordId) } returns Unit
        every { fileDataSource.deleteRecordFile(testRecordEntity.path) } returns false

        val result = recordsDataSourceImpl.deleteRecordAndFileForever(recordId)

        assertFalse(result)
        assertEquals(recordId, recordEditOperation.captured.recordId)
        assertEquals(RecordEditOperation.DeleteForever, recordEditOperation.captured.editOperation)
        assertNull(recordEditOperation.captured.renameName)

        //Verify finish transaction never called
        verify(exactly = 0) {
            recordEditDao.deleteRecordEditOperationById(transactionId)
        }
        verifyOrder {
            //Start transaction
            recordEditDao.insertRecordsEditOperation(any())
            //Perform Step 1
            recordDao.deleteRecordById(recordId)
            //Perform Step 2
            fileDataSource.deleteRecordFile(testRecordEntity.path)
        }
    }

    @Test
    fun test_moveRecordToRecycleById_success() = runBlocking {
        // 1. Setup
        val recordId = 101L
        val initialRecord = testRecordEntity.copy(id = recordId, isMovedToRecycle = false, removed = -1)

        // We use a slot to capture what is actually passed to the update function
        val updatedRecordSlot = slot<RecordEntity>()

        every { recordDao.getRecordById(recordId) } returns initialRecord
        every { recordDao.updateRecord(capture(updatedRecordSlot)) } returns 1

        // 2. Execution
        val result = recordsDataSourceImpl.moveRecordToRecycle(recordId)

        // 3. Verification
        assertTrue(result)

        // Check the captured argument to ensure logic inside the function worked
        val capturedRecord = updatedRecordSlot.captured
        assertEquals(recordId, capturedRecord.id)
        assertTrue(capturedRecord.isMovedToRecycle)
        assertTrue(capturedRecord.removed > 0) // Verify timestamp was set

        verify(exactly = 1) { recordDao.updateRecord(any()) }
    }

    @Test
    fun test_moveRecordToRecycle_recordNotFound_returnsFalse() = runBlocking {
        // 1. Setup - Simulate record returning null
        val recordId = 999L
        every { recordDao.getRecordById(recordId) } returns null

        // 2. Execution
        val result = recordsDataSourceImpl.moveRecordToRecycle(recordId)

        // 3. Verification
        assertFalse(result)

        // Ensure we never attempted an update
        verify(exactly = 0) { recordDao.updateRecord(any()) }
    }

    @Test
    fun test_moveRecordsToRecycle_success() = runBlocking {
        // 1. Setup
        val ids = listOf(101L, 102L)
        val record1 = testRecordEntity.copy(id = 101L, isMovedToRecycle = false, removed = -1)
        val record2 = testRecordEntity.copy(id = 102L, isMovedToRecycle = false, removed = -1)

        val capturedListSlot = slot<List<RecordEntity>>()

        every { recordDao.getRecordsByIds(ids) } returns listOf(record1, record2)
        every { recordDao.updateRecords(capture(capturedListSlot)) } returns 2

        // 2. Execution
        val resultCount = recordsDataSourceImpl.moveRecordsToRecycle(ids)

        // 3. Verification
        assertEquals(2, resultCount)

        // Verify all items in the list were updated correctly
        val capturedList = capturedListSlot.captured
        assertEquals(2, capturedList.size)

        capturedList.forEach { record ->
            assertTrue(record.isMovedToRecycle)
            assertTrue(record.removed > 0)
        }
    }

    // ==================== restoreRecordFromRecycle ====================

    @Test
    fun test_restoreRecordFromRecycle_success() = runBlocking {
        val recordId = 101L
        val initialRecord = testRecordEntity.copy(
            id = recordId,
            isMovedToRecycle = true,
            removed = 999L
        )

        val updatedRecordSlot = slot<RecordEntity>()

        every { recordDao.getRecordById(recordId) } returns initialRecord
        every { recordDao.updateRecord(capture(updatedRecordSlot)) } returns 1

        val result = recordsDataSourceImpl.restoreRecordFromRecycle(recordId)

        assertTrue(result)

        val captured = updatedRecordSlot.captured
        assertEquals(recordId, captured.id)
        assertFalse(captured.isMovedToRecycle)
        assertEquals(-1L, captured.removed)

        verify(exactly = 1) { recordDao.getRecordById(recordId) }
        verify(exactly = 1) { recordDao.updateRecord(any()) }
    }

    @Test
    fun test_restoreRecordFromRecycle_recordNotFound() = runBlocking {
        val recordId = 999L

        every { recordDao.getRecordById(recordId) } returns null

        val result = recordsDataSourceImpl.restoreRecordFromRecycle(recordId)

        assertFalse(result)
        verify(exactly = 0) { recordDao.updateRecord(any()) }
    }

    @Test
    fun test_restoreRecordFromRecycle_updateFails_returnsFalse() = runBlocking {
        val recordId = 101L
        val initialRecord = testRecordEntity.copy(
            id = recordId,
            isMovedToRecycle = true,
            removed = 999L
        )

        every { recordDao.getRecordById(recordId) } returns initialRecord
        every { recordDao.updateRecord(any()) } returns 0

        val result = recordsDataSourceImpl.restoreRecordFromRecycle(recordId)

        assertFalse(result)
        verify(exactly = 1) { recordDao.updateRecord(any()) }
    }

    @Test
    fun test_restoreRecordFromRecycle_preservesOtherFields() = runBlocking {
        val recordId = 101L
        val initialRecord = testRecordEntity.copy(
            id = recordId,
            name = "my_recording",
            path = "some/path/to/file",
            isMovedToRecycle = true,
            removed = 123456L,
        )

        val updatedRecordSlot = slot<RecordEntity>()

        every { recordDao.getRecordById(recordId) } returns initialRecord
        every { recordDao.updateRecord(capture(updatedRecordSlot)) } returns 1

        val result = recordsDataSourceImpl.restoreRecordFromRecycle(recordId)

        assertTrue(result)

        val captured = updatedRecordSlot.captured
        // Restored fields
        assertFalse(captured.isMovedToRecycle)
        assertEquals(-1L, captured.removed)
        // Preserved fields
        assertEquals(recordId, captured.id)
        assertEquals("my_recording", captured.name)
        assertEquals("some/path/to/file", captured.path)
        assertEquals(initialRecord.duration, captured.duration)
        assertEquals(initialRecord.created, captured.created)
        assertEquals(initialRecord.added, captured.added)
        assertEquals(initialRecord.format, captured.format)
        assertEquals(initialRecord.size, captured.size)
        assertEquals(initialRecord.sampleRate, captured.sampleRate)
        assertEquals(initialRecord.channelCount, captured.channelCount)
        assertEquals(initialRecord.bitrate, captured.bitrate)
        assertTrue(captured.isBookmarked)
        assertEquals(initialRecord.isWaveformProcessed, captured.isWaveformProcessed)
    }

    // ==================== getAllRecords ====================

    @Test
    fun test_getAllRecords_returnsMappedRecords() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, name = "record_1")
        val entity2 = testRecordEntity.copy(id = 2, name = "record_2")

        every { recordDao.getAllRecords() } returns listOf(entity1, entity2)

        val result = recordsDataSourceImpl.getAllRecords()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("record_1", result[0].name)
        assertEquals(2L, result[1].id)
        assertEquals("record_2", result[1].name)
    }

    @Test
    fun test_getAllRecords_emptyList() = runBlocking {
        every { recordDao.getAllRecords() } returns emptyList()

        val result = recordsDataSourceImpl.getAllRecords()

        assertTrue(result.isEmpty())
    }

    // ==================== getMovedToRecycleRecords ====================

    @Test
    fun test_getMovedToRecycleRecords_returnsMappedRecords() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, name = "recycled_1", isMovedToRecycle = true)
        val entity2 = testRecordEntity.copy(id = 2, name = "recycled_2", isMovedToRecycle = true)

        every { recordDao.getMovedToRecycleRecords() } returns listOf(entity1, entity2)

        val result = recordsDataSourceImpl.getMovedToRecycleRecords()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("recycled_1", result[0].name)
        assertTrue(result[0].isMovedToRecycle)
        assertEquals(2L, result[1].id)
        assertEquals("recycled_2", result[1].name)
        assertTrue(result[1].isMovedToRecycle)
    }

    @Test
    fun test_getMovedToRecycleRecords_emptyList() = runBlocking {
        every { recordDao.getMovedToRecycleRecords() } returns emptyList()

        val result = recordsDataSourceImpl.getMovedToRecycleRecords()

        assertTrue(result.isEmpty())
    }

    // ==================== getMovedToRecycleRecordsCount ====================

    @Test
    fun test_getMovedToRecycleRecordsCount() = runBlocking {
        every { recordDao.getMovedToRecycleRecordsCount() } returns 5

        val result = recordsDataSourceImpl.getMovedToRecycleRecordsCount()

        assertEquals(5, result)
    }

    @Test
    fun test_getMovedToRecycleRecordsCount_zero() = runBlocking {
        every { recordDao.getMovedToRecycleRecordsCount() } returns 0

        val result = recordsDataSourceImpl.getMovedToRecycleRecordsCount()

        assertEquals(0, result)
    }

    // ==================== getRecords (paged) ====================

    @Test
    fun test_getRecords_paged_defaultSortOrder() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, name = "record_1")
        val entity2 = testRecordEntity.copy(id = 2, name = "record_2")

        every { recordDao.getRecordsRewQuery(any()) } returns listOf(entity1, entity2)

        val result = recordsDataSourceImpl.getRecords(page = 1, pageSize = 10)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(2L, result[1].id)

        verify(exactly = 1) { recordDao.getRecordsRewQuery(any()) }
    }

    @Test
    fun test_getRecords_paged_withBookmarked() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, name = "bookmarked_1", isBookmarked = true)

        every { recordDao.getRecordsRewQuery(any()) } returns listOf(entity1)

        val result = recordsDataSourceImpl.getRecords(
            page = 1,
            pageSize = 10,
            isBookmarked = true
        )

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("bookmarked_1", result[0].name)
    }

    @Test
    fun test_getRecords_paged_secondPage() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 11, name = "record_11")

        every { recordDao.getRecordsRewQuery(any()) } returns listOf(entity1)

        val result = recordsDataSourceImpl.getRecords(page = 2, pageSize = 10)

        assertEquals(1, result.size)
        assertEquals(11L, result[0].id)
    }

    @Test
    fun test_getRecords_paged_emptyResult() = runBlocking {
        every { recordDao.getRecordsRewQuery(any()) } returns emptyList()

        val result = recordsDataSourceImpl.getRecords(page = 1, pageSize = 10)

        assertTrue(result.isEmpty())
    }

    @Test
    fun test_getRecords_paged_nameSortOrder() = runBlocking {
        every { recordDao.getRecordsRewQuery(any()) } returns listOf(testRecordEntity)

        val result = recordsDataSourceImpl.getRecords(
            page = 1,
            pageSize = 20,
            sortOrder = SortOrder.NameAsc
        )

        assertEquals(1, result.size)
    }

    // ==================== insertRecord ====================

    @Test
    fun test_insertRecord() = runBlocking {
        val record = testRecordEntity.toRecord()
        val generatedId = 42L

        every { recordDao.insertRecord(record.toRecordEntity()) } returns generatedId

        val result = recordsDataSourceImpl.insertRecord(record)

        assertEquals(generatedId, result)
        verify(exactly = 1) { recordDao.insertRecord(any()) }
    }

    // ==================== getRecordsCount ====================

    @Test
    fun test_getRecordsCount() = runBlocking {
        every { recordDao.getRecordsCount() } returns 15

        val result = recordsDataSourceImpl.getRecordsCount()

        assertEquals(15, result)
    }

    @Test
    fun test_getRecordsCount_zero() = runBlocking {
        every { recordDao.getRecordsCount() } returns 0

        val result = recordsDataSourceImpl.getRecordsCount()

        assertEquals(0, result)
    }

    // ==================== getRecordTotalDuration ====================
    @Test
    fun test_getRecordTotalDuration() = runBlocking {
        every { recordDao.getRecordTotalDuration() } returns 360000L

        val result = recordsDataSourceImpl.getRecordTotalDuration()

        assertEquals(360000L, result)
    }

    @Test
    fun test_getRecordTotalDuration_zero() = runBlocking {
        every { recordDao.getRecordTotalDuration() } returns 0L

        val result = recordsDataSourceImpl.getRecordTotalDuration()

        assertEquals(0L, result)
    }

    // ==================== clearRecycle ====================

    @Test
    fun test_clearRecycle_success() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, isMovedToRecycle = true)
        val entity2 = testRecordEntity.copy(id = 2, isMovedToRecycle = true)
        val transactionId1 = 301L
        val transactionId2 = 302L

        every { recordDao.getMovedToRecycleRecords() } returns listOf(entity1, entity2)

        // Setup deleteRecordAndFileForever for entity1
        every { recordEditDao.insertRecordsEditOperation(match { it.recordId == 1L }) } returns transactionId1
        every { recordDao.deleteRecordById(1L) } returns Unit
        every { fileDataSource.deleteRecordFile(entity1.path) } returns true
        every { recordEditDao.deleteRecordEditOperationById(transactionId1) } returns Unit

        // Setup deleteRecordAndFileForever for entity2
        every { recordEditDao.insertRecordsEditOperation(match { it.recordId == 2L }) } returns transactionId2
        every { recordDao.deleteRecordById(2L) } returns Unit
        every { fileDataSource.deleteRecordFile(entity2.path) } returns true
        every { recordEditDao.deleteRecordEditOperationById(transactionId2) } returns Unit

        val result = recordsDataSourceImpl.clearRecycle()

        assertTrue(result)
    }

    @Test
    fun test_clearRecycle_partialFailure_returnsFalse() = runBlocking {
        val entity1 = testRecordEntity.copy(id = 1, isMovedToRecycle = true)
        val entity2 = testRecordEntity.copy(id = 2, isMovedToRecycle = true)
        val transactionId1 = 301L
        val transactionId2 = 302L

        every { recordDao.getMovedToRecycleRecords() } returns listOf(entity1, entity2)

        // entity1 deletes successfully
        every { recordEditDao.insertRecordsEditOperation(match { it.recordId == 1L }) } returns transactionId1
        every { recordDao.deleteRecordById(1L) } returns Unit
        every { fileDataSource.deleteRecordFile(entity1.path) } returns true
        every { recordEditDao.deleteRecordEditOperationById(transactionId1) } returns Unit

        // entity2 fails to delete file
        every { recordEditDao.insertRecordsEditOperation(match { it.recordId == 2L }) } returns transactionId2
        every { recordDao.deleteRecordById(2L) } returns Unit
        every { fileDataSource.deleteRecordFile(entity2.path) } returns false

        val result = recordsDataSourceImpl.clearRecycle()

        assertFalse(result)
    }

    @Test
    fun test_clearRecycle_emptyRecycleBin_returnsFalse() = runBlocking {
        every { recordDao.getMovedToRecycleRecords() } returns emptyList()

        val result = recordsDataSourceImpl.clearRecycle()

        assertFalse(result)
    }

    @Test
    fun test_clearRecycle_singleRecord_dbDeleteFails() = runBlocking {
        val entity = testRecordEntity.copy(id = 1, isMovedToRecycle = true)
        val transactionId = 301L

        every { recordDao.getMovedToRecycleRecords() } returns listOf(entity)
        every { recordEditDao.insertRecordsEditOperation(match { it.recordId == 1L }) } returns transactionId
        every { recordDao.deleteRecordById(1L) } throws Exception("DB error")
        every { recordEditDao.deleteRecordEditOperationById(transactionId) } returns Unit

        val result = recordsDataSourceImpl.clearRecycle()

        assertFalse(result)
    }

    // ==================== deleteLostRecord ====================

    @Test
    fun test_deleteLostRecord_success() = runBlocking {
        val recordId = 101L

        every { recordDao.deleteRecordById(recordId) } returns Unit
        every { prefs.activeRecordId } returns recordId
        every { prefs.activeRecordId = -1 } returns Unit

        val result = recordsDataSourceImpl.deleteLostRecord(recordId)

        assertTrue(result)
        verify(exactly = 1) { recordDao.deleteRecordById(recordId) }
        verify(exactly = 1) { prefs.activeRecordId = -1 }
    }

    @Test
    fun test_deleteLostRecord_success_activeRecordIdDifferent() = runBlocking {
        val recordId = 101L

        every { recordDao.deleteRecordById(recordId) } returns Unit
        every { prefs.activeRecordId } returns 999L

        val result = recordsDataSourceImpl.deleteLostRecord(recordId)

        assertTrue(result)
        verify(exactly = 1) { recordDao.deleteRecordById(recordId) }
        verify(exactly = 0) { prefs.activeRecordId = -1 }
    }

    @Test
    fun test_deleteLostRecord_dbFailure_returnsFalse() = runBlocking {
        val recordId = 101L

        every { recordDao.deleteRecordById(recordId) } throws Exception("DB error")

        val result = recordsDataSourceImpl.deleteLostRecord(recordId)

        assertFalse(result)
    }

    // ==================== deleteRecordAndFileForever - record not found ====================

    @Test
    fun test_deleteRecordAndFileForever_recordNotFound_returnsFalse() = runBlocking {
        val recordId = 999L

        every { recordDao.getRecordById(recordId) } returns null

        val result = recordsDataSourceImpl.deleteRecordAndFileForever(recordId)


        assertFalse(result)
        verify(exactly = 0) { recordEditDao.insertRecordsEditOperation(any()) }
    }
}
