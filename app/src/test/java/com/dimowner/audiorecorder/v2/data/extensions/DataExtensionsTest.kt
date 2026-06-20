package com.dimowner.audiorecorder.v2.data.extensions

import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataExtensionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestRecord(path: String, id: Long = 1L): Record {
        return Record(
            id = id,
            name = "TestRecord",
            durationMills = 1000L,
            created = System.currentTimeMillis(),
            added = System.currentTimeMillis(),
            removed = 0L,
            path = path,
            format = "m4a",
            size = 1024L,
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128000,
            isBookmarked = false,
            isWaveformProcessed = false,
            isMovedToRecycle = false,
            amps = intArrayOf(),
            description = ""
        )
    }

    @Test
    fun test_toSqlSortOrder() {
        assertEquals("ASC", SortOrder.DateAsc.toSqlSortOrder())
        assertEquals("DESC", SortOrder.DateDesc.toSqlSortOrder())
        assertEquals("ASC", SortOrder.NameAsc.toSqlSortOrder())
        assertEquals("DESC", SortOrder.NameDesc.toSqlSortOrder())
        assertEquals("ASC", SortOrder.DurationShortest.toSqlSortOrder())
        assertEquals("DESC", SortOrder.DurationLongest.toSqlSortOrder())
    }

    @Test
    fun test_toRecordsSortColumnName() {
        assertEquals(RECORDS_COLUMN_ADDED, SortOrder.DateAsc.toRecordsSortColumnName())
        assertEquals(RECORDS_COLUMN_ADDED, SortOrder.DateDesc.toRecordsSortColumnName())
        assertEquals(RECORDS_COLUMN_NAME, SortOrder.NameAsc.toRecordsSortColumnName())
        assertEquals(RECORDS_COLUMN_NAME, SortOrder.NameDesc.toRecordsSortColumnName())
        assertEquals(RECORDS_COLUMN_DURATION, SortOrder.DurationShortest.toRecordsSortColumnName())
        assertEquals(RECORDS_COLUMN_DURATION, SortOrder.DurationLongest.toRecordsSortColumnName())
    }

    @Test
    fun test_sort_columns_exists() {
        assertEquals(RECORDS_COLUMN_ADDED, RecordEntity::added.name)
        assertEquals(RECORDS_COLUMN_NAME, RecordEntity::name.name)
        assertEquals(RECORDS_COLUMN_DURATION, RecordEntity::duration.name)
    }

    @Test
    fun test_isLostRecord_nonExistentPath() {
        val record = createTestRecord("/nonexistent/path/record.m4a")
        assertTrue(record.isLostRecord())
    }

    @Test
    fun test_isLostRecord_existingFile() {
        val file = tempFolder.newFile("existing_record.m4a")
        val record = createTestRecord(file.absolutePath)
        assertFalse(record.isLostRecord())
    }

    @Test
    fun test_checkForLostRecords_emptyList() {
        val result = checkForLostRecords(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun test_checkForLostRecords_allValid() {
        val file1 = tempFolder.newFile("record1.m4a")
        val file2 = tempFolder.newFile("record2.m4a")
        val records = listOf(
            createTestRecord(file1.absolutePath, id = 1L),
            createTestRecord(file2.absolutePath, id = 2L)
        )

        val result = checkForLostRecords(records)
        assertTrue(result.isEmpty())
    }

    @Test
    fun test_checkForLostRecords_allLost() {
        val records = listOf(
            createTestRecord("/nonexistent/path/record1.m4a", id = 1L),
            createTestRecord("/nonexistent/path/record2.m4a", id = 2L)
        )

        val result = checkForLostRecords(records)
        assertEquals(2, result.size)
    }

    @Test
    fun test_checkForLostRecords_mixedList() {
        val existingFile = tempFolder.newFile("existing_record.m4a")
        val records = listOf(
            createTestRecord(existingFile.absolutePath, id = 1L),
            createTestRecord("/nonexistent/path/lost1.m4a", id = 2L),
            createTestRecord("/nonexistent/path/lost2.m4a", id = 3L)
        )

        val result = checkForLostRecords(records)
        assertEquals(2, result.size)
        assertEquals(2L, result[0].id)
        assertEquals(3L, result[1].id)
    }

    @Test
    fun test_isFileExists_existingFile() {
        val file = tempFolder.newFile("test_file.m4a")
        assertTrue(isFileExists(file.absolutePath))
    }

    @Test
    fun test_isFileExists_nonExistentPath() {
        assertFalse(isFileExists("/nonexistent/path/file.m4a"))
    }

    @Test
    fun test_isFileExists_emptyPath() {
        assertFalse(isFileExists(""))
    }
}
