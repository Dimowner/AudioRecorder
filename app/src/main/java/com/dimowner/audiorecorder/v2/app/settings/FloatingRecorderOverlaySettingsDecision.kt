package com.dimowner.audiorecorder.v2.app.settings

internal enum class FloatingRecorderOverlayEnableAction {
    Enable,
    RequestMicrophonePermission,
    OpenOverlayPermissionSettings,
}

internal fun decideFloatingRecorderOverlayEnableAction(
    hasMicrophonePermission: Boolean,
    hasOverlayPermission: Boolean,
): FloatingRecorderOverlayEnableAction {
    return when {
        !hasMicrophonePermission -> FloatingRecorderOverlayEnableAction.RequestMicrophonePermission
        !hasOverlayPermission -> FloatingRecorderOverlayEnableAction.OpenOverlayPermissionSettings
        else -> FloatingRecorderOverlayEnableAction.Enable
    }
}
