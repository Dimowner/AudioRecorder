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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import org.mp4parser.muxer.FileDataSourceImpl
import org.mp4parser.muxer.Movie
import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.tracks.AACTrackImpl
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores broken audio recording files that were interrupted (e.g., by a device reboot)
 * before the recorder was properly stopped.
 *
 * Supported formats and strategies:
 *
 * **WAV** (produced by [WavRecorderV2]):
 *   A broken WAV file contains all the raw PCM data but has an all-zero 44-byte RIFF header
 *   (the placeholder written at recording start that was never filled in).
 *   Restoration rewrites the header in-place using recording parameters from the database.
 * **M4A / MPEG-4** (produced by [AacCodecRecorderV2], or [AudioRecorderV2] via MediaRecorder):
 *   An interrupted MPEG-4 file is missing its 'moov' atom, and with it the sample table that
 *   says where each AAC access unit in the mdat atom ends. Raw AAC-LC frames carry no sync word,
 *   so those boundaries cannot be recovered from the bitstream. Strategies, in order:
 *   1. Try MediaExtractor (works if the OS partially recovered the file)
 *   2. Try re-muxing with MediaExtractor + MediaMuxer
 *   3. Rebuild from the [AacFrameIndex] sidecar [AacCodecRecorderV2] writes while recording —
 *      the sample table survives the kill there, so this rebuild is exact
 *   4. Only for a payload that is already ADTS-framed, and so self-describing: re-mux it into a
 *      new container (with a heap-guarded mp4parser fallback)
 *
 * **3GP / AMR** (produced by [ThreeGpRecorderV2]):
 *   Same missing-'moov' problem, but AMR frames carry a size in their own header: the payload is
 *   re-framed as a standalone AMR file and muxed back into a valid 3GP container.
 */
@Singleton
class BrokenRecordRestorer @Inject constructor() {

    /**
     * Attempts to restore a broken audio recording file.
     *
     * Dispatches to a format-specific strategy based on the file extension.
     * See the class KDoc for per-format details.
     *
     * @param filePath Path to the broken audio file
     * @param sampleRate Sample rate from the database record (required for WAV and 3GP)
     * @param channelCount Channel count from the database record (required for WAV)
     * @return RestoreResult indicating success or failure
     */
    fun restoreFile(
        filePath: String,
        sampleRate: Int = 0,
        channelCount: Int = 0,
    ): RestoreResult {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return RestoreResult.Failed("File does not exist or is empty")
        }

        return when {
            file.extension.equals("wav",  ignoreCase = true) -> tryRestoreWavFile(file, sampleRate, channelCount)
            file.extension.equals("3gp",  ignoreCase = true) -> tryRestore3gpContainer(file, sampleRate)
            else                                              -> tryRestoreMp4Container(file)
        }
    }

    // -------------------------------------------------------------------------
    // 3GP container restoration
    // -------------------------------------------------------------------------

    private fun tryRestore3gpContainer(file: File, sampleRate: Int): RestoreResult {
        val directReadResult = tryReadWithExtractor(file.absolutePath)
        if (directReadResult != null) {
            Timber.d("File is already readable by MediaExtractor: ${file.absolutePath}, duration: ${directReadResult}μs")
            return RestoreResult.AlreadyReadable(directReadResult)
        }
        Timber.d("File is not directly readable, attempting re-mux: ${file.absolutePath}")
        val remuxResult = tryRemuxFile(file)
        if (remuxResult !is RestoreResult.Failed) return remuxResult
        Timber.d("Re-mux failed, attempting 3GP/AMR-specific restore: ${file.absolutePath}")
        return tryRestore3gpFile(file, sampleRate)
    }

    // -------------------------------------------------------------------------
    // MPEG-4 container restoration
    // -------------------------------------------------------------------------

    /**
     * Restores a broken MPEG-4 recording, and clears the [AacFrameIndex] sidecar behind whichever
     * strategy succeeded.
     *
     * The sidecar only exists to rebuild a container that was never closed, so once the file
     * carries a sample table of its own it is dead weight - and nothing else ever removes it:
     * [AacCodecRecorderV2] deletes it when the recording it belongs to stops normally, which by
     * definition did not happen to a file that reached the restorer. A kill landing between
     * `moov` hitting the disk and that cleanup leaves a readable file next to a hidden index
     * that would otherwise stay there for good, so the deletion belongs on every restored
     * outcome rather than only on the rebuild that happens to read the index.
     *
     * A failure keeps the sidecar: it is the only thing a later attempt could rebuild from.
     */
    private fun tryRestoreMp4Container(file: File): RestoreResult {
        val result = restoreMp4Container(file)
        if (result !is RestoreResult.Failed) {
            AacFrameIndex.delete(file)
        }
        return result
    }

    private fun restoreMp4Container(file: File): RestoreResult {
        val directReadResult = tryReadWithExtractor(file.absolutePath)
        if (directReadResult != null) {
            Timber.d("File is already readable by MediaExtractor: ${file.absolutePath}, duration: ${directReadResult}μs")
            return RestoreResult.AlreadyReadable(directReadResult)
        }
        Timber.d("File is not directly readable, attempting re-mux: ${file.absolutePath}")
        val remuxResult = tryRemuxFile(file)
        if (remuxResult !is RestoreResult.Failed) return remuxResult

        // The sample table [AacCodecRecorderV2] mirrored while recording is the only source of
        // exact frame boundaries, so it is tried before anything that has to infer them.
        Timber.d("Re-mux failed, attempting frame-index rebuild: ${file.absolutePath}")
        val indexedResult = tryRestoreWithFrameIndex(file)
        if (indexedResult !is RestoreResult.Failed) return indexedResult

        Timber.d("Frame-index rebuild unavailable (${indexedResult.error}), attempting mp4parser fallback")
        return tryRestoreWithMp4Parser(file)
    }

    /**
     * Rebuilds a broken `.m4a` from the sidecar sample table written by [AacCodecRecorderV2].
     *
     * The `mdat` atom of an interrupted recording still holds every encoded access unit, but an
     * MPEG-4 container keeps their sizes in `moov`, which `MediaMuxer` only writes on stop. Raw
     * AAC-LC frames carry no sync word, so once `moov` is missing the boundaries are gone with
     * it — which is precisely what [AacFrameIndex] preserves.
     *
     * With the sizes in hand the rebuild is exact rather than approximate: each access unit is
     * handed to a fresh `MediaMuxer` untouched, under the encoder's own `csd-0`, with timestamps
     * derived from the AAC frame length. The muxer buffers its `mdat` writes, so the payload on
     * disk usually stops short of the index; the rebuild simply ends at the last complete frame.
     */
    private fun tryRestoreWithFrameIndex(file: File): RestoreResult {
        val index = AacFrameIndex.openReader(file)
            ?: return RestoreResult.Failed("No AAC frame index alongside ${file.name}")

        val tempAacFile = File(file.parent, "${file.nameWithoutExtension}_raw.aac")
        val tempMp4File = File(file.parent, "${file.nameWithoutExtension}_restored.${file.extension}")
        return try {
            if (!extractMdatPayload(file, tempAacFile)) {
                return RestoreResult.Failed("Could not extract audio data from broken file")
            }
            Timber.d("Extracted ${tempAacFile.length()} bytes of AAC payload from: ${file.absolutePath}")

            val framesWritten = muxIndexedAacIntoMp4(tempAacFile, index, tempMp4File)
            if (framesWritten <= 0) {
                return RestoreResult.Failed("Frame index describes no complete audio frame")
            }

            val verifyDuration = tryReadWithExtractor(tempMp4File.absolutePath)
            if (verifyDuration == null || verifyDuration <= 0) {
                return RestoreResult.Failed("Restored file is not readable after frame-index rebuild")
            }

            replaceFile(tempMp4File, file)
            Timber.d(
                "File restored from frame index: ${file.absolutePath}, " +
                        "frames: $framesWritten, duration: ${verifyDuration}μs"
            )
            RestoreResult.Success(verifyDuration)
        } catch (e: Exception) {
            Timber.e(e, "Frame-index restoration failed for: ${file.absolutePath}")
            RestoreResult.Failed("Frame-index restoration failed: ${e.message}")
        } finally {
            index.close()
            tempAacFile.delete()
            tempMp4File.delete()
        }
    }

    /**
     * Writes the access units [rawFile] holds into a fresh MPEG-4 container, cutting them at the
     * boundaries [index] recorded and stopping at the last one the payload covers in full.
     *
     * @return the number of access units written.
     */
    private fun muxIndexedAacIntoMp4(rawFile: File, index: AacFrameIndex.Reader, outputFile: File): Int {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, index.sampleRate, index.channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            // The encoder's own AudioSpecificConfig: replaying it verbatim keeps the rebuilt
            // track's profile, sample rate and channel layout identical to what was recorded.
            setByteBuffer("csd-0", ByteBuffer.wrap(index.csd0))
        }

        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var framesWritten = 0
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val frame = ByteArray(AacFrameIndex.MAX_FRAME_SIZE)
            val buffer = ByteBuffer.wrap(frame)
            val info = MediaCodec.BufferInfo()

            BufferedInputStream(FileInputStream(rawFile), DEFAULT_BUFFER_SIZE).use { input ->
                while (true) {
                    val frameSize = index.nextFrameSize()
                    if (frameSize <= 0) break
                    // A short read means the muxer never flushed this frame: the audio simply
                    // ends here, and the frames already written stay valid.
                    if (!input.readFrame(frame, frameSize)) break

                    buffer.clear()
                    buffer.limit(frameSize)
                    info.set(
                        0,
                        frameSize,
                        aacPtsUs(framesWritten.toLong(), index.sampleRate),
                        MediaCodec.BUFFER_FLAG_KEY_FRAME,
                    )
                    muxer.writeSampleData(trackIndex, buffer, info)
                    framesWritten++
                }
            }

            if (framesWritten == 0) return 0
            muxer.stop()
            muxerStarted = false
            Timber.d("Frame-index re-mux wrote $framesWritten samples to ${outputFile.absolutePath}")
            return framesWritten
        } catch (e: Exception) {
            Timber.e(e, "Frame-index re-mux failed for: ${rawFile.absolutePath}")
            return 0
        } finally {
            if (muxerStarted) {
                // stop() was never reached - the muxer would otherwise throw on release().
                try { muxer?.stop() } catch (_: Throwable) {}
            }
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    /** Fills [size] bytes of [frame] from this stream, returning false if the stream ends first. */
    private fun InputStream.readFrame(frame: ByteArray, size: Int): Boolean {
        var filled = 0
        while (filled < size) {
            val read = read(frame, filled, size - filled)
            if (read <= 0) return false
            filled += read
        }
        return true
    }

    /**
     * Restores a broken WAV file recorded by [WavRecorderV2].
     *
     * When [WavRecorderV2] starts a recording it writes a 44-byte all-zero placeholder header
     * and then appends raw PCM-16LE samples. On a normal stop it seeks back and overwrites that
     * header with the correct RIFF/WAV values. If the app is killed before [WavRecorderV2.stopRecording]
     * completes, the PCM data is intact but the header is still all-zeros (or partially written),
     * making the file unreadable.
     *
     * This method:
     * 1. Validates that the file is large enough to contain a header + some PCM data.
     * 2. Checks whether the header is already valid (RIFF magic bytes present) — if so, the file
     *    may already be readable; falls through to [tryReadWithExtractor] to confirm.
     * 3. Computes correct WAV header values from [sampleRate] and [channelCount] (stored in the DB).
     * 4. Overwrites only the first 44 bytes in-place — the PCM data is untouched.
     * 5. Verifies the restored file is readable via [tryReadWithExtractor].
     *
     * @param file        The broken WAV file.
     * @param sampleRate  Sample rate stored in the DB record (e.g. 44100).
     * @param channelCount Number of channels stored in the DB record (1 = mono, 2 = stereo).
     * @return [RestoreResult.Success] with duration in µs, [RestoreResult.AlreadyReadable] if the
     *         file needed no fix, or [RestoreResult.Failed] with a reason string.
     */
    @Suppress("MagicNumber")
    private fun tryRestoreWavFile(file: File, sampleRate: Int, channelCount: Int): RestoreResult {
        val fileSize = file.length()

        // A valid WAV file needs at least the 44-byte header plus 1 byte of audio data.
        if (fileSize <= WAV_HEADER_SIZE) {
            return RestoreResult.Failed("WAV file too small to contain audio data: ${file.absolutePath}")
        }

        if (sampleRate <= 0 || channelCount <= 0) {
            return RestoreResult.Failed(
                "Cannot restore WAV: missing recording parameters " +
                        "(sampleRate=$sampleRate, channelCount=$channelCount)"
            )
        }

        // Check whether the file already has a valid RIFF header.
        val hasValidHeader = try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                magic[0] == 'R'.code.toByte() &&
                        magic[1] == 'I'.code.toByte() &&
                        magic[2] == 'F'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read WAV header: ${file.absolutePath}")
            false
        }

        if (hasValidHeader) {
            // Header looks correct already — verify the file is actually playable.
            val duration = tryReadWithExtractor(file.absolutePath)
            return if (duration != null) {
                Timber.d("WAV file already has valid header and is readable: ${file.absolutePath}")
                RestoreResult.AlreadyReadable(duration)
            } else {
                // RIFF magic is there but MediaExtractor still can't read it —
                // fall through to rewrite the header anyway.
                Timber.d("WAV has RIFF magic but is not readable, rewriting header: ${file.absolutePath}")
                rewriteWavHeader(file, fileSize, sampleRate, channelCount)
            }
        }

        Timber.d("WAV file has broken/zero header, rewriting: ${file.absolutePath}")
        return rewriteWavHeader(file, fileSize, sampleRate, channelCount)
    }

    /**
     * Rewrites the 44-byte RIFF/WAV header of [file] in-place and verifies the result.
     *
     * The PCM payload size is derived as `fileSize - 44` (the placeholder header written
     * by [WavRecorderV2] occupies the first 44 bytes; everything after is raw PCM-16LE).
     */
    @Suppress("MagicNumber")
    private fun rewriteWavHeader(file: File, fileSize: Long, sampleRate: Int, channelCount: Int): RestoreResult {
        val bitsPerSample = 16
        val totalAudioLen = fileSize - WAV_HEADER_SIZE          // raw PCM bytes
        val totalDataLen  = totalAudioLen + 36                  // ChunkSize field value
        val byteRate      = (sampleRate * channelCount * bitsPerSample / 8).toLong()

        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)

                val header = createWavHeader(
                    totalAudioLen = totalAudioLen,
                    totalDataLen = totalDataLen,
                    sampleRate = sampleRate,
                    channels = channelCount,
                    byteRate = byteRate,
                )

                raf.write(header)
            }

            // Verify the repaired file is now readable
            val duration = tryReadWithExtractor(file.absolutePath)
            if (duration != null && duration > 0) {
                Timber.d("WAV file restored successfully: ${file.absolutePath}, duration: ${duration}μs")
                RestoreResult.Success(duration)
            } else {
                // MediaExtractor couldn't parse it — calculate duration from PCM byte count as fallback
                val durationMicros = if (byteRate > 0) (totalAudioLen * 1_000_000L) / byteRate else 0L
                if (durationMicros > 0) {
                    Timber.d("WAV header rewritten, using calculated duration: ${file.absolutePath}, duration: ${durationMicros}μs")
                    RestoreResult.Success(durationMicros)
                } else {
                    Timber.e("WAV header rewritten but file is still not readable: ${file.absolutePath}")
                    RestoreResult.Failed("WAV header rewritten but file is still not readable")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rewrite WAV header: ${file.absolutePath}")
            RestoreResult.Failed("Failed to rewrite WAV header: ${e.message}")
        }
    }

    /**
     * Tries to read the audio file with MediaExtractor.
     * @return duration in microseconds if readable, null if not readable
     */
    private fun tryReadWithExtractor(filePath: String): Long? {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
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
        } catch (t: Throwable) {
            // Catches both Exception (broken file, API issues) and Error/RuntimeException
            // (e.g. UnsatisfiedLinkError or "Method not mocked" when MediaExtractor native
            // library is unavailable in JVM unit-test environments without Robolectric).
            Timber.d("MediaExtractor cannot read file: ${t.message}")
            null
        } finally {
            try {
                extractor?.release()
            } catch (_: Throwable) {
                // Ignore release() failures — the extractor is being discarded anyway.
                // On JVM unit tests, release() throws RuntimeException: Method not mocked.
            }
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

    // -------------------------------------------------------------------------
    // 3GP / AMR restoration
    // -------------------------------------------------------------------------

    /**
     * Restores a broken 3GPP recording whose moov atom was never written.
     *
     * Strategy:
     * 1. Extract the raw AMR bitstream from the mdat atom of the broken 3GP container.
     * 2. Prepend the magic file header so the stream is a valid standalone AMR file
     *    (`#!AMR\n` for AMR-NB / `#!AMR-WB\n` for AMR-WB).
     * 3. Re-mux the valid AMR file into a new 3GPP container using
     *    MediaExtractor + MediaMuxer — the same technique used by [tryRemuxFile].
     * 4. Verify the result is playable and replace the original file.
     *
     * Codec choice is based on [sampleRate]: ≤ 8 000 Hz → AMR-NB, otherwise → AMR-WB,
     * matching the logic in [ThreeGpRecorderV2].
     */
    private fun tryRestore3gpFile(file: File, sampleRate: Int): RestoreResult {
        val isWb = sampleRate > AMR_NB_SAMPLE_RATE
        val codec = if (isWb) "AMR-WB" else "AMR-NB"
        Timber.d("Restoring 3GP file as $codec (sampleRate=$sampleRate): ${file.absolutePath}")

        val rawAmrFile = File(file.parent, "${file.nameWithoutExtension}_raw.amr")
        val restoredFile = File(file.parent, "${file.nameWithoutExtension}_restored.3gp")

        return try {
            // Step 1: Extract raw AMR payload from the mdat atom
            val extracted = extractMdatPayload(file, rawAmrFile)
            if (!extracted) {
                return RestoreResult.Failed("3GP: could not extract audio data from broken file")
            }
            Timber.d("3GP: extracted ${rawAmrFile.length()} bytes from mdat atom")

            // Step 2: Build a valid standalone AMR file by prepending the magic header
            val amrFile = File(file.parent, "${file.nameWithoutExtension}.amr")
            val built = buildAmrFile(rawAmrFile, amrFile, isWb)
            if (!built) {
                return RestoreResult.Failed("3GP: could not build a valid AMR stream from extracted data")
            }
            Timber.d("3GP: built valid AMR file (${amrFile.length()} bytes)")

            // Step 3: Re-mux the AMR file into a new 3GPP container
            val remuxResult = remuxAmrInto3gp(amrFile, restoredFile)
            if (remuxResult is RestoreResult.Failed) {
                return remuxResult
            }

            // Step 4: Replace the original broken file
            replaceFile(restoredFile, file)
            Timber.d("3GP file restored successfully: ${file.absolutePath}")
            remuxResult
        } catch (e: Exception) {
            Timber.e(e, "3GP restore failed for: ${file.absolutePath}")
            RestoreResult.Failed("3GP restore failed: ${e.message}")
        } finally {
            rawAmrFile.delete()
            // amrFile may not exist if buildAmrFile failed — delete silently
            File(file.parent, "${file.nameWithoutExtension}.amr").delete()
            restoredFile.delete()
        }
    }

    /**
     * Prepends the AMR magic file header to [rawAmrData] and writes the result to
     * [outputFile]. Before writing, attempts to detect and skip any bytes that precede
     * the first valid AMR frame so we don't corrupt the stream with container remnants.
     *
     * AMR-NB frame sync byte: high nibble = 0x0 (class bits), overall structure
     *   `0 FT(4) Q(1) P(2)` = `0xxxxxx0` where top bit is always 0.
     * AMR-WB frame sync byte: `0 FT(4) Q(1) P(2)` same layout, FT 0–8 are speech.
     *
     * @return true if at least one valid frame was found and written, false otherwise.
     */
    @Suppress("MagicNumber")
    internal fun buildAmrFile(rawAmrData: File, outputFile: File, isWb: Boolean): Boolean {
        if (rawAmrData.length() <= 0) return false

        val magic = if (isWb) AMR_WB_MAGIC else AMR_NB_MAGIC
        // The frame start can only be within the first AMR_SCAN_LIMIT bytes, so only that
        // head is buffered — the payload itself is streamed and may be arbitrarily large.
        BufferedInputStream(FileInputStream(rawAmrData), DEFAULT_BUFFER_SIZE).use { input ->
            val head = ByteArray(AMR_SCAN_LIMIT)
            var headSize = 0
            while (headSize < head.size) {
                val read = input.read(head, headSize, head.size - headSize)
                if (read <= 0) break
                headSize += read
            }
            if (headSize <= 0) return false

            val frameStart = findFirstAmrFrame(head.copyOf(headSize), isWb)
            if (frameStart < 0) return false

            FileOutputStream(outputFile).use { fos ->
                fos.write(magic)
                fos.write(head, frameStart, headSize - frameStart)
                input.copyTo(fos, DEFAULT_BUFFER_SIZE)
            }
        }
        return outputFile.length() > magic.size
    }

    /**
     * Scans [data] for the first byte that looks like a valid AMR frame header and
     * returns its index, or -1 if none found in the first [AMR_SCAN_LIMIT] bytes.
     *
     * For both AMR-NB and AMR-WB the TOC byte layout is:
     *   `P | FT(4) | Q | P | P`  (bit 7 = padding/F-bit = 0 for single-frame files)
     * The top bit must be 0 (it is the continuation bit, 0 = last frame in the list).
     * FT for speech frames: NB 0–7, WB 0–8.  FT = 15 (NO_DATA) is also valid.
     */
    @Suppress("MagicNumber")
    internal fun findFirstAmrFrame(data: ByteArray, isWb: Boolean): Int {
        val maxFt = if (isWb) AMR_WB_MAX_SPEECH_FT else AMR_NB_MAX_SPEECH_FT
        val limit = minOf(data.size, AMR_SCAN_LIMIT)
        for (i in 0 until limit) {
            val b = data[i].toInt() and 0xFF
            if (b and 0x80 != 0) continue          // top bit must be 0
            val ft = (b ushr 3) and 0x0F
            if (ft <= maxFt || ft == AMR_FT_NO_DATA) return i
        }
        return -1
    }

    /**
     * Re-muxes a valid standalone AMR file into a 3GPP container using
     * MediaExtractor + MediaMuxer.
     *
     * @param amrFile   Valid AMR file (with magic header)
     * @param outputFile Destination 3GP file
     * @return [RestoreResult.Success] with duration in µs, or [RestoreResult.Failed]
     */
    private fun remuxAmrInto3gp(amrFile: File, outputFile: File): RestoreResult {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(amrFile.absolutePath)
            if (extractor.trackCount == 0) {
                return RestoreResult.Failed("3GP: MediaExtractor found no tracks in AMR file")
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return RestoreResult.Failed("3GP: no audio track in AMR file")
            }

            extractor.selectTrack(audioTrackIndex)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufSize = try {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                DEFAULT_BUFFER_SIZE
            }
            val buffer = java.nio.ByteBuffer.allocate(bufSize)
            val info = android.media.MediaCodec.BufferInfo()
            var lastTs = 0L
            var sampleCount = 0

            while (true) {
                val n = extractor.readSampleData(buffer, 0)
                if (n < 0) break
                info.offset = 0
                info.size = n
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                lastTs = extractor.sampleTime
                muxer.writeSampleData(muxerTrack, buffer, info)
                sampleCount++
                extractor.advance()
            }

            muxer.stop()
            muxer.release()

            if (sampleCount == 0) {
                outputFile.delete()
                return RestoreResult.Failed("3GP: no audio samples found in AMR file")
            }

            Timber.d("3GP: remuxed $sampleCount AMR frames, duration=${lastTs}μs")
            RestoreResult.Success(lastTs)
        } catch (e: Exception) {
            Timber.e(e, "3GP: remux into 3GP failed")
            outputFile.delete()
            RestoreResult.Failed("3GP remux failed: ${e.message}")
        } finally {
            extractor.release()
        }
    }

    // -------------------------------------------------------------------------
    // End of 3GP / AMR restoration
    // -------------------------------------------------------------------------

    /**
     * Last-resort restoration for an MPEG-4 file whose payload is a self-describing ADTS stream.
     *
     * This covers a file that carries an AAC elementary stream inside (or instead of) a broken
     * container - a `.aac` recording given an `.m4a` name, for instance. Every ADTS frame states
     * its own length, so the stream can be re-containerised without knowing anything else:
     * 1. Extract the payload from the broken file by locating the mdat atom.
     * 2. Rebuild a valid MPEG-4 container around it with MediaExtractor + MediaMuxer
     *    ([remuxAdtsIntoMp4]), which streams frame by frame and so uses a constant amount of
     *    heap regardless of how long the recording is.
     * 3. Only if the platform muxer cannot read the stream, fall back to mp4parser's
     *    AACTrackImpl + DefaultMp4Builder. That path holds every frame on the heap at once, so
     *    it is gated on [canAffordMp4ParserRebuild] to avoid an OutOfMemoryError.
     *
     * A payload of raw AAC-LC frames - what `MediaMuxer` and `MediaRecorder` actually write into
     * an `.m4a` mdat - is reported unrecoverable instead. Those frames carry no sync word and no
     * length field, so without the sample table from [AacFrameIndex] or a `moov` atom there is
     * nothing in the bitstream that marks where one ends: any split is a guess, and a wrong one
     * yields a file that reports a plausible duration while decoding to nothing.
     *
     * @param file The broken audio file
     * @return RestoreResult indicating success or failure
     */
    @Suppress("TooGenericExceptionCaught")
    private fun tryRestoreWithMp4Parser(file: File): RestoreResult {
        val tempAacFile = File(file.parent, "${file.nameWithoutExtension}_raw.aac")
        val tempMp4File = File(file.parent, "${file.nameWithoutExtension}_restored.${file.extension}")

        return try {
            // Step 1: Extract raw AAC data from the broken MPEG-4 file
            val extracted = extractMdatPayload(file, tempAacFile)
            if (!extracted) {
                return RestoreResult.Failed("Could not extract audio data from broken file")
            }

            Timber.d("Extracted raw AAC data: ${tempAacFile.length()} bytes from broken file: ${file.absolutePath}")

            // Step 2: AACTrackImpl and MediaExtractor both need ADTS framing (sync word 0xFFF at
            // the start of every frame) to find frame boundaries. Nothing else is recoverable.
            if (!hasAdtsHeader(tempAacFile)) {
                tempAacFile.delete()
                return RestoreResult.Failed(
                    "Audio data has no frame boundaries: the recording was interrupted before its " +
                            "sample table was written and no frame index was kept alongside it"
                )
            }

            // Step 3: Rebuild a valid MPEG-4 container around the ADTS stream.
            // Preferred path: MediaExtractor + MediaMuxer. It streams one frame at a time
            // through a single reusable buffer, so heap use is constant no matter how long
            // the recording is.
            val rebuilt = remuxAdtsIntoMp4(tempAacFile, tempMp4File)

            // Step 4: Fall back to mp4parser only if the platform muxer could not read the
            // stream. mp4parser materialises one Java object per AAC frame (see
            // MP4PARSER_HEAP_BYTES_PER_FRAME), so it is only attempted when the frame count
            // of this particular file fits in the heap we actually have left.
            if (!rebuilt) {
                tempMp4File.delete()
                if (!canAffordMp4ParserRebuild(tempAacFile)) {
                    tempAacFile.delete()
                    return RestoreResult.Failed(
                        "Recording is too long to rebuild with mp4parser without exhausting the heap"
                    )
                }

                val aacTrack = AACTrackImpl(FileDataSourceImpl(tempAacFile))

                val movie = Movie()
                movie.addTrack(aacTrack)

                val mp4Builder = DefaultMp4Builder()
                val container = mp4Builder.build(movie)

                FileOutputStream(tempMp4File).use { fos ->
                    container.writeContainer(fos.channel)
                }
            }

            // Step 5: Verify the restored file is readable
            val verifyDuration = tryReadWithExtractor(tempMp4File.absolutePath)
            if (verifyDuration == null || verifyDuration <= 0) {
                tempAacFile.delete()
                tempMp4File.delete()
                return RestoreResult.Failed("Restored file is not readable after mp4parser rebuild")
            }

            // Step 6: Replace the original file with the restored file
            replaceFile(tempMp4File, file)
            tempAacFile.delete()

            val strategy = if (rebuilt) "ADTS re-mux" else "mp4parser"
            Timber.d("File restored via $strategy: ${file.absolutePath}, duration: ${verifyDuration}μs")
            RestoreResult.Success(verifyDuration)
        } catch (e: Exception) {
            Timber.e(e, "mp4parser restoration failed for: ${file.absolutePath}")
            tempAacFile.delete()
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
     * Re-muxes an ADTS AAC stream into a valid MPEG-4 container using the platform
     * [MediaExtractor] + [MediaMuxer].
     *
     * This is the memory-safe way to rebuild the container: frames are copied one at a time
     * through a single reusable [ByteBuffer], and the sample tables are accumulated by the
     * native muxer rather than on the Java heap. Heap use is therefore independent of the
     * recording length, unlike the mp4parser path (see [canAffordMp4ParserRebuild]).
     *
     * @param adtsFile   Source ADTS AAC stream.
     * @param outputFile Destination MPEG-4 file. Left deleted if the re-mux fails.
     * @return true if an audio track was fully written, false if the stream could not be
     *         read or contained no samples.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun remuxAdtsIntoMp4(adtsFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            extractor.setDataSource(adtsFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                Timber.d("ADTS re-mux: no audio track found in ${adtsFile.absolutePath}")
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            // One ADTS frame can never exceed 8191 bytes, but honour KEY_MAX_INPUT_SIZE when
            // the extractor reports a larger value.
            val bufferSize = try {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                0
            }.coerceAtLeast(ADTS_REMUX_BUFFER_SIZE)
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var sampleCount = 0
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                // Every AAC frame is independently decodable.
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                sampleCount++

                extractor.advance()
            }

            if (sampleCount == 0) {
                Timber.d("ADTS re-mux: no samples read from ${adtsFile.absolutePath}")
                return false
            }

            muxer.stop()
            muxerStarted = false
            Timber.d("ADTS re-mux wrote $sampleCount samples to ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "ADTS re-mux failed for: ${adtsFile.absolutePath}")
            false
        } finally {
            if (muxerStarted) {
                // stop() was never reached — the muxer would otherwise throw on release().
                try { muxer?.stop() } catch (_: Throwable) {}
            }
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Decides whether rebuilding [adtsFile] with mp4parser can fit in the heap still available.
     *
     * mp4parser is not streaming: `AACTrackImpl` allocates one `Sample` object per ADTS frame
     * and keeps them all in a list, and `DefaultMp4Builder` then adds a per-frame entry to the
     * decoding-time and sample-size arrays plus the chunk list. That works out to roughly
     * [MP4PARSER_HEAP_BYTES_PER_FRAME] bytes of live heap per frame, which at ~43 frames per
     * second is several MB per hour of audio — enough to exhaust the default 128 MB heap on a
     * long recording and take the whole process down with an OutOfMemoryError (the crash may
     * then surface on any thread, typically in Compose recomposition rather than here).
     *
     * The frame count is estimated from the average `aac_frame_length` of the first
     * [ADTS_FRAMES_TO_SAMPLE] frames rather than assumed, because a stream of unusually short
     * frames would otherwise blow the estimate up by an order of magnitude — exactly the case
     * that must be rejected.
     *
     * @return true if the estimated cost stays under [MP4PARSER_HEAP_BUDGET_FRACTION] of the
     *         heap headroom, false if it does not or the stream could not be measured.
     */
    private fun canAffordMp4ParserRebuild(adtsFile: File): Boolean {
        val averageFrameLength = averageAdtsFrameLength(adtsFile)
        if (averageFrameLength == null || averageFrameLength <= 0) {
            Timber.w("Cannot measure ADTS frame size, refusing mp4parser rebuild: ${adtsFile.absolutePath}")
            return false
        }

        val estimatedFrames = adtsFile.length() / averageFrameLength
        val estimatedHeapBytes = estimatedFrames * MP4PARSER_HEAP_BYTES_PER_FRAME

        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val headroom = runtime.maxMemory() - usedHeap
        val budget = (headroom * MP4PARSER_HEAP_BUDGET_FRACTION).toLong()

        val affordable = estimatedHeapBytes < budget
        Timber.d(
            "mp4parser rebuild estimate: frames=$estimatedFrames (avg ${averageFrameLength}B), " +
                    "heap needed=${estimatedHeapBytes / 1024}KB, budget=${budget / 1024}KB, affordable=$affordable"
        )
        return affordable
    }

    /**
     * Reads the `aac_frame_length` field of up to [ADTS_FRAMES_TO_SAMPLE] leading frames of
     * [adtsFile] and returns their average size in bytes, walking the stream header-to-header.
     *
     * @return the average frame length, or null if the file does not start with a valid ADTS
     *         sync word or no complete frame could be read.
     */
    @Suppress("MagicNumber", "ReturnCount")
    internal fun averageAdtsFrameLength(adtsFile: File): Int? {
        return try {
            RandomAccessFile(adtsFile, "r").use { raf ->
                val fileLength = raf.length()
                val header = ByteArray(ADTS_HEADER_SIZE)
                var offset = 0L
                var frames = 0
                var totalLength = 0L

                while (frames < ADTS_FRAMES_TO_SAMPLE && offset + ADTS_HEADER_SIZE <= fileLength) {
                    raf.seek(offset)
                    raf.readFully(header)

                    // Sync word: 0xFF followed by 0xF in the high nibble of byte 1.
                    val b0 = header[0].toInt() and 0xFF
                    val b1 = header[1].toInt() and 0xFF
                    if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) break

                    // aac_frame_length is 13 bits spanning bytes 3..5.
                    val frameLength = ((header[3].toInt() and 0x03) shl 11) or
                            ((header[4].toInt() and 0xFF) shl 3) or
                            ((header[5].toInt() and 0xFF) ushr 5)
                    if (frameLength <= ADTS_HEADER_SIZE) break

                    totalLength += frameLength
                    offset += frameLength
                    frames++
                }

                if (frames == 0) null else (totalLength / frames).toInt()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to measure ADTS frame length: ${adtsFile.absolutePath}")
            null
        }
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
        return when {
            file.extension.equals("3gp", ignoreCase = true) ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
            // Opus recordings only exist on API 29+, where the Ogg muxer is available.
            file.extension.equals("opus", ignoreCase = true) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB

        /** Size of the standard RIFF/WAV header written by [WavRecorderV2] (no extra chunks). */
        private const val WAV_HEADER_SIZE = 44

        /** Size of a 7-byte ADTS header (no CRC, protection_absent = 1). */
        internal const val ADTS_HEADER_SIZE = 7

        /**
         * Copy buffer for [remuxAdtsIntoMp4]. A single ADTS frame is capped at 8191 bytes by
         * the 13-bit aac_frame_length field, so 8 KB always holds one frame.
         */
        private const val ADTS_REMUX_BUFFER_SIZE = 8 * 1024

        /**
         * Approximate live heap cost per AAC frame of an mp4parser rebuild: the anonymous
         * `Sample` instance held by `AACTrackImpl` (~40 B) and its list slot, plus the
         * per-frame entries `DefaultMp4Builder` adds to the decoding-time and sample-size
         * arrays and the chunk list. Deliberately rounded up — this is a safety budget.
         */
        private const val MP4PARSER_HEAP_BYTES_PER_FRAME = 64L

        /**
         * Share of the remaining heap an mp4parser rebuild is allowed to claim. Half leaves
         * room for the UI, the Room cache and GC headroom, all of which stay live while the
         * restore runs on a background dispatcher.
         */
        private const val MP4PARSER_HEAP_BUDGET_FRACTION = 0.5

        /**
         * How many leading ADTS frames [averageAdtsFrameLength] measures before extrapolating
         * to the whole file. 64 frames is ~1.5 s of audio: enough to smooth out VBR variation,
         * cheap enough to be a handful of seeks.
         */
        private const val ADTS_FRAMES_TO_SAMPLE = 64

        // -----------------------------------------------------------------
        // AMR constants
        // -----------------------------------------------------------------

        /** AMR-NB is recorded at 8 000 Hz; anything above this uses AMR-WB. */
        internal const val AMR_NB_SAMPLE_RATE = 8_000

        /** Magic header for a standalone AMR-NB file (RFC 4867 §5.1). */
        internal val AMR_NB_MAGIC = "#!AMR\n".toByteArray(Charsets.US_ASCII)

        /** Magic header for a standalone AMR-WB file (RFC 4867 §5.1). */
        internal val AMR_WB_MAGIC = "#!AMR-WB\n".toByteArray(Charsets.US_ASCII)

        /**
         * Maximum valid speech frame-type index for AMR-NB (FT 0–7).
         * FT 8–14 are reserved/comfort-noise; 15 = NO_DATA.
         */
        internal const val AMR_NB_MAX_SPEECH_FT = 7

        /**
         * Maximum valid speech frame-type index for AMR-WB (FT 0–8).
         * FT 9–14 are reserved/comfort-noise; 15 = NO_DATA.
         */
        internal const val AMR_WB_MAX_SPEECH_FT = 8

        /** Frame-type 15 = NO_DATA, valid in both NB and WB. */
        internal const val AMR_FT_NO_DATA = 15

        /**
         * How far into the raw mdat payload to scan for the first AMR frame header.
         * Container remnants (ftyp/mdat atom bytes) are typically at most a few dozen
         * bytes; 512 bytes gives ample margin without risking false positives deep in
         * the audio payload.
         */
        internal const val AMR_SCAN_LIMIT = 512
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
