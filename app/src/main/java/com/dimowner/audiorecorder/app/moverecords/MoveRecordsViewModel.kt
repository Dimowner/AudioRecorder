package com.dimowner.audiorecorder.app.moverecords

import com.dimowner.audiorecorder.BackgroundQueue
import com.dimowner.audiorecorder.app.settings.SettingsMapper
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.data.database.LocalRepository
import com.dimowner.audiorecorder.data.database.Record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Created on 19.06.2021.
 * @author Dimowner
 */
@ExperimentalCoroutinesApi
class MoveRecordsViewModel(
		private val loadingTasks: BackgroundQueue,
		private val recordingsTasks: BackgroundQueue,
		private val localRepository: LocalRepository,
		private val fileRepository: FileRepository,
		private val settingsMapper: SettingsMapper,
		private val prefs: Prefs
) {

	private val _uiState = MutableStateFlow(MoveRecordsScreenState())
	val uiState: StateFlow<MoveRecordsScreenState> = _uiState

	init {
		loadingTasks.postRunnable{
			val records = localRepository.findRecordsByPath(fileRepository.publicDir.absolutePath)
			setCount(records.size)
			setList(records)
		}
	}

	fun showProgress(show: Boolean) {
		_uiState.value = uiState.value.copy(showProgress = show)
	}

	fun setCount(count: Int) {
		_uiState.value = uiState.value.copy(count = count)
	}

	fun setList(items: List<Record>) {
		_uiState.value = uiState.value.copy(list = recordsToMoveRecordsItems(settingsMapper, items))
	}

	fun clear() {

	}
}