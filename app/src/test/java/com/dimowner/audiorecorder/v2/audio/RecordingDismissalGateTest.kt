package com.dimowner.audiorecorder.v2.audio

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecordingDismissalGateTest {
    @Test
    fun `idle dismissal completes without a recorder stop`() = runTest {
        val gate = RecordingDismissalGate()
        assertTrue(gate.dismiss { fail("An idle service has no recorder to stop") })
    }

    @Test
    fun `dismissal waits for saving even after recorder stop returns`() = runTest {
        val gate = RecordingDismissalGate()
        gate.recordingStarted()
        var stopRequests = 0
        val result = async { gate.dismiss { stopRequests++ } }
        runCurrent()
        assertEquals(1, stopRequests)
        assertTrue(gate.isDismissalRequested)
        assertFalse(result.isCompleted)
        gate.recordingFinished(saved = true)
        assertTrue(result.await())
    }

    @Test
    fun `failed save does not report a successful dismissal`() = runTest {
        val gate = RecordingDismissalGate()
        gate.recordingStarted()
        val result = async { gate.dismiss {} }
        runCurrent()
        gate.recordingFinished(saved = false)
        assertFalse(result.await())
        assertTrue(gate.dismiss { fail("Failed save already finished; nothing remains to stop") })
    }

    @Test
    fun `next recording clears temporary dismissal suppression`() = runTest {
        val gate = RecordingDismissalGate()
        gate.dismiss {}
        gate.recordingStarted()
        assertFalse(gate.isDismissalRequested)
        gate.recordingFinished(saved = true)
    }

    @Test
    fun `duplicate start cannot orphan a pending dismissal`() = runTest {
        val gate = RecordingDismissalGate()
        gate.recordingStarted()
        val result = async { gate.dismiss {} }
        runCurrent()
        assertFalse(gate.recordingStarted())
        assertTrue(gate.isDismissalRequested)
        gate.recordingFinished(saved = true)
        runCurrent()
        assertTrue(result.isCompleted)
        assertTrue(result.await())
    }
}
