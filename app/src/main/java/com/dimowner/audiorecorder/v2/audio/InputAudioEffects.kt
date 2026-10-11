package com.dimowner.audiorecorder.v2.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import timber.log.Timber

internal enum class InputEffectKind { NS, AEC, AGC }

internal interface InputEffectHandle {
    val enabled: Boolean
    val hasControl: Boolean
    fun setEnabledState(value: Boolean): Int
    fun release()
}

internal interface InputEffectFactory {
    fun available(kind: InputEffectKind): Boolean
    fun create(kind: InputEffectKind, sessionId: Int): InputEffectHandle?
}

internal data class InputEffectStatus(
    val kind: InputEffectKind,
    val available: Boolean,
    val requestedEnabled: Boolean?,
    val actualEnabled: Boolean? = null,
    val hasControl: Boolean? = null,
    val message: String,
) {
    override fun toString() = "$kind: available=$available, requested=${requestedEnabled ?: "system"}, " +
        "enabled=${actualEnabled ?: "unknown"}, control=${hasControl ?: "unknown"} ($message)"
}

/** Owns only the Java-level effect handles for one AudioRecord session, never the recorder itself. */
internal class InputAudioEffects(
    sessionId: Int,
    policy: InputPreprocessingPolicy,
    factory: InputEffectFactory = AndroidInputEffectFactory,
) : AutoCloseable {
    private val handles = mutableListOf<InputEffectHandle>()
    val status: List<InputEffectStatus> = InputEffectKind.entries.map { kind ->
        val requested = when (kind) {
            InputEffectKind.NS -> policy.requestedNs
            InputEffectKind.AEC -> policy.requestedAec
            InputEffectKind.AGC -> policy.requestedAgc
        }
        var available = false
        try {
            available = factory.available(kind)
            val handle = if (available) factory.create(kind, sessionId) else null
            if (handle == null) {
                InputEffectStatus(kind, available, requested, message = if (available) "create returned null" else "Unavailable")
            } else {
                // Retain before inspecting/applying: any later exception must still release it.
                handles.add(handle)
                val controlled = handle.hasControl
                val result = if (requested != null && controlled) handle.setEnabledState(requested) else null
                val actual = handle.enabled
                val message = when {
                    requested == null -> "System state retained"
                    !controlled -> "Control denied"
                    result != AudioEffect.SUCCESS -> "setEnabled failed: $result"
                    actual != requested -> "Requested state not applied"
                    else -> "Applied"
                }
                InputEffectStatus(kind, available, requested, actual, controlled, message)
            }
        } catch (e: Exception) {
            Timber.w(e, "Input effect %s unavailable for session %d", kind, sessionId)
            InputEffectStatus(kind, available, requested, message = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun close() {
        handles.forEach { handle -> runCatching { handle.release() }.onFailure { Timber.w(it, "Releasing input effect") } }
        handles.clear()
    }
}

private object AndroidInputEffectFactory : InputEffectFactory {
    override fun available(kind: InputEffectKind) = when (kind) {
        InputEffectKind.NS -> NoiseSuppressor.isAvailable()
        InputEffectKind.AEC -> AcousticEchoCanceler.isAvailable()
        InputEffectKind.AGC -> AutomaticGainControl.isAvailable()
    }

    override fun create(kind: InputEffectKind, sessionId: Int): InputEffectHandle? {
        val effect = when (kind) {
            InputEffectKind.NS -> NoiseSuppressor.create(sessionId)
            InputEffectKind.AEC -> AcousticEchoCanceler.create(sessionId)
            InputEffectKind.AGC -> AutomaticGainControl.create(sessionId)
        } ?: return null
        return AndroidInputEffectHandle(effect)
    }

    private class AndroidInputEffectHandle(private val effect: AudioEffect) : InputEffectHandle {
        override val enabled get() = effect.enabled
        override val hasControl get() = effect.hasControl()
        override fun setEnabledState(value: Boolean) = effect.setEnabled(value)
        override fun release() = effect.release()
    }
}
