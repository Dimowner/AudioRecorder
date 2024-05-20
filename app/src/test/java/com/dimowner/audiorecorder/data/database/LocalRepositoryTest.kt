package com.dimowner.audiorecorder.data.database

import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.FailedToRestoreRecord
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class LocalRepositoryTest {

    @MockK
    lateinit var recordsDataSource: RecordsDataSource

    @MockK
    lateinit var trashDataSource: TrashDataSource

    @MockK
    lateinit var fileRepository: FileRepository

    @MockK
    lateinit var prefs: Prefs

    private lateinit var localRepository: LocalRepository

    private lateinit var testRecord: Record

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        testRecord = Record(
            101,
            "name",
            100L,
            100500L,
            500100L,
            0L,
            "path",
            "format",
            400L,
            32000,
            2,
            128000,
            true,
            true,
            intArrayOf(1, 2, 3, 4)
        )

        every { trashDataSource.isOpen } returns true
        every { trashDataSource.getAll() } returns arrayListOf()

        localRepository = LocalRepositoryImpl.getInstance(
            recordsDataSource,
            trashDataSource,
            fileRepository,
            prefs
        )
    }

    @After
    fun after() {
        LocalRepositoryImpl.clearInstance()
    }

    @Test
    fun test_deleteRecord_success() {
        val testId = 10
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { recordsDataSource.getItem(testId) } returns testRecord
        every { fileRepository.markAsTrashRecord(path) } returns "$path.deleted"
        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { trashDataSource.insertItem(capture(updatedRecord)) } returns testRecord
        every { recordsDataSource.deleteItem(testId) } returns 1

        val result = localRepository.deleteRecord(testId)

        assertTrue(result)
        assertEquals("$path.deleted", testRecord.path)
        assertEquals("$path.deleted", updatedRecord.captured.path)

        verifyOrder {
            recordsDataSource.isOpen
            trashDataSource.isOpen
            recordsDataSource.getItem(testId)
            fileRepository.markAsTrashRecord(path)
            trashDataSource.insertItem(testRecord)
            recordsDataSource.deleteItem(testId)
        }
    }

    @Test
    fun test_deleteRecord_success2() {
        val testId = 10
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { recordsDataSource.getItem(testId) } returns testRecord
        every { fileRepository.markAsTrashRecord(path) } returns null andThen "$path.deleted"
        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { trashDataSource.insertItem(capture(updatedRecord)) } returns testRecord
        every { recordsDataSource.deleteItem(testId) } returns 1

        val result = localRepository.deleteRecord(testId)

        assertTrue(result)
        assertEquals("$path.deleted", testRecord.path)
        assertEquals("$path.deleted", updatedRecord.captured.path)

        verifyOrder {
            recordsDataSource.isOpen
            trashDataSource.isOpen
            recordsDataSource.getItem(testId)
            fileRepository.markAsTrashRecord(path)
            fileRepository.markAsTrashRecord(path)
            trashDataSource.insertItem(testRecord)
            recordsDataSource.deleteItem(testId)
        }
    }

    @Test
    fun test_deleteRecord_fail_to_insert_to_trash_records() {
        val testId = 10
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { recordsDataSource.getItem(testId) } returns testRecord
        every { fileRepository.markAsTrashRecord(path) } returns "$path.deleted"
        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { trashDataSource.insertItem(capture(updatedRecord)) } returns null
        every { fileRepository.unmarkTrashRecord("$path.deleted") } returns path

        val result = localRepository.deleteRecord(testId)

        assertFalse(result)
        assertEquals("$path.deleted", testRecord.path)
        assertEquals("$path.deleted", updatedRecord.captured.path)

        verifyOrder {
            recordsDataSource.isOpen
            trashDataSource.isOpen
            recordsDataSource.getItem(testId)
            fileRepository.markAsTrashRecord(path)
            trashDataSource.insertItem(testRecord)
            fileRepository.unmarkTrashRecord("$path.deleted")
        }
    }

    @Test
    fun test_deleteRecord_fail_to_insert_to_trash_records2() {
        val testId = 10
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { recordsDataSource.getItem(testId) } returns testRecord
        every { fileRepository.markAsTrashRecord(path) } returns null andThen "$path.deleted"
        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { trashDataSource.insertItem(capture(updatedRecord)) } returns null
        every { fileRepository.unmarkTrashRecord("$path.deleted") } returns null andThen path

        val result = localRepository.deleteRecord(testId)

        assertFalse(result)
        assertEquals("$path.deleted", testRecord.path)
        assertEquals("$path.deleted", updatedRecord.captured.path)

        verifyOrder {
            recordsDataSource.isOpen
            trashDataSource.isOpen
            recordsDataSource.getItem(testId)
            fileRepository.markAsTrashRecord(path)
            fileRepository.markAsTrashRecord(path)
            trashDataSource.insertItem(testRecord)
            fileRepository.unmarkTrashRecord("$path.deleted")
            fileRepository.unmarkTrashRecord("$path.deleted")
        }
    }

    @Test
    fun test_restoreFromTrash_success() {
        val testId = 10
        testRecord.path = "path.deleted"
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { trashDataSource.getItem(testId) } returns testRecord
        every { fileRepository.unmarkTrashRecord(path) } returns "path"

        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { recordsDataSource.insertItem(capture(updatedRecord)) } returns testRecord
        every { trashDataSource.deleteItem(testId) } returns 1

        localRepository.restoreFromTrash(testId)

        assertEquals("path", testRecord.path)
        assertEquals("path", updatedRecord.captured.path)

        verifyOrder {
            trashDataSource.isOpen
            trashDataSource.getItem(testId)
            fileRepository.unmarkTrashRecord(path)
            recordsDataSource.isOpen
            recordsDataSource.insertItem(testRecord)
            trashDataSource.deleteItem(testId)
        }
    }

    @Test
    fun test_restoreFromTrash_success2() {
        val testId = 10
        testRecord.path = "path.deleted"
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { trashDataSource.getItem(testId) } returns testRecord
        every { fileRepository.unmarkTrashRecord(path) } returns null andThen "path"

        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { recordsDataSource.insertItem(capture(updatedRecord)) } returns testRecord
        every { trashDataSource.deleteItem(testId) } returns 1

        localRepository.restoreFromTrash(testId)

        assertEquals("path", testRecord.path)
        assertEquals("path", updatedRecord.captured.path)

        verifyOrder {
            trashDataSource.isOpen
            trashDataSource.getItem(testId)
            fileRepository.unmarkTrashRecord(path)
            fileRepository.unmarkTrashRecord(path)
            recordsDataSource.isOpen
            recordsDataSource.insertItem(testRecord)
            trashDataSource.deleteItem(testId)
        }
    }

    @Test
    fun test_restoreFromTrash_fail2() {
        val testId = 10
        testRecord.path = "path.deleted"
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { trashDataSource.getItem(testId) } returns testRecord
        every { fileRepository.unmarkTrashRecord(path) } returns null andThen "path"
        every { fileRepository.markAsTrashRecord("path") } returns null andThen "path.deleted"

        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { recordsDataSource.insertItem(capture(updatedRecord)) } returns null andThen testRecord

        assertThrows(FailedToRestoreRecord::class.java) {
            localRepository.restoreFromTrash(testId)
        }

        assertEquals("path", testRecord.path)
        assertEquals("path", updatedRecord.captured.path)

        verify(exactly = 0) {
            trashDataSource.deleteItem(testId)
        }
        verifyOrder {
            trashDataSource.isOpen
            trashDataSource.getItem(testId)
            fileRepository.unmarkTrashRecord(path)
            fileRepository.unmarkTrashRecord(path)
            recordsDataSource.isOpen
            recordsDataSource.insertItem(testRecord)
            fileRepository.markAsTrashRecord("path")
            fileRepository.markAsTrashRecord("path")
        }
    }

    @Test
    fun test_restoreFromTrash_fail() {
        val testId = 10
        testRecord.path = "path.deleted"
        val path = testRecord.path
        every { recordsDataSource.isOpen } returns true
        every { trashDataSource.isOpen } returns true
        every { trashDataSource.getItem(testId) } returns testRecord
        every { fileRepository.unmarkTrashRecord(path) } returns "path"
        every { fileRepository.markAsTrashRecord("path") } returns null andThen "path.deleted"

        val updatedRecord = slot<Record>()
        assertEquals(path, testRecord.path)
        every { recordsDataSource.insertItem(capture(updatedRecord)) } returns null andThen testRecord

        assertThrows(FailedToRestoreRecord::class.java) {
            localRepository.restoreFromTrash(testId)
        }

        assertEquals("path", testRecord.path)
        assertEquals("path", updatedRecord.captured.path)

        verify(exactly = 0) {
            trashDataSource.deleteItem(testId)
        }
        verifyOrder {
            trashDataSource.isOpen
            trashDataSource.getItem(testId)
            fileRepository.unmarkTrashRecord(path)
            recordsDataSource.isOpen
            recordsDataSource.insertItem(testRecord)
            fileRepository.markAsTrashRecord("path")
            fileRepository.markAsTrashRecord("path")
        }
    }
}
