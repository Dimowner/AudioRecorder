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
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.exception.PlayerDataSourceException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

public class AudioPlayer implements PlayerContract.Player, MediaPlayer.OnPreparedListener {

	private List<PlayerContract.PlayerCallback> actionsListeners = new ArrayList<>();

	private MediaPlayer mediaPlayer;
	private Timer timerProgress;
	private boolean isPrepared = false;
	private long seekPos = 0;
	private String dataSource = null;


	private static class SingletonHolder {
		private static AudioPlayer singleton = new AudioPlayer();

		public static AudioPlayer getSingleton() {
			return SingletonHolder.singleton;
		}
	}

	public static AudioPlayer getInstance() {
		return SingletonHolder.getSingleton();
	}

	private AudioPlayer() {}

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
		if (mediaPlayer != null && dataSource != null && dataSource.equals(data)) {
			//Do nothing
		} else {
			try {
				isPrepared = false;
				mediaPlayer = new MediaPlayer();
				mediaPlayer.setDataSource(data);
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				dataSource = data;
			} catch (IOException e) {
				Timber.e(e);
				onError(new PlayerDataSourceException());
			}
		}
	}

	private void restartPlayer() {
		if (dataSource != null) {
			try {
				isPrepared = false;
				mediaPlayer = new MediaPlayer();
				mediaPlayer.setDataSource(dataSource);
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			} catch (IOException e) {
				Timber.e(e);
				onError(new PlayerDataSourceException());
			}
		}
	}

	@Override
	public void playOrPause() {
		try {
			if (mediaPlayer != null) {
				if (mediaPlayer.isPlaying()) {
					pause();
				} else {
					if (!isPrepared) {
						try {
							mediaPlayer.setOnPreparedListener(this);
							mediaPlayer.prepareAsync();
						} catch (IllegalStateException ex) {
							Timber.e(ex);
							restartPlayer();
							mediaPlayer.setOnPreparedListener(this);
							try {
								mediaPlayer.prepareAsync();
							} catch (IllegalStateException e) {
								Timber.e(e);
								restartPlayer();
							}
						}
					} else {
						mediaPlayer.start();
						mediaPlayer.seekTo((int) seekPos);
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
								try {
									if (mediaPlayer != null && mediaPlayer.isPlaying()) {
										int curPos = mediaPlayer.getCurrentPosition();
										onPlayProgress(curPos);
									}
								} catch(IllegalStateException e){
									Timber.e(e, "Player is not initialized!");
								}
							}
						}, 0, AppConstants.VISUALIZATION_INTERVAL);
					}
				}
			}
		} catch(IllegalStateException e){
			Timber.e(e, "Player is not initialized!");
		}
	}

	@Override
	public void onPrepared(final MediaPlayer mp) {
		if (mediaPlayer != mp) {
			mediaPlayer.stop();
			mediaPlayer.release();
			mediaPlayer = mp;
		}
		onPreparePlay();
		isPrepared = true;
		mediaPlayer.start();
		mediaPlayer.seekTo((int) seekPos);
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
				try {
					if (mediaPlayer != null && mediaPlayer.isPlaying()) {
						int curPos = mediaPlayer.getCurrentPosition();
						onPlayProgress(curPos);
					}
				} catch(IllegalStateException e){
					Timber.e(e, "Player is not initialized!");
				}
			}
		}, 0, AppConstants.VISUALIZATION_INTERVAL);
	}

	@Override
	public void seek(long mills) {
		seekPos = mills;
		try {
			if (mediaPlayer != null && mediaPlayer.isPlaying()) {
				mediaPlayer.seekTo((int) seekPos);
				onSeek((int) seekPos);
			}
		} catch(IllegalStateException e){
			Timber.e(e, "Player is not initialized!");
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
				seekPos = mediaPlayer.getCurrentPosition();
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
			seekPos = 0;
		}
	}

	@Override
	public boolean isPlaying() {
		try {
			return mediaPlayer != null && mediaPlayer.isPlaying();
		} catch(IllegalStateException e){
			Timber.e(e, "Player is not initialized!");
		}
		return false;
	}

	@Override
	public void release() {
		stop();
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
		isPrepared = false;
		dataSource = null;
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
			for (int i = actionsListeners.size()-1; i >= 0; i--) {
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

	private void onError(AppException throwable) {
		if (!actionsListeners.isEmpty()) {
			for (int i = 0; i < actionsListeners.size(); i++) {
				actionsListeners.get(i).onError(throwable);
			}
		}
	}
}
