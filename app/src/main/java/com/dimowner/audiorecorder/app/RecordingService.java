package com.dimowner.audiorecorder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;

import timber.log.Timber;

public class RecordingService extends Service {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.NotificationId";

	public static final String ACTION_START_RECORDING_SERVICE = "ACTION_START_RECORDING_SERVICE";

	public static final String ACTION_STOP_RECORDING_SERVICE = "ACTION_STOP_RECORDING_SERVICE";

	public static final String ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING";

	private static final int NOTIF_ID = 101;
	private NotificationCompat.Builder builder;
	private NotificationManager notificationManager;
	private RemoteViews remoteViewsSmall;
	private RemoteViews remoteViewsBig;
	private Notification notification;

	private AppRecorder appRecorder;
	private AppRecorderCallback appRecorderCallback;
	private ColorMap colorMap;
	private boolean started = false;

	public RecordingService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		appRecorder = ARApplication.getInjector().provideAppRecorder();
		colorMap = ARApplication.getInjector().provideColorMap();
		appRecorderCallback = new AppRecorderCallback() {
			@Override public void onRecordingStarted() { }
			@Override public void onRecordingPaused() { }
			@Override public void onRecordProcessing() { }
			@Override public void onRecordFinishProcessing() { }
			@Override public void onRecordingStopped(long id, File file) { }

			@Override
			public void onRecordingProgress(long mills, int amp) {
				if (mills%1000 == 0) {
					updateNotification(mills);
				}
			}

			@Override public void onError(AppException throwable) { }
		};
		appRecorder.addRecordingCallback(appRecorderCallback);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_RECORDING_SERVICE:
						startForegroundService();
						break;
					case ACTION_STOP_RECORDING_SERVICE:
						stopForegroundService();
						break;
					case ACTION_STOP_RECORDING:
						appRecorder.stopRecording();
						stopForegroundService();
						break;
				}
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	private void startForegroundService() {
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.layout_record_notification_small);
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_recording_stop, getPendingSelfIntent(getApplicationContext(), ACTION_STOP_RECORDING));
		remoteViewsSmall.setTextViewText(R.id.txt_recording_progress, TimeUtils.formatTimeIntervalMinSecMills(0));
		remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		remoteViewsBig = new RemoteViews(getPackageName(), R.layout.layout_record_notification_big);
		remoteViewsBig.setOnClickPendingIntent(R.id.btn_recording_stop, getPendingSelfIntent(getApplicationContext(), ACTION_STOP_RECORDING));
		remoteViewsBig.setTextViewText(R.id.txt_recording_progress, TimeUtils.formatTimeIntervalMinSecMills(0));
		remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		// Create notification default intent.
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

		// Create notification builder.
		builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(R.drawable.ic_record_rec);
		builder.setPriority(Notification.PRIORITY_MAX);
		// Make head-up notification.
		builder.setContentIntent(pendingIntent);
		builder.setCustomContentView(remoteViewsSmall);
		builder.setCustomBigContentView(remoteViewsBig);
		builder.setOnlyAlertOnce(true);
		builder.setDefaults(0);
		builder.setSound(null);
		notification = builder.build();
		startForeground(NOTIF_ID, notification);
		started = true;
	}

	private void stopForegroundService() {
		appRecorder.removeRecordingCallback(appRecorderCallback);
		stopForeground(true);
		stopSelf();
		started = false;
	}

	protected PendingIntent getPendingSelfIntent(Context context, String action) {
		Intent intent = new Intent(context, StopRecordingReceiver.class);
		intent.setAction(action);
		return PendingIntent.getBroadcast(context, 10, intent, 0);
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel(String channelId, String channelName) {
		NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
		if (channel == null) {
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			chan.setSound(null,null);
			chan.enableLights(false);
			chan.enableVibration(false);

			notificationManager.createNotificationChannel(chan);
		}
		return channelId;
	}

	private void updateNotification(long mills) {
		if (started) {
			remoteViewsSmall.setTextViewText(R.id.txt_recording_progress,
					getResources().getString(R.string.recording, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

			remoteViewsBig.setTextViewText(R.id.txt_recording_progress,
					getResources().getString(R.string.recording, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

			notificationManager.notify(NOTIF_ID, notification);
		}
	}

	public static class StopRecordingReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Intent stopIntent = new Intent(context, RecordingService.class);
			stopIntent.setAction(intent.getAction());
			context.startService(stopIntent);
		}
	}
}
