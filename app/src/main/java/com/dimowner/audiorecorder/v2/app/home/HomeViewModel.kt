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

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.animation.DecelerateInterpolator
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.AppConstantsV2.RECORDING_GRID_STEP
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.app.DecodeServiceListener
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.audio.player.AudioPlaybackService
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.ErrorParser
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.AudioManagerHelper
import com.dimowner.audiorecorder.util.BluetoothDeviceInfo
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.adjustWaveformHeights
import com.dimowner.audiorecorder.v2.app.calculateGridStep
import com.dimowner.audiorecorder.v2.app.calculateScale
import com.dimowner.audiorecorder.v2.app.components.WaveformState
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.isDescriptionFileWriteSupported
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.audio.AudioRecordingService
import com.dimowner.audiorecorder.v2.audio.AudioRecordingServiceEvent
import com.dimowner.audiorecorder.v2.audio.RecordingServiceState
import com.dimowner.audiorecorder.v2.audio.RecordingState
import com.dimowner.audiorecorder.v2.audio.readDescription
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.extensions.isLostRecord
import com.dimowner.audiorecorder.v2.data.extensions.copyFile
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
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
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val ANIMATION_DURATION = 330L //mills.
private const val RECORDING_PROGRESS_UPDATE_INTERVAL = 1000L //mills.

@SuppressWarnings("LongParameterList")
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val fileDataSource: FileDataSource,
    private val prefs: PrefsV2,
    private val audioPlayer: PlayerContractNew.Player,
    private val audioManagerHelper: AudioManagerHelper,
    private val analyticsTracker: AnalyticsTracker,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : AndroidViewModel(context as Application) {

    private var recordingStateJob: Job? = null
    private var recordingEventJob: Job? = null

    private val _state = mutableStateOf(HomeScreenState())
    val state: State<HomeScreenState> = _state


    private val _event = MutableSharedFlow<HomeScreenEvent?>()
    val event: SharedFlow<HomeScreenEvent?> = _event

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DecodeService.LocalBinder
            val decodeService = binder.getService()
            decodeService.setDecodeListener(object : DecodeServiceListener {
                override fun onStartProcessing() {
                    _state.value = _state.value.copy(isShowRecordProcessing = true)
                }

                override fun onFinishProcessing(recordId: Long, decodedData: IntArray) {
                    _state.value = _state.value.copy(isShowRecordProcessing = false)
                    viewModelScope.launch(ioDispatcher) {
                        if (recordId < 0) return@launch
                        // Only update UI if the decoded record is still the active record
                        if (prefs.activeRecordId == recordId) {
                            updateState()
                        }
                    }
                }
            })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            _state.value = _state.value.copy(isShowRecordProcessing = false)
        }

        override fun onBindingDied(name: ComponentName) {
            _state.value = _state.value.copy(isShowRecordProcessing = false)
        }
    }

    private var playbackService: AudioPlaybackService? = null
    private var isPlaybackServiceBound = false

    private val playbackServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("HomeViewModel onServiceConnected: $name")
            val binder = service as? AudioPlaybackService.ServiceBinder
            playbackService = binder?.getService()
            isPlaybackServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("HomeViewModel onServiceDisconnected: $name")
            playbackService = null
            isPlaybackServiceBound = false
        }
    }

    private var recordingService: AudioRecordingService? = null
    private var isRecordingServiceBound = false

    private val recordingServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("HomeViewModel onServiceConnected: $name")
            val binder = service as? AudioRecordingService.ServiceBinder
            recordingService = binder?.getService()
            isRecordingServiceBound = true
            recordingService?.let { svc ->
                subscribeRecordingServiceState(svc)
                subscribeRecordingServiceEvents(svc)
                // If the service connected while idle but prefs still hold a recording ID,
                // a previous recording was interrupted (e.g. force-kill) — the broken-record
                // check in onStart() was skipped because the service wasn't bound yet.
                // Run it now that we can confirm recording is not actually in progress.
                val svcState = svc.recordingState.value
                if (!svcState.isRecording() && prefs.recordedRecordId >= 0) {
                    viewModelScope.launch(ioDispatcher) {
                        checkForBrokenRecords()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("HomeViewModel onServiceDisconnected: $name")
            onRecordingServiceDisconnect()
        }
    }

    init {
        bindPlaybackService()
        bindRecordingService()
        subscribePlayerUpdates()

        // Register AudioManagerHelper and subscribe to Bluetooth mic state
        audioManagerHelper.register()
        viewModelScope.launch {
            audioManagerHelper.bluetoothMicState.collect { bluetoothState ->
                _state.value = _state.value.copy(
                    isBluetoothMicAvailable = bluetoothState.isAvailable,
                    isBluetoothMicEnabled = bluetoothState.isEnabled,
                    bluetoothDeviceName = bluetoothState.deviceName,
                    connectedBluetoothDevices = bluetoothState.connectedDevices,
                    selectedBluetoothDevice = bluetoothState.selectedDevice
                )
            }
        }
    }

    private fun bindPlaybackService() {
        val context: Context = getApplication<Application>().applicationContext
        val intent = Intent(context, AudioPlaybackService::class.java)
        context.bindService(intent, playbackServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindPlaybackService() {
        if (isPlaybackServiceBound) {
            val context: Context = getApplication<Application>().applicationContext
            context.unbindService(playbackServiceConnection)
        }
        playbackService = null
        isPlaybackServiceBound = false
    }

    private fun bindRecordingService() {
        val context: Context = getApplication<Application>().applicationContext
        val intent = Intent(context, AudioRecordingService::class.java)
        context.bindService(intent, recordingServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindRecordingService() {
        if (isRecordingServiceBound) {
            val context: Context = getApplication<Application>().applicationContext
            context.unbindService(recordingServiceConnection)
            isRecordingServiceBound = false
        }
        onRecordingServiceDisconnect()
    }

    private fun onRecordingServiceDisconnect() {
        recordingStateJob?.cancel()
        recordingStateJob = null
        recordingEventJob?.cancel()
        recordingEventJob = null
        recordingService = null
        isRecordingServiceBound = false
    }

    private fun subscribeRecordingServiceState(service: AudioRecordingService) {
        recordingStateJob?.cancel()
        recordingStateJob = viewModelScope.launch {
            val context: Context = getApplication<Application>().applicationContext
            var lastProgressUpdate = 0L
            service.recordingState.collect { recState ->
                if (recState.isPaused()) {
                    // Restore live waveform data from the service so the UI reflects the
                    // actual recording position when re-binding after a paused recording.
                    val pausedWaveformState = if (recState.durationMills > 0) {
                        _state.value.waveformState.copy(
                            waveformData = recState.amplitudes,
                            durationSample = recState.totalSampleCount,
                            durationMills = recState.durationMills,
                            progressMills = recState.durationMills,
                            widthScale = recState.widthScale,
                            gridStepMills = RECORDING_GRID_STEP,
                            isRecording = true,
                            waveformDataOffset = recState.waveformDataOffset,
                        )
                    } else {
                        _state.value.waveformState.copy(isRecording = true)
                    }
                    _state.value = state.value.copy(
                        bottomBarState = BottomBarState.PAUSED,
                        recordName = context.getString(R.string.recording_paused),
                        keepScreenOn = false,
                        time = if (recState.durationMills > 0)
                            TimeUtils.formatTimeIntervalHourMinSec2(recState.durationMills)
                        else _state.value.time,
                        isShowWaveform = recState.durationMills > 0 || _state.value.isShowWaveform,
                        waveformState = pausedWaveformState,
                    )
                } else if (recState.isRecording()) {
                    when (recState.recordingState) {
                        RecordingState.STARTED -> {
                            // Recording just started – initialise UI
                            lastProgressUpdate = 0L
                            _state.value = state.value.copy(
                                bottomBarState = BottomBarState.RECORDING,
                                waveformState = WaveformState(isRecording = true),
                                isShowWaveform = true,
                                startTime = "",
                                endTime = "",
                                recordName = context.getString(R.string.recording_progress),
                                recordDescription = "",
                                keepScreenOn = prefs.isKeepScreenOn,
                            )
                            withContext(ioDispatcher) {
                                recordsDataSource.getRecord(prefs.recordedRecordId)?.let {
                                    _state.value = state.value.copy(
                                        recordInfo = it.toInfoCombinedText(context)
                                    )
                                }
                            }
                        }
                        RecordingState.RESUMED -> {
                            // Recording resumed from pause – update BottomBar state without resetting waveform
                            _state.value = _state.value.copy(
                                bottomBarState = BottomBarState.RECORDING,
                                recordName = context.getString(R.string.recording_progress),
                                keepScreenOn = prefs.isKeepScreenOn,
                            )
                        }
                        else -> {
                            _state.value = state.value.copy(
                                bottomBarState = BottomBarState.RECORDING,
                            )
                        }
                    }

                    // Update waveform on every progress tick
                    if (recState.durationMills > 0) {
                        _state.value = _state.value.copy(
                            isShowWaveform = true,
                            showPause = false,
                            showStop = false,
                            time = TimeUtils.formatTimeIntervalHourMinSec2(recState.durationMills),
                            waveformState = _state.value.waveformState.copy(
                                waveformData = recState.amplitudes,
                                durationSample = recState.totalSampleCount,
                                durationMills = recState.durationMills,
                                progressMills = recState.durationMills,
                                widthScale = recState.widthScale,
                                gridStepMills = RECORDING_GRID_STEP,
                                isRecording = true,
                                waveformDataOffset = recState.waveformDataOffset,
                            )
                        )
                    }

                    // Refresh record info (file size, etc.) at a slower interval to avoid IO churn
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= RECORDING_PROGRESS_UPDATE_INTERVAL) {
                        lastProgressUpdate = now
                        withContext(ioDispatcher) {
                            updateRecordingProgressInfo()
                        }
                    }
                } else {
                    // Covers STOPPED and IDLE (e.g. after stopForegroundService resets state,
                    // or when STOPPED is skipped because the recorded record couldn't be loaded).
                    // Always reconcile the UI to READY_TO_START_RECORDING so a late PROGRESS
                    // event can't leave the BottomBar stuck in RECORDING.
                    if (_state.value.bottomBarState != BottomBarState.READY_TO_START_RECORDING) {
                        _state.value = _state.value.copy(
                            waveformState = _state.value.waveformState.copy(
                                isRecording = false,
                                waveformDataOffset = 0,
                            ),
                            bottomBarState = BottomBarState.READY_TO_START_RECORDING,
                            keepScreenOn = false,
                        )
                    }
                }
            }
        }
    }

    private fun subscribeRecordingServiceEvents(service: AudioRecordingService) {
        recordingEventJob?.cancel()
        recordingEventJob = viewModelScope.launch {
            service.event.collect { event ->
                when (event) {
                    is AudioRecordingServiceEvent.RecordingStopped -> {
                        // Primary trigger for post-recording UI update. Using the SharedFlow
                        // event instead of the StateFlow state ensures delivery even when the
                        // rapid STOPPED→IDLE StateFlow transition is conflated and the collector
                        // misses the STOPPED state.
                        handleRecordingStopped(event.recordId, event.recordName)
                    }
                    is AudioRecordingServiceEvent.NewRecordingPartStarted -> {
                        handleNewRecordingPartStarted(event.recordId)
                    }
                    is AudioRecordingServiceEvent.ShowInfoSnack -> {
                        if (!_state.value.isDeleteRecordingProgressRequested) {
                            showInfoMessage(event.message)
                        }
                    }
                    is AudioRecordingServiceEvent.ShowErrorSnack -> {
                        handleError(event.message)
                    }
                    else -> {
                        Timber.d("Unknown Audio Recording Service Event")
                    }
                }
            }
        }
    }

    private fun subscribePlayerUpdates() {
        audioPlayer.addPlayerCallback(callback = object : PlayerContractNew.PlayerCallback {
            override fun onStartPlay() {
                _state.value = _state.value.copy(
                    showPause = true,
                    showStop = true,
                )
            }

            override fun onPlayProgress(mills: Long) {
                if (!_state.value.isSeek) {
                    _state.value = _state.value.copy(
                        waveformState = _state.value.waveformState.copy(
                            progressMills = mills
                        ),
                        progress = millsToProgress(mills, _state.value.waveformState.durationMills),
                        time = TimeUtils.formatTimeIntervalHourMinSec2(mills),
                        showPause = true,
                        showStop = true,
                    )
                }
            }

            override fun onPausePlay() {
                _state.value = _state.value.copy(
                    showPause = false,
                    showStop = true
                )
            }

            override fun onSeek(mills: Long) {
                //Do nothing
            }

            override fun onStopPlay() {
                _state.value = _state.value.copy(
                    showPause = false,
                    showStop = false
                )
                moveToStart()
            }

            override fun onError(throwable: AppException) {
                Timber.e(throwable)
                handleError(throwable)
            }
        })
    }

    private suspend fun handleRecordingStopped(recordedRecordId: Long, recordName: String?) {
        withContext(ioDispatcher) {
            if (recordedRecordId >= 0) {
                if (_state.value.isDeleteRecordingProgressRequested) {
                    moveRecordToRecycle(recordedRecordId, false)
                } else if (prefs.askToRenameAfterRecordingStopped) {
                    updateState()
                    val record = recordsDataSource.getRecord(recordedRecordId)
                    withContext(mainDispatcher) {
                        _state.value = _state.value.copy(
                            recordName = recordName ?: _state.value.recordName,
                            recordDescription = record?.description ?: "",
                            recordFormat = record?.format ?: "",
                            showRenameAfterRecordingDialog = true,
                            keepScreenOn = false,
                        )
                    }
                } else {
                    updateState()
                }
            }
        }
    }

    private fun handleNewRecordingPartStarted(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getRecord(recordId)?.let {
                showInfoMessage(R.string.new_recording_part_started)
            }
        }
    }

    private fun handleError(exception: AppException) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            HomeScreenEvent.ShowErrorSnack(
                context.getString(ErrorParser.parseException(exception))
            )
        )
    }

    private fun handleError(text: String) {
        emitEvent(
            HomeScreenEvent.ShowErrorSnack(text)
        )
    }

    private fun showInfoMessage(text: String) {
        emitEvent(
            HomeScreenEvent.ShowInfoSnack(
                text
            )
        )
    }

    private fun showInfoMessage(@StringRes resId: Int) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            HomeScreenEvent.ShowInfoSnack(
                context.getString(resId)
            )
        )
    }

    private fun showInfoMessage(@StringRes resId: Int, vararg formatArgs: Any) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            HomeScreenEvent.ShowInfoSnack(
                context.getString(resId, *formatArgs)
            )
        )
    }

    fun onStart() {
        // Re-subscribe to recording service flows if the service is already bound
        // but the jobs were cancelled (e.g. after navigating away and back).
        recordingService?.let { service ->
            if (recordingStateJob == null) {
                subscribeRecordingServiceState(service)
            }
            if (recordingEventJob == null) {
                subscribeRecordingServiceEvents(service)
            }
        }

        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            updateState(false)
            // Check for broken records after a potentially interrupted recording
            if (!state.value.isRecording() && prefs.recordedRecordId >= 0) {
                checkForBrokenRecords()
            }
            //Update playback progress if playback service is running and paused.
            playbackService?.let { service ->
                withContext(mainDispatcher) {
                    if (service.isPaused()) {
                        val currentPosition = service.getCurrentProgress()
                        handleSeekProgress(currentPosition)
                        _state.value = _state.value.copy(
                            showPause = false,
                            showStop = true
                        )
                    }
                }
            }
        }

        val context: Context = getApplication<Application>().applicationContext
        val intent = Intent(context, DecodeService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun onStop() {
        recordingStateJob?.cancel()
        recordingStateJob = null
        recordingEventJob?.cancel()
        recordingEventJob = null
    }

    private suspend fun updateRecordingProgressInfo() {
        val context: Context = getApplication<Application>().applicationContext
        val record = recordsDataSource.getRecord(prefs.recordedRecordId)
        record?.let {
            val file = File(record.path)
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    recordInfo = record.copy(size = file.length()).toInfoCombinedText(context)
                )
            }
        }
    }

    private suspend fun updateState(resetPlayProgress: Boolean = true) {
        val context: Context = getApplication<Application>().applicationContext
        val activeRecord = recordsDataSource.getActiveRecord()
        if (activeRecord != null) {
            val lostRecord = if (activeRecord.isLostRecord()) {
                activeRecord
            } else {
                null
            }
            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    waveformState = _state.value.waveformState.copy(
                        widthScale = calculateScale(
                            activeRecord.durationMills,
                            defaultWidthScale = AppConstantsV2.DEFAULT_WIDTH_SCALE
                        ),
                        durationMills = activeRecord.durationMills,
                        progressMills = if (resetPlayProgress) 0L else _state.value.waveformState.progressMills,
                        waveformData = adjustWaveformHeights(activeRecord.amps),
                        durationSample = activeRecord.amps.size,
                        gridStepMills = calculateGridStep(activeRecord.durationMills),
                        isRecording = _state.value.bottomBarState != BottomBarState.READY_TO_START_RECORDING,
                        waveformDataOffset = 0,
                    ),
                    startTime = context.getString(R.string.zero_time),
                    endTime = TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.durationMills),
                    recordName = activeRecord.name,
                    recordDescription = activeRecord.description,
                    recordInfo = activeRecord.toInfoCombinedText(context),
                    isShowWaveform = true,
                    isShowLoadingProgress = false,
                    isDeleteRecordingProgressRequested = false,
                    showLostRecordsDialog = lostRecord != null,
                    lostRecord = lostRecord,
                )
            }
        } else {
            // If the recording service hasn't connected yet (binding is async) but we know
            // a recording session is in progress (recordedRecordId >= 0), skip resetting the UI
            // here. subscribeRecordingServiceState will push the correct PAUSED/RECORDING state
            // as soon as the service connection is established.
            if (recordingService == null && prefs.recordedRecordId >= 0) {
                Timber.d("updateState: recording service not yet bound but recording in progress (recordedRecordId=${prefs.recordedRecordId}), deferring state update")
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowLoadingProgress = false)
                }
                return
            }
            val recState = recordingService?.recordingState?.value
            val isServiceRecording = recState?.isRecording() == true
            val bottomBarState = recState.toBottomBarState()
            if (isServiceRecording) {
                recordsDataSource.getRecord(prefs.recordedRecordId)?.let {
                    val recordInfo = it.toInfoCombinedText(context)
                    val hasWaveformData = recState.durationMills > 0
                    val waveformState = if (hasWaveformData) {
                        WaveformState(
                            waveformData = recState.amplitudes,
                            durationSample = recState.totalSampleCount,
                            durationMills = recState.durationMills,
                            progressMills = recState.durationMills,
                            widthScale = recState.widthScale,
                            gridStepMills = RECORDING_GRID_STEP,
                            isRecording = true,
                            waveformDataOffset = recState.waveformDataOffset,
                        )
                    } else {
                        WaveformState(isRecording = true)
                    }
                    if (bottomBarState == BottomBarState.RECORDING) {
                        withContext(mainDispatcher) {
                            _state.value = state.value.copy(
                                bottomBarState = BottomBarState.RECORDING,
                                waveformState = waveformState,
                                isShowLoadingProgress = false,
                                isShowWaveform = true,
                                startTime = "",
                                endTime = "",
                                time = TimeUtils.formatTimeIntervalHourMinSec2(recState.durationMills),
                                recordInfo = recordInfo,
                                recordName = context.getString(R.string.recording_progress),
                                keepScreenOn = prefs.isKeepScreenOn,
                            )
                        }
                    } else if (bottomBarState == BottomBarState.PAUSED) {
                        withContext(mainDispatcher) {
                            _state.value = state.value.copy(
                                bottomBarState = BottomBarState.PAUSED,
                                waveformState = waveformState,
                                isShowLoadingProgress = false,
                                isShowWaveform = true,
                                startTime = "",
                                endTime = "",
                                time = TimeUtils.formatTimeIntervalHourMinSec2(recState.durationMills),
                                recordInfo = recordInfo,
                                recordName = context.getString(R.string.recording_paused),
                                keepScreenOn = false,
                            )
                        }
                    }
                }
            } else {
                withContext(mainDispatcher) {
                    _state.value = HomeScreenState(
                        bottomBarState = bottomBarState,
                        waveformState = WaveformState()
                    )
                }
            }
        }
    }

    private fun RecordingServiceState?.toBottomBarState(): BottomBarState {
        return if (this?.isPaused() == true) {
            BottomBarState.PAUSED
        } else if (this?.isRecording() == true) {
            BottomBarState.RECORDING
        } else {
            BottomBarState.READY_TO_START_RECORDING
        }
    }

    @SuppressLint("Recycle")
    fun importAudioFile(uri: Uri) {
        val context: Context = getApplication<Application>().applicationContext
        showLoadingProgress(true)
        _state.value = _state.value.copy(isShowImportProgress = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val parcelFileDescriptor: ParcelFileDescriptor? =
                    context.contentResolver.openFileDescriptor(uri, "r")
                val fileDescriptor = parcelFileDescriptor?.fileDescriptor
                val name: String? = DocumentFile.fromSingleUri(context, uri)?.name
                if (name != null) {
                    val newFile: File = fileDataSource.createRecordFile(name)
                    if (fileDescriptor != null && copyFile(fileDescriptor, newFile)) {
                        val info = AudioDecoder.readRecordInfo(newFile)
                        val importedDescription = newFile.readDescription()

                        //Do 2 step import: 1) Import record with empty waveform.
                        //2) Process and update waveform in background.
                        val record = Record(
                            id = 0,
                            name = newFile.nameWithoutExtension,
                            durationMills = if (info.duration >= 0) info.duration / 1000 else 0,
                            created = newFile.lastModified(),
                            added = System.currentTimeMillis(),
                            removed = Long.MAX_VALUE,
                            path = newFile.absolutePath,
                            format = info.format,
                            size = info.size,
                            sampleRate = info.sampleRate,
                            channelCount = info.channelCount,
                            bitrate = info.bitrate,
                            isBookmarked = false,
                            isWaveformProcessed = false,
                            isMovedToRecycle = false,
                            amps = IntArray(ARApplication.longWaveformSampleCount),
                            description = importedDescription,
                        )
                        val id = recordsDataSource.insertRecord(record)
                        withContext(mainDispatcher) {
                            audioPlayer.stop()
                            _state.value = _state.value.copy(isShowImportProgress = false)
                        }
                        prefs.activeRecordId = id
                        updateState()
                        decodeRecord(id, record.path, record.durationMills)
                    }
                } else {
                    withContext(mainDispatcher) {
                        _state.value = _state.value.copy(isShowImportProgress = false)
                    }
                    handleError(context.getString(R.string.error_unable_to_read_sound_file))
                }
            } catch (e: SecurityException) {
                Timber.e(e)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowImportProgress = false)
                }
                handleError(context.getString(R.string.error_permission_denied))
            } catch (e: IOException) {
                Timber.e(e)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowImportProgress = false)
                }
                handleError(context.getString(R.string.error_unable_to_read_sound_file))
            } catch (e: OutOfMemoryError) {
                Timber.e(e)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowImportProgress = false)
                }
                handleError(context.getString(R.string.error_unable_to_read_sound_file))
            } catch (e: IllegalStateException) {
                Timber.e(e)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowImportProgress = false)
                }
                handleError(context.getString(R.string.error_unable_to_read_sound_file))
            } catch (ex: CantCreateFileException) {
                Timber.e(ex)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(isShowImportProgress = false)
                }
                handleError(ex)
            }
        }
    }

    private fun decodeRecord(recordId: Long, path: String, durationMills: Long) {
        DecodeService.startNotificationV2(
            getApplication<Application>().applicationContext,
            recordId,
            path,
            durationMills
        )
    }

    fun shareActiveRecord() {
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
        viewModelScope.launch(ioDispatcher) {
            recordsDataSource.getActiveRecord()?.toRecordInfoState()?.let {
                emitEvent(HomeScreenEvent.RecordInformationEvent(it))
            }
        }
    }

    fun renameActiveRecord(newName: String) {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                performRenameActiveRecord(newName, activeRecord)
                updateState(false)
            }
        }
    }

    private suspend fun performRenameActiveRecord(newName: String, activeRecord: Record) {
        val currentFile = File(activeRecord.path)
        // Skip rename if the name hasn't changed
        if (currentFile.nameWithoutExtension == newName) {
            showLoadingProgress(false)
            return
        }
        // Check if a file with the new name already exists on disk
        val extension = currentFile.extension
        val targetFile = File(currentFile.parentFile, "$newName.$extension")
        if (targetFile.exists() && currentFile.nameWithoutExtension != newName) {
            val context: Context = getApplication<Application>().applicationContext
            emitEvent(
                HomeScreenEvent.ShowErrorSnack(
                    context.getString(R.string.error_file_exists)
                )
            )
            showLoadingProgress(false)
            return
        } else {
            recordsDataSource.renameRecord(activeRecord, newName)
            val context: Context = getApplication<Application>().applicationContext
            emitEvent(
                HomeScreenEvent.ShowInfoSnack(
                    context.getString(R.string.msg_record_renamed, newName)
                )
            )
        }
    }

    private fun updateActiveRecordNameAndDescription(
        newName: String,
        newDescription: String,
        writeToFile: Boolean
    ) {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                performRenameActiveRecord(newName, activeRecord)
                recordsDataSource.updateRecordDescription(activeRecord.id, newDescription, writeToFile)
                updateState(false)
            }
        }
    }

    fun showDescriptionDialog() {
        _state.value = _state.value.copy(
            showDescriptionDialog = true,
            saveDescriptionToFile = prefs.saveDescriptionToFile,
        )
    }

    fun dismissDescriptionDialog() {
        _state.value = _state.value.copy(showDescriptionDialog = false)
    }

    fun saveActiveRecordDescription(description: String, writeToFile: Boolean) {
        val isFileWriteSupported = isDescriptionFileWriteSupported(_state.value.recordFormat)
        if (isFileWriteSupported) {
            prefs.saveDescriptionToFile = writeToFile
        }

        viewModelScope.launch(ioDispatcher) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                recordsDataSource.updateRecordDescription(activeRecord.id, description, writeToFile)
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        showDescriptionDialog = false,
                        recordDescription = description,
                    )
                }
            } else {
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(showDescriptionDialog = false)
                }
            }
        }
    }

    fun openActiveRecordWithAnotherApp() {
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
        moveRecordToRecycle(prefs.activeRecordId)
    }

    private fun moveRecordToRecycle(recordId: Long, showName: Boolean = true) {
        if (audioPlayer.isPlaying()) {
            audioPlayer.stop()
        }
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val record = recordsDataSource.getRecord(recordId)
            if (record != null && recordsDataSource.moveRecordToRecycle(recordId)) {
                prefs.activeRecordId = -1
                updateState()
                emitEvent(
                    HomeScreenEvent.RecordMovedToRecycleSnack(
                        recordId,
                        if (showName) record.name else null
                    )
                )
            } else {
                val context: Context = getApplication<Application>().applicationContext
                emitEvent(
                    HomeScreenEvent.ShowErrorSnack(
                        context.getString(R.string.msg_move_to_trash_failed)
                    )
                )

                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
            }
        }
    }

    fun handleSeekStart() {
        _state.value = _state.value.copy(
            isSeek = true
        )
    }

    fun handleSeekProgress(mills: Long) {
        _state.value = _state.value.copy(
            time = TimeUtils.formatTimeIntervalHourMinSec2(mills),
            progress = millsToProgress(mills, _state.value.waveformState.durationMills),
            waveformState = _state.value.waveformState.copy(
                progressMills = mills
            )
        )
    }

    fun handleSeekEnd(mills: Long) {
        _state.value = _state.value.copy(
            time = TimeUtils.formatTimeIntervalHourMinSec2(mills),
            progress = millsToProgress(mills, _state.value.waveformState.durationMills),
            isSeek = false,
            waveformState = _state.value.waveformState.copy(
                progressMills = mills,
            )
        )
        if (!audioPlayer.isPlaying()) {
            _state.value = _state.value.copy(
                showPause = false,
                showStop = true
            )
        }
        audioPlayer.seek(mills)
    }

    fun handleProgressBarStateChange(value: Float) {
        val mills = (_state.value.waveformState.durationMills * value).toLong()
        _state.value = _state.value.copy(
            time = TimeUtils.formatTimeIntervalHourMinSec2(mills),
            progress = millsToProgress(mills, _state.value.waveformState.durationMills),
            waveformState = _state.value.waveformState.copy(
                progressMills = mills
            )
        )
        audioPlayer.seek(mills)
    }

    suspend fun handlePlayClick() {
        if (!audioPlayer.isPlaying()) {
            val activeRecord = recordsDataSource.getActiveRecord()
            if (activeRecord != null) {
                withContext(mainDispatcher) {
                    //Start playback in Audio Playback Service
                    val context: Context = getApplication<Application>().applicationContext
                    AudioPlaybackService.startServiceForeground(
                        context = context,
                        name = activeRecord.name,
                        path = activeRecord.path,
                        durationMills = activeRecord.durationMills
                    )
                }
            }
        } else {
            Timber.e("Playback did not started because already playing")
        }
    }

    fun handlePlaybackPauseClick() {
        audioPlayer.pause()
    }

    fun handlePlaybackStopClick() {
        audioPlayer.stop()
    }

    // - If is playing, stop playback
    // - Start recording service
    fun handleStartRecordingClick() {
        audioPlayer.stop()
        val context: Context = getApplication<Application>().applicationContext

        // Start the recording service
        AudioRecordingService.startServiceForeground(context)
    }

    fun handlePauseRecordingClick() {
        recordingService?.pauseRecording()
    }

    fun handleResumeRecordingClick() {
        recordingService?.resumeRecording()
    }

    fun handleStopRecordingClick() {
        recordingService?.stopRecording()
        _state.value = state.value.copy(
            waveformState = _state.value.waveformState.copy(
                isRecording = false,
                waveformDataOffset = 0,
            ),
            //TODO: do not change state to READY_TO_START_RECORDING before recording stopped
            bottomBarState = BottomBarState.READY_TO_START_RECORDING,
            keepScreenOn = false,
        )
    }

    fun handleOnDeleteRecordingProgressClick() {
        // Set the flag BEFORE stopping recording so handleRecordingStopped() sees it
        _state.value = state.value.copy(
            isDeleteRecordingProgressRequested = true,
            keepScreenOn = false,
        )
        recordingService?.stopRecording()
    }

    fun handleRestoreRecordFromRecycle(recordId: Long) {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            if (recordsDataSource.restoreRecordFromRecycle(recordId)) {
                prefs.activeRecordId = recordId
                val record = recordsDataSource.getRecord(recordId)
                showInfoMessage(R.string.msg_recording_restored, record?.name ?: "")
                updateState()
            } else {
                showInfoMessage(R.string.msg_operation_failed_generic)

                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
            }
        }
    }

    private fun millsToProgress(mills: Long, duration: Long): Float {
        return if (duration <= 0) {
            0f
        } else {
            mills / duration.toFloat()
        }
    }

    fun moveToStart() {
        val moveAnimator = ValueAnimator.ofObject(
            LongEvaluator(),
            _state.value.waveformState.progressMills,
            0L
        )
        moveAnimator.interpolator = DecelerateInterpolator()
        moveAnimator.duration = ANIMATION_DURATION
        moveAnimator.addUpdateListener { animation: ValueAnimator ->
            val moveValMills = animation.animatedValue as Long
            handleSeekProgress(moveValMills)
        }
        moveAnimator.start()
    }

    fun showLoadingProgress(value: Boolean) {
        _state.value = _state.value.copy(isShowLoadingProgress = value)
    }

    @SuppressWarnings("CyclomaticComplexMethod")
    fun onAction(action: HomeScreenAction) {
        when (action) {
            HomeScreenAction.OnStartHomeScreen -> onStart()
            HomeScreenAction.LoadActiveRecordAndPlay -> {
                viewModelScope.launch(ioDispatcher) {
                    updateState()
                    handlePlayClick()
                }
            }
            HomeScreenAction.OnStopHomeScreen -> onStop()
            is HomeScreenAction.ImportAudioFile -> importAudioFile(action.uri)
            HomeScreenAction.ShareActiveRecord -> shareActiveRecord()
            HomeScreenAction.ShowActiveRecordInfo -> showActiveRecordInfo()
            HomeScreenAction.OpenActiveRecordWithAnotherApp -> openActiveRecordWithAnotherApp()
            HomeScreenAction.DeleteActiveRecord -> deleteActiveRecord()
            HomeScreenAction.SaveActiveRecordAs -> saveActiveRecordAs()
            is HomeScreenAction.RenameActiveRecord -> {
                viewModelScope.launch(mainDispatcher) {
                    renameActiveRecord(action.newName)
                }
            }
            is HomeScreenAction.UpdateActiveRecordNameAndDescription -> {
                updateActiveRecordNameAndDescription(action.newName, action.newDescription, action.writeToFile)
            }
            HomeScreenAction.OnSeekStart -> handleSeekStart()
            is HomeScreenAction.OnSeekProgress -> handleSeekProgress(action.mills)
            is HomeScreenAction.OnSeekEnd -> handleSeekEnd(action.mills)
            is HomeScreenAction.OnProgressBarStateChange -> handleProgressBarStateChange(action.value)
            HomeScreenAction.OnPauseClick -> handlePlaybackPauseClick()
            HomeScreenAction.OnPlayClick -> {
                viewModelScope.launch(ioDispatcher) {
                    handlePlayClick()
                }
            }
            HomeScreenAction.OnStopClick -> handlePlaybackStopClick()
            //Recording
            HomeScreenAction.OnStartRecordingClick -> {
                handleStartRecordingClick()
            }
            HomeScreenAction.OnPauseRecordingClick -> handlePauseRecordingClick()
            HomeScreenAction.OnResumeRecordingClick -> handleResumeRecordingClick()
            HomeScreenAction.OnStopRecordingClick -> handleStopRecordingClick()
            HomeScreenAction.OnDeleteRecordingProgressClick -> handleOnDeleteRecordingProgressClick()
            is HomeScreenAction.RestoreRecordFromRecycle -> handleRestoreRecordFromRecycle(action.recordId)
            is HomeScreenAction.SetBluetoothMicEnabled -> {
                viewModelScope.launch {
                    audioManagerHelper.enableBluetoothMic(action.enabled)
                }
            }
            is HomeScreenAction.SelectBluetoothDevice -> {
                audioManagerHelper.selectBluetoothDevice(action.device)
            }
            HomeScreenAction.DismissLostRecordsDialog -> dismissLostRecordsDialog()
            is HomeScreenAction.DismissRenameAfterRecordingDialog -> {
                dismissRenameAfterRecordingDialog(action.dontAskAgain)
            }
            HomeScreenAction.RestoreBrokenRecord -> restoreBrokenRecord()
            HomeScreenAction.DismissBrokenRecordDialog -> dismissBrokenRecordDialog()
            HomeScreenAction.ShowDescriptionDialog -> showDescriptionDialog()
            is HomeScreenAction.SaveActiveRecordDescription -> saveActiveRecordDescription(action.description, action.writeToFile)
            HomeScreenAction.DismissDescriptionDialog -> dismissDescriptionDialog()
        }
    }

    private fun dismissRenameAfterRecordingDialog(dontAskAgain: Boolean) {
        _state.value = _state.value.copy(showRenameAfterRecordingDialog = false)
        if (dontAskAgain) {
            prefs.askToRenameAfterRecordingStopped = false
        }
    }

    private fun dismissLostRecordsDialog() {
        _state.value = _state.value.copy(
            showLostRecordsDialog = false,
            lostRecord = null
        )
    }

    /**
     * Checks if a previous recording was interrupted (e.g., by device reboot)
     * and shows a dialog to restore or delete the broken record.
     */
    private suspend fun checkForBrokenRecords() {
        // Recording was in progress but the app restarted - recording was interrupted
        withContext(ioDispatcher) {
            // Only exclude the current recording ID when the service is actually alive and
            // recording/paused. If the service was killed (e.g. app force-stopped), the
            // recordedRecordId pref is stale and must not suppress broken-record detection.
            val isServiceActivelyRecording = recordingService?.recordingState?.value?.isRecording() == true
            // Also exclude the current recording ID when the service is not yet bound.
            // This prevents a false-positive broken-record dialog when recording was started
            // from a widget and the app opens while the recording is still in progress —
            // the service binding is async so recordingService may be null even though
            // the service is alive and actively recording.
            // See onServiceConnected: when binding completes as idle it re-runs this check,
            // so genuinely interrupted recordings (force-kill) are still detected.
            val isServiceNotYetBound = recordingService == null && prefs.recordedRecordId >= 0
            val currentRecordingId = if (isServiceActivelyRecording || isServiceNotYetBound) prefs.recordedRecordId else -1L
            val brokenRecords = recordsDataSource.getBrokenRecords()
                .filter { it.id != currentRecordingId }
            if (brokenRecords.isNotEmpty()) {
                // Show the last broken record for restoration
                val brokenRecord = brokenRecords.last()
                Timber.d("Broken record detected: id=${brokenRecord.id}, name=${brokenRecord.name}, path=${brokenRecord.path}")
                analyticsTracker.trackBrokenRecordDetected(
                    format = brokenRecord.format,
                    count = brokenRecords.size,
                )
                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        showBrokenRecordDialog = true,
                        brokenRecord = brokenRecord,
                    )
                }
            }
        }
    }

    private fun restoreBrokenRecord() {
        val brokenRecord = _state.value.brokenRecord ?: return
        dismissBrokenRecordDialog()
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            val context: Context = getApplication<Application>().applicationContext
            val success = recordsDataSource.restoreBrokenRecord(brokenRecord.id)
            if (success) {
                analyticsTracker.trackBrokenRecordRestoreSuccess(format = brokenRecord.format)
                prefs.activeRecordId = brokenRecord.id
                updateState()
                // Trigger waveform decoding for the restored record
                val restoredRecord = recordsDataSource.getRecord(brokenRecord.id)
                restoredRecord?.let {
                    decodeRecord(it.id, it.path, it.durationMills)
                }
                showInfoMessage(
                    context.getString(R.string.msg_broken_record_restored, brokenRecord.name)
                )
            } else {
                analyticsTracker.trackBrokenRecordRestoreFailed(format = brokenRecord.format)
                withContext(mainDispatcher) {
                    showLoadingProgress(false)
                }
                handleError(context.getString(R.string.error_broken_record_restore_failed))
            }
        }
    }

    private fun dismissBrokenRecordDialog() {
        _state.value = _state.value.copy(
            showBrokenRecordDialog = false,
            brokenRecord = null,
        )
    }

    private fun emitEvent(event: HomeScreenEvent) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            audioManagerHelper.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing AudioManagerHelper")
        }
        unbindPlaybackService()
        unbindRecordingService()
    }
}


data class HomeScreenState(
    val waveformState: WaveformState = WaveformState(),
    val startTime: String = "",
    val endTime: String = "",
    val time: String = TimeUtils.formatTimeIntervalHourMinSec2(0),
    //Progress is value between 0 - 1f
    val progress: Float = 0f,
    val recordName: String = "",
    val recordDescription: String = "",
    val recordFormat: String = "",
    val recordInfo: String = "",
    val isShowWaveform: Boolean = false,
    // Indicates loading progress
    val isShowLoadingProgress: Boolean = false,
    // Indicates waveform decoding in progress
    val isShowRecordProcessing: Boolean = false,
    // Indicates audio file import in progress
    val isShowImportProgress: Boolean = false,
    val isStopRecordingButtonAvailable: Boolean = false,
    val bottomBarState: BottomBarState = BottomBarState.READY_TO_START_RECORDING,
    val showPause: Boolean = false,
    val showStop: Boolean = false,
    val isSeek: Boolean = false,
    val isDeleteRecordingProgressRequested: Boolean = false,
    // Bluetooth mic state
    val isBluetoothMicAvailable: Boolean = false,
    val isBluetoothMicEnabled: Boolean = false,
    val bluetoothDeviceName: String? = null,
    val connectedBluetoothDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedBluetoothDevice: BluetoothDeviceInfo? = null,
    // Audio source selection
    val selectedAudioSource: AudioSource = AudioSource.MIC,
    // Lost records
    val showLostRecordsDialog: Boolean = false,
    val lostRecord: Record? = null,
    // Keep screen on during recording
    val keepScreenOn: Boolean = false,
    // Show rename dialog after recording stopped
    val showRenameAfterRecordingDialog: Boolean = false,
    // Show description edit dialog
    val showDescriptionDialog: Boolean = false,
    val saveDescriptionToFile: Boolean = true,
    // Broken record detection and restoration
    val showBrokenRecordDialog: Boolean = false,
    val brokenRecord: Record? = null,
) {
    fun isRecording(): Boolean {
        return this.bottomBarState == BottomBarState.RECORDING || this.bottomBarState == BottomBarState.PAUSED
    }
}

enum class BottomBarState {
    READY_TO_START_RECORDING,
    RECORDING,
    PAUSED,
}

sealed class HomeScreenAction {
    data object OnStartHomeScreen : HomeScreenAction()
    data object LoadActiveRecordAndPlay : HomeScreenAction()
    data object OnStopHomeScreen : HomeScreenAction()
    data class ImportAudioFile(val uri: Uri) : HomeScreenAction()
    data object ShareActiveRecord : HomeScreenAction()
    data object ShowActiveRecordInfo : HomeScreenAction()
    data object OpenActiveRecordWithAnotherApp : HomeScreenAction()
    data object DeleteActiveRecord : HomeScreenAction()
    data class RestoreRecordFromRecycle(val recordId: Long) : HomeScreenAction()
    data object SaveActiveRecordAs : HomeScreenAction()
    data class RenameActiveRecord(val newName: String) : HomeScreenAction()
    data class UpdateActiveRecordNameAndDescription(val newName: String, val newDescription: String, val writeToFile: Boolean) : HomeScreenAction()
    data object OnSeekStart : HomeScreenAction()
    data object OnPlayClick : HomeScreenAction()
    data object OnPauseClick : HomeScreenAction()
    data object OnStopClick : HomeScreenAction()
    data object OnStartRecordingClick : HomeScreenAction()
    data object OnPauseRecordingClick : HomeScreenAction()
    data object OnResumeRecordingClick : HomeScreenAction()
    data object OnStopRecordingClick : HomeScreenAction()
    data object OnDeleteRecordingProgressClick : HomeScreenAction()
    data class OnSeekProgress(val mills: Long) : HomeScreenAction()
    data class OnSeekEnd(val mills: Long) : HomeScreenAction()
    data class OnProgressBarStateChange(val value: Float) : HomeScreenAction()
    data class SetBluetoothMicEnabled(val enabled: Boolean) : HomeScreenAction()
    data class SelectBluetoothDevice(val device: BluetoothDeviceInfo?) : HomeScreenAction()
    data object DismissLostRecordsDialog : HomeScreenAction()
    data class DismissRenameAfterRecordingDialog(val dontAskAgain: Boolean) : HomeScreenAction()
    data object RestoreBrokenRecord : HomeScreenAction()
    data object DismissBrokenRecordDialog : HomeScreenAction()
    data object ShowDescriptionDialog : HomeScreenAction()
    data class SaveActiveRecordDescription(val description: String, val writeToFile: Boolean) : HomeScreenAction()
    data object DismissDescriptionDialog : HomeScreenAction()
}

sealed class HomeScreenEvent {
    data class RecordMovedToRecycleSnack(val recordId: Long, val recordName: String?) :
        HomeScreenEvent()
    data object ShowImportErrorError : HomeScreenEvent()
    data class ShowErrorSnack(val message: String) : HomeScreenEvent()
    data class ShowInfoSnack(val message: String) : HomeScreenEvent()
    data class RecordInformationEvent(val recordInfo: RecordInfoState) : HomeScreenEvent()
}

private class LongEvaluator : TypeEvaluator<Long> {
    override fun evaluate(fraction: Float, startValue: Long, endValue: Long): Long {
        return startValue + ((endValue - startValue) * fraction).toLong()
    }
}