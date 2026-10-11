package com.dimowner.audiorecorder.v2.audio

import android.media.AudioRecord
import android.media.AudioManager
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode

/** Frozen configuration and owned effects for one capture, shared by WAV and direct AAC. */
internal class CaptureProcessingSession(
    private val recorder: AudioRecord,
    input: AudioInput,
    sampleRate: Int,
    channelCount: Int,
    backend: String,
    private val diagnostics: CaptureDiagnostics,
    private val manager: AudioManager? = null,
    requestedBitrate: Int? = null,
) : AutoCloseable {
    private val mic = input as? AudioInput.Mic
    private val effects = mic?.let { InputAudioEffects(recorder.audioSessionId, it.preprocessing) }
    private val processor = PcmProcessor.create(mic?.gain ?: PcmGainMode.OFF, sampleRate, channelCount)
    private val summary = buildString {
        appendLine("Backend: $backend / AudioRecord")
        appendLine(describeInput(input))
        appendLine("Requested: $sampleRate Hz, $channelCount channel(s)")
        if (requestedBitrate != null) appendLine("Requested AAC bitrate: $requestedBitrate bps")
        appendLine("AudioRecord: session=${recorder.audioSessionId}, ${recorder.sampleRate} Hz, ${recorder.channelCount} channel(s)")
        if (effects != null) appendLine("Java effect states observed during configuration:")
        effects?.status?.forEach { appendLine(it) }
        if (mic == null) appendLine("Microphone effects and software gain not applied to system playback.")
        append("Java effect states do not prove that headset/OEM processing is bypassed.")
    }
    private val requestedRate = sampleRate
    private val requestedChannels = channelCount
    private var prepared = false
    private val inputBufferBytes = if (mic?.bluetoothInput == null) 0L
        else recorder.bufferSizeInFrames.toLong() * channelCount * 2
    private var bytesToDrain = inputBufferBytes
    private var drainingRouteEpoch: Int? = null
    private val routing = mic?.bluetoothInput?.let { selection ->
        manager?.let { InputRoutingGuard(recorder, it, selection, { evidence -> diagnostics.session(summary + "\n" + evidence) }) }
    }

    init { diagnostics.session(summary) }

    fun prepare(): Boolean {
        return try {
            verifyAudioMode()
            if (recorder.sampleRate != requestedRate || recorder.channelCount != requestedChannels) {
                throw CaptureInputException("Android created a different PCM rate/channel count than selected.")
            }
            if (mic != null && mic.audioSource != AudioSource.DEFAULT.value && recorder.audioSource != mic.audioSource) {
                throw CaptureInputException("Requested source ${mic.audioSource}, AudioRecord reports ${recorder.audioSource}; no source substitution.")
            }
            if (mic?.audioSource == AudioSource.UNPROCESSED.value &&
                manager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) != "true") {
                throw CaptureInputException("Android does not advertise UNPROCESSED support; select another source explicitly.")
            }
            if (mic?.bluetoothInput != null && routing == null) throw CaptureInputException("Bluetooth input verification is unavailable.")
            if (mic?.bluetoothInput != null && inputBufferBytes <= 0) throw CaptureInputException("Bluetooth input buffer capacity is unknown; unverified queued audio cannot be discarded safely.")
            routing?.prepare()
            prepared = true
            true
        } catch (e: Exception) {
            diagnostics.session(summary + "\nCapture configuration rejected: ${e.message}")
            false
        }
    }

    fun started(recorder: AudioRecord) {
        routing?.started()
        val routed = runCatching { recorder.routedDevice }.getOrNull()
        diagnostics.session(summary + "\nObserved input: " +
            (routed?.let { "${it.productName} (type=${it.type}, id=${it.id})" } ?: "not yet reported by Android"))
    }

    /** Null marks a read that starts before the selected input is verified. */
    fun beginRead(): Int? {
        verifyAudioMode()
        return if (routing == null) 0 else routing.beginRead()
    }

    /** False means this startup/transition buffer must be discarded, not written or encoded. */
    fun acceptPcm(buffer: ByteArray, bytesRead: Int, readToken: Int?): Boolean {
        check(prepared) { "Capture session was not prepared" }
        verifyAudioMode()
        if (routing != null) {
            if (!routing.finishRead(readToken)) {
                bytesToDrain = inputBufferBytes
                drainingRouteEpoch = null
                return false
            }
            if (readToken != drainingRouteEpoch) {
                // Also covers a route change while the writer/encoder was busy between reads.
                bytesToDrain = inputBufferBytes
                drainingRouteEpoch = readToken
            }
            // AudioRecord can still contain older samples even after its reported route changes.
            // Drain a complete client-buffer capacity after initial verification/transition before
            // promoting any PCM to the file. No extra buffer is allocated for this warm-up.
            if (bytesToDrain > 0) {
                bytesToDrain = (bytesToDrain - bytesRead).coerceAtLeast(0)
                return false
            }
        }
        processor.processInPlace(buffer, bytesRead)
        return true
    }
    override fun close() { routing?.close(); effects?.close() }

    private fun verifyAudioMode() {
        // Bluetooth's routing guard already verifies mode at both read boundaries.
        if (routing != null) return
        mic?.let { CaptureConfiguration.modeProblem(it, manager?.mode) }?.let { reason ->
            diagnostics.session(summary + "\n" + reason)
            throw CaptureInputException(reason)
        }
    }

    companion object {
        fun describeInput(input: AudioInput): String = when (input) {
            is AudioInput.Mic -> "Source: ${AudioSource.fromValue(input.audioSource)}\n" +
                (input.requestedSettings?.let { settings ->
                    "Scope: ${if (settings.onlyBluetooth) "Bluetooth microphone only" else "all microphones"}\n" +
                    "Selected Android mode: ${settings.mode}; preprocessing: ${settings.preprocessing}; software gain: ${settings.gain}\n" +
                    if (input.audioMode == null) "Scoped overrides skipped for non-Bluetooth microphone.\n" else "Effective Android mode request: ${input.audioMode}\n"
                } ?: "") +
                "Effective preprocessing request: ${input.preprocessing}; software gain: ${input.gain}" +
                (input.bluetoothInput?.let { "\nRequested Bluetooth input: ${it.device.productName} (id=${it.device.id}); mode=${it.mode}" } ?: "") +
                if (input.audioSource == AudioSource.UNPROCESSED.value) "\nUNPROCESSED requires advertised platform support; vendor DSP cannot be independently verified." else ""
            is AudioInput.SystemPlayback -> "Source: SYSTEM_AUDIO"
        }
    }
}
