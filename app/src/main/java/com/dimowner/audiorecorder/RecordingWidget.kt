package com.dimowner.audiorecorder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.widget.RemoteViews
import com.dimowner.audiorecorder.app.TransparentRecordingActivity

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
		val activityIntent = Intent(context, TransparentRecordingActivity::class.java)
		activityIntent.flags = FLAG_ACTIVITY_NEW_TASK
		context.startActivity(activityIntent)
	}
}
