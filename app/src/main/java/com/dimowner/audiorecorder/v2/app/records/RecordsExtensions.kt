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

package com.dimowner.audiorecorder.v2.app.records

import android.content.Context
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.util.TimeUtils.formatDateSmartLocale
import com.dimowner.audiorecorder.v2.app.DropDownMenuItem
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import com.dimowner.audiorecorder.v2.data.model.SortOrder

fun getRecordsDroDownMenuItems(): List<DropDownMenuItem<RecordDropDownMenuItemId>> {
    return RecordDropDownMenuItemId.entries.map {
        when (it) {
            RecordDropDownMenuItemId.SHARE -> DropDownMenuItem(
                id = it, textResId = R.string.share, imageResId = R.drawable.ic_share
            )
            RecordDropDownMenuItemId.INFORMATION -> DropDownMenuItem(
                id = it, textResId = R.string.info, imageResId = R.drawable.ic_info
            )
            RecordDropDownMenuItemId.RENAME -> DropDownMenuItem(
                id = it, textResId = R.string.rename, imageResId = R.drawable.ic_pencil
            )
            RecordDropDownMenuItemId.OPEN_WITH -> DropDownMenuItem(
                id = it, textResId = R.string.open_with, imageResId = R.drawable.ic_open_with
            )
            RecordDropDownMenuItemId.SAVE_AS -> DropDownMenuItem(
                id = it, textResId = R.string.save_as, imageResId = R.drawable.ic_save_alt
            )
            RecordDropDownMenuItemId.DELETE -> DropDownMenuItem(
                id = it, textResId = R.string.delete, imageResId = R.drawable.ic_delete_forever
            )
        }
    }
}

fun getSortDroDownMenuItems(): List<DropDownMenuItem<SortDropDownMenuItemId>> {
    return SortDropDownMenuItemId.entries.map {
        when (it) {
            SortDropDownMenuItemId.DATE_DESC -> DropDownMenuItem(
                id = it, textResId = R.string.by_date, imageResId = R.drawable.ic_calendar_today
            )
            SortDropDownMenuItemId.DATE_ASC -> DropDownMenuItem(
                id = it, textResId = R.string.by_date_desc, imageResId = R.drawable.ic_calendar_today
            )
            SortDropDownMenuItemId.NAME -> DropDownMenuItem(
                id = it, textResId = R.string.by_name, imageResId = R.drawable.ic_sort_by_alpha
            )
            SortDropDownMenuItemId.NAME_DESC -> DropDownMenuItem(
                id = it, textResId = R.string.by_name_desc, imageResId = R.drawable.ic_sort_by_alpha
            )
            SortDropDownMenuItemId.DURATION_DESC -> DropDownMenuItem(
                id = it, textResId = R.string.by_duration, imageResId = R.drawable.ic_access_time
            )
            SortDropDownMenuItemId.DURATION -> DropDownMenuItem(
                id = it, textResId = R.string.by_duration_desc, imageResId = R.drawable.ic_access_time
            )
        }
    }
}

fun SortOrder.toText(context: Context): String {
    return when (this) {
        SortOrder.DateDesc -> context.getString(R.string.by_date)
        SortOrder.DateAsc -> context.getString(R.string.by_date_desc)
        SortOrder.NameAsc -> context.getString(R.string.by_name)
        SortOrder.NameDesc -> context.getString(R.string.by_name_desc)
        SortOrder.DurationShortest -> context.getString(R.string.by_duration_desc)
        SortOrder.DurationLongest -> context.getString(R.string.by_duration)
    }
}

fun SortDropDownMenuItemId.toSortOrder(): SortOrder {
    return when (this) {
        SortDropDownMenuItemId.DATE_DESC -> SortOrder.DateDesc
        SortDropDownMenuItemId.DATE_ASC -> SortOrder.DateAsc
        SortDropDownMenuItemId.NAME -> SortOrder.NameAsc
        SortDropDownMenuItemId.NAME_DESC -> SortOrder.NameDesc
        SortDropDownMenuItemId.DURATION -> SortOrder.DurationShortest
        SortDropDownMenuItemId.DURATION_DESC -> SortOrder.DurationLongest
    }
}

/**
 * Updates the [recordsMap] within the current [RecordsScreenState]
 * and returning a new [RecordsScreenState] instance.
 *
 * This ensures the entire state object remains immutable while reflecting the change
 * to a single record in the nested map structure.
 *
 * @param recordId The unique ID of the record to find and update.
 * @param onUpdate A function that takes the old [RecordListItem] and returns the new, updated one.
 * @return A new [RecordsScreenState] with the updated record map.
 */
fun RecordsScreenState.updateRecordInMap(
    recordId: Long,
    onUpdate: (oldRecord: RecordListItem) -> RecordListItem
): RecordsScreenState {
    return this.copy(
        recordsMap = this.recordsMap.mapRecordInMap(recordId) { record ->
            onUpdate(record)
        }
    )
}

/**
 * Immutably finds and updates a single [RecordListItem] within the nested map structure.
 *
 * It iterates through each list in the map and applies the [onUpdate] lambda only to the
 * record whose [recordId] matches the provided ID, preserving all other records and groups.
 *
 * @param recordId The unique ID of the record to find and update.
 * @param onUpdate A function that takes the old [RecordListItem] and returns the new, updated one.
 * @return A new [Map] with the single record updated.
 */
fun Map<String, List<RecordListItem>>.mapRecordInMap(
    recordId: Long,
    onUpdate: (oldRecord: RecordListItem) -> RecordListItem
): Map<String, List<RecordListItem>> {
    return this.mapValues { (_, recordList) ->
        recordList.map { record ->
            if (record.recordId == recordId) {
                onUpdate(record)
            } else {
                record
            }
        }
    }
}

/**
 * Immutably removes a single [RecordListItem] with the matching [recordId] from the map.
 *
 * This function performs two filtering steps:
 * 1. Filters the records within each list, removing the targeted record.
 * 2. Filters the map itself, removing any date entries (keys) whose list of records
 * became empty after the first step.
 *
 * @param recordId The unique ID of the record to remove.
 * @return A new [Map] with the record removed, and potentially, an empty list group removed.
 */
fun Map<String, List<RecordListItem>>.removeRecordFromMap(
    recordId: Long,
): Map<String, List<RecordListItem>> {

    // Filtering out the record to be removed.
    val mapWithFilteredLists = this.mapValues { (_, recordList) ->
        recordList.filter { record ->
            record.recordId != recordId
        }
    }

    // Filtering out any date keys that now have an empty list.
    return mapWithFilteredLists.filterValues { recordList ->
        recordList.isNotEmpty()
    }
}

/**
 * Immutably adds a single [RecordListItem] to the map, placing it in the correct date group,
 * Ensures the group remains sorted.
 * 1. Identifies the correct group key using the provided [sortOrder].
 * 2. Appends the record to the existing list for that key, or creates a new list if the key doesn't exist.
 * 3. Sort the list based on the active SortOrder.
 * 4. Returns a new Map containing the updated data.
 */
fun Map<String, List<RecordListItem>>.addRecordToMap(
    context: Context,
    record: RecordListItem,
    sortOrder: SortOrder
): Map<String, List<RecordListItem>> {
    // Determine the key where this record belongs
    val key = if (sortOrder.isSortOrderByDate()) {
        formatDateSmartLocale(record.added, context)
    } else {
        ""
    }

    // Get the current list for that key (or empty if it's a new date)
    val currentList = this[key] ?: emptyList()

    // Add the new record
    val newList = currentList + record

    // Sort the list based on the active SortOrder
    val sortedList = newList.sort(sortOrder)

    return this + (key to sortedList)
}

/**
 * Returns a new list of [RecordListItem] sorted according to the specified [SortOrder].
 * @param sortOrder The strategy used to determine the element sequence.
 * @return A sorted copy of the original list.
 */
fun List<RecordListItem>.sort(sortOrder: SortOrder): List<RecordListItem> {
    return when (sortOrder) {
        SortOrder.DateAsc -> this.sortedBy { it.added }
        SortOrder.DateDesc -> this.sortedByDescending { it.added }
        SortOrder.NameAsc -> this.sortedBy { it.name }
        SortOrder.NameDesc -> this.sortedByDescending { it.name }
        SortOrder.DurationShortest -> this.sortedBy { it.duration }
        SortOrder.DurationLongest -> this.sortedByDescending { it.duration }
    }
}

/**
 * Groups the list of [RecordListItem] objects into groups of records divided by date.
 * This function is designed to support a UI with conditional sticky headers.
 * @param context The [Context] required by [TimeUtils.formatDateSmartLocale] to generate
 * localized date strings.
 * @param sortOrder Records list sort order.
 * @return A [Map] where keys are the formatted date strings and values are the
 * corresponding lists of [RecordListItem] objects.
 */
fun List<RecordListItem>.groupRecordsByDate(
    context: Context,
    sortOrder: SortOrder
): Map<String, List<RecordListItem>> {
    return this.groupBy {
        if (sortOrder.isSortOrderByDate()) {
            formatDateSmartLocale(it.added, context)
        } else {
            ""
        }
    }
}

/**
 * Checks if the current [SortOrder] is related to sorting by date
 * @return `true` if the sort order is [SortOrder.DateAsc] or [SortOrder.DateDesc], `false` otherwise.
 */
fun SortOrder.isSortOrderByDate(): Boolean {
    return this == SortOrder.DateAsc || this == SortOrder.DateDesc
}
