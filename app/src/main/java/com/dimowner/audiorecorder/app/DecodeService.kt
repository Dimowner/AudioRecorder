/*
 * Copyright 2021 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.*
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.audio.AudioDecodingListener
import com.dimowner.audiorecorder.audio.AudioWaveformVisualization
import com.dimowner.audiorecorder.data.database.LocalRepository
import com.dimowner.audiorecorder.data.database.Record
import timber.log.Timber

/**
 * Created on 02.02.2021.
 * @author Dimowner
 */
class DecodeService : Service() {

	companion object {
		private const val CHANNEL_NAME = "Default"
		private const val CHANNEL_ID = "com.dimowner.audiorecorder.Decode.Notification"
		const val ACTION_START_DECODING_SERVICE = "ACTION_START_DECODING_SERVICE"
		const val ACTION_STOP_DECODING_SERVICE = "ACTION_STOP_DECODING_SERVICE"
		const val ACTION_CANCEL_DECODE = "ACTION_CANCEL_DECODE"
		const val EXTRAS_KEY_DECODE_INFO = "key_decode_info"
		private const val NOTIF_ID = 104

		fun startNotification(context: Context, recId: Int) {
			val intent = Intent(context, DecodeService::class.java)
			intent.action = ACTION_START_DECODING_SERVICE
			intent.putExtra(EXTRAS_KEY_DECODE_INFO, recId)
			context.startService(intent)
		}
	}

	private var decodeListener: DecodeServiceListener? = null
	private val binder = LocalBinder()

	lateinit var builder: NotificationCompat.Builder
	lateinit var notificationManager: NotificationManagerCompat
	lateinit var remoteViewsSmall: RemoteViews
	lateinit var processingTasks: BackgroundQueue
	lateinit var recordingsTasks: BackgroundQueue
	lateinit var localRepository: LocalRepository
	lateinit var waveformVisualization: AudioWaveformVisualization
	lateinit var colorMap: ColorMap
	private var isCancel = false

	override fun onBind(intent: Intent): IBinder? {
		return binder
	}

	override fun onCreate() {
		super.onCreate()
		colorMap = ARApplication.getInjector().provideColorMap()
		processingTasks = ARApplication.getInjector().provideProcessingTasksQueue()
		recordingsTasks = ARApplication.getInjector().provideRecordingTasksQueue()
		localRepository = ARApplication.getInjector().provideLocalRepository()
		waveformVisualization = ARApplication.getInjector().provideAudioWaveformVisualization()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent != null) {
			val action = intent.action
			if (action != null && action.isNotEmpty()) {
				when (action) {
					ACTION_START_DECODING_SERVICE -> if (intent.hasExtra(EXTRAS_KEY_DECODE_INFO)) {
						val id = intent.getIntExtra(EXTRAS_KEY_DECODE_INFO, -1)
						if (id >= 0) {
							startDecode(id)
						}
					}
					ACTION_STOP_DECODING_SERVICE -> stopService()
					ACTION_CANCEL_DECODE -> {
						isCancel = true
						stopService()
					}
				}
			}
		}
		return super.onStartCommand(intent, flags, startId)
	}

	private fun startDecode(id: Int) {
		isCancel = false
		startNotification()
		processingTasks.postRunnable {
			var prevTime: Long = 0
			val rec = localRepository.getRecord(id)
			if (rec != null && rec.duration / 1000 < AppConstants.DECODE_DURATION) {
				waveformVisualization.decodeRecordWaveform(rec.path, object : AudioDecodingListener {
					override fun isCanceled(): Boolean {
						return isCancel
					}

					override fun onStartProcessing(duration: Long, channelsCount: Int, sampleRate: Int) {
						decodeListener?.onStartProcessing()
					}

					override fun onProcessingProgress(percent: Int) {
						val curTime = System.currentTimeMillis()
						if (percent == 100 || curTime > prevTime + 200) {
							updateNotification(percent)
							prevTime = curTime
						}
					}

					override fun onProcessingCancel() {
						Toast.makeText(applicationContext, R.string.processing_canceled, Toast.LENGTH_LONG).show()
						decodeListener?.onFinishProcessing()
						stopService()
					}

					override fun onFinishProcessing(data: IntArray, duration: Long) {
						recordingsTasks.postRunnable {
							val rec1 = localRepository.getRecord(id)
							if (rec1 != null) {
								val decodedRecord = Record(
										rec1.id,
										rec1.name,
										rec1.duration,
										rec1.created,
										rec1.added,
										rec1.removed,
										rec1.path,
										rec1.format,
										rec1.size,
										rec1.sampleRate,
										rec1.channelCount,
										rec1.bitrate,
										rec1.isBookmarked,
										true,
										data)
								localRepository.updateRecord(decodedRecord)
							}
							decodeListener?.onFinishProcessing()
							stopService()
						}
					}

					override fun onError(exception: Exception) {
						Timber.e(exception)
						decodeListener?.onFinishProcessing()
						stopService()
					}
				})
			} else {
				stopService()
			}
		}
	}

	private fun startNotification() {
		notificationManager = NotificationManagerCompat.from(this)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
		}
		remoteViewsSmall = RemoteViews(packageName, R.layout.layout_progress_notification)
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_close, getCancelDecodePendingIntent(applicationContext))
		remoteViewsSmall.setTextViewText(R.id.txt_name, resources.getString(R.string.record_calculation))
		remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", ContextCompat.getColor(applicationContext, colorMap.primaryColorRes))

		// Create notification default intent.
		val intent = Intent(applicationContext, MainActivity::class.java)
		intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
		val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, 0)

		// Create notification builder.
		builder = NotificationCompat.Builder(this, CHANNEL_ID)
		builder.setWhen(System.currentTimeMillis())
		builder.setContentTitle(resources.getString(R.string.app_name))
		builder.setSmallIcon(R.drawable.ic_loop)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.priority = NotificationManagerCompat.IMPORTANCE_DEFAULT
		} else {
			builder.priority = NotificationCompat.PRIORITY_DEFAULT
		}
		// Make head-up notification.
		builder.setContentIntent(pendingIntent)
		builder.setCustomContentView(remoteViewsSmall)
		builder.setOngoing(true)
		builder.setOnlyAlertOnce(true)
		builder.setDefaults(0)
		builder.setSound(null)
		startForeground(NOTIF_ID, builder.build())
	}

	fun stopService() {
		stopForeground(true)
		stopSelf()
	}

	private fun getCancelDecodePendingIntent(context: Context): PendingIntent {
		val intent = Intent(context, StopDecodeReceiver::class.java)
		intent.action = ACTION_CANCEL_DECODE
		return PendingIntent.getBroadcast(context, 15, intent, 0)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun createNotificationChannel(channelId: String, channelName: String) {
		val channel = notificationManager.getNotificationChannel(channelId)
		if (channel == null) {
			val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
			chan.lightColor = Color.BLUE
			chan.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
			chan.setSound(null, null)
			chan.enableLights(false)
			chan.enableVibration(false)
			notificationManager.createNotificationChannel(chan)
		} else {
			Timber.v("Channel already exists: %s", CHANNEL_ID)
		}
	}

	private fun updateNotification(percent: Int) {
		remoteViewsSmall.setProgressBar(R.id.progress, 100, percent, false)
		notificationManager.notify(NOTIF_ID, builder.build())
	}

	fun setDecodeListener(listener: DecodeServiceListener?) {
		decodeListener = listener
	}

	class StopDecodeReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val stopIntent = Intent(context, DecodeService::class.java)
			stopIntent.action = intent.action
			context.startService(stopIntent)
		}
	}

	inner class LocalBinder : Binder() {
		fun getService(): DecodeService = this@DecodeService
	}
}

interface DecodeServiceListener {
	fun onStartProcessing()
	fun onFinishProcessing()
}
