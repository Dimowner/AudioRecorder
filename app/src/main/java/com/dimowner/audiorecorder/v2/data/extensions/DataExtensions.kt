package com.dimowner.audiorecorder.v2.data.extensions

import com.dimowner.audiorecorder.v2.data.model.SortOrder

const val RECORDS_COLUMN_ADDED = "added"
const val RECORDS_COLUMN_NAME = "name"
const val RECORDS_COLUMN_DURATION = "duration"

fun SortOrder.toSqlSortOrder(): String {
    return when(this) {
        SortOrder.DateDesc,
        SortOrder.NameDesc,
        SortOrder.DurationLongest -> "DESC"
        SortOrder.DateAsc,
        SortOrder.NameAsc,
        SortOrder.DurationShortest -> "ASC"
    }
}

fun SortOrder.toRecordsSortColumnName(): String {
    return when(this) {
        SortOrder.DateAsc,
        SortOrder.DateDesc -> RECORDS_COLUMN_ADDED
        SortOrder.NameAsc,
        SortOrder.NameDesc -> RECORDS_COLUMN_NAME
        SortOrder.DurationShortest,
        SortOrder.DurationLongest -> RECORDS_COLUMN_DURATION
    }
}
