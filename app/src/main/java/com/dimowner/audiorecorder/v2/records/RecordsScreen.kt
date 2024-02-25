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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.InfoItem
import com.dimowner.audiorecorder.v2.TitleBar

@Composable
fun RecordsScreen(
    navController: NavHostController,
) {

    val records = listOf(
        RecordDataItem("Name1", "Description1"),
        RecordDataItem("Name2", "Description2"),
        RecordDataItem("Name3", "Description3")
    )

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                stringResource(id = R.string.records),
                onBackPressed = { navController.popBackStack() }
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                InfoItem(stringResource(R.string.rec_name), "Record-15")
                InfoItem(stringResource(R.string.rec_format), "Format m4a")
                Spacer(modifier = Modifier.size(8.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(vertical = 25.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\uD83C\uDF3F  Plants in Cosmetics",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                items(records) { plant ->
                    InfoItem(label = plant.name, value = plant.description)
                }
            }
        }
    }
}

data class RecordDataItem(
    val name: String,
    val description: String,
)
