package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmProcessorTest {
    @Test
    fun `plus 6 dB scales signed little endian samples`() = assertFixedGain(PcmGainMode.DB_PLUS_6, 6)

    @Test
    fun `plus 12 dB scales signed little endian samples`() = assertFixedGain(PcmGainMode.DB_PLUS_12, 12)

    @Test
    fun `plus 18 dB scales signed little endian samples`() = assertFixedGain(PcmGainMode.DB_PLUS_18, 18)

    @Test
    fun `fixed gains saturate both signed extremes without wrapping`() {
        for (mode in listOf(PcmGainMode.DB_PLUS_6, PcmGainMode.DB_PLUS_12, PcmGainMode.DB_PLUS_18)) {
            val data = pcm(32767, -32768, 20000, -20000)
            PcmProcessor.create(mode, 48000, 1).processInPlace(data, data.size)
            assertArrayEquals(intArrayOf(32767, -32768, 32767, -32768), samples(data))
        }
    }

    @Test
    fun `OFF preserves every byte including odd input and unused tail`() {
        val original = byteArrayOf(0, 1, -1, -128, 127, 35, -56)
        val data = original.copyOf()
        PcmProcessor.create(PcmGainMode.OFF, 48000, 2).processInPlace(data, 5)
        assertArrayEquals(original, data)
    }

    @Test
    fun `every mode ignores an odd trailing byte and preserves unread tail`() {
        for (mode in PcmGainMode.entries) {
            val data = byteArrayOf(-24, 3, 77, 88, 99, 111)
            PcmProcessor.create(mode, 48000, 1).processInPlace(data, 3)
            assertArrayEquals(byteArrayOf(77, 88, 99, 111), data.copyOfRange(2, data.size))
        }
    }

    @Test
    fun `zero and incomplete sample reads do not change the buffer or advance auto gain`() {
        for (mode in PcmGainMode.entries) {
            val processor = PcmProcessor.create(mode, 48000, 1)
            val original = pcm(1000, -1000)
            val data = original.copyOf()
            processor.processInPlace(data, 0)
            processor.processInPlace(data, 1)
            processor.processInPlace(byteArrayOf(), 0)
            assertArrayEquals(original, data)
            val expected = constant(1000, 4800)
            val actual = expected.copyOf()
            PcmProcessor.create(mode, 48000, 1).processInPlace(expected, expected.size)
            processor.processInPlace(actual, actual.size)
            assertArrayEquals(expected, actual)
        }
    }

    @Test
    fun `auto level boosts a quiet sine to minus 18 dBFS RMS`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        var output = byteArrayOf()
        repeat(60) {
            output = sine(2000, 4800)
            processor.processInPlace(output, output.size)
        }
        assertEquals(targetRms, rms(output), 3.0)
    }

    @Test
    fun `auto level caps the boost at plus 18 dB`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        var output = byteArrayOf()
        repeat(60) {
            output = sine(100, 4800)
            processor.processInPlace(output, output.size)
        }
        assertEquals(rms(sine(100, 4800)) * 10.0.pow(18.0 / 20.0), rms(output), 1.0)
    }

    @Test
    fun `auto level attenuates a loud sine to target`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        var output = byteArrayOf()
        repeat(10) {
            output = sine(16000, 4800)
            processor.processInPlace(output, output.size)
        }
        assertEquals(targetRms, rms(output), 3.0)
    }

    @Test
    fun `increasing gain uses a 500 ms time constant and ramps inside the buffer`() {
        val data = constant(1000, 4800) // 100 ms at 48 kHz mono.
        PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1).processInPlace(data, data.size)
        val output = samples(data)
        val expectedEndGain = 1.0 + (targetRms / 1000 - 1.0) * (1.0 - exp(-0.1 / 0.5))
        assertEquals(1000.0, output.first().toDouble(), 1.0)
        assertEquals(1000 * expectedEndGain, output.last().toDouble(), 1.0)
        assertTrue((1 until output.size).all { output[it] - output[it - 1] in 0..1 })
    }

    @Test
    fun `reducing gain uses a 50 ms time constant`() {
        val data = constant(10000, 2400) // One 50 ms time constant, starting at unity gain.
        PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1).processInPlace(data, data.size)
        val expectedEndGain = targetRms / 10000 + (1 - targetRms / 10000) * exp(-1.0)
        assertEquals(10000 * expectedEndGain, samples(data).last().toDouble(), 1.0)
    }

    @Test
    fun `gain ramps continue across buffer boundaries`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        val first = constant(1000, 4800)
        val second = first.copyOf()
        processor.processInPlace(first, first.size)
        processor.processInPlace(second, second.size)
        assertEquals(samples(first).last(), samples(second).first())
        assertTrue(samples(second).last() > samples(second).first())
    }

    @Test
    fun `near silence relaxes boosted gain to unity without muting audio`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        repeat(30) { processConstant(processor, 1000, 4800) }
        val first = processConstant(processor, 10, 4800)
        assertTrue(samples(first).first() > samples(first).last())
        var last = first
        repeat(10) { last = processConstant(processor, 10, 4800) }
        assertTrue(samples(last).all { it == 10 })
    }

    @Test
    fun `near silence relaxes attenuated gain upward to unity`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        repeat(10) { processConstant(processor, 10000, 4800) }
        var last = processConstant(processor, -10, 4800)
        assertTrue(samples(last).first() in -9..-1)
        repeat(40) { last = processConstant(processor, -10, 4800) }
        assertTrue(samples(last).all { it == -10 })
    }

    @Test
    fun `digital silence stays zero and relaxes gain`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        repeat(30) { processConstant(processor, 1000, 4800) }
        repeat(10) {
            assertTrue(samples(processConstant(processor, 0, 4800)).all { it == 0 })
        }
        assertEquals(1000, samples(processConstant(processor, 1000, 4800)).first())
    }

    @Test
    fun `auto timing follows sample rate and stereo frame duration`() {
        val mono48 = processConstant(PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1), 1000, 4800)
        val mono16 = processConstant(PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 16000, 1), 1000, 1600)
        val stereo = processConstant(PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 2), 1000, 9600)
        assertEquals(samples(mono48).last(), samples(mono16).last())
        val stereoSamples = samples(stereo)
        assertArrayEquals(samples(mono48), IntArray(4800) { stereoSamples[it * 2] })
        assertArrayEquals(samples(mono48), IntArray(4800) { stereoSamples[it * 2 + 1] })
    }

    @Test
    fun `auto level shares a gain across unequal stereo channels`() {
        val data = pcm(*IntArray(9600) { if (it % 2 == 0) 1000 else -2000 })
        PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 2).processInPlace(data, data.size)
        val output = samples(data)
        for (frame in 0 until 4800) {
            assertEquals(-2.0 * output[frame * 2], output[frame * 2 + 1].toDouble(), 1.0)
        }
        val desiredGain = targetRms / sqrt((1000.0.pow(2) + 2000.0.pow(2)) / 2)
        val expectedEndGain = 1 + (desiredGain - 1) * (1 - exp(-0.1 / 0.5))
        assertEquals(1000 * expectedEndGain, output[9598].toDouble(), 1.0)
    }

    @Test
    fun `auto RMS and timing exclude unread bytes`() {
        val prefix = constant(1000, 4800)
        val withTail = prefix + constant(32767, 4800)
        PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1).processInPlace(withTail, prefix.size)
        PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1).processInPlace(prefix, prefix.size)
        assertArrayEquals(prefix, withTail.copyOfRange(0, prefix.size))
        assertArrayEquals(constant(32767, 4800), withTail.copyOfRange(prefix.size, withTail.size))
    }

    @Test
    fun `auto level saturates a transient after boosting quiet audio`() {
        val processor = PcmProcessor.create(PcmGainMode.AUTO_LEVEL, 48000, 1)
        repeat(30) { processConstant(processor, 1000, 4800) }
        val transient = pcm(32767, -32768, 32767, -32768)
        processor.processInPlace(transient, transient.size)
        assertArrayEquals(intArrayOf(32767, -32768, 32767, -32768), samples(transient))
    }

    private val targetRms = 32768.0 * 10.0.pow(-18.0 / 20.0)

    private fun assertFixedGain(mode: PcmGainMode, db: Int) {
        val input = intArrayOf(0, 1, -1, 1000, -1000, 257, -257)
        val data = pcm(*input)
        PcmProcessor.create(mode, 48000, 1).processInPlace(data, data.size)
        val gain = 10.0.pow(db / 20.0)
        assertArrayEquals(input.map { (it * gain).roundToInt() }.toIntArray(), samples(data))
    }

    private fun processConstant(processor: PcmProcessor, sample: Int, count: Int): ByteArray {
        val data = constant(sample, count)
        processor.processInPlace(data, data.size)
        return data
    }

    private fun constant(sample: Int, count: Int) = pcm(*IntArray(count) { sample })

    private fun sine(amplitude: Int, count: Int) = pcm(*IntArray(count) {
        (amplitude * sin(2 * PI * 1000 * it / 48000)).roundToInt()
    })

    private fun rms(data: ByteArray): Double = sqrt(samples(data).map { it.toDouble().pow(2) }.average())

    private fun pcm(vararg samples: Int): ByteArray = ByteArray(samples.size * 2) { index ->
        (samples[index / 2] shr (8 * (index % 2))).toByte()
    }

    private fun samples(data: ByteArray): IntArray = IntArray(data.size / 2) {
        ((data[it * 2].toInt() and 0xff) or (data[it * 2 + 1].toInt() shl 8)).toShort().toInt()
    }
}
