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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [Build.VERSION_CODES.Q])
class AudioManagerHelperLegacyScoTest {
    private val context = mockk<Context>(relaxed = true)
    private val audioManager = mockk<AudioManager>(relaxed = true)
    private val receiver = slot<BroadcastReceiver>()
    private lateinit var helper: AudioManagerHelper
    private var stickyIntent: Intent? = null

    @Before
    fun setup() {
        val device = mockk<AudioDeviceInfo>(relaxed = true)
        every { device.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { device.productName } returns "Headset"
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(device)
        every { audioManager.mode } returns AudioManager.MODE_NORMAL
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { context.registerReceiver(capture(receiver), any()) } answers { stickyIntent }
        helper = AudioManagerHelper(context)
    }

    private fun scoIntent(state: Int, previousState: Int = AudioManager.SCO_AUDIO_STATE_DISCONNECTED) =
        Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, previousState)

    private fun sendScoState(state: Int, previousState: Int = AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        receiver.captured.onReceive(context, scoIntent(state, previousState))
    }

    @Test
    fun waitsBeyond500msUntilScoIsConnected() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        assertTrue(receiver.isCaptured)
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTING)
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(enable.isActive)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 0) { audioManager.isBluetoothScoOn = true }

        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED, AudioManager.SCO_AUDIO_STATE_CONNECTING)
        runCurrent()
        assertTrue(enable.isCompleted)
        assertTrue(helper.bluetoothMicState.value.isEnabled)
    }

    @Test
    fun alreadyConnectedStickyStateDoesNotWaitForAnotherBroadcast() = runTest {
        stickyIntent = scoIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }

        assertTrue(enable.isCompleted)
        assertTrue(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.startBluetoothSco() }
    }

    @Test
    fun initialDisconnectedStateDoesNotFailNewConnection() = runTest {
        stickyIntent = scoIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        sendScoState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        runCurrent()
        assertTrue(enable.isActive)

        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED, AudioManager.SCO_AUDIO_STATE_CONNECTING)
        runCurrent()
        assertTrue(helper.bluetoothMicState.value.isEnabled)
    }

    @Test
    fun failedConnectionRestoresModeAndReleasesRequest() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTING)
        sendScoState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED, AudioManager.SCO_AUDIO_STATE_CONNECTING)
        runCurrent()

        assertTrue(enable.isCompleted)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun missingBroadcastTimesOutAndReleasesRequest() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        advanceUntilIdle()

        assertTrue(enable.isCompleted)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun cancellingEnableReleasesPendingConnection() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        enable.cancel()
        enable.join()

        assertTrue(enable.isCancelled)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun disablingWhileConnectingPreventsLateConnectionFromReenablingRouting() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        helper.enableBluetoothMic(false)
        runCurrent()
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)

        assertTrue(enable.isCompleted)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 0) { audioManager.isBluetoothScoOn = true }
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
    }

    @Test
    fun releaseWhileConnectingRemovesReceiver() = runTest {
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        helper.release()
        runCurrent()

        assertTrue(enable.isCompleted)
        assertEquals(BluetoothMicState(), helper.bluetoothMicState.value)
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
    }

    @Test
    fun repeatedEnableSharesPendingRequestAndPreservesOriginalMode() = runTest {
        every { audioManager.mode } returnsMany listOf(
            AudioManager.MODE_NORMAL, AudioManager.MODE_IN_COMMUNICATION
        )
        val first = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        runCurrent()

        assertTrue(first.isCompleted)
        assertTrue(second.isCompleted)
        assertTrue(helper.bluetoothMicState.value.isEnabled)
        helper.enableBluetoothMic(true)
        helper.enableBluetoothMic(false)
        verify(exactly = 1) { audioManager.startBluetoothSco() }
        verify(exactly = 1) { audioManager.mode = AudioManager.MODE_NORMAL }
    }

    @Test
    fun disconnectAfterConnectionClearsEnabledState() = runTest {
        stickyIntent = scoIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        helper.enableBluetoothMic(true)
        sendScoState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED, AudioManager.SCO_AUDIO_STATE_CONNECTED)

        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun headsetRemovalWhileConnectingReleasesRequest() = runTest {
        val callback = slot<AudioDeviceCallback>()
        every { audioManager.registerAudioDeviceCallback(capture(callback), any()) } returns Unit
        helper.register()
        val enable = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns emptyArray()
        callback.captured.onAudioDevicesRemoved(emptyArray())
        runCurrent()

        assertTrue(enable.isCompleted)
        assertFalse(helper.bluetoothMicState.value.isAvailable)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { audioManager.stopBluetoothSco() }
    }

    @Test
    fun startExceptionRestoresModeAndRemovesReceiver() = runTest {
        every { audioManager.startBluetoothSco() } throws IllegalStateException("SCO unavailable")
        helper.enableBluetoothMic(true)

        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun noHeadsetDoesNotRequestScoOrChangeMode() = runTest {
        every { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns emptyArray()
        helper.enableBluetoothMic(true)

        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 0) { audioManager.startBluetoothSco() }
        verify(exactly = 0) { audioManager.mode = any() }
    }

    @Test
    fun oldReceiverCannotCompleteANewRequest() = runTest {
        val first = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        val oldReceiver = receiver.captured
        helper.enableBluetoothMic(false)
        val second = launch(start = CoroutineStart.UNDISPATCHED) { helper.enableBluetoothMic(true) }
        oldReceiver.onReceive(context, scoIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        runCurrent()

        assertTrue(first.isCompleted)
        assertTrue(second.isActive)
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        runCurrent()
        assertTrue(second.isCompleted)
        assertTrue(helper.bluetoothMicState.value.isEnabled)
        helper.release()
    }

    @Test
    fun unregisterPreservesConnectedRoutingUntilRelease() = runTest {
        stickyIntent = scoIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        helper.enableBluetoothMic(true)
        helper.unregister()

        assertTrue(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 0) { context.unregisterReceiver(any()) }
        helper.release()
        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }

    @Test
    fun stopExceptionStillRestoresModeAndClearsState() = runTest {
        stickyIntent = scoIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        helper.enableBluetoothMic(true)
        every { audioManager.stopBluetoothSco() } throws IllegalStateException("Cannot stop SCO")
        helper.enableBluetoothMic(false)

        assertFalse(helper.bluetoothMicState.value.isEnabled)
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify(exactly = 1) { context.unregisterReceiver(receiver.captured) }
    }
}
