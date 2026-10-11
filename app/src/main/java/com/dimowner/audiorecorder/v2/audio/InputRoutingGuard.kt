package com.dimowner.audiorecorder.v2.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber

/** Frozen input device and mode selected for one recording, not a process-wide routing hint. */
data class BluetoothInputSelection(val device: AudioDeviceInfo, val mode: BluetoothAudioMode)

internal class CaptureInputException(message: String) : IOException(message)

/** Validates the actual input before accepting captured audio. */
internal class InputRoutingGuard(
    private val router: AudioRouting,
    private val manager: AudioManager,
    private val selection: BluetoothInputSelection,
    private val report: (String) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AutoCloseable {
    private val routeVersion = AtomicInteger()
    private var checkedVersion = -1
    private var startedAt = 0L
    private var confirmed = false
    private var registered = false
    @Volatile private var closed = false
    private var failure: CaptureInputException? = null
    private val listener = AudioRouting.OnRoutingChangedListener { routeVersion.incrementAndGet() }

    fun prepare() {
        try {
            if (!selection.device.isSource || !router.setPreferredDevice(selection.device)) {
                reject("Android rejected selected Bluetooth input ${describe(selection.device)}; no input substitution.")
            }
            router.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
            registered = true
        } catch (e: RuntimeException) {
            reject("Cannot request selected Bluetooth input: ${e.message}")
        }
    }

    fun started() { startedAt = clock() }

    fun beginRead(): Int? {
        val version = routeVersion.get()
        return if (accept() && routeVersion.get() == version) version else null
    }

    fun finishRead(token: Int?): Boolean {
        val ready = accept()
        // Never promote a buffer captured before verification or across an observed transition.
        return ready && token != null && token == routeVersion.get()
    }

    fun accept(allowWait: Boolean = true): Boolean {
        failure?.let { throw it }
        if (closed) return false
        val now = clock()
        val version = routeVersion.get()
        // Query at both PCM read boundaries. A cached success would accept another microphone
        // while Android's main-looper routing callback was still queued.
        val actual = try { router.routedDevice } catch (e: RuntimeException) {
            reject("Cannot verify capture input: ${e.message}")
        }
        val mode = try { manager.mode } catch (e: RuntimeException) {
            reject("Cannot verify audio mode: ${e.message}")
        }
        val matches = actual != null && actual.isSource && actual.id == selection.device.id &&
            actual.type == selection.device.type && mode == selection.mode.value
        if (matches) {
            if (!confirmed || version != checkedVersion) {
                report("Verified Bluetooth input: ${describe(actual)}; requested mode ${selection.mode}, actual mode $mode")
            }
            confirmed = true
            checkedVersion = version
            return true
        }
        val details = "Capture input/mode mismatch: requested ${describe(selection.device)}, mode ${selection.mode} (${selection.mode.value}); " +
            "observed ${describe(actual)}, mode $mode. Recording the phone microphone instead is not allowed."
        // AudioRecord callers discard unverified PCM during this bounded startup interval.
        // MediaRecorder cannot discard its encoded audio and must pass the immediate check.
        if (confirmed || !allowWait || now - startedAt >= STARTUP_TIMEOUT_MS) reject(details)
        return false
    }

    private fun reject(message: String): Nothing {
        report(message)
        throw CaptureInputException(message).also { failure = it }
    }

    override fun close() {
        closed = true
        if (registered) {
            registered = false
            runCatching { router.removeOnRoutingChangedListener(listener) }.onFailure { Timber.w(it, "Removing input routing listener") }
        }
    }

    private fun describe(device: AudioDeviceInfo?): String = device?.let {
        "${it.productName} (id=${it.id}, type=${it.type})"
    } ?: "input not yet reported by Android"

    companion object {
        private const val STARTUP_TIMEOUT_MS = 1500L
    }
}
