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
            SortDropDownMenuItemId.DATE -> DropDownMenuItem(
                id = it, textResId = R.string.by_date, imageResId = R.drawable.ic_calendar_today
            )
            SortDropDownMenuItemId.DATE_DESC -> DropDownMenuItem(
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
        SortOrder.DateAsc -> context.getString(R.string.by_date)
        SortOrder.DateDesc -> context.getString(R.string.by_date_desc)
        SortOrder.NameAsc -> context.getString(R.string.by_name)
        SortOrder.NameDesc -> context.getString(R.string.by_name_desc)
        SortOrder.DurationShortest -> context.getString(R.string.by_duration_desc)
        SortOrder.DurationLongest -> context.getString(R.string.by_duration)
    }
}

fun SortDropDownMenuItemId.toSortOrder(): SortOrder {
    return when (this) {
        SortDropDownMenuItemId.DATE -> SortOrder.DateAsc
        SortDropDownMenuItemId.DATE_DESC -> SortOrder.DateDesc
        SortDropDownMenuItemId.NAME -> SortOrder.NameAsc
        SortDropDownMenuItemId.NAME_DESC -> SortOrder.NameDesc
        SortDropDownMenuItemId.DURATION -> SortOrder.DurationShortest
        SortDropDownMenuItemId.DURATION_DESC -> SortOrder.DurationLongest
    }
}
