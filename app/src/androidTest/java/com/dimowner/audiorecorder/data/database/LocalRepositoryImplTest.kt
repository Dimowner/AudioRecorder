package com.dimowner.audiorecorder.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.FailedToRestoreRecord
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@SmallTest
class LocalRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var recordsDataSource: RecordsDataSource
    private lateinit var trashDataSource: TrashDataSource
    private lateinit var fileRepository: FileRepository
    private lateinit var prefs: Prefs
    private lateinit var repository: LocalRepositoryImpl

    private var tempFiles = mutableListOf<File>()

    @SuppressWarnings("LongParameterList")
    private fun createRecord(
        id: Int = Record.NO_ID,
        name: String = "test_record",
        duration: Long = 5000_000L, //5 seconds
        created: Long = System.currentTimeMillis(),
        added: Long = System.currentTimeMillis(),
        removed: Long = Long.MAX_VALUE,
        path: String = "/fake/path/test_record.m4a",
        format: String = "m4a",
        size: Long = 1024L,
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        bitrate: Int = 128000,
        bookmark: Boolean = false,
        waveformProcessed: Boolean = false,
        amps: IntArray = IntArray(0)
    ): Record {
        return Record(
            id, name, duration, created, added, removed, path,
            format, size, sampleRate, channelCount, bitrate,
            bookmark, waveformProcessed, amps
        )
    }

    private fun createTempFile(name: String = "test_record.m4a"): File {
        val dir = context.cacheDir
        val file = File(dir, name)
        file.createNewFile()
        tempFiles.add(file)
        return file
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clear singleton before each test
        LocalRepositoryImpl.clearInstance()

        recordsDataSource = RecordsDataSource.getInstance(context)
        trashDataSource = TrashDataSource.getInstance(context)

        fileRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)

        every { prefs.settingRecordingFormat } returns "m4a"
        every { prefs.settingSampleRate } returns 44100
        every { prefs.settingChannelCount } returns 2
        every { prefs.settingBitrate } returns 128000

        repository = LocalRepositoryImpl.getInstance(
            recordsDataSource, trashDataSource, fileRepository, prefs
        )
        repository.open()

        // Clean up tables before each test
        cleanUpDatabase()
    }

    @After
    fun tearDown() {
        cleanUpDatabase()
        repository.close()
        LocalRepositoryImpl.clearInstance()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun cleanUpDatabase() {
        // Delete all records from both tables
        val allIds = try { repository.allItemsIds } catch (_: Exception) { emptyList() }
        for (id in allIds) {
            repository.deleteRecordForever(id)
        }
        repository.emptyTrash()
    }

    // ── Insert & get record ─────────────────────────────────────────────────────

    @Test
    fun insertRecord_returnsRecordWithGeneratedId() {
        val file = createTempFile("insert_test.m4a")
        val record = createRecord(path = file.absolutePath)

        val inserted = repository.insertRecord(record)

        assertNotNull(inserted)
        assertTrue(inserted!!.id > 0)
        assertEquals("test_record", inserted.name)
        assertEquals(file.absolutePath, inserted.path)
        assertEquals(5000_000, inserted.duration)
        assertEquals(Long.MAX_VALUE, inserted.removed)
    }

    @Test
    fun getRecord_returnsInsertedRecord() {
        val file = createTempFile("get_test.m4a")
        val record = createRecord(path = file.absolutePath)
        val inserted = repository.insertRecord(record)!!

        val retrieved = repository.getRecord(inserted.id)

        assertNotNull(retrieved)
        assertEquals(inserted.id, retrieved!!.id)
        assertEquals("test_record", retrieved.name)
        assertEquals(file.absolutePath, retrieved.path)
        assertEquals(5000_000, retrieved.duration)
        assertEquals(Long.MAX_VALUE, retrieved.removed)
    }

    @Test
    fun getRecord_returnsNull_forNonExistentId() {
        val result = repository.getRecord(99999)

        assertNull(result)
    }

    // ── Find record by path ─────────────────────────────────────────────────────

    @Test
    fun findRecordByPath_returnsRecord_whenPathMatches() {
        val file = createTempFile("find_path.m4a")
        val record = createRecord(path = file.absolutePath)
        repository.insertRecord(record)

        val found = repository.findRecordByPath(file.absolutePath)

        assertNotNull(found)
        assertEquals(file.absolutePath, found!!.path)
    }

    @Test
    fun findRecordByPath_returnsNull_whenPathNotFound() {
        val found = repository.findRecordByPath("/nonexistent/path.m4a")

        assertNull(found)
    }

    @Test
    fun findRecordByPath_handlesPathWithApostrophe() {
        val file = createTempFile("it's_a_test.m4a")
        val record = createRecord(name = "it's_a_test", path = file.absolutePath)
        repository.insertRecord(record)

        val found = repository.findRecordByPath(file.absolutePath)

        assertNotNull(found)
        assertEquals(file.absolutePath, found!!.path)
    }

    // ── Find records by path (LIKE) ─────────────────────────────────────────────

    @Test
    fun findRecordsByPath_returnsMatchingRecords() {
        val file1 = createTempFile("search_a.m4a")
        val file2 = createTempFile("search_b.m4a")
        val file3 = createTempFile("other.m4a")

        repository.insertRecord(createRecord(name = "search_a", path = file1.absolutePath))
        repository.insertRecord(createRecord(name = "search_b", path = file2.absolutePath))
        repository.insertRecord(createRecord(name = "other", path = file3.absolutePath))

        val results = repository.findRecordsByPath("search_")

        assertEquals(2, results.size)
    }

    @Test
    fun findRecordsByPath_returnsEmpty_whenNoMatch() {
        val results = repository.findRecordsByPath("/zzz/nonexistent/")

        assertTrue(results.isEmpty())
    }

    // ── hasRecordsWithPath ──────────────────────────────────────────────────────

    @Test
    fun hasRecordsWithPath_returnsTrue_whenRecordExists() {
        val file = createTempFile("has_path.m4a")
        repository.insertRecord(createRecord(path = file.absolutePath))

        assertTrue(repository.hasRecordsWithPath("has_path"))
    }

    @Test
    fun hasRecordsWithPath_returnsFalse_whenNoRecordExists() {
        assertFalse(repository.hasRecordsWithPath("/nonexistent/path"))
    }

    // ── Update record ───────────────────────────────────────────────────────────

    @Test
    fun updateRecord_returnsTrue_whenRecordExists() {
        val file = createTempFile("update_test.m4a")
        val record = createRecord(path = file.absolutePath)
        val inserted = repository.insertRecord(record)!!

        inserted.setBookmark(true)
        inserted.duration = 10000L
        val updated = repository.updateRecord(inserted)

        assertTrue(updated)

        val retrieved = repository.getRecord(inserted.id)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isBookmarked)
        assertEquals(10000L, retrieved.duration)
    }

    @Test
    fun updateRecord_returnsFalse_whenRecordDoesNotExist() {
        val record = createRecord(id = 99999, path = "/fake/path.m4a")

        // updateItem with non-existent id should return 0 rows affected
        val updated = repository.updateRecord(record)

        assertFalse(updated)
    }

    // ── Get all records ─────────────────────────────────────────────────────────

    @Test
    fun getAllRecords_returnsAllInsertedRecords() {
        val file1 = createTempFile("all_1.m4a")
        val file2 = createTempFile("all_2.m4a")
        val file3 = createTempFile("all_3.m4a")

        repository.insertRecord(createRecord(name = "all_1", path = file1.absolutePath))
        repository.insertRecord(createRecord(name = "all_2", path = file2.absolutePath))
        repository.insertRecord(createRecord(name = "all_3", path = file3.absolutePath))

        val all = repository.allRecords

        assertEquals(3, all.size)
    }

    @Test
    fun getAllRecords_returnsEmptyList_whenNoRecords() {
        val all = repository.allRecords

        assertTrue(all.isEmpty())
    }

    // ── Get all items IDs ───────────────────────────────────────────────────────

    @Test
    fun getAllItemsIds_returnsCorrectIds() {
        val file1 = createTempFile("ids_1.m4a")
        val file2 = createTempFile("ids_2.m4a")

        val r1 = repository.insertRecord(createRecord(name = "ids_1", path = file1.absolutePath))!!
        val r2 = repository.insertRecord(createRecord(name = "ids_2", path = file2.absolutePath))!!

        val ids = repository.allItemsIds

        assertEquals(2, ids.size)
        assertTrue(ids.contains(r1.id))
        assertTrue(ids.contains(r2.id))
    }

    // ── Get records paged ───────────────────────────────────────────────────────

    @Test
    fun getRecords_returnsPagedResults() {
        // Insert a few records
        for (i in 1..5) {
            val file = createTempFile("paged_$i.m4a")
            repository.insertRecord(createRecord(name = "paged_$i", path = file.absolutePath))
        }

        val page1 = repository.getRecords(1)

        assertEquals(5, page1.size)
    }

    @Test
    fun getRecords_returnsPagedResults_2pages() {
        // Insert a few records
        for (i in 1..51) {
            val file = createTempFile("paged_$i.m4a")
            repository.insertRecord(createRecord(name = "paged_$i", path = file.absolutePath))
        }

        val page1 = repository.getRecords(1)
        val page2 = repository.getRecords(2)

        assertEquals(50, page1.size)
        assertEquals(1, page2.size)
    }

    // ── Get records paged with ordering ─────────────────────────────────────────

    @Test
    fun getRecords_withSortByName_returnsSortedByNameAsc() {
        val fileA = createTempFile("alpha.m4a")
        val fileC = createTempFile("charlie.m4a")
        val fileB = createTempFile("bravo.m4a")

        repository.insertRecord(createRecord(name = "charlie", path = fileC.absolutePath))
        repository.insertRecord(createRecord(name = "alpha", path = fileA.absolutePath))
        repository.insertRecord(createRecord(name = "bravo", path = fileB.absolutePath))

        val records = repository.getRecords(1, AppConstants.SORT_NAME)

        assertEquals(3, records.size)
        assertEquals("alpha", records[0].name)
        assertEquals("bravo", records[1].name)
        assertEquals("charlie", records[2].name)
    }

    @Test
    fun getRecords_withSortByNameDesc_returnsSortedByNameDesc() {
        val fileA = createTempFile("alpha2.m4a")
        val fileC = createTempFile("charlie2.m4a")
        val fileB = createTempFile("bravo2.m4a")

        repository.insertRecord(createRecord(name = "charlie", path = fileC.absolutePath))
        repository.insertRecord(createRecord(name = "alpha", path = fileA.absolutePath))
        repository.insertRecord(createRecord(name = "bravo", path = fileB.absolutePath))

        val records = repository.getRecords(1, AppConstants.SORT_NAME_DESC)

        assertEquals(3, records.size)
        assertEquals("charlie", records[0].name)
        assertEquals("bravo", records[1].name)
        assertEquals("alpha", records[2].name)
    }

    @Test
    fun getRecords_withSortByDuration_returnsSortedByDurationDesc() {
        val f1 = createTempFile("dur1.m4a")
        val f2 = createTempFile("dur2.m4a")
        val f3 = createTempFile("dur3.m4a")

        repository.insertRecord(createRecord(name = "short", duration = 1000L, path = f1.absolutePath))
        repository.insertRecord(createRecord(name = "long", duration = 9000L, path = f2.absolutePath))
        repository.insertRecord(createRecord(name = "medium", duration = 5000L, path = f3.absolutePath))

        val records = repository.getRecords(1, AppConstants.SORT_DURATION)

        assertEquals(3, records.size)
        assertEquals("long", records[0].name)
        assertEquals("medium", records[1].name)
        assertEquals("short", records[2].name)
    }

    @Test
    fun getRecords_withSortByDurationDesc_returnsSortedByDurationAsc() {
        val f1 = createTempFile("dur_asc1.m4a")
        val f2 = createTempFile("dur_asc2.m4a")
        val f3 = createTempFile("dur_asc3.m4a")

        repository.insertRecord(createRecord(name = "short", duration = 1000L, path = f1.absolutePath))
        repository.insertRecord(createRecord(name = "long", duration = 9000L, path = f2.absolutePath))
        repository.insertRecord(createRecord(name = "medium", duration = 5000L, path = f3.absolutePath))

        val records = repository.getRecords(1, AppConstants.SORT_DURATION_DESC)

        assertEquals(3, records.size)
        assertEquals("short", records[0].name)
        assertEquals("medium", records[1].name)
        assertEquals("long", records[2].name)
    }

    // ── Delete record (moves to trash) ──────────────────────────────────────────

    @Test
    fun deleteRecord_movesRecordToTrash() {
        val file = createTempFile("delete_me.m4a")
        val inserted = repository.insertRecord(createRecord(name = "delete_me", path = file.absolutePath))!!

        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns file.absolutePath + ".del"

        val result = repository.deleteRecord(inserted.id)
        assertTrue(result)

        val record = repository.getRecord(inserted.id)
        assertNull(record)

        val trashRecord = repository.getTrashRecord(inserted.id)
        assertNotNull(trashRecord)
        assertEquals("delete_me", trashRecord.name)
        //Verify the record was removed in 1 minute range
        assertEquals(System.currentTimeMillis()/60_000, (trashRecord?.removed ?: 0)/60_000L)
    }

    // ── Delete record forever ───────────────────────────────────────────────────

    @Test
    fun deleteRecordForever_removesFromDatabase() {
        val file = createTempFile("forever_delete.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "forever_delete", path = file.absolutePath)
        )!!

        repository.deleteRecordForever(inserted.id)

        val record = repository.getRecord(inserted.id)
        assertNull(record)
    }

    // ── Get records durations ───────────────────────────────────────────────────

    @Test
    fun getRecordsDurations_returnsAllDurations() {
        val durationsEmpty = repository.recordsDurations
        assertTrue(durationsEmpty.isEmpty())

        val f1 = createTempFile("dur_r1.m4a")
        val f2 = createTempFile("dur_r2.m4a")

        repository.insertRecord(createRecord(name = "r1", duration = 3000L, path = f1.absolutePath))
        repository.insertRecord(createRecord(name = "r2", duration = 7000L, path = f2.absolutePath))

        val durations = repository.recordsDurations

        assertEquals(2, durations.size)
        assertTrue(durations.contains(3000L))
        assertTrue(durations.contains(7000L))
    }

    // ── Bookmarks ───────────────────────────────────────────────────────────────

    @Test
    fun addToBookmarks_setsBookmarkFlag() {
        val file = createTempFile("bookmark_add.m4a")
        val inserted = repository.insertRecord(createRecord(path = file.absolutePath))!!

        val result = repository.addToBookmarks(inserted.id)

        assertTrue(result)
        val retrieved = repository.getRecord(inserted.id)
        assertTrue(retrieved!!.isBookmarked)
    }

    @Test
    fun addToBookmarks_returnsFalse_forNonExistentId() {
        val result = repository.addToBookmarks(99999)

        assertFalse(result)
    }

    @Test
    fun removeFromBookmarks_clearsBookmarkFlag() {
        val file = createTempFile("bookmark_remove.m4a")
        val inserted = repository.insertRecord(createRecord(bookmark = true, path = file.absolutePath))!!
        val result1 = repository.addToBookmarks(inserted.id)

        assertTrue(result1)
        val retrieved1 = repository.getRecord(inserted.id)
        assertTrue(retrieved1!!.isBookmarked)

        val result2 = repository.removeFromBookmarks(inserted.id)

        assertTrue(result2)
        val retrieved2 = repository.getRecord(inserted.id)
        assertFalse(retrieved2!!.isBookmarked)
    }

    @Test
    fun removeFromBookmarks_returnsFalse_forNonExistentId() {
        val result = repository.removeFromBookmarks(99999)

        assertFalse(result)
    }

    @Test
    fun getBookmarks_returnsOnlyBookmarkedRecords() {
        val f1 = createTempFile("bk1.m4a")
        val f2 = createTempFile("bk2.m4a")
        val f3 = createTempFile("bk3.m4a")

        val r1 = repository.insertRecord(createRecord(name = "bk1", path = f1.absolutePath))!!
        repository.insertRecord(createRecord(name = "bk2", path = f2.absolutePath))
        val r3 = repository.insertRecord(createRecord(name = "bk3", path = f3.absolutePath))!!

        repository.addToBookmarks(r1.id)
        repository.addToBookmarks(r3.id)

        val bookmarks = repository.bookmarks

        assertEquals(2, bookmarks.size)
        assertTrue(bookmarks.any { it.name == "bk1" })
        assertTrue(bookmarks.any { it.name == "bk3" })
        assertFalse(bookmarks.any { it.name == "bk2" })
    }

    @Test
    fun getBookmarks_returnsEmptyList_whenNoBookmarks() {
        val f1 = createTempFile("no_bk.m4a")
        repository.insertRecord(createRecord(name = "no_bk", path = f1.absolutePath))

        val bookmarks = repository.bookmarks

        assertTrue(bookmarks.isEmpty())
    }

    // ── Trash records ───────────────────────────────────────────────────────────

    @Test
    fun getTrashRecords_returnsEmptyInitially() {
        val trashRecords = repository.trashRecords

        assertTrue(trashRecords.isEmpty())
    }

    @Test
    fun getTrashRecordsCount_returnsZeroInitially() {
        assertEquals(0, repository.trashRecordsCount)
    }

    @Test
    fun getTrashRecordsIds_returnsEmptyInitially() {
        assertTrue(repository.trashRecordsIds.isEmpty())
    }

    // ── Trash record operations via deleteRecord ────────────────────────────────

    @Test
    fun deleteRecord_andGetTrashRecord() {
        val file = createTempFile("trash_test.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "trash_test", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".del"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath

        val deleted = repository.deleteRecord(inserted.id)
        assertTrue(deleted)

        // Record removed from main table
        assertNull(repository.getRecord(inserted.id))

        // Record should be in trash
        val trashRecords = repository.trashRecords
        assertTrue(trashRecords.isNotEmpty())
        val trashRecord = trashRecords.firstOrNull { it.name == "trash_test" }
        assertNotNull(trashRecord)
        assertTrue(trashRecord?.path?.contains(".del") ?: false)
    }

    // ── Remove from trash ───────────────────────────────────────────────────────

    @Test
    fun removeFromTrash_removesRecordFromTrash() {
        val file = createTempFile("remove_trash.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "remove_trash", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".del"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath

        repository.deleteRecord(inserted.id)

        val trashRecords = repository.trashRecords
        assertTrue(trashRecords.isNotEmpty())
        val trashId = trashRecords.first { it.name == "remove_trash" }.id

        val removed = repository.removeFromTrash(trashId)
        assertTrue(removed)

        val trashRecords2 = repository.trashRecords
        assertTrue(trashRecords2.isEmpty())

        assertEquals(0, repository.trashRecordsCount)
    }

    // ── Empty trash ─────────────────────────────────────────────────────────────

    @Test
    fun emptyTrash_removesAllTrashRecords() {
        val file1 = createTempFile("empty_trash1.m4a")
        val file2 = createTempFile("empty_trash2.m4a")

        val r1 = repository.insertRecord(
            createRecord(name = "empty_trash1", path = file1.absolutePath)
        )!!
        val r2 = repository.insertRecord(
            createRecord(name = "empty_trash2", path = file2.absolutePath)
        )!!

        every { fileRepository.markAsTrashRecord(file1.absolutePath) } returns file1.absolutePath + ".del"
        every { fileRepository.markAsTrashRecord(file2.absolutePath) } returns file2.absolutePath + ".del"

        repository.deleteRecord(r1.id)
        repository.deleteRecord(r2.id)

        assertTrue(repository.trashRecordsCount == 2)

        val result = repository.emptyTrash()
        assertTrue(result)

        val trashRecords = repository.trashRecords
        assertTrue(trashRecords.isEmpty())
        assertEquals(0, repository.trashRecordsCount)
    }

    // ── Restore from trash ──────────────────────────────────────────────────────

    @Test
    fun restoreFromTrash_restoresRecordToMainTable() {
        val file = createTempFile("restore_test.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "restore_test", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".del"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath
        every { fileRepository.unmarkTrashRecord(renamedPath) } returns file.absolutePath

        repository.deleteRecord(inserted.id)

        // Record is in trash
        val trashRecords = repository.trashRecords
        assertTrue(trashRecords.any { it.name == "restore_test" })
        val trashId = trashRecords.first { it.name == "restore_test" }.id

        // Restore it
        repository.restoreFromTrash(trashId)

        // Record should be back in main table
        val allRecords = repository.allRecords
        assertTrue(allRecords.any { it.name == "restore_test" })

        // Trash should be empty for this record
        val trashAfterRestore = repository.trashRecords
        assertTrue(trashAfterRestore.isEmpty())
        assertFalse(trashAfterRestore.any { it.id == trashId })
    }

    @Test(expected = FailedToRestoreRecord::class)
    fun restoreFromTrash_throwsException_whenRecordNotFound() {
        repository.restoreFromTrash(99999)
    }

    @Test(expected = FailedToRestoreRecord::class)
    fun restoreFromTrash_throwsException_whenFileRestoreFails() {
        val file = createTempFile("fail_restore.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "fail_restore", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".trash"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath
        every { fileRepository.unmarkTrashRecord(renamedPath) } returns null

        repository.deleteRecord(inserted.id)

        val trashRecords = repository.trashRecords
        val trashId = trashRecords.first { it.name == "fail_restore" }.id

        repository.restoreFromTrash(trashId)
    }

    // ── Update trash record ─────────────────────────────────────────────────────

    @Test
    fun updateTrashRecord_updatesTrashEntry() {
        val file = createTempFile("update_trash.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "update_trash", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".trash"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath

        repository.deleteRecord(inserted.id)

        val trashRecords = repository.trashRecords
        assertTrue(trashRecords.isNotEmpty())

        val trashRecord = trashRecords.first { it.name == "update_trash" }
        trashRecord.duration = 99999L

        val updated = repository.updateTrashRecord(trashRecord)
        assertTrue(updated)

        val retrievedTrash = repository.getTrashRecord(trashRecord.id)
        assertNotNull(retrievedTrash)
        assertEquals(99999L, retrievedTrash!!.duration)
    }

    // ── Get trash record by id ──────────────────────────────────────────────────

    @Test
    fun getTrashRecord_returnsNull_forNonExistentId() {
        val result = repository.getTrashRecord(99999)

        assertNull(result)
    }

    // ── insertEmptyFile ─────────────────────────────────────────────────────────

    @Test
    fun insertEmptyFile_createsRecordFromPath() {
        val file = createTempFile("empty_file.m4a")

        val record = repository.insertEmptyFile(file.absolutePath)

        assertNotNull(record)
        assertEquals("empty_file", record!!.name)
        assertEquals(file.absolutePath, record.path)
        assertEquals("m4a", record.format)
        assertEquals(44100, record.sampleRate)
        assertEquals(2, record.channelCount)
        assertEquals(128000, record.bitrate)
    }

    @Test(expected = IOException::class)
    fun insertEmptyFile_throwsIOException_whenPathIsEmpty() {
        repository.insertEmptyFile("")
    }

    @Test(expected = IOException::class)
    fun insertEmptyFile_throwsIOException_whenPathIsNull() {
        repository.insertEmptyFile(null)
    }

    // ── deleteAllRecords (always returns false in current impl) ─────────────────

    @Test
    fun deleteAllRecords_returnsFalse() {
        assertFalse(repository.deleteAllRecords())
    }

    // ── Lost records listener ───────────────────────────────────────────────────

    @Test
    fun checkForLostRecords_notifiesListener_whenFileDoesNotExist() {
        var lostRecords: List<Record>? = null
        repository.setOnRecordsLostListener { list -> lostRecords = list }

        // Insert a record with a non-existent file path
        val record = createRecord(path = "/nonexistent/lost_file.m4a")
        repository.insertRecord(record)

        // getAllRecords triggers checkForLostRecords
        repository.allRecords

        assertNotNull(lostRecords)
        assertTrue(lostRecords!!.isNotEmpty())
        assertTrue(lostRecords.any { it.path == "/nonexistent/lost_file.m4a" })
    }

    @Test
    fun checkForLostRecords_doesNotNotify_whenAllFilesExist() {
        var listenerCalled = false
        repository.setOnRecordsLostListener { listenerCalled = true }

        val file = createTempFile("existing_file.m4a")
        repository.insertRecord(createRecord(path = file.absolutePath))

        // getAllRecords triggers checkForLostRecords
        repository.allRecords

        assertFalse(listenerCalled)
    }

    @Test
    fun checkForLostRecords_doesNotNotify_whenListenerIsNull() {
        repository.setOnRecordsLostListener(null)

        val record = createRecord(path = "/nonexistent/no_listener.m4a")
        repository.insertRecord(record)

        // Should not throw
        repository.allRecords
    }

    // ── Singleton behavior ──────────────────────────────────────────────────────

    @Test
    fun getInstance_returnsSameInstance() {
        val instance1 = LocalRepositoryImpl.getInstance(
            recordsDataSource, trashDataSource, fileRepository, prefs
        )
        val instance2 = LocalRepositoryImpl.getInstance(
            recordsDataSource, trashDataSource, fileRepository, prefs
        )

        assertTrue(instance1 === instance2)
    }

    @Test
    fun clearInstance_allowsNewInstanceCreation() {
        val instance1 = LocalRepositoryImpl.getInstance(
            recordsDataSource, trashDataSource, fileRepository, prefs
        )
        LocalRepositoryImpl.clearInstance()
        val instance2 = LocalRepositoryImpl.getInstance(
            recordsDataSource, trashDataSource, fileRepository, prefs
        )

        // After clearing, a new instance should be created
        // Note: they may or may not be different objects depending on internal state,
        // but clearInstance guarantees getInstance will create new
        assertNotNull(instance2)
        assertFalse(instance1 === instance2)
    }

    // ── Open/close behavior ─────────────────────────────────────────────────────

    @Test
    fun getRecord_opensDataSourceIfClosed() {
        repository.close()

        // getRecord should auto-open the data source
        val file = createTempFile("auto_open.m4a")
        // Need to open first to insert
        repository.open()
        val inserted = repository.insertRecord(createRecord(path = file.absolutePath))!!
        repository.close()

        // getRecord should work even after close (auto-opens)
        val retrieved = repository.getRecord(inserted.id)
        assertNotNull(retrieved)
    }

    // ── removeOutdatedTrashRecords ──────────────────────────────────────────────

    @Test
    fun removeOutdatedTrashRecords_removesExpiredRecords() {
        // Insert a record, move it to trash
        val file = createTempFile("outdated.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "outdated", path = file.absolutePath)
        )!!

        val renamedPath = file.absolutePath + ".del"
        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns renamedPath

        repository.deleteRecord(inserted.id)

        // Verify it's in trash
        assertTrue(repository.trashRecordsCount > 0)

        // removeOutdatedTrashRecords should not remove it since it was just added
        repository.removeOutdatedTrashRecords()

        // Record should still be in trash (not expired yet)
        assertTrue(repository.trashRecords.any { it.name == "outdated" })
    }

    //-- Case when a record expired and deleted from trash is not covered --------

    // ── deleteRecord edge cases ─────────────────────────────────────────────────

    @Test
    fun deleteRecord_returnsFalse_whenRecordNotFound() {
        val result = repository.deleteRecord(99999)

        assertFalse(result)
    }

    @Test
    fun deleteRecord_returnsFalse_whenFileRenameFails() {
        val file = createTempFile("rename_fail.m4a")
        val inserted = repository.insertRecord(
            createRecord(name = "rename_fail", path = file.absolutePath)
        )!!

        every { fileRepository.markAsTrashRecord(file.absolutePath) } returns null

        val result = repository.deleteRecord(inserted.id)

        assertFalse(result)
    }
}