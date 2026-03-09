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

import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_FT_NO_DATA
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_NB_MAGIC
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_NB_MAX_SPEECH_FT
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_NB_SAMPLE_RATE
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_WB_MAGIC
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_WB_MAX_SPEECH_FT
import com.dimowner.audiorecorder.v2.audio.BrokenRecordRestorer.Companion.AMR_SCAN_LIMIT
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * Unit tests for 3GP/AMR broken-record restoration in [BrokenRecordRestorer].
 *
 * All tests operate on real temporary files and run on the JVM without an
 * emulator / device. Methods that exercise [android.media.MediaExtractor] or
 * [android.media.MediaMuxer] (e.g. the full [BrokenRecordRestorer.restoreFile] pipeline)
 * will fail at the MediaExtractor step on JVM, so those tests validate the
 * AMR-extraction layer (buildAmrFile / findFirstAmrFrame) directly rather than
 * the full re-mux pipeline.
 */
class BrokenRecordRestorer3gpTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var restorer: BrokenRecordRestorer

    @Before
    fun setUp() {
        restorer = BrokenRecordRestorer()
    }

    // -------------------------------------------------------------------------
    // Helpers – synthetic AMR bitstreams
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal synthetic AMR-NB TOC byte for frame-type [ft].
     *
     * Layout (ISO 26904):  `F(1) | FT(4) | Q(1) | P(2)`
     * For a single-frame file the F-bit (continuation) must be 0,
     * Q = 1 (good frame), P = 0 (padding).
     *   → byte = `0 | FT(4) | 1 | 00`  = `FT << 3 | 0x04`
     */
    private fun amrNbTocByte(ft: Int): Byte = ((ft shl 3) or 0x04).toByte()

    /**
     * Payload sizes (bytes) for each AMR-NB frame type (FT 0–7) as per the codec spec.
     * These are the raw frame sizes WITHOUT any TOC byte.
     *   FT 0 = 12, 1 = 13, 2 = 15, 3 = 17, 4 = 19, 5 = 20, 6 = 26, 7 = 31
     */
    private val amrNbPayloadSizes = intArrayOf(12, 13, 15, 17, 19, 20, 26, 31)

    /**
     * Payload sizes (bytes) for each AMR-WB frame type (FT 0–8).
     *   FT 0 = 17, 1 = 23, 2 = 32, 3 = 36, 4 = 40, 5 = 46, 6 = 50, 7 = 58, 8 = 60
     */
    private val amrWbPayloadSizes = intArrayOf(17, 23, 32, 36, 40, 46, 50, 58, 60)

    /**
     * Creates a byte array representing [frameCount] valid AMR-NB frames of type [ft]
     * (no magic header, no ADTS wrapping — raw mdat payload).
     */
    private fun buildRawAmrNbFrames(frameCount: Int = 5, ft: Int = 4): ByteArray {
        val payloadSize = amrNbPayloadSizes[ft]
        val buf = mutableListOf<Byte>()
        repeat(frameCount) {
            buf.add(amrNbTocByte(ft))
            // Payload: repeated 0x55 bytes (don't have top-bit set so they won't
            // accidentally look like AMR TOC bytes with F-bit set)
            repeat(payloadSize) { buf.add(0x55) }
        }
        return buf.toByteArray()
    }

    /**
     * Creates a byte array representing [frameCount] valid AMR-WB frames of type [ft].
     */
    private fun buildRawAmrWbFrames(frameCount: Int = 5, ft: Int = 4): ByteArray {
        val payloadSize = amrWbPayloadSizes[ft]
        val buf = mutableListOf<Byte>()
        repeat(frameCount) {
            // AMR-WB TOC same layout, same calculation
            buf.add(amrNbTocByte(ft))
            repeat(payloadSize) { buf.add(0x55) }
        }
        return buf.toByteArray()
    }

    /**
     * Writes a minimal broken 3GP file: a valid-looking ftyp atom followed by an mdat
     * atom whose payload is [mdatPayload].  The moov atom is absent (simulating an
     * interrupted recording).
     *
     * Atom layout:
     *   ftyp  (20 bytes): size(4) + "ftyp"(4) + "3gp4"(4) + version(4) + "3gp4"(4)
     *   mdat  (8 + payload bytes): size(4) + "mdat"(4) + payload
     */
    private fun create3gpWithMdatPayload(mdatPayload: ByteArray, fileName: String = "broken.3gp"): File {
        val file = tempFolder.newFile(fileName)
        FileOutputStream(file).use { fos ->
            // ftyp atom (20 bytes)
            val ftypSize = 20
            fos.write(byteArrayOf(
                0, 0, 0, ftypSize.toByte(),     // size = 20
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), // "ftyp"
                '3'.code.toByte(), 'g'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), // brand "3gp4"
                0, 0, 0, 0,                      // version
                '3'.code.toByte(), 'g'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), // compat "3gp4"
            ))
            // mdat atom
            val mdatSize = 8 + mdatPayload.size
            fos.write(byteArrayOf(
                (mdatSize ushr 24).toByte(),
                (mdatSize ushr 16 and 0xFF).toByte(),
                (mdatSize ushr 8 and 0xFF).toByte(),
                (mdatSize and 0xFF).toByte(),
                'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(),
            ))
            fos.write(mdatPayload)
        }
        return file
    }

    // -------------------------------------------------------------------------
    // Tests for findFirstAmrFrame
    // -------------------------------------------------------------------------

    @Test
    fun `findFirstAmrFrame returns 0 when first byte is valid AMR-NB TOC`() {
        val data = buildRawAmrNbFrames(frameCount = 3)
        val idx = restorer.findFirstAmrFrame(data, isWb = false)
        assertEquals("Should find frame at index 0", 0, idx)
    }

    @Test
    fun `findFirstAmrFrame returns 0 when first byte is valid AMR-WB TOC`() {
        val data = buildRawAmrWbFrames(frameCount = 3)
        val idx = restorer.findFirstAmrFrame(data, isWb = true)
        assertEquals("Should find frame at index 0", 0, idx)
    }

    @Test
    fun `findFirstAmrFrame skips leading invalid bytes`() {
        // Prefix 4 bytes with top-bit set (invalid for AMR TOC) then valid frame
        val prefix = ByteArray(4) { 0xFF.toByte() }
        val frames = buildRawAmrNbFrames(frameCount = 2)
        val data = prefix + frames
        val idx = restorer.findFirstAmrFrame(data, isWb = false)
        assertEquals("Should skip 4 invalid bytes", 4, idx)
    }

    @Test
    fun `findFirstAmrFrame returns -1 for empty array`() {
        val idx = restorer.findFirstAmrFrame(ByteArray(0), isWb = false)
        assertEquals(-1, idx)
    }

    @Test
    fun `findFirstAmrFrame returns -1 when all bytes have top-bit set`() {
        val data = ByteArray(AMR_SCAN_LIMIT) { 0xFF.toByte() }
        val idx = restorer.findFirstAmrFrame(data, isWb = false)
        assertEquals(-1, idx)
    }

    @Test
    fun `findFirstAmrFrame recognises FT_NO_DATA (15) as valid`() {
        // TOC byte for FT=15 (NO_DATA), F=0, Q=1 → (15 shl 3) or 4 = 0x7C = 124
        val tocByte = ((AMR_FT_NO_DATA shl 3) or 0x04).toByte()
        val data = byteArrayOf(tocByte) + ByteArray(20) { 0x00 }
        val idx = restorer.findFirstAmrFrame(data, isWb = false)
        assertEquals("FT_NO_DATA should be accepted", 0, idx)
    }

    @Test
    fun `findFirstAmrFrame does not accept FT above NB max for NB mode`() {
        // FT = AMR_NB_MAX_SPEECH_FT + 1 = 8, not NO_DATA → should be rejected for NB
        // But ft=8 is valid for WB, so only check NB mode
        val ft = AMR_NB_MAX_SPEECH_FT + 1
        val tocByte = ((ft shl 3) or 0x04).toByte()
        // Only this byte in the first AMR_SCAN_LIMIT bytes (rest are high-bit-set)
        val data = byteArrayOf(tocByte) + ByteArray(AMR_SCAN_LIMIT - 1) { 0xFF.toByte() }
        // FT=8 is NOT a valid NB speech frame and NOT NO_DATA(15), so should not be found in NB mode
        // (It would be found in WB mode, but not NB)
        val nbIdx = restorer.findFirstAmrFrame(data, isWb = false)
        val wbIdx = restorer.findFirstAmrFrame(data, isWb = true)
        assertEquals("FT=8 should NOT be accepted in NB mode (reserved)", -1, nbIdx)
        assertEquals("FT=8 SHOULD be accepted in WB mode (speech frame)", 0, wbIdx)
    }

    @Test
    fun `findFirstAmrFrame only scans up to AMR_SCAN_LIMIT bytes`() {
        // Place a valid NB TOC byte just BEYOND the scan limit → should NOT be found
        val data = ByteArray(AMR_SCAN_LIMIT + 10) { 0xFF.toByte() }
        data[AMR_SCAN_LIMIT + 1] = amrNbTocByte(4) // valid, but past the limit
        val idx = restorer.findFirstAmrFrame(data, isWb = false)
        assertEquals("Should not scan beyond AMR_SCAN_LIMIT", -1, idx)
    }

    // -------------------------------------------------------------------------
    // Tests for buildAmrFile
    // -------------------------------------------------------------------------

    @Test
    fun `buildAmrFile for AMR-NB prepends NB magic header`() {
        val rawFrames = buildRawAmrNbFrames(frameCount = 5)
        val rawFile = tempFolder.newFile("raw_nb.amr")
        rawFile.writeBytes(rawFrames)

        val outFile = tempFolder.newFile("out_nb.amr")
        val result = restorer.buildAmrFile(rawFile, outFile, isWb = false)

        assertTrue("buildAmrFile should succeed for valid NB frames", result)
        val bytes = outFile.readBytes()
        val magic = AMR_NB_MAGIC
        assertTrue("Output must start with AMR-NB magic", bytes.take(magic.size).toByteArray().contentEquals(magic))
    }

    @Test
    fun `buildAmrFile for AMR-WB prepends WB magic header`() {
        val rawFrames = buildRawAmrWbFrames(frameCount = 5)
        val rawFile = tempFolder.newFile("raw_wb.amr")
        rawFile.writeBytes(rawFrames)

        val outFile = tempFolder.newFile("out_wb.amr")
        val result = restorer.buildAmrFile(rawFile, outFile, isWb = true)

        assertTrue("buildAmrFile should succeed for valid WB frames", result)
        val magic = AMR_WB_MAGIC
        val bytes = outFile.readBytes()
        assertTrue("Output must start with AMR-WB magic", bytes.take(magic.size).toByteArray().contentEquals(magic))
    }

    @Test
    fun `buildAmrFile returns false for empty raw file`() {
        val rawFile = tempFolder.newFile("empty_raw.amr")
        val outFile = tempFolder.newFile("out_empty.amr")
        val result = restorer.buildAmrFile(rawFile, outFile, isWb = false)
        assertFalse("Should return false for empty input", result)
    }

    @Test
    fun `buildAmrFile returns false when no valid AMR frame found`() {
        // All bytes with top-bit set → no valid AMR TOC byte
        val rawFile = tempFolder.newFile("garbage.amr")
        rawFile.writeBytes(ByteArray(AMR_SCAN_LIMIT) { 0xFF.toByte() })
        val outFile = tempFolder.newFile("out_garbage.amr")
        val result = restorer.buildAmrFile(rawFile, outFile, isWb = false)
        assertFalse("Should return false when no frame can be detected", result)
    }

    @Test
    fun `buildAmrFile skips container prefix bytes before first AMR frame`() {
        // Simulate a small container remnant (e.g. 8 bytes) before the AMR frames
        val prefix = ByteArray(8) { 0xFF.toByte() }
        val rawFrames = buildRawAmrNbFrames(frameCount = 3)
        val rawFile = tempFolder.newFile("prefixed_raw.amr")
        rawFile.writeBytes(prefix + rawFrames)

        val outFile = tempFolder.newFile("out_prefixed.amr")
        val result = restorer.buildAmrFile(rawFile, outFile, isWb = false)

        assertTrue("Should succeed even with leading container bytes", result)
        val bytes = outFile.readBytes()
        val magic = AMR_NB_MAGIC
        // Output = magic + rawFrames (container prefix stripped)
        val expectedSize = magic.size + rawFrames.size
        assertEquals("Output size should equal magic + AMR frames (no prefix)", expectedSize.toLong(), outFile.length())
        assertTrue("Output must start with AMR-NB magic", bytes.take(magic.size).toByteArray().contentEquals(magic))
    }

    @Test
    fun `buildAmrFile output size equals magic plus payload after first valid frame`() {
        val rawFrames = buildRawAmrNbFrames(frameCount = 10)
        val rawFile = tempFolder.newFile("frames10.amr")
        rawFile.writeBytes(rawFrames)
        val outFile = tempFolder.newFile("out_frames10.amr")

        restorer.buildAmrFile(rawFile, outFile, isWb = false)

        val expectedSize = AMR_NB_MAGIC.size.toLong() + rawFrames.size.toLong()
        assertEquals(expectedSize, outFile.length())
    }

    // -------------------------------------------------------------------------
    // Tests for the full restoreFile path (JVM — MediaExtractor unavailable)
    // -------------------------------------------------------------------------

    @Test
    fun `restoreFile returns Failed for non-existent 3GP file`() {
        val result = restorer.restoreFile(
            filePath = tempFolder.root.absolutePath + "/missing.3gp",
            sampleRate = AMR_NB_SAMPLE_RATE,
            channelCount = 1,
        )
        assertTrue("Expected Failed for missing file", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    @Test
    fun `restoreFile returns Failed for empty 3GP file`() {
        val file = tempFolder.newFile("empty.3gp")
        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = AMR_NB_SAMPLE_RATE,
            channelCount = 1,
        )
        assertTrue("Expected Failed for empty file", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    // -------------------------------------------------------------------------
    // Tests for AMR constants correctness
    // -------------------------------------------------------------------------

    @Test
    fun `AMR_NB_SAMPLE_RATE is 8000`() {
        assertEquals(8_000, AMR_NB_SAMPLE_RATE)
    }

    @Test
    fun `AMR_NB_MAGIC equals hash-bang-AMR-newline`() {
        assertNotNull(AMR_NB_MAGIC)
        assertEquals("#!AMR\n", AMR_NB_MAGIC.toString(Charsets.US_ASCII))
    }

    @Test
    fun `AMR_WB_MAGIC equals hash-bang-AMR-WB-newline`() {
        assertNotNull(AMR_WB_MAGIC)
        assertEquals("#!AMR-WB\n", AMR_WB_MAGIC.toString(Charsets.US_ASCII))
    }

    @Test
    fun `AMR_NB_MAX_SPEECH_FT is 7`() {
        assertEquals(7, AMR_NB_MAX_SPEECH_FT)
    }

    @Test
    fun `AMR_WB_MAX_SPEECH_FT is 8`() {
        assertEquals(8, AMR_WB_MAX_SPEECH_FT)
    }

    @Test
    fun `AMR_FT_NO_DATA is 15`() {
        assertEquals(15, AMR_FT_NO_DATA)
    }
}

