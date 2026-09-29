package com.dimowner.audiorecorder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
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

/**
 * The tap starts [TransparentRecordingActivity] directly, and must not be routed through a
 * BroadcastReceiver that starts the activity itself. The permission to start an activity from the
 * background travels with this PendingIntent, granted because the launcher sending it is visible,
 * and it does not survive that extra hop: the receiver's own start carries no such grant and the
 * system blocks it ("Activity start request from <uid> stopped") for every tap outside the short
 * grace period that follows the app being in the foreground. That is why only the first tap after
 * using the app started a recording.
 *
 * The flags keep the activity out of the app's own task. Without them it lands on top of the task
 * the app is already using, which brings whatever screen was last open - Settings, for example -
 * to the front instead of leaving the user where they were; [FLAG_ACTIVITY_CLEAR_TASK] makes each
 * tap start a fresh instance, so one left waiting on a permission dialog cannot swallow the next
 * tap. The matching empty taskAffinity is declared in the manifest.
 */
@SuppressLint("WrongConstant")
private fun getRecordingPendingIntent(context: Context): PendingIntent {
	val intent = Intent(context, TransparentRecordingActivity::class.java)
	intent.flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
	return PendingIntent.getActivity(
		context,
		11,
		intent,
		AppConstants.PENDING_INTENT_FLAGS or PendingIntent.FLAG_UPDATE_CURRENT
	)
}
