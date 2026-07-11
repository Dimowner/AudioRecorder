package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStoppedRenamePolicyTest {

    @Test
    fun `in-app stop shows only in-app rename dialog`() {
        val policy = recordingStoppedRenamePolicy(
            askToRenameAfterRecordingStopped = true,
            recordId = 7L,
            stoppedFromFloatingOverlay = false,
        )

        assertTrue(policy.showInAppRenameDialog)
        assertFalse(policy.showFloatingOverlayRenameDialog)
    }

    @Test
    fun `overlay stop shows only floating overlay rename dialog`() {
        val policy = recordingStoppedRenamePolicy(
            askToRenameAfterRecordingStopped = true,
            recordId = 7L,
            stoppedFromFloatingOverlay = true,
        )

        assertFalse(policy.showInAppRenameDialog)
        assertTrue(policy.showFloatingOverlayRenameDialog)
    }

    @Test
    fun `rename disabled suppresses both post-recording rename surfaces`() {
        val policy = recordingStoppedRenamePolicy(
            askToRenameAfterRecordingStopped = false,
            recordId = 7L,
            stoppedFromFloatingOverlay = true,
        )

        assertFalse(policy.showInAppRenameDialog)
        assertFalse(policy.showFloatingOverlayRenameDialog)
    }

    @Test
    fun `invalid record id suppresses both post-recording rename surfaces`() {
        val policy = recordingStoppedRenamePolicy(
            askToRenameAfterRecordingStopped = true,
            recordId = -1L,
            stoppedFromFloatingOverlay = true,
        )

        assertFalse(policy.showInAppRenameDialog)
        assertFalse(policy.showFloatingOverlayRenameDialog)
    }
}
