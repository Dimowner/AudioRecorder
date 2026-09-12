/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind [AacCodecRecorderV2]. The recorder itself needs `MediaCodec` and
 * `AudioRecord`, so it is covered by the instrumented tests instead.
 */
class AacCodecRecorderHelpersTest {

    // -------------------------------------------------------------------------
    // aacPtsUs — the timeline written into the container
    // -------------------------------------------------------------------------

    @Test
    fun `first frame starts the timeline at zero`() {
        assertEquals(0L, aacPtsUs(frameIndex = 0, sampleRate = 48000))
    }

    @Test
    fun `one frame covers 1024 samples`() {
        // 1024 samples at 48 kHz = 21333.33 us, truncated.
        assertEquals(21_333L, aacPtsUs(frameIndex = 1, sampleRate = 48000))
        // 1024 samples at 16 kHz = 64 ms exactly.
        assertEquals(64_000L, aacPtsUs(frameIndex = 1, sampleRate = 16000))
    }

    @Test
    fun `timestamps land on the second at 48 kHz`() {
        // 48000 / 1024 * 1000 s: frame 46875 is exactly 1000 s in.
        assertEquals(1_000_000_000L, aacPtsUs(frameIndex = 46_875, sampleRate = 48000))
    }

    @Test
    fun `timestamps are strictly monotonic`() {
        var previous = -1L
        for (frame in 0L until 100_000L) {
            val pts = aacPtsUs(frame, sampleRate = 44100)
            assertTrue("pts went backwards at frame $frame", pts > previous)
            previous = pts
        }
    }

    @Test
    fun `a day of recording does not overflow`() {
        // 24 h at 48 kHz is exactly 4.05 million frames, and stays a whole day in microseconds.
        val frames = 24L * 60 * 60 * 48000 / 1024
        assertEquals(4_050_000L, frames)
        assertEquals(86_400_000_000L, aacPtsUs(frames, sampleRate = 48000))
    }

    // -------------------------------------------------------------------------
    // pcmDurationMills
    // -------------------------------------------------------------------------

    @Test
    fun `duration follows the PCM actually captured`() {
        assertEquals(0L, pcmDurationMills(framesFed = 0, sampleRate = 48000))
        assertEquals(1000L, pcmDurationMills(framesFed = 48000, sampleRate = 48000))
        assertEquals(500L, pcmDurationMills(framesFed = 22050, sampleRate = 44100))
    }

    // -------------------------------------------------------------------------
    // clampAacBitRate
    // -------------------------------------------------------------------------

    @Test
    fun `a reachable bitrate is left alone`() {
        assertEquals(
            192_000,
            clampAacBitRate(requested = 192_000, sampleRate = 48000, channelCount = 2, codecUpper = 960_000)
        )
    }

    @Test
    fun `the AAC-LC frame ceiling caps low sample rates`() {
        // 6 bits per sample and channel: 16 kHz mono can not exceed 96 kbps.
        assertEquals(
            96_000,
            clampAacBitRate(requested = 192_000, sampleRate = 16000, channelCount = 1, codecUpper = 960_000)
        )
        assertEquals(
            48_000,
            clampAacBitRate(requested = 288_000, sampleRate = 8000, channelCount = 1, codecUpper = 960_000)
        )
    }

    @Test
    fun `the encoder range caps the request too`() {
        assertEquals(
            64_000,
            clampAacBitRate(requested = 192_000, sampleRate = 48000, channelCount = 2, codecUpper = 64_000)
        )
    }

    @Test
    fun `the result is never zero or negative`() {
        assertTrue(clampAacBitRate(requested = 0, sampleRate = 48000, channelCount = 2, codecUpper = 960_000) > 0)
    }
}
