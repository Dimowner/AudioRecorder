package com.dimowner.audiorecorder.v2.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Process-local evidence survives navigating to settings after stopping a recording. */
@Singleton
class CaptureDiagnostics @Inject constructor() {
    private val mutable = MutableStateFlow(CaptureDiagnosticState())
    val state = mutable.asStateFlow()

    fun route(message: String) {
        mutable.update { it.copy(route = message) }
        Timber.i("Bluetooth experiment: %s", message)
    }

    fun session(message: String) {
        mutable.update { it.copy(session = message) }
        Timber.i("Capture experiment: %s", message)
    }

    fun noteSession(message: String) {
        mutable.update { it.copy(session = it.session + "\n" + message) }
        Timber.i("Capture experiment: %s", message)
    }
}

data class CaptureDiagnosticState(
    val route: String = "Bluetooth route has not been requested.",
    val session: String = "No recording attempted in this app session.",
)
