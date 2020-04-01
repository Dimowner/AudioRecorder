package com.dimowner.audiorecorder;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.concurrent.CountDownLatch;

import timber.log.Timber;

public class BackgroundQueue extends Thread {

	private volatile Handler handler = null;
	private CountDownLatch countDownLatch = new CountDownLatch(1);

	public BackgroundQueue(final String threadName) {
		setName(threadName);
		start();
	}

	public void postRunnable(Runnable runnable) {
		postRunnable(runnable, 0);
	}

	public void postRunnable(Runnable runnable, long delay) {
		try {
			countDownLatch.await();
			if (delay <= 0) {
				handler.post(runnable);
			} else {
				handler.postDelayed(runnable, delay);
			}
		} catch (Exception e) {
			Timber.e(e);
		}
	}

	public void cancelRunnable(Runnable runnable) {
		try {
			countDownLatch.await();
			handler.removeCallbacks(runnable);
		} catch (Exception e) {
			Timber.e(e);
		}
	}

	public void cleanupQueue() {
		try {
			countDownLatch.await();
			handler.removeCallbacksAndMessages(null);
		} catch (Exception e) {
			Timber.e(e);
		}
	}

	public void handleMessage(Message inputMessage) {
	}

	public void close() {
		handler.getLooper().quit();
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				BackgroundQueue.this.handleMessage(msg);
			}
		};
		countDownLatch.countDown();
		Looper.loop();
	}
}
