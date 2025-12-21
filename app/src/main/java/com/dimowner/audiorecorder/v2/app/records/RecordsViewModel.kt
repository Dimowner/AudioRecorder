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
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.audio.player.PlayerContractNew.PlayerCallback
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.audio.RecorderV2
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
    private val audioPlayer: PlayerContractNew.Player,
    private val audioRecorder: RecorderV2,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : AndroidViewModel(context as Application) {

    private val _state = mutableStateOf(RecordsScreenState())
    val state: State<RecordsScreenState> = _state

    private val _event = MutableSharedFlow<RecordsScreenEvent?>()
    val event: SharedFlow<RecordsScreenEvent?> = _event

    private val playerCallback: PlayerCallback = object : PlayerCallback {
        override fun onStartPlay() {
            _state.value = _state.value.copy(
                showRecordPlaybackPanel = true,
            )
        }
        override fun onPlayProgress(mills: Long) {
            //Do nothing
        }
        override fun onPausePlay() {
            //Do nothing
        }
        override fun onSeek(mills: Long) {
            //Do nothing
        }
        override fun onStopPlay() {
            _state.value = _state.value.copy(
                showRecordPlaybackPanel = false,
                activeRecord = null,
            )
        }
        override fun onError(throwable: AppException) {
            //Do nothing
        }
    }

    fun init(showPlayPanel: Boolean) {
        showLoading(true)
        viewModelScope.launch(ioDispatcher) {
            initState(showPlayPanel)
        }
        audioPlayer.addPlayerCallback(playerCallback)
    }

    fun onStop() {
        audioPlayer.removePlayerCallback(playerCallback)
    }

    private suspend fun initState(showPlayPanel: Boolean) {
        val context: Context = getApplication<Application>().applicationContext
        val sortOrder = state.value.sortOrder
        val records = recordsDataSource.getRecords(
            sortOrder = sortOrder,
            page = 1,
            pageSize = 100,
            isBookmarked = false,
        )
        val deletedRecordsCount = recordsDataSource.getMovedToRecycleRecordsCount()
        withContext(mainDispatcher) {
            _state.value = RecordsScreenState(
                sortOrder = sortOrder,
                recordsMap = records.map {
                    it.toRecordListItem(context)
                }.groupRecordsByDate(context, sortOrder),
                showDeletedRecordsButton = deletedRecordsCount > 0,
                deletedRecordsCount = deletedRecordsCount,
                showRecordPlaybackPanel = showPlayPanel,
                isRecording = audioRecorder.isRecording,
                recordedRecordId = prefs.recordedRecordId
            )
            showLoading(false)
        }
    }

    fun updateListWithBookmarks(bookmarksSelected: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val sortOrder = state.value.sortOrder
            val records = recordsDataSource.getRecords(
                sortOrder = sortOrder,
                page = 1,
                pageSize = 100,
                isBookmarked = bookmarksSelected,
            )
            val context = getApplication<Application>().applicationContext
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    recordsMap = records.map {
                        it.toRecordListItem(context)
                    }.groupRecordsByDate(context, sortOrder),
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
                    _state.value = _state.value.updateRecordInMap(recordId) { oldRecord ->
                        oldRecord.copy(isBookmarked = addToBookmarks)
                    }
                }
            }
        }
    }

    fun onItemSelect(record: RecordListItem) {
        multiSelectCancel()
        audioPlayer.stop()
        prefs.activeRecordId = record.recordId
        _state.value = _state.value.copy(
            activeRecord = record
        )
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
            val context = getApplication<Application>().applicationContext
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    recordsMap = records.map {
                        it.toRecordListItem(context)
                    }.groupRecordsByDate(context, sortOrder),
                    sortOrder = sortOrder
                )
            }
        }
    }

    fun shareRecord(recordId: Long) {
        multiSelectCancel()
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
        multiSelectCancel()
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.toRecordInfoState()?.let {
                emitEvent(RecordsScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun onRenameRecordRequest(record: RecordListItem) {
        multiSelectCancel()
        _state.value = _state.value.copy(
            showRenameDialog = true,
            operationSelectedRecord = record
        )
    }

    fun onRenameRecordDismiss() {
        _state.value = _state.value.copy(
            showRenameDialog = false,
        )
    }

    fun renameRecord(recordId: Long, newName: String) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let { record ->
                if (recordsDataSource.renameRecord(record, newName)) {
                    _state.value = _state.value.copy(
                        showRenameDialog = false,
                        operationSelectedRecord = null,
                        recordsMap = _state.value.recordsMap.mapRecordInMap(recordId) { oldRecord ->
                            if (recordId == record.id) {
                                oldRecord.copy(name = newName)
                            } else {
                                oldRecord
                            }
                        }
                    )
                } else {
                    _state.value = _state.value.copy(
                        showRenameDialog = false,
                        operationSelectedRecord = null
                    )
                }
            }
        }
    }

    fun openRecordWithAnotherApp(recordId: Long) {
        multiSelectCancel()
        audioPlayer.stop()
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
        multiSelectCancel()
        _state.value = _state.value.copy(
            showSaveAsDialog = true,
            operationSelectedRecord = record
        )
    }

    fun onSaveAsDismiss() {
        _state.value = _state.value.copy(
            showSaveAsDialog = false,
        )
    }

    fun saveRecordAs(recordId: Long) {
        multiSelectCancel()
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                DownloadService.startNotification(
                    getApplication<Application>().applicationContext,
                    it.path
                )
            }
            _state.value = _state.value.copy(
                showSaveAsDialog = false,
                operationSelectedRecord = null
            )
        }
    }

    fun onMoveToRecycleRecordRequest(record: RecordListItem) {
        multiSelectCancel()
        _state.value = _state.value.copy(
            showMoveToRecycleDialog = true,
            operationSelectedRecord = record
        )
    }

    fun onMoveToRecycleRecordDismiss() {
        _state.value = _state.value.copy(
            showMoveToRecycleDialog = false,
        )
    }

    fun moveRecordToRecycle(recordId: Long) {
        audioPlayer.stop()
        viewModelScope.launch(ioDispatcher) {
            if (recordId != -1L && recordsDataSource.moveRecordToRecycle(recordId)) {
                prefs.activeRecordId = -1
                //TODO: Notify active record deleted. Show Toast
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        recordsMap = _state.value.recordsMap.removeRecordFromMap(recordId),
                        showMoveToRecycleDialog = false,
                        showDeletedRecordsButton = true,
                        operationSelectedRecord = null,
                        activeRecord = null,
                    )
                }
            } else {
                //TODO: Show error message
            }
        }
    }

    private fun showLoading(value: Boolean) {
        _state.value = _state.value.copy(isShowProgress = value)
    }

    fun onAction(action: RecordsScreenAction) {
        when (action) {
            is RecordsScreenAction.InitRecordsScreen -> init(action.showPlayPanel)
            is RecordsScreenAction.OnStopRecordsScreen -> onStop()
            is RecordsScreenAction.UpdateListWithSortOrder -> updateListWithSortOrder(action.sortOrderId)
            is RecordsScreenAction.UpdateListWithBookmarks -> updateListWithBookmarks(action.bookmarksSelected)
            is RecordsScreenAction.BookmarkRecord -> bookmarkRecord(action.recordId, action.addToBookmarks)
            is RecordsScreenAction.OnItemSelect -> onItemSelect(action.record)
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
            is RecordsScreenAction.MultiSelectAddItem -> multiSelectAdd(action.selectedRecord)
            RecordsScreenAction.MultiSelectCancel -> multiSelectCancel()
            is RecordsScreenAction.MultiSelectMoveToRecycle -> multiSelectMoveToRecycle()
            is RecordsScreenAction.MultiSelectMoveToRecycleRequest ->
                multiSelectMoveToRecycleRequest()
            RecordsScreenAction.MultiSelectMoveToRecycleDismiss -> multiSelectMoveToRecycleDismiss()
            is RecordsScreenAction.MultiSelectSaveAs -> multiSelectSaveAs()
            is RecordsScreenAction.MultiSelectSaveAsRequest -> multiSelectSaveAsRequest()
            RecordsScreenAction.MultiSelectSaveAsDismiss -> multiSelectSaveAsDismiss()
            is RecordsScreenAction.MultiSelectShare -> multiSelectShare(action.selectedRecords)
        }
    }

    private fun multiSelectAdd(selected: RecordListItem) {
        audioPlayer.stop()
        val records = _state.value.selectedRecords.toMutableList()
        if (records.contains(selected)) {
           records.remove(selected)
        } else {
            records.add(selected)
        }
        _state.value = _state.value.copy(
            selectedRecords = records,
        )
    }

    private fun multiSelectCancel() {
        _state.value = _state.value.copy(
            selectedRecords = emptyList(),
        )
    }

    private fun multiSelectShare(selectedRecords: List<RecordListItem>) {
        viewModelScope.launch(ioDispatcher) {
            val recordList = recordsDataSource.getRecords(selectedRecords.map { it.recordId })
            if (recordList.isNotEmpty()) {
                withContext(mainDispatcher) {
                    AndroidUtils.shareAudioFiles(
                        getApplication<Application>().applicationContext,
                        recordList.map { it.path }
                    )
                    multiSelectCancel()
                }
            } else {
                //TODO: handle error here
            }
        }
    }

    private fun multiSelectSaveAsRequest() {
        _state.value = _state.value.copy(
            showSaveAsMultipleDialog = true,
        )
    }

    private fun multiSelectSaveAs() {
        viewModelScope.launch(ioDispatcher) {
            val recordList = recordsDataSource.getRecords(state.value.selectedRecords.map { it.recordId })
            if (recordList.isNotEmpty()) {
                withContext(mainDispatcher) {
                    //Download record file with Service
                    DownloadService.startNotification(
                        getApplication<Application>().applicationContext,
                        recordList
                            .map { it.path }
                            .toCollection(ArrayList())
                    )
                    multiSelectCancel()
                    _state.value = _state.value.copy(
                        showSaveAsMultipleDialog = false,
                    )
                }
            } else {
                //TODO: handle error here
            }
        }
    }

    private fun multiSelectMoveToRecycleRequest() {
        _state.value = _state.value.copy(
            showMoveToRecycleMultipleDialog = true,
        )
    }

    private fun multiSelectMoveToRecycleDismiss() {
        _state.value = _state.value.copy(
            showMoveToRecycleMultipleDialog = false,
        )
    }

    private fun multiSelectSaveAsDismiss() {
        _state.value = _state.value.copy(
            showSaveAsMultipleDialog = false,
        )
    }

    private fun multiSelectMoveToRecycle() {
        viewModelScope.launch(ioDispatcher) {
            val deletedCount = recordsDataSource.moveRecordsToRecycle(state.value.selectedRecords.map { it.recordId })
            if (deletedCount > 0) {
                val context: Context = getApplication<Application>().applicationContext
                val sortOrder = state.value.sortOrder
                val records = recordsDataSource.getRecords(
                    sortOrder = sortOrder,
                    page = 1,
                    pageSize = 100,
                    isBookmarked = state.value.bookmarksSelected,
                )
                val deletedRecordsCount = recordsDataSource.getMovedToRecycleRecordsCount()
                withContext(mainDispatcher) {
                    multiSelectCancel()
                    _state.value = _state.value.copy(
                        recordsMap = records.map {
                            it.toRecordListItem(context)
                        }.groupRecordsByDate(context, sortOrder),
                        showDeletedRecordsButton = deletedRecordsCount > 0,
                        deletedRecordsCount = deletedRecordsCount,
                        showMoveToRecycleMultipleDialog = false,
                    )
                }
            } else {
                //TODO: show an error here.
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
    val recordsMap: Map<String, List<RecordListItem>> = emptyMap(),
    val selectedRecords: List<RecordListItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DateDesc,
    val bookmarksSelected: Boolean = false,
    val showDeletedRecordsButton: Boolean = false,
    val showRecordPlaybackPanel: Boolean = false,
    val deletedRecordsCount: Int = 0,
    val isShowProgress: Boolean = false,

    val showRenameDialog: Boolean = false,
    val showMoveToRecycleDialog: Boolean = false,
    val showMoveToRecycleMultipleDialog: Boolean = false,
    val showSaveAsDialog: Boolean = false,
    val showSaveAsMultipleDialog: Boolean = false,
    //A record for which some operation requested (rename, save as, delete)
    val operationSelectedRecord: RecordListItem? = null,
    val activeRecord: RecordListItem? = null,
    val isRecording: Boolean = false,
    val recordedRecordId: Long = -1,
)

data class RecordListItem(
    val recordId: Long,
    val name: String,
    val details: String,
    val duration: String,
    val added: Long,
    val isBookmarked: Boolean
)

internal sealed class RecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : RecordsScreenEvent()
}

internal sealed class RecordsScreenAction {
    data class InitRecordsScreen(val showPlayPanel: Boolean) : RecordsScreenAction()
    data object OnStopRecordsScreen : RecordsScreenAction()
    data class UpdateListWithSortOrder(val sortOrderId: SortDropDownMenuItemId) : RecordsScreenAction()
    data class UpdateListWithBookmarks(val bookmarksSelected: Boolean) : RecordsScreenAction()
    data class OnItemSelect(val record: RecordListItem) : RecordsScreenAction()
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
    data class MultiSelectAddItem(val selectedRecord: RecordListItem) : RecordsScreenAction()
    data object MultiSelectCancel : RecordsScreenAction()
    data class MultiSelectShare(val selectedRecords: List<RecordListItem>) : RecordsScreenAction()
    data object MultiSelectSaveAs : RecordsScreenAction()
    data object MultiSelectSaveAsRequest : RecordsScreenAction()
    data object MultiSelectSaveAsDismiss : RecordsScreenAction()
    data object MultiSelectMoveToRecycle : RecordsScreenAction()
    data object MultiSelectMoveToRecycleRequest : RecordsScreenAction()
    data object MultiSelectMoveToRecycleDismiss : RecordsScreenAction()
}

internal fun Record.toRecordListItem(context: Context): RecordListItem {
    return RecordListItem(
        recordId = this.id,
        name = this.name,
        details = this.toInfoCombinedText(context),
        duration =  TimeUtils.formatTimeIntervalHourMinSec2(this.durationMills),
        added = this.added,
        isBookmarked = this.isBookmarked
    )
}
