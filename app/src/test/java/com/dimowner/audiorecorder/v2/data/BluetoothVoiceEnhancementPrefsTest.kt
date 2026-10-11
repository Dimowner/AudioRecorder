package com.dimowner.audiorecorder.v2.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.AppConstants.PREF_NAME
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class BluetoothVoiceEnhancementPrefsTest {
    private lateinit var context: Context
    private lateinit var stored: SharedPreferences
    private lateinit var prefs: PrefsV2Impl

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        stored = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        prefs = PrefsV2Impl(context)
    }

    @Test fun `new installs default to disabled`() {
        assertEquals(BluetoothVoiceEnhancement.DISABLED, prefs.bluetoothVoiceEnhancement)
        assertEquals(BluetoothAudioMode.NORMAL, prefs.bluetoothAudioMode)
    }

    @Test fun `presets preserve the external system audio selection`() {
        prefs.settingAudioSource = AudioSource.SYSTEM_AUDIO
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.DISABLED
        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
    }

    @Test fun `HFP preset applies exactly the five user-tested choices`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        assertHfpConfiguration()
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, PrefsV2Impl(context).bluetoothVoiceEnhancement)
        assertEquals("HFP_PRESET", stored.getString(MODE_KEY, null))
    }

    @Test fun `disabled restores capture defaults without resetting encoding or Bluetooth switch preference`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.settingRecordingFormat = RecordingFormat.Wav
        prefs.settingSampleRate = SampleRate.SR16000
        prefs.alwaysUseBluetoothMic = true
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.DISABLED
        assertEquals(AudioSource.DEFAULT, prefs.settingAudioSource)
        assertEquals(BluetoothCaptureRoute.STANDARD_SCO, prefs.bluetoothCaptureRoute)
        assertEquals(BluetoothAudioMode.NORMAL, prefs.bluetoothAudioMode)
        assertEquals(InputPreprocessingPolicy.SYSTEM_DEFAULT, prefs.inputPreprocessingPolicy)
        assertEquals(PcmGainMode.OFF, prefs.pcmGainMode)
        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
        assertEquals(SampleRate.SR16000, prefs.settingSampleRate)
        assertTrue(prefs.alwaysUseBluetoothMic)
    }

    @Test fun `custom preserves current choices even when they match a preset`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        assertHfpConfiguration()
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, PrefsV2Impl(context).bluetoothVoiceEnhancement)
    }

    @Test fun `custom on default settings remains custom after restart`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, PrefsV2Impl(context).bluetoothVoiceEnhancement)
        assertEquals(AudioSource.DEFAULT, prefs.settingAudioSource)
    }

    @Test fun `upgrading the tested HFP combination recognizes it without rewriting values`() {
        writeLegacyHfpConfiguration()
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
        assertHfpConfiguration()
        assertFalse(stored.contains(MODE_KEY))
    }

    @Test fun `upgrading a different combination preserves it as custom`() {
        stored.edit().clear().putInt("pref_key_setting_audio_source", AudioSource.MIC.value)
            .putString("pref_key_pcm_gain_mode", "DB_PLUS_12").commit()
        prefs = PrefsV2Impl(context)
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
        assertEquals(AudioSource.MIC, prefs.settingAudioSource)
        assertEquals(PcmGainMode.DB_PLUS_12, prefs.pcmGainMode)
        assertEquals(BluetoothAudioMode.IN_COMMUNICATION, prefs.bluetoothAudioMode)
    }

    @Test fun `external source edits preserve the HFP preset`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.settingAudioSource = AudioSource.MIC
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `Bluetooth source edits mark the profile custom`() {
        assertEditBecomesCustom { bluetoothAudioSource = AudioSource.MIC }
    }

    @Test fun `scope defaults on and persists independently of every preset`() {
        assertTrue(prefs.applyOnlyToBluetoothMic)
        prefs.applyOnlyToBluetoothMic = false
        BluetoothVoiceEnhancement.entries.forEach {
            prefs.bluetoothVoiceEnhancement = it
            assertFalse(PrefsV2Impl(context).applyOnlyToBluetoothMic)
            assertEquals(it, prefs.bluetoothVoiceEnhancement)
        }
    }

    @Test fun `legacy source is copied once and external edits remain independent`() {
        stored.edit().clear().putInt("pref_key_setting_audio_source", AudioSource.UNPROCESSED.value).commit()
        prefs = PrefsV2Impl(context)
        assertEquals(AudioSource.UNPROCESSED, prefs.bluetoothAudioSource)
        prefs.settingAudioSource = AudioSource.MIC
        assertEquals(AudioSource.UNPROCESSED, PrefsV2Impl(context).bluetoothAudioSource)
    }

    @Test fun `migration never copies system playback into Bluetooth source`() {
        stored.edit().clear().putInt("pref_key_setting_audio_source", AudioSource.SYSTEM_AUDIO.value).commit()
        prefs = PrefsV2Impl(context)
        assertEquals(AudioSource.DEFAULT, prefs.bluetoothAudioSource)
        assertEquals(AudioSource.SYSTEM_AUDIO, prefs.settingAudioSource)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Bluetooth source rejects system audio`() {
        prefs.bluetoothAudioSource = AudioSource.SYSTEM_AUDIO
    }

    @Test fun `only legacy disabled defaults migrate mode to normal`() {
        for (label in listOf(null, "DISABLED", "CUSTOM")) {
            stored.edit().clear().putString(MODE_KEY, label)
                .putString("pref_key_bluetooth_audio_mode", "IN_COMMUNICATION").commit()
            prefs = PrefsV2Impl(context)
            assertEquals(if (label == "CUSTOM") BluetoothAudioMode.IN_COMMUNICATION else BluetoothAudioMode.NORMAL,
                prefs.bluetoothAudioMode)
        }
    }

    @Test fun `an explicit custom communication mode survives subsequent launches`() {
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        assertEquals(BluetoothAudioMode.IN_COMMUNICATION, PrefsV2Impl(context).bluetoothAudioMode)
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `individual route edits leave the HFP preset as custom`() {
        assertEditBecomesCustom { bluetoothCaptureRoute = BluetoothCaptureRoute.STANDARD_SCO }
    }

    @Test fun `individual mode edits leave the HFP preset as custom`() {
        assertEditBecomesCustom { bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION }
    }

    @Test fun `individual preprocessing edits leave the HFP preset as custom`() {
        assertEditBecomesCustom { inputPreprocessingPolicy = InputPreprocessingPolicy.SYSTEM_DEFAULT }
    }

    @Test fun `individual gain edits leave the HFP preset as custom`() {
        assertEditBecomesCustom { pcmGainMode = PcmGainMode.OFF }
    }

    @Test fun `writing the same values does not silently leave the selected preset`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.bluetoothAudioSource = AudioSource.VOICE_RECOGNITION
        prefs.bluetoothCaptureRoute = BluetoothCaptureRoute.HFP_VOICE_RECOGNITION
        prefs.bluetoothAudioMode = BluetoothAudioMode.NORMAL
        prefs.inputPreprocessingPolicy = InputPreprocessingPolicy.AGC_ONLY
        prefs.pcmGainMode = PcmGainMode.AUTO_LEVEL
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `a manually changed configuration stays custom even if changed back to preset values`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.pcmGainMode = PcmGainMode.DB_PLUS_6
        prefs.pcmGainMode = PcmGainMode.AUTO_LEVEL
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `unknown selection classifies the actual settings instead of overwriting them`() {
        writeLegacyHfpConfiguration()
        stored.edit().putString(MODE_KEY, "FUTURE_PRESET").commit()
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
        assertHfpConfiguration()
    }

    @Test fun `a stale fixed preset label cannot misrepresent restored settings`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        stored.edit().putString("pref_key_pcm_gain_mode", "OFF").commit()
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `recording reset clears the preset and custom selection`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        prefs.applyOnlyToBluetoothMic = false
        prefs.bluetoothAudioSource = AudioSource.MIC
        prefs.resetRecordingSettings()
        assertEquals(BluetoothVoiceEnhancement.DISABLED, prefs.bluetoothVoiceEnhancement)
        assertTrue(prefs.applyOnlyToBluetoothMic)
        assertEquals(AudioSource.DEFAULT, prefs.bluetoothAudioSource)
        assertEquals(BluetoothAudioMode.NORMAL, prefs.bluetoothAudioMode)
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.fullPreferenceReset()
        assertEquals(BluetoothVoiceEnhancement.DISABLED, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `preference listeners see the complete preset rather than intermediate combinations`() {
        val observations = mutableListOf<List<Any>>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> observations.add(configuration()) }
        stored.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
            assertTrue(observations.isNotEmpty())
            assertTrue(observations.all { it == hfpValues })
        } finally { stored.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun assertEditBecomesCustom(edit: PrefsV2.() -> Unit) {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        prefs.edit()
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, PrefsV2Impl(context).bluetoothVoiceEnhancement)
    }

    private fun assertHfpConfiguration() = assertEquals(hfpValues, configuration())
    private fun configuration(): List<Any> = listOf(prefs.bluetoothAudioSource, prefs.bluetoothCaptureRoute,
        prefs.bluetoothAudioMode, prefs.inputPreprocessingPolicy, prefs.pcmGainMode)

    private fun writeLegacyHfpConfiguration() {
        stored.edit().clear().putInt("pref_key_setting_audio_source", AudioSource.VOICE_RECOGNITION.value)
            .putString("pref_key_bluetooth_capture_route", "HFP_VOICE_RECOGNITION")
            .putString("pref_key_bluetooth_audio_mode", "NORMAL")
            .putString("pref_key_input_preprocessing_policy", "AGC_ONLY")
            .putString("pref_key_pcm_gain_mode", "AUTO_LEVEL").commit()
        prefs = PrefsV2Impl(context)
    }

    companion object {
        private const val MODE_KEY = "pref_key_bluetooth_voice_enhancement"
        private val hfpValues = listOf(AudioSource.VOICE_RECOGNITION, BluetoothCaptureRoute.HFP_VOICE_RECOGNITION,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
    }
}
