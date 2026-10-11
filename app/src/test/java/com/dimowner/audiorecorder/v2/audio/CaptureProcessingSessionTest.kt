package com.dimowner.audiorecorder.v2.audio

import android.app.Application
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class CaptureProcessingSessionTest {
    private val recorder = mockk<AudioRecord>(relaxed = true)
    private val manager = mockk<AudioManager>(relaxed = true)
    private val diagnostics = CaptureDiagnostics()

    @Before fun setUp() {
        every { recorder.sampleRate } returns 16000
        every { recorder.channelCount } returns 1
        every { recorder.audioSessionId } returns 42
        every { recorder.audioSource } returns AudioSource.VOICE_RECOGNITION.value
    }

    @Test fun `a changed sample rate is rejected before PCM can be written under a wrong header`() {
        every { recorder.sampleRate } returns 48000
        session(AudioSource.VOICE_RECOGNITION).use {
            assertFalse(it.prepare())
            assertTrue(diagnostics.state.value.session.contains("different PCM rate/channel count"))
        }
    }
    @Test fun `a changed configured source is rejected`() {
        every { recorder.audioSource } returns AudioSource.MIC.value
        session(AudioSource.VOICE_RECOGNITION).use { assertFalse(it.prepare()) }
        assertTrue(diagnostics.state.value.session.contains("no source substitution"))
    }
    @Test fun `unprocessed capture requires declared platform support`() {
        every { recorder.audioSource } returns AudioSource.UNPROCESSED.value
        every { manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) } returns "false"
        session(AudioSource.UNPROCESSED).use { assertFalse(it.prepare()) }
        every { manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) } returns "true"
        session(AudioSource.UNPROCESSED).use { assertTrue(it.prepare()) }
    }

    @Test fun `Bluetooth startup drains queued PCM before accepting any buffer`() {
        val input = mockk<AudioDeviceInfo>(relaxed = true)
        every { input.id } returns 20
        every { input.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        every { input.isSource } returns true
        every { input.productName } returns "Earbud"
        every { recorder.setPreferredDevice(input) } returns true
        every { recorder.routedDevice } returns input
        every { recorder.bufferSizeInFrames } returns 200 // 400 PCM16 mono bytes.
        every { manager.mode } returns AudioManager.MODE_NORMAL
        val listener = slot<AudioRouting.OnRoutingChangedListener>()
        every { recorder.addOnRoutingChangedListener(capture(listener), any()) } answers { Unit }
        CaptureProcessingSession(recorder, AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value,
            bluetoothInput = BluetoothInputSelection(input, BluetoothAudioMode.NORMAL)),
            16000, 1, "Test", diagnostics, manager).use {
            assertTrue(it.prepare())
            it.started(recorder)
            val pcm = ByteArray(200)
            assertFalse(it.acceptPcm(pcm, pcm.size, it.beginRead()))
            assertFalse(it.acceptPcm(pcm, pcm.size, it.beginRead()))
            assertTrue(it.acceptPcm(pcm, pcm.size, it.beginRead()))
            // A transition between reads must restart draining even when both next boundaries
            // report the selected input again (for example after the encoder was descheduled).
            listener.captured.onRoutingChanged(recorder)
            assertFalse(it.acceptPcm(pcm, pcm.size, it.beginRead()))
            assertFalse(it.acceptPcm(pcm, pcm.size, it.beginRead()))
            assertTrue(it.acceptPcm(pcm, pcm.size, it.beginRead()))
        }
    }

    @Test fun `phone mode mismatch is rejected before native capture`() {
        every { manager.mode } returns AudioManager.MODE_NORMAL
        CaptureProcessingSession(recorder, AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value,
            audioMode = BluetoothAudioMode.IN_COMMUNICATION), 16000, 1, "Test", diagnostics, manager).use {
            assertFalse(it.prepare())
            assertTrue(diagnostics.state.value.session.contains("Requested Android audio mode"))
        }
    }

    @Test fun `phone mode is verified at both PCM read boundaries`() {
        for (changeAfterRead in listOf(true, false)) {
            every { manager.mode } returns AudioManager.MODE_IN_COMMUNICATION
            CaptureProcessingSession(recorder, AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value,
                audioMode = BluetoothAudioMode.IN_COMMUNICATION), 16000, 1, "Test", diagnostics, manager).use {
                assertTrue(it.prepare())
                val token = it.beginRead()
                every { manager.mode } returns AudioManager.MODE_NORMAL
                assertThrows(CaptureInputException::class.java) {
                    if (changeAfterRead) it.acceptPcm(ByteArray(20), 20, token) else it.beginRead()
                }
            }
        }
    }

    private fun session(source: AudioSource) = CaptureProcessingSession(recorder, AudioInput.Mic(source.value),
        16000, 1, "Test", diagnostics, manager)
}
