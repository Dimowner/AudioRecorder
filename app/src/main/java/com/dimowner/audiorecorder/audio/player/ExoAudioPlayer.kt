/*
 * Copyright 2026 Dmytro Ponomarenko
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

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.PlayerDataSourceException
import com.dimowner.audiorecorder.exception.PlayerInitException
import com.dimowner.audiorecorder.v2.analytics.ANALYTICS_VALUE_NONE
import com.dimowner.audiorecorder.v2.analytics.ANALYTICS_VALUE_UNKNOWN_NUMBER
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.analytics.PlaybackStartFailure
import com.dimowner.audiorecorder.v2.analytics.PlaybackStartFailureReason
import timber.log.Timber
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Uri scheme of a record stored as a plain file path. */
private const val SCHEME_FILE = "file"

/** Longest string still treated as a file extension when reporting a failed playback. */
private const val MAX_EXTENSION_LENGTH = 5

/**
 * [PlayerContractNew.Player] backed by ExoPlayer.
 *
 * Compared to [AudioPlayerNew] (MediaPlayer based) it supports changing the playback rate
 * at any moment, including while paused, and it plays `content://` sources (records stored in
 * a SAF picked folder) as naturally as plain file paths.
 *
 * ExoPlayer is single threaded: every interaction with it happens on the main looper. Public
 * methods may be called from any thread, so they post onto [playerHandler]. The state the
 * getters expose is mirrored into volatile fields to keep them callable from any thread too.
 */
@UnstableApi
class ExoAudioPlayer(
	context: Context,
	private val analyticsTracker: AnalyticsTracker,
) : PlayerContractNew.Player {

	private val actionsListeners = CopyOnWriteArrayList<PlayerContractNew.PlayerCallback>()

	private val playerHandler = Handler(Looper.getMainLooper())
	private val appContext = context.applicationContext

	@Volatile
	private var playerState = PlayerState.STOPPED

	@Volatile
	private var pauseTimeMills: Long = 0

	@Volatile
	private var playbackSpeed: Float = NORMAL_PLAYBACK_SPEED

	private var prevPosMills: Long = 0

	/**
	 * Anchor used to interpolate the playback position between ExoPlayer readings, see
	 * [currentPositionMills]. [lastRawPosMills] is [C.TIME_UNSET] while there is no anchor yet.
	 */
	private var lastRawPosMills: Long = C.TIME_UNSET
	private var anchorPosMills: Long = 0
	private var anchorRealtimeMills: Long = 0

	/** True between [play] and the moment ExoPlayer reports the source as ready. */
	private var isPreparing = false

	/**
	 * Source of the track that is being prepared, kept so a failed start can be reported with the
	 * details of the file it failed on. Player-thread confined, like the rest of the state above.
	 */
	private var preparingPath: String? = null

	private var isReleased = false

	private val playerListener = object : Player.Listener {
		override fun onPlaybackStateChanged(playbackState: Int) {
			when (playbackState) {
				Player.STATE_READY -> if (isPreparing) {
					isPreparing = false
					preparingPath = null
					pauseTimeMills = 0
					prevPosMills = 0
					resetPositionInterpolation()
					playerState = PlayerState.PLAYING
					onStartPlay()
					schedulePlaybackTimeUpdate()
				}
				Player.STATE_ENDED -> stop()
				else -> { /* STATE_IDLE and STATE_BUFFERING need no handling */ }
			}
		}

		override fun onPlayerError(error: PlaybackException) {
			Timber.e(error, "ExoPlayer playback error")
			val failedToStart = isPreparing
			val failedPath = preparingPath
			isPreparing = false
			preparingPath = null
			stopPlaybackTimeUpdate()
			playerState = PlayerState.STOPPED
			pauseTimeMills = 0
			prevPosMills = 0
			resetPositionInterpolation()
			val exception = if (
				error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
				error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
			) {
				PlayerDataSourceException()
			} else {
				PlayerInitException()
			}
			// An error raised while the track is already playing is a different problem, and is
			// deliberately left out of the start-failure funnel.
			if (failedToStart) {
				trackStartFailure(
					reason = PlaybackStartFailureReason.fromException(exception),
					path = failedPath,
					// The PlaybackException is what carries the stack trace and the real cause;
					// the app level exception above is only a marker for the UI.
					error = error,
					playerErrorCode = error.errorCode,
					playerErrorName = error.errorCodeName,
				)
			}
			onError(exception)
		}
	}

	// Built eagerly with an explicit looper, so that constructing the player from a background
	// thread (Hilt may instantiate the singleton anywhere) is safe.
	private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
		.setLooper(Looper.getMainLooper())
		.build()
		.apply {
			setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(C.USAGE_MEDIA)
					.setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
					.build(),
				/* handleAudioFocus = */ false
			)
			addListener(playerListener)
		}

	override fun addPlayerCallback(callback: PlayerContractNew.PlayerCallback) {
		actionsListeners.add(callback)
	}

	override fun removePlayerCallback(callback: PlayerContractNew.PlayerCallback): Boolean {
		return actionsListeners.remove(callback)
	}

	override fun play(filePath: String) {
		runOnPlayerThread {
			if (playerState == PlayerState.PLAYING) return@runOnPlayerThread
			val uri = filePath.toPlayableUri()
			if (uri == null) {
				val exception = PlayerDataSourceException()
				trackStartFailure(
					reason = PlaybackStartFailureReason.DATA_SOURCE,
					path = filePath,
					error = exception,
				)
				onError(exception)
				return@runOnPlayerThread
			}
			try {
				stopPlaybackTimeUpdate()
				playerState = PlayerState.STOPPED
				isPreparing = true
				preparingPath = filePath
				exoPlayer.setMediaItem(MediaItem.fromUri(uri), pauseTimeMills)
				exoPlayer.setPlaybackSpeed(playbackSpeed)
				exoPlayer.playWhenReady = true
				exoPlayer.prepare()
			} catch (e: IllegalStateException) {
				Timber.e(e, "Player is not initialized!")
				isPreparing = false
				preparingPath = null
				trackStartFailure(
					reason = PlaybackStartFailureReason.PLAYER_INIT,
					path = filePath,
					error = e,
				)
				onError(PlayerInitException())
			}
		}
	}

	override fun seek(mills: Long) {
		runOnPlayerThread {
			pauseTimeMills = mills
			prevPosMills = 0
			resetPositionInterpolation()
			if (playerState == PlayerState.PLAYING || playerState == PlayerState.PAUSED) {
				exoPlayer.seekTo(mills)
				onSeek(mills)
			}
		}
	}

	override fun pause() {
		runOnPlayerThread {
			stopPlaybackTimeUpdate()
			if (playerState == PlayerState.PLAYING) {
				// Sampled before pausing and taken no lower than the last reported progress:
				// ExoPlayer's own position lags behind what the UI has already shown (see
				// [currentPositionMills]), so resuming from it would visibly rewind the waveform.
				val positionMills = maxOf(currentPositionMills(), prevPosMills)
				exoPlayer.pause()
				pauseTimeMills = positionMills
				prevPosMills = 0
				resetPositionInterpolation()
				playerState = PlayerState.PAUSED
				onPausePlay()
			}
		}
	}

	override fun unpause() {
		runOnPlayerThread {
			if (playerState == PlayerState.PAUSED) {
				exoPlayer.seekTo(pauseTimeMills)
				exoPlayer.setPlaybackSpeed(playbackSpeed)
				exoPlayer.play()
				resetPositionInterpolation()
				pauseTimeMills = 0
				playerState = PlayerState.PLAYING
				onStartPlay()
				schedulePlaybackTimeUpdate()
			}
		}
	}

	override fun stop() {
		runOnPlayerThread {
			stopPlaybackTimeUpdate()
			isPreparing = false
			preparingPath = null
			exoPlayer.stop()
			exoPlayer.clearMediaItems()
			playerState = PlayerState.STOPPED
			pauseTimeMills = 0
			prevPosMills = 0
			resetPositionInterpolation()
			onStopPlay()
		}
	}

	override fun release() {
		runOnPlayerThread {
			if (isReleased) return@runOnPlayerThread
			stopPlaybackTimeUpdate()
			isPreparing = false
			preparingPath = null
			exoPlayer.stop()
			exoPlayer.clearMediaItems()
			playerState = PlayerState.STOPPED
			pauseTimeMills = 0
			prevPosMills = 0
			resetPositionInterpolation()
			onStopPlay()
			exoPlayer.removeListener(playerListener)
			exoPlayer.release()
			isReleased = true
			actionsListeners.clear()
		}
	}

	override fun getPauseTime(): Long = pauseTimeMills

	override fun isPaused(): Boolean = playerState == PlayerState.PAUSED

	override fun isPlaying(): Boolean = playerState == PlayerState.PLAYING

	override fun setPlaybackSpeed(speed: Float) {
		val coerced = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
		playbackSpeed = coerced
		runOnPlayerThread {
			// Unlike MediaPlayer, ExoPlayer accepts the rate in any state and does not resume
			// a paused playback, so it can be pushed unconditionally.
			exoPlayer.setPlaybackSpeed(coerced)
		}
	}

	override fun getPlaybackSpeed(): Float = playbackSpeed

	/**
	 * Records created before the SAF support store an absolute file path, the ones created in a
	 * user picked folder store a `content://` uri. Both arrive here as a plain string.
	 */
	private fun String.toPlayableUri(): Uri? {
		if (isEmpty()) return null
		val uri = toUri()
		return if (uri.scheme == null) Uri.fromFile(File(this)) else uri
	}

	/**
	 * Reports a playback that never started, together with what can be told about its source.
	 *
	 * The file is stat-ed here, on the player (main) thread. That is a single stat on an error
	 * path that ends in a snackbar anyway, and it answers the first question these reports raise:
	 * whether the record file was there at all, and whether it was empty - the signature of a
	 * recording that was interrupted before anything was written to it.
	 */
	private fun trackStartFailure(
		reason: PlaybackStartFailureReason,
		path: String?,
		error: Throwable?,
		playerErrorCode: Int = ANALYTICS_VALUE_UNKNOWN_NUMBER.toInt(),
		playerErrorName: String = ANALYTICS_VALUE_NONE,
	) {
		val uri = path?.toPlayableUri()
		val file = if (uri?.scheme == SCHEME_FILE) uri.path?.let(::File) else null
		val exists = file?.exists()
		analyticsTracker.trackPlaybackStartFailed(
			PlaybackStartFailure(
				reason = reason,
				format = path.playbackFormat(),
				uriScheme = uri?.scheme ?: ANALYTICS_VALUE_NONE,
				fileExists = exists,
				fileSizeBytes = if (exists == true) file.length() else ANALYTICS_VALUE_UNKNOWN_NUMBER,
				playerErrorCode = playerErrorCode,
				playerErrorName = playerErrorName,
				error = error,
			)
		)
	}

	/**
	 * Lowercase extension of the source, so failures can be told apart per format. Anything that
	 * does not look like an extension (a name with a dot in it, no dot at all) is not reported.
	 */
	private fun String?.playbackFormat(): String {
		val extension = this?.substringAfterLast('/')?.substringAfterLast('.', "").orEmpty().lowercase()
		return if (extension.isNotEmpty() && extension.length <= MAX_EXTENSION_LENGTH) {
			extension
		} else {
			ANALYTICS_VALUE_NONE
		}
	}

	/**
	 * Playback position to report to the UI, interpolated with the wall clock.
	 *
	 * [ExoPlayer.getCurrentPosition] returns `PlaybackInfo.positionUs` exactly as the playback
	 * thread last published it to the application thread; media3 only extrapolates it with the
	 * elapsed real time in the audio offload path (`PlaybackInfo.getEstimatedPositionUs`). Polling
	 * it therefore returns the same number for a few hundred milliseconds and then jumps: measured
	 * on device the value changed roughly every 310 ms (in ~253 ms + ~60 ms steps) even though this
	 * task runs every [AppConstants.PLAYBACK_VISUALIZATION_INTERVAL] ms. That is what made the
	 * waveform and the timer stutter.
	 *
	 * So every reading that actually differs from the previous one becomes an anchor, and between
	 * anchors the position is advanced from it by the elapsed real time scaled by the playback
	 * speed - which is what the audio is doing anyway. Interpolation only runs while the player is
	 * really rendering; while it is buffering or suppressed the raw value is reported as is, so the
	 * reported position can never run away from the audio that is being heard.
	 */
	private fun currentPositionMills(): Long {
		val raw = exoPlayer.currentPosition
		val now = SystemClock.elapsedRealtime()
		val isRendering = exoPlayer.isPlaying
		if (raw != lastRawPosMills || !isRendering) {
			lastRawPosMills = raw
			anchorPosMills = raw
			anchorRealtimeMills = now
		}
		if (!isRendering) return raw
		val interpolated = anchorPosMills + ((now - anchorRealtimeMills) * playbackSpeed).toLong()
		val durationMills = exoPlayer.duration
		return if (durationMills == C.TIME_UNSET) {
			interpolated
		} else {
			interpolated.coerceAtMost(durationMills)
		}
	}

	/** Drops the anchor, so the next tick re-reads the position instead of extrapolating a stale one. */
	private fun resetPositionInterpolation() {
		lastRawPosMills = C.TIME_UNSET
		anchorPosMills = 0
		anchorRealtimeMills = 0
	}

	private val playbackTimeUpdateTask = object : Runnable {
		override fun run() {
			if (playerState != PlayerState.PLAYING) return
			var pos = currentPositionMills()
			if (pos < prevPosMills) {
				pos = prevPosMills
			} else {
				prevPosMills = pos
			}
			onPlayProgress(pos)
			playerHandler.postDelayed(this, AppConstants.PLAYBACK_VISUALIZATION_INTERVAL.toLong())
		}
	}

	private fun schedulePlaybackTimeUpdate() {
		playerHandler.removeCallbacks(playbackTimeUpdateTask)
		playerHandler.postDelayed(
			playbackTimeUpdateTask,
			AppConstants.PLAYBACK_VISUALIZATION_INTERVAL.toLong()
		)
	}

	private fun stopPlaybackTimeUpdate() {
		playerHandler.removeCallbacks(playbackTimeUpdateTask)
	}

	/**
	 * Runs [action] on the ExoPlayer thread, inline when already on it so that a call made from
	 * the main thread keeps the same ordering guarantees the MediaPlayer implementation had.
	 */
	private fun runOnPlayerThread(action: () -> Unit) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			action()
		} else {
			playerHandler.post(action)
		}
	}

	private fun onStartPlay() {
		actionsListeners.forEach { it.onStartPlay() }
	}

	private fun onPlayProgress(mills: Long) {
		actionsListeners.forEach { it.onPlayProgress(mills) }
	}

	private fun onStopPlay() {
		actionsListeners.reversed().forEach { it.onStopPlay() }
	}

	private fun onPausePlay() {
		actionsListeners.forEach { it.onPausePlay() }
	}

	private fun onSeek(mills: Long) {
		actionsListeners.forEach { it.onSeek(mills) }
	}

	private fun onError(throwable: AppException) {
		actionsListeners.forEach { it.onError(throwable) }
	}
}
