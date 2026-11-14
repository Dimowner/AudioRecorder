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

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Size of one copy step in [PcmRingBuffer.snapshotToWav]; the writer is blocked for at most one. */
private const val SNAPSHOT_CHUNK_BYTES = 64 * 1024

/** Size of the canonical PCM WAV header written by [createWavHeader]. */
internal const val WAV_HEADER_SIZE = 44

/**
 * A circular PCM store backed by a file: once [capacity] bytes have been written, every new byte
 * replaces the oldest one, so the file always holds the most recent [capacity] bytes of audio.
 *
 * Positions are tracked as absolute byte counts since the buffer was created; the byte at absolute
 * position `p` lives at file offset `p % capacity`. Only whole frames are ever committed, and the
 * capacity is a whole number of frames, so every committed position - and therefore every
 * snapshot - starts and ends on a frame boundary. A partial frame handed to [write] is held back
 * until the rest of it arrives.
 *
 * Threading: one writer thread calls [write]; any number of threads may call [snapshotToWav]
 * concurrently with it. Both take [lock] around each file access, but a snapshot holds it for one
 * [SNAPSHOT_CHUNK_BYTES] chunk at a time, never for the whole copy, so the writer keeps up.
 *
 * Pure JVM code on purpose - no Android types - so it is unit-tested on real temporary files.
 */
class PcmRingBuffer(
    private val file: File,
    capacityBytes: Long,
    private val frameSize: Int = ReplayBufferConfig.FRAME_SIZE,
) : Closeable {

    init {
        require(frameSize > 0) { "frameSize must be positive: $frameSize" }
        require(capacityBytes >= frameSize) {
            "capacity must hold at least one frame: $capacityBytes < $frameSize"
        }
    }

    /** Usable size in bytes: the requested capacity rounded down to whole frames. */
    val capacity: Long = capacityBytes - capacityBytes % frameSize

    private val lock = Any()

    /** A fresh buffer never inherits audio: any stale file left by a killed process is truncated. */
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0) }

    /** Absolute count of committed bytes; always a multiple of [frameSize]. Guarded by [lock]. */
    private var totalWritten = 0L

    /** Bytes of an incomplete trailing frame, waiting for the rest of it. Guarded by [lock]. */
    private val pendingFrame = ByteArray(frameSize)
    private var pendingSize = 0

    @Volatile private var closed = false

    /** Committed bytes currently held: grows until the first wrap, then stays at [capacity]. */
    val bufferedBytes: Long
        get() = synchronized(lock) { minOf(totalWritten, capacity) }

    /** Total committed bytes since creation, including those already overwritten. */
    val totalBytesWritten: Long
        get() = synchronized(lock) { totalWritten }

    /** True once the oldest audio has started being overwritten. */
    val hasWrapped: Boolean
        get() = synchronized(lock) { totalWritten > capacity }

    /**
     * Appends PCM bytes, overwriting the oldest audio once the buffer is full.
     *
     * @throws IOException when the backing file cannot be written, or the buffer is closed
     */
    @Throws(IOException::class)
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Range [$offset, ${offset + length}) is outside an array of ${data.size}"
        }
        synchronized(lock) {
            ensureOpen()
            var off = offset
            var len = length
            if (pendingSize > 0) {
                val take = minOf(frameSize - pendingSize, len)
                System.arraycopy(data, off, pendingFrame, pendingSize, take)
                pendingSize += take
                off += take
                len -= take
                if (pendingSize < frameSize) return
                commitFrames(pendingFrame, 0, frameSize)
                pendingSize = 0
            }
            val whole = len - len % frameSize
            if (whole > 0) commitFrames(data, off, whole)
            val rest = len - whole
            if (rest > 0) {
                System.arraycopy(data, off + whole, pendingFrame, 0, rest)
                pendingSize = rest
            }
        }
    }

    /**
     * Drops an incomplete trailing frame. Called when capture stops, so that the first bytes of
     * the next capture session are not glued onto half a sample of the previous one.
     */
    fun discardPartialFrame() {
        synchronized(lock) { pendingSize = 0 }
    }

    /** Writes whole frames at the head. Caller holds [lock] and passes a frame-aligned length. */
    private fun commitFrames(data: ByteArray, offset: Int, length: Int) {
        var off = offset
        var len = length
        if (len > capacity) {
            // Only the newest `capacity` bytes can survive; skip the rest but still count them,
            // so absolute positions stay consistent with what a reader computed earlier.
            val skip = (len - capacity).toInt()
            totalWritten += skip
            off += skip
            len -= skip
        }
        while (len > 0) {
            val filePos = totalWritten % capacity
            val n = minOf(len.toLong(), capacity - filePos).toInt()
            raf.seek(filePos)
            raf.write(data, off, n)
            totalWritten += n
            off += n
            len -= n
        }
    }

    /**
     * Copies the buffered audio, oldest to newest, into [target] as a PCM WAV file, while the
     * writer keeps running.
     *
     * The snapshot ends at the head as it was when the call started; audio written afterwards
     * is not included. If the writer laps the copy before it reaches the oldest bytes - possible
     * only if the copy is far slower than real time - the overwritten bytes are skipped rather
     * than copied out of order, so the result always plays in order, just starting later.
     *
     * @return number of PCM bytes written after the header; 0 when nothing was buffered
     * @throws IOException when either file cannot be read or written, or the buffer is closed
     */
    @Throws(IOException::class)
    fun snapshotToWav(
        target: File,
        sampleRate: Int = ReplayBufferConfig.SAMPLE_RATE,
        channels: Int = ReplayBufferConfig.CHANNEL_COUNT,
    ): Long {
        val end = synchronized(lock) {
            ensureOpen()
            totalWritten
        }
        var pos = maxOf(0L, end - capacity)
        val chunk = ByteArray(SNAPSHOT_CHUNK_BYTES - SNAPSHOT_CHUNK_BYTES % frameSize)
        var written = 0L
        RandomAccessFile(target, "rw").use { out ->
            out.setLength(0)
            // Placeholder header; sizes are only known once the copy is done.
            out.write(ByteArray(WAV_HEADER_SIZE))
            while (pos < end) {
                val n = synchronized(lock) {
                    ensureOpen()
                    val oldest = maxOf(0L, totalWritten - capacity)
                    if (pos < oldest) pos = oldest
                    if (pos >= end) return@synchronized 0
                    val filePos = pos % capacity
                    val n = minOf(chunk.size.toLong(), end - pos, capacity - filePos).toInt()
                    raf.seek(filePos)
                    raf.readFully(chunk, 0, n)
                    n
                }
                if (n == 0) break
                out.write(chunk, 0, n)
                pos += n
                written += n
            }
            val byteRate = sampleRate.toLong() * channels * ReplayBufferConfig.BITS_PER_SAMPLE / 8
            out.seek(0)
            out.write(
                createWavHeader(
                    totalAudioLen = written,
                    totalDataLen = written + WAV_HEADER_SIZE - 8,
                    sampleRate = sampleRate,
                    channels = channels,
                    byteRate = byteRate,
                )
            )
        }
        return written
    }

    private fun ensureOpen() {
        if (closed) throw IOException("Ring buffer is closed: ${file.absolutePath}")
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            raf.close()
        }
    }

    /** Closes the buffer and removes its file, discarding the audio it held. */
    fun closeAndDelete() {
        close()
        if (file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }
}
