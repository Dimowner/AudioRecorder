/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.v2.app.lostrecords

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
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
internal class LostRecordsViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context
) : AndroidViewModel(context as Application) {

    private val _state = mutableStateOf(LostRecordsScreenState())
    val state: State<LostRecordsScreenState> = _state

    private val _event = MutableSharedFlow<LostRecordsScreenEvent?>()
    val event: SharedFlow<LostRecordsScreenEvent?> = _event

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            if (recordsDataSource.deleteLostRecordForever(recordId)) {
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        records = _state.value.records.filter { it.recordId != recordId }
                    )
                }
            }
        }
    }

    fun deleteAllRecords() {
        viewModelScope.launch(ioDispatcher) {
            val recordIds = _state.value.records.map { it.recordId }
            for (id in recordIds) {
                recordsDataSource.deleteLostRecordForever(id)
            }
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    records = emptyList()
                )
            }
        }
    }

    fun showRecordInfo(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.toRecordInfoState()?.let {
                emitEvent(LostRecordsScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun onAction(action: LostRecordsScreenAction) {
        when (action) {
            is LostRecordsScreenAction.DeleteRecord -> deleteRecord(action.recordId)
            LostRecordsScreenAction.DeleteAllRecords -> deleteAllRecords()
            is LostRecordsScreenAction.ShowRecordInfo -> showRecordInfo(action.recordId)
        }
    }

    private fun emitEvent(event: LostRecordsScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }
}

internal data class LostRecordsScreenState(
    val records: List<LostRecordListItem> = emptyList(),
)

internal data class LostRecordListItem(
    val recordId: Long,
    val name: String,
    val duration: String,
    val path: String,
)

internal sealed class LostRecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : LostRecordsScreenEvent()
}

internal sealed class LostRecordsScreenAction {
    data class DeleteRecord(val recordId: Long) : LostRecordsScreenAction()
    data object DeleteAllRecords : LostRecordsScreenAction()
    data class ShowRecordInfo(val recordId: Long) : LostRecordsScreenAction()
}

internal fun Record.toLostRecordListItem(): LostRecordListItem {
    return LostRecordListItem(
        recordId = this.id,
        name = this.name,
        duration = TimeUtils.formatTimeIntervalHourMinSec2(this.durationMills),
        path = this.path,
    )
}
