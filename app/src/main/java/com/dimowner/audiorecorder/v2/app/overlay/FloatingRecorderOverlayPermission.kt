package com.dimowner.audiorecorder.v2.app.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object FloatingRecorderOverlayPermission {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun overlayPermissionSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }
}
