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
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.app.DecodeServiceListener
import com.dimowner.audiorecorder.app.DownloadService
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.ErrorParser
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.AudioManagerHelper
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.adjustWaveformHeights
import com.dimowner.audiorecorder.v2.app.calculateGridStep
import com.dimowner.audiorecorder.v2.app.calculateScale
import com.dimowner.audiorecorder.v2.app.components.WaveformState
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.app.toInfoCombinedText
import com.dimowner.audiorecorder.v2.audio.RecorderEvent
import com.dimowner.audiorecorder.v2.audio.RecorderV2
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
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
import javax.inject.Inject

private const val ANIMATION_DURATION = 330L //mills.

@SuppressWarnings("LongParameterList")
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val fileDataSource: FileDataSource,
    private val prefs: PrefsV2,
    private val audioPlayer: PlayerContractNew.Player,
    private val audioRecorder: RecorderV2,
    private val audioManagerHelper: AudioManagerHelper,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : AndroidViewModel(context as Application) {

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
                    //Do nothing
                }

                override fun onFinishProcessing(decodedData: IntArray) {
                    viewModelScope.launch(ioDispatcher) {
                        //TODO: Handle the case when active racord has changed during decoding.
                        recordsDataSource.getActiveRecord()?.let {
                            recordsDataSource.updateRecord(
                                it.copy(
                                    amps = decodedData
                                )
                            )
                        }
                    }
                }
            })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            //Do nothing
        }

        override fun onBindingDied(name: ComponentName) {
            //Do nothing
        }
    }

    init {
        viewModelScope.launch {
            //TODO: these events should be handled in a service
            subscribeRecorderUpdates()
        }
        subscribePlayerUpdates()
        
        // Register AudioManagerHelper and subscribe to Bluetooth mic state
        audioManagerHelper.register()
        viewModelScope.launch {
            audioManagerHelper.bluetoothMicState.collect { bluetoothState ->
                _state.value = _state.value.copy(
                    isBluetoothMicAvailable = bluetoothState.isAvailable,
                    isBluetoothMicEnabled = bluetoothState.isEnabled,
                    bluetoothDeviceName = bluetoothState.deviceName
                )
            }
        }
    }

    private suspend fun subscribeRecorderUpdates() {
        val context: Context = getApplication<Application>().applicationContext
        audioRecorder.subscribeRecorderEvents().collect { event ->
            Timber.d("HomeViewModel audioRecorder: event: $event")
            when (event) {
                is RecorderEvent.OnError -> {
                    handleError(event.exception)
                }

                RecorderEvent.OnStartRecording -> {
                    _state.value = state.value.copy(
                        bottomBarState = BottomBarState.RECORDING,
                        waveformState = WaveformState(),
                        isShowWaveform = false,
                        startTime = "",
                        endTime = "",
                        recordName = context.getString(R.string.recording_progress),
                    )
                    withContext(ioDispatcher) {
                        recordsDataSource.getRecord(prefs.recordedRecordId)?.let {
                            _state.value = state.value.copy(
                                recordInfo = it.toInfoCombinedText(context)
                            )
                        }
                    }
                }
                is RecorderEvent.OnRecordingProgress -> {
                    _state.value = _state.value.copy(
                        time = TimeUtils.formatTimeIntervalHourMinSec2(event.durationMills),
                        showPause = false,
                        showStop = false,
                        isShowWaveform = false
                    )
                }
                RecorderEvent.OnPauseRecording -> {
                    _state.value = state.value.copy(
                        bottomBarState = BottomBarState.PAUSED,
                        recordName = context.getString(R.string.recording_paused),
                    )
                }
                RecorderEvent.OnResumeRecording -> {
                    _state.value = state.value.copy(
                        bottomBarState = BottomBarState.RECORDING,
                        recordName = context.getString(R.string.recording_progress),
                    )
                }
                is RecorderEvent.OnMaxDurationReached -> {
                    handleMaxDurationReached()
                }
                RecorderEvent.OnStopRecording -> {
                    handleRecordingStopped()
                    resetRecordedRecordPartCounter()
                    prefs.recordedRecordBaseName = null
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

    private suspend fun handleRecordingStopped() {
        // - Read recorded file info
        // - Update recorded file duration, size, format, bitrate, sample rate, channel count
        // - Move updated to recycle if requested to delete the record, otherwise set it as active record
        withContext(ioDispatcher) {
            val recordedRecordId = prefs.recordedRecordId
            if (recordedRecordId >= 0) {
                val record = recordsDataSource.getRecord(recordedRecordId)
                if (record != null) {
                    val output = File(record.path)
                    val info = AudioDecoder.readRecordInfo(output);
                    val success = recordsDataSource.updateRecord(
                        record.copy(
                            durationMills = info.duration / 1000,
                            format = info.format,
                            size = info.size,
                            sampleRate = info.sampleRate,
                            channelCount = info.channelCount,
                            bitrate = info.bitrate,
                        )
                    )
                    if (_state.value.isDeleteRecordingProgressRequested) {
                        moveRecordToRecycle(recordedRecordId, false)
                    } else {
                        if (success) {
                            prefs.activeRecordId = recordedRecordId
                            //Record saved successfully
                            showInfoMessage(R.string.msg_recording_saved)
                        } else {
                            //Failed to save record
                            showInfoMessage(R.string.msg_save_recording_failed)
                        }
                        updateState()
                    }
                } else {
                    if (!_state.value.isDeleteRecordingProgressRequested) {
                        //Failed to save record
                        showInfoMessage(R.string.msg_save_recording_failed)
                    }
                    updateState()
                }
                prefs.recordedRecordId = -1
            }
        }
    }

    private fun handleMaxDurationReached() {
        viewModelScope.launch(ioDispatcher) {
            handleRecordingStopped()

            val partCounter = prefs.recordedRecordPartCounter
            recordsDataSource.getActiveRecord()?.let {
                val baseName = prefs.recordedRecordBaseName
                if (baseName != null) {
                    //Rename saved record to record name and part 1 at the end.
                    //Because the first part has base name without part number by default.
                    if (partCounter == 1) {
                        recordsDataSource.renameRecord(it, getPartName(baseName, partCounter))
                        updateState(false)
                    }

                    //Get record part name for the next part.
                    val recordName = getPartName(baseName, partCounter + 1)
                    handleStartRecordingClick(recordName)
                } else {
                    //In case if there something wrong with base record name, just start normal recording.
                    handleStartRecordingClick(getNewRecordName())
                }
            }
        }
        // Load the selected audio source from preferences
        _state.value = _state.value.copy(
            selectedAudioSource = prefs.settingAudioSource
        )
    }

    private fun handleError(exception: AppException) {
        val context: Context = getApplication<Application>().applicationContext
        emitEvent(
            HomeScreenEvent.ShowErrorSnack(
                context.getString(ErrorParser.parseException(exception))
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

    fun init() {
        showLoadingProgress(true)
        viewModelScope.launch(ioDispatcher) {
            updateState(false)
        }

        val context: Context = getApplication<Application>().applicationContext
        val intent = Intent(context, DecodeService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private suspend fun updateState(resetPlayProgress: Boolean = true) {
        val context: Context = getApplication<Application>().applicationContext
        val activeRecord = recordsDataSource.getActiveRecord()
        if (activeRecord != null) {
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
                        gridStepMills = calculateGridStep(activeRecord.durationMills)
                    ),
                    startTime = context.getString(R.string.zero_time),
                    endTime = TimeUtils.formatTimeIntervalHourMinSec2(activeRecord.durationMills),
                    time = context.getString(R.string.zero_time),
                    recordName = activeRecord.name,
                    recordInfo = activeRecord.toInfoCombinedText(context),
                    isContextMenuAvailable = true,
                    isShowWaveform = true,
                    isShowLoadingProgress = false,
                    isDeleteRecordingProgressRequested = false,
                )
            }
        } else {
            val bottomBarState = audioRecorder.toBottomBarState()
            if (audioRecorder.isRecording) {
                recordsDataSource.getRecord(prefs.recordedRecordId)?.let {
                    val recordInfo = it.toInfoCombinedText(context)
                    if (bottomBarState == BottomBarState.RECORDING) {
                        _state.value = state.value.copy(
                            bottomBarState = BottomBarState.RECORDING,
                            waveformState = WaveformState(),
                            isShowLoadingProgress = false,
                            isShowWaveform = false,
                            startTime = "",
                            endTime = "",
                            recordInfo = recordInfo,
                            recordName = context.getString(R.string.recording_progress),
                        )
                    } else if (bottomBarState == BottomBarState.PAUSED) {
                        _state.value = state.value.copy(
                            bottomBarState = BottomBarState.PAUSED,
                            waveformState = WaveformState(),
                            isShowLoadingProgress = false,
                            isShowWaveform = false,
                            startTime = "",
                            endTime = "",
                            recordInfo = recordInfo,
                            recordName = context.getString(R.string.recording_paused),
                        )
                    }
                }
            } else {
                withContext(mainDispatcher) {
                    //isShowProgress = false is default value. So it cancels progress
                    _state.value = HomeScreenState(
                        bottomBarState = bottomBarState
                    )
                }
            }
        }
    }

    private fun RecorderV2.toBottomBarState(): BottomBarState {
        return if (this.isPaused) {
            BottomBarState.PAUSED
        } else if (this.isRecording) {
            BottomBarState.RECORDING
        } else {
            BottomBarState.READY_TO_START_RECORDING
        }
    }

    @SuppressLint("Recycle")
    fun importAudioFile(uri: Uri) {
        val context: Context = getApplication<Application>().applicationContext
        showLoadingProgress(true)
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
                            id = 0,
                            name = FileUtil.removeFileExtension(newFile.name), //TODO: Fix
                            durationMills = if (info.duration >= 0) info.duration / 1000 else 0,
                            created = newFile.lastModified(),
                            added = System.currentTimeMillis(),
                            removed = -1,
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
                        )
                        val id = recordsDataSource.insertRecord(record)
                        withContext(mainDispatcher) {
                            audioPlayer.stop()
                        }
                        prefs.activeRecordId = id
                        updateState()
                        decodeRecord(record.path, record.durationMills)
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

    private fun decodeRecord(path: String, durationMills: Long) {
        DecodeService.startNotificationV2(
            getApplication<Application>().applicationContext,
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
                recordsDataSource.renameRecord(activeRecord, newName)
                updateState(false)
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

    fun handlePlayClick() {
        if (!audioPlayer.isPlaying()) {
            viewModelScope.launch(ioDispatcher) {
                val activeRecord = recordsDataSource.getActiveRecord()
                if (activeRecord != null) {
                    withContext(mainDispatcher) {
                        audioPlayer.play(activeRecord.path)
                    }
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

    // - Has available space
    // - Is already recoding
    // - If is playing, stop playback
    // - Create a record file
    // - Create empty record in the database with created file path
    // - Set it as active record
    // - Start recording
    suspend fun handleStartRecordingClick(recordName: String) {
        withContext(mainDispatcher) {
            audioPlayer.stop()
        }
        val availableTimeSeconds = convertSpaceBytesToTimeInSeconds(
            spaceBytes = fileDataSource.getAvailableSpace(),
            recordingFormat = prefs.settingRecordingFormat,
            sampleRate = prefs.settingSampleRate.value,
            bitrate = prefs.settingBitrate.value,
            channels = prefs.settingChannelCount.value,
        )

        withContext(ioDispatcher) {
            if (availableTimeSeconds > AppConstants.MIN_REMAIN_RECORDING_TIME && !audioRecorder.isRecording) {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.stop()
                }
                val recordFile = fileDataSource.createRecordFile(addExtension(recordName))
                val record = Record(
                    id = 0,
                    name = recordName,
                    durationMills = 0,
                    created = recordFile.lastModified(),
                    added = System.currentTimeMillis(),
                    removed = -1,
                    path = recordFile.absolutePath,
                    format = prefs.settingRecordingFormat.value,
                    size = 0,
                    sampleRate = prefs.settingSampleRate.value,
                    channelCount = prefs.settingChannelCount.value,
                    bitrate = prefs.settingBitrate.value,
                    isBookmarked = false,
                    isWaveformProcessed = false,
                    isMovedToRecycle = false,
                    amps = IntArray(ARApplication.longWaveformSampleCount)
                )
                val id = recordsDataSource.insertRecord(record)
                prefs.activeRecordId = -1
                prefs.recordedRecordId = id

                audioRecorder.startRecording(
                    outputFile = recordFile,
                    channelCount = prefs.settingChannelCount.value,
                    sampleRate = prefs.settingSampleRate.value,
                    bitrate = prefs.settingBitrate.value,
                    maxRecordingDurationMills = prefs.maxRecordingDurationMills,
                    audioSource = _state.value.selectedAudioSource.value,
                )
                incrementRecordedRecordPartCounter()
            }
        }
    }

    fun handlePauseRecordingClick() {
        audioRecorder.pauseRecording()
    }

    fun handleResumeRecordingClick() {
        audioRecorder.resumeRecording()
    }

    fun handleStopRecordingClick() {
        audioRecorder.stopRecording()
        _state.value = state.value.copy(
            waveformState = _state.value.waveformState.copy(isRecording = false),
            bottomBarState = BottomBarState.READY_TO_START_RECORDING
        )
    }

    fun handleOnDeleteRecordingProgressClick() {
        audioRecorder.stopRecording()
        _state.value = state.value.copy(
            isDeleteRecordingProgressRequested = true
        )
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

    fun getNewRecordName(): String {
        val recordName = when (prefs.settingNamingFormat) {
            NameFormat.Record -> {
                prefs.incrementRecordCounter()
                FileUtil.generateRecordNameCounted(prefs.recordCounter)
            }
            NameFormat.Date -> FileUtil.generateRecordNameDateVariant()
            NameFormat.DateUs -> FileUtil.generateRecordNameDateUS()
            NameFormat.DateIso8601 -> FileUtil.generateRecordNameDateISO8601()
            NameFormat.Timestamp -> FileUtil.generateRecordNameMills()
        }

        return recordName
    }

    fun incrementRecordedRecordPartCounter() {
        prefs.recordedRecordPartCounter += 1
    }

    fun resetRecordedRecordPartCounter() {
        prefs.recordedRecordPartCounter = 0
    }

    private fun getPartName(baseName: String, partCounter: Int): String {
        return "${baseName}_$partCounter"
    }

    fun addExtension(name: String): String {
        return FileUtil.addExtension(name, prefs.settingRecordingFormat.value)
    }

    //TODO: This function shouldn't be here
    private fun convertSpaceBytesToTimeInSeconds(
        spaceBytes: Long,
        recordingFormat: RecordingFormat,
        sampleRate: Int,
        bitrate: Int,
        channels: Int
    ): Long {
        return when (recordingFormat) {
            RecordingFormat.ThreeGp -> 1000L * (spaceBytes / (AppConstants.RECORD_ENCODING_BITRATE_12000 / 8))
            RecordingFormat.M4a -> 1000L * (spaceBytes / (bitrate / 8))
            RecordingFormat.Wav -> 1000L * (spaceBytes / (sampleRate * channels * 2))
        }
    }

    @SuppressWarnings("CyclomaticComplexMethod")
    fun onAction(action: HomeScreenAction) {
        when (action) {
            HomeScreenAction.InitHomeScreen -> init()
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
            HomeScreenAction.OnSeekStart -> handleSeekStart()
            is HomeScreenAction.OnSeekProgress -> handleSeekProgress(action.mills)
            is HomeScreenAction.OnSeekEnd -> handleSeekEnd(action.mills)
            is HomeScreenAction.OnProgressBarStateChange -> handleProgressBarStateChange(action.value)
            HomeScreenAction.OnPauseClick -> handlePlaybackPauseClick()
            HomeScreenAction.OnPlayClick -> handlePlayClick()
            HomeScreenAction.OnStopClick -> handlePlaybackStopClick()
            //Recording
            HomeScreenAction.OnStartRecordingClick -> {
                viewModelScope.launch(mainDispatcher) {
                    resetRecordedRecordPartCounter()
                    val recordName = getNewRecordName()
                    handleStartRecordingClick(recordName)
                    prefs.recordedRecordBaseName = recordName
                }
            }
            HomeScreenAction.OnPauseRecordingClick -> handlePauseRecordingClick()
            HomeScreenAction.OnResumeRecordingClick -> handleResumeRecordingClick()
            HomeScreenAction.OnStopRecordingClick -> handleStopRecordingClick()
            HomeScreenAction.OnDeleteRecordingProgressClick -> handleOnDeleteRecordingProgressClick()
            is HomeScreenAction.RestoreRecordFromRecycle -> handleRestoreRecordFromRecycle(action.recordId)
            is HomeScreenAction.SetBluetoothMicEnabled -> {
                audioManagerHelper.enableBluetoothMic(action.enabled)
            }
        }
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
    val recordInfo: String = "",
    val isShowWaveform: Boolean = false,
    // Indicates loading progress
    val isShowLoadingProgress: Boolean = false,
    val isContextMenuAvailable: Boolean = false,
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
    // Audio source selection
    val selectedAudioSource: AudioSource = AudioSource.MIC,
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
    data object InitHomeScreen : HomeScreenAction()
    data class ImportAudioFile(val uri: Uri) : HomeScreenAction()
    data object ShareActiveRecord : HomeScreenAction()
    data object ShowActiveRecordInfo : HomeScreenAction()
    data object OpenActiveRecordWithAnotherApp : HomeScreenAction()
    data object DeleteActiveRecord : HomeScreenAction()
    data class RestoreRecordFromRecycle(val recordId: Long) : HomeScreenAction()
    data object SaveActiveRecordAs : HomeScreenAction()
    data class RenameActiveRecord(val newName: String) : HomeScreenAction()
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