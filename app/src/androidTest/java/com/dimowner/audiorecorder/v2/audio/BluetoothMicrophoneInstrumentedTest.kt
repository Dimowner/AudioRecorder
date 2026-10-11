package com.dimowner.audiorecorder.v2.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.util.AudioManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothMicrophoneInstrumentedTest {
    @Test fun scopedPhoneCaptureReadsNativePcmAndRestoresAudioMode() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        )).use { it.readBytes() }
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousMode = manager.mode
        val helper = AudioManagerHelper(context)
        val rate = 16000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        assertTrue(size > 0)
        for (scope in listOf(true, false)) {
            val settings = MicrophoneCaptureSettings(bluetoothSource = AudioSource.VOICE_RECOGNITION,
                mode = BluetoothAudioMode.NORMAL, preprocessing = InputPreprocessingPolicy.AGC_ONLY,
                gain = PcmGainMode.DB_PLUS_6, onlyBluetooth = scope)
            val input = CaptureConfiguration.resolveInput(AudioSource.MIC, settings, false, true, null).getOrThrow() as AudioInput.Mic
            assertEquals(AudioSource.MIC.value, input.audioSource)
            assertEquals(scope, CaptureConfiguration.mediaRecorderProblem(input) == null)
            assertTrue(helper.prepareRecording(settings, false))
            val record = AudioRecordFactory.create(input, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, size * 2)
            val diagnostics = CaptureDiagnostics()
            try {
                CaptureProcessingSession(record, input, rate, 1, "Scoped phone", diagnostics, manager).use { session ->
                    assertTrue(session.prepare())
                    record.startRecording()
                    session.started(record)
                    val pcm = ByteArray(size)
                    val token = session.beginRead()
                    val read = record.read(pcm, 0, pcm.size)
                    assertTrue(read > 0)
                    assertTrue(session.acceptPcm(pcm, read, token))
                    assertEquals(scope, diagnostics.state.value.session.contains("overrides skipped"))
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                withContext(Dispatchers.Main) { helper.finishRecordingRoute() }
            }
            assertEquals(previousMode, manager.mode)
        }
    }

    @Test fun voiceRecognitionInitializesReadsPcmAndReleasesEffectsAcrossPolicies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Opening a shell command is asynchronous; closing its descriptor immediately races
        // AudioRecord initialization on a fresh install. Wait for a synchronous grant instead.
        if (Build.VERSION.SDK_INT >= 28) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        } else {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
            )).use { it.readBytes() }
        }
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val rate = 16000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        assertTrue("AudioRecord minimum buffer must be supported", size > 0)
        for (policy in InputPreprocessingPolicy.entries) {
            val record = AudioRecordFactory.create(AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value), rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
            var session: CaptureProcessingSession? = null
            try {
                assertEquals(AudioRecord.STATE_INITIALIZED, record.state)
                assertTrue(record.audioSessionId > 0)
                val diagnostics = CaptureDiagnostics()
                session = CaptureProcessingSession(record, AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value, policy, PcmGainMode.DB_PLUS_6),
                    rate, 1, "Instrumented microphone", diagnostics)
                assertTrue(session.prepare())
                record.startRecording()
                session.started(record)
                val pcm = ByteArray(size)
                val readToken = session.beginRead()
                val read = record.read(pcm, 0, pcm.size)
                assertTrue("VOICE_RECOGNITION must produce PCM for $policy, result=$read", read > 0)
                assertTrue(session.acceptPcm(pcm, read, readToken))
                val evidence = diagnostics.state.value.session
                assertTrue(evidence.contains("NS: available="))
                assertTrue(evidence.contains("AEC: available="))
                assertTrue(evidence.contains("AGC: available="))
                assertTrue(evidence.contains("session=${record.audioSessionId}"))
            } finally {
                runCatching { record.stop() }
                record.release()
                session?.close()
            }
        }
    }
}
