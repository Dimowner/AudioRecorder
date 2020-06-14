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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.OnCopyListener;

import java.io.File;

import androidx.core.app.NotificationManagerCompat;
import timber.log.Timber;

/**
 * Created on 28.03.2020.
 * @author Dimowner
 */
public class DownloadService extends Service {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.Download.Notification";

	public static final String ACTION_START_DOWNLOAD_SERVICE = "ACTION_START_DOWNLOAD_SERVICE";
	public static final String ACTION_STOP_DOWNLOAD_SERVICE = "ACTION_STOP_DOWNLOAD_SERVICE";
	public static final String ACTION_CANCEL_DOWNLOAD = "ACTION_CANCEL_DOWNLOAD";

	public static final String EXTRAS_KEY_RECORD_NAME = "key_record_name";
	public static final String EXTRAS_KEY_RECORD_PATH = "key_record_path";

	private static final int NOTIF_ID = 103;
	private NotificationCompat.Builder builder;
	private NotificationManagerCompat notificationManager;
	private RemoteViews remoteViewsSmall;
	private String recordName = "";
	private BackgroundQueue copyTasks;
	private FileRepository fileRepository;
	private ColorMap colorMap;
	private boolean isCancel = false;

	public DownloadService() {
	}

	public static void startNotification(Context context, String name, String path) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.setAction(ACTION_START_DOWNLOAD_SERVICE);
		intent.putExtra(EXTRAS_KEY_RECORD_NAME, name);
		intent.putExtra(EXTRAS_KEY_RECORD_PATH, path);
		context.startService(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		colorMap = ARApplication.getInjector().provideColorMap();
		copyTasks = ARApplication.getInjector().provideCopyTasksQueue();
		fileRepository = ARApplication.getInjector().provideFileRepository();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_DOWNLOAD_SERVICE:
						if (intent.hasExtra(EXTRAS_KEY_RECORD_NAME) && intent.hasExtra(EXTRAS_KEY_RECORD_PATH)) {
							startDownload(
									intent.getStringExtra(EXTRAS_KEY_RECORD_NAME),
									intent.getStringExtra(EXTRAS_KEY_RECORD_PATH)
							);
						}
						break;
					case ACTION_STOP_DOWNLOAD_SERVICE:
						stopService();
						break;
					case ACTION_CANCEL_DOWNLOAD:
						isCancel = true;
						stopService();
						break;
				}
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public void startDownload(String name, String path) {
		if (name == null || path == null) {
			stopService();
		} else {
			recordName = name;
			copyFile(name, path);
			startNotification();
		}
	}

	private void copyFile(final String name, final String path) {
		isCancel = false;
		copyTasks.postRunnable(new Runnable() {
			long prevTime = 0;
			@Override
			public void run() {
				File file = new File(path, name);
				if (!file.exists()) {
					File created = FileUtil.createFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
					if (created != null) {
						FileUtil.copyFile(new File(path), created,
								new OnCopyListener() {
									@Override
									public boolean isCancel() {
										return isCancel;
									}

									@Override
									public void onCopyProgress(int percent, final long progress, final long total) {
										long curTime = System.currentTimeMillis();
										if (percent >= 95) {
											percent = 100;
										}
										if (percent == 100 || curTime > prevTime + 500) {
											updateNotification(percent);
											prevTime = curTime;
										}
									}

									@Override
									public void onCanceled() {
										Toast.makeText(getApplicationContext(), R.string.downloading_cancel, Toast.LENGTH_LONG).show();
										stopService();
									}

									@Override
									public void onCopyFinish() {
										Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_success, name), Toast.LENGTH_LONG).show();
										stopService();
									}
								});
					} else {
						Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_failed, name), Toast.LENGTH_LONG).show();
					}
				} else {
					stopService();
					Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_failed, name), Toast.LENGTH_LONG).show();
				}
				stopService();
			}
		});
	}

	private void startNotification() {
		notificationManager = NotificationManagerCompat.from(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.layout_download_notification);
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CANCEL_DOWNLOAD));
		remoteViewsSmall.setTextViewText(R.id.txt_name, getResources().getString(R.string.downloading, recordName));
		remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));

		// Create notification default intent.
		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

		// Create notification builder.
		builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setContentTitle(getResources().getString(R.string.app_name));
		builder.setSmallIcon(R.drawable.ic_save_alt);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT);
		} else {
			builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
		}
		// Make head-up notification.
		builder.setContentIntent(pendingIntent);
		builder.setCustomContentView(remoteViewsSmall);
		builder.setOngoing(true);
		builder.setOnlyAlertOnce(true);
		builder.setDefaults(0);
		builder.setSound(null);
		startForeground(NOTIF_ID, builder.build());
	}

	public void stopService() {
		stopForeground(true);
		stopSelf();
	}

	protected PendingIntent getPendingSelfIntent(Context context, String action) {
		Intent intent = new Intent(context, StopDownloadReceiver.class);
		intent.setAction(action);
		return PendingIntent.getBroadcast(context, 10, intent, 0);
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private void createNotificationChannel(String channelId, String channelName) {
		NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
		if (channel == null) {
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
			chan.setSound(null,null);
			chan.enableLights(false);
			chan.enableVibration(false);

			notificationManager.createNotificationChannel(chan);
		} else {
			Timber.v("Channel already exists: %s", CHANNEL_ID);
		}
	}

	private void updateNotification(int percent) {
		remoteViewsSmall.setProgressBar(R.id.progress, 100, percent, false);
		notificationManager.notify(NOTIF_ID, builder.build());
	}

	public static class StopDownloadReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Intent stopIntent = new Intent(context, DownloadService.class);
			stopIntent.setAction(intent.getAction());
			context.startService(stopIntent);
		}
	}
}
