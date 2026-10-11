package com.dimowner.audiorecorder.v2.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingRecorderOverlaySettingsDecisionTest {

    @Test
    fun `decideFloatingRecorderOverlayEnableAction enables when all permissions are granted`() {
        val result = decideFloatingRecorderOverlayEnableAction(
            hasMicrophonePermission = true,
            hasOverlayPermission = true,
        )

        assertEquals(FloatingRecorderOverlayEnableAction.Enable, result)
    }

    @Test
    fun `decideFloatingRecorderOverlayEnableAction requests microphone before enabling`() {
        val result = decideFloatingRecorderOverlayEnableAction(
            hasMicrophonePermission = false,
            hasOverlayPermission = true,
        )

        assertEquals(FloatingRecorderOverlayEnableAction.RequestMicrophonePermission, result)
    }

    @Test
    fun `decideFloatingRecorderOverlayEnableAction opens overlay settings when overlay permission is missing`() {
        val result = decideFloatingRecorderOverlayEnableAction(
            hasMicrophonePermission = true,
            hasOverlayPermission = false,
        )

        assertEquals(FloatingRecorderOverlayEnableAction.OpenOverlayPermissionSettings, result)
    }

    @Test
    fun `decideFloatingRecorderOverlayEnableAction requests microphone first when both permissions are missing`() {
        val result = decideFloatingRecorderOverlayEnableAction(
            hasMicrophonePermission = false,
            hasOverlayPermission = false,
        )

        assertEquals(FloatingRecorderOverlayEnableAction.RequestMicrophonePermission, result)
    }
}
