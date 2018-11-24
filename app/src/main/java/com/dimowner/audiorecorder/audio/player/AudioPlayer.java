/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.audio.player;

import android.media.AudioManager;
import android.media.MediaPlayer;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.util.AndroidUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

public class AudioPlayer implements PlayerContract.Player {

	private List<PlayerContract.PlayerCallback> actionsListeners = new ArrayList<>();

	private MediaPlayer mediaPlayer;
	private Timer timerProgress;
	private boolean isPrepared = false;


	private static class SingletonHolder {
		private static AudioPlayer singleton = new AudioPlayer();

		public static AudioPlayer getSingleton() {
			return SingletonHolder.singleton;
		}
	}

	public static AudioPlayer getInstance() {
		return SingletonHolder.getSingleton();
	}

//	public AudioPlayer() {}
//
//	public AudioPlayer(PlayerContract.PlayerCallback playerCallback) {
//		this.actionsListeners.add(playerCallback);
//	}

	@Override
	public void addPlayerCallback(PlayerContract.PlayerCallback callback) {
		actionsListeners.add(callback);
	}

	@Override
	public boolean removePlayerCallback(PlayerContract.PlayerCallback callback) {
		return actionsListeners.remove(callback);
	}

	@Override
	public void setData(String data) {
		try {
			isPrepared = false;
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setDataSource(data);
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} catch (IOException e) {
			Timber.e(e);
			onError(e);
		}
	}

	@Override
	public void playOrPause() {
		if (mediaPlayer != null) {
			if (mediaPlayer.isPlaying()) {
				pause();
			} else {
				try {
					if (!isPrepared) {
						mediaPlayer.prepare();
						onPreparePlay();
						isPrepared = true;
					}

					mediaPlayer.start();
					onStartPlay();
					mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp) {
							stop();
							onStopPlay();
						}
					});

					timerProgress = new Timer();
					timerProgress.schedule(new TimerTask() {
						@Override
						public void run() {
							if (mediaPlayer != null && mediaPlayer.isPlaying()) {
								onPlayProgress(mediaPlayer.getCurrentPosition());
							}
						}
					}, 0, AppConstants.VISUALIZATION_INTERVAL);
				} catch (IOException e) {
					Timber.e(e);
					onError(e);
				}
			}
		}
	}

	@Override
	public void seek(int pixels) {
		if (mediaPlayer != null) {
			double mills = AndroidUtils.convertPxToMills(pixels);
			mediaPlayer.seekTo((int) mills);
			onSeek((int) mills);
		}
	}

	@Override
	public void pause() {
		if (timerProgress != null) {
			timerProgress.cancel();
			timerProgress.purge();
		}
		if (mediaPlayer != null) {
			if (mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
				onPausePlay();
			}
		}
	}

	@Override
	public void stop() {
		if (timerProgress != null) {
			timerProgress.cancel();
			timerProgress.purge();
		}
		if (mediaPlayer != null) {
			mediaPlayer.stop();
			mediaPlayer.setOnCompletionListener(null);
			isPrepared = false;
			onStopPlay();
			mediaPlayer.getCurrentPosition();
		}
	}

	@Override
	public boolean isPlaying() {
		return mediaPlayer != null && mediaPlayer.isPlaying();
	}

	@Override
	public void release() {
		stop();
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
		isPrepared = false;
		actionsListeners.clear();
	}

	private void onPreparePlay() {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onPreparePlay();
			}
		}
	}

	private  void onStartPlay() {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onStartPlay();
			}
		}
	}

	private void onPlayProgress(long mills) {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onPlayProgress(mills);
			}
		}
	}

	private void onStopPlay() {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onStopPlay();
			}
		}
	}

	private void onPausePlay() {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onPausePlay();
			}
		}
	}

	private void onSeek(long mills) {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onSeek(mills);
			}
		}
	}

	private void onError(Throwable throwable) {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onError(throwable);
			}
		}
	}
}
