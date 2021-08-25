package com.dimowner.audiorecorder.app.moverecords

import androidx.annotation.StringRes
import com.dimowner.audiorecorder.BackgroundQueue
import com.dimowner.audiorecorder.app.AppRecorder
import com.dimowner.audiorecorder.app.AppRecorderCallback
import com.dimowner.audiorecorder.app.settings.SettingsMapper
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.data.database.LocalRepository
import com.dimowner.audiorecorder.data.database.Record
import com.dimowner.audiorecorder.exception.AppException
import com.dimowner.audiorecorder.exception.ErrorParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File

/**
 * Created on 19.06.2021.
 * @author Dimowner
 */
@ExperimentalCoroutinesApi
class MoveRecordsViewModel(
		private val loadingTasks: BackgroundQueue,
		private val localRepository: LocalRepository,
		private val fileRepository: FileRepository,
		private val settingsMapper: SettingsMapper,
		private val audioPlayer: PlayerContractNew.Player,
		private val appRecorder: AppRecorder,
		private val prefs: Prefs
) {

	private val scope = CoroutineScope(Dispatchers.Main)

	private val _uiState = MutableStateFlow(MoveRecordsScreenState())
	val uiState: StateFlow<MoveRecordsScreenState> = _uiState

	private val _uiPlayState = MutableStateFlow(MoveRecordsPlayPanelState())
	val uiPlayState: StateFlow<MoveRecordsPlayPanelState> = _uiPlayState

	private val _event = MutableSharedFlow<MoveRecordsEvent>()
	val event: SharedFlow<MoveRecordsEvent> = _event

	private var listenPlaybackProgress: Boolean = true

	fun loadRecords() {
		showProgress(true)
		loadingTasks.postRunnable {
			val records = getPublicRecords()
			setState(uiState.value.copy(
				recordsCount = records.size,
				isMoveAllVisible = records.isNotEmpty(),
				isEmptyVisible = records.isEmpty(),
				recordsLocation = fileRepository.publicDir.absolutePath
			))
			val activeRecordId = prefs.activeRecord.toInt()
			if (activeRecordId > -1 && (audioPlayer.isPlaying() || audioPlayer.isPaused())) {
				var activeItemPos = -1
				for ((index, rec) in records.withIndex()) {
					if (rec.id == activeRecordId) {
						activeItemPos = index
					}
				}
				val rec = localRepository.getRecord(activeRecordId)
				if (rec != null) {
					setState(
						uiState.value.copy(
							activeRecordId = activeRecordId,
							activeRecordPos = activeItemPos,
							playState = PlayState.IDLE,
						)
					)
					setPlayState(
						uiPlayState.value.copy(
							playRecordDuration = rec.duration / 1000,
							recordPath = rec.path,
							playRecordName = rec.name,
							activeRecordData = rec.amps
						)
					)
				}
			}
			when {
				appRecorder.isRecording -> {
					setState(uiState.value.copy(playState = PlayState.RECORDING))
				}
				audioPlayer.isPlaying() -> {
					setState(uiState.value.copy(
						playState = PlayState.PLAYING,
						isShowPlayPanel = true
					))
				}
				audioPlayer.isPaused() -> {
					val playProgressMills = audioPlayer.getPauseTime()
					setState(uiState.value.copy(
						playState = PlayState.PAUSED,
						isShowPlayPanel = true
					))
					showPlayProgress(playProgressMills)
				}
			}
			setList(records)
			showProgress(false)
		}

		val playerCallback = object : PlayerContractNew.PlayerCallback {
			override fun onStartPlay() {
				setState(uiState.value.copy(
					isShowPlayPanel = true,
					playState = PlayState.PLAYING
				))
				emitEvent(MoveRecordsEvent.StartPlaybackService(uiPlayState.value.playRecordName))
			}

			override fun onPlayProgress(mills: Long) {
				showPlayProgress(mills)
			}

			override fun onStopPlay() {
				setState(uiState.value.copy(playState = PlayState.IDLE))
				setPlayState(uiPlayState.value.copy(
					playProgress = 0,
					playProgressMills = 0
				))
			}

			override fun onPausePlay() {
				setState(uiState.value.copy(playState = PlayState.PAUSED))
			}

			override fun onSeek(mills: Long) {}
			override fun onError(throwable: AppException) {
				Timber.e(throwable)
				setState(uiState.value.copy(
					isShowPlayPanel = false,
					playState = PlayState.IDLE,
					activeRecordId = -1,
					activeRecordPos = -1
				))
				emitEvent(MoveRecordsEvent.ShowError(ErrorParser.parseException(throwable)))

			}
		}
		audioPlayer.addPlayerCallback(playerCallback)

		val appRecorderCallback = object : AppRecorderCallback {
				override fun onRecordingStarted(file: File) {}
				override fun onRecordingPaused() {}
				override fun onRecordingResumed() {}
				override fun onRecordingProgress(mills: Long, amp: Int) {}
				override fun onRecordingStopped(file: File, rec: Record) {
					setState(uiState.value.copy(playState = PlayState.IDLE))
				}
				override fun onError(e: AppException) {
					setState(uiState.value.copy(playState = PlayState.IDLE))
				}
			}
		appRecorder.addRecordingCallback(appRecorderCallback)
	}

	private fun showPlayProgress(mills: Long) {
		if (uiPlayState.value.playRecordDuration > 0 && listenPlaybackProgress) {
			setPlayState(
				uiPlayState.value.copy(
					playProgress = (1000 * mills / uiPlayState.value.playRecordDuration).toInt(),
					playProgressMills = mills
				)
			)
		}
	}

	fun showProgress(show: Boolean) {
		setState(uiState.value.copy(showProgress = show))
	}

	private fun setList(items: List<Record>) {
		setState(uiState.value.copy(list = recordsToMoveRecordsItems(settingsMapper, items)))
	}

	fun startPlaybackById(id: Int) {
		if (id == uiState.value.activeRecordId) {
			startPlayback()
		} else {
			when (uiState.value.playState) {
				PlayState.RECORDING -> return
				else -> {
					audioPlayer.stop()
					prefs.activeRecord = id.toLong()
					loadingTasks.postRunnable {
						val record = localRepository.getRecord(id)

						var activeItemPos = -1;
						for ((index, rec) in uiState.value.list.withIndex()) {
							if (rec.id == id) {
								activeItemPos = index
							}
						}
						setState(
							uiState.value.copy(
								activeRecordId = id,
								activeRecordPos = activeItemPos,
							)
						)
						setPlayState(
							uiPlayState.value.copy(
								playRecordName = record.name,
								playRecordDuration = record.duration / 1000,
								recordPath = record.path,
								activeRecordData = record.amps
							)
						)
						audioPlayer.play(record.path)
					}
				}
			}
		}
	}

	fun moveAllRecords() {
		loadingTasks.postRunnable {
			val ids = getPublicRecords().map { it.id }
			emitEvent(MoveRecordsEvent.MoveAllRecords(ids))
		}
	}

	fun startPlayback() {
		when (uiState.value.playState) {
			PlayState.RECORDING -> return
			PlayState.PLAYING -> audioPlayer.pause()
			PlayState.PAUSED -> audioPlayer.unpause()
			PlayState.IDLE -> {
				loadingTasks.postRunnable {
					val record = localRepository.getRecord(uiState.value.activeRecordId)
					setPlayState(uiPlayState.value.copy(
						playRecordName = record.name,
						playRecordDuration = record.duration/1000,
						recordPath = record.path,
						activeRecordData = record.amps
					))
					audioPlayer.play(record.path)
				}
			}
		}
	}

	fun stopPlayback() {
		when (uiState.value.playState) {
			PlayState.PLAYING,
			PlayState.PAUSED -> {
				audioPlayer.stop()
				setPlayState(MoveRecordsPlayPanelState())
			}
			else -> {
				//Do nothing
			}
		}
		setState(uiState.value.copy(
			isShowPlayPanel = false,
			activeRecordPos = -1,
			activeRecordId = -1
		))
	}

	fun seekPlayback(mills: Long) {
		audioPlayer.seek(mills)
	}

	fun openRecordsLocation() {
		emitEvent(MoveRecordsEvent.OpenRecordsLocation(fileRepository.publicDir))
	}

	fun disablePlaybackProgressListener() {
		listenPlaybackProgress = false
	}

	fun enablePlaybackProgressListener() {
		listenPlaybackProgress = true
	}

	private fun getPublicRecords(): List<Record> {
		return localRepository.findRecordsByPath(fileRepository.publicDir.absolutePath)
	}

	fun clear() {
		scope.cancel()
	}

	fun setState(state: MoveRecordsScreenState) {
		_uiState.value = state
	}

	fun setPlayState(state: MoveRecordsPlayPanelState) {
		_uiPlayState.value = state
	}

	fun emitEvent(event: MoveRecordsEvent) {
		scope.launch {
			_event.emit(event)
		}
	}
}

sealed class MoveRecordsEvent {
	data class ShowError(@StringRes val resId: Int) : MoveRecordsEvent()
	data class StartPlaybackService(val name: String) : MoveRecordsEvent()
	data class OpenRecordsLocation(val file: File) : MoveRecordsEvent()
	data class MoveAllRecords(val list: List<Int>) : MoveRecordsEvent()
}
