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
package com.dimowner.audiorecorder.v2.app.settings

import android.content.Context
import android.os.Parcelable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Exercises [SettingsViewModel.selectRecordingFormat] which drives the private
 * `adjustSelectionToFormat` logic: when switching formats, any persisted sample rate / channel
 * count / bitrate that is not supported by the new format must be replaced with that format's
 * default, persisted, and reflected in the rebuilt chips.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class SettingsViewModelAdjustFormatTest {

    private lateinit var prefs: PrefsV2
    private lateinit var recordsDataSource: RecordsDataSource
    private lateinit var fileDataSource: FileDataSource
    private lateinit var audioPlayer: PlayerContractNew.Player
    private lateinit var audioRecorderDelegate: AudioRecorderDelegate
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var context: Context

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        recordsDataSource = mockk(relaxed = true)
        fileDataSource = mockk(relaxed = true)
        audioPlayer = mockk(relaxed = true)
        audioRecorderDelegate = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Stubs the four recording-setting preferences with in-memory backing so reads return the last
     * written value, then constructs the view model (which reads these prefs in its constructor).
     */
    private fun createViewModel(
        format: RecordingFormat,
        sampleRate: SampleRate,
        bitRate: BitRate,
        channelCount: ChannelCount,
    ): SettingsViewModel {
        var fmt = format
        var sr = sampleRate
        var br = bitRate
        var cc = channelCount
        every { prefs.settingRecordingFormat } answers { fmt }
        every { prefs.settingRecordingFormat = any() } answers { fmt = firstArg() }
        every { prefs.settingSampleRate } answers { sr }
        every { prefs.settingSampleRate = any() } answers { sr = firstArg() }
        every { prefs.settingBitrate } answers { br }
        every { prefs.settingBitrate = any() } answers { br = firstArg() }
        every { prefs.settingChannelCount } answers { cc }
        every { prefs.settingChannelCount = any() } answers { cc = firstArg() }
        return SettingsViewModel(
            prefs = prefs,
            recordsDataSource = recordsDataSource,
            fileDataSource = fileDataSource,
            audioPlayer = audioPlayer,
            audioRecorderDelegate = audioRecorderDelegate,
            analyticsTracker = analyticsTracker,
            mainDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            context = context,
        )
    }

    private fun SettingsViewModel.settingFor(format: RecordingFormat) =
        state.value.recordingSettings.first { it.recordingFormat.value == format }

    private fun <T : Parcelable> List<ChipItem<T>>.selected(): T? =
        firstOrNull { it.isSelected }?.value

    // -------------------------------------------------------------------------
    // Switching to a more restrictive format (3GP)
    // -------------------------------------------------------------------------

    @Test
    fun `switching to ThreeGp replaces unsupported sample rate and channel count with 3gp defaults`() {
        val viewModel = createViewModel(
            format = RecordingFormat.M4a,
            sampleRate = SampleRate.SR44100, // unsupported by 3GP
            bitRate = BitRate.BR128,
            channelCount = ChannelCount.Stereo, // unsupported by 3GP
        )

        viewModel.selectRecordingFormat(RecordingFormat.ThreeGp)

        assertEquals(RecordingFormat.ThreeGp, prefs.settingRecordingFormat)
        assertEquals(DefaultValues.Default3GpSampleRate, prefs.settingSampleRate)
        assertEquals(DefaultValues.Default3GpChannelCount, prefs.settingChannelCount)

        val setting = viewModel.settingFor(RecordingFormat.ThreeGp)
        assertEquals(DefaultValues.Default3GpSampleRate, setting.sampleRates.selected())
        assertEquals(DefaultValues.Default3GpChannelCount, setting.channelCounts.selected())
        assertTrue("3GP must expose no bitrate chips", setting.bitRates.isEmpty())
    }

    @Test
    fun `switching to ThreeGp keeps already supported sample rate and channel count`() {
        val viewModel = createViewModel(
            format = RecordingFormat.M4a,
            sampleRate = SampleRate.SR8000, // supported by 3GP
            bitRate = BitRate.BR128,
            channelCount = ChannelCount.Mono, // supported by 3GP
        )

        viewModel.selectRecordingFormat(RecordingFormat.ThreeGp)

        assertEquals(SampleRate.SR8000, prefs.settingSampleRate)
        assertEquals(ChannelCount.Mono, prefs.settingChannelCount)

        val setting = viewModel.settingFor(RecordingFormat.ThreeGp)
        assertEquals(SampleRate.SR8000, setting.sampleRates.selected())
        assertEquals(ChannelCount.Mono, setting.channelCounts.selected())
        assertTrue("3GP must expose no bitrate chips", setting.bitRates.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Switching to a less restrictive format (M4a / WAV)
    // -------------------------------------------------------------------------

    @Test
    fun `switching from ThreeGp to M4a preserves supported selections and keeps bitrate selectable`() {
        val viewModel = createViewModel(
            format = RecordingFormat.ThreeGp,
            sampleRate = SampleRate.SR16000, // supported by M4a too
            bitRate = BitRate.BR128,
            channelCount = ChannelCount.Mono, // supported by M4a too
        )

        viewModel.selectRecordingFormat(RecordingFormat.M4a)

        assertEquals(RecordingFormat.M4a, prefs.settingRecordingFormat)
        assertEquals(SampleRate.SR16000, prefs.settingSampleRate)
        assertEquals(ChannelCount.Mono, prefs.settingChannelCount)
        assertEquals(BitRate.BR128, prefs.settingBitrate)

        val setting = viewModel.settingFor(RecordingFormat.M4a)
        assertEquals(SampleRate.SR16000, setting.sampleRates.selected())
        assertEquals(ChannelCount.Mono, setting.channelCounts.selected())
        assertEquals(BitRate.BR128, setting.bitRates.selected())
    }

    @Test
    fun `switching to Wav preserves supported selections and exposes no bitrate chips`() {
        val viewModel = createViewModel(
            format = RecordingFormat.M4a,
            sampleRate = SampleRate.SR44100,
            bitRate = BitRate.BR128,
            channelCount = ChannelCount.Stereo,
        )

        viewModel.selectRecordingFormat(RecordingFormat.Wav)

        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
        assertEquals(SampleRate.SR44100, prefs.settingSampleRate)
        assertEquals(ChannelCount.Stereo, prefs.settingChannelCount)

        val setting = viewModel.settingFor(RecordingFormat.Wav)
        assertEquals(SampleRate.SR44100, setting.sampleRates.selected())
        assertEquals(ChannelCount.Stereo, setting.channelCounts.selected())
        assertTrue("WAV must expose no bitrate chips", setting.bitRates.isEmpty())
    }
}
