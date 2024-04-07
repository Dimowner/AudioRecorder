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

package com.dimowner.audiorecorder.v2.app.home

import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.DropDownMenuItem

fun getHomeDroDownMenuItems(): List<DropDownMenuItem<HomeDropDownMenuItemId>> {
    return HomeDropDownMenuItemId.entries.map {
        when (it) {
            HomeDropDownMenuItemId.SHARE -> DropDownMenuItem(
                id = it, textResId = R.string.share, imageResId = R.drawable.ic_share
            )
            HomeDropDownMenuItemId.INFORMATION -> DropDownMenuItem(
                id = it, textResId = R.string.info, imageResId = R.drawable.ic_info
            )
            HomeDropDownMenuItemId.RENAME -> DropDownMenuItem(
                id = it, textResId = R.string.rename, imageResId = R.drawable.ic_pencil
            )
            HomeDropDownMenuItemId.OPEN_WITH -> DropDownMenuItem(
                id = it, textResId = R.string.open_with, imageResId = R.drawable.ic_open_with
            )
            HomeDropDownMenuItemId.SAVE_AS -> DropDownMenuItem(
                id = it, textResId = R.string.save_as, imageResId = R.drawable.ic_save_alt
            )
            HomeDropDownMenuItemId.DELETE -> DropDownMenuItem(
                id = it, textResId = R.string.delete, imageResId = R.drawable.ic_delete_forever
            )
        }
    }
}