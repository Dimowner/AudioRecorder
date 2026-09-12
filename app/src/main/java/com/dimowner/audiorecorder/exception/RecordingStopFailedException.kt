package com.dimowner.audiorecorder.exception

/**
 * The recorder captured audio but failed while finalising the output file, so the container on
 * disk is missing the index that makes it playable.
 *
 * Reported as a [RECORDING_ERROR] like any other failure mid recording, but kept as its own type
 * because the file is worth recovering: it holds everything that was recorded up to the failure.
 */
class RecordingStopFailedException: AppException() {
    override fun getType(): Int {
        return RECORDING_ERROR
    }
}
