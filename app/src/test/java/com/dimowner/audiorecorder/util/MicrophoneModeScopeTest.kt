package com.dimowner.audiorecorder.util

import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.audio.MicrophoneCaptureSettings
import kotlinx.coroutines.test.runTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [28])
class MicrophoneModeScopeTest {
    private lateinit var context: Context
    private lateinit var prefs: PrefsV2Impl
    private lateinit var manager: AudioManager

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = PrefsV2Impl(context).apply { fullPreferenceReset() }
        manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_NORMAL
    }

    @Test fun `all mic scope applies phone mode and restores it after stop`() = runTest {
        prefs.applyOnlyToBluetoothMic = false
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val helper = AudioManagerHelper(context, prefs)
        assertTrue(helper.prepareRecording())
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
        helper.finishRecordingRoute()
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        assertFalse(manager.isBluetoothScoOn)
    }

    @Test fun `Bluetooth only scope leaves phone mode untouched`() = runTest {
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val helper = AudioManagerHelper(context, prefs)
        assertTrue(helper.prepareRecording())
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        helper.finishRecordingRoute()
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
    }

    @Test fun `mode change during phone capture is detected without a Bluetooth route`() = runTest {
        prefs.applyOnlyToBluetoothMic = false
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val helper = AudioManagerHelper(context, prefs)
        assertTrue(helper.prepareRecording())
        manager.mode = AudioManager.MODE_NORMAL
        assertFalse(helper.isRouteReadyForRecording)
        helper.finishRecordingRoute()
    }

    @Test fun `prepared snapshot survives preference edits and cannot switch routing mid capture`() = runTest {
        prefs.applyOnlyToBluetoothMic = false
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val frozen = MicrophoneCaptureSettings.from(prefs)
        prefs.bluetoothAudioMode = BluetoothAudioMode.NORMAL
        prefs.applyOnlyToBluetoothMic = true
        val helper = AudioManagerHelper(context, prefs)
        assertTrue(helper.prepareRecording(frozen, false))
        helper.enableBluetoothMic(true)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
        assertFalse(helper.ownsBluetoothRecordingRoute)
        helper.finishRecordingRoute()
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
    }

    @Test fun `phone preparation releases a Bluetooth preview started after the snapshot`() = runTest {
        val device = mockk<AudioDeviceInfo>(relaxed = true)
        every { device.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val audioManager = mockk<AudioManager>(relaxed = true)
        var mode = AudioManager.MODE_NORMAL
        every { audioManager.mode } answers { mode }
        every { audioManager.mode = any() } answers { mode = firstArg() }
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(device)
        val mockedContext = mockk<Context>(relaxed = true)
        every { mockedContext.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val helper = AudioManagerHelper(mockedContext, prefs)
        val useBluetooth = helper.useBluetoothForRecording
        assertFalse(useBluetooth)
        helper.enableBluetoothMic(true)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, mode)
        assertTrue(helper.prepareRecording(MicrophoneCaptureSettings.from(prefs), useBluetooth))
        assertEquals(AudioManager.MODE_NORMAL, mode)
        helper.finishRecordingRoute()
        assertEquals(AudioManager.MODE_NORMAL, mode)
    }

    @Test fun `system playback blocks Bluetooth preview until capture ownership is released`() = runTest {
        prefs.applyOnlyToBluetoothMic = false
        prefs.bluetoothAudioMode = BluetoothAudioMode.IN_COMMUNICATION
        val helper = AudioManagerHelper(context, prefs)
        helper.prepareSystemPlayback()
        helper.enableBluetoothMic(true)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        helper.finishRecordingRoute()
        helper.enableBluetoothMic(true)
        assertTrue(helper.bluetoothMicState.value.isEnabled)
        helper.finishRecordingRoute()
    }
}
