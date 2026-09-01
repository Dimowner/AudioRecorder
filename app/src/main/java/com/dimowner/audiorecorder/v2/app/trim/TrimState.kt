package com.dimowner.audiorecorder.v2.app.trim

import com.dimowner.audiorecorder.v2.app.info.RecordInfoState

data class TrimState(
    val recordInfo: RecordInfoState? = null,
    val startMills: Long = 0L,
    val endMills: Long = 0L,
    val playProgressMills: Long = 0L,
    val isPlaying: Boolean = false,
    val isTrimming: Boolean = false,
    val trimProgress: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
)
