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
package com.dimowner.audiorecorder.v2.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

@Composable
fun HomeScreen(
    navController: NavHostController,
    showRecordsScreen: () -> Unit,
    showSettingsScreen: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                onImportClick = {

                },
                onMoreClick = {

                })

            Spacer(modifier = Modifier
                .weight(1f)
                .wrapContentHeight())
            TimePanel(
                "Record-14",
                "1.2Mb, M4a, 44.1kHz",
                "02:23",
                "00:00",
                "05:53",
                onRenameClick = {}
            )
            BottomBar(
                onSettingsClick = { showSettingsScreen() },
                onRecordsListClick = { showRecordsScreen() },
                onRecordingClick = {},
                onStopRecordingClick = {},
                onDeleteRecordingClick = {},
            )
            Spacer(modifier = Modifier
                .fillMaxWidth()
                .height(12.dp))
        }
    }
}

@Preview
@Composable
fun UserInputScreenPreview() {
    HomeScreen(rememberNavController(), {}, {})
}
