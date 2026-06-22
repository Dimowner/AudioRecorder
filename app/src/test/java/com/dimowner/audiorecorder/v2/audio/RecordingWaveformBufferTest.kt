package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RecordingWaveformBufferTest {

    // ── compressUniformly ────────────────────────────────────────────────────

    @Test
    fun `compressUniformly reduces buffer to cap over 2 slots`() {
        val targetSize = 10
        val cap = targetSize * RecordingWaveformBuffer.HALVING_CAP_MULTIPLIER // 40
        val buf = RecordingWaveformBuffer(targetSize = targetSize)
        // Adding exactly cap samples triggers compression automatically
        repeat(cap) { buf.add(1000) }
        assertEquals(cap / 2, buf.size())
    }

    @Test
    fun `compressUniformly preserves constant-amplitude signal`() {
        val buf = RecordingWaveformBuffer(targetSize = 10)
        val cap = 10 * RecordingWaveformBuffer.HALVING_CAP_MULTIPLIER
        repeat(cap) { buf.add(5000) }
        buf.compressUniformly()
        val result = buf.downsampleToIntArray()
        assertTrue(result.all { it == 5000 })
    }

    @Test
    fun `compressUniformly is idempotent on constant signal after multiple rounds`() {
        val buf = RecordingWaveformBuffer(targetSize = 10)
        val cap = 10 * RecordingWaveformBuffer.HALVING_CAP_MULTIPLIER
        // Trigger 3 automatic compression rounds
        repeat(cap * 3) { buf.add(8000) }
        val result = buf.downsampleToIntArray()
        assertTrue("All bins should be ~8000, got: ${result.toList()}", result.all { it == 8000 })
    }

    // ── KEY: timeline correctness test ───────────────────────────────────────

    @Test
    fun `downsampleToIntArray preserves temporal order across multiple compressions`() {
        // First half of the recording is silence (0), second half is full amplitude (32767).
        // After many compressions the output must still reflect this split faithfully:
        // the first half of output bins ≈ 0 and the second half ≈ 32767.
        val targetSize = 100
        val totalSamples = 10_000   // enough to trigger many compressions
        val buf = RecordingWaveformBuffer(targetSize = targetSize)
        repeat(totalSamples / 2) { buf.add(0) }
        repeat(totalSamples / 2) { buf.add(32767) }

        val result = buf.downsampleToIntArray()

        // First quarter of output must be clearly silence
        for (i in 0 until targetSize / 4) {
            assertTrue("Expected near 0 at index $i, got ${result[i]}", result[i] < 3000)
        }
        // Last quarter of output must be clearly full amplitude
        for (i in targetSize * 3 / 4 until targetSize) {
            assertTrue("Expected near 32767 at index $i, got ${result[i]}", result[i] > 29000)
        }
    }

    @Test
    fun `downsampleToIntArray output is monotonically increasing for linearly rising signal`() {
        // Input: amplitude rises linearly 0 → 32767 over many samples.
        // Output bins should also rise monotonically.
        val targetSize = 20
        val totalSamples = 5_000
        val buf = RecordingWaveformBuffer(targetSize = targetSize)
        repeat(totalSamples) { i -> buf.add(i * 32767 / totalSamples) }

        val result = buf.downsampleToIntArray()

        for (i in 1 until targetSize) {
            assertTrue(
                "Expected result[$i]=${result[i]} >= result[${i-1}]=${result[i-1]}",
                result[i] >= result[i - 1]
            )
        }
    }

    // ── automatic compression cap ─────────────────────────────────────────────

    @Test
    fun `buffer size never exceeds cap after many samples`() {
        val targetSize = 10
        val cap = targetSize * RecordingWaveformBuffer.HALVING_CAP_MULTIPLIER
        val buf = RecordingWaveformBuffer(targetSize = targetSize)
        repeat(10_000) { i -> buf.add(i % 32767) }
        assertTrue("size ${buf.size()} should be < cap $cap", buf.size() < cap)
    }

    // ── reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all accumulated samples`() {
        val buf = RecordingWaveformBuffer(targetSize = 10)
        repeat(50) { buf.add(it) }
        buf.reset()
        assertEquals(0, buf.size())
    }

    @Test
    fun `reset clears timeline counters so next recording starts fresh`() {
        val buf = RecordingWaveformBuffer(targetSize = 10)
        repeat(500) { buf.add(32767) }
        buf.reset()
        buf.add(42)
        val result = buf.downsampleToIntArray()
        assertEquals(42, result[0])
        // All other bins should be zero (no previous data leaks through)
        for (i in 1 until 10) assertEquals("index $i should be 0", 0, result[i])
    }

    @Test
    fun `add works correctly after reset`() {
        val buf = RecordingWaveformBuffer(targetSize = 10)
        repeat(500) { buf.add(it % 32767) }
        buf.reset()
        buf.add(42)
        assertEquals(1, buf.size())
    }

    // ── downsampleToIntArray general contract ────────────────────────────────

    @Test
    fun `downsampleToIntArray always returns targetSize array`() {
        val buf = RecordingWaveformBuffer(targetSize = 100)
        repeat(300) { buf.add(it % 32767) }
        assertEquals(100, buf.downsampleToIntArray().size)
    }

    @Test
    fun `downsampleToIntArray on empty buffer returns zero-filled targetSize array`() {
        val buf = RecordingWaveformBuffer(targetSize = 50)
        val result = buf.downsampleToIntArray()
        assertEquals(50, result.size)
        assertTrue(result.all { it == 0 })
    }

    @Test
    fun `downsampleToIntArray with fewer samples than targetSize left-aligns and zero-fills`() {
        val buf = RecordingWaveformBuffer(targetSize = 100)
        intArrayOf(10, 20, 30).forEach { buf.add(it) }
        val result = buf.downsampleToIntArray()
        assertEquals(10, result[0])
        assertEquals(20, result[1])
        assertEquals(30, result[2])
        for (i in 3 until 100) assertEquals("index $i should be 0", 0, result[i])
    }

    @Test
    fun `downsampleToIntArray output values are within 0 to 32767`() {
        val buf = RecordingWaveformBuffer(targetSize = 50)
        repeat(500) { buf.add(it % 32767) }
        val result = buf.downsampleToIntArray()
        assertTrue(result.all { it in 0..32767 })
    }

    @Test
    fun `downsampleToIntArray returns targetSize after many compressions`() {
        val buf = RecordingWaveformBuffer(targetSize = 20)
        repeat(10_000) { buf.add(it % 32767) }
        assertEquals(20, buf.downsampleToIntArray().size)
    }

    @Test
    fun `downsampleToIntArray result is reproducible on same buffer state`() {
        val buf = RecordingWaveformBuffer(targetSize = 30)
        repeat(200) { buf.add(it % 500) }
        val first = buf.downsampleToIntArray()
        val second = buf.downsampleToIntArray()
        assertTrue(first.contentEquals(second))
    }

    // ── thread-safety ─────────────────────────────────────────────────────────

    @Test
    fun `concurrent add-reset and downsample do not throw`() {
        // Regression test for IndexOutOfBoundsException when reset() (new recording
        // starting) raced downsampleToIntArray() (previous recording being saved)
        // on different IO-pool threads.
        val buf = RecordingWaveformBuffer(targetSize = 50)
        val error = AtomicReference<Throwable?>(null)
        val running = AtomicBoolean(true)

        val writer = Thread {
            try {
                while (running.get()) {
                    // Enough samples to trigger compression, so the compressed-region
                    // branch of getAmplitudeAtOriginalIndex is exercised.
                    repeat(1000) { buf.add(it % 32767) }
                    buf.reset()
                }
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        writer.start()

        try {
            val deadline = System.currentTimeMillis() + 500
            while (System.currentTimeMillis() < deadline && error.get() == null) {
                assertEquals(50, buf.downsampleToIntArray().size)
            }
        } catch (t: Throwable) {
            error.set(t)
        } finally {
            running.set(false)
            writer.join(5000)
        }

        error.get()?.let { throw AssertionError("Concurrent access threw", it) }
    }
}
