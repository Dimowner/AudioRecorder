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

import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AAC_ADTS_MAX_PAYLOAD_SIZE
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.ADTS_HEADER_SIZE
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.MIN_AAC_FRAME_BYTES
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for the raw-AAC → ADTS wrapping step of [BrokenRecordRestorer].
 *
 * This step feeds the container rebuild and runs over the whole mdat payload of a broken
 * recording, which can be hundreds of megabytes — so besides producing correct ADTS framing
 * it must work as a stream and not load the file into memory.
 *
 * Also covers [BrokenRecordRestorer.averageAdtsFrameLength], which sizes the resulting stream
 * so the non-streaming mp4parser fallback is never entered on a file that would exhaust the heap.
 */
class BrokenRecordRestorerAacTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var restorer: BrokenRecordRestorer

    @Before
    fun setUp() {
        restorer = BrokenRecordRestorer()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a synthetic raw AAC-LC mono stream of [frameCount] frames, each [frameSize]
     * bytes. Byte 0 of every frame carries the SCE id_syn_ele (top-3-bits = 0b000);
     * all other bytes are filled with a value whose top-3-bits are 0b111 so they can
     * never be mistaken for a frame start.
     */
    private fun buildRawMonoAac(frameCount: Int, frameSize: Int): ByteArray {
        val data = ByteArray(frameCount * frameSize) { 0xFF.toByte() }
        for (frame in 0 until frameCount) {
            data[frame * frameSize] = 0x00
        }
        return data
    }

    /** Reads the `aac_frame_length` field (13 bits) of the ADTS header at [offset]. */
    private fun adtsFrameLength(bytes: ByteArray, offset: Int): Int {
        val b3 = bytes[offset + 3].toInt() and 0x03
        val b4 = bytes[offset + 4].toInt() and 0xFF
        val b5 = (bytes[offset + 5].toInt() and 0xFF) ushr 5
        return (b3 shl 11) or (b4 shl 3) or b5
    }

    /**
     * Walks the ADTS stream in [file] and returns the payload size of every frame,
     * asserting that each frame carries a valid sync word.
     */
    private fun readAdtsPayloadSizes(file: File): List<Int> {
        val bytes = file.readBytes()
        val sizes = mutableListOf<Int>()
        var pos = 0
        while (pos + ADTS_HEADER_SIZE <= bytes.size) {
            assertEquals("Missing ADTS sync at $pos", 0xFF, bytes[pos].toInt() and 0xFF)
            assertEquals("Missing ADTS sync at ${pos + 1}", 0xF0, bytes[pos + 1].toInt() and 0xF0)
            val frameLength = adtsFrameLength(bytes, pos)
            sizes.add(frameLength - ADTS_HEADER_SIZE)
            pos += frameLength
        }
        assertEquals("ADTS stream must end on a frame boundary", bytes.size, pos)
        return sizes
    }

    private fun wrap(raw: ByteArray, channelCount: Int = 1): Pair<Boolean, File> {
        val rawFile = tempFolder.newFile("raw_${System.nanoTime()}.aac")
        rawFile.writeBytes(raw)
        val outFile = tempFolder.newFile("adts_${System.nanoTime()}.aac")
        val result = restorer.wrapRawAacWithAdts(
            rawFile = rawFile,
            outputFile = outFile,
            sampleRate = 44100,
            channelCount = channelCount,
            bitrate = 128000,
        )
        return result to outFile
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `wrapRawAacWithAdts splits stream on detected frame boundaries`() {
        val frameSize = 400
        val frameCount = 25
        val (result, outFile) = wrap(buildRawMonoAac(frameCount, frameSize))

        assertTrue("Wrapping should succeed for a well-formed stream", result)
        val sizes = readAdtsPayloadSizes(outFile)
        assertEquals("Every synthetic frame should be detected", frameCount, sizes.size)
        assertTrue("Every frame should keep its original size", sizes.all { it == frameSize })
    }

    @Test
    fun `wrapRawAacWithAdts preserves the payload byte-for-byte`() {
        val raw = buildRawMonoAac(frameCount = 10, frameSize = 300)
        val (result, outFile) = wrap(raw)

        assertTrue(result)
        val wrapped = outFile.readBytes()
        val payload = ByteArray(raw.size)
        var src = 0
        var dst = 0
        while (src < wrapped.size) {
            val frameLength = adtsFrameLength(wrapped, src)
            val dataSize = frameLength - ADTS_HEADER_SIZE
            System.arraycopy(wrapped, src + ADTS_HEADER_SIZE, payload, dst, dataSize)
            src += frameLength
            dst += dataSize
        }
        assertEquals("All payload bytes must be written", raw.size, dst)
        assertTrue("Payload must survive wrapping unchanged", payload.contentEquals(raw))
    }

    @Test
    fun `wrapRawAacWithAdts caps frames that have no detectable boundary`() {
        // No byte other than the very first one can start a frame, so the wrapper has to
        // cut frames at the maximum payload the 13-bit ADTS length field can describe.
        val raw = ByteArray(AAC_ADTS_MAX_PAYLOAD_SIZE * 3) { 0xFF.toByte() }
        raw[0] = 0x00
        val (result, outFile) = wrap(raw)

        assertTrue(result)
        val sizes = readAdtsPayloadSizes(outFile)
        assertTrue(
            "No frame may exceed the ADTS payload limit, got ${sizes.maxOrNull()}",
            sizes.all { it <= AAC_ADTS_MAX_PAYLOAD_SIZE },
        )
        assertEquals("All bytes should be accounted for", raw.size, sizes.sum())
    }

    @Test
    fun `wrapRawAacWithAdts handles a stream larger than the read buffer`() {
        // ~4 MB — large enough to span many window refills and prove the wrapper streams
        // instead of holding the whole file in memory.
        val frameSize = 400
        val frameCount = 10_000
        val (result, outFile) = wrap(buildRawMonoAac(frameCount, frameSize))

        assertTrue(result)
        val sizes = readAdtsPayloadSizes(outFile)
        assertEquals(frameCount, sizes.size)
        assertEquals(frameCount * frameSize, sizes.sum())
    }

    @Test
    fun `wrapRawAacWithAdts writes trailing bytes that have no frame start`() {
        // The 120 trailing bytes contain no id_syn_ele, so they belong to the last frame
        // rather than forming a new one — but they must still reach the output.
        val frameSize = 400
        val raw = buildRawMonoAac(frameCount = 5, frameSize = frameSize) + ByteArray(120) { 0xFF.toByte() }
        val (result, outFile) = wrap(raw)

        assertTrue(result)
        val sizes = readAdtsPayloadSizes(outFile)
        assertEquals("Trailing bytes should extend the last frame", 5, sizes.size)
        assertEquals(frameSize + 120, sizes.last())
        assertEquals("No byte may be dropped", raw.size, sizes.sum())
    }

    @Test
    fun `wrapRawAacWithAdts uses stereo sync bits for two channels`() {
        // Stereo streams start each frame with CPE (0b001) instead of SCE (0b000).
        val frameSize = 400
        val frameCount = 8
        val raw = ByteArray(frameCount * frameSize) { 0xFF.toByte() }
        for (frame in 0 until frameCount) {
            raw[frame * frameSize] = 0x20 // top-3-bits = 0b001
        }
        val (result, outFile) = wrap(raw, channelCount = 2)

        assertTrue(result)
        assertEquals(frameCount, readAdtsPayloadSizes(outFile).size)
    }

    @Test
    fun `wrapRawAacWithAdts rejects a stream too short to contain frames`() {
        val (result, _) = wrap(ByteArray(MIN_AAC_FRAME_BYTES) { 0x00 })
        assertFalse("Streams shorter than two minimal frames cannot be wrapped", result)
    }

    @Test
    fun `wrapRawAacWithAdts rejects an empty file`() {
        val (result, _) = wrap(ByteArray(0))
        assertFalse("Empty input cannot be wrapped", result)
    }

    // -------------------------------------------------------------------------
    // averageAdtsFrameLength — sizing guard for the mp4parser fallback
    // -------------------------------------------------------------------------

    @Test
    fun `averageAdtsFrameLength measures uniform frames`() {
        val frameSize = 400
        val (result, outFile) = wrap(buildRawMonoAac(frameCount = 30, frameSize = frameSize))

        assertTrue(result)
        assertEquals(
            "Average should be the payload plus its ADTS header",
            frameSize + ADTS_HEADER_SIZE,
            restorer.averageAdtsFrameLength(outFile),
        )
    }

    @Test
    fun `averageAdtsFrameLength reports tiny frames produced by mis-detected boundaries`() {
        // A stream that looks like a frame start every MIN_AAC_FRAME_BYTES bytes is the
        // pathological case: it inflates the frame count and, with it, the heap an
        // mp4parser rebuild would need.
        val (result, outFile) = wrap(
            buildRawMonoAac(frameCount = 40, frameSize = MIN_AAC_FRAME_BYTES)
        )

        assertTrue(result)
        val average = restorer.averageAdtsFrameLength(outFile)!!
        assertEquals(MIN_AAC_FRAME_BYTES + ADTS_HEADER_SIZE, average)
        assertTrue(
            "A tiny average must imply a frame count far above the real one",
            outFile.length() / average > 30,
        )
    }

    @Test
    fun `averageAdtsFrameLength returns null when the stream has no sync word`() {
        val notAdts = tempFolder.newFile("not_adts_${System.nanoTime()}.aac")
        notAdts.writeBytes(ByteArray(1024) { 0x00 })

        assertNull("A stream without a sync word cannot be measured", restorer.averageAdtsFrameLength(notAdts))
    }

    @Test
    fun `averageAdtsFrameLength returns null for an empty file`() {
        val empty = tempFolder.newFile("empty_${System.nanoTime()}.aac")

        assertNull("An empty file cannot be measured", restorer.averageAdtsFrameLength(empty))
    }
}
