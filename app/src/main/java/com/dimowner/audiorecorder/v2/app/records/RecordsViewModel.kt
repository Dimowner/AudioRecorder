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

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
//    savedStateHandle: SavedStateHandle,
    private val recordsDataSource: RecordsDataSource,
//    private val fileDataSource: FileDataSource,
    private val prefs: PrefsV2,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context
) : AndroidViewModel(context as Application) {

    val uiState = mutableStateOf(RecordsScreenState())

    private val _event = MutableSharedFlow<RecordsScreenEvent?>()
    val event: SharedFlow<RecordsScreenEvent?> = _event

    init {
        viewModelScope.launch(ioDispatcher) {
            val records = recordsDataSource.getAllRecords()
            withContext(mainDispatcher) {
                uiState.value = RecordsScreenState(
                    sortOrder = SortOrder.DateAsc,
                    records = records.map { it.toRecordListItem(context) }
                )
            }
        }
    }

    fun updateListWithBookmarks(bookmarksSelected: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val records = recordsDataSource.getRecords(
                sortOrder = uiState.value.sortOrder,
                page = 1,
                pageSize = 100,
                isBookmarked = bookmarksSelected,
            )
            withContext(mainDispatcher) {
                uiState.value = uiState.value.copy(
                    records = records.map {
                        it.toRecordListItem(getApplication<Application>().applicationContext)
                    },
                    bookmarksSelected = bookmarksSelected
                )
            }
        }
    }

    fun bookmarkRecord(recordId: Long, addToBookmarks: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                recordsDataSource.updateRecord(it.copy(isBookmarked = addToBookmarks))
            }
            val updated = recordsDataSource.getRecord(recordId)
            if (updated != null) {
                withContext(mainDispatcher) {
                    uiState.value = uiState.value.copy(
                        records = uiState.value.records.map {
                            if (it.recordId == updated.id) {
                                it.copy(isBookmarked = updated.isBookmarked)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }
    }

    fun updateListWithSortOrder(sortOrderId: SortDropDownMenuItemId) {
        viewModelScope.launch(ioDispatcher) {
            val sortOrder = sortOrderId.toSortOrder()
            val records = recordsDataSource.getRecords(
                sortOrder = sortOrder,
                page = 1,
                pageSize = 100,
                isBookmarked = uiState.value.bookmarksSelected,
            )
            withContext(mainDispatcher) {
                uiState.value = uiState.value.copy(
                    records = records.map {
                        it.toRecordListItem(getApplication<Application>().applicationContext)
                    },
                    sortOrder = sortOrder
                )
            }
        }
    }

    fun shareRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val record = recordsDataSource.getRecord(recordId)
            if (record != null) {
                withContext(mainDispatcher) {
                    AndroidUtils.shareAudioFile(
                        getApplication<Application>().applicationContext,
                        record.path,
                        record.name,
                        record.format
                    )
                }
            }
        }
    }

    fun showRecordInfo(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.toRecordInfoState()?.let {
                emitEvent(RecordsScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun onRenameRecordRequest(record: RecordListItem) {
        uiState.value = uiState.value.copy(
            showRenameDialog = true,
            selectedRecord = record
        )
    }

    fun onRenameRecordFinish() {
        uiState.value = uiState.value.copy(
            showRenameDialog = false,
            selectedRecord = null
        )
    }

    fun renameRecord(recordId: Long, newName: String) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                recordsDataSource.renameRecord(it, newName)
//                updateState()
                onRenameRecordFinish()
            }
        }
    }

    fun openRecordWithAnotherApp(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val record = recordsDataSource.getRecord(recordId)
            if (record != null) {
                withContext(mainDispatcher) {
                    AndroidUtils.openAudioFile(
                        getApplication<Application>().applicationContext,
                        record.path,
                        record.name
                    )
                }
            }
        }
    }

    fun onSaveAsRequest(record: RecordListItem) {
        uiState.value = uiState.value.copy(
            showSaveAsDialog = true,
            selectedRecord = record
        )
    }

    fun onSaveAsFinish() {
        uiState.value = uiState.value.copy(
            showSaveAsDialog = false,
            selectedRecord = null
        )
    }

    fun saveRecordAs(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                DownloadService.startNotification(
                    getApplication<Application>().applicationContext,
                    it.path
                )
            }
            onSaveAsFinish()
        }
    }

    fun onDeleteRecordRequest(record: RecordListItem) {
        uiState.value = uiState.value.copy(
            showDeleteDialog = true,
            selectedRecord = record
        )
    }

    fun onDeleteRecordFinish() {
        uiState.value = uiState.value.copy(
            showDeleteDialog = false,
            selectedRecord = null
        )
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            if (recordId != -1L) {
                recordsDataSource.deleteRecord(recordId)
                prefs.activeRecordId = -1
                //TODO: Notify active record deleted
//                updateState()
                onDeleteRecordFinish()
            }
        }
    }

    private fun emitEvent(event: RecordsScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }
}

data class RecordsScreenState(
    val records: List<RecordListItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DateAsc,
    val bookmarksSelected: Boolean = false,

    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showSaveAsDialog: Boolean = false,
    val selectedRecord: RecordListItem? = null,
)

data class RecordListItem(
    val recordId: Long,
    val name: String,
    val details: String,
    val duration: String,
    val isBookmarked: Boolean
)

sealed class RecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : RecordsScreenEvent()
}
