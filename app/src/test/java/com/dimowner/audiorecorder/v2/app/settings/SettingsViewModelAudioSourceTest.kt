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
import android.os.Build
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
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the audio-source rules in [SettingsViewModel].
 *
 * System audio is captured through `AudioRecord`, and 3GP is the one recording format that only
 * has a `MediaRecorder` backend, so the two can never be selected together. Whichever of the pair
 * the user picks last wins, and the other one moves to its default.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class SettingsViewModelAudioSourceTest {

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
     * Backs the preferences this view model reads in its constructor with in-memory state, so a
     * value written by the view model is visible to the next read.
     */
    private fun createViewModel(
        format: RecordingFormat = RecordingFormat.M4a,
        audioSource: AudioSource = DefaultValues.DefaultAudioSource,
    ): SettingsViewModel {
        var fmt = format
        var src = audioSource
        var sr = SampleRate.SR44100
        var br = BitRate.BR128
        var cc = ChannelCount.Stereo
        every { prefs.settingRecordingFormat } answers { fmt }
        every { prefs.settingRecordingFormat = any() } answers { fmt = firstArg() }
        every { prefs.settingAudioSource } answers { src }
        every { prefs.settingAudioSource = any() } answers { src = firstArg() }
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

    @Test
    fun `system audio is offered when the feature is supported`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.state.value.audioSourceOptions.contains(AudioSource.SYSTEM_AUDIO))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `system audio is hidden when the feature is unsupported`() {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.value.audioSourceOptions.contains(AudioSource.SYSTEM_AUDIO))
        // The microphone sources stay available on every device.
        assertTrue(viewModel.state.value.audioSourceOptions.contains(AudioSource.MIC))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `selecting system audio is ignored when the feature is unsupported`() {
        val viewModel = createViewModel(
            audioSource = AudioSource.MIC,
        )

        viewModel.setAudioSource(AudioSource.SYSTEM_AUDIO)

        assertEquals(AudioSource.MIC, prefs.settingAudioSource)
        assertEquals(AudioSource.MIC, viewModel.state.value.selectedAudioSource)
    }

    @Test
    fun `selecting system audio moves the format off ThreeGp`() {
        val viewModel = createViewModel(format = RecordingFormat.ThreeGp)

        viewModel.setAudioSource(AudioSource.SYSTEM_AUDIO)

        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
        assertEquals(DefaultValues.DefaultRecordingFormat, prefs.settingRecordingFormat)
    }

    @Test
    fun `selecting system audio keeps a format that has an AudioRecord backend`() {
        val viewModel = createViewModel(format = RecordingFormat.Wav)

        viewModel.setAudioSource(AudioSource.SYSTEM_AUDIO)

        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
    }

    @Test
    fun `selecting ThreeGp gives up system audio`() {
        val viewModel = createViewModel(
            format = RecordingFormat.M4a,
            audioSource = AudioSource.SYSTEM_AUDIO,
        )

        viewModel.selectRecordingFormat(RecordingFormat.ThreeGp)

        assertEquals(RecordingFormat.ThreeGp, prefs.settingRecordingFormat)
        assertEquals(DefaultValues.DefaultAudioSource, prefs.settingAudioSource)
        assertEquals(DefaultValues.DefaultAudioSource, viewModel.state.value.selectedAudioSource)
    }

    @Test
    fun `selecting a format other than ThreeGp keeps system audio`() {
        val viewModel = createViewModel(
            format = RecordingFormat.M4a,
            audioSource = AudioSource.SYSTEM_AUDIO,
        )

        viewModel.selectRecordingFormat(RecordingFormat.Wav)

        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
    }
}
