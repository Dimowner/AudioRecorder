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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.Record
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
internal class RecordsViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val prefs: PrefsV2,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context
) : AndroidViewModel(context as Application) {

    private val _state = mutableStateOf(RecordsScreenState())
    val state: State<RecordsScreenState> = _state

    private val _event = MutableSharedFlow<RecordsScreenEvent?>()
    val event: SharedFlow<RecordsScreenEvent?> = _event

    fun init() {
        viewModelScope.launch(ioDispatcher) {
            initState()
        }
    }

    private suspend fun initState() {
        val context: Context = getApplication<Application>().applicationContext
        val records = recordsDataSource.getRecords(
            sortOrder = state.value.sortOrder,
            page = 1,
            pageSize = 100,
            isBookmarked = false,
        )
        val deletedRecordsCount = recordsDataSource.getMovedToRecycleRecordsCount()
        withContext(mainDispatcher) {
            _state.value = RecordsScreenState(
                sortOrder = SortOrder.DateAsc,
                records = records.map { it.toRecordListItem(context) },
                showDeletedRecordsButton = deletedRecordsCount > 0,
                deletedRecordsCount = deletedRecordsCount
            )
        }
    }

    fun updateListWithBookmarks(bookmarksSelected: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val records = recordsDataSource.getRecords(
                sortOrder = state.value.sortOrder,
                page = 1,
                pageSize = 100,
                isBookmarked = bookmarksSelected,
            )
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
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
                    _state.value = _state.value.copy(
                        records = _state.value.records.map {
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

    fun onItemSelect(recordId: Long) {
        prefs.activeRecordId = recordId
    }

    fun updateListWithSortOrder(sortOrderId: SortDropDownMenuItemId) {
        viewModelScope.launch(ioDispatcher) {
            val sortOrder = sortOrderId.toSortOrder()
            val records = recordsDataSource.getRecords(
                sortOrder = sortOrder,
                page = 1,
                pageSize = 100,
                isBookmarked = _state.value.bookmarksSelected,
            )
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
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
        _state.value = _state.value.copy(
            showRenameDialog = true,
            selectedRecord = record
        )
    }

    fun onRenameRecordDismiss() {
        _state.value = _state.value.copy(
            showRenameDialog = false,
        )
    }

    fun renameRecord(recordId: Long, newName: String) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                recordsDataSource.renameRecord(it, newName)
                _state.value = _state.value.copy(
                    showRenameDialog = false,
                    selectedRecord = null
                )
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
        _state.value = _state.value.copy(
            showSaveAsDialog = true,
            selectedRecord = record
        )
    }

    fun onSaveAsDismiss() {
        _state.value = _state.value.copy(
            showSaveAsDialog = false,
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
            _state.value = _state.value.copy(
                showSaveAsDialog = false,
                selectedRecord = null
            )
        }
    }

    fun onMoveToRecycleRecordRequest(record: RecordListItem) {
        _state.value = _state.value.copy(
            showMoveToRecycleDialog = true,
            selectedRecord = record
        )
    }

    fun onMoveToRecycleRecordDismiss() {
        _state.value = _state.value.copy(
            showMoveToRecycleDialog = false,
        )
    }

    fun moveRecordToRecycle(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            if (recordId != -1L && recordsDataSource.moveRecordToRecycle(recordId)) {
                prefs.activeRecordId = -1
                //TODO: Notify active record deleted. Show Toast
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        records = _state.value.records.filter { it.recordId != recordId },
                        showMoveToRecycleDialog = false,
                        showDeletedRecordsButton = true,
                        selectedRecord = null
                    )
                }
            } else {
                //TODO: Show error message
            }
        }
    }

    fun onAction(action: RecordsScreenAction) {
        when (action) {
            RecordsScreenAction.InitRecordsScreen -> init()
            is RecordsScreenAction.UpdateListWithSortOrder -> updateListWithSortOrder(action.sortOrderId)
            is RecordsScreenAction.UpdateListWithBookmarks -> updateListWithBookmarks(action.bookmarksSelected)
            is RecordsScreenAction.BookmarkRecord -> bookmarkRecord(action.recordId, action.addToBookmarks)
            is RecordsScreenAction.OnItemSelect -> onItemSelect(action.recordId)
            is RecordsScreenAction.ShareRecord -> shareRecord(action.recordId)
            is RecordsScreenAction.ShowRecordInfo -> showRecordInfo(action.recordId)
            is RecordsScreenAction.OnRenameRecordRequest -> onRenameRecordRequest(action.record)
            is RecordsScreenAction.OpenRecordWithAnotherApp -> openRecordWithAnotherApp(action.recordId)
            is RecordsScreenAction.OnSaveAsRequest -> onSaveAsRequest(action.record)
            is RecordsScreenAction.OnMoveToRecycleRecordRequest -> onMoveToRecycleRecordRequest(action.record)
            is RecordsScreenAction.MoveRecordToRecycle -> moveRecordToRecycle(action.recordId)
            RecordsScreenAction.OnMoveToRecycleRecordDismiss -> onMoveToRecycleRecordDismiss()
            is RecordsScreenAction.SaveRecordAs -> saveRecordAs(action.recordId)
            RecordsScreenAction.OnSaveAsDismiss -> onSaveAsDismiss()
            is RecordsScreenAction.RenameRecord -> renameRecord(action.recordId, action.newName)
            RecordsScreenAction.OnRenameRecordDismiss -> onRenameRecordDismiss()
        }
    }

    private fun emitEvent(event: RecordsScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }
}

internal data class RecordsScreenState(
    val records: List<RecordListItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DateAsc,
    val bookmarksSelected: Boolean = false,
    val showDeletedRecordsButton: Boolean = false,
    val deletedRecordsCount: Int = 0,

    val showRenameDialog: Boolean = false,
    val showMoveToRecycleDialog: Boolean = false,
    val showSaveAsDialog: Boolean = false,
    val selectedRecord: RecordListItem? = null,
)

internal data class RecordListItem(
    val recordId: Long,
    val name: String,
    val details: String,
    val duration: String,
    val isBookmarked: Boolean
)

internal sealed class RecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : RecordsScreenEvent()
}

internal sealed class RecordsScreenAction {
    data object InitRecordsScreen : RecordsScreenAction()
    data class UpdateListWithSortOrder(val sortOrderId: SortDropDownMenuItemId) : RecordsScreenAction()
    data class UpdateListWithBookmarks(val bookmarksSelected: Boolean) : RecordsScreenAction()
    data class OnItemSelect(val recordId: Long) : RecordsScreenAction()
    data class BookmarkRecord(val recordId: Long, val addToBookmarks: Boolean) : RecordsScreenAction()
    data class ShareRecord(val recordId: Long) : RecordsScreenAction()
    data class ShowRecordInfo(val recordId: Long) : RecordsScreenAction()
    data class OnRenameRecordRequest(val record: RecordListItem) : RecordsScreenAction()
    data class OpenRecordWithAnotherApp(val recordId: Long) : RecordsScreenAction()
    data class OnSaveAsRequest(val record: RecordListItem) : RecordsScreenAction()
    data class OnMoveToRecycleRecordRequest(val record: RecordListItem) : RecordsScreenAction()
    data class MoveRecordToRecycle(val recordId: Long) : RecordsScreenAction()
    data object OnMoveToRecycleRecordDismiss : RecordsScreenAction()
    data class SaveRecordAs(val recordId: Long) : RecordsScreenAction()
    data object OnSaveAsDismiss : RecordsScreenAction()
    data class RenameRecord(val recordId: Long, val newName: String) : RecordsScreenAction()
    data object OnRenameRecordDismiss : RecordsScreenAction()
}

internal fun Record.toRecordListItem(context: Context): RecordListItem {
    return RecordListItem(
        recordId = this.id,
        name = this.name,
        details = this.toInfoCombinedText(context),
        duration =  TimeUtils.formatTimeIntervalHourMinSec2(this.durationMills),
        isBookmarked = this.isBookmarked
    )
}
