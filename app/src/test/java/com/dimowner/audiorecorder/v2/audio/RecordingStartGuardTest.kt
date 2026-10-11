package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.*
import org.junit.Test

class RecordingStartGuardTest {
    @Test fun `stop after readiness stays cancelled through database preparation`() {
        val guard = RecordingStartGuard()
        guard.begin()
        assertTrue(guard.mayStart(routeReady = true))
        guard.requestStop()
        assertFalse(guard.mayStart(routeReady = true))
        assertFalse(guard.mayStart(routeReady = true))
        guard.begin()
        assertTrue(guard.mayStart(routeReady = true))
    }

    @Test fun `disconnection after preflight cannot pass the final startup check`() {
        val guard = RecordingStartGuard()
        guard.begin()
        assertTrue(guard.mayStart(routeReady = true))
        assertFalse(guard.mayStart(routeReady = false))
        guard.requestStop()
        assertFalse(guard.mayStart(routeReady = true))
    }
}
