/*
 * Copyright 2020 Dmytro Ponomarenko
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

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import android.annotation.SuppressLint;
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

import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.util.DownloadManagerKt;
import com.dimowner.audiorecorder.util.ExtensionsKt;
import com.dimowner.audiorecorder.util.OnCopyListListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

	public static final String EXTRAS_KEY_DOWNLOAD_INFO = "key_download_info";

	private static final int NOTIF_ID = 103;
	private NotificationManagerCompat notificationManager;
	private PendingIntent contentPendingIntent;
	private String downloadingRecordName = "";
	private int downloadProgress = 0;
	private int notifiedProgress = -1;
	private BackgroundQueue copyTasks;
	private ColorMap colorMap;
	private boolean isCancel = false;

	public DownloadService() {
	}

	public static void startNotification(Context context, String downloadInfo) {
		ArrayList<String> list = new ArrayList<>();
		list.add(downloadInfo);
		startNotification(context, list);
	}

	public static void startNotification(Context context, ArrayList<String> downloadInfoList) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.setAction(ACTION_START_DOWNLOAD_SERVICE);
		intent.putStringArrayListExtra(EXTRAS_KEY_DOWNLOAD_INFO, downloadInfoList);
		context.startService(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		colorMap = ARApplication.getInjector().provideColorMap(getApplicationContext());
		copyTasks = ARApplication.getInjector().provideCopyTasksQueue();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_DOWNLOAD_SERVICE:
						if (intent.hasExtra(EXTRAS_KEY_DOWNLOAD_INFO)) {
							startDownload(intent.getStringArrayListExtra(EXTRAS_KEY_DOWNLOAD_INFO));
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

	public void startDownload(ArrayList<String> list) {
		if (list == null || list.isEmpty()) {
			stopService();
		} else {
			List<File> files = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				files.add(new File(list.get(i)));
			}
			startNotification();
			copyFiles(files);
		}
	}

	private void copyFiles(final List<File> list) {
		isCancel = false;
		copyTasks.postRunnable(new Runnable() {
			long prevTime = 0;
			@Override
			public void run() {
				DownloadManagerKt.downloadFiles(getApplicationContext(), list,
						new OnCopyListListener() {
							@Override
							public void onStartCopy(@NotNull String name) {
								updateNotificationText(name);
							}

							@Override
							public boolean isCancel() {
								return isCancel;
							}

							@Override
							public void onCopyProgress(int percent) {
								long curTime = System.currentTimeMillis();
								if (percent >= 95) {
									percent = 100;
								}
								if (percent == 100 || curTime > prevTime + 200) {
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
							public void onCopyFinish(String message) {
								Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
								stopService();
							}

							@Override
							public void onError(String message) {
								Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
								stopService();
							}
						});
			}
		});
	}

	@SuppressLint("WrongConstant")
	private void startNotification() {
		notificationManager = NotificationManagerCompat.from(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		// Create notification default intent.
		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		contentPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, AppConstants.PENDING_INTENT_FLAGS);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			startForeground(NOTIF_ID, buildNotification());
		} else {
			startForeground(NOTIF_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC);
		}
	}

	/**
	 * Builds a fresh {@link RemoteViews} reflecting the current download state.
	 *
	 * RemoteViews accumulates every setter call in an internal action list that is serialized on
	 * each notify(). Reusing a single instance across progress updates grows that list without
	 * bound and eventually breaks the binder transaction limit
	 * (see TransactionTooLargeException in NotificationManager.notify), so a new instance is
	 * created for every notification instead.
	 */
	private RemoteViews buildRemoteViews() {
		boolean isNightMode = ExtensionsKt.isUsingNightModeResources(getApplicationContext());

		RemoteViews views = new RemoteViews(getPackageName(), R.layout.layout_progress_notification);
		views.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CANCEL_DOWNLOAD));
		views.setTextViewText(R.id.txt_name, getResources().getString(R.string.downloading, downloadingRecordName));
		views.setProgressBar(R.id.progress, 100, downloadProgress, false);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			views.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));
			views.setInt(R.id.app_logo, "setVisibility", View.VISIBLE);
		} else {
			views.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(R.color.transparent));
			views.setInt(R.id.app_logo, "setVisibility", View.GONE);
			if (isNightMode) {
				views.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_light));
				views.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_secondary_light));
				views.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close);
			} else {
				views.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_dark));
				views.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_secondary_dark));
				views.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_round_close_dark);
			}
		}
		return views;
	}

	private Notification buildNotification() {
		// Create notification builder.
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setContentTitle(getResources().getString(R.string.app_name));
		builder.setSmallIcon(R.drawable.ic_save_alt);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT);
		} else {
			builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
		}
		// Make head-up notification.
		builder.setContentIntent(contentPendingIntent);
		builder.setCustomContentView(buildRemoteViews());
		builder.setCustomBigContentView(buildRemoteViews());
		builder.setOngoing(true);
		builder.setOnlyAlertOnce(true);
		builder.setDefaults(0);
		builder.setSound(null);
		return builder.build();
	}

	public void stopService() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
			stopForeground(STOP_FOREGROUND_REMOVE);
		} else {
            stopForeground(true);
		}
		stopSelf();
	}

	/**
	 * Called by the system on API 35+ when a {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_DATA_SYNC}
	 * foreground service has been running for the maximum allowed duration (6 hours).
	 * Stop the service gracefully so it doesn't get force-killed.
	 */
	@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
	@Override
	public void onTimeout(int startId, int fgsType) {
		super.onTimeout(startId, fgsType);
		timber.log.Timber.w("DownloadService: onTimeout — stopping foreground service after system-imposed limit");
		stopService();
	}

	@SuppressLint("WrongConstant")
	protected PendingIntent getPendingSelfIntent(Context context, String action) {
		Intent intent = new Intent(context, StopDownloadReceiver.class);
		intent.setAction(action);
		return PendingIntent.getBroadcast(context, 10, intent, AppConstants.PENDING_INTENT_FLAGS);
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private void createNotificationChannel(String channelId, String channelName) {
		NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
		if (channel == null) {
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
			chan.setSound(null, null);
			chan.enableLights(false);
			chan.enableVibration(false);

			notificationManager.createNotificationChannel(chan);
		} else {
			Timber.v("DownloadService: Channel already exists: %s", CHANNEL_ID);
		}
	}

	private void updateNotification(int percent) {
		if (percent == notifiedProgress) {
			return;
		}
		downloadProgress = percent;
		notifiedProgress = percent;
		notificationManager.notify(NOTIF_ID, buildNotification());
	}

	private void updateNotificationText(String text) {
		downloadingRecordName = text;
		notificationManager.notify(NOTIF_ID, buildNotification());
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
