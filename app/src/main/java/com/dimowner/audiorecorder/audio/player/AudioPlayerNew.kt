/*
 * Copyright 2020 Dmytro Ponomarenko
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
package com.dimowner.audiorecorder.audio.player

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.os.Handler
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.PlayerDataSourceException
import com.dimowner.audiorecorder.exception.PlayerInitException
import timber.log.Timber
import java.util.*

class AudioPlayerNew: PlayerContractNew.Player, OnPreparedListener {

	private val actionsListeners: MutableList<PlayerContractNew.PlayerCallback> = ArrayList()

	private var mediaPlayer: MediaPlayer = MediaPlayer()
	private var playerState = PlayerState.STOPPED
	private var pauseTimeMills: Long = 0
	private var prevPosMills: Long = 0
	private val handler = Handler()

	override fun addPlayerCallback(callback: PlayerContractNew.PlayerCallback) {
		actionsListeners.add(callback)
	}

	override fun removePlayerCallback(callback: PlayerContractNew.PlayerCallback): Boolean {
		return actionsListeners.remove(callback)
	}

	private fun restartPlayer(dataSource: String) {
		try {
			playerState = PlayerState.STOPPED
			mediaPlayer.reset()
			mediaPlayer.setDataSource(dataSource)
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
		} catch (e: Exception) {
			Timber.e(e)
			onError(PlayerDataSourceException())
		}
	}

	override fun play(filePath: String) {
		try {
			if (playerState != PlayerState.PLAYING) {
				restartPlayer(filePath)
				try {
					mediaPlayer.setOnPreparedListener(this)
					mediaPlayer.prepareAsync()
				} catch (ex: IllegalStateException) {
					Timber.e(ex)
					restartPlayer(filePath)
					mediaPlayer.setOnPreparedListener(this)
					try {
						mediaPlayer.prepareAsync()
					} catch (e: IllegalStateException) {
						Timber.e(e)
						restartPlayer(filePath)
					}
				}
			}
		} catch (e: IllegalStateException) {
			Timber.e(e, "Player is not initialized!")
		}
	}

	override fun onPrepared(mp: MediaPlayer) {
		mediaPlayer.start()
		mediaPlayer.seekTo(pauseTimeMills.toInt())
		pauseTimeMills = 0
		playerState = PlayerState.PLAYING
		onStartPlay()
		mediaPlayer.setOnCompletionListener {
			stop()
		}
		schedulePlaybackTimeUpdate()
	}

	override fun seek(mills: Long) {
		pauseTimeMills = mills
		prevPosMills = 0
		try {
			if (playerState == PlayerState.PLAYING) {
				mediaPlayer.seekTo(mills.toInt())
				onSeek(mills)
			}
		} catch (e: IllegalStateException) {
			Timber.e(e, "Player is not initialized!")
		}
	}

	override fun pause() {
		stopPlaybackTimeUpdate()
		if (playerState == PlayerState.PLAYING) {
			mediaPlayer.pause()
			pauseTimeMills = mediaPlayer.currentPosition.toLong()
			prevPosMills = 0
			playerState = PlayerState.PAUSED
			onPausePlay()
		}
	}

	override fun unpause() {
		if (playerState == PlayerState.PAUSED) {
			mediaPlayer.start()
			mediaPlayer.seekTo(pauseTimeMills.toInt())
			pauseTimeMills = 0
			playerState = PlayerState.PLAYING
			onStartPlay()
			mediaPlayer.setOnCompletionListener {
				stop()
			}
			schedulePlaybackTimeUpdate()
		}
	}

	override fun stop() {
		stopPlaybackTimeUpdate()
		mediaPlayer.stop()
		mediaPlayer.reset()
		mediaPlayer.setOnCompletionListener(null)
		onStopPlay()
		playerState = PlayerState.STOPPED
		pauseTimeMills = 0
		prevPosMills = 0
	}

	override fun release() {
		stop()
		mediaPlayer.release()
		actionsListeners.clear()
	}

	override fun getPauseTime(): Long {
		return pauseTimeMills
	}

	override fun isPaused(): Boolean {
		return playerState == PlayerState.PAUSED
	}

	override fun isPlaying(): Boolean {
		return playerState == PlayerState.PLAYING
	}

	private fun schedulePlaybackTimeUpdate() {
		handler.postDelayed({
			try {
				if (playerState == PlayerState.PLAYING) {
					var pos = mediaPlayer.currentPosition.toLong()
					if (pos < prevPosMills) {
						pos = prevPosMills
					} else {
						prevPosMills = pos
					}
					onPlayProgress(pos)

				}
				schedulePlaybackTimeUpdate()
			} catch (e: IllegalStateException) {
				Timber.e(e, "Player is not initialized!")
				onError(PlayerInitException())
			}
		}, AppConstants.PLAYBACK_VISUALIZATION_INTERVAL.toLong())
	}

	private fun stopPlaybackTimeUpdate() {
		handler.removeCallbacksAndMessages(null)
	}

	private fun onStartPlay() {
		for (i in actionsListeners.indices) {
			actionsListeners[i].onStartPlay()
		}
	}

	private fun onPlayProgress(mills: Long) {
		for (i in actionsListeners.indices) {
			actionsListeners[i].onPlayProgress(mills)
		}
	}

	private fun onStopPlay() {
		for (i in actionsListeners.indices.reversed()) {
			actionsListeners[i].onStopPlay()
		}
	}

	private fun onPausePlay() {
		for (i in actionsListeners.indices) {
			actionsListeners[i].onPausePlay()
		}
	}

	private fun onSeek(mills: Long) {
		for (i in actionsListeners.indices) {
			actionsListeners[i].onSeek(mills)
		}
	}

	private fun onError(throwable: AppException) {
		for (i in actionsListeners.indices) {
			actionsListeners[i].onError(throwable)
		}
	}
}
