package com.dimowner.audiorecorder.v2.audio

/** A Stop intent must survive every startup suspension, including file/database preparation. */
internal class RecordingStartGuard {
    @Volatile private var stopRequested = false
    fun begin() { stopRequested = false }
    fun requestStop() { stopRequested = true }
    fun mayStart(routeReady: Boolean): Boolean = !stopRequested && routeReady
}
