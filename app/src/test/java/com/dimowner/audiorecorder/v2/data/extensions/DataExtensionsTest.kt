package com.dimowner.audiorecorder.v2.data.extensions

import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DataExtensionsTest {

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
}
