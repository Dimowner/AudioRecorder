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
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.audio.AudioDecoder
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AudioRecordingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "audio_recording_channel"
        private const val NOTIFICATION_UPDATE_INTERVAL = 5000L // 5 seconds

        private const val ACTION_START_RECORDING = "com.dimowner.audiorecorder.ACTION_START_RECORDING"
        private const val ACTION_PAUSE_RESUME_RECORDING = "com.dimowner.audiorecorder.ACTION_PAUSE_RESUME_RECORDING"
        private const val ACTION_STOP_RECORDING = "com.dimowner.audiorecorder.ACTION_STOP_RECORDING"

        const val EXTRA_RECORD_NAME = "extra_record_name"

        fun startServiceForeground(context: Context, recordName: String) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_RECORD_NAME, recordName)
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
                val recordName = intent.getStringExtra(EXTRA_RECORD_NAME)
                if (recordName != null) {
                    serviceScope.launch {
                        resetRecordedRecordPartCounter()
                        prefs.recordedRecordBaseName = recordName
                        handleStartRecording(recordName)
                    }
                }
            }
            ACTION_PAUSE_RESUME_RECORDING -> handlePauseResumeAction()
            ACTION_STOP_RECORDING -> handleStopAction()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
        serviceScope.launch {
            audioRecorder.subscribeRecorderEvents().collect { event ->
                Timber.d("AudioRecordingService: event: $event")
                when (event) {
                    is RecorderEvent.OnStartRecording -> {
                        _recordingState.value = _recordingState.value.copy(
                            isRecording = true,
                            isPaused = false,
                        )
                        startNotificationUpdates()
                        updateNotification()
                    }
                    is RecorderEvent.OnRecordingProgress -> {
                        _recordingState.value = _recordingState.value.copy(
                            durationMills = event.durationMills,
                            amplitude = event.amplitude
                        )
                    }
                    is RecorderEvent.OnPauseRecording -> {
                        _recordingState.value = _recordingState.value.copy(
                            isPaused = true
                        )
                        updateNotification()
                    }
                    is RecorderEvent.OnResumeRecording -> {
                        _recordingState.value = _recordingState.value.copy(
                            isPaused = false
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

    // - Has available space
    // - Is already recoding
    // - Create a record file
    // - Create empty record in the database with created file path
    // - Set it as active record
    // - Start recording
    private suspend fun handleStartRecording(recordName: String): Long? {
        val availableTimeSeconds = convertSpaceBytesToTimeInSeconds(
            spaceBytes = fileDataSource.getAvailableSpace(),
            recordingFormat = prefs.settingRecordingFormat,
            sampleRate = prefs.settingSampleRate.value,
            bitrate = prefs.settingBitrate.value,
            channels = prefs.settingChannelCount.value,
        )

        if (availableTimeSeconds > AppConstants.MIN_REMAIN_RECORDING_TIME && !audioRecorder.isRecording) {
            //TODO: hande CantCreateFileException
            val format = prefs.settingRecordingFormat
            val recordFile = fileDataSource.createRecordFile(addExtension(recordName))
            val record = Record(
                id = 0,
                name = recordName,
                durationMills = 0,
                created = recordFile.lastModified(),
                added = System.currentTimeMillis(),
                removed = -1,
                path = recordFile.absolutePath,
                format = format.value,
                size = 0,
                sampleRate = prefs.settingSampleRate.value,
                channelCount = prefs.settingChannelCount.value,
                bitrate = if (format == RecordingFormat.M4a) prefs.settingBitrate.value else 0,
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
                recordName = recordName
            )

            audioRecorder.startRecording(
                outputFile = recordFile,
                channelCount = prefs.settingChannelCount.value,
                sampleRate = prefs.settingSampleRate.value,
                bitrate = prefs.settingBitrate.value,
                maxRecordingDurationMills = prefs.maxRecordingDurationMills,
                audioSource = prefs.settingAudioSource.value,
            )

            // Start foreground with notification
            startForegroundWithNotification()
            return id
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

        if (state.isPaused) {
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
        recordingFormat: com.dimowner.audiorecorder.v2.data.model.RecordingFormat,
        sampleRate: Int,
        bitrate: Int,
        channels: Int
    ): Long {
        return when (recordingFormat) {
            com.dimowner.audiorecorder.v2.data.model.RecordingFormat.M4a,
            com.dimowner.audiorecorder.v2.data.model.RecordingFormat.ThreeGp -> {
                // For compressed formats, use bitrate
                if (bitrate > 0) {
                    (spaceBytes * 8) / bitrate
                } else {
                    Long.MAX_VALUE
                }
            }
            com.dimowner.audiorecorder.v2.data.model.RecordingFormat.Wav -> {
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
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMills: Long = 0L,
    val amplitude: Int = 0,
    val recordId: Long = -1L,
    val recordName: String? = null,
)

sealed class AudioRecordingServiceEvent {
    data class ShowErrorSnack(val message: String) : AudioRecordingServiceEvent()
    data class ShowInfoSnack(val message: String) : AudioRecordingServiceEvent()
    data class NewRecordingPartStarted(val part: Int, val recordId: Long) : AudioRecordingServiceEvent()
}
