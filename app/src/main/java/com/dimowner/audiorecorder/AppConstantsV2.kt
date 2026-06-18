package com.dimowner.audiorecorder

object AppConstantsV2 {
    const val WAVEFORM_AMPLITUDE_MAX_VALUE = 32767f
    /** Waveform recording grid step in milliseconds */
    const val RECORDING_GRID_STEP = 2000L //Milliseconds
    const val SHORT_RECORD = 18000L //Milliseconds
    const val DEFAULT_WIDTH_SCALE = 1.5F //Const val describes how many screens a record will take.

    const val DEFAULT_MAX_RECORDING_DURATION_MS = 120 * 60 * 1000 //120 minutes

    const val RECORD_DESCRIPTION_MAX_LENGTH = 500
}
