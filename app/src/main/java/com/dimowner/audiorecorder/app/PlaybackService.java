/*
 * Copyright 2020 Dmitriy Ponomarenko
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
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.exception.AppException;

import androidx.core.app.NotificationManagerCompat;
import timber.log.Timber;

public class PlaybackService extends Service {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.NotificationId";

	public static final String ACTION_START_PLAYBACK_SERVICE = "ACTION_START_PLAYBACK_SERVICE";

	public static final String ACTION_PAUSE_PLAYBACK = "ACTION_PAUSE_PLAYBACK";

	public static final String ACTION_CLOSE = "ACTION_CLOSE";

	public static final String EXTRAS_KEY_RECORD_NAME = "record_name";

	private static final int NOTIF_ID = 101;
	private NotificationCompat.Builder builder;
	private NotificationManagerCompat notificationManager;
	private RemoteViews remoteViewsSmall;
//	private RemoteViews remoteViewsBig;
	private Notification notification;
	private String recordName = "";

	private PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private ColorMap colorMap;

	public PlaybackService() {
	}

	public static void startServiceForeground(Context context, String name) {
		Intent intent = new Intent(context, PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_START_PLAYBACK_SERVICE);
		intent.putExtra(PlaybackService.EXTRAS_KEY_RECORD_NAME, name);
		context.startService(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		audioPlayer = ARApplication.getInjector().provideAudioPlayer();
		colorMap = ARApplication.getInjector().provideColorMap();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {

			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_PLAYBACK_SERVICE:
						if (intent.hasExtra(EXTRAS_KEY_RECORD_NAME)) {
							startForeground(intent.getStringExtra(EXTRAS_KEY_RECORD_NAME));
						}
						break;
					case ACTION_PAUSE_PLAYBACK:
						audioPlayer.playOrPause();
						break;
					case ACTION_CLOSE:
						audioPlayer.stop();
						stopForegroundService();
						break;
				}
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public void startForeground(String name) {
		recordName = name;
		if (playerCallback == null) {
			playerCallback = new PlayerContract.PlayerCallback() {
				@Override public void onPreparePlay() {}
				@Override public void onPlayProgress(final long mills) {}
				@Override public void onStopPlay() {
					stopForegroundService();
				}
				@Override public void onSeek(long mills) {}
				@Override public void onError(AppException throwable) {
					stopForegroundService();
				}

				@Override
				public void onStartPlay() {
					onStartPlayback();
				}

				@Override
				public void onPausePlay() {
					onPausePlayback();
				}
			};
		}

		this.audioPlayer.addPlayerCallback(playerCallback);
		startForegroundService();
	}

	private void startForegroundService() {
		notificationManager = NotificationManagerCompat.from(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.layout_play_notification_small);
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CLOSE));
		remoteViewsSmall.setTextViewText(R.id.txt_name, recordName);
		remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

//		remoteViewsBig = new RemoteViews(getPackageName(), R.layout.layout_play_notification_big);
//		remoteViewsBig.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
//		remoteViewsBig.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CLOSE));
//		remoteViewsBig.setTextViewText(R.id.txt_name, recordName);
//		remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		// Create notification default intent.
		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

		// Create notification builder.
		builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setContentTitle(getResources().getString(R.string.app_name));
		builder.setSmallIcon(R.drawable.ic_play_circle);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_LOW);
		} else {
			builder.setPriority(Notification.PRIORITY_LOW);
		}
		// Make head-up notification.
		builder.setContentIntent(pendingIntent);
		builder.setCustomContentView(remoteViewsSmall);
//		builder.setCustomBigContentView(remoteViewsBig);
		builder.setOngoing(true);
		builder.setOnlyAlertOnce(true);
		builder.setDefaults(0);
		builder.setSound(null);
		notification = builder.build();
		startForeground(NOTIF_ID, notification);
	}

	public void stopForegroundService() {
		audioPlayer.removePlayerCallback(playerCallback);
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
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			chan.setSound(null,null);
			chan.enableLights(false);
			chan.enableVibration(false);

			notificationManager.createNotificationChannel(chan);
		} else {
			Timber.d("Channel already exists: " + CHANNEL_ID);
		}
		return channelId;
	}

	public void onPausePlayback() {
//		if (remoteViewsBig != null && remoteViewsSmall != null) {
		if (remoteViewsSmall != null) {
//			remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_play);
			remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_play);
			builder.setOngoing(false);
			notificationManager.notify(NOTIF_ID, notification);
		}
	}

	public void onStartPlayback() {
//		if (remoteViewsBig != null && remoteViewsSmall != null) {
		if (remoteViewsSmall != null) {
//			remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause);
			remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause);
			builder.setOngoing(true);
			notificationManager.notify(NOTIF_ID, notification);
		}
	}

	public static class StopPlaybackReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Intent stopIntent = new Intent(context, PlaybackService.class);
			stopIntent.setAction(intent.getAction());
			context.startService(stopIntent);
		}
	}
}
