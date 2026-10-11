package com.dimowner.audiorecorder.v2.audio

import android.media.projection.MediaProjection
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CaptureConfigurationTest {
    @Test fun `missing system consent is rejected rather than recording the microphone`() {
        val result = CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO, MicrophoneCaptureSettings(), false, true, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("consent"))
    }
    @Test fun `unsupported system capture is rejected rather than changing source`() {
        assertTrue(CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO, MicrophoneCaptureSettings(), false, false, null).isFailure)
    }
    @Test fun `microphone choices reach the recorder unchanged`() {
        val settings = MicrophoneCaptureSettings(preprocessing = InputPreprocessingPolicy.AGC_ONLY,
            gain = PcmGainMode.AUTO_LEVEL, onlyBluetooth = false)
        val input = CaptureConfiguration.resolveInput(AudioSource.VOICE_RECOGNITION, settings, false, false, null).getOrThrow()
        assertEquals(AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL,
            audioMode = BluetoothAudioMode.NORMAL, requestedSettings = settings), input)
    }
    @Test fun `valid system capture keeps its projection`() {
        val projection = mockk<MediaProjection>()
        assertEquals(AudioInput.SystemPlayback(projection), CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO,
            MicrophoneCaptureSettings(preprocessing = InputPreprocessingPolicy.AGC_ONLY, gain = PcmGainMode.AUTO_LEVEL),
            true, true, projection).getOrThrow())
    }

    @Test fun `source scope and backend compatibility follow the full microphone matrix`() {
        for (onlyBluetooth in listOf(true, false)) for (useBluetooth in listOf(true, false)) {
            val settings = MicrophoneCaptureSettings(bluetoothSource = AudioSource.VOICE_RECOGNITION,
                mode = BluetoothAudioMode.IN_COMMUNICATION, preprocessing = InputPreprocessingPolicy.AGC_ONLY,
                gain = PcmGainMode.AUTO_LEVEL, onlyBluetooth = onlyBluetooth)
            val input = CaptureConfiguration.resolveInput(AudioSource.MIC, settings, useBluetooth, true, null).getOrThrow() as AudioInput.Mic
            val active = useBluetooth || !onlyBluetooth
            assertEquals(if (useBluetooth) AudioSource.VOICE_RECOGNITION.value else AudioSource.MIC.value, input.audioSource)
            assertEquals(if (active) BluetoothAudioMode.IN_COMMUNICATION else null, input.audioMode)
            assertEquals(if (active) InputPreprocessingPolicy.AGC_ONLY else InputPreprocessingPolicy.SYSTEM_DEFAULT, input.preprocessing)
            assertEquals(if (active) PcmGainMode.AUTO_LEVEL else PcmGainMode.OFF, input.gain)
            assertEquals(active, CaptureConfiguration.mediaRecorderProblem(input) != null)
            assertEquals(active, CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 16000, 1, input) != null)
            val evidence = CaptureProcessingSession.describeInput(input)
            assertTrue(evidence.contains("Selected Android mode: IN_COMMUNICATION"))
            assertEquals(!active, evidence.contains("overrides skipped"))
        }
    }

    @Test fun `system playback takes precedence over both scope positions and Bluetooth intent`() {
        val projection = mockk<MediaProjection>()
        for (scope in listOf(true, false)) for (bt in listOf(true, false)) {
            assertEquals(AudioInput.SystemPlayback(projection), CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO,
                MicrophoneCaptureSettings(onlyBluetooth = scope), bt, true, projection).getOrThrow())
        }
    }

    @Test fun `a malformed Bluetooth source cannot turn microphone capture into playback`() {
        assertTrue(CaptureConfiguration.resolveInput(AudioSource.MIC,
            MicrophoneCaptureSettings(bluetoothSource = AudioSource.SYSTEM_AUDIO), true, true, mockk()).isFailure)
    }

    @Test fun `skipped audio mode imposes no global mode requirement`() {
        assertNull(CaptureConfiguration.modeProblem(AudioInput.Mic(AudioSource.MIC.value), 3))
        assertNotNull(CaptureConfiguration.modeProblem(AudioInput.Mic(AudioSource.MIC.value, audioMode = BluetoothAudioMode.NORMAL), 3))
    }
    @Test fun `3GP rejects system audio instead of substituting M4A`() {
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 16000, 1,
            AudioInput.SystemPlayback(mockk())))
    }
    @Test fun `MediaRecorder refuses every non-default processing policy`() {
        InputPreprocessingPolicy.entries.filter { it != InputPreprocessingPolicy.SYSTEM_DEFAULT }.forEach {
            assertNotNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.MIC.value, it)))
        }
    }
    @Test fun `MediaRecorder refuses every enabled software gain`() {
        PcmGainMode.entries.filter { it != PcmGainMode.OFF }.forEach {
            assertNotNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.MIC.value, gain = it)))
        }
    }
    @Test fun `MediaRecorder accepts system-managed microphone processing with gain off`() {
        assertNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value)))
    }
    @Test fun `3GP cannot silently replace unsupported rate or stereo`() {
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 44100, 1, AudioInput.Mic(1)))
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 16000, 2, AudioInput.Mic(1)))
    }
    @Test fun `AudioRecord formats accept the selected microphone processing`() {
        val input = AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
        assertNull(CaptureConfiguration.configurationProblem(RecordingFormat.Wav, 16000, 1, input))
        assertNull(CaptureConfiguration.configurationProblem(RecordingFormat.M4a, 16000, 1, input))
    }
    @Test fun `AAC rejects an unattainable selected bitrate instead of lowering it`() {
        assertFalse(CaptureConfiguration.supportsAacBitrate(288000, 8000, 1, 960000))
        assertFalse(CaptureConfiguration.supportsAacBitrate(192000, 48000, 2, 96000))
        assertFalse(CaptureConfiguration.supportsAacBitrate(0, 48000, 2, 960000))
        assertTrue(CaptureConfiguration.supportsAacBitrate(192000, 48000, 2, 960000))
        assertTrue(CaptureConfiguration.supportsAacBitrate(48000, 8000, 1, 960000))
    }
}
