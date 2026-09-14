package com.dimowner.audiorecorder.v2.app.trim

import com.dimowner.audiorecorder.v2.app.info.RecordInfoState

data class TrimState(
    val recordInfo: RecordInfoState? = null,
    val startMills: Long = 0L,
    val endMills: Long = 0L,
    val originalStartMills: Long = 0L,
    val originalEndMills: Long = 0L,
    val playProgressMills: Long = 0L,
    val isPlaying: Boolean = false,
    val isTrimming: Boolean = false,
    val trimProgress: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDialog: Boolean = false,
    val showExitDialog: Boolean = false,
) {
    val hasChanges: Boolean
        get() = startMills != originalStartMills || endMills != originalEndMills
}
