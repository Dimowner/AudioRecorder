package com.dimowner.audiorecorder.exception

class AlreadyRecordingException: AppException() {
    override fun getType(): Int {
        return ALREADY_RECORDING
    }
}