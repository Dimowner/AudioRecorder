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
package com.dimowner.audiorecorder.audio.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.app.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AudioPlaybackService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_UPDATE_INTERVAL = 5000L // 5 seconds

        private const val ACTION_START_SERVICE = "com.dimowner.audiorecorder.ACTION_START_SERVICE"
        private const val ACTION_PLAY_PAUSE = "com.dimowner.audiorecorder.ACTION_PLAY_PAUSE"
        private const val ACTION_STOP = "com.dimowner.audiorecorder.ACTION_STOP"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_DURATION = "extra_duration"

        fun startServiceForeground(context: Context, name: String, path: String, durationMills: Long) {
            val intent = Intent(context, AudioPlaybackService::class.java)
            intent.setAction(ACTION_START_SERVICE)
            intent.putExtra(EXTRA_FILE_PATH, path)
            intent.putExtra(EXTRA_FILE_NAME, name)
            intent.putExtra(EXTRA_DURATION, durationMills)
            context.startService(intent)
        }
    }

    private val binder = ServiceBinder()

    @Inject
    lateinit var audioPlayer: PlayerContractNew.Player

    private val notificationHandler = Handler(Looper.getMainLooper())
    private var notificationManager: NotificationManager? = null

    private val _playbackState = MutableStateFlow(PlaybackState())

    private var currentFilePath: String? = null
    private var currentFileName: String? = null
    private var currentDuration: Long = 0L

    inner class ServiceBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        setupPlayerCallbacks()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> handleStartServiceAction(intent)
            ACTION_PLAY_PAUSE -> handlePlayPauseAction()
            ACTION_STOP -> handleStopAction()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationUpdates()
        notificationManager = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_playback_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_playback_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupPlayerCallbacks() {
        audioPlayer.addPlayerCallback(object : PlayerContractNew.PlayerCallback {
            override fun onStartPlay() {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    isPaused = false
                )
                startNotificationUpdates()
                updateNotification()
            }

            override fun onPlayProgress(mills: Long) {
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMills = mills
                )
            }

            override fun onPausePlay() {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = true
                )
                updateNotification()
            }

            override fun onSeek(mills: Long) {
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMills = mills
                )
            }

            override fun onStopPlay() {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    currentPositionMills = 0L,
                    trackName = null,
                    duration = 0L
                )
                stopNotificationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            override fun onError(throwable: AppException) {
                Timber.e(throwable)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    error = throwable
                )
                stopNotificationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        })
    }

    fun play(filePath: String, fileName: String, duration: Long) {
        currentFilePath = filePath
        currentFileName = fileName
        currentDuration = duration

        _playbackState.value = _playbackState.value.copy(
            trackName = fileName,
            duration = duration,
            error = null
        )

        audioPlayer.play(filePath)
    }

    fun pause() {
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause()
        }
    }

    fun resume() {
        if (audioPlayer.isPaused()) {
            audioPlayer.unpause()
        }
    }

    fun stop() {
        audioPlayer.stop()
    }

    fun seek(mills: Long) {
        audioPlayer.seek(mills)
    }

    fun isPlaying(): Boolean = audioPlayer.isPlaying()

    fun getCurrentProgress(): Long {
        return _playbackState.value.currentPositionMills
    }

    fun isPaused(): Boolean = audioPlayer.isPaused()

    private fun handleStartServiceAction(intent: Intent) {
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: getString(R.string.app_name)
        val duration = intent.getLongExtra(EXTRA_DURATION, 0L)

        if (filePath != null) {
            play(filePath, fileName, duration)
        }
    }

    private fun handlePlayPauseAction() {
        if (audioPlayer.isPlaying()) {
            pause()
        } else if (audioPlayer.isPaused()) {
            resume()
        }
    }

    private fun handleStopAction() {
        stop()
    }

    private fun startNotificationUpdates() {
        notificationHandler.postDelayed(object : Runnable {
            override fun run() {
                if (audioPlayer.isPlaying()) {
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
        val state = _playbackState.value
        val notification = buildNotification(
            trackName = state.trackName ?: getString(R.string.app_name),
            currentPosition = state.currentPositionMills,
            duration = state.duration,
            isPlaying = state.isPlaying,
            isPaused = state.isPaused
        )

        notificationManager?.notify(NOTIFICATION_ID, notification.build())

        if (state.isPlaying || state.isPaused) {
            startForeground(NOTIFICATION_ID, notification.build())
        }
    }

    private fun buildNotification(
        trackName: String,
        currentPosition: Long,
        duration: Long,
        isPlaying: Boolean,
        isPaused: Boolean
    ): NotificationCompat.Builder {
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

        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            flags
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            AppConstants.PENDING_INTENT_FLAGS or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon: Int
        val playPauseText: String

        when {
            isPlaying -> {
                playPauseIcon = R.drawable.ic_pause
                playPauseText = getString(R.string.button_pause)
            }
            isPaused -> {
                playPauseIcon = R.drawable.ic_play
                playPauseText = getString(R.string.button_resume)
            }
            else -> {
                playPauseIcon = R.drawable.ic_play
                playPauseText = getString(R.string.btn_play)
            }
        }

        val timeText = if (duration > 0) {
            "${TimeUtils.formatTimeIntervalHourMinSec2(currentPosition)} / ${TimeUtils.formatTimeIntervalHourMinSec2(duration)}"
        } else {
            TimeUtils.formatTimeIntervalHourMinSec2(currentPosition)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(trackName)
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_play_circle)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(playPauseIcon, playPauseText, playPauseIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.button_stop), stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
    }
}

internal data class PlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMills: Long = 0L,
    val trackName: String? = null,
    val duration: Long = 0L,
    val error: AppException? = null
)
