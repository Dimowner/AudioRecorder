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

import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.ADTS_HEADER_SIZE
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [BrokenRecordRestorer.averageAdtsFrameLength], which sizes an ADTS stream so the
 * non-streaming mp4parser fallback is never entered on a file that would exhaust the heap.
 *
 * The restorer's own AAC paths need `MediaMuxer` and `MediaExtractor`, so they are covered by
 * instrumentation rather than here; the sidecar those paths depend on is covered by
 * [AacFrameIndexTest].
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

    /** Builds a 7-byte ADTS header (44.1 kHz, mono, AAC-LC) describing [frameLength] total bytes. */
    private fun adtsHeader(frameLength: Int): ByteArray {
        val samplingFreqIndex = 4 // 44100 Hz
        val channelConfig = 1
        val profile = 1 // AAC-LC: audioObjectType 2, stored as objectType - 1
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((profile shl 6) or (samplingFreqIndex shl 2) or (channelConfig ushr 2)).toByte(),
            (((channelConfig and 0x3) shl 6) or ((frameLength ushr 11) and 0x3)).toByte(),
            ((frameLength ushr 3) and 0xFF).toByte(),
            (((frameLength and 0x7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /** Writes an ADTS stream of [frameCount] frames, each carrying [payloadSize] bytes of audio. */
    private fun buildAdtsStream(frameCount: Int, payloadSize: Int): File {
        val file = tempFolder.newFile("adts_${System.nanoTime()}.aac")
        file.outputStream().buffered().use { out ->
            repeat(frameCount) {
                out.write(adtsHeader(payloadSize + ADTS_HEADER_SIZE))
                out.write(ByteArray(payloadSize) { 0x42 })
            }
        }
        return file
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `averageAdtsFrameLength measures uniform frames`() {
        val payloadSize = 400
        val stream = buildAdtsStream(frameCount = 30, payloadSize = payloadSize)

        assertEquals(
            "Average should be the payload plus its ADTS header",
            payloadSize + ADTS_HEADER_SIZE,
            restorer.averageAdtsFrameLength(stream),
        )
    }

    @Test
    fun `averageAdtsFrameLength reports a stream of tiny frames`() {
        // A stream of very short frames is the pathological case for the mp4parser fallback: it
        // inflates the frame count and, with it, the heap the rebuild would need.
        val stream = buildAdtsStream(frameCount = 40, payloadSize = 25)

        val average = restorer.averageAdtsFrameLength(stream)!!
        assertEquals(25 + ADTS_HEADER_SIZE, average)
        assertTrue(
            "A tiny average must imply a frame count far above what a real recording produces",
            stream.length() / average > 30,
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
