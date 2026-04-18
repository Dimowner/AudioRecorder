/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.content.res.Resources
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.AppConstantsV2
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.HomeActivity
import com.dimowner.audiorecorder.v2.app.getNewRecordName
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.LinkedList
import javax.inject.Inject

/** Scaling factor applied to raw amplitude samples to visually amplify the recorded waveform. */
private const val WAVEFORM_AMPLITUDE_SCALE = 1.2f

@AndroidEntryPoint
class AudioRecordingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "audio_recording_channel"
        private const val NOTIFICATION_UPDATE_INTERVAL = 5000L // 5 seconds

        private const val ACTION_START_RECORDING = "com.dimowner.audiorecorder.ACTION_START_RECORDING"
        private const val ACTION_PAUSE_RESUME_RECORDING = "com.dimowner.audiorecorder.ACTION_PAUSE_RESUME_RECORDING"
        private const val ACTION_STOP_RECORDING = "com.dimowner.audiorecorder.ACTION_STOP_RECORDING"

        fun startServiceForeground(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = ServiceBinder()

    private lateinit var audioRecorder: RecorderV2

    @Inject
    lateinit var audioRecorderDelegate: AudioRecorderDelegate

    @Inject
    lateinit var recordsDataSource: RecordsDataSource

    @Inject
    lateinit var fileDataSource: FileDataSource

    @Inject
    lateinit var prefs: PrefsV2

    @Inject
    lateinit var recordTagWriter: RecordTagWriter

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceJob = SupervisorJob()
    private val serviceScope by lazy { CoroutineScope(ioDispatcher + serviceJob) }

    private val notificationHandler = Handler(Looper.getMainLooper())
    private var notificationManager: NotificationManager? = null

    private val _recordingState = MutableStateFlow(RecordingServiceState())
    val recordingState: StateFlow<RecordingServiceState> = _recordingState.asStateFlow()

    private val _event = MutableSharedFlow<AudioRecordingServiceEvent?>()
    val event: SharedFlow<AudioRecordingServiceEvent?> = _event

    /**
     * The number of recording amplitude samples that fit in half the waveform view width.
     * Calculated from screen width so the buffer adapts to different screen sizes.
     */
    private val recordingAmplitudeBufferSize: Int = calculateRecordingAmplitudeBufferSize()

    /**
     * Fixed-size sliding-window buffer of recording amplitudes, pre-filled with zeros.
     * When a new amplitude arrives it is appended at the end and the oldest value is removed
     * from the front, creating a moving-waveform effect.
     */
    private val recordingAmplitudes = LinkedList<Int>()

    /** Total number of amplitude samples received during the current recording session. */
    private var totalRecordingSampleCount: Int = 0

    /** Job for the current recorder-events subscription; cancelled before re-subscribing. */
    private var subscriptionJob: Job? = null

    inner class ServiceBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        audioRecorder = audioRecorderDelegate.provideAudioRecorder()
        subscribeRecorderEvents()
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                // Must call startForeground() synchronously before any async work
                // to satisfy the foreground service contract and avoid ANR.
                startForegroundWithNotification()
                serviceScope.launch {
                    val recordName = prefs.settingNamingFormat.getNewRecordName(prefs)
                    resetRecordedRecordPartCounter()
                    prefs.recordedRecordBaseName = recordName
                    handleStartRecording(recordName)
                }
            }
            ACTION_PAUSE_RESUME_RECORDING -> handlePauseResumeAction()
            ACTION_STOP_RECORDING -> handleStopAction()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionJob?.cancel()
        serviceJob.cancel()
        stopNotificationUpdates()
        notificationManager = null
    }

    fun getCurrentProgress(): Long {
        return _recordingState.value.durationMills
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_recording_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_recording_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun subscribeRecorderEvents() {
        subscriptionJob?.cancel()
        subscriptionJob = serviceScope.launch {
            audioRecorder.subscribeRecorderEvents().collect { event ->
                Timber.d("AudioRecordingService: event: $event")
                when (event) {
                    is RecorderEvent.OnStartRecording -> {
                        recordingAmplitudes.clear()
                        totalRecordingSampleCount = 0
                        _recordingState.value = _recordingState.value.copy(
                            recordingState = RecordingState.STARTED,
                            amplitudes = intArrayOf(),
                            totalSampleCount = 0,
                            waveformDataOffset = 0,
                        )
                        startNotificationUpdates()
                        updateNotification()
                    }
                    is RecorderEvent.OnRecordingProgress -> {
                        handleRecordingProgress(event.durationMills, event.amplitude)
                    }
                    is RecorderEvent.OnPauseRecording -> {
                        _recordingState.value = _recordingState.value.copy(
                            recordingState = RecordingState.PAUSED,
                        )
                        updateNotification()
                    }
                    is RecorderEvent.OnResumeRecording -> {
                        _recordingState.value = _recordingState.value.copy(
                            recordingState = RecordingState.RESUMED,
                        )
                        updateNotification()
                    }
                    is RecorderEvent.OnStopRecording -> {
                        handleRecordingStopped()
                    }
                    is RecorderEvent.OnMaxDurationReached -> {
                        handleMaxDurationReachedInternal()
                    }
                    is RecorderEvent.OnError -> {
                        //TODO: handle error???
                        handleRecordingStopped()
                    }
                }
            }
        }
    }

    fun handleRecordingProgress(durationMills: Long, amplitude: Int) {
        _recordingState.value = _recordingState.value.copy(
            recordingState = RecordingState.PROGRESS,
        )
        val state = _recordingState.value
        state.recordingFormat?.let { format ->
            val space = fileDataSource.getAvailableSpace()
            val availableTimeSeconds = convertSpaceBytesToTimeInSeconds(
                spaceBytes = space,
                recordingFormat = format,
                sampleRate = state.sampleRate,
                bitrate = state.bitrate,
                channels = state.channelCount,
            )
            if (availableTimeSeconds < AppConstants.MIN_REMAIN_RECORDING_TIME) {
                //There is running out space on the device.
                //Stop recording before it completely run out.
                audioRecorder.stopRecording()
            }
        }

        // Derive sample count from durationMills so waveform stays in sync with the
        // recorder's clock (the single source of truth) instead of counting timer ticks
        // which drift due to scheduling jitter.
        val newSampleCount =
            (durationMills / AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW).toInt()
        val samplesToAdd = (newSampleCount - totalRecordingSampleCount).coerceAtLeast(0)
        val scaledAmplitude = (amplitude * WAVEFORM_AMPLITUDE_SCALE).toInt()

        // If the timer skipped ticks, fill the gap by repeating the current amplitude
        // so the waveform buffer stays aligned with the time-derived sample count.
        repeat(samplesToAdd) {
            recordingAmplitudes.addLast(scaledAmplitude)
            if (recordingAmplitudes.size > recordingAmplitudeBufferSize) {
                recordingAmplitudes.removeFirst()
            }
        }
        totalRecordingSampleCount = newSampleCount

        val amps = recordingAmplitudes.toIntArray()
        val waveformDataOffset =
            (totalRecordingSampleCount - amps.size).coerceAtLeast(0)
        val widthScale =
            durationMills * (AppConstantsV2.DEFAULT_WIDTH_SCALE / AppConstantsV2.SHORT_RECORD)

        _recordingState.value = _recordingState.value.copy(
            durationMills = durationMills,
            amplitude = amplitude,
            amplitudes = amps,
            totalSampleCount = totalRecordingSampleCount,
            waveformDataOffset = waveformDataOffset,
            widthScale = widthScale,
        )
    }

    // - Has available space
    // - Is already recoding
    // - Create a record file
    // - Create empty record in the database with created file path
    // - Set it as active record
    // - Start recording
    private suspend fun handleStartRecording(recordName: String): Long? {
        val format = prefs.settingRecordingFormat
        val sampleRate = prefs.settingSampleRate.value
        val bitrate = prefs.settingBitrate.value
        val channelCount = prefs.settingChannelCount.value

        val availableTimeSeconds = convertSpaceBytesToTimeInSeconds(
            spaceBytes = fileDataSource.getAvailableSpace(),
            recordingFormat = format,
            sampleRate = sampleRate,
            bitrate = bitrate,
            channels = channelCount
        )

        if (availableTimeSeconds > AppConstants.MIN_REMAIN_RECORDING_TIME && !audioRecorder.isRecording) {
            try {
                val recordFile = fileDataSource.createRecordFile(addExtension(recordName))
                // Use the actual file name (without extension) in case a suffix was added to avoid collision
                val actualRecordName = recordFile.nameWithoutExtension
                val record = Record(
                    id = 0,
                    name = actualRecordName,
                    durationMills = 0,
                    created = recordFile.lastModified(),
                    added = System.currentTimeMillis(),
                    removed = -1,
                    path = recordFile.absolutePath,
                    format = format.value,
                    size = 0,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    bitrate = if (format == RecordingFormat.M4a) bitrate else 0,
                    isBookmarked = false,
                    isWaveformProcessed = false,
                    isMovedToRecycle = false,
                    amps = IntArray(ARApplication.longWaveformSampleCount)
                )
                val id = recordsDataSource.insertRecord(record)
                prefs.activeRecordId = -1
                prefs.recordedRecordId = id
                prefs.recordedRecordPartCounter += 1

                _recordingState.value = _recordingState.value.copy(
                    recordId = id,
                    recordName = actualRecordName,
                    recordingFormat = format,
                    sampleRate = sampleRate,
                    bitrate = bitrate,
                    channelCount = channelCount,
                )

                audioRecorder.startRecording(
                    outputFile = recordFile,
                    channelCount = channelCount,
                    sampleRate = sampleRate,
                    bitrate = bitrate,
                    maxRecordingDurationMills = prefs.maxRecordingDurationMills,
                    audioSource = prefs.settingAudioSource.value,
                )
                return id
            } catch (e: CantCreateFileException) {
                Timber.e(e, "Failed to start recording with name: $recordName")
                val cantCreateFileMsg = applicationContext.getString(R.string.error_cant_create_file)
                val failedToStartRecordingMsg = applicationContext.getString(R.string.error_failed_to_start_recording)
                emitEvent(AudioRecordingServiceEvent.ShowErrorSnack(
                    "$failedToStartRecordingMsg\n$cantCreateFileMsg"
                ))
                stopForegroundService()
            }
        }
        return null
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun handlePauseResumeAction() {
        if (audioRecorder.isPaused) {
            audioRecorder.resumeRecording()
        } else if (audioRecorder.isRecording) {
            audioRecorder.pauseRecording()
        }
    }

    private fun handleStopAction() {
        audioRecorder.stopRecording()
    }

    fun pauseRecording() {
        if (audioRecorder.isRecording && !audioRecorder.isPaused) {
            audioRecorder.pauseRecording()
        }
    }

    fun resumeRecording() {
        if (audioRecorder.isRecording && audioRecorder.isPaused) {
            audioRecorder.resumeRecording()
        }
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    private suspend fun handleRecordingStopped(isNotMaxDurationHandling: Boolean = true) {
        // - Read recorded file info
        // - Update recorded file duration, size, format, bitrate, sample rate, channel count
        // - Move updated to recycle if requested to delete the record, otherwise set it as active record
        withContext(ioDispatcher) {
            val recordedRecordId = prefs.recordedRecordId
            prefs.recordedRecordId = -1
            if (recordedRecordId >= 0) {
                val record = recordsDataSource.getRecord(recordedRecordId)
                if (record != null) {
                    val output = File(record.path)
                    val info = AudioDecoder.readRecordInfo(output)
                    recordTagWriter.writeTags(output, record.name, prefs.recordAuthorName)
                    val recordUpdated = record.copy(
                        durationMills = info.duration / 1000,
                        format = info.format,
                        size = info.size,
                        sampleRate = info.sampleRate,
                        channelCount = info.channelCount,
                        bitrate = info.bitrate,
                    )
                    val success = recordsDataSource.updateRecord(recordUpdated)
                    _recordingState.value = _recordingState.value.copy(
                        recordingState = RecordingState.STOPPED,
                    )
                    if (success) {
                        prefs.activeRecordId = recordedRecordId
                        //Record saved successfully
                        emitEvent(AudioRecordingServiceEvent.ShowInfoSnack(
                            applicationContext.getString(R.string.msg_recording_saved_with_name, record.name)
                        ))
                        if (isNotMaxDurationHandling) {
                            decodeRecord(
                                recordId = recordUpdated.id,
                                path = recordUpdated.path,
                                durationMills = recordUpdated.durationMills,
                            )
                        }
                    } else {
                        //Failed to save record
                        emitEvent(AudioRecordingServiceEvent.ShowErrorSnack(
                            applicationContext.getString(R.string.msg_save_recording_failed)
                        ))
                    }
                } else {
                   //Failed to save record
                    emitEvent(AudioRecordingServiceEvent.ShowErrorSnack(
                        applicationContext.getString(R.string.msg_save_recording_failed)
                    ))
                }
                if (isNotMaxDurationHandling) {
                    resetRecordedRecordPartCounter()
                    stopForegroundService()
                }
            }
        }
    }

    private fun stopForegroundService() {
        recordingAmplitudes.clear()
        totalRecordingSampleCount = 0
        _recordingState.value = RecordingServiceState()
        stopNotificationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun decodeRecord(recordId: Long, path: String, durationMills: Long) {
        DecodeService.startNotificationV2(
            applicationContext,
            recordId,
            path,
            durationMills
        )
    }

    private fun startNotificationUpdates() {
        notificationHandler.postDelayed(object : Runnable {
            override fun run() {
                if (audioRecorder.isRecording) {
                    updateNotification()
                    notificationHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL)
                }
            }
        }, NOTIFICATION_UPDATE_INTERVAL)
    }

    private fun stopNotificationUpdates() {
        notificationHandler.removeCallbacksAndMessages(null)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val state = _recordingState.value
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            flags
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioRecordingService::class.java).apply {
                action = ACTION_PAUSE_RESUME_RECORDING
            },
            flags
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            },
            flags
        )

        val pauseResumeIcon: Int
        val pauseResumeText: String
        val statusText: String

        if (state.isPaused()) {
            pauseResumeIcon = R.drawable.ic_play
            pauseResumeText = getString(R.string.button_resume)
            statusText = getString(R.string.status_recording_paused)
        } else {
            pauseResumeIcon = R.drawable.ic_pause
            pauseResumeText = getString(R.string.button_pause)
            statusText = getString(R.string.status_recording_active)
        }

        val durationText = TimeUtils.formatTimeIntervalHourMinSec2(state.durationMills)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(statusText)
            .setContentText(durationText)
            .setSmallIcon(R.drawable.ic_record_rec)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(pauseResumeIcon, pauseResumeText, pauseResumeIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.button_stop), stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .build()
    }

    private fun addExtension(name: String): String {
        return "$name.${prefs.settingRecordingFormat.value}"
    }

    private fun getPartName(baseName: String, partCounter: Int): String {
        return "${baseName}_$partCounter"
    }

    private suspend fun handleMaxDurationReachedInternal() {
        // Save the current recording first
        handleRecordingStopped(isNotMaxDurationHandling = false)

        val partCounter = prefs.recordedRecordPartCounter
        val activeRecord = withContext(ioDispatcher) {
            recordsDataSource.getActiveRecord()
        }

        activeRecord?.let {
            val baseName = prefs.recordedRecordBaseName
            if (baseName != null) {
                // Rename saved record to record name and part 1 at the end.
                // Because the first part has base name without part number by default.
                if (partCounter == 1) {
                    withContext(ioDispatcher) {
                        recordsDataSource.renameRecord(it, getPartName(baseName, partCounter))
                    }
                }
                recordsDataSource.getRecord(it.id)?.let { recordUpdated ->
                    decodeRecord(
                        recordId = recordUpdated.id,
                        path = recordUpdated.path,
                        durationMills = recordUpdated.durationMills,
                    )
                }
                val incrementedPart = partCounter + 1

                // Get record part name for the next part and start recording
                val recordName = getPartName(baseName, incrementedPart)
                val recordId = handleStartRecording(recordName)
                recordId?.let {
                    emitEvent(
                        AudioRecordingServiceEvent.NewRecordingPartStarted(
                            part = incrementedPart, recordId = recordId,
                        )
                    )
                }
            } else {
                //In case if there something wrong with base record name, just start normal recording.
                handleStartRecording(prefs.settingNamingFormat.getNewRecordName(prefs))
            }
        } ?: run {
            // Failed to get active record, set error state
            //TODO: need to handle error here.
        }
    }

    private fun convertSpaceBytesToTimeInSeconds(
        spaceBytes: Long,
        recordingFormat: RecordingFormat,
        sampleRate: Int,
        bitrate: Int,
        channels: Int
    ): Long {
        return when (recordingFormat) {
            RecordingFormat.M4a,
            RecordingFormat.ThreeGp -> {
                // For compressed formats, use bitrate
                if (bitrate > 0) {
                    (spaceBytes * 8) / bitrate
                } else {
                    Long.MAX_VALUE
                }
            }
            RecordingFormat.Wav -> {
                // For WAV: bytes per second = sampleRate * channels * 2 (16-bit)
                val bytesPerSecond = sampleRate * channels * 2
                if (bytesPerSecond > 0) {
                    spaceBytes / bytesPerSecond
                } else {
                    Long.MAX_VALUE
                }
            }
        }
    }

    fun resetRecordedRecordPartCounter() {
        prefs.recordedRecordPartCounter = 0
    }

    private fun emitEvent(event: AudioRecordingServiceEvent) {
        serviceScope.launch {
            _event.emit(event)
        }
    }
}

data class RecordingServiceState(
    val recordingFormat: RecordingFormat? = null,
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val channelCount: Int = 0,
    val recordingState: RecordingState = RecordingState.IDLE,
    val durationMills: Long = 0L,
    val amplitude: Int = 0,
    val recordId: Long = -1L,
    val recordName: String? = null,
    /** Sliding-window amplitude buffer snapshot for waveform rendering. */
    val amplitudes: IntArray = intArrayOf(),
    /** Total number of amplitude samples received during the current recording session. */
    val totalSampleCount: Int = 0,
    /** Offset of the first element in [amplitudes] relative to the total sample timeline. */
    val waveformDataOffset: Int = 0,
    /** Width scale for waveform rendering. */
    val widthScale: Float = 1.5f,
) {

    fun isRecording(): Boolean {
        return this.recordingState == RecordingState.STARTED
                || this.recordingState == RecordingState.PROGRESS
                || this.recordingState == RecordingState.RESUMED
    }

    fun isPaused(): Boolean {
        return this.recordingState == RecordingState.PAUSED
    }

    fun isStoppedRecording(): Boolean {
        return this.recordingState == RecordingState.STOPPED
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordingServiceState) return false
        if (recordingFormat != other.recordingFormat) return false
        if (sampleRate != other.sampleRate) return false
        if (bitrate != other.bitrate) return false
        if (channelCount != other.channelCount) return false
        if (recordingState != other.recordingState) return false
        if (durationMills != other.durationMills) return false
        if (amplitude != other.amplitude) return false
        if (recordId != other.recordId) return false
        if (recordName != other.recordName) return false
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (totalSampleCount != other.totalSampleCount) return false
        if (waveformDataOffset != other.waveformDataOffset) return false
        if (widthScale != other.widthScale) return false
        return true
    }

    override fun hashCode(): Int {
        var result = recordingFormat?.hashCode() ?: 0
        result = 31 * result + sampleRate
        result = 31 * result + bitrate
        result = 31 * result + channelCount
        result = 31 * result + recordingState.hashCode()
        result = 31 * result + durationMills.hashCode()
        result = 31 * result + amplitude
        result = 31 * result + recordId.hashCode()
        result = 31 * result + (recordName?.hashCode() ?: 0)
        result = 31 * result + amplitudes.contentHashCode()
        result = 31 * result + totalSampleCount
        result = 31 * result + waveformDataOffset
        result = 31 * result + widthScale.hashCode()
        return result
    }
}

enum class RecordingState {
    IDLE,
    STARTED,
    PROGRESS,
    PAUSED,
    RESUMED,
    STOPPED,
}

/**
 * Calculates how many amplitude samples fit in half the waveform view width.
 *
 * The calculation mirrors the rendering math in WaveformComposeView:
 *   pxPerMill  = screenWidth × DEFAULT_WIDTH_SCALE / SHORT_RECORD
 *   pxPerSample = pxPerMill × RECORDING_VISUALIZATION_INTERVAL_NEW
 *   bufferSize  = (screenWidth / 2) / pxPerSample
 */
private fun calculateRecordingAmplitudeBufferSize(): Int {
    val screenWidthPx = Resources.getSystem().displayMetrics.widthPixels
    val pxPerMill = screenWidthPx * AppConstantsV2.DEFAULT_WIDTH_SCALE / AppConstantsV2.SHORT_RECORD
    val pxPerSample = pxPerMill * AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW
    return ((screenWidthPx / 2f) / pxPerSample).toInt() + 5
}

sealed class AudioRecordingServiceEvent {
    data class ShowErrorSnack(val message: String) : AudioRecordingServiceEvent()
    data class ShowInfoSnack(val message: String) : AudioRecordingServiceEvent()
    data class NewRecordingPartStarted(val part: Int, val recordId: Long) : AudioRecordingServiceEvent()
}
