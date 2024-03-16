package com.dimowner.audiorecorder.v2.app.records

import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.DropDownMenuItem
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId

fun getRecordsDroDownMenuItems(): List<DropDownMenuItem<RecordDropDownMenuItemId>> {
    return RecordDropDownMenuItemId.entries.map {
        when (it) {
            RecordDropDownMenuItemId.SHARE -> DropDownMenuItem(
                id = it, textResId = R.string.share, imageResId = R.drawable.ic_share
            )
            RecordDropDownMenuItemId.INFO -> DropDownMenuItem(
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
            SortDropDownMenuItemId.DURATION -> DropDownMenuItem(
                id = it, textResId = R.string.by_duration, imageResId = R.drawable.ic_access_time
            )
            SortDropDownMenuItemId.DURATION_DESC -> DropDownMenuItem(
                id = it, textResId = R.string.by_duration_desc, imageResId = R.drawable.ic_access_time
            )
        }
    }
}