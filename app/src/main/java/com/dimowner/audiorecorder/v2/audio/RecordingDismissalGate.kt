package com.dimowner.audiorecorder.v2.audio

import kotlinx.coroutines.CompletableDeferred

/** Keeps closing the UI separate from stopping the hardware: WAV flushing and DB writes are async. */
internal class RecordingDismissalGate {
    @Volatile
    var isDismissalRequested = false
        private set

    private var completion = CompletableDeferred(true)

    fun recordingStarted(): Boolean {
        // Duplicate start commands must not replace the completion awaited by a close request.
        if (!completion.isCompleted) return false
        isDismissalRequested = false
        completion = CompletableDeferred()
        return true
    }

    fun recordingFinished(saved: Boolean) {
        completion.complete(saved)
    }

    suspend fun dismiss(stopRecorder: suspend () -> Unit): Boolean {
        isDismissalRequested = true
        val pendingSave = completion
        // Once a failed save has been reported, the idle overlay must still be dismissible.
        if (pendingSave.isCompleted) return true
        stopRecorder()
        return pendingSave.await()
    }
}
