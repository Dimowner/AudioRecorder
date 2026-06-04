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

package com.dimowner.audiorecorder.app.migration

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.BackgroundQueue
import com.dimowner.audiorecorder.ColorMap
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.data.database.LocalRepository
import com.dimowner.audiorecorder.util.isUsingNightModeResources
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.data.room.AppDatabase
import com.dimowner.audiorecorder.v2.data.toRecordEntity
import com.dimowner.audiorecorder.v2.data.toRecordV2
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Service to migrate records from old SQLite database to Room database.
 * Runs in the background with a foreground notification showing "Database update in progress".
 * On successful migration, sets the preference flag to indicate migration is complete.
 * On error, the service stops without retry.
 */
@AndroidEntryPoint
class DatabaseMigrationService : Service() {

    companion object {
        private const val CHANNEL_NAME = "DatabaseMigration"
        private const val CHANNEL_ID = "com.dimowner.audiorecorder.DatabaseMigration.Notification"
        const val ACTION_START_MIGRATION = "ACTION_START_MIGRATION"
        private const val NOTIF_ID = 107

        fun startService(context: Context) {
            val intent = Intent(context, DatabaseMigrationService::class.java)
            intent.action = ACTION_START_MIGRATION
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private lateinit var builder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var remoteViewsSmall: RemoteViews
    private lateinit var remoteViewsBig: RemoteViews
    private lateinit var copyTasks: BackgroundQueue
    private lateinit var colorMap: ColorMap
    private lateinit var prefs: Prefs
    private lateinit var localRepository: LocalRepository

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        colorMap = ARApplication.injector.provideColorMap(applicationContext)
        prefs = ARApplication.injector.providePrefs(applicationContext)
        copyTasks = ARApplication.injector.provideCopyTasksQueue()
        localRepository = ARApplication.injector.provideLocalRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action == ACTION_START_MIGRATION) {
                startMigration()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMigration() {
        // Check if migration already completed
        if (prefs.isDatabaseMigratedToRoom) {
            Timber.d("Database migration already completed, skipping")
            stopService()
            return
        }

        startNotification()

        copyTasks.postRunnable {
            try {
                performMigration()
            } catch (e: Exception) {
                Timber.e(e, "Database migration failed")
                analyticsTracker.trackDbMigrationFailed(e)
                prefs.setLastMigrationToRoomFailedTime(System.currentTimeMillis())
                stopService()
            }
        }
    }

    private fun performMigration() {
        Timber.d("Starting database migration from SQLite to Room")

        analyticsTracker.trackDbMigrationStarted()
        val migrationStartMs = System.currentTimeMillis()

        val recordDao = appDatabase.recordDao()
        var totalMigrated = 0

        // Migrate regular records page by page
        var page = 1
        while (true) {
            val records = localRepository.getRecords(page, AppConstants.SORT_DATE_DESC)
            if (records.isNullOrEmpty()) {
                Timber.d("No more records to migrate, ending pagination")
                break
            }

            val recordEntities = records.map { oldRecord ->
                oldRecord.toRecordV2(isMovedToRecycle = false).toRecordEntity()
            }

            recordDao.insertRecords(recordEntities)
            totalMigrated += recordEntities.size

            Timber.d("Migrated page $page with ${recordEntities.size} records")
            page++
        }

        // Migrate trash records
        val trashRecords = localRepository.trashRecords
        if (!trashRecords.isNullOrEmpty()) {
            val trashEntities = trashRecords.map { oldRecord ->
                oldRecord.toRecordV2(isMovedToRecycle = true).toRecordEntity()
            }

            recordDao.insertRecords(trashEntities)
            Timber.d("Migrated ${trashEntities.size} trash records")
        }

        val totalRecordsCount = localRepository.getRecordsDurations().size

        if (totalMigrated == totalRecordsCount) {
            // Mark migration as complete
            prefs.setDatabaseMigratedToRoom(true)
            val durationMs = System.currentTimeMillis() - migrationStartMs
            analyticsTracker.trackDbMigrationSuccess(
                migratedRecordCount = totalMigrated,
                durationMs = durationMs,
            )
            Timber.d("Database migration completed successfully. Record count to migrate: $totalRecordsCount, Total records migrated: $totalMigrated")
        } else {
            val message = "Database migration failed: expected to migrate $totalRecordsCount records but only migrated $totalMigrated"
            Timber.d(message)
            analyticsTracker.trackDbMigrationFailed(Exception("message"))
        }

        stopService()
    }

    @SuppressLint("WrongConstant")
    private fun startNotification() {
        notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
        }

        remoteViewsSmall = RemoteViews(packageName, R.layout.layout_migration_notification_small)
        remoteViewsSmall.setTextViewText(
            R.id.txt_migration_status,
            resources.getString(R.string.database_update_in_progress)
        )

        val isNightMode = isUsingNightModeResources(applicationContext)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            remoteViewsSmall.setInt(
                R.id.container, "setBackgroundColor",
                this.resources.getColor(colorMap.primaryColorRes)
            )
        } else {
            remoteViewsSmall.setInt(
                R.id.container, "setBackgroundColor",
                this.resources.getColor(R.color.transparent)
            )
            if (isNightMode) {
                remoteViewsSmall.setInt(
                    R.id.txt_app_label, "setTextColor",
                    this.resources.getColor(R.color.text_primary_light)
                )
                remoteViewsSmall.setInt(
                    R.id.txt_migration_status, "setTextColor",
                    this.resources.getColor(R.color.text_primary_light)
                )
            } else {
                remoteViewsSmall.setInt(
                    R.id.txt_app_label, "setTextColor",
                    this.resources.getColor(R.color.text_primary_dark)
                )
                remoteViewsSmall.setInt(
                    R.id.txt_migration_status, "setTextColor",
                    this.resources.getColor(R.color.text_primary_dark)
                )
            }
        }

        remoteViewsBig = RemoteViews(packageName, R.layout.layout_migration_notification_big)
        remoteViewsBig.setTextViewText(
            R.id.txt_migration_status,
            resources.getString(R.string.database_update_in_progress)
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            remoteViewsBig.setInt(
                R.id.container, "setBackgroundColor",
                this.resources.getColor(colorMap.primaryColorRes)
            )
        } else {
            remoteViewsBig.setInt(
                R.id.container, "setBackgroundColor",
                this.resources.getColor(R.color.transparent)
            )
            if (isNightMode) {
                remoteViewsBig.setInt(
                    R.id.txt_migration_status, "setTextColor",
                    this.resources.getColor(R.color.text_primary_light)
                )
            } else {
                remoteViewsBig.setInt(
                    R.id.txt_migration_status, "setTextColor",
                    this.resources.getColor(R.color.text_primary_dark)
                )
            }
        }

        // Create notification default intent
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, AppConstants.PENDING_INTENT_FLAGS
        )

        // Create notification builder
        builder = NotificationCompat.Builder(this, CHANNEL_ID)
        builder.setWhen(System.currentTimeMillis())
        builder.setContentTitle(resources.getString(R.string.app_name))
        builder.setSmallIcon(R.drawable.ic_save_alt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.priority = NotificationManagerCompat.IMPORTANCE_LOW
        } else {
            builder.priority = NotificationCompat.PRIORITY_LOW
        }
        builder.setContentIntent(pendingIntent)
        builder.setCustomContentView(remoteViewsSmall)
        builder.setCustomBigContentView(remoteViewsBig)
        builder.setOngoing(true)
        builder.setOnlyAlertOnce(true)
        builder.setDefaults(0)
        builder.setSound(null)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIF_ID, builder.build())
        } else {
            startForeground(
                NOTIF_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
    }

    private fun stopService() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * Called by the system on API 35+ when a [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
     * foreground service has been running for the maximum allowed duration (6 hours).
     * Stop the service gracefully so it doesn't get force-killed.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Timber.w("DatabaseMigrationService: onTimeout — stopping foreground service after system-imposed limit")
        stopService()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String) {
        val channel = notificationManager.getNotificationChannel(channelId)
        if (channel == null) {
            val channelNew = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            channelNew.lightColor = Color.BLUE
            channelNew.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            channelNew.setSound(null, null)
            channelNew.enableLights(false)
            channelNew.enableVibration(false)
            notificationManager.createNotificationChannel(channelNew)
        } else {
            Timber.v("Channel already exists: %s", CHANNEL_ID)
        }
    }
}
