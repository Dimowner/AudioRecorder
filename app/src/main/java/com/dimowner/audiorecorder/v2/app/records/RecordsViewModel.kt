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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordsDataSource: RecordsDataSource,
    private val fileDataSource: FileDataSource,
    private val prefs: PrefsV2,
    @ApplicationContext context: Context,
) : ViewModel() {

    val uiState = mutableStateOf(RecordsScreenState())

    init {
        viewModelScope.launch(Dispatchers.IO) {//TODO: Fix hardcoded dispatcher
            val records = recordsDataSource.getActiveRecords()
            withContext(Dispatchers.Main) {
                uiState.value = RecordsScreenState(
                    sortOrder = SortOrder.DateAsc,
                    records = records.map { it.toRecordListItem(context) }
                )
            }
        }
    }

    fun shareActiveRecord() {
        Timber.v("shareActiveRecord")
    }

    fun showActiveRecordInfo() {
        Timber.v("showActiveRecord")
    }

    fun renameActiveRecord() {
        Timber.v("shareActiveRecord")
    }

    fun openActiveRecordWithAnotherApp() {
        Timber.v("shareActiveRecord")
    }

    fun saveActiveRecordAs() {
        Timber.v("shareActiveRecord")
    }

    fun deleteActiveRecord() {
        Timber.v("shareActiveRecord")
    }
}

data class RecordsScreenState(
    val records: List<RecordListItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DateAsc,
)

data class RecordListItem(
    val recordId: Int,
    val name: String,
    val details: String,
    val duration: String,
    val isBookmarked: Boolean
)
