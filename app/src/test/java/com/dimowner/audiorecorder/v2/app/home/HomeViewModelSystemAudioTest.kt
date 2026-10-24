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
package com.dimowner.audiorecorder.v2.app.home

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.util.AudioManagerHelper
import com.dimowner.audiorecorder.util.BluetoothMicState
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers [HomeViewModel.state]'s `isSystemAudioRecordingSelected`, the flag the home screen reads
 * to decide whether to collect MediaProjection consent before starting a recording.
 *
 * When the flag is wrong the consent dialog is skipped, the service gets no projection, and the
 * recording is made from the microphone although the user picked System Audio.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class HomeViewModelSystemAudioTest {

    private lateinit var prefs: PrefsV2
    private lateinit var recordsDataSource: RecordsDataSource
    private lateinit var fileDataSource: FileDataSource
    private lateinit var audioPlayer: PlayerContractNew.Player
    private lateinit var audioManagerHelper: AudioManagerHelper
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var context: Context

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        recordsDataSource = mockk(relaxed = true)
        fileDataSource = mockk(relaxed = true)
        audioPlayer = mockk(relaxed = true)
        audioManagerHelper = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()

        every { audioManagerHelper.bluetoothMicState } returns MutableStateFlow(BluetoothMicState())
        // No recording in progress, so updateState() takes its "idle screen" branch.
        every { prefs.recordedRecordId } returns -1L
    }

    private fun createViewModel(audioSource: AudioSource): HomeViewModel {
        every { prefs.settingAudioSource } returns audioSource
        return HomeViewModel(
            recordsDataSource = recordsDataSource,
            fileDataSource = fileDataSource,
            prefs = prefs,
            audioPlayer = audioPlayer,
            audioManagerHelper = audioManagerHelper,
            analyticsTracker = analyticsTracker,
            mainDispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            context = context,
        )
    }

    @Test
    fun `system audio flag is set when there is an active record`() {
        coEvery { recordsDataSource.getActiveRecord() } returns mockk(relaxed = true)
        val viewModel = createViewModel(AudioSource.SYSTEM_AUDIO)

        viewModel.onStart()

        assertTrue(viewModel.state.value.isSystemAudioRecordingSelected)
        assertEquals(AudioSource.SYSTEM_AUDIO, viewModel.state.value.selectedAudioSource)
    }

    @Test
    fun `system audio flag survives the idle screen reset when there is no active record`() {
        // A fresh install, or a user who deleted every record: updateState() rebuilds the whole
        // screen state, and the flag used to be dropped with it.
        coEvery { recordsDataSource.getActiveRecord() } returns null
        val viewModel = createViewModel(AudioSource.SYSTEM_AUDIO)

        viewModel.onStart()

        assertTrue(viewModel.state.value.isSystemAudioRecordingSelected)
        assertEquals(AudioSource.SYSTEM_AUDIO, viewModel.state.value.selectedAudioSource)
    }

    @Test
    fun `system audio flag stays off for a microphone source`() {
        coEvery { recordsDataSource.getActiveRecord() } returns null
        val viewModel = createViewModel(AudioSource.MIC)

        viewModel.onStart()

        assertFalse(viewModel.state.value.isSystemAudioRecordingSelected)
        assertEquals(AudioSource.MIC, viewModel.state.value.selectedAudioSource)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `system audio flag stays off when the device cannot capture playback`() {
        coEvery { recordsDataSource.getActiveRecord() } returns null
        val viewModel = createViewModel(AudioSource.SYSTEM_AUDIO)

        viewModel.onStart()

        assertFalse(viewModel.state.value.isSystemAudioRecordingSelected)
    }
}
