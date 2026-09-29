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
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [AacFrameIndex], the sidecar sample table that makes an interrupted `.m4a`
 * recoverable at all: raw AAC-LC frames carry no sync word, so once `MediaMuxer` fails to write
 * `moov` these sizes are the only record of where one access unit ends and the next begins.
 *
 * The cases that matter are the ones a force-kill actually produces — an index that was never
 * closed, and one whose header did not finish being written.
 */
class AacFrameIndexTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val csd0 = byteArrayOf(0x12, 0x10)

    private fun recordFile(name: String = "Record-75.m4a"): File = tempFolder.newFile(name)

    private fun readAllSizes(reader: AacFrameIndex.Reader): List<Int> {
        val sizes = mutableListOf<Int>()
        while (true) {
            val size = reader.nextFrameSize()
            if (size <= 0) break
            sizes.add(size)
        }
        return sizes
    }

    @Test
    fun `sidecar sits next to the recording and stays hidden`() {
        val record = recordFile()

        val sidecar = AacFrameIndex.sidecarFile(record)

        assertEquals(record.parentFile, sidecar.parentFile)
        assertEquals(".Record-75.m4a.aacidx", sidecar.name)
    }

    @Test
    fun `frame sizes survive a writer that is closed normally`() {
        val record = recordFile()
        val sizes = listOf(371, 372, 517, 8191, 1)

        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        sizes.forEach(writer::append)
        writer.close()

        val reader = AacFrameIndex.openReader(record)!!
        assertEquals(44100, reader.sampleRate)
        assertEquals(2, reader.channelCount)
        assertTrue(csd0.contentEquals(reader.csd0))
        assertEquals(sizes, readAllSizes(reader))
        reader.close()
    }

    @Test
    fun `entries flushed before a kill are readable from an index that was never closed`() {
        val record = recordFile()
        // Only whole flush intervals reach the disk when the process dies mid-recording; the tail
        // is expendable because the muxer buffers its own mdat writes too.
        val flushedFrames = 64

        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        repeat(flushedFrames) { writer.append(400) }
        // Deliberately no close(): this is what a force-kill leaves behind.

        val reader = AacFrameIndex.openReader(record)!!
        assertEquals(
            "Every flushed frame must be recoverable",
            flushedFrames,
            readAllSizes(reader).size,
        )
        reader.close()
    }

    @Test
    fun `reader rejects an index whose header never finished being written`() {
        val record = recordFile()
        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        writer.append(400)
        writer.close()

        val sidecar = AacFrameIndex.sidecarFile(record)
        sidecar.writeBytes(sidecar.readBytes().copyOf(5))

        assertNull("A truncated header must not be trusted", AacFrameIndex.openReader(record))
    }

    @Test
    fun `reader rejects an index written by another version`() {
        val record = recordFile()
        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        writer.append(400)
        writer.close()

        val sidecar = AacFrameIndex.sidecarFile(record)
        val bytes = sidecar.readBytes()
        bytes[6] = 99 // version byte
        sidecar.writeBytes(bytes)

        assertNull("An unknown layout must not be parsed", AacFrameIndex.openReader(record))
    }

    @Test
    fun `reader reports no index when the recording never had one`() {
        assertNull(AacFrameIndex.openReader(recordFile()))
    }

    @Test
    fun `writer skips sizes no AAC access unit can have`() {
        val record = recordFile()

        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        writer.append(0)
        writer.append(-1)
        writer.append(AacFrameIndex.MAX_FRAME_SIZE + 1)
        writer.append(371)
        writer.close()

        val reader = AacFrameIndex.openReader(record)!!
        assertEquals(listOf(371), readAllSizes(reader))
        reader.close()
    }

    @Test
    fun `delete removes the sidecar and tolerates its absence`() {
        val record = recordFile()
        AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 1, csd0 = csd0)!!.close()
        assertTrue(AacFrameIndex.sidecarFile(record).exists())

        AacFrameIndex.delete(record)
        assertFalse(AacFrameIndex.sidecarFile(record).exists())

        AacFrameIndex.delete(record)
        assertFalse(AacFrameIndex.sidecarFile(record).exists())
    }

    @Test
    fun `an index of a long recording stays small`() {
        val record = recordFile()
        // An AAC-LC access unit carries 1024 samples, so ~43 of them per second at 44.1 kHz.
        val framesPerHour = 3600 * 44100 / 1024

        val writer = AacFrameIndex.openWriter(record, sampleRate = 44100, channelCount = 2, csd0 = csd0)!!
        repeat(framesPerHour) { writer.append(371) }
        writer.close()

        val sidecarSize = AacFrameIndex.sidecarFile(record).length()
        assertNotNull(AacFrameIndex.openReader(record))
        assertTrue(
            "An hour of index must stay well under a megabyte, was $sidecarSize bytes",
            sidecarSize < 512 * 1024,
        )
    }
}
