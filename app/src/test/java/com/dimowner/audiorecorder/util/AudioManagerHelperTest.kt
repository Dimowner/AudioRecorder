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

package com.dimowner.audiorecorder.util

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.intArrayOf

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class AudioManagerHelperTest {

    @MockK(relaxed = true)
    private lateinit var context: Context

    @MockK(relaxed = true)
    private lateinit var audioManager: AudioManager

    private lateinit var audioManagerHelper: AudioManagerHelper

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager

        audioManagerHelper = AudioManagerHelper(context)
    }

    @Test
    fun testBluetoothMicState_initialState() = runTest {
        val state = audioManagerHelper.bluetoothMicState.first()
        
        assertFalse(state.isAvailable)
        assertFalse(state.isEnabled)
        assertNull(state.deviceName)
    }

    @Test
    fun testRegister_registersCallback() {
        val callbackSlot = slot<AudioDeviceCallback>()
        every { audioManager.registerAudioDeviceCallback(capture(callbackSlot), any()) } returns Unit

        audioManagerHelper.register()

        verify { audioManager.registerAudioDeviceCallback(any(), null) }
        assertNotNull(callbackSlot.captured)
    }

    @Test
    fun testUnregister_unregistersCallback() {
        audioManagerHelper.unregister()

        verify { audioManager.unregisterAudioDeviceCallback(any()) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun testEnableBluetoothMic_legacy_enable() = runTest {
        val mockDevice = mockk<AudioDeviceInfo>(relaxed = true)
        every { mockDevice.isSource } returns true
        every { mockDevice.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(mockDevice)
        every { audioManager.mode } returns AudioManager.MODE_NORMAL

        audioManagerHelper.enableBluetoothMic(true)

        verify { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        verify { audioManager.startBluetoothSco() }
        verify { audioManager.isBluetoothScoOn = true }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun testEnableBluetoothMic_legacy_disable() = runTest {
        val mockDevice = mockk<AudioDeviceInfo>(relaxed = true)
        every { mockDevice.isSource } returns true
        every { mockDevice.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(mockDevice)
        every { audioManager.mode } returns AudioManager.MODE_NORMAL

        // First enable
        audioManagerHelper.enableBluetoothMic(true)

        // Then disable
        audioManagerHelper.enableBluetoothMic(false)

        verify { audioManager.stopBluetoothSco() }
        verify { audioManager.isBluetoothScoOn = false }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testEnableBluetoothMic_api31Plus_enable() = runTest {
        val mockDevice = mockk<AudioDeviceInfo>(relaxed = true)
        every { mockDevice.isSource } returns true
        every { mockDevice.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { mockDevice.productName } returns "Test Device"
        
        every { audioManager.availableCommunicationDevices } returns listOf(mockDevice)
        every { audioManager.setCommunicationDevice(any()) } returns true
        every { audioManager.mode } returns AudioManager.MODE_NORMAL

        audioManagerHelper.enableBluetoothMic(true)

        verify { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        verify { audioManager.setCommunicationDevice(mockDevice) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testEnableBluetoothMic_api31Plus_disable() = runTest {
        every { audioManager.mode } returns AudioManager.MODE_IN_COMMUNICATION

        audioManagerHelper.enableBluetoothMic(false)

        verify { audioManager.clearCommunicationDevice() }
        verify { audioManager.mode = any() }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun testRelease_cleansUpResources_legacy() = runTest {
        val mockDevice = mockk<AudioDeviceInfo>(relaxed = true)
        every { mockDevice.isSource } returns true
        every { mockDevice.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(mockDevice)
        every { audioManager.mode } returns AudioManager.MODE_NORMAL

        // Enable Bluetooth first
        audioManagerHelper.enableBluetoothMic(true)

        // Then release
        audioManagerHelper.release()

        verify { audioManager.stopBluetoothSco() }
        verify { audioManager.isBluetoothScoOn = false }
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify { audioManager.unregisterAudioDeviceCallback(any()) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testRelease_cleansUpResources_api31Plus() {
        audioManagerHelper.release()

        verify { audioManager.clearCommunicationDevice() }
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify { audioManager.unregisterAudioDeviceCallback(any()) }
    }

    @Test
    fun testBluetoothMicState_dataClass() {
        val state1 = BluetoothMicState()
        assertFalse(state1.isAvailable)
        assertFalse(state1.isEnabled)
        assertNull(state1.deviceName)

        val state2 = BluetoothMicState(
            isAvailable = true,
            isEnabled = true,
            deviceName = "Test Device"
        )
        assertTrue(state2.isAvailable)
        assertTrue(state2.isEnabled)
        assertEquals("Test Device", state2.deviceName)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testEnableBluetoothMic_api31Plus_noDeviceAvailable() = runTest {
        every { audioManager.availableCommunicationDevices } returns emptyList()

        audioManagerHelper.enableBluetoothMic(true)

        verify(exactly = 0) { audioManager.setCommunicationDevice(any()) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testEnableBluetoothMic_api31Plus_setCommunicationDeviceFails() = runTest {
        val mockDevice = mockk<AudioDeviceInfo>(relaxed = true)
        every { mockDevice.isSource } returns true
        every { mockDevice.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { mockDevice.productName } returns "Test Device"
        
        every { audioManager.availableCommunicationDevices } returns listOf(mockDevice)
        every { audioManager.setCommunicationDevice(any()) } returns false
        every { audioManager.mode } returns AudioManager.MODE_NORMAL

        audioManagerHelper.enableBluetoothMic(true)

        verify { audioManager.setCommunicationDevice(mockDevice) }
        // Audio mode should be restored on failure
        verify(atLeast = 1) { audioManager.mode = any() }
    }
}

class TestARApplication : Application() {
    override fun onTerminate() {
        // Do nothing - avoid calling Injector.closeTasks() in tests
    }
}
