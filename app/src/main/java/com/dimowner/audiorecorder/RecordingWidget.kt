package com.dimowner.audiorecorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.app.RecordingService
import com.dimowner.audiorecorder.app.TransparentRecordingActivity
import com.dimowner.audiorecorder.data.RecordingTarget
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.ErrorParser
import timber.log.Timber

class RecordingWidget : AppWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		// There may be multiple widgets active, so update all of them
		for (appWidgetId in appWidgetIds) {
			updateAppWidget(context, appWidgetManager, appWidgetId)
		}
	}

	override fun onEnabled(context: Context) {
		// Enter relevant functionality for when the first widget is created
	}

	override fun onDisabled(context: Context) {
		// Enter relevant functionality for when the last widget is disabled
	}
}

internal fun updateAppWidget(
	context: Context,
	appWidgetManager: AppWidgetManager,
	appWidgetId: Int
) {
	val views = RemoteViews(context.packageName, R.layout.recording_widget)
	views.setOnClickPendingIntent(R.id.btn_record, getRecordingPendingIntent(context))

	// Instruct the widget manager to update the widget
	appWidgetManager.updateAppWidget(appWidgetId, views)
}

@SuppressLint("WrongConstant")
private fun getRecordingPendingIntent(context: Context): PendingIntent {
	val intent = Intent(context, WidgetReceiver::class.java)
	return PendingIntent.getBroadcast(context, 11, intent, AppConstants.PENDING_INTENT_FLAGS)
}

class WidgetReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val fileRepository = ARApplication.injector.provideFileRepository(context)

		// Check permissions first
		if (!hasRecordingPermissions(context)) {
			launchTransparentActivity(context)
			return
		}

		try {
			val target = fileRepository.provideRecordingTarget(context)
			startRecordingService(context, target)
		} catch (e: CantCreateFileException) {
			Timber.e(e, "Failed to create recording file from widget")
			Toast.makeText(context, ErrorParser.parseException(e), Toast.LENGTH_LONG).show()
		}
	}

	private fun hasRecordingPermissions(context: Context): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				return false
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				return false
			}
		}
		return true
	}

	private fun startRecordingService(context: Context, target: RecordingTarget) {
		val startIntent = Intent(context, RecordingService::class.java).apply {
			action = RecordingService.ACTION_START_RECORDING_SERVICE
			putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, target.path)
			if (target.isSaf) {
				putExtra(RecordingService.EXTRAS_KEY_SAF_URI, target.safUri.toString())
			}
		}
		ContextCompat.startForegroundService(context, startIntent)
	}

	private fun launchTransparentActivity(context: Context) {
		val activityIntent = Intent(context, TransparentRecordingActivity::class.java)
		activityIntent.flags = FLAG_ACTIVITY_NEW_TASK
		context.startActivity(activityIntent)
	}
}
