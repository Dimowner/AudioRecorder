package com.dimowner.audiorecorder.v2.app.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Use actual preferences: these tests cover the UI/persistence boundary, not a mocked preset. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class SettingsViewModelBluetoothPresetTest {
    private lateinit var context: Context
    private lateinit var prefs: PrefsV2Impl
    private val recorder = mockk<AudioRecorderDelegate>(relaxed = true)
    private val sessionActive = MutableStateFlow(false)

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = PrefsV2Impl(context)
        prefs.fullPreferenceReset()
        every { recorder.provideAudioRecorder().isRecording } returns false
        every { recorder.captureSessionActive } returns sessionActive
        every { recorder.isCaptureSessionActive } answers { sessionActive.value }
    }

    private fun createViewModel() = SettingsViewModel(
        prefs = prefs, recordsDataSource = mockk(relaxed = true), fileDataSource = mockk(relaxed = true),
        audioPlayer = mockk(relaxed = true), audioRecorderDelegate = recorder, analyticsTracker = mockk(relaxed = true),
        mainDispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined, context = context,
    )

    @Test fun `screen loads the persisted HFP selection and values`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val state = createViewModel().state.value
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, state.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(state))
    }

    @Test fun `selecting HFP updates the source and every experimental control together`() {
        prefs.settingAudioSource = AudioSource.SYSTEM_AUDIO
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.HFP_PRESET))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, PrefsV2Impl(context).bluetoothVoiceEnhancement)
        assertEquals(AudioSource.SYSTEM_AUDIO, vm.state.value.selectedAudioSource)
    }

    @Test fun `selecting Custom keeps preset values available for editing`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.CUSTOM))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(vm.state.value))
    }

    @Test fun `selecting Disabled refreshes all controls while keeping the chosen format`() {
        prefs.settingRecordingFormat = RecordingFormat.Wav
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.DISABLED))
        assertEquals(BluetoothVoiceEnhancement.DISABLED, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(defaultValues, values(vm.state.value))
        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
    }

    @Test fun `editing the separately displayed audio source preserves the Bluetooth profile`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.setAudioSource(AudioSource.MIC)
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(AudioSource.MIC, vm.state.value.selectedAudioSource)
        assertEquals(PcmGainMode.AUTO_LEVEL, vm.state.value.pcmGainMode)
    }

    @Test fun `editing an experimental choice changes the preset label to Custom`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Gain(PcmGainMode.DB_PLUS_6))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(PcmGainMode.DB_PLUS_6, vm.state.value.pcmGainMode)
    }

    @Test fun `recording reset displays Disabled and default values`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        val vm = createViewModel()
        vm.resetRecordingSettings()
        assertEquals(BluetoothVoiceEnhancement.DISABLED, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(defaultValues, values(vm.state.value))
    }

    @Test fun `preset selection cannot change a recording in progress`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        prefs.pcmGainMode = PcmGainMode.DB_PLUS_12
        val vm = createViewModel()
        val original = values(vm.state.value)
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.HFP_PRESET))
        assertEquals(original, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
        assertEquals(PcmGainMode.DB_PLUS_12, prefs.pcmGainMode)
    }

    @Test fun `source selection cannot change a preset after recording starts outside Settings`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.setAudioSource(AudioSource.MIC)
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `reset cannot clear a preset after recording starts outside Settings`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.resetRecordingSettings()
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `scope switch persists without changing preset or either source`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        assertTrue(vm.state.value.applyOnlyToBluetoothMic)
        vm.onAction(SettingsScreenAction.SetExperiment.Scope(false))
        assertFalse(vm.state.value.applyOnlyToBluetoothMic)
        assertFalse(PrefsV2Impl(context).applyOnlyToBluetoothMic)
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(AudioSource.DEFAULT, vm.state.value.selectedAudioSource)
        assertEquals(AudioSource.VOICE_RECOGNITION, vm.state.value.bluetoothAudioSource)
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.DISABLED))
        assertFalse(vm.state.value.applyOnlyToBluetoothMic)
    }

    @Test fun `Bluetooth source edit changes only the profile source and marks Custom`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Source(AudioSource.UNPROCESSED))
        assertEquals(AudioSource.UNPROCESSED, vm.state.value.bluetoothAudioSource)
        assertEquals(AudioSource.DEFAULT, vm.state.value.selectedAudioSource)
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        vm.onAction(SettingsScreenAction.SetExperiment.Source(AudioSource.SYSTEM_AUDIO))
        assertEquals(AudioSource.UNPROCESSED, prefs.bluetoothAudioSource)
    }

    @Test fun `startup locks scope source and reset before recorder amplitudes arrive`() {
        every { recorder.isCaptureSessionActive } returns true
        val vm = createViewModel()
        assertFalse(vm.state.value.isRecordingSettingEditable)
        vm.onAction(SettingsScreenAction.SetExperiment.Scope(false))
        vm.onAction(SettingsScreenAction.SetExperiment.Source(AudioSource.UNPROCESSED))
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.HFP_PRESET))
        vm.setAudioSource(AudioSource.SYSTEM_AUDIO)
        vm.resetRecordingSettings()
        assertTrue(prefs.applyOnlyToBluetoothMic)
        assertEquals(AudioSource.DEFAULT, prefs.bluetoothAudioSource)
        assertEquals(AudioSource.DEFAULT, prefs.settingAudioSource)
        assertEquals(BluetoothVoiceEnhancement.DISABLED, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `encoding controls cannot bypass the startup lock or change system audio`() {
        prefs.settingAudioSource = AudioSource.SYSTEM_AUDIO
        prefs.settingRecordingFormat = RecordingFormat.M4a
        val before = listOf(prefs.settingSampleRate, prefs.settingBitrate, prefs.settingChannelCount,
            prefs.maxRecordingDurationMills)
        val vm = createViewModel()
        every { recorder.isCaptureSessionActive } returns true
        vm.selectRecordingFormat(RecordingFormat.ThreeGp)
        vm.selectSampleRate(SampleRate.SR16000)
        vm.selectBitrate(BitRate.BR128)
        vm.selectChannelCount(ChannelCount.Mono)
        vm.setMaxRecordingDuration(1)
        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
        assertEquals(RecordingFormat.M4a, prefs.settingRecordingFormat)
        assertEquals(before, listOf(prefs.settingSampleRate, prefs.settingBitrate, prefs.settingChannelCount,
            prefs.maxRecordingDurationMills))
    }

    @Test fun `an open Settings screen follows start and stop editability without reopening`() {
        val vm = createViewModel()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.state.value.isRecordingSettingEditable)
        sessionActive.value = true
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.state.value.isRecordingSettingEditable)
        sessionActive.value = false
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.state.value.isRecordingSettingEditable)
    }

    private fun values(state: SettingsState): List<Any> = listOf(state.bluetoothAudioSource,
        state.bluetoothCaptureRoute, state.bluetoothAudioMode, state.inputPreprocessingPolicy, state.pcmGainMode)

    companion object {
        private val hfpValues = listOf(AudioSource.VOICE_RECOGNITION, BluetoothCaptureRoute.HFP_VOICE_RECOGNITION,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
        private val defaultValues = listOf(AudioSource.DEFAULT, BluetoothCaptureRoute.STANDARD_SCO,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.SYSTEM_DEFAULT, PcmGainMode.OFF)
    }
}
