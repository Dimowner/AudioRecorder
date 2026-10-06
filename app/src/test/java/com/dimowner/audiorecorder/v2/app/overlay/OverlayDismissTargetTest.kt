package com.dimowner.audiorecorder.v2.app.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayDismissTargetTest {
    @Test
    fun `overlapping circles hit including their shared boundary`() {
        assertTrue(OverlayDismissTarget.overlaps(100f, 200f, 28f, 100f, 200f, 32f))
        assertTrue(OverlayDismissTarget.overlaps(160f, 200f, 28f, 100f, 200f, 32f))
        assertFalse(OverlayDismissTarget.overlaps(161f, 200f, 28f, 100f, 200f, 32f))
    }

    @Test
    fun `bounding box corners outside the circular target do not dismiss`() {
        assertFalse(OverlayDismissTarget.overlaps(160f, 260f, 28f, 100f, 200f, 32f))
    }

    @Test
    fun `hit test uses actual resized bubble radius`() {
        assertFalse(OverlayDismissTarget.overlaps(200f, 200f, 28f, 100f, 200f, 32f))
        assertTrue(OverlayDismissTarget.overlaps(200f, 200f, 80f, 100f, 200f, 32f))
    }
}
