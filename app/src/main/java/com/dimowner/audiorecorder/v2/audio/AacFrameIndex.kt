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

import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Sidecar sample table for an in-progress `.m4a` recording.
 *
 * An MPEG-4 container keeps its sample table (`stsz`) in the `moov` atom, which `MediaMuxer`
 * only writes when the recording is stopped. If the process is killed first, the `mdat` atom
 * still holds every encoded AAC access unit, but nothing on disk says where one ends and the
 * next begins — and raw AAC-LC frames carry no sync word, so those boundaries cannot be
 * recovered from the bitstream afterwards. Without them the payload is undecodable.
 *
 * [AacCodecRecorderV2] therefore mirrors the sample sizes into this sidecar as it muxes, which
 * lets [BrokenRecordRestorer] rebuild an exact container after a force-kill. The file is deleted
 * as soon as the recording finishes normally, so it only ever exists while a recording is live.
 *
 * File layout (big-endian, matching `DataOutputStream`):
 * ```
 * "AACIDX" + version(1) + reserved(1)   8 bytes
 * sampleRate                            int32
 * channelCount                          int32
 * csd0Length                            int32
 * csd0 (AudioSpecificConfig)            csd0Length bytes
 * frameSize per access unit             uint16, repeated to EOF
 * ```
 * A `uint16` covers every AAC access unit: the format caps one at [MAX_FRAME_SIZE] bytes.
 */
internal object AacFrameIndex {

    /** File-format marker; [VERSION] is bumped if the layout below ever changes. */
    private val MAGIC = "AACIDX".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val RESERVED = 0

    /** Suffix appended to the recording's own file name, prefixed with a dot to stay hidden. */
    private const val SIDECAR_SUFFIX = ".aacidx"

    /**
     * Largest access unit the index can describe. The ADTS `aac_frame_length` field is 13 bits,
     * so no AAC frame can exceed 8191 bytes including its 7-byte header.
     */
    const val MAX_FRAME_SIZE = 8191

    /** The sidecar that belongs to [mediaFile], e.g. `Record-75.m4a` -> `.Record-75.m4a.aacidx`. */
    fun sidecarFile(mediaFile: File): File = File(mediaFile.parentFile, ".${mediaFile.name}$SIDECAR_SUFFIX")

    /** Removes the sidecar of [mediaFile] if one is left over. Never throws. */
    fun delete(mediaFile: File) {
        runCatching { sidecarFile(mediaFile).delete() }
            .onFailure { Timber.w(it, "Failed to delete AAC frame index for ${mediaFile.name}") }
    }

    /**
     * Starts a sidecar for [mediaFile]. Returns null if it cannot be created — indexing is a
     * recovery aid, so a failure here must never stop the recording it belongs to.
     *
     * @param csd0 the encoder's `csd-0` (AudioSpecificConfig), replayed verbatim into the
     *             rebuilt track so the restored file decodes with the original configuration.
     */
    fun openWriter(mediaFile: File, sampleRate: Int, channelCount: Int, csd0: ByteArray): Writer? {
        return try {
            val out = DataOutputStream(BufferedOutputStream(FileOutputStream(sidecarFile(mediaFile))))
            out.write(MAGIC)
            out.writeByte(VERSION)
            out.writeByte(RESERVED)
            out.writeInt(sampleRate)
            out.writeInt(channelCount)
            out.writeInt(csd0.size)
            out.write(csd0)
            out.flush()
            Writer(out)
        } catch (e: IOException) {
            Timber.w(e, "Could not open AAC frame index for ${mediaFile.name}")
            null
        }
    }

    /**
     * Opens the sidecar of [mediaFile] for reading, or returns null when it is absent, truncated
     * or written by an incompatible version.
     */
    fun openReader(mediaFile: File): Reader? {
        val sidecar = sidecarFile(mediaFile)
        if (!sidecar.exists()) return null
        var input: DataInputStream? = null
        return try {
            input = DataInputStream(FileInputStream(sidecar).buffered())
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            val version = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            if (!magic.contentEquals(MAGIC) || version != VERSION) {
                Timber.w("AAC frame index of ${mediaFile.name} has an unexpected header")
                input.close()
                return null
            }
            val sampleRate = input.readInt()
            val channelCount = input.readInt()
            val csd0Length = input.readInt()
            if (sampleRate <= 0 || channelCount <= 0 || csd0Length !in 1..MAX_CSD0_SIZE) {
                Timber.w("AAC frame index of ${mediaFile.name} carries an implausible header")
                input.close()
                return null
            }
            val csd0 = ByteArray(csd0Length)
            input.readFully(csd0)
            Reader(input, sampleRate, channelCount, csd0)
        } catch (e: IOException) {
            // A force-kill can land between creating the sidecar and finishing its header.
            Timber.d("AAC frame index of ${mediaFile.name} is unreadable: ${e.message}")
            runCatching { input?.close() }
            null
        }
    }

    /** Sanity bound on `csd-0`; an AudioSpecificConfig for AAC-LC is a handful of bytes. */
    private const val MAX_CSD0_SIZE = 1024

    /**
     * Appends one entry per muxed access unit.
     *
     * Entries are flushed every [FLUSH_INTERVAL_FRAMES] frames so that at most a fraction of a
     * second of index is lost when the process dies. Losing the tail is harmless anyway — the
     * muxer buffers `mdat` writes too, so the restorer stops at whichever of the two runs out
     * first — but an index that lags the payload would throw away recorded audio.
     */
    class Writer(private val out: DataOutputStream) {

        private var pendingFrames = 0

        fun append(frameSize: Int) {
            if (frameSize !in 1..MAX_FRAME_SIZE) return
            try {
                out.writeShort(frameSize)
                if (++pendingFrames >= FLUSH_INTERVAL_FRAMES) {
                    out.flush()
                    pendingFrames = 0
                }
            } catch (e: IOException) {
                Timber.w(e, "Failed to append to the AAC frame index")
            }
        }

        fun close() {
            runCatching { out.close() }
        }

        private companion object {
            /** ~0.75 s of audio at 44.1 kHz: 64 bytes of index per flush. */
            const val FLUSH_INTERVAL_FRAMES = 32
        }
    }

    /** Streams the sidecar back: header fields plus the frame sizes in muxing order. */
    class Reader(
        private val input: DataInputStream,
        val sampleRate: Int,
        val channelCount: Int,
        val csd0: ByteArray,
    ) {
        /** Size of the next access unit, or -1 at the end of the index. */
        fun nextFrameSize(): Int = try {
            val size = input.readUnsignedShort()
            if (size in 1..MAX_FRAME_SIZE) size else -1
        } catch (_: IOException) {
            -1
        }

        fun close() {
            runCatching { input.close() }
        }
    }
}
