package com.dimowner.audiorecorder.v2.audio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.dimowner.audiorecorder.v2.app.HomeActivity
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class CaptureStartupInstrumentedTest {
    @Test fun rejectedSystemCaptureWhileBoundDoesNotViolateForegroundStartDeadline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        context.startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val connected = CountDownLatch(1)
        var service: AudioRecordingService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as AudioRecordingService.ServiceBinder).getService()
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val prefs = PrefsV2Impl(context)
        val previousSource = prefs.settingAudioSource
        val previousPreset = prefs.bluetoothVoiceEnhancement
        assertTrue(context.bindService(Intent(context, AudioRecordingService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(5, TimeUnit.SECONDS))
            prefs.settingAudioSource = AudioSource.SYSTEM_AUDIO
            AudioRecordingService.startServiceForeground(context) // Deliberately no projection consent.
            // The bound service remains alive: wait beyond Android's foreground-start deadline.
            Thread.sleep(12000) // Android 9 uses a 10-second foreground-start timeout.
            assertFalse(service!!.recordingState.value.isRecording())
            assertTrue(service!!.captureDiagnostics.state.value.session.contains("consent"))
        } finally {
            prefs.settingAudioSource = previousSource
            prefs.bluetoothVoiceEnhancement = previousPreset
            context.unbindService(connection)
        }
    }
}
