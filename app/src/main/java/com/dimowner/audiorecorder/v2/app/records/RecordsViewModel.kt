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
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.audio.player.PlayerContractNew.PlayerCallback
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.isDescriptionFileWriteSupported
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilter
import com.dimowner.audiorecorder.v2.app.records.models.RecordsFilterOptions
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.data.extensions.checkForLostRecords
import com.dimowner.audiorecorder.v2.data.extensions.isContentUri
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.SortOrder
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

const val DEFAULT_PAGE_SIZE = 50

@HiltViewModel
internal class RecordsViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val prefs: PrefsV2,
    private val audioPlayer: PlayerContractNew.Player,
    private val audioRecorderDelegate: AudioRecorderDelegate,
    private val analyticsTracker: AnalyticsTracker,
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

    private var currentPage = 1

    /**
     * The in-flight search request. Every keystroke starts a new one and cancels the previous
     * one, so a slow query for an earlier prefix can never overwrite the results of a later,
     * more specific one.
     */
    private var searchJob: Job? = null

    /**
     * Loads a single page of records honoring [sortOrder], [filter] (including its
     * bookmarked-only dimension) and [searchQuery]. Every list load goes through here so a new
     * query dimension never gets forgotten at one of the call sites.
     */
    private suspend fun fetchRecordsPage(
        page: Int,
        sortOrder: SortOrder,
        filter: RecordsFilter,
        searchQuery: String,
    ): List<Record> {
        return recordsDataSource.getRecords(
            page = page,
            pageSize = DEFAULT_PAGE_SIZE,
            sortOrder = sortOrder,
            filter = filter,
            searchQuery = searchQuery,
        )
    }

    fun onStart(showPlayPanel: Boolean) {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            initState(showPlayPanel)
        }
        audioPlayer.addPlayerCallback(playerCallback)
    }

    fun onStop() {
        audioPlayer.removePlayerCallback(playerCallback)
    }

    private suspend fun initState(showPlayPanel: Boolean) {
        currentPage = 1
        val context: Context = getApplication<Application>().applicationContext
        val sortOrder = state.value.sortOrder
        val filter = state.value.filter
        // Preserved across restarts (e.g. a configuration change) so an open search is not
        // silently reset while the user is typing.
        val isSearchActive = state.value.isSearchActive
        val searchQuery = state.value.searchQuery
        val activeRecordId = prefs.activeRecordId

        // Load pages until the active record is found or there are no more pages.
        // Extra pages are only fetched when the playback panel is visible (i.e. a record is
        // currently playing) AND there is a valid active record ID, because only then are the
        // playNextRecord/playPreviousRecord buttons available and need the record in-memory.
        val allLoadedRecords = mutableListOf<Record>()
        var hasMoreData: Boolean
        var activeRecordFound = !showPlayPanel || activeRecordId <= 0
        while (true) {
            val page = fetchRecordsPage(currentPage, sortOrder, filter, searchQuery)
            allLoadedRecords.addAll(page)
            hasMoreData = page.size >= DEFAULT_PAGE_SIZE
            if (!activeRecordFound && page.any { it.id == activeRecordId }) {
                activeRecordFound = true
            }
            // Stop when found, or when there are no more pages to load.
            if (activeRecordFound || !hasMoreData) break
            currentPage++
        }

        val deletedRecordsCount = recordsDataSource.getMovedToRecycleRecordsCount()
        val filterOptions = recordsDataSource.getFilterOptions()
        val lostRecords = checkForLostRecords(context, allLoadedRecords)
        if (lostRecords.isNotEmpty()) {
            analyticsTracker.trackLostRecordsDetected(count = lostRecords.size)
        }

        val activeRecord: RecordListItem? = if (showPlayPanel && activeRecordId > 0) {
            allLoadedRecords.find { it.id == activeRecordId }?.toRecordListItem(context)
        } else null

        withContext(mainDispatcher) {
            _state.value = RecordsScreenState(
                sortOrder = sortOrder,
                filter = filter,
                filterOptions = filterOptions,
                recordsMap = allLoadedRecords.map {
                    it.toRecordListItem(context)
                }.groupRecordsByDate(context, sortOrder),
                showDeletedRecordsButton = deletedRecordsCount > 0,
                deletedRecordsCount = deletedRecordsCount,
                // Only show the playback panel when there is a real active record to display.
                showRecordPlaybackPanel = showPlayPanel && activeRecord != null,
                isRecording = audioRecorderDelegate.provideAudioRecorder().isRecording,
                recordedRecordId = prefs.recordedRecordId,
                showLostRecordsDialog = lostRecords.isNotEmpty(),
                lostRecords = lostRecords,
                hasMoreData = hasMoreData,
                activeRecord = activeRecord,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
            )
            showLoadingProgress(false)
        }
    }

    fun loadNextPage() {
        if (!state.value.hasMoreData || state.value.isShowLoadingProgress) return
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            currentPage++
            val context: Context = getApplication<Application>().applicationContext
            val sortOrder = state.value.sortOrder
            val searchQuery = state.value.searchQuery
            val newRecords = fetchRecordsPage(
                page = currentPage,
                sortOrder = sortOrder,
                filter = state.value.filter,
                searchQuery = searchQuery,
            )
            withContext(mainDispatcher) {
                // A keystroke may have restarted the list from page 1 while this page was
                // loading; appending it then would mix results of two different queries.
                if (_state.value.searchQuery != searchQuery) {
                    showLoadingProgress(false)
                    return@withContext
                }
                val newRecordsMap = newRecords.map { it.toRecordListItem(context) }
                    .groupRecordsByDate(context, sortOrder)
                val merged = state.value.recordsMap.toMutableMap()
                newRecordsMap.forEach { (key, list) ->
                    merged[key] = (merged[key] ?: emptyList()) + list
                }
                _state.value = _state.value.copy(
                    recordsMap = merged,
                    hasMoreData = newRecords.size >= DEFAULT_PAGE_SIZE,
                    isShowLoadingProgress = false,
                )
            }
        }
    }

    /** Opens the search input in the top bar. The list is left untouched until text is typed. */
    private fun openSearch() {
        if (_state.value.isSearchActive) return
        multiSelectCancel()
        _state.value = _state.value.copy(
            isSearchActive = true,
            searchQuery = "",
            // The filter panel would overlay the search field, so it is closed on entry.
            showFilterPanel = false,
        )
    }

    /** Closes the search input and reloads the unfiltered-by-text list. */
    private fun closeSearch() {
        if (!_state.value.isSearchActive) return
        val hadQuery = _state.value.searchQuery.isNotEmpty()
        searchJob?.cancel()
        _state.value = _state.value.copy(
            isSearchActive = false,
            searchQuery = "",
        )
        if (hadQuery) {
            reloadFirstPage()
        }
    }

    /**
     * Runs a new search for [query]. Called on every keystroke: the previous request is
     * cancelled so only the newest query can write its results into the state.
     */
    private fun updateSearchQuery(query: String) {
        if (_state.value.searchQuery == query) return
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch(ioDispatcher) {
            currentPage = 1
            val sortOrder = _state.value.sortOrder
            val records = fetchRecordsPage(currentPage, sortOrder, _state.value.filter, query)
            val context = getApplication<Application>().applicationContext
            val recordsMap = records.map { it.toRecordListItem(context) }
                .groupRecordsByDate(context, sortOrder)
            withContext(mainDispatcher) {
                // The query may have moved on while this page was loading; only publish results
                // that still match what is in the input field.
                if (_state.value.searchQuery == query) {
                    _state.value = _state.value.copy(
                        recordsMap = recordsMap,
                        hasMoreData = records.size >= DEFAULT_PAGE_SIZE,
                        isShowLoadingProgress = false,
                    )
                }
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
        // Capture panel visibility before stop() synchronously triggers onStopPlay()
        // which would set showRecordPlaybackPanel = false. We restore it afterwards so
        // AnimatedVisibility never sees the brief false→true flicker.
        val wasShowingPanel = _state.value.showRecordPlaybackPanel
        audioPlayer.stop()
        prefs.activeRecordId = record.recordId
        _state.value = _state.value.copy(
            activeRecord = record,
            showRecordPlaybackPanel = wasShowingPanel,
        )
    }

    private fun bookmarkActiveRecord() {
        val record = _state.value.activeRecord ?: return
        val newIsBookmarked = !record.isBookmarked
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(record.recordId)?.let {
                recordsDataSource.updateRecord(it.copy(isBookmarked = newIsBookmarked))
            }
            withContext(mainDispatcher) {
                val updatedRecord = record.copy(isBookmarked = newIsBookmarked)
                _state.value = _state.value.copy(
                    activeRecord = updatedRecord
                ).updateRecordInMap(record.recordId) { oldRecord ->
                    oldRecord.copy(isBookmarked = newIsBookmarked)
                }
            }
        }
    }

    private fun playNextRecord() {
        val allRecords = _state.value.recordsMap.values.flatten()
        val activeRecord = _state.value.activeRecord ?: return
        val currentIndex = allRecords.indexOfFirst { it.recordId == activeRecord.recordId }
        val nextRecord = allRecords.getOrNull(currentIndex + 1) ?: return
        // Capture panel visibility before stop() synchronously triggers onStopPlay()
        // which would set showRecordPlaybackPanel = false, causing an animation glitch.
        val wasShowingPanel = _state.value.showRecordPlaybackPanel
        audioPlayer.stop()
        prefs.activeRecordId = nextRecord.recordId
        _state.value = _state.value.copy(
            activeRecord = nextRecord,
            showRecordPlaybackPanel = wasShowingPanel,
        )
    }

    private fun playPreviousRecord() {
        val allRecords = _state.value.recordsMap.values.flatten()
        val activeRecord = _state.value.activeRecord ?: return
        val currentIndex = allRecords.indexOfFirst { it.recordId == activeRecord.recordId }
        if (currentIndex <= 0) return
        val previousRecord = allRecords.getOrNull(currentIndex - 1) ?: return
        // Capture panel visibility before stop() synchronously triggers onStopPlay()
        // which would set showRecordPlaybackPanel = false, causing an animation glitch.
        val wasShowingPanel = _state.value.showRecordPlaybackPanel
        audioPlayer.stop()
        prefs.activeRecordId = previousRecord.recordId
        _state.value = _state.value.copy(
            activeRecord = previousRecord,
            showRecordPlaybackPanel = wasShowingPanel,
        )
    }

    fun updateListWithSortOrder(sortOrderId: SortDropDownMenuItemId) {
        viewModelScope.launch(ioDispatcher) {
            currentPage = 1
            val sortOrder = sortOrderId.toSortOrder()
            val records = fetchRecordsPage(
                page = currentPage,
                sortOrder = sortOrder,
                filter = _state.value.filter,
                searchQuery = _state.value.searchQuery,
            )
            val context = getApplication<Application>().applicationContext
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    recordsMap = records.map {
                        it.toRecordListItem(context)
                    }.groupRecordsByDate(context, sortOrder),
                    sortOrder = sortOrder,
                    hasMoreData = records.size >= DEFAULT_PAGE_SIZE,
                )
            }
        }
    }

    private fun toggleFilterPanel() {
        _state.value = _state.value.copy(showFilterPanel = !_state.value.showFilterPanel)
    }

    private fun updateFilter(filter: RecordsFilter) {
        _state.value = _state.value.copy(filter = filter)
        reloadFirstPage()
    }

    private fun clearFilter() {
        if (_state.value.filter.isEmpty) return
        _state.value = _state.value.copy(filter = RecordsFilter())
        reloadFirstPage()
    }

    /**
     * Reloads the first page of records honoring the currently active sort order, filter
     * (including bookmarked-only) and search query. Used whenever one of those changes.
     */
    private fun reloadFirstPage() {
        viewModelScope.launch(ioDispatcher) {
            currentPage = 1
            val sortOrder = _state.value.sortOrder
            val records = fetchRecordsPage(
                page = currentPage,
                sortOrder = sortOrder,
                filter = _state.value.filter,
                searchQuery = _state.value.searchQuery,
            )
            val context = getApplication<Application>().applicationContext
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    recordsMap = records.map {
                        it.toRecordListItem(context)
                    }.groupRecordsByDate(context, sortOrder),
                    hasMoreData = records.size >= DEFAULT_PAGE_SIZE,
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
                if (record.path.isContentUri()) {
                    renameSafRecord(record, newName)
                    return@let
                }
                val currentFile = File(record.path)
                // Skip rename if the name hasn't changed
                if (currentFile.nameWithoutExtension == newName) {
                    _state.value = _state.value.copy(
                        showRenameDialog = false,
                        operationSelectedRecord = null
                    )
                    return@let
                }
                // Check if a file with the new name already exists on disk
                val extension = currentFile.extension
                val targetFile = File(currentFile.parentFile, "$newName.$extension")
                if (targetFile.exists() && currentFile.nameWithoutExtension != newName) {
                    val context: Context = getApplication<Application>().applicationContext
                    emitEvent(
                        RecordsScreenEvent.ShowErrorSnack(
                            context.getString(R.string.error_file_exists)
                        )
                    )
                    _state.value = _state.value.copy(
                        showRenameDialog = false,
                        operationSelectedRecord = null
                    )
                } else if (recordsDataSource.renameRecord(record, newName) != null) {
                    val context: Context = getApplication<Application>().applicationContext
                    emitEvent(
                        RecordsScreenEvent.ShowInfoSnack(
                            context.getString(R.string.msg_record_renamed, newName)
                        )
                    )
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

    /**
     * Renames a record stored in a user-selected public directory. A SAF document has no
     * filesystem path to pre-check for collisions; the DocumentsProvider resolves them itself,
     * so the record is shown under the name the file actually got.
     */
    private suspend fun renameSafRecord(record: Record, newName: String) {
        if (record.name == newName) {
            _state.value = _state.value.copy(
                showRenameDialog = false,
                operationSelectedRecord = null
            )
            return
        }
        val context: Context = getApplication<Application>().applicationContext
        val actualName = recordsDataSource.renameRecord(record, newName)
        if (actualName != null) {
            emitEvent(
                RecordsScreenEvent.ShowInfoSnack(
                    context.getString(R.string.msg_record_renamed, actualName)
                )
            )
            _state.value = _state.value.copy(
                showRenameDialog = false,
                operationSelectedRecord = null,
                recordsMap = _state.value.recordsMap.mapRecordInMap(record.id) { oldRecord ->
                    oldRecord.copy(name = actualName)
                }
            )
        } else {
            emitEvent(
                RecordsScreenEvent.ShowErrorSnack(
                    context.getString(R.string.error_file_exists)
                )
            )
            _state.value = _state.value.copy(
                showRenameDialog = false,
                operationSelectedRecord = null
            )
        }
    }

    fun onEditDescriptionRequest(record: RecordListItem) {
        multiSelectCancel()
        _state.value = _state.value.copy(
            showEditDescriptionDialog = true,
            saveDescriptionToFile = prefs.saveDescriptionToFile,
            operationSelectedRecord = record
        )
    }

    fun onEditDescriptionDismiss() {
        _state.value = _state.value.copy(
            showEditDescriptionDialog = false,
            operationSelectedRecord = null
        )
    }

    fun saveRecordDescription(recordId: Long, description: String, writeToFile: Boolean) {
        // Only remember the checkbox choice for formats that actually support file-write,
        // so a forced-off 3GP save doesn't clobber the default for other formats.
        val isFileWriteSupported = _state.value.operationSelectedRecord
            ?.let { isDescriptionFileWriteSupported(it.format) } ?: true
        if (isFileWriteSupported) {
            prefs.saveDescriptionToFile = writeToFile
        }
        viewModelScope.launch(ioDispatcher) {
            val success = recordsDataSource.updateRecordDescription(recordId, description, writeToFile)
            _state.value = if (success) {
                _state.value.copy(
                    showEditDescriptionDialog = false,
                    operationSelectedRecord = null,
                    recordsMap = _state.value.recordsMap.mapRecordInMap(recordId) { oldRecord ->
                        oldRecord.copy(description = description)
                    }
                )
            } else {
                _state.value.copy(
                    showEditDescriptionDialog = false,
                    operationSelectedRecord = null
                )
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
                if (it.path.isContentUri()) {
                    //The download pipeline requires direct file access; a record in a
                    // user-selected public directory is already reachable by other apps.
                    val context: Context = getApplication<Application>().applicationContext
                    emitEvent(
                        RecordsScreenEvent.ShowInfoSnack(
                            context.getString(R.string.msg_record_already_in_public_dir)
                        )
                    )
                } else {
                    DownloadService.startNotification(
                        getApplication<Application>().applicationContext,
                        it.path
                    )
                }
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

    private fun moveRecordToRecycle(recordId: Long) {
        val activeRecordId = prefs.activeRecordId
        if (audioPlayer.isPlaying() && recordId == activeRecordId) {
            audioPlayer.stop()
        }
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val record = recordsDataSource.getRecord(recordId)
            if (record != null && recordsDataSource.moveRecordToRecycle(recordId)) {
                if (recordId == activeRecordId) {
                    prefs.activeRecordId = -1
                }
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        recordsMap = _state.value.recordsMap.removeRecordFromMap(recordId),
                        showMoveToRecycleDialog = false,
                        showDeletedRecordsButton = true,
                        operationSelectedRecord = null,
                        activeRecord = if (recordId == activeRecordId) null else _state.value.activeRecord,
                        isShowLoadingProgress = false
                    )
                }
                emitEvent(
                    RecordsScreenEvent.RecordMovedToRecycleSnack(
                        recordId,
                        record.name
                    )
                )
            } else {
                val context: Context = getApplication<Application>().applicationContext
                emitEvent(
                    RecordsScreenEvent.ShowErrorSnack(
                        context.getString(R.string.msg_move_to_trash_failed)
                    )
                )

                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
            }
        }
    }

    fun handleRestoreRecordFromRecycle(recordId: Long) {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            if (recordsDataSource.restoreRecordFromRecycle(recordId)) {
                prefs.activeRecordId = recordId
                val record = recordsDataSource.getRecord(recordId)
                showInfoMessage(R.string.msg_recording_restored, record?.name ?: "")

                //Update list state. Put removed record back into the list.
                if (record != null) {
                    withContext(mainDispatcher) {
                        val context: Context = getApplication<Application>().applicationContext
                        _state.value = _state.value.copy(
                            recordsMap = _state.value.recordsMap.addRecordToMap(
                                context,
                                record.toRecordListItem(context),
                                state.value.sortOrder
                            ),
                        )
                    }
                }
            } else {
                showInfoMessage(R.string.msg_operation_failed_generic)

                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
            }
        }
    }

    private fun showLoadingProgress(value: Boolean) {
        _state.value = _state.value.copy(isShowLoadingProgress = value)
    }

    private fun showInfoMessage(@StringRes resId: Int) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            RecordsScreenEvent.ShowInfoSnack(
                context.getString(resId)
            )
        )
    }

    private fun showInfoMessage(@StringRes resId: Int, vararg formatArgs: Any) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            RecordsScreenEvent.ShowInfoSnack(
                context.getString(resId, *formatArgs)
            )
        )
    }

    @SuppressWarnings("CyclomaticComplexMethod")
    fun onAction(action: RecordsScreenAction) {
        when (action) {
            is RecordsScreenAction.OnStartRecordsScreen -> onStart(action.showPlayPanel)
            is RecordsScreenAction.OnStopRecordsScreen -> onStop()
            is RecordsScreenAction.UpdateListWithSortOrder -> updateListWithSortOrder(action.sortOrderId)
            RecordsScreenAction.OpenSearch -> openSearch()
            RecordsScreenAction.CloseSearch -> closeSearch()
            is RecordsScreenAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            RecordsScreenAction.ToggleFilterPanel -> toggleFilterPanel()
            is RecordsScreenAction.UpdateFilter -> updateFilter(action.filter)
            RecordsScreenAction.ClearFilter -> clearFilter()
            is RecordsScreenAction.BookmarkRecord -> bookmarkRecord(action.recordId, action.addToBookmarks)
            RecordsScreenAction.BookmarkActiveRecord -> bookmarkActiveRecord()
            RecordsScreenAction.PlayNextRecord -> playNextRecord()
            RecordsScreenAction.PlayPreviousRecord -> playPreviousRecord()
            is RecordsScreenAction.OnItemSelect -> onItemSelect(action.record)
            is RecordsScreenAction.ShareRecord -> shareRecord(action.recordId)
            is RecordsScreenAction.ShowRecordInfo -> showRecordInfo(action.recordId)
            is RecordsScreenAction.OnRenameRecordRequest -> onRenameRecordRequest(action.record)
            is RecordsScreenAction.OpenRecordWithAnotherApp -> openRecordWithAnotherApp(action.recordId)
            is RecordsScreenAction.OnSaveAsRequest -> onSaveAsRequest(action.record)
            is RecordsScreenAction.OnMoveToRecycleRecordRequest -> onMoveToRecycleRecordRequest(action.record)
            is RecordsScreenAction.MoveRecordToRecycle -> moveRecordToRecycle(action.recordId)
            RecordsScreenAction.OnMoveToRecycleRecordDismiss -> onMoveToRecycleRecordDismiss()
            is RecordsScreenAction.RestoreRecordFromRecycle -> handleRestoreRecordFromRecycle(action.recordId)
            is RecordsScreenAction.SaveRecordAs -> saveRecordAs(action.recordId)
            RecordsScreenAction.OnSaveAsDismiss -> onSaveAsDismiss()
            is RecordsScreenAction.RenameRecord -> renameRecord(action.recordId, action.newName)
            RecordsScreenAction.OnRenameRecordDismiss -> onRenameRecordDismiss()
            is RecordsScreenAction.OnEditDescriptionRequest -> onEditDescriptionRequest(action.record)
            is RecordsScreenAction.SaveRecordDescription -> saveRecordDescription(action.recordId, action.description, action.writeToFile)
            RecordsScreenAction.OnEditDescriptionDismiss -> onEditDescriptionDismiss()
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
            RecordsScreenAction.DismissLostRecordsDialog -> dismissLostRecordsDialog()
            RecordsScreenAction.LoadNextPage -> loadNextPage()
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
                val context: Context = getApplication<Application>().applicationContext
                emitEvent(
                    RecordsScreenEvent.ShowErrorSnack(
                        context.getString(R.string.error_unknown)
                    )
                )
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
            //The download pipeline requires direct file access; records in a user-selected
            // public directory are skipped — they are already reachable by other apps.
            val downloadableList = recordList.filter { !it.path.isContentUri() }
            if (downloadableList.size < recordList.size) {
                val context: Context = getApplication<Application>().applicationContext
                emitEvent(
                    RecordsScreenEvent.ShowInfoSnack(
                        context.getString(R.string.msg_record_already_in_public_dir)
                    )
                )
            }
            if (downloadableList.isNotEmpty()) {
                withContext(mainDispatcher) {
                    //Download record file with Service
                    DownloadService.startNotification(
                        getApplication<Application>().applicationContext,
                        downloadableList
                            .map { it.path }
                            .toCollection(ArrayList())
                    )
                    multiSelectCancel()
                    _state.value = _state.value.copy(
                        showSaveAsMultipleDialog = false,
                    )
                }
            } else if (recordList.isNotEmpty()) {
                multiSelectCancel()
                _state.value = _state.value.copy(
                    showSaveAsMultipleDialog = false,
                )
            } else {
                val context: Context = getApplication<Application>().applicationContext
                emitEvent(
                    RecordsScreenEvent.ShowErrorSnack(
                        context.getString(R.string.error_unknown)
                    )
                )
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
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val deletedCount = recordsDataSource.moveRecordsToRecycle(state.value.selectedRecords.map { it.recordId })
            if (deletedCount > 0) {
                currentPage = 1
                val context: Context = getApplication<Application>().applicationContext
                val sortOrder = state.value.sortOrder
                val records = fetchRecordsPage(
                    page = currentPage,
                    sortOrder = sortOrder,
                    filter = state.value.filter,
                    searchQuery = state.value.searchQuery,
                )
                val recordsInRecycleCount = recordsDataSource.getMovedToRecycleRecordsCount()
                val selectedRecords = state.value.selectedRecords
                val isActiveRecordDeleted = selectedRecords.map { it.recordId }.contains(prefs.activeRecordId)
                if (isActiveRecordDeleted) {
                    prefs.activeRecordId = -1
                }
                withContext(mainDispatcher) {
                    multiSelectCancel()
                    _state.value = _state.value.copy(
                        recordsMap = records.map {
                            it.toRecordListItem(context)
                        }.groupRecordsByDate(context, sortOrder),
                        showDeletedRecordsButton = recordsInRecycleCount > 0,
                        deletedRecordsCount = recordsInRecycleCount,
                        showMoveToRecycleMultipleDialog = false,
                        activeRecord = if (isActiveRecordDeleted) null else _state.value.activeRecord,
                        isShowLoadingProgress = false,
                        hasMoreData = records.size >= DEFAULT_PAGE_SIZE,
                    )
                }
                multipleMoveToRecycleSuccessSnack(selectedRecords, deletedCount)
            } else {
                multipleMoveToRecycleFailSnack()
                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
            }
        }
    }

    private fun multipleMoveToRecycleSuccessSnack(selectedRecords: List<RecordListItem>, deletedRecordsCount: Int) {
        if (selectedRecords.size == 1) {
            val record = selectedRecords.first()
            emitEvent(
                RecordsScreenEvent.RecordMovedToRecycleSnack(
                    record.recordId,
                    record.name
                )
            )
        } else {
            emitEvent(
                RecordsScreenEvent.FewRecordsMovedToRecycleSnack(
                    deletedRecordsCount,
                    selectedRecords.size
                )
            )
        }
    }

    private fun multipleMoveToRecycleFailSnack() {
        val context: Context = getApplication<Application>().applicationContext
        if (state.value.selectedRecords.size > 1) {
            emitEvent(
                RecordsScreenEvent.ShowErrorSnack(
                    context.getString(R.string.msg_multiple_move_to_trash_failed)
                )
            )
        } else {
            emitEvent(
                RecordsScreenEvent.ShowErrorSnack(
                    context.getString(R.string.msg_move_to_trash_failed)
                )
            )
        }
    }

    private fun emitEvent(event: RecordsScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    fun dismissLostRecordsDialog() {
        _state.value = _state.value.copy(
            showLostRecordsDialog = false,
            lostRecords = emptyList()
        )
    }
}

data class RecordsScreenState(
    val recordsMap: Map<String, List<RecordListItem>> = emptyMap(),
    val selectedRecords: List<RecordListItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DateDesc,
    val showDeletedRecordsButton: Boolean = false,
    val showRecordPlaybackPanel: Boolean = false,
    val deletedRecordsCount: Int = 0,
    val isShowLoadingProgress: Boolean = false,
    val hasMoreData: Boolean = false,

    val filter: RecordsFilter = RecordsFilter(),
    val filterOptions: RecordsFilterOptions = RecordsFilterOptions(),
    val showFilterPanel: Boolean = false,

    /** True while the top bar shows the search input instead of the title and actions. */
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",

    val showRenameDialog: Boolean = false,
    val showEditDescriptionDialog: Boolean = false,
    val saveDescriptionToFile: Boolean = true,
    val showMoveToRecycleDialog: Boolean = false,
    val showMoveToRecycleMultipleDialog: Boolean = false,
    val showSaveAsDialog: Boolean = false,
    val showSaveAsMultipleDialog: Boolean = false,
    //Lost records
    val showLostRecordsDialog: Boolean = false,
    val lostRecords: List<Record> = emptyList(),
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
    val isBookmarked: Boolean,
    val description: String = "",
    val format: String = ""
)

internal sealed class RecordsScreenEvent {
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : RecordsScreenEvent()
    data class RecordMovedToRecycleSnack(val recordId: Long, val recordName: String) :
        RecordsScreenEvent()
    data class FewRecordsMovedToRecycleSnack(val movedCount: Int, val expectedCount: Int) :
        RecordsScreenEvent()
    data class ShowErrorSnack(val message: String) : RecordsScreenEvent()
    data class ShowInfoSnack(val message: String) : RecordsScreenEvent()
    data object StartPlayback : RecordsScreenEvent()
}

internal sealed class RecordsScreenAction {
    data class OnStartRecordsScreen(val showPlayPanel: Boolean) : RecordsScreenAction()
    data object OnStopRecordsScreen : RecordsScreenAction()
    data class UpdateListWithSortOrder(val sortOrderId: SortDropDownMenuItemId) : RecordsScreenAction()
    data object OpenSearch : RecordsScreenAction()
    data object CloseSearch : RecordsScreenAction()
    data class UpdateSearchQuery(val query: String) : RecordsScreenAction()
    data object ToggleFilterPanel : RecordsScreenAction()
    data class UpdateFilter(val filter: RecordsFilter) : RecordsScreenAction()
    data object ClearFilter : RecordsScreenAction()
    data class OnItemSelect(val record: RecordListItem) : RecordsScreenAction()
    data class BookmarkRecord(val recordId: Long, val addToBookmarks: Boolean) : RecordsScreenAction()
    data object BookmarkActiveRecord : RecordsScreenAction()
    data object PlayNextRecord : RecordsScreenAction()
    data object PlayPreviousRecord : RecordsScreenAction()
    data class ShareRecord(val recordId: Long) : RecordsScreenAction()
    data class ShowRecordInfo(val recordId: Long) : RecordsScreenAction()
    data class OnRenameRecordRequest(val record: RecordListItem) : RecordsScreenAction()
    data class OpenRecordWithAnotherApp(val recordId: Long) : RecordsScreenAction()
    data class OnSaveAsRequest(val record: RecordListItem) : RecordsScreenAction()
    data class OnMoveToRecycleRecordRequest(val record: RecordListItem) : RecordsScreenAction()
    data class MoveRecordToRecycle(val recordId: Long) : RecordsScreenAction()
    data class RestoreRecordFromRecycle(val recordId: Long) : RecordsScreenAction()
    data object OnMoveToRecycleRecordDismiss : RecordsScreenAction()
    data class SaveRecordAs(val recordId: Long) : RecordsScreenAction()
    data object OnSaveAsDismiss : RecordsScreenAction()
    data class RenameRecord(val recordId: Long, val newName: String) : RecordsScreenAction()
    data object OnRenameRecordDismiss : RecordsScreenAction()
    data class OnEditDescriptionRequest(val record: RecordListItem) : RecordsScreenAction()
    data class SaveRecordDescription(
        val recordId: Long,
        val description: String,
        val writeToFile: Boolean,
    ) : RecordsScreenAction()
    data object OnEditDescriptionDismiss : RecordsScreenAction()
    data class MultiSelectAddItem(val selectedRecord: RecordListItem) : RecordsScreenAction()
    data object MultiSelectCancel : RecordsScreenAction()
    data class MultiSelectShare(val selectedRecords: List<RecordListItem>) : RecordsScreenAction()
    data object MultiSelectSaveAs : RecordsScreenAction()
    data object MultiSelectSaveAsRequest : RecordsScreenAction()
    data object MultiSelectSaveAsDismiss : RecordsScreenAction()
    data object MultiSelectMoveToRecycle : RecordsScreenAction()
    data object MultiSelectMoveToRecycleRequest : RecordsScreenAction()
    data object MultiSelectMoveToRecycleDismiss : RecordsScreenAction()
    data object DismissLostRecordsDialog : RecordsScreenAction()
    data object LoadNextPage : RecordsScreenAction()
}

internal fun Record.toRecordListItem(context: Context): RecordListItem {
    return RecordListItem(
        recordId = this.id,
        name = this.name,
        details = this.toInfoCombinedText(context),
        duration =  TimeUtils.formatTimeIntervalHourMinSec2(this.durationMills),
        added = this.added,
        isBookmarked = this.isBookmarked,
        description = this.description,
        format = this.format
    )
}
