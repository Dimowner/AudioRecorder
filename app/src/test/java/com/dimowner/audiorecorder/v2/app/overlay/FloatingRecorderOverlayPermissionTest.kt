package com.dimowner.audiorecorder.v2.app.overlay

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class FloatingRecorderOverlayPermissionTest {

    @Test
    fun `overlay permission settings intent targets this app package`() {
        val context = RuntimeEnvironment.getApplication()

        val intent = FloatingRecorderOverlayPermission.overlayPermissionSettingsIntent(context)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}
