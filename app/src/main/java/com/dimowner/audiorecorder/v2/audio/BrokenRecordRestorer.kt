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

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import org.mp4parser.muxer.FileDataSourceImpl
import org.mp4parser.muxer.Movie
import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.tracks.AACTrackImpl
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores broken audio recording files that were interrupted (e.g., by a device reboot)
 * before MediaRecorder.stop() was called.
 *
 * When MediaRecorder is interrupted without proper stop, the MPEG-4/3GP container file
 * may be missing its 'moov' atom, making it unplayable. This class attempts to recover
 * such files using multiple strategies:
 * 1. Try MediaExtractor (works if the OS partially recovered the file)
 * 2. Try re-muxing with MediaExtractor + MediaMuxer
 * 3. Fallback to mp4parser to extract raw AAC frames and build a new valid container
 */
@Singleton
class BrokenRecordRestorer @Inject constructor() {

    /**
     * Attempts to restore a broken audio recording file.
     *
     * Strategy:
     * 1. First, try to read the file with MediaExtractor. On many Android versions,
     *    MediaExtractor can read partially-written MPEG-4 files.
     * 2. If MediaExtractor can read it, the file is already playable — return success.
     * 3. If MediaExtractor cannot read it, attempt to re-mux with MediaExtractor + MediaMuxer.
     * 4. If re-mux also fails (MediaExtractor can't parse tracks), fallback to mp4parser
     *    to extract raw AAC data from the mdat atom and build a new valid container.
     *
     * @param filePath Path to the broken audio file
     * @param sampleRate Sample rate from the database record (used by mp4parser fallback)
     * @param channelCount Channel count from the database record (used by mp4parser fallback)
     * @return RestoreResult indicating success or failure
     */
    fun restoreFile(
        filePath: String,
        sampleRate: Int = 0,
        channelCount: Int = 0,
        bitrate: Int = 0,
    ): RestoreResult {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return RestoreResult.Failed("File does not exist or is empty")
        }

        // Step 1: Try reading the file directly with MediaExtractor
        val directReadResult = tryReadWithExtractor(filePath)
        if (directReadResult != null) {
            Timber.d("File is already readable by MediaExtractor: $filePath, duration: ${directReadResult}μs")
            return RestoreResult.AlreadyReadable(directReadResult)
        }

        // Step 2: Try to re-mux the file with MediaExtractor
        Timber.d("File is not directly readable, attempting re-mux: $filePath")
        val remuxResult = tryRemuxFile(file)
        if (remuxResult !is RestoreResult.Failed) {
            return remuxResult
        }

        // Step 3: Fallback to mp4parser for raw AAC extraction
        Timber.d("Re-mux failed, attempting mp4parser fallback: $filePath")
        return tryRestoreWithMp4Parser(file, sampleRate, channelCount, bitrate)
    }

    /**
     * Tries to read the audio file with MediaExtractor.
     * @return duration in microseconds if readable, null if not readable
     */
    private fun tryReadWithExtractor(filePath: String): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(filePath)
            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                null
            } else {
                var audioDuration: Long? = null
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioDuration = try {
                            format.getLong(MediaFormat.KEY_DURATION)
                        } catch (e: Exception) {
                            // Duration not available, but track exists
                            // Try to calculate from file by seeking to end
                            extractor.selectTrack(i)
                            seekToEndAndGetTimestamp(extractor)
                        }
                        break
                    }
                }
                audioDuration
            }
        } catch (e: Exception) {
            Timber.d("MediaExtractor cannot read file: ${e.message}")
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * Seeks to the end of the track to get the last sample timestamp.
     */
    private fun seekToEndAndGetTimestamp(extractor: MediaExtractor): Long {
        var lastTimestamp = 0L
        while (extractor.advance()) {
            lastTimestamp = extractor.sampleTime
        }
        return lastTimestamp
    }

    /**
     * Attempts to re-mux a broken audio file by extracting audio frames
     * and writing them into a new valid container using MediaExtractor + MediaMuxer.
     */
    //TODO: Looks like this remux only rewrites only one track. What if the track has more that one track?
    //TODO: Need to test this.
    private fun tryRemuxFile(file: File): RestoreResult {
        val tempFile = File(file.parent, "${file.nameWithoutExtension}_restored.${file.extension}")
        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount

            if (trackCount == 0) {
                return RestoreResult.Failed("No tracks found in the file")
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return RestoreResult.Failed("No audio track found")
            }

            extractor.selectTrack(audioTrackIndex)

            val outputFormat = determineOutputFormat(file)
            val muxer = MediaMuxer(tempFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = try {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                DEFAULT_BUFFER_SIZE
            }
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            var lastTimestamp = 0L
            var sampleCount = 0

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                // Map MediaExtractor sample flags to MediaCodec buffer flags
                val extractorFlags = extractor.sampleFlags
                bufferInfo.flags = if (extractorFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }

                lastTimestamp = extractor.sampleTime
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                sampleCount++

                extractor.advance()
            }

            muxer.stop()
            muxer.release()

            if (sampleCount == 0) {
                tempFile.delete()
                return RestoreResult.Failed("No audio samples found in the file")
            }

            // Replace original file with restored file
            replaceFile(tempFile, file)
            Timber.d("File restored via re-mux: ${file.absolutePath}, samples: $sampleCount, duration: ${lastTimestamp}μs")
            RestoreResult.Success(lastTimestamp)
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-mux file: ${file.absolutePath}")
            tempFile.delete()
            RestoreResult.Failed("Re-mux failed: ${e.message}")
        } finally {
            extractor.release()
        }
    }

    /**
     * Fallback restoration using mp4parser library.
     *
     * When a MediaRecorder-produced MPEG-4 file is missing its moov atom,
     * the raw AAC data is still present in the file. This method:
     * 1. Extracts raw AAC data from the broken file by locating the mdat atom
     * 2. If the extracted data does not start with an ADTS sync word (0xFFF),
     *    wraps each raw AAC-LC frame with an ADTS header so that AACTrackImpl
     *    can parse it. The ADTS header encodes profile, sample-rate index and
     *    channel configuration derived from the recording settings stored in the DB.
     * 3. Uses mp4parser's AACTrackImpl to parse the ADTS stream and create a proper track
     * 4. Builds a new valid MPEG-4 container with DefaultMp4Builder
     *
     * @param file The broken audio file
     * @return RestoreResult indicating success or failure
     */
    @Suppress("TooGenericExceptionCaught")
    private fun tryRestoreWithMp4Parser(file: File, sampleRate: Int, channelCount: Int, bitrate: Int): RestoreResult {
        val tempAacFile = File(file.parent, "${file.nameWithoutExtension}_raw.aac")
        val tempAdtsFile = File(file.parent, "${file.nameWithoutExtension}_adts.aac")
        val tempMp4File = File(file.parent, "${file.nameWithoutExtension}_restored.${file.extension}")

        return try {
            // Step 1: Extract raw AAC data from the broken MPEG-4 file
            val extracted = extractMdatPayload(file, tempAacFile)
            if (!extracted) {
                return RestoreResult.Failed("Could not extract audio data from broken file")
            }

            Timber.d("Extracted raw AAC data: ${tempAacFile.length()} bytes from broken file: ${file.absolutePath}")

            // Step 2: Determine which AAC file to feed to AACTrackImpl.
            // AACTrackImpl expects ADTS-framed AAC (sync word 0xFFF at start of every frame).
            // M4A containers store raw AAC-LC frames WITHOUT ADTS headers, so we must add them.
            val aacFileForParsing = if (hasAdtsHeader(tempAacFile)) {
                Timber.d("Extracted AAC data already has ADTS headers, using as-is")
                tempAacFile
            } else {
                Timber.d("Extracted AAC data lacks ADTS headers, wrapping raw frames with ADTS")
                val wrapped = wrapRawAacWithAdts(
                    rawFile = tempAacFile,
                    outputFile = tempAdtsFile,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    bitrate = bitrate,
                )
                if (!wrapped) {
                    return RestoreResult.Failed("Failed to wrap raw AAC frames with ADTS headers")
                }
                tempAdtsFile
            }

            // Step 3: Use mp4parser to parse the ADTS AAC stream and create a valid container
            val aacTrack = AACTrackImpl(FileDataSourceImpl(aacFileForParsing))

            val movie = Movie()
            movie.addTrack(aacTrack)

            val mp4Builder = DefaultMp4Builder()
            val container = mp4Builder.build(movie)

            // Step 4: Write the valid MP4 container to the temp file
            FileOutputStream(tempMp4File).use { fos ->
                container.writeContainer(fos.channel)
            }

            // Step 5: Verify the restored file is readable
            val verifyDuration = tryReadWithExtractor(tempMp4File.absolutePath)
            if (verifyDuration == null || verifyDuration <= 0) {
                tempAacFile.delete()
                tempAdtsFile.delete()
                tempMp4File.delete()
                return RestoreResult.Failed("Restored file is not readable after mp4parser rebuild")
            }

            // Step 6: Replace the original file with the restored file
            replaceFile(tempMp4File, file)
            tempAacFile.delete()
            tempAdtsFile.delete()

            Timber.d("File restored via mp4parser: ${file.absolutePath}, duration: ${verifyDuration}μs")
            RestoreResult.Success(verifyDuration)
        } catch (e: Exception) {
            Timber.e(e, "mp4parser restoration failed for: ${file.absolutePath}")
            tempAacFile.delete()
            tempAdtsFile.delete()
            tempMp4File.delete()
            RestoreResult.Failed("mp4parser restoration failed: ${e.message}")
        }
    }

    /**
     * Checks whether the first bytes of [file] look like an ADTS AAC stream.
     * ADTS frames start with a 12-bit sync word: the first byte is 0xFF and the
     * high nibble of the second byte is 0xF.
     */
    private fun hasAdtsHeader(file: File): Boolean {
        if (file.length() < 2) return false
        RandomAccessFile(file, "r").use { raf ->
            val b0 = raf.read()
            val b1 = raf.read()
            return b0 == 0xFF && (b1 and 0xF0) == 0xF0
        }
    }

    /**
     * Wraps raw AAC-LC frames (as stored in the M4A mdat atom) with ADTS headers so
     * that the resulting file is a standard ADTS AAC stream readable by [AACTrackImpl].
     *
     * In an M4A container each audio sample is a complete raw AAC-LC frame with no ADTS
     * header. When the moov atom is missing we don't have a sample table (stsz) to tell
     * us individual frame sizes, so we must detect frame boundaries from the bitstream.
     *
     * Strategy:
     * 1. Scan the raw AAC-LC bitstream looking for the **ID_END element** (3-bit value
     *    `0b111`) that every AAC-LC raw_data_block() ends with, byte-aligned after padding.
     *    This lets us identify where each frame ends.
     * 2. Each detected frame gets a 7-byte ADTS header prepended.
     *
     * If the bitstream-scan produces zero frames (e.g. the data is opaque), we fall back
     * to splitting the raw data into equal-sized chunks using a typical AAC-LC frame size
     * estimate (bitrate / sampleRate * 1024 / 8), capped to [AAC_ADTS_MAX_PAYLOAD_SIZE].
     *
     * @param rawFile     Input file: concatenated raw AAC-LC frames without ADTS headers.
     * @param outputFile  Where to write the ADTS-framed output.
     * @param sampleRate  Sample rate of the recording (used for ADTS header and frame size).
     * @param channelCount Number of audio channels (written into the ADTS channel_config field).
     * @param bitrate     Encoding bitrate in bps (used to derive the per-frame byte count).
     * @return true on success, false if wrapping failed entirely.
     */
    @Suppress("MagicNumber")
    private fun wrapRawAacWithAdts(
        rawFile: File,
        outputFile: File,
        sampleRate: Int,
        channelCount: Int,
        bitrate: Int,
    ): Boolean {
        // --- ADTS header constants ---
        // AAC sampling frequency index table (ISO 13818-7 §8.1.3.2 Table 35)
        val sampleRateTable = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )
        val samplingFreqIndex = sampleRateTable.indexOfFirst { it == sampleRate }
            .takeIf { it >= 0 } ?: 4 // default index 4 = 44100 Hz

        // channel_configuration field in ADTS header
        val channelConfig = when (channelCount) {
            1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; 5 -> 5; 6 -> 6; 8 -> 7; else -> 2
        }

        // AAC-LC profile_ObjectType = 0  (profile - 1, where AAC-LC = 1)
        val profileObjectType = 0

        val rawData = rawFile.readBytes()
        if (rawData.isEmpty()) return false

        // --- Detect frame boundaries ---
        val frameBoundaries = findAacLcFrameBoundaries(rawData, channelCount)

        val frameSizes: List<Int> = if (frameBoundaries.size >= 2) {
            // Build sizes from boundary list (last entry is end-of-data)
            List(frameBoundaries.size - 1) { i -> frameBoundaries[i + 1] - frameBoundaries[i] }
        } else {
            return false
        }

        // --- Write ADTS stream ---
        var framesWritten = 0
        var dataPos = 0
        FileOutputStream(outputFile).use { fos ->
            for (frameDataSize in frameSizes) {
                if (frameDataSize <= 0 || dataPos + frameDataSize > rawData.size) break

                val adtsFrameLength = frameDataSize + ADTS_HEADER_SIZE

                // 7-byte ADTS header (no CRC, protection_absent = 1):
                //   Sync(12) | ID(1) | layer(2) | protection_absent(1)
                //   | profile_ObjectType(2) | sampling_frequency_index(4)
                //   | private_bit(1) | channel_configuration(3)
                //   | originality/copy/home/copyright bits(4)
                //   | aac_frame_length(13) | buffer_fullness(11) | raw_data_blocks(2)
                val h = ByteArray(ADTS_HEADER_SIZE)
                h[0] = 0xFF.toByte()
                h[1] = 0xF1.toByte()                                                          // MPEG-4, no CRC
                h[2] = ((profileObjectType shl 6) or (samplingFreqIndex shl 2) or (channelConfig ushr 2)).toByte()
                h[3] = (((channelConfig and 0x3) shl 6) or ((adtsFrameLength ushr 11) and 0x3)).toByte()
                h[4] = ((adtsFrameLength ushr 3) and 0xFF).toByte()
                h[5] = (((adtsFrameLength and 0x7) shl 5) or 0x1F).toByte()                  // buffer_fullness = 0x7FF (VBR), high 5 bits
                h[6] = 0xFC.toByte()                                                          // buffer_fullness low 6 bits = 0x3F, raw_blocks = 0
                fos.write(h)
                fos.write(rawData, dataPos, frameDataSize)
                dataPos += frameDataSize
                framesWritten++
            }
        }

        Timber.d("Wrapped $framesWritten ADTS frames, output size: ${outputFile.length()} bytes")
        return framesWritten > 0 && outputFile.length() > 0
    }

    /**
     * Scans a raw (headerless) AAC-LC bitstream and returns a list of byte offsets at
     * which each frame starts, with one extra entry at the end equal to [data].size.
     *
     * Raw AAC-LC frames stored in an M4A mdat atom do not have ADTS sync words, so we
     * use a bitstream heuristic to locate frame boundaries:
     *
     * - The first syntactic element of every `raw_data_block()` is an **id_syn_ele**
     *   (3 bits). For typical MediaRecorder output this is `SCE = 0b000` (mono) or
     *   `CPE = 0b001` (stereo).
     * - The end of every `raw_data_block()` is marked by an **ID_END** element (`0b111`)
     *   written byte-aligned.
     * - Therefore, the byte *immediately after* the last padding byte of a frame tends
     *   to start with `0b000xxxxx` (SCE) or `0b001xxxxx` (CPE).
     *
     * Algorithm: starting from the previous boundary + MIN_AAC_FRAME_BYTES, scan forward
     * one byte at a time looking for a byte whose top-3-bits match the expected element
     * type. The *first* such byte is accepted as the next frame start. This ensures we
     * never create frames smaller than MIN_AAC_FRAME_BYTES and avoids the false-positive
     * flood that a naive per-byte scan produces.
     *
     * @param data        Raw concatenated AAC-LC frames (no ADTS headers).
     * @param channelCount 1 = mono (SCE), 2 = stereo (CPE), else unknown.
     * @return List of frame-start byte offsets with an end-of-data sentinel appended;
     *         a single-element list (just [0]) means no additional boundaries were found.
     */
    @Suppress("MagicNumber")
    private fun findAacLcFrameBoundaries(data: ByteArray, channelCount: Int): List<Int> {
        if (data.size < MIN_AAC_FRAME_BYTES * 2) return emptyList()

        val boundaries = mutableListOf(0) // first frame always starts at byte 0

        // expected id_syn_ele top-3-bits for this channel layout
        val expectedTopBits = when (channelCount) {
            1 -> intArrayOf(0b000)          // SCE only
            2 -> intArrayOf(0b001)          // CPE only
            else -> intArrayOf(0b000, 0b001) // accept both
        }

        // Scan forward: each iteration looks for the NEXT frame boundary starting at
        // (last boundary + MIN_AAC_FRAME_BYTES), then records the FIRST qualifying byte.
        while (true) {
            val searchStart = boundaries.last() + MIN_AAC_FRAME_BYTES
            if (searchStart >= data.size) break

            var found = false
            var pos = searchStart
            while (pos < data.size) {
                // Cap search: don't scan more than AAC_ADTS_MAX_PAYLOAD_SIZE past the
                // last boundary so we don't emit oversized frames.
                if (pos - boundaries.last() > AAC_ADTS_MAX_PAYLOAD_SIZE) {
                    // Force a boundary at the max-size mark even if no sync was detected
                    boundaries.add(boundaries.last() + AAC_ADTS_MAX_PAYLOAD_SIZE)
                    found = true
                    break
                }
                val topBits = (data[pos].toInt() and 0xFF) ushr 5
                if (topBits in expectedTopBits) {
                    boundaries.add(pos)
                    found = true
                    break
                }
                pos++
            }
            if (!found) break // no further boundary found — the rest is the last frame
        }

        // Append end-of-data sentinel so callers can derive frame sizes from differences
        if (boundaries.last() != data.size) boundaries.add(data.size)

        return boundaries
    }

    /**
     * Extracts the raw media data (mdat atom payload) from a broken MPEG-4 file.
     *
     * An MPEG-4 file consists of atoms (boxes). The 'mdat' atom contains the actual
     * audio data. When MediaRecorder is interrupted, the 'moov' atom (which contains
     * the sample table and track info) is missing, but the 'mdat' data is still there.
     *
     * This method scans the file for the 'mdat' atom and extracts its payload.
     * If no mdat atom is found, it falls back to copying the entire file as raw AAC
     * (in case the file has no proper atom structure at all).
     *
     * @param inputFile The broken MPEG-4 file
     * @param outputFile Where to write the extracted raw AAC data
     * @return true if extraction succeeded, false otherwise
     */
    @Suppress("MagicNumber")
    private fun extractMdatPayload(inputFile: File, outputFile: File): Boolean {
        RandomAccessFile(inputFile, "r").use { raf ->
            val fileSize = raf.length()
            var position = 0L

            while (position < fileSize - 8) {
                raf.seek(position)
                // Read atom size (4 bytes, big-endian) and type (4 bytes ASCII)
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)

                when {
                    type == "mdat" -> {
                        // Found the mdat atom — extract its payload
                        val headerSize: Long
                        val dataSize: Long
                        if (size == 1L) {
                            // Extended size: next 8 bytes contain the actual 64-bit size
                            val extendedSize = raf.readLong()
                            headerSize = 16L
                            dataSize = extendedSize - headerSize
                        } else if (size == 0L) {
                            // Size 0 means the atom extends to the end of the file
                            headerSize = 8L
                            dataSize = fileSize - position - headerSize
                        } else {
                            headerSize = 8L
                            dataSize = size - headerSize
                        }

                        val dataStart = position + headerSize

                        if (dataSize <= 0 || dataStart + dataSize > fileSize) {
                            // mdat extends beyond file (interrupted write) — take whatever is available
                            val availableData = fileSize - dataStart
                            if (availableData <= 0) return false
                            return copyFileRange(raf, dataStart, availableData, outputFile)
                        }

                        return copyFileRange(raf, dataStart, dataSize, outputFile)
                    }
                    size >= 8 -> {
                        // Skip to the next atom
                        position += size
                    }
                    else -> {
                        // Invalid atom size — try advancing byte by byte to find mdat
                        position++
                    }
                }
            }

            // mdat atom not found. The file might not have an atom structure at all.
            // Try treating the entire file as raw AAC data.
            Timber.d("No mdat atom found, trying entire file as raw AAC")
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outputFile.length() > 0
        }
    }

    /**
     * Copies a range of bytes from a RandomAccessFile to an output file.
     */
    @Suppress("MagicNumber")
    private fun copyFileRange(
        raf: RandomAccessFile,
        offset: Long,
        length: Long,
        outputFile: File,
    ): Boolean {
        raf.seek(offset)
        FileOutputStream(outputFile).use { fos ->
            val buffer = ByteArray(8192)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                val bytesRead = raf.read(buffer, 0, toRead)
                if (bytesRead <= 0) break
                fos.write(buffer, 0, bytesRead)
                remaining -= bytesRead
            }
        }
        return outputFile.length() > 0
    }

    /**
     * Replaces the target file with the source file.
     * Tries rename first, falls back to copy.
     */
    //TODO: move this to File Utils and cover with unit tests.
    private fun replaceFile(source: File, target: File) {
        if (target.delete() && source.renameTo(target)) {
            return
        }
        // If rename failed, try copy
        source.copyTo(target, overwrite = true)
        source.delete()
    }

    /**
     * Determines the appropriate MediaMuxer output format based on file extension.
     */
    private fun determineOutputFormat(file: File): Int {
        //TODO: this need to be improved to support more extensions
        return when (file.extension.lowercase()) {
            "3gp" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB

        /** Size of a 7-byte ADTS header (no CRC, protection_absent = 1). */
        private const val ADTS_HEADER_SIZE = 7

        /**
         * Maximum payload (AAC data) size per ADTS frame.
         * The ADTS aac_frame_length field is 13 bits → max total frame = 8191 bytes.
         * Subtract the 7-byte header to get the max payload.
         */
        private const val AAC_ADTS_MAX_PAYLOAD_SIZE = 8191 - ADTS_HEADER_SIZE // = 8184

        /**
         * Minimum plausible size of a single raw AAC-LC frame in bytes.
         * Very small values would indicate noise rather than real frame boundaries.
         */
        private const val MIN_AAC_FRAME_BYTES = 32
    }


    /**
     * Result of a broken record restoration attempt.
     */
    sealed class RestoreResult {
        /**
         * File was successfully restored. Contains recovered duration in microseconds.
         */
        data class Success(val durationMicros: Long) : RestoreResult()

        /**
         * File is already readable by MediaExtractor — no re-muxing needed.
         * The file can be used as-is.
         */
        data class AlreadyReadable(val durationMicros: Long) : RestoreResult()

        /**
         * Restoration failed. The file is unrecoverable.
         */
        data class Failed(val error: String) : RestoreResult()
    }
}
