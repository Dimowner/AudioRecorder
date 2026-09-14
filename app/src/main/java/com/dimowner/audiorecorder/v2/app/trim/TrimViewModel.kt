package com.dimowner.audiorecorder.v2.app.trim

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.v2.app.info.toRecordInfoState
import com.dimowner.audiorecorder.v2.audio.AudioTrimmer
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val AUDITION_DURATION_MS = 5000L

@HiltViewModel
class TrimViewModel @Inject constructor(
    private val recordsDataSource: RecordsDataSource,
    private val audioTrimmer: AudioTrimmer,
    private val audioPlayer: PlayerContractNew.Player,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    application: Application,
) : AndroidViewModel(application) {

    private val _state = mutableStateOf(TrimState())
    val state: State<TrimState> = _state

    private val _event = MutableSharedFlow<TrimEvent?>()
    val event: SharedFlow<TrimEvent?> = _event

    private var record: Record? = null
    private var auditionLimitJob: Job? = null
    private var trimEndForAudition: Long? = null

    private val playerCallback = object : PlayerContractNew.PlayerCallback {
        override fun onStartPlay() {
            _state.value = _state.value.copy(isPlaying = true)
        }

        override fun onPlayProgress(mills: Long) {
            _state.value = _state.value.copy(playProgressMills = mills)
            trimEndForAudition?.let { endPos ->
                if (mills >= endPos) {
                    audioPlayer.pause()
                }
            }
        }

        override fun onPausePlay() {
            _state.value = _state.value.copy(isPlaying = false)
        }

        override fun onSeek(mills: Long) {
            _state.value = _state.value.copy(playProgressMills = mills)
        }

        override fun onStopPlay() {
            _state.value = _state.value.copy(isPlaying = false, playProgressMills = 0L)
            auditionLimitJob?.cancel()
        }

        override fun onError(throwable: com.dimowner.audiorecorder.exception.AppException) {
            _state.value = _state.value.copy(isPlaying = false, playProgressMills = 0L)
            auditionLimitJob?.cancel()
        }
    }

    init {
        audioPlayer.addPlayerCallback(playerCallback)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.removePlayerCallback(playerCallback)
        audioPlayer.stop()
        auditionLimitJob?.cancel()
    }

    fun loadRecord(recordId: Long) {
        viewModelScope.launch(ioDispatcher) {
            stopPlayback()
            val loadedRecord = recordsDataSource.getRecord(recordId)
            if (loadedRecord != null) {
                record = loadedRecord
                val infoState = loadedRecord.toRecordInfoState()
                _state.value = _state.value.copy(
                    recordInfo = infoState,
                    endMills = loadedRecord.durationMills,
                    playProgressMills = 0L,
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Record not found",
                )
            }
        }
    }

    fun onAction(action: TrimAction) {
        when (action) {
            is TrimAction.SetStartMills -> setStartMills(action.mills)
            is TrimAction.SetEndMills -> setEndMills(action.mills)
            TrimAction.SetStartToPlayhead -> setStartToPlayhead()
            TrimAction.SetEndToPlayhead -> setEndToPlayhead()
            TrimAction.SetEndToPlayheadAndStop -> { setEndToPlayhead(); stopPlayback() }
            is TrimAction.SeekPlayhead -> seekPlayhead(action.mills)
            TrimAction.ApplyTrim -> _state.value = _state.value.copy(showDialog = true)
            is TrimAction.SaveChoice -> {
                _state.value = _state.value.copy(showDialog = false)
                applyTrim(action.overwrite)
            }
            TrimAction.DismissDialog -> _state.value = _state.value.copy(showDialog = false)
            TrimAction.DismissError -> _state.value = _state.value.copy(error = null)
            TrimAction.PlayFromStart -> playFromStart()
            TrimAction.PlayLastFiveSeconds -> playLastFiveSeconds()
            TrimAction.PlayPauseToggle -> playPauseToggle()
            TrimAction.StopPlayback -> stopPlayback()
        }
    }

    private fun setStartMills(mills: Long) {
        val current = _state.value
        val newStart = mills.coerceIn(0L, current.endMills - MIN_TRIM_DURATION)
        _state.value = current.copy(startMills = newStart)
        if ((audioPlayer.isPlaying() || audioPlayer.isPaused()) && current.playProgressMills < newStart) {
            seekPlayhead(newStart)
        }
    }

    private fun setEndMills(mills: Long) {
        val current = _state.value
        val newEnd = mills.coerceIn(current.startMills + MIN_TRIM_DURATION, current.recordInfo?.duration ?: 0L)
        _state.value = current.copy(endMills = newEnd)
        if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
            trimEndForAudition = newEnd
        }
    }

    private fun setStartToPlayhead() {
        val current = _state.value
        val playhead = current.playProgressMills
        _state.value = current.copy(
            startMills = playhead.coerceIn(0L, current.endMills - MIN_TRIM_DURATION)
        )
    }

    private fun setEndToPlayhead() {
        val current = _state.value
        val playhead = current.playProgressMills
        _state.value = current.copy(
            endMills = playhead.coerceIn(current.startMills + MIN_TRIM_DURATION, current.recordInfo?.duration ?: 0L)
        )
    }

    private fun seekPlayhead(mills: Long) {
        val current = _state.value
        val clamped = mills.coerceIn(0L, current.recordInfo?.duration ?: 0L)
        _state.value = current.copy(playProgressMills = clamped)
        if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
            audioPlayer.seek(clamped)
        }
    }

    private fun playFromStart() {
        val currentRecord = record ?: return
        val currentState = _state.value
        stopPlayback()
        trimEndForAudition = currentState.endMills
        audioPlayer.seek(currentState.startMills)
        audioPlayer.play(currentRecord.path)
    }

    private fun playLastFiveSeconds() {
        val currentRecord = record ?: return
        val currentState = _state.value
        stopPlayback()
        val startPos = currentState.startMills
        val endPos = currentState.endMills
        val rangeDuration = endPos - startPos
        val seekStart = if (rangeDuration >= AUDITION_DURATION_MS) {
            endPos - AUDITION_DURATION_MS
        } else {
            startPos
        }
        trimEndForAudition = endPos
        audioPlayer.seek(seekStart)
        audioPlayer.play(currentRecord.path)
    }

    private fun playPauseToggle() {
        val currentRecord = record ?: return
        val currentState = _state.value
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause()
            auditionLimitJob?.cancel()
        } else if (audioPlayer.isPaused()) {
            val atTrimEnd = trimEndForAudition?.let { end ->
                currentState.playProgressMills >= end - 200
            } ?: false
            val beforeTrimStart = currentState.playProgressMills < currentState.startMills
            trimEndForAudition = currentState.endMills
            if (atTrimEnd || beforeTrimStart) {
                audioPlayer.seek(currentState.startMills)
            }
            audioPlayer.unpause()
        } else {
            trimEndForAudition = currentState.endMills
            audioPlayer.seek(currentState.startMills)
            audioPlayer.play(currentRecord.path)
        }
    }

    private fun stopPlayback() {
        audioPlayer.stop()
        auditionLimitJob?.cancel()
        trimEndForAudition = null
    }

    private fun startAuditionLimit(maxDurationMs: Long) {
        auditionLimitJob?.cancel()
        auditionLimitJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (audioPlayer.isPlaying()) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= maxDurationMs) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        audioPlayer.pause()
                    }
                    break
                }
                delay(100)
            }
        }
    }

    private fun applyTrim(isOverwrite: Boolean = true) {
        val currentRecord = record ?: return
        val currentState = _state.value
        if (currentState.isTrimming) return
        if (currentState.endMills - currentState.startMills < MIN_TRIM_DURATION) {
            _state.value = currentState.copy(error = "Trim range is too short")
            return
        }

        stopPlayback()
        _state.value = currentState.copy(isTrimming = true)
        viewModelScope.launch(ioDispatcher) {
            val result = audioTrimmer.trim(
                sourcePath = currentRecord.path,
                startMills = currentState.startMills,
                endMills = currentState.endMills,
            )
            if (result.success) {
                try {
                    val updatedRecord: Record
                    if (isOverwrite) {
                        val trimmedFile = File(result.outputPath)
                        val originalFile = File(currentRecord.path)
                        val backupPath = currentRecord.path + ".bak"

                        originalFile.copyTo(File(backupPath), overwrite = true)
                        trimmedFile.copyTo(originalFile, overwrite = true)
                        trimmedFile.delete()

                        val slicedAmps = sliceAmps(
                            amps = currentRecord.amps,
                            originalDurationMills = currentRecord.durationMills,
                            startMills = currentState.startMills,
                            endMills = currentState.endMills,
                        )
                        updatedRecord = currentRecord.copy(
                            durationMills = result.durationMills,
                            size = result.size,
                            amps = slicedAmps,
                        )
                        recordsDataSource.updateRecord(updatedRecord)
                        File(backupPath).delete()
                    } else {
                        val slicedAmps = sliceAmps(
                            amps = currentRecord.amps,
                            originalDurationMills = currentRecord.durationMills,
                            startMills = currentState.startMills,
                            endMills = currentState.endMills,
                        )
                        val newName = generateUniqueName(currentRecord.name)
                        val tempFile = File(result.outputPath)
                        val parentDir = tempFile.parentFile ?: File(result.outputPath).parentFile
                        val newFile = File(parentDir, "${newName}.${currentRecord.format}")
                        tempFile.copyTo(newFile, overwrite = true)
                        tempFile.delete()
                        updatedRecord = currentRecord.copy(
                            id = 0,
                            path = newFile.absolutePath,
                            durationMills = result.durationMills,
                            size = result.size,
                            name = newName,
                            amps = slicedAmps,
                        )
                        recordsDataSource.insertRecord(updatedRecord)
                    }

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _state.value = _state.value.copy(isTrimming = false)
                        _event.emit(TrimEvent.TrimApplied)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save trimmed file")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            isTrimming = false,
                            error = e.message ?: "Failed to save trimmed file",
                        )
                    }
                }
            } else {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isTrimming = false,
                        error = result.error ?: "Trim failed",
                    )
                }
            }
        }
    }

    private suspend fun generateUniqueName(originalName: String): String {
        val baseName = originalName.removeSuffix("_trimmed")
        val allNames = recordsDataSource.getAllRecords().map { it.name }.toSet()
        if ("${baseName}_trimmed" !in allNames) return "${baseName}_trimmed"
        var counter = 2
        while ("${baseName}_trimmed_$counter" in allNames) counter++
        return "${baseName}_trimmed_$counter"
    }

    companion object {
        private const val MIN_TRIM_DURATION = 100L

        private fun sliceAmps(
            amps: IntArray,
            originalDurationMills: Long,
            startMills: Long,
            endMills: Long,
        ): IntArray {
            if (amps.isEmpty() || originalDurationMills <= 0) return amps
            val startIndex = ((startMills.toFloat() / originalDurationMills) * amps.size).toInt().coerceIn(0, amps.size)
            val endIndex = ((endMills.toFloat() / originalDurationMills) * amps.size).toInt().coerceIn(startIndex, amps.size)
            return amps.sliceArray(startIndex until endIndex)
        }
    }
}

sealed class TrimAction {
    data class SetStartMills(val mills: Long) : TrimAction()
    data class SetEndMills(val mills: Long) : TrimAction()
    data object SetStartToPlayhead : TrimAction()
    data object SetEndToPlayhead : TrimAction()
    data object SetEndToPlayheadAndStop : TrimAction()
    data class SeekPlayhead(val mills: Long) : TrimAction()
    data object ApplyTrim : TrimAction()
    data class SaveChoice(val overwrite: Boolean) : TrimAction()
    data object DismissDialog : TrimAction()
    data object DismissError : TrimAction()
    data object PlayFromStart : TrimAction()
    data object PlayLastFiveSeconds : TrimAction()
    data object PlayPauseToggle : TrimAction()
    data object StopPlayback : TrimAction()
}

sealed class TrimEvent {
    data object TrimApplied : TrimEvent()
}
