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

package com.dimowner.audiorecorder.v2.app.deleted

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.Record
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
internal class DeletedRecordsViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context
) : AndroidViewModel(context as Application) {

    val uiState = mutableStateOf(DeletedRecordsScreenState())

    private val _event = MutableSharedFlow<DeletedRecordsScreenEvent?>()
    val event: SharedFlow<DeletedRecordsScreenEvent?> = _event

    init {
        updateState()
    }

    private fun updateState() {
        viewModelScope.launch(ioDispatcher) {
            val records = recordsDataSource.getMovedToRecycleRecords()
            withContext(mainDispatcher) {
                val context: Context = getApplication<Application>().applicationContext
                uiState.value = DeletedRecordsScreenState(
                    records = records.map { it.toDeletedRecordListItem(context) }
                )
            }
        }
    }

    fun deleteAllRecordsFromRecycle() {
        viewModelScope.launch(ioDispatcher) {
            if (recordsDataSource.clearRecycle()) {
                withContext(mainDispatcher) {
                    uiState.value = DeletedRecordsScreenState(
                        records = emptyList()
                    )
                }
            } else {
                //TODO: Show failed to remove records message
                withContext(mainDispatcher) {
                    updateState()
                }
            }
        }
    }

    fun showRecordInfo(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.toRecordInfoState()?.let {
                emitEvent(DeletedRecordsScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun restoreRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            if (recordId != -1L && recordsDataSource.restoreRecordFromRecycle(recordId)) {
                //TODO: Show success message
                withContext(mainDispatcher) {
                    uiState.value = DeletedRecordsScreenState(
                        records = uiState.value.records.filter { it.recordId != recordId }
                    )
                }
            } else {
                //TODO: Show error message
            }
        }
    }

    fun deleteForeverRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            if (recordId != -1L && recordsDataSource.deleteRecordAndFileForever(recordId)) {
                //TODO: Show success message
                withContext(mainDispatcher) {
                    uiState.value = DeletedRecordsScreenState(
                        records = uiState.value.records.filter { it.recordId != recordId }
                    )
                }
            } else {
                //TODO: Show error message
            }
        }
    }

    private fun emitEvent(event: DeletedRecordsScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }
}

internal data class DeletedRecordsScreenState(
    val records: List<DeletedRecordListItem> = emptyList(),
)

internal data class DeletedRecordListItem(
    val recordId: Long,
    val name: String,
    val details: String,
    val duration: String,
    val isBookmarked: Boolean
)

internal sealed class DeletedRecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : DeletedRecordsScreenEvent()
}

internal fun Record.toDeletedRecordListItem(context: Context): DeletedRecordListItem {
    return DeletedRecordListItem(
        recordId = this.id,
        name = this.name,
        details = this.toInfoCombinedText(context),
        duration =  TimeUtils.formatTimeIntervalHourMinSec2(this.durationMills),
        isBookmarked = this.isBookmarked
    )
}
