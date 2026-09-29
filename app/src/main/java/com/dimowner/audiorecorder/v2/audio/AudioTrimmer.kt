package com.dimowner.audiorecorder.v2.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

private const val TRIM_BUFFER_SIZE = 256 * 1024

@Singleton
class AudioTrimmer @Inject constructor() {

    data class TrimResult(
        val success: Boolean,
        val outputPath: String = "",
        val durationMills: Long = 0L,
        val size: Long = 0L,
        val error: String? = null,
    )

    fun trim(sourcePath: String, startMills: Long, endMills: Long): TrimResult {
        if (startMills < 0L || endMills <= startMills) {
            return TrimResult(success = false, error = "Invalid trim range")
        }

        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return TrimResult(success = false, error = "Source file not found or unreadable")
        }

        return when (sourceFile.extension.lowercase()) {
            "wav" -> trimWav(sourceFile, startMills, endMills)
            else -> remux(sourceFile, startMills, endMills)
        }
    }

    private fun remux(sourceFile: File, startMills: Long, endMills: Long): TrimResult {
        val startUs = startMills * 1000
        val endUs = endMills * 1000
        val tempFile = createTempFile(sourceFile)

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            extractor.setDataSource(sourceFile.absolutePath)

            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                return TrimResult(success = false, error = "No audio track found")
            }
            val audioFormat = extractor.getTrackFormat(audioTrackIndex)
            extractor.selectTrack(audioTrackIndex)

            val outputFormat = determineOutputFormat(sourceFile)
            muxer = MediaMuxer(tempFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(TRIM_BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var lastTimestamp = 0L
            var sampleCount = 0
            var skippedAtStart = true

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime >= endUs) break

                if (skippedAtStart && sampleTime < startUs) {
                    extractor.advance()
                    continue
                }
                skippedAtStart = false

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTime - startUs
                bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                lastTimestamp = sampleTime - startUs
                sampleCount++

                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            extractor.release()

            if (sampleCount == 0) {
                tempFile.delete()
                return TrimResult(success = false, error = "No samples in trim range")
            }

            val durationMills = endUs - startUs
            TrimResult(
                success = true,
                outputPath = tempFile.absolutePath,
                durationMills = durationMills / 1000,
                size = tempFile.length(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to trim (remux) file: ${sourceFile.absolutePath}")
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
            tempFile.delete()
            TrimResult(success = false, error = e.message ?: "Trim failed")
        }
    }

    @Suppress("MagicNumber")
    private fun trimWav(sourceFile: File, startMills: Long, endMills: Long): TrimResult {
        var tempFile: File? = null
        return try {
            val extractor = MediaExtractor()
            var sampleRate = 0
            var channelCount = 1
            try {
                extractor.setDataSource(sourceFile.absolutePath)
                val trackIdx = findAudioTrack(extractor)
                if (trackIdx >= 0) {
                    val format = extractor.getTrackFormat(trackIdx)
                    sampleRate = try {
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } catch (_: Exception) { 0 }
                    channelCount = try {
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } catch (_: Exception) { 1 }
                }
            } finally {
                extractor.release()
            }

            if (sampleRate <= 0) {
                return TrimResult(success = false, error = "Cannot read WAV sample rate")
            }

            val bitsPerSample = 16
            val bytesPerSample = bitsPerSample / 8
            val bytesPerSecond = sampleRate * channelCount * bytesPerSample

            val dataOffset = findWavDataOffset(sourceFile)
                .takeIf { it >= 0 } ?: 44

            val startByte = dataOffset + startMills * bytesPerSecond / 1000L
            val endByte = dataOffset + endMills * bytesPerSecond / 1000L
            val clampedEndByte = minOf(endByte, sourceFile.length())

            if (startByte >= clampedEndByte) {
                return TrimResult(success = false, error = "Trim range has no PCM data")
            }

            tempFile = createTempFile(sourceFile)
            RandomAccessFile(sourceFile, "r").use { input ->
                FileOutputStream(tempFile).use { output ->
                    val totalAudioLen = clampedEndByte - startByte
                    val totalDataLen = totalAudioLen + 36
                    val byteRate = bytesPerSecond.toLong()
                    val header = createWavHeader(
                        totalAudioLen = totalAudioLen,
                        totalDataLen = totalDataLen,
                        sampleRate = sampleRate,
                        channels = channelCount,
                        byteRate = byteRate,
                    )
                    output.write(header)

                    input.seek(startByte)
                    val buffer = ByteArray(TRIM_BUFFER_SIZE)
                    var remaining = clampedEndByte - startByte
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead <= 0) break
                        output.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                }
            }

            val trimmedDurationMills = (clampedEndByte - startByte) * 1000L / bytesPerSecond
            TrimResult(
                success = true,
                outputPath = tempFile.absolutePath,
                durationMills = trimmedDurationMills,
                size = tempFile.length(),
            )
        } catch (e: Exception) {
            tempFile?.delete()
            Timber.e(e, "Failed to trim WAV file: ${sourceFile.absolutePath}")
            TrimResult(success = false, error = e.message ?: "WAV trim failed")
        }
    }

    @Suppress("MagicNumber")
    private fun findWavDataOffset(file: File): Long {
        val raf = RandomAccessFile(file, "r")
        return try {
            if (raf.length() < 12) return -1L

            val riff = ByteArray(4)
            raf.readFully(riff)
            if (riff[0] != 'R'.code.toByte() || riff[1] != 'I'.code.toByte() ||
                riff[2] != 'F'.code.toByte() || riff[3] != 'F'.code.toByte()
            ) return -1L

            raf.readInt()
            val wave = ByteArray(4)
            raf.readFully(wave)
            if (wave[0] != 'W'.code.toByte() || wave[1] != 'A'.code.toByte() ||
                wave[2] != 'V'.code.toByte() || wave[3] != 'E'.code.toByte()
            ) return -1L

            while (raf.filePointer < raf.length() - 8) {
                val chunkStart = raf.filePointer
                val chunkId = ByteArray(4)
                raf.readFully(chunkId)
                val chunkSize = raf.readInt()

                if (chunkId[0] == 'd'.code.toByte() && chunkId[1] == 'a'.code.toByte() &&
                    chunkId[2] == 't'.code.toByte() && chunkId[3] == 'a'.code.toByte()
                ) {
                    return raf.filePointer
                }

                raf.seek(chunkStart + 8 + chunkSize + (chunkSize and 1))
            }
            -1L
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WAV header: ${file.absolutePath}")
            -1L
        } finally {
            raf.close()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    private fun determineOutputFormat(file: File): Int {
        return when (file.extension.lowercase()) {
            "3gp" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
    }

    private fun createTempFile(sourceFile: File): File {
        val parent = sourceFile.parentFile ?: return File(sourceFile.parent, "${sourceFile.nameWithoutExtension}_trim.${sourceFile.extension}")
        return File(parent, "${sourceFile.nameWithoutExtension}_trim.${sourceFile.extension}")
    }
}
