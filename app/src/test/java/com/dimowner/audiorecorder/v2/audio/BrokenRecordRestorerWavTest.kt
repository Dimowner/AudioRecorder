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

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * Unit tests for WAV broken-record restoration in [BrokenRecordRestorer].
 *
 * All tests operate on real temporary files (no Android framework needed) so they
 * run on the JVM without an emulator / device.
 */
class BrokenRecordRestorerWavTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var restorer: BrokenRecordRestorer

    private val sampleRate   = 44100
    private val channelCount = 1                    // mono
    private val bitsPerSample = 16
    private val byteRate     = (sampleRate * channelCount * bitsPerSample / 8).toLong()

    @Before
    fun setUp() {
        restorer = BrokenRecordRestorer()
    }

    @After
    fun tearDown() {
        // TemporaryFolder cleans up automatically
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Creates a WAV file that mimics a WavRecorderV2 recording interrupted before
     * stopRecording() wrote the header: 44 zero bytes followed by [pcmFrames] of
     * silent PCM-16LE data.
     */
    private fun createBrokenWavFile(
        pcmFrames: Int = 44100,   // 1 second of mono 44100 Hz silence
        fileName: String = "broken.wav",
    ): File {
        val file = tempFolder.newFile(fileName)
        RandomAccessFile(file, "rw").use { raf ->
            // 44-byte all-zero placeholder header
            raf.write(ByteArray(44))
            // Silent PCM-16LE: 2 bytes per sample * frames * channels
            val pcmBytes = ByteArray(pcmFrames * channelCount * (bitsPerSample / 8))
            raf.write(pcmBytes)
        }
        return file
    }

    /**
     * Creates a WAV file with a valid RIFF header already written (as WavRecorderV2
     * does on a clean stop), to test the AlreadyReadable path.
     *
     * NOTE: Because these unit tests run on the JVM (no MediaExtractor), the
     * tryReadWithExtractor() inside BrokenRecordRestorer will always return null.
     * Therefore a file with a valid RIFF header but still unreadable by
     * MediaExtractor will be re-written, and the restorer returns Success via
     * the calculated-duration fallback — which is still an acceptable outcome.
     */
    private fun createValidWavFile(
        pcmFrames: Int = 44100,
        fileName: String = "valid.wav",
    ): File {
        val file = tempFolder.newFile(fileName)
        val pcmBytes = ByteArray(pcmFrames * channelCount * (bitsPerSample / 8))
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen  = totalAudioLen + 36
        RandomAccessFile(file, "rw").use { raf ->
            val header = createWavHeader(
                totalAudioLen = totalAudioLen,
                totalDataLen = totalDataLen,
                sampleRate = sampleRate,
                channels = channelCount,
                byteRate = byteRate,
            )
            raf.write(header)
            raf.write(pcmBytes)
        }
        return file
    }

    /** Reads the first 4 bytes of [file] and checks they match "RIFF". */
    private fun hasRiffMagic(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            return magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                   magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()
        }
    }

    /** Reads the WAV data sub-chunk size from bytes 40-43 (little-endian). */
    private fun readDataChunkSize(file: File): Long {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(40)
            val b = ByteArray(4)
            raf.readFully(b)
            return (b[0].toLong() and 0xFF) or
                   ((b[1].toLong() and 0xFF) shl 8) or
                   ((b[2].toLong() and 0xFF) shl 16) or
                   ((b[3].toLong() and 0xFF) shl 24)
        }
    }

    /** Reads the RIFF chunk size from bytes 4-7 (little-endian). */
    private fun readRiffChunkSize(file: File): Long {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            val b = ByteArray(4)
            raf.readFully(b)
            return (b[0].toLong() and 0xFF) or
                   ((b[1].toLong() and 0xFF) shl 8) or
                   ((b[2].toLong() and 0xFF) shl 16) or
                   ((b[3].toLong() and 0xFF) shl 24)
        }
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `restore broken WAV with zero header returns Success`() {
        val pcmFrames = 44100 // 1 second mono 44100 Hz
        val file = createBrokenWavFile(pcmFrames)

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        assertTrue("Expected Success but got $result", result is BrokenRecordRestorer.RestoreResult.Success)
    }

    @Test
    fun `restored WAV file has correct RIFF magic bytes`() {
        val file = createBrokenWavFile()

        restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        assertTrue("RIFF magic not found after restore", hasRiffMagic(file))
    }

    @Test
    fun `restored WAV data chunk size equals file size minus 44`() {
        val pcmFrames = 22050 // 0.5 s mono 44100 Hz
        val file = createBrokenWavFile(pcmFrames)
        val expectedPcmBytes = (pcmFrames * channelCount * (bitsPerSample / 8)).toLong()

        restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        val dataChunkSize = readDataChunkSize(file)
        assertEquals(expectedPcmBytes, dataChunkSize)
    }

    @Test
    fun `restored WAV RIFF chunk size equals file size minus 8`() {
        val pcmFrames = 8000 // small file
        val file = createBrokenWavFile(pcmFrames)
        val fileSize = file.length()

        restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        val riffChunkSize = readRiffChunkSize(file)
        assertEquals(fileSize - 8, riffChunkSize)
    }

    @Test
    fun `restored WAV duration is calculated correctly from PCM size`() {
        val pcmFrames = 44100 // 1 second
        val file = createBrokenWavFile(pcmFrames)

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        ) as BrokenRecordRestorer.RestoreResult.Success

        // Expected duration: 1 second = 1_000_000 µs
        val expectedDurationMicros = 1_000_000L
        assertEquals(expectedDurationMicros, result.durationMicros)
    }

    @Test
    fun `restore broken stereo WAV returns Success`() {
        val stereoChannels = 2
        val pcmFrames = 44100
        val file = tempFolder.newFile("broken_stereo.wav")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteArray(44)) // zero header
            raf.write(ByteArray(pcmFrames * stereoChannels * (bitsPerSample / 8)))
        }

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = stereoChannels,
        )

        assertTrue("Expected Success but got $result", result is BrokenRecordRestorer.RestoreResult.Success)
        assertTrue("RIFF magic not found after stereo restore", hasRiffMagic(file))
    }

    @Test
    fun `restore fails when file is too small (header only)`() {
        val file = tempFolder.newFile("tiny.wav")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteArray(44)) // only the header placeholder, no PCM
        }

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        assertTrue("Expected Failed but got $result", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    @Test
    fun `restore fails when sampleRate is zero`() {
        val file = createBrokenWavFile()

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = 0,
            channelCount = channelCount,
        )

        assertTrue("Expected Failed but got $result", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    @Test
    fun `restore fails when channelCount is zero`() {
        val file = createBrokenWavFile()

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = 0,
        )

        assertTrue("Expected Failed but got $result", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    @Test
    fun `restore fails for non-existent file`() {
        val result = restorer.restoreFile(
            filePath = tempFolder.root.absolutePath + "/does_not_exist.wav",
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        assertTrue("Expected Failed but got $result", result is BrokenRecordRestorer.RestoreResult.Failed)
    }

    @Test
    fun `restore WAV file with already valid header rewrites header and returns Success`() {
        // On JVM, MediaExtractor is unavailable so tryReadWithExtractor always returns null.
        // The restorer falls back to rewriting and using calculated duration.
        val file = createValidWavFile()

        val result = restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            channelCount = channelCount,
        )

        // We expect either AlreadyReadable (on Android with MediaExtractor) or
        // Success (on JVM where MediaExtractor is unavailable — calculated duration path).
        assertTrue(
            "Expected AlreadyReadable or Success but got $result",
            result is BrokenRecordRestorer.RestoreResult.AlreadyReadable ||
            result is BrokenRecordRestorer.RestoreResult.Success
        )
        assertTrue("RIFF magic should still be present", hasRiffMagic(file))
    }

    @Test
    fun `restore WAV with different sample rates produces correct header`() {
        val rate8k = 8000
        val file = tempFolder.newFile("broken_8k.wav")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteArray(44))
            raf.write(ByteArray(rate8k * 1 * 2)) // 1 second mono 8000 Hz
        }

        restorer.restoreFile(
            filePath = file.absolutePath,
            sampleRate = rate8k,
            channelCount = 1,
        )

        assertTrue(hasRiffMagic(file))
        // SampleRate bytes at offset 24-27 (little-endian)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(24)
            val b = ByteArray(4)
            raf.readFully(b)
            val storedRate = (b[0].toInt() and 0xFF) or
                             ((b[1].toInt() and 0xFF) shl 8) or
                             ((b[2].toInt() and 0xFF) shl 16) or
                             ((b[3].toInt() and 0xFF) shl 24)
            assertEquals(rate8k, storedRate)
        }
    }
}


