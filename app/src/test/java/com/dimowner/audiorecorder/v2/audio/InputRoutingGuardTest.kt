package com.dimowner.audiorecorder.v2.audio

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class InputRoutingGuardTest {
    private val router = mockk<AudioRouting>(relaxed = true)
    private val manager = mockk<AudioManager>(relaxed = true)
    private val headset = device(20, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Earbud")
    private val phone = device(3, AudioDeviceInfo.TYPE_BUILTIN_MIC, "Phone")
    private val listener = slot<AudioRouting.OnRoutingChangedListener>()
    private var actual: AudioDeviceInfo? = null
    private var now = 0L
    private val reports = mutableListOf<String>()
    private lateinit var guard: InputRoutingGuard

    @Before fun setUp() {
        every { manager.mode } returns AudioManager.MODE_NORMAL
        every { router.setPreferredDevice(headset) } returns true
        every { router.routedDevice } answers { actual }
        every { router.addOnRoutingChangedListener(capture(listener), any()) } answers { Unit }
        guard = InputRoutingGuard(router, manager, BluetoothInputSelection(headset, BluetoothAudioMode.NORMAL), reports::add, { now })
    }

    @Test fun `selected physical input is requested explicitly`() {
        guard.prepare()
        verify(exactly = 1) { router.setPreferredDevice(headset) }
        guard.close()
    }
    @Test fun `a refused preferred input is an error rather than a successful fallback`() {
        every { router.setPreferredDevice(headset) } returns false
        assertThrows(CaptureInputException::class.java) { guard.prepare() }
    }
    @Test fun `unconfirmed and phone input are discarded until the bounded deadline`() {
        guard.prepare(); guard.started()
        assertFalse(guard.accept())
        actual = phone
        now = 500
        assertFalse(guard.accept())
        now = 2000
        val failure = assertThrows(CaptureInputException::class.java) { guard.accept() }
        assertTrue(failure.message!!.contains("Phone"))
        guard.close()
    }
    @Test fun `capture starts only once Android reports the selected headset`() {
        guard.prepare(); guard.started()
        actual = phone
        assertFalse(guard.accept())
        actual = headset
        now = 50
        assertTrue(guard.accept())
        assertTrue(reports.any { it.contains("Verified") && it.contains("Earbud") })
        guard.close()
    }
    @Test fun `selected mode mismatch is rejected even when the headset is routed`() {
        guard.prepare(); guard.started()
        actual = headset
        every { manager.mode } returns AudioManager.MODE_IN_COMMUNICATION
        now = 2000
        assertThrows(CaptureInputException::class.java) { guard.accept() }
        guard.close()
    }
    @Test fun `a route change after confirmation never starts another grace period`() {
        guard.prepare(); guard.started()
        actual = headset
        assertTrue(guard.accept())
        assertTrue(listener.isCaptured)
        actual = phone
        listener.captured.onRoutingChanged(router)
        assertThrows(CaptureInputException::class.java) { guard.accept() }
        guard.close()
    }
    @Test fun `MediaRecorder cannot accept uncertain initial routing because it cannot discard PCM`() {
        guard.prepare(); guard.started()
        actual = phone
        assertThrows(CaptureInputException::class.java) { guard.accept(allowWait = false) }
        guard.close()
    }
    @Test fun `routing listener is released once`() {
        guard.prepare(); guard.close(); guard.close()
        verify(exactly = 1) { router.removeOnRoutingChangedListener(any()) }
    }

    @Test fun `a buffer read before input verification is discarded even if routing becomes correct`() {
        guard.prepare(); guard.started()
        actual = phone
        val token = guard.beginRead()
        actual = headset
        assertFalse(guard.finishRead(token))
        assertTrue(guard.finishRead(guard.beginRead()))
        guard.close()
    }

    @Test fun `each PCM read checks actual routing even before its callback is delivered`() {
        guard.prepare(); guard.started()
        actual = headset
        assertNotNull(guard.beginRead())
        actual = phone
        assertThrows(CaptureInputException::class.java) { guard.beginRead() }
        guard.close()
    }

    @Test fun `a route transition during a read discards that entire buffer`() {
        guard.prepare(); guard.started()
        actual = headset
        val token = guard.beginRead()
        assertTrue(listener.isCaptured)
        listener.captured.onRoutingChanged(router)
        assertFalse(guard.finishRead(token))
        guard.close()
    }

    private fun device(id: Int, type: Int, name: String) = mockk<AudioDeviceInfo>(relaxed = true).also {
        every { it.id } returns id
        every { it.type } returns type
        every { it.productName } returns name
        every { it.isSource } returns true
    }
}
