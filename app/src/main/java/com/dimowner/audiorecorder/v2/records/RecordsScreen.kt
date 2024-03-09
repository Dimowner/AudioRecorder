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
package com.dimowner.audiorecorder.v2.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.dimowner.audiorecorder.R

@Composable
fun RecordsScreen(
    navController: NavHostController,
) {

    val records = listOf(
        RecordDataItem("Name1", "Description1", "1:01"),
        RecordDataItem("Name2", "Description2", "2:02"),
        RecordDataItem("Name3", "Description3", "2:03"),
        RecordDataItem("Name4", "Description4", "2:04"),
        RecordDataItem("Name5", "Description5", "2:05"),
        RecordDataItem("Name6", "Description6", "2:06"),
        RecordDataItem("Name7", "Description7", "2:07"),
        RecordDataItem("Name8", "Description8", "2:08"),
        RecordDataItem("Name9", "Description9", "2:09"),
        RecordDataItem("Name10", "Description10", "2:10"),
        RecordDataItem("Name11", "Description11", "2:11"),
        RecordDataItem("Name12", "Description12", "2:12"),
        RecordDataItem("Name13", "Description13", "2:13"),
        RecordDataItem("Name14", "Description14", "2:14"),
    )

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecordsTopBar(
                stringResource(id = R.string.records),
                "Sort by",
                onBackPressed = { navController.popBackStack() },
                onSortItemClick = {},
                onBookmarksClick = {}
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(records) { record ->
                    RecordListItem(
                        name = record.name,
                        details = record.description,
                        duration = record.duration,
                        onClickItem = {},
                        onClickBookmark = {},
                        onClickMenu = {},
                    )
                }
            }
        }
    }
}

data class RecordDataItem(
    val name: String,
    val description: String,
    val duration: String,
)
