package com.dimowner.audiorecorder.v2.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.AppConstants.PREF_NAME
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class PrefsV2ImplTest {

    private lateinit var context: Context
    private lateinit var prefs: PrefsV2Impl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = PrefsV2Impl(context)
    }

    @Test
    fun `floating recorder overlay is disabled by default`() {
        assertFalse(prefs.isFloatingRecorderOverlayEnabled)
    }

    @Test
    fun `floating recorder overlay enabled value persists`() {
        prefs.isFloatingRecorderOverlayEnabled = true

        val reloadedPrefs = PrefsV2Impl(context)

        assertTrue(reloadedPrefs.isFloatingRecorderOverlayEnabled)
    }

    @Test
    fun `floating recorder overlay position defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderOverlayX)
        assertEquals(-1, prefs.floatingRecorderOverlayY)
    }

    @Test
    fun `floating recorder overlay size defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderOverlaySize)
    }

    @Test
    fun `floating recorder rename overlay position defaults to unset`() {
        assertEquals(-1, prefs.floatingRecorderRenameOverlayX)
        assertEquals(-1, prefs.floatingRecorderRenameOverlayY)
    }

    @Test
    fun `floating recorder rename speech mode defaults to append`() {
        assertEquals(RenameSpeechMode.Append, prefs.floatingRecorderRenameSpeechMode)
    }

    @Test
    fun `floating recorder overlay position persists`() {
        prefs.floatingRecorderOverlayX = 42
        prefs.floatingRecorderOverlayY = 84

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(42, reloadedPrefs.floatingRecorderOverlayX)
        assertEquals(84, reloadedPrefs.floatingRecorderOverlayY)
    }

    @Test
    fun `floating recorder overlay size persists`() {
        prefs.floatingRecorderOverlaySize = 112

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(112, reloadedPrefs.floatingRecorderOverlaySize)
    }

    @Test
    fun `floating recorder rename overlay position persists`() {
        prefs.floatingRecorderRenameOverlayX = 123
        prefs.floatingRecorderRenameOverlayY = 456

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(123, reloadedPrefs.floatingRecorderRenameOverlayX)
        assertEquals(456, reloadedPrefs.floatingRecorderRenameOverlayY)
    }

    @Test
    fun `floating recorder rename speech mode persists replace`() {
        prefs.floatingRecorderRenameSpeechMode = RenameSpeechMode.Replace

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(RenameSpeechMode.Replace, reloadedPrefs.floatingRecorderRenameSpeechMode)
    }

    @Test
    fun `floating recorder rename speech mode persists append to audio note`() {
        prefs.floatingRecorderRenameSpeechMode = RenameSpeechMode.AppendToAudioNote

        val reloadedPrefs = PrefsV2Impl(context)

        assertEquals(RenameSpeechMode.AppendToAudioNote, reloadedPrefs.floatingRecorderRenameSpeechMode)
    }

    @Test
    fun `capture experiments default to the existing system behavior`() {
        assertExperimentDefaults(prefs)
    }

    @Test
    fun `every bluetooth capture route persists by stable name`() {
        assertEnumPersistence("pref_key_bluetooth_capture_route", BluetoothCaptureRoute.entries,
            { prefs.bluetoothCaptureRoute = it }, { PrefsV2Impl(context).bluetoothCaptureRoute })
    }

    @Test
    fun `every bluetooth audio mode persists by stable name`() {
        assertEnumPersistence("pref_key_bluetooth_audio_mode", BluetoothAudioMode.entries,
            { prefs.bluetoothAudioMode = it }, { PrefsV2Impl(context).bluetoothAudioMode })
    }

    @Test
    fun `every preprocessing policy persists by stable name`() {
        assertEnumPersistence("pref_key_input_preprocessing_policy", InputPreprocessingPolicy.entries,
            { prefs.inputPreprocessingPolicy = it }, { PrefsV2Impl(context).inputPreprocessingPolicy })
    }

    @Test
    fun `every pcm gain mode persists by stable name`() {
        assertEnumPersistence("pref_key_pcm_gain_mode", PcmGainMode.entries,
            { prefs.pcmGainMode = it }, { PrefsV2Impl(context).pcmGainMode })
    }

    @Test
    fun `unknown experimental settings safely fall back to defaults`() {
        // Simulates preferences written by a newer version, including an empty stored name.
        for (unknown in listOf("FUTURE_OPTION", "", "off")) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString("pref_key_bluetooth_capture_route", unknown)
                .putString("pref_key_bluetooth_audio_mode", unknown)
                .putString("pref_key_input_preprocessing_policy", unknown)
                .putString("pref_key_pcm_gain_mode", unknown)
                .commit()
            assertExperimentDefaults(PrefsV2Impl(context))
        }
    }

    @Test
    fun `reset recording settings resets experiments and audio source`() {
        prefs.bluetoothCaptureRoute = BluetoothCaptureRoute.HFP_VOICE_RECOGNITION
        prefs.bluetoothAudioMode = BluetoothAudioMode.NORMAL
        prefs.inputPreprocessingPolicy = InputPreprocessingPolicy.AGC_ONLY
        prefs.pcmGainMode = PcmGainMode.AUTO_LEVEL
        prefs.settingAudioSource = AudioSource.VOICE_RECOGNITION
        prefs.alwaysUseBluetoothMic = true
        prefs.recordAuthorName = "Keep this author"

        prefs.resetRecordingSettings()

        val reloaded = PrefsV2Impl(context)
        assertExperimentDefaults(reloaded)
        assertEquals(DefaultValues.DefaultAudioSource, reloaded.settingAudioSource)
        assertEquals(DefaultValues.DefaultRecordingFormat, reloaded.settingRecordingFormat)
        assertEquals(DefaultValues.DefaultSampleRate, reloaded.settingSampleRate)
        assertEquals(DefaultValues.DefaultBitRate, reloaded.settingBitrate)
        assertEquals(DefaultValues.DefaultChannelCount, reloaded.settingChannelCount)
        assertTrue(reloaded.alwaysUseBluetoothMic)
        assertEquals("Keep this author", reloaded.recordAuthorName)
    }

    @Test
    fun `voice recognition source persists as the platform constant`() {
        prefs.settingAudioSource = AudioSource.VOICE_RECOGNITION
        assertEquals(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt("pref_key_setting_audio_source", -1))
        assertEquals(AudioSource.VOICE_RECOGNITION, PrefsV2Impl(context).settingAudioSource)
    }

    private fun assertExperimentDefaults(actual: PrefsV2) {
        assertEquals(BluetoothCaptureRoute.STANDARD_SCO, actual.bluetoothCaptureRoute)
        assertEquals(BluetoothAudioMode.NORMAL, actual.bluetoothAudioMode)
        assertEquals(InputPreprocessingPolicy.SYSTEM_DEFAULT, actual.inputPreprocessingPolicy)
        assertEquals(PcmGainMode.OFF, actual.pcmGainMode)
    }

    private fun <T : Enum<T>> assertEnumPersistence(
        key: String,
        values: List<T>,
        write: (T) -> Unit,
        read: () -> T,
    ) {
        val stored = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        for (value in values) {
            write(value)
            assertEquals(value.name, stored.getString(key, null))
            assertEquals(value, read())
        }
    }
}
