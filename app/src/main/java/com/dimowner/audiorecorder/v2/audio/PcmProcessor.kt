package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** In-place processing of interleaved, little-endian signed PCM16 audio. */
interface PcmProcessor {
    fun processInPlace(buffer: ByteArray, bytesRead: Int)

    companion object {
        fun create(mode: PcmGainMode, sampleRate: Int, channelCount: Int): PcmProcessor = when (mode) {
            PcmGainMode.OFF -> IdentityPcmProcessor
            PcmGainMode.DB_PLUS_6 -> PcmGainProcessor(6)
            PcmGainMode.DB_PLUS_12 -> PcmGainProcessor(12)
            PcmGainMode.DB_PLUS_18 -> PcmGainProcessor(18)
            PcmGainMode.AUTO_LEVEL -> PcmAutoLevelProcessor(sampleRate, channelCount)
        }
    }
}

/** Shared sample conversion keeps signed decoding and saturation identical in both processors. */
internal object Pcm16 {
    fun sample(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 255) or (buffer[offset + 1].toInt() shl 8)

    fun write(buffer: ByteArray, offset: Int, value: Double) {
        val sample = value.roundToInt().coerceIn(-32768, 32767)
        buffer[offset] = sample.toByte()
        buffer[offset + 1] = (sample shr 8).toByte()
    }

    fun length(buffer: ByteArray, bytesRead: Int): Int = bytesRead.coerceIn(0, buffer.size) and -2
}

internal class PcmGainProcessor(decibels: Int) : PcmProcessor {
    private val gain = 10.0.pow(decibels / 20.0)
    override fun processInPlace(buffer: ByteArray, bytesRead: Int) {
        for (offset in 0 until Pcm16.length(buffer, bytesRead) step 2) {
            Pcm16.write(buffer, offset, Pcm16.sample(buffer, offset) * gain)
        }
    }
}

/** A leveler, not a gate: even near-silence is preserved, with gain returning toward unity. */
internal class PcmAutoLevelProcessor(
    private val sampleRate: Int,
    private val channelCount: Int,
) : PcmProcessor {
    private var gain = 1.0

    init { require(sampleRate > 0 && channelCount > 0) }

    override fun processInPlace(buffer: ByteArray, bytesRead: Int) {
        val length = Pcm16.length(buffer, bytesRead)
        if (length == 0) return
        var squares = 0.0
        for (offset in 0 until length step 2) {
            val sample = Pcm16.sample(buffer, offset).toDouble()
            squares += sample * sample
        }
        val samples = length / 2
        val rms = sqrt(squares / samples)
        val desired = if (rms < SILENCE_RMS) 1.0 else (TARGET_RMS / rms).coerceAtMost(MAX_GAIN)
        // Time constants refer to audio time, not read-buffer count. Stereo channels share gain.
        val seconds = samples.toDouble() / (sampleRate.toDouble() * channelCount)
        val timeConstant = if (desired < gain) ATTACK_SECONDS else RELEASE_SECONDS
        val endGain = desired + (gain - desired) * exp(-seconds / timeConstant)
        val frames = (samples + channelCount - 1) / channelCount
        for (index in 0 until samples) {
            val fraction = if (frames <= 1) 1.0 else (index / channelCount).toDouble() / (frames - 1)
            val rampGain = gain + (endGain - gain) * fraction
            Pcm16.write(buffer, index * 2, Pcm16.sample(buffer, index * 2) * rampGain)
        }
        gain = endGain
    }

    companion object {
        private val TARGET_RMS = 32768.0 * 10.0.pow(-18.0 / 20.0)
        private val MAX_GAIN = 10.0.pow(18.0 / 20.0)
        private const val SILENCE_RMS = 32.768 // -60 dBFS
        private const val ATTACK_SECONDS = 0.050
        private const val RELEASE_SECONDS = 0.500
    }
}

private object IdentityPcmProcessor : PcmProcessor {
    override fun processInPlace(buffer: ByteArray, bytesRead: Int) = Unit
}
