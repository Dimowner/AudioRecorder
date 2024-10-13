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

package com.dimowner.audiorecorder.v2.app.home

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
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
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Date
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val fileDataSource: FileDataSource,
    private val prefs: PrefsV2,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : AndroidViewModel(context as Application) {

    private val _state = mutableStateOf(HomeScreenState())
    val state: State<HomeScreenState> = _state

    private val _event = MutableSharedFlow<HomeScreenEvent?>()
    val event: SharedFlow<HomeScreenEvent?> = _event

    fun init() {
        viewModelScope.launch(ioDispatcher) {
            updateState()
        }
    }

    private suspend fun updateState() {
        val context: Context = getApplication<Application>().applicationContext
        val activeRecord = recordsDataSource.getActiveRecord()
        if (activeRecord != null) {
            withContext(mainDispatcher) {
                _state.value = HomeScreenState(
                    startTime = context.getString(R.string.zero_time),
                    endTime = TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.durationMills),
                    time = context.getString(R.string.zero_time),
                    recordName = activeRecord.name,
                    recordInfo = activeRecord.toInfoCombinedText(context),
                    isContextMenuAvailable = true
                )
            }
        } else {
            withContext(mainDispatcher) {
                _state.value = HomeScreenState()
            }
        }
    }

    @SuppressLint("Recycle")
    fun importAudioFile(uri: Uri) {
        val context: Context = getApplication<Application>().applicationContext
        viewModelScope.launch(ioDispatcher) {
            try {
                val parcelFileDescriptor: ParcelFileDescriptor? =
                    context.contentResolver.openFileDescriptor(uri, "r")
                val fileDescriptor = parcelFileDescriptor?.fileDescriptor
                val name: String? = DocumentFile.fromSingleUri(context, uri)?.name
                if (name != null) {
                    val newFile: File = fileDataSource.createRecordFile(name)
                    if (FileUtil.copyFile(fileDescriptor, newFile)) { //TODO: Fix
                        val info = AudioDecoder.readRecordInfo(newFile)

                        //Do 2 step import: 1) Import record with empty waveform.
                        //2) Process and update waveform in background.
                        val record = Record(
                            0,
                            FileUtil.removeFileExtension(newFile.name), //TODO: Fix
                            if (info.duration >= 0) info.duration/1000 else 0,
                            newFile.lastModified(),
                            System.currentTimeMillis(),
                            Long.MAX_VALUE,
                            newFile.absolutePath,
                            info.format,
                            info.size,
                            info.sampleRate,
                            info.channelCount,
                            info.bitrate,
                            isBookmarked = false,
                            isWaveformProcessed = false,
                            isMovedToRecycle = false,
                            IntArray(ARApplication.longWaveformSampleCount),
                        )
                        val id = recordsDataSource.insertRecord(record)
                        prefs.activeRecordId = id
                        updateState()
                    }
                } else {
                    //TODO: Show an error
                }
            } catch (e: SecurityException) {
                Timber.e(e)
            } catch (e: IOException) {
                Timber.e(e)
            } catch (e: OutOfMemoryError) {
                Timber.e(e)
            } catch (e: IllegalStateException) {
                Timber.e(e)
            } catch (ex: CantCreateFileException) {
                Timber.e(ex)
            }
        }
    }

    fun shareActiveRecord() {
        Timber.v("shareActiveRecord")
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                withContext(mainDispatcher) {
                    AndroidUtils.shareAudioFile(
                        getApplication<Application>().applicationContext,
                        activeRecord.path,
                        activeRecord.name,
                        activeRecord.format
                    )
                }
            }
        }
    }

    fun showActiveRecordInfo() {
        Timber.v("showActiveRecord")
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getActiveRecord()?.toRecordInfoState()?.let {
                emitEvent(HomeScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun renameActiveRecord(newName: String) {
        Timber.v("renameActiveRecord newName = $newName")
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                recordsDataSource.renameRecord(activeRecord, newName)
                updateState()
            }
        }
    }

    fun openActiveRecordWithAnotherApp() {
        Timber.v("shareActiveRecord")
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                withContext(mainDispatcher) {
                    AndroidUtils.openAudioFile(
                        getApplication<Application>().applicationContext,
                        activeRecord.path,
                        activeRecord.name
                    )
                }
            }
        }
    }

    fun saveActiveRecordAs() {
        Timber.v("shareActiveRecord")
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                DownloadService.startNotification(
                    getApplication<Application>().applicationContext,
                    activeRecord.path
                )
            }
        }
    }

    fun deleteActiveRecord() {
        Timber.v("deleteActiveRecord")
        viewModelScope.launch(ioDispatcher) {
            val recordId = prefs.activeRecordId
            if (recordId != -1L && recordsDataSource.moveRecordToRecycle(recordId)) {
                prefs.activeRecordId = -1
                //TODO: Notify active record deleted
                updateState()
            } else {
                //TODO: Show error message
            }
        }
    }

    fun onAction(action: HomeScreenAction) {
        when (action) {
            HomeScreenAction.InitHomeScreen -> init()
            is HomeScreenAction.ImportAudioFile -> importAudioFile(action.uri)
            HomeScreenAction.ShareActiveRecord -> shareActiveRecord()
            HomeScreenAction.ShowActiveRecordInfo -> showActiveRecordInfo()
            HomeScreenAction.OpenActiveRecordWithAnotherApp -> openActiveRecordWithAnotherApp()
            HomeScreenAction.DeleteActiveRecord -> deleteActiveRecord()
            HomeScreenAction.SaveActiveRecordAs -> saveActiveRecordAs()
            is HomeScreenAction.RenameActiveRecord -> renameActiveRecord(action.newName)
        }
    }

    private fun emitEvent(event: HomeScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }
}

data class HomeScreenState(
    val startTime: String = "",
    val endTime: String = "",
    val time: String = "",
    val recordName: String = "",
    val recordInfo: String = "",
    val isContextMenuAvailable: Boolean = false,
    val isStopRecordingButtonAvailable: Boolean = false,
)

internal sealed class HomeScreenAction {
    data object InitHomeScreen : HomeScreenAction()
    data class ImportAudioFile(val uri: Uri) : HomeScreenAction()
    data object ShareActiveRecord : HomeScreenAction()
    data object ShowActiveRecordInfo : HomeScreenAction()
    data object OpenActiveRecordWithAnotherApp : HomeScreenAction()
    data object DeleteActiveRecord : HomeScreenAction()
    data object SaveActiveRecordAs : HomeScreenAction()
    data class RenameActiveRecord(val newName: String) : HomeScreenAction()
}

sealed class HomeScreenEvent {
    data object ShowImportErrorError : HomeScreenEvent()
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : HomeScreenEvent()
}
