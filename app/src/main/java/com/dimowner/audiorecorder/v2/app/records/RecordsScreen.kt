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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.dimowner.audiorecorder.R

@Composable
fun RecordsScreen(
    navController: NavHostController,
    viewModel: RecordsViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val uiState = viewModel.uiState.value

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecordsTopBar(
                stringResource(id = R.string.records),
                uiState.sortOrder.toText(context),
                onBackPressed = { navController.popBackStack() },
                onSortItemClick = {},
                onBookmarksClick = {}
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(uiState.records) { record ->
                    RecordListItem(
                        name = record.name,
                        details = record.details,
                        duration = record.duration,
                        isBookmarked = record.isBookmarked,
                        onClickItem = {},
                        onClickBookmark = {},
                        onClickMenu = {},
                    )
                }
            }
        }
    }
}
