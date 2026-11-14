/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [PcmRingBuffer]: frame alignment, snapshots before and after the first wrap,
 * snapshots taken while a writer keeps running, and the WAV header of a snapshot.
 *
 * Runs on real temporary files; no Android framework involved.
 */
class PcmRingBufferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newBuffer(capacity: Long, frameSize: Int = 2): PcmRingBuffer =
        PcmRingBuffer(File(tempFolder.root, "ring.pcm"), capacity, frameSize)

    private fun sequentialBytes(from: Int, count: Int): ByteArray =
        ByteArray(count) { ((from + it) and 0xFF).toByte() }

    private fun File.pcmData(): ByteArray = readBytes().copyOfRange(WAV_HEADER_SIZE, length().toInt())

    private fun ByteArray.intLe(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.shortLe(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    // ---- frame alignment ----

    @Test
    fun `capacity is rounded down to whole frames`() {
        newBuffer(capacity = 1001, frameSize = 2).use { assertEquals(1000L, it.capacity) }
        newBuffer(capacity = 1003, frameSize = 4).use { assertEquals(1000L, it.capacity) }
    }

    @Test
    fun `odd sized writes only commit whole frames`() {
        newBuffer(capacity = 1000, frameSize = 2).use { buffer ->
            buffer.write(sequentialBytes(0, 3))
            assertEquals(2L, buffer.bufferedBytes)
            buffer.write(sequentialBytes(3, 1))
            assertEquals(4L, buffer.bufferedBytes)
            buffer.write(sequentialBytes(4, 5))
            assertEquals(8L, buffer.bufferedBytes)

            val out = tempFolder.newFile("aligned.wav")
            val dataLen = buffer.snapshotToWav(out)
            assertEquals(8L, dataLen)
            assertArrayEquals(sequentialBytes(0, 8), out.pcmData())
        }
    }

    @Test
    fun `split frames are reassembled across many writes and wraps`() {
        newBuffer(capacity = 64, frameSize = 4).use { buffer ->
            val all = sequentialBytes(0, 1003)
            var offset = 0
            var step = 1
            while (offset < all.size) {
                val n = minOf(step, all.size - offset)
                buffer.write(all, offset, n)
                assertEquals(0L, buffer.totalBytesWritten % 4)
                offset += n
                step = step % 7 + 1
            }
            // 1003 bytes = 250 whole frames + 3 pending bytes.
            assertEquals(1000L, buffer.totalBytesWritten)
            val out = tempFolder.newFile("reassembled.wav")
            assertEquals(64L, buffer.snapshotToWav(out))
            assertArrayEquals(all.copyOfRange(1000 - 64, 1000), out.pcmData())
        }
    }

    @Test
    fun `discarding a partial frame keeps later audio aligned`() {
        newBuffer(capacity = 100, frameSize = 2).use { buffer ->
            buffer.write(byteArrayOf(1, 2, 3))
            buffer.discardPartialFrame()
            buffer.write(byteArrayOf(10, 11))
            val out = tempFolder.newFile("discard.wav")
            buffer.snapshotToWav(out)
            assertArrayEquals(byteArrayOf(1, 2, 10, 11), out.pcmData())
        }
    }

    // ---- before the first wrap ----

    @Test
    fun `snapshot before the first wrap holds exactly what was written`() {
        newBuffer(capacity = 1000).use { buffer ->
            buffer.write(sequentialBytes(0, 300))
            assertFalse(buffer.hasWrapped)
            assertEquals(300L, buffer.bufferedBytes)

            val out = tempFolder.newFile("before-wrap.wav")
            assertEquals(300L, buffer.snapshotToWav(out))
            assertEquals(WAV_HEADER_SIZE + 300L, out.length())
            assertArrayEquals(sequentialBytes(0, 300), out.pcmData())
        }
    }

    @Test
    fun `snapshot of a buffer filled exactly to capacity is not treated as wrapped`() {
        newBuffer(capacity = 256).use { buffer ->
            buffer.write(sequentialBytes(0, 256))
            assertFalse(buffer.hasWrapped)
            val out = tempFolder.newFile("full.wav")
            assertEquals(256L, buffer.snapshotToWav(out))
            assertArrayEquals(sequentialBytes(0, 256), out.pcmData())
        }
    }

    @Test
    fun `snapshot of an empty buffer is a valid header with no data`() {
        newBuffer(capacity = 1000).use { buffer ->
            val out = tempFolder.newFile("empty.wav")
            assertEquals(0L, buffer.snapshotToWav(out))
            assertEquals(WAV_HEADER_SIZE.toLong(), out.length())
            val bytes = out.readBytes()
            assertEquals(36, bytes.intLe(4))
            assertEquals(0, bytes.intLe(40))
        }
    }

    // ---- after wrapping ----

    @Test
    fun `snapshot after wrapping returns the newest capacity bytes oldest first`() {
        newBuffer(capacity = 1000).use { buffer ->
            // 2.5 laps, written in uneven chunks so the head lands mid-file.
            val all = sequentialBytes(0, 2500)
            var offset = 0
            for (size in generateSequence(130) { it + 34 }) {
                if (offset >= all.size) break
                val n = minOf(size, all.size - offset)
                buffer.write(all, offset, n)
                offset += n
            }
            assertTrue(buffer.hasWrapped)
            assertEquals(1000L, buffer.bufferedBytes)

            val out = tempFolder.newFile("wrapped.wav")
            assertEquals(1000L, buffer.snapshotToWav(out))
            assertArrayEquals(all.copyOfRange(1500, 2500), out.pcmData())
        }
    }

    @Test
    fun `a single write larger than capacity keeps only its tail`() {
        newBuffer(capacity = 100).use { buffer ->
            buffer.write(sequentialBytes(0, 10))
            buffer.write(sequentialBytes(10, 350))
            assertEquals(360L, buffer.totalBytesWritten)
            val out = tempFolder.newFile("oversized.wav")
            assertEquals(100L, buffer.snapshotToWav(out))
            assertArrayEquals(sequentialBytes(260, 100), out.pcmData())
        }
    }

    @Test
    fun `repeated snapshots do not disturb the buffer`() {
        newBuffer(capacity = 500).use { buffer ->
            buffer.write(sequentialBytes(0, 800))
            val first = tempFolder.newFile("first.wav")
            buffer.snapshotToWav(first)
            buffer.write(sequentialBytes(800, 100))
            val second = tempFolder.newFile("second.wav")
            buffer.snapshotToWav(second)
            assertArrayEquals(sequentialBytes(300, 500), first.pcmData())
            assertArrayEquals(sequentialBytes(400, 500), second.pcmData())
        }
    }

    // ---- concurrent writes ----

    /**
     * Runs a writer thread that stores consecutive frame indexes (4-byte frames), split into
     * writes that are not frame aligned. It first fills the buffer past one lap as fast as it
     * can, then keeps writing at [bytesPerSecond] (unthrottled when 0), and runs [block] while
     * it is going.
     */
    private fun withRunningWriter(
        buffer: PcmRingBuffer,
        bytesPerSecond: Int,
        block: () -> Unit,
    ) {
        val frameSize = 4
        val capacityFrames = buffer.capacity / frameSize
        val running = AtomicBoolean(true)
        val writerError = AtomicReference<Throwable?>(null)
        val wrapped = CountDownLatch(1)
        val writer = Thread {
            try {
                var index = 0
                val chunk = ByteBuffer.allocate(frameSize * 317).order(ByteOrder.LITTLE_ENDIAN)
                val bytes = chunk.array()
                val pauseNanos = if (bytesPerSecond > 0) bytes.size * 1_000_000_000L / bytesPerSecond else 0L
                while (running.get()) {
                    chunk.clear()
                    while (chunk.remaining() >= frameSize) chunk.putInt(index++)
                    // Split at an odd offset so individual writes are not frame aligned.
                    val split = 4 * 101 + 3
                    buffer.write(bytes, 0, split)
                    buffer.write(bytes, split, bytes.size - split)
                    if (index > capacityFrames) {
                        wrapped.countDown()
                        if (pauseNanos > 0) Thread.sleep(pauseNanos / 1_000_000, (pauseNanos % 1_000_000).toInt())
                    }
                }
            } catch (t: Throwable) {
                writerError.set(t)
                wrapped.countDown()
            }
        }
        writer.start()
        try {
            assertTrue("writer did not wrap in time", wrapped.await(30, TimeUnit.SECONDS))
            writerError.get()?.let { throw AssertionError("writer failed", it) }
            block()
        } finally {
            running.set(false)
            writer.join(5_000)
        }
        writerError.get()?.let { throw AssertionError("writer failed", it) }
    }

    /** Reads the frame indexes of a snapshot, checking its size and header on the way. */
    private fun File.frameIndexes(dataLen: Long): IntArray {
        assertEquals(0L, dataLen % 4)
        assertEquals(dataLen, length() - WAV_HEADER_SIZE)
        val bytes = readBytes()
        assertEquals(dataLen.toInt(), bytes.intLe(40))
        assertEquals(dataLen.toInt() + 36, bytes.intLe(4))
        val data = bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size)
        return IntArray(data.size / 4) { data.intLe(it * 4) }
    }

    /**
     * Real capture writes 32 KB/s; this writer runs ~10x faster. A snapshot must then be the
     * window as it was when the snapshot started: contiguous, in order, ending at the head.
     */
    @Test
    fun `snapshot during realistic concurrent writes is the full window in order`() {
        val capacityFrames = 512 * 1024 // 2 MiB: several snapshot chunks
        newBuffer(capacity = capacityFrames * 4L, frameSize = 4).use { buffer ->
            withRunningWriter(buffer, bytesPerSecond = 320_000) {
                repeat(5) { round ->
                    val before = buffer.totalBytesWritten / 4
                    val out = tempFolder.newFile("realistic-$round.wav")
                    val frames = out.frameIndexes(buffer.snapshotToWav(out))
                    val after = buffer.totalBytesWritten / 4

                    for (i in 1 until frames.size) {
                        assertEquals("gap or reordering at frame $i", frames[i - 1] + 1, frames[i])
                    }
                    val endExclusive = frames.last() + 1L
                    assertTrue("snapshot ended at $endExclusive, before $before", endExclusive >= before)
                    assertTrue("snapshot ended at $endExclusive, after $after", endExclusive <= after)
                    // Never older than one window back from its end...
                    assertTrue(frames.first() >= endExclusive - capacityFrames)
                    // ...and the only audio it may miss is the oldest, overwritten by what the
                    // writer added while the copy ran (normally nothing at this rate).
                    assertTrue(
                        "snapshot has ${frames.size} frames, writer added ${after - before}",
                        frames.size >= capacityFrames - (after - before)
                    )
                }
            }
        }
    }

    /**
     * Stress: an unthrottled writer (orders of magnitude faster than capture) may lap a snapshot
     * in progress, even repeatedly, so a snapshot can come out short or empty. Overwritten audio
     * must then be skipped, never copied out of order or torn: frames stay whole and strictly
     * increasing, and the header always matches the data.
     */
    @Test
    fun `snapshot during unthrottled concurrent writes stays whole and ordered`() {
        val capacityFrames = 50_000
        newBuffer(capacity = capacityFrames * 4L, frameSize = 4).use { buffer ->
            withRunningWriter(buffer, bytesPerSecond = 0) {
                repeat(20) { round ->
                    val out = tempFolder.newFile("stress-$round.wav")
                    val frames = out.frameIndexes(buffer.snapshotToWav(out))
                    assertTrue(frames.size <= capacityFrames)
                    for (i in 1 until frames.size) {
                        assertTrue(
                            "frame ${frames[i]} after ${frames[i - 1]} is out of order",
                            frames[i] > frames[i - 1]
                        )
                    }
                }
            }
        }
    }

    // ---- WAV header ----

    @Test
    fun `snapshot header describes 16 kHz mono 16-bit PCM with correct sizes`() {
        newBuffer(capacity = ReplayBufferConfig.bufferSizeBytes(5)).use { buffer ->
            buffer.write(ByteArray(32_000) { 7 })
            val out = tempFolder.newFile("header.wav")
            val dataLen = buffer.snapshotToWav(out)
            val bytes = out.readBytes()

            assertEquals(32_000L, dataLen)
            assertEquals(WAV_HEADER_SIZE + 32_000, bytes.size)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals(32_000 + 36, bytes.intLe(4)) // RIFF chunk size = file size - 8
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals(16, bytes.intLe(16))
            assertEquals(1, bytes.shortLe(20)) // PCM
            assertEquals(1, bytes.shortLe(22)) // mono
            assertEquals(16_000, bytes.intLe(24))
            assertEquals(32_000, bytes.intLe(28)) // byte rate
            assertEquals(2, bytes.shortLe(32)) // block align
            assertEquals(16, bytes.shortLe(34)) // bits per sample
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
            assertEquals(32_000, bytes.intLe(40))
        }
    }

    @Test
    fun `header sizes match a wrapped buffer of the largest option`() {
        // 60 minutes: the biggest buffer a user can choose, still far under the 2 GiB WAV limit.
        val capacity = ReplayBufferConfig.bufferSizeBytes(60)
        assertEquals(115_200_000L, capacity)
        newBuffer(capacity = 4_000).use { buffer ->
            buffer.write(ByteArray(10_001))
            val out = tempFolder.newFile("wrapped-header.wav")
            assertEquals(4_000L, buffer.snapshotToWav(out))
            val bytes = out.readBytes()
            assertEquals(4_000 + 36, bytes.intLe(4))
            assertEquals(4_000, bytes.intLe(40))
            assertEquals(out.length() - 8, bytes.intLe(4).toLong())
        }
    }

    // ---- lifecycle ----

    @Test
    fun `a new buffer discards a stale file left behind`() {
        val file = File(tempFolder.root, "ring.pcm")
        file.writeBytes(ByteArray(500) { 9 })
        PcmRingBuffer(file, 1000).use { buffer ->
            assertEquals(0L, buffer.bufferedBytes)
            assertEquals(0L, file.length())
        }
    }

    @Test(expected = IOException::class)
    fun `writing to a closed buffer fails`() {
        val buffer = newBuffer(capacity = 100)
        buffer.close()
        buffer.write(ByteArray(4))
    }

    @Test(expected = IOException::class)
    fun `snapshotting a closed buffer fails`() {
        val buffer = newBuffer(capacity = 100)
        buffer.write(ByteArray(4))
        buffer.close()
        buffer.snapshotToWav(tempFolder.newFile("closed.wav"))
    }

    @Test
    fun `closeAndDelete removes the buffer file`() {
        val file = File(tempFolder.root, "ring.pcm")
        val buffer = PcmRingBuffer(file, 100)
        buffer.write(ByteArray(10))
        buffer.closeAndDelete()
        assertFalse(file.exists())
    }
}
