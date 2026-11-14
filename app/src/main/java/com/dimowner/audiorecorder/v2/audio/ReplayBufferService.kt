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

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.app.HomeActivity
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that keeps the instant replay buffer alive: the app keeps recording the
 * microphone in the background but only the last N minutes survive, and the user can save them
 * as a normal record at any time from the notification or the home screen.
 *
 * Deliberately limited:
 * - `START_NOT_STICKY` and no boot receiver. From Android 14 a microphone foreground service can
 *   not be started from the background, so the buffer is (re)started only from the app, while it
 *   is visible - see [startIfEnabled].
 * - Microphone only. System-audio capture needs a fresh MediaProjection consent per session, which
 *   an always-on buffer cannot ask for, so a system-audio source setting falls back to the mic.
 * - Stopping deletes the buffered audio; nothing is kept that the user did not choose to save.
 *
 * Capture itself lives in [ReplayBufferRecorder], whose [ReplayBufferRecorder.state] is the
 * StateFlow the UI observes.
 */
@AndroidEntryPoint
class ReplayBufferService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "replay_buffer_channel"

        private const val ACTION_START = "com.dimowner.audiorecorder.ACTION_START_REPLAY_BUFFER"
        private const val ACTION_SAVE = "com.dimowner.audiorecorder.ACTION_SAVE_REPLAY_BUFFER"
        private const val ACTION_STOP = "com.dimowner.audiorecorder.ACTION_STOP_REPLAY_BUFFER"

        private const val BUFFER_FILE_NAME = "replay_buffer.pcm"

        /**
         * Directory of the ring buffer file: app-private and excluded from backups, since it holds
         * unsaved microphone audio.
         */
        fun bufferDirectory(context: Context): File = context.noBackupFilesDir

        fun hasRecordAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Starts the buffer, or restarts it if its length changed. Must be called while the app is
         * in the foreground: that is when a microphone foreground service may start.
         */
        fun start(context: Context) {
            val intent = Intent(context, ReplayBufferService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the app is not in the foreground.
                Timber.e(e, "ReplayBufferService: start not allowed")
            }
        }

        /** Starts the buffer when the user enabled it and it is not running already. */
        fun startIfEnabled(context: Context, prefs: PrefsV2, recorder: ReplayBufferRecorder) {
            if (prefs.isReplayBufferEnabled &&
                !recorder.state.value.isRunning &&
                hasRecordAudioPermission(context)
            ) {
                start(context)
            }
        }

        /** Saves the buffered audio as a new record; a no-op when the buffer is not running. */
        fun save(context: Context) {
            val intent = Intent(context, ReplayBufferService::class.java).setAction(ACTION_SAVE)
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                Timber.e(e, "ReplayBufferService: save request not allowed")
            }
        }

        /** Stops the buffer and deletes the buffered audio. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ReplayBufferService::class.java))
        }
    }

    @Inject
    lateinit var recorder: ReplayBufferRecorder

    @Inject
    lateinit var saver: ReplayBufferSaver

    @Inject
    lateinit var prefs: PrefsV2

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceJob = SupervisorJob()
    private val serviceScope by lazy { CoroutineScope(ioDispatcher + serviceJob) }

    private var notificationManager: NotificationManager? = null
    private var stateJob: Job? = null
    private var saveJob: Job? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_SAVE -> handleSave()
            ACTION_STOP -> {
                // The notification's Stop means "turn instant replay off", not "pause it until
                // the app is opened again", so the setting follows.
                prefs.isReplayBufferEnabled = false
                finish()
            }
            // A null intent would be a sticky restart, which START_NOT_STICKY rules out.
            else -> if (!recorder.state.value.isRunning) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateJob?.cancel()
        serviceJob.cancel()
        // Covers stopService() from Settings as well as finish(): unsaved audio never outlives
        // the service.
        recorder.stop()
        notificationManager = null
        super.onDestroy()
    }

    private fun handleStart() {
        // startForeground() must come first and synchronously, whatever happens next.
        if (!startInForeground()) {
            stopSelf()
            return
        }
        val minutes = prefs.replayBufferDurationMinutes
        val current = recorder.state.value
        if (!current.isRunning || current.durationMinutes != minutes) {
            val started = recorder.start(
                bufferFile = File(bufferDirectory(this), BUFFER_FILE_NAME),
                durationMinutes = minutes,
                audioSource = micAudioSource(),
            )
            if (!started) {
                showToast(R.string.replay_buffer_start_failed)
                finish()
                return
            }
        }
        observeState()
    }

    private fun handleSave() {
        if (!recorder.state.value.isRunning) {
            // Started only to deliver this request; never left running without a notification.
            if (!isForeground) stopSelf()
            return
        }
        if (saveJob?.isActive == true) return
        saveJob = serviceScope.launch {
            val result = saver.save()
            withContext(Dispatchers.Main) {
                when (result) {
                    is ReplayBufferSaveResult.Saved -> showToast(
                        getString(R.string.msg_recording_saved_with_name, result.recordName)
                    )
                    ReplayBufferSaveResult.NothingBuffered ->
                        showToast(R.string.replay_buffer_nothing_to_save)
                    ReplayBufferSaveResult.NotEnoughSpace ->
                        showToast(R.string.replay_buffer_not_enough_space)
                    ReplayBufferSaveResult.Failed -> showToast(R.string.msg_save_recording_failed)
                }
            }
        }
    }

    /** Lets a save in progress complete, then stops; [onDestroy] deletes the buffer. */
    private fun finish() {
        val pendingSave = saveJob
        serviceScope.launch {
            pendingSave?.join()
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
            }
        }
    }

    /**
     * The buffer always uses a microphone. A system-audio selection falls back to the default mic
     * source, because playback capture needs a per-session consent an always-on buffer can't get.
     */
    private fun micAudioSource(): Int {
        val source = prefs.settingAudioSource
        return if (source.isSystemAudio) DefaultValues.DefaultAudioSource.value else source.value
    }

    private fun startInForeground(): Boolean {
        val notification = buildNotification(recorder.state.value)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startForeground(
                    NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            true
        } catch (e: SecurityException) {
            // Android 14+: microphone type without RECORD_AUDIO granted.
            Timber.e(e, "ReplayBufferService: can't start microphone foreground service")
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: started while in the background.
            Timber.e(e, "ReplayBufferService: foreground start not allowed")
            false
        }
    }

    private fun observeState() {
        if (stateJob?.isActive == true) return
        stateJob = serviceScope.launch {
            recorder.state
                .map { NotificationContent(it.durationMinutes, it.isPausedForRecording, it.isSaving) }
                .distinctUntilChanged()
                .collect { notificationManager?.notify(NOTIFICATION_ID, buildNotification(recorder.state.value)) }
        }
    }

    private data class NotificationContent(
        val minutes: Int,
        val isPaused: Boolean,
        val isSaving: Boolean,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.replay_buffer_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.replay_buffer_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(state: ReplayBufferState): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val saveIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ReplayBufferService::class.java).setAction(ACTION_SAVE),
            flags
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReplayBufferService::class.java).setAction(ACTION_STOP),
            flags
        )
        val minutes = prefs.replayBufferDurationMinutes.takeIf { !state.isRunning }
            ?: state.durationMinutes
        val text = when {
            state.isSaving -> getString(R.string.replay_buffer_saving)
            state.isPausedForRecording -> getString(R.string.replay_buffer_notification_paused)
            else -> getString(R.string.replay_buffer_notification_text, minutes)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.replay_buffer_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_record_rec)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(R.drawable.ic_save_alt, getString(R.string.replay_buffer_save, minutes), saveIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.button_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun showToast(@StringRes message: Int) = showToast(getString(message))

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
