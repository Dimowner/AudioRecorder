package com.dimowner.audiorecorder.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;

import timber.log.Timber;

public class RecordingService extends Service {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.NotificationId";

	private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";

	public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";

	public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

	public static final String ACTION_PAUSE = "ACTION_PAUSE";

	public static final String ACTION_CLOSE = "ACTION_CLOSE";

	private MainContract.UserActionsListener presenter;

	public RecordingService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Timber.v("My foreground service onCreate().");
		presenter = ARApplication.getInjector().provideMainPresenter();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();

			switch (action) {
				case ACTION_START_FOREGROUND_SERVICE:
					Timber.v("StartForeground");
					startForegroundService();
					Toast.makeText(getApplicationContext(), "Foreground service is started.", Toast.LENGTH_LONG).show();
					break;
				case ACTION_STOP_FOREGROUND_SERVICE:
					Timber.v("StopForeground");
					stopForegroundService();
					Toast.makeText(getApplicationContext(), "Foreground service is stopped.", Toast.LENGTH_LONG).show();
					break;
				case ACTION_CLOSE:
					Toast.makeText(getApplicationContext(), "You click Play button.", Toast.LENGTH_LONG).show();
					stopForegroundService();
					break;
				case ACTION_PAUSE:
					Toast.makeText(getApplicationContext(), "You click Pause button.", Toast.LENGTH_LONG).show();
					presenter.recordingClicked();
					break;
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	/* Used to build and start foreground service. */
	private void startForegroundService() {
		Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		// Create notification default intent.
		Intent intent = new Intent();
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

		// Create notification builder.
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		// Make notification show big text.
		NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
		bigTextStyle.setBigContentTitle("Microphone recording:");
//		bigTextStyle.bigText("Android foreground service is a android service which can run in foreground always, it can be controlled by user via notification.");
		// Set big text style.
		builder.setStyle(bigTextStyle);

		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(R.drawable.dna);
//		Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dna);
//		builder.setLargeIcon(largeIconBitmap);
		// Make the notification max priority.
		builder.setPriority(Notification.PRIORITY_MAX);
		// Make head-up notification.
		builder.setFullScreenIntent(pendingIntent, true);

		// Add Play button intent in notification.
		Intent playIntent = new Intent(this, RecordingService.class);
		playIntent.setAction(ACTION_CLOSE);
		PendingIntent pendingPlayIntent = PendingIntent.getService(this, 0, playIntent, 0);
		NotificationCompat.Action playAction = new NotificationCompat.Action(R.drawable.round_arrow_back, "Stop", pendingPlayIntent);
		builder.addAction(playAction);

		// Add Pause button intent in notification.
		Intent pauseIntent = new Intent(this, RecordingService.class);
		pauseIntent.setAction(ACTION_PAUSE);
		PendingIntent pendingPrevIntent = PendingIntent.getService(this, 0, pauseIntent, 0);
		NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.pause, "Pause", pendingPrevIntent);
		builder.addAction(prevAction);

		// Build the notification.
		Notification notification = builder.build();

		// Start foreground service.
		startForeground(1, notification);

	}


	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel(String channelId, String channelName) {
		NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationChannel channel = service.getNotificationChannel(channelId);
		if (channel == null) {
			Timber.v("Create notification channel: " + CHANNEL_ID);
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			service.createNotificationChannel(chan);
		} else {
			Timber.v("Channel already exists: " + CHANNEL_ID);
		}
		return channelId;
	}

	private void stopForegroundService() {
		Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.");

		// Stop foreground service and remove the notification.
		stopForeground(true);

		// Stop the foreground service.
		stopSelf();
	}
}
