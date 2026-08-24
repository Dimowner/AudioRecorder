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

import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the arithmetic and the learning rules of [DeviceRecordingCapabilities]. Reading the
 * device media profile and the codec list needs a real device, so [DeviceRecordingCapabilities.detect]
 * is not exercised here.
 */
class DeviceRecordingCapabilitiesTest {

    private lateinit var prefs: PrefsV2
    private lateinit var capabilities: DeviceRecordingCapabilities

    private var storedLimit = 0
    private var storedMeasured = false

    @Before
    fun setup() {
        storedLimit = 0
        storedMeasured = false
        prefs = mockk(relaxed = true)
        every { prefs.deviceM4aMaxBitRate } answers { storedLimit }
        every { prefs.deviceM4aMaxBitRate = any() } answers { storedLimit = firstArg() }
        every { prefs.isDeviceMaxBitRateMeasured } answers { storedMeasured }
        every { prefs.isDeviceMaxBitRateMeasured = any() } answers { storedMeasured = firstArg() }
        capabilities = DeviceRecordingCapabilities(prefs)
    }

    // -------------------------------------------------------------------------
    // maxBitRate
    // -------------------------------------------------------------------------

    @Test
    fun `AAC frame ceiling limits the bitrate to 6 bits per sample and channel`() {
        // 16 kHz mono can not carry more than 6 * 16000 = 96 kbps, whatever the encoder is asked for.
        assertEquals(
            96_000,
            capabilities.maxBitRate(RecordingFormat.M4a, SampleRate.SR16000, ChannelCount.Mono)
        )
        assertEquals(
            192_000,
            capabilities.maxBitRate(RecordingFormat.M4a, SampleRate.SR16000, ChannelCount.Stereo)
        )
        assertEquals(
            576_000,
            capabilities.maxBitRate(RecordingFormat.M4a, SampleRate.SR48000, ChannelCount.Stereo)
        )
    }

    @Test
    fun `known device limit wins over the frame ceiling`() {
        storedLimit = 96_000

        assertEquals(
            96_000,
            capabilities.maxBitRate(RecordingFormat.M4a, SampleRate.SR48000, ChannelCount.Stereo)
        )
    }

    @Test
    fun `formats without a configurable bitrate are not limited`() {
        storedLimit = 96_000

        assertEquals(
            BIT_RATE_UNLIMITED,
            capabilities.maxBitRate(RecordingFormat.Wav, SampleRate.SR48000, ChannelCount.Stereo)
        )
        assertEquals(
            BIT_RATE_UNLIMITED,
            capabilities.maxBitRate(RecordingFormat.ThreeGp, SampleRate.SR16000, ChannelCount.Mono)
        )
    }

    // -------------------------------------------------------------------------
    // learnFromRecording
    // -------------------------------------------------------------------------

    @Test
    fun `a clipped recording is remembered as the device limit`() {
        // 192 kbps requested at 48 kHz stereo, 96 kbps in the file: the device clipped it.
        capabilities.learnFromRecording(
            format = RecordingFormat.M4a,
            requestedBitRate = 192_000,
            measuredBitRate = 96_331,
            sampleRate = 48_000,
            channelCount = 2,
        )

        assertEquals(96_000, storedLimit)
        assertTrue(storedMeasured)
        assertEquals(
            96_000,
            capabilities.maxBitRate(RecordingFormat.M4a, SampleRate.SR48000, ChannelCount.Stereo)
        )
    }

    @Test
    fun `a drop the sample rate already explains is not treated as a device limit`() {
        // 16 kHz mono can not exceed 96 kbps, so 96 kbps out of 192 kbps requested says nothing
        // about what the device can do at higher sample rates.
        capabilities.learnFromRecording(
            format = RecordingFormat.M4a,
            requestedBitRate = 192_000,
            measuredBitRate = 96_000,
            sampleRate = 16_000,
            channelCount = 1,
        )

        assertEquals(0, storedLimit)
        assertFalse(storedMeasured)
    }

    @Test
    fun `an honoured recording leaves the limit alone`() {
        capabilities.learnFromRecording(
            format = RecordingFormat.M4a,
            requestedBitRate = 192_000,
            measuredBitRate = 190_500,
            sampleRate = 48_000,
            channelCount = 2,
        )

        assertEquals(0, storedLimit)
        assertFalse(storedMeasured)
    }

    @Test
    fun `an honoured recording above a guessed limit raises it`() {
        storedLimit = 96_000

        capabilities.learnFromRecording(
            format = RecordingFormat.M4a,
            requestedBitRate = 128_000,
            measuredBitRate = 127_000,
            sampleRate = 44_100,
            channelCount = 2,
        )

        assertEquals(128_000, storedLimit)
        assertTrue(storedMeasured)
    }

    @Test
    fun `recordings of formats without a configurable bitrate are ignored`() {
        capabilities.learnFromRecording(
            format = RecordingFormat.ThreeGp,
            requestedBitRate = 96_000,
            measuredBitRate = 12_000,
            sampleRate = 16_000,
            channelCount = 1,
        )

        assertEquals(0, storedLimit)
        assertFalse(storedMeasured)
    }

    @Test
    fun `unreadable bitrates are ignored`() {
        capabilities.learnFromRecording(
            format = RecordingFormat.M4a,
            requestedBitRate = 192_000,
            measuredBitRate = 0,
            sampleRate = 48_000,
            channelCount = 2,
        )

        assertEquals(0, storedLimit)
        assertFalse(storedMeasured)
    }
}
