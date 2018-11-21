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
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

public class AudioPlayer implements PlayerContract.UserActions {

	private PlayerContract.PlayerActions actionsListener;

	private MediaPlayer mediaPlayer;
	private Timer timerProgress;
	private boolean isPrepared = false;

	public AudioPlayer(PlayerContract.PlayerActions playerActions) {
		this.actionsListener = playerActions;
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
			actionsListener.onError(e);
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
						if (actionsListener != null) {
							actionsListener.onPreparePlay();
						}
						isPrepared = true;
					}

					mediaPlayer.start();
					if (actionsListener != null) {
						actionsListener.onStartPlay();
					}
					mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp) {
							stop();
							if (actionsListener != null) {
								actionsListener.onStopPlay();
							}
						}
					});

					timerProgress = new Timer();
					timerProgress.schedule(new TimerTask() {
						@Override
						public void run() {
							if (actionsListener != null && mediaPlayer != null && mediaPlayer.isPlaying()) {
								actionsListener.onPlayProgress(mediaPlayer.getCurrentPosition());
							}
						}
					}, 0, AppConstants.VISUALIZATION_INTERVAL);
				} catch (IOException e) {
					Timber.e(e);
					actionsListener.onError(e);
				}
			}
		}
	}

	@Override
	public void seek(int pixels) {
		if (mediaPlayer != null) {
			double mills = AndroidUtils.convertPxToMills(pixels);
			mediaPlayer.seekTo((int) mills);
			if (actionsListener != null) {
				actionsListener.onSeek((int) mills);
			}
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
				if (actionsListener != null) {
					actionsListener.onPausePlay();
				}
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
			isPrepared = false;
			if (actionsListener != null) {
				actionsListener.onStopPlay();
			}
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
	}
}
