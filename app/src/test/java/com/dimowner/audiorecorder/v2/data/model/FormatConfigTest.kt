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
package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatConfigTest {

    // -------------------------------------------------------------------------
    // RecordingFormat.config — supported lists per format
    // -------------------------------------------------------------------------

    @Test
    fun `M4a config supports all sample rates, all bitrates and both channel counts`() {
        val config = RecordingFormat.M4a.config

        assertEquals(RecordingFormat.M4a, config.format)
        assertEquals(listOf(
            SampleRate.SR8000,
            SampleRate.SR16000,
            SampleRate.SR22050,
            SampleRate.SR32000,
            SampleRate.SR44100,
            SampleRate.SR48000
        ), config.supportedSampleRates)
        assertEquals(listOf(
            BitRate.BR48,
            BitRate.BR96,
            BitRate.BR128,
            BitRate.BR192,
            BitRate.BR256,
            BitRate.BR288,
        ), config.supportedBitRates)
        assertEquals(
            listOf(ChannelCount.Mono, ChannelCount.Stereo),
            config.supportedChannelCounts
        )
    }

    @Test
    fun `Wav config supports all sample rates, no bitrates and both channel counts`() {
        val config = RecordingFormat.Wav.config

        assertEquals(RecordingFormat.Wav, config.format)
        assertEquals(listOf(
            SampleRate.SR8000,
            SampleRate.SR16000,
            SampleRate.SR22050,
            SampleRate.SR32000,
            SampleRate.SR44100,
            SampleRate.SR48000
        ), config.supportedSampleRates)
        assertTrue(config.supportedBitRates.isEmpty())
        assertEquals(
            listOf(ChannelCount.Mono, ChannelCount.Stereo),
            config.supportedChannelCounts
        )
    }

    @Test
    fun `ThreeGp config supports only AMR sample rates, no bitrates and mono only`() {
        val config = RecordingFormat.ThreeGp.config

        assertEquals(RecordingFormat.ThreeGp, config.format)
        assertEquals(
            listOf(SampleRate.SR8000, SampleRate.SR16000),
            config.supportedSampleRates
        )
        assertTrue(config.supportedBitRates.isEmpty())
        assertEquals(
            listOf(ChannelCount.Mono),
            config.supportedChannelCounts
        )
    }

    // -------------------------------------------------------------------------
    // hasBitrate
    // -------------------------------------------------------------------------

    @Test
    fun `hasBitrate is true only for M4a`() {
        assertTrue(RecordingFormat.M4a.config.hasBitrate)
        assertFalse(RecordingFormat.Wav.config.hasBitrate)
        assertFalse(RecordingFormat.ThreeGp.config.hasBitrate)
    }

    @Test
    fun `RecordingFormat hasBitrate delegates to its config`() {
        RecordingFormat.entries.forEach { format ->
            assertEquals(format.config.hasBitrate, format.hasBitrate)
        }
    }

    // -------------------------------------------------------------------------
    // isSampleRateSupported
    // -------------------------------------------------------------------------

    @Test
    fun `isSampleRateSupported reflects the supported list`() {
        val m4a = RecordingFormat.M4a.config
        assertTrue(m4a.isSampleRateSupported(SampleRate.SR44100))

        val threeGp = RecordingFormat.ThreeGp.config
        assertTrue(threeGp.isSampleRateSupported(SampleRate.SR8000))
        assertTrue(threeGp.isSampleRateSupported(SampleRate.SR16000))
        assertFalse(threeGp.isSampleRateSupported(SampleRate.SR44100))
    }

    // -------------------------------------------------------------------------
    // isBitRateSupported
    // -------------------------------------------------------------------------

    @Test
    fun `isBitRateSupported is true for M4a bitrates and false for formats without bitrate`() {
        assertTrue(RecordingFormat.M4a.config.isBitRateSupported(BitRate.BR128))
        assertTrue(RecordingFormat.M4a.config.isBitRateSupported(BitRate.BR288))

        assertFalse(RecordingFormat.Wav.config.isBitRateSupported(BitRate.BR128))
        assertFalse(RecordingFormat.ThreeGp.config.isBitRateSupported(BitRate.BR128))
    }

    // -------------------------------------------------------------------------
    // isChannelCountSupported
    // -------------------------------------------------------------------------

    @Test
    fun `isChannelCountSupported reflects the supported list`() {
        val m4a = RecordingFormat.M4a.config
        assertTrue(m4a.isChannelCountSupported(ChannelCount.Mono))
        assertTrue(m4a.isChannelCountSupported(ChannelCount.Stereo))

        val threeGp = RecordingFormat.ThreeGp.config
        assertTrue(threeGp.isChannelCountSupported(ChannelCount.Mono))
        assertFalse(threeGp.isChannelCountSupported(ChannelCount.Stereo))
    }
}
