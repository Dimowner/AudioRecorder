package com.dimowner.audiorecorder.ui;

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
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import timber.log.Timber;

public class PlaybackService extends Service implements MainContract.SimpleView {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.NotificationId";

	public static final String ACTION_START_PLAYBACK_SERVICE = "ACTION_START_PLAYBACK_SERVICE";

	public static final String ACTION_STOP_PLAYBACK_SERVICE = "ACTION_STOP_PLAYBACK_SERVICE";

	public static final String ACTION_PAUSE_PLAYBACK = "ACTION_PAUSE_PLAYBACK";

	public static final String ACTION_PLAY_NEXT = "ACTION_PLAY_NEXT";

	public static final String ACTION_PLAY_PREV = "ACTION_PLAY_PREV";

	private static final int NOTIF_ID = 101;
	private NotificationCompat.Builder builder;
	private NotificationManager notificationManager;
	private RemoteViews remoteViewsSmall;
	private RemoteViews remoteViewsBig;
	private Notification notification;

	private MainContract.UserActionsListener presenter;
	private ColorMap colorMap;

	public PlaybackService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		presenter = ARApplication.getInjector().provideMainPresenter();
		colorMap = ARApplication.getInjector().provideColorMap();
		presenter.bindSimpleView(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_PLAYBACK_SERVICE:
						Timber.v("ACTION_START_PLAYBACK_SERVICE");
						startForegroundService();
						break;
					case ACTION_STOP_PLAYBACK_SERVICE:
						Timber.v("ACTION_STOP_PLAYBACK_SERVICE");
						stopForegroundService();
						break;
					case ACTION_PAUSE_PLAYBACK:
						Timber.v("ACTION_PAUSE_PLAYBACK");
						presenter.startPlayback();
						break;
					case ACTION_PLAY_NEXT:
						Timber.v("ACTION_PLAY_NEXT");
						break;
					case ACTION_PLAY_PREV:
						Timber.v("ACTION_PLAY_PREV");
						presenter.pausePlayback();
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

		remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.layout_play_notification_small);
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
//		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_next, getPendingSelfIntent(getApplicationContext(), ACTION_PLAY_NEXT));
//		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_prev, getPendingSelfIntent(getApplicationContext(), ACTION_PLAY_PREV));
		remoteViewsSmall.setTextViewText(R.id.txt_playback_progress, TimeUtils.formatTimeIntervalMinSecMills(0));
		remoteViewsSmall.setTextViewText(R.id.txt_name, "Record2222");
		remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		remoteViewsBig = new RemoteViews(getPackageName(), R.layout.layout_play_notification_big);
		remoteViewsBig.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
//		remoteViewsBig.setOnClickPendingIntent(R.id.btn_next, getPendingSelfIntent(getApplicationContext(), ACTION_PLAY_NEXT));
//		remoteViewsBig.setOnClickPendingIntent(R.id.btn_prev, getPendingSelfIntent(getApplicationContext(), ACTION_PLAY_PREV));
		remoteViewsBig.setTextViewText(R.id.txt_playback_progress, TimeUtils.formatTimeIntervalMinSecMills(0));
		remoteViewsBig.setTextViewText(R.id.txt_name, FileUtil.removeFileExtension(presenter.getRecordName()));
		remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		// Create notification default intent.
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

		// Create notification builder.
		builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setSmallIcon(R.drawable.ic_play_circle);
		builder.setPriority(Notification.PRIORITY_MAX);
		// Make head-up notification.
		builder.setContentIntent(pendingIntent);
		builder.setCustomContentView(remoteViewsSmall);
		builder.setCustomBigContentView(remoteViewsBig);
		builder.setOngoing(true);
		notification = builder.build();
		startForeground(NOTIF_ID, notification);
	}

	private void stopForegroundService() {
		presenter.unbindSimpleView();
		stopForeground(true);
		stopSelf();
	}

	protected PendingIntent getPendingSelfIntent(Context context, String action) {
		Intent intent = new Intent(context, StopPlaybackReceiver.class);
		intent.setAction(action);
		return PendingIntent.getBroadcast(context, 10, intent, 0);
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel(String channelId, String channelName) {
		NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
		if (channel == null) {
			Timber.v("Create notification channel: " + CHANNEL_ID);
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			notificationManager.createNotificationChannel(chan);
		} else {
			Timber.v("Channel already exists: " + CHANNEL_ID);
		}
		return channelId;
	}

	private void updateNotification(long mills) {
		Timber.v("updateNotification: " + mills);
		remoteViewsSmall.setTextViewText(R.id.txt_playback_progress,
				getResources().getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

		remoteViewsBig.setTextViewText(R.id.txt_playback_progress,
				getResources().getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

		notificationManager.notify(NOTIF_ID, builder.build());
	}

	@Override
	public void onPlayProgress(long mills) {
		Timber.v("onPlayProgress: " + mills);
//		if (mills%1000 == 0) {
			updateNotification(mills);
//		}
	}

	@Override
	public void onRecordingProgress(long mills, int amp) { }

	@Override
	public void onPausePlayback() {
		Timber.v("onPausePlayback");
//		remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));
		remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_play);
		remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_play);
		builder.setOngoing(false);
		notificationManager.notify(NOTIF_ID, builder.build());
	}

	@Override
	public void onStartPlayback() {
		Timber.v("onStartPlayback");
		remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause);
		remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause);
		builder.setOngoing(true);
		notificationManager.notify(NOTIF_ID, builder.build());
	}

	public static class StopPlaybackReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Timber.v("ON RECEIVE StopPlaybackReceiver");
			Intent stopIntent = new Intent(context, PlaybackService.class);
			stopIntent.setAction(intent.getAction());
			context.startService(stopIntent);
		}
	}
}
