package com.dimowner.audiorecorder.v2.audio

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class BluetoothCaptureControllerTest {
    private val manager = mockk<AudioManager>(relaxed = true)
    private val bluetooth = mockk<BluetoothManager>()
    private val adapter = mockk<BluetoothAdapter>(relaxed = true)
    private val proxy = mockk<BluetoothHeadset>(relaxed = true)
    private val headset = mockk<BluetoothDevice>(relaxed = true)
    private val input = mockk<AudioDeviceInfo>(relaxed = true)
    private val listener = slot<BluetoothProfile.ServiceListener>()
    private lateinit var context: Context
    private lateinit var controller: BluetoothCaptureController

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? = when (name) {
                Context.AUDIO_SERVICE -> manager
                Context.BLUETOOTH_SERVICE -> bluetooth
                else -> super.getSystemService(name)
            }
        }
        var actualMode = AudioManager.MODE_NORMAL
        every { manager.mode } answers { actualMode }
        every { manager.mode = any() } answers { actualMode = firstArg() }
        every { bluetooth.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getProfileProxy(any(), capture(listener), BluetoothProfile.HEADSET) } returns true
        every { proxy.connectedDevices } returns listOf(headset)
        every { proxy.startVoiceRecognition(headset) } returns true
        every { headset.name } returns "Test headset"
        every { headset.address } returns "00:11:22:33:44:55"
        every { input.address } returns "00:11:22:33:44:55"
        controller = BluetoothCaptureController(context, CaptureDiagnostics()) {}
    }

    @Test fun `SCO request does not become ready after a blind delay`() {
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.IN_COMMUNICATION, input)
        verify { manager.startBluetoothSco() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        context.sendBroadcast(Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
        controller.stop()
        verify { manager.stopBluetoothSco() }
    }

    @Test fun `legacy normal mode is selected and timeout is explicit`() {
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        verify(exactly = 0) { manager.mode = AudioManager.MODE_IN_COMMUNICATION }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("Timed out"))
        verify { manager.stopBluetoothSco() }
    }

    @Suppress("DEPRECATION")
    @Test fun `already connected SCO stays ready without a new connected broadcast`() {
        val connected = Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTING)
        context.sendStickyBroadcast(connected)
        try {
            controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
            // No new CONNECTED event: registration is the only source of the existing state.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertEquals(controller.state.value.message, BluetoothRoutePhase.READY, controller.state.value.phase)
            verify(exactly = 1) { manager.startBluetoothSco() }
            verify(exactly = 0) { manager.stopBluetoothSco() }
        } finally {
            controller.stop()
            context.removeStickyBroadcast(connected)
        }
    }

    @Test fun `returned connected SCO state needs no receiver callback and owns its request`() = runBlocking {
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.IN_COMMUNICATION, input)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        try {
            assertEquals(controller.state.value.message, BluetoothRoutePhase.READY, controller.state.value.phase)
            assertTrue(controller.awaitReady())
            verify(exactly = 1) { manager.startBluetoothSco() }
            verify(exactly = 0) { manager.stopBluetoothSco() }
        } finally {
            controller.stop()
        }
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        verify(exactly = 1) { manager.stopBluetoothSco() }
        verify { manager.isBluetoothScoOn = false }
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        assertEquals(BluetoothRoutePhase.IDLE, controller.state.value.phase)
    }

    @Test fun `returned connected SCO state still observes later disconnection`() {
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.IN_COMMUNICATION, input)
        assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
        sendScoState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("disconnected"))
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        verify(exactly = 1) { manager.stopBluetoothSco() }
        verify { manager.isBluetoothScoOn = false }
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
    }

    @Test fun `initial disconnected SCO state waits for a new connection`() {
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        sendScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
        controller.stop()
    }

    @Test fun `returned connected SCO state cannot bypass requested mode validation`() {
        every { manager.mode } returns AudioManager.MODE_IN_COMMUNICATION
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("mode"))
        verify(exactly = 1) { manager.stopBluetoothSco() }
    }

    @Test fun `returned connected SCO state cannot bypass a failed start request`() {
        every { manager.startBluetoothSco() } throws IllegalStateException("SCO unavailable")
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.IN_COMMUNICATION, input)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("SCO unavailable"))
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        verify(exactly = 1) { manager.stopBluetoothSco() }
    }

    @Test fun `returned SCO state does not make an HFP request ready`() {
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        verify(exactly = 0) { manager.startBluetoothSco() }
        controller.stop()
    }

    @Test fun `returned connected SCO state does not permit a phone input substitution`() {
        val phone = mockk<AudioDeviceInfo>(relaxed = true)
        every { phone.isSource } returns true
        every { phone.type } returns AudioDeviceInfo.TYPE_BUILTIN_MIC
        every { manager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(phone)
        controller = controllerWithInitialScoState(AudioManager.SCO_AUDIO_STATE_CONNECTED)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        try {
            assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
            assertThrows(IllegalStateException::class.java) { controller.inputSelection() }
        } finally {
            controller.stop()
        }
    }

    /** Return the registration snapshot without delivering an initial onReceive callback.
     * Keep real receiver registration so later disconnections and cleanup exercise the platform boundary.
     */
    private fun controllerWithInitialScoState(state: Int): BluetoothCaptureController {
        val initial = Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
        val registrationContext = object : ContextWrapper(context) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter,
                broadcastPermission: String?, scheduler: Handler?, flags: Int): Intent? {
                super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
                return initial
            }
        }
        return BluetoothCaptureController(registrationContext, CaptureDiagnostics()) {}
    }

    private fun sendScoState(state: Int) {
        context.sendBroadcast(Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `HFP requests voice recognition and readiness follows audio state`() {
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.IN_COMMUNICATION, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        verify { proxy.startVoiceRecognition(headset) }
        verify(exactly = 0) { manager.startBluetoothSco() }
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        // A profile query can confirm the same real connection state as its broadcast.
        every { proxy.isAudioConnected(headset) } returns true
        controller.stop()
        verify { proxy.stopVoiceRecognition(headset) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `HFP rejection never silently falls back to SCO`() {
        every { proxy.startVoiceRecognition(headset) } returns false
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("returned false"))
        verify(exactly = 0) { manager.startBluetoothSco() }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `missing HFP headset is explicit and profile is closed`() {
        every { proxy.connectedDevices } returns emptyList()
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertTrue(controller.state.value.message.contains("No connected HFP"))
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `late profile callback after cancellation cannot enable microphone`() {
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        controller.stop()
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `HFP never substitutes a different connected headset`() {
        every { input.address } returns "00:99:88:77:66:55"
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("no device fallback"))
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test @Config(sdk = [31, 36])
    fun `Standard SCO with Normal never forces communication mode or substitutes modern routing`() {
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        verify { manager.mode = AudioManager.MODE_NORMAL }
        verify(exactly = 0) { manager.mode = AudioManager.MODE_IN_COMMUNICATION }
        verify { manager.startBluetoothSco() }
        verify(exactly = 0) { manager.setCommunicationDevice(any()) }
        controller.stop()
    }

    @Test @Config(sdk = [31, 36])
    fun `explicit modern route honors each selected audio mode`() {
        every { manager.setCommunicationDevice(input) } returns true
        every { manager.communicationDevice } returns input
        for (mode in BluetoothAudioMode.entries) {
            controller.start(BluetoothCaptureRoute.COMMUNICATION_DEVICE, mode, input)
            assertEquals(if (mode == BluetoothAudioMode.NORMAL) AudioManager.MODE_NORMAL else AudioManager.MODE_IN_COMMUNICATION, manager.mode)
            assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
            controller.stop()
        }
        verify(exactly = 0) { manager.startBluetoothSco() }
    }

    @Test fun `modern routing on older Android fails rather than silently using SCO`() {
        controller.start(BluetoothCaptureRoute.COMMUNICATION_DEVICE, BluetoothAudioMode.NORMAL, input)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("Android 12"))
        verify(exactly = 0) { manager.startBluetoothSco() }
    }

    @Test fun `Android rejecting a mode is not reported as ready`() {
        every { manager.mode } returns AudioManager.MODE_IN_COMMUNICATION
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        context.sendBroadcast(Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("mode"))
    }

    @Test @Config(sdk = [31])
    fun `communication output is matched to its input port instead of recording a phone mic`() {
        every { input.id } returns 10
        every { input.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { input.isSource } returns false
        val capture = mockk<AudioDeviceInfo>(relaxed = true)
        every { capture.id } returns 11
        every { capture.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { capture.isSource } returns true
        every { capture.address } returns "00:11:22:33:44:55"
        every { manager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(capture)
        every { manager.setCommunicationDevice(input) } returns true
        every { manager.communicationDevice } returns input
        controller.start(BluetoothCaptureRoute.COMMUNICATION_DEVICE, BluetoothAudioMode.NORMAL, input)
        assertEquals(capture, controller.inputSelection().device)
        assertEquals(BluetoothAudioMode.NORMAL, controller.inputSelection().mode)
        controller.stop()
    }

    @Test fun `a connected SCO link without an exposed Bluetooth input cannot select the phone`() {
        val phone = mockk<AudioDeviceInfo>(relaxed = true)
        every { phone.isSource } returns true
        every { phone.type } returns AudioDeviceInfo.TYPE_BUILTIN_MIC
        every { manager.getDevices(AudioManager.GET_DEVICES_INPUTS) } returns arrayOf(phone)
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        context.sendBroadcast(Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertThrows(IllegalStateException::class.java) { controller.inputSelection() }
        controller.stop()
    }
}
