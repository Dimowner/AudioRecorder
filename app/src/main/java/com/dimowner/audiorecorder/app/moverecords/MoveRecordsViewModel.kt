package com.dimowner.audiorecorder.app.moverecords

import com.dimowner.audiorecorder.BackgroundQueue
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.data.database.LocalRepository
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
		private val prefs: Prefs
) {

	private val _uiState = MutableStateFlow(MoveRecordsScreenState())
	val uiState: StateFlow<MoveRecordsScreenState> = _uiState

	fun showProgress(show: Boolean) {
		_uiState.value = uiState.value.copy(showProgress = show)
	}

	fun clear() {

	}
}