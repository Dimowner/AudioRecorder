package com.dimowner.audiorecorder.v2.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFileNameCleanerTest {

    @Test
    fun `cleanRecordFileNameForSave deletes characters that common filesystems reject`() {
        val result = cleanRecordFileNameForSave(
            "  road / bridge \\ tunnel: <left>|right? *today* \"quote\"\u0000  "
        )

        assertEquals("road bridge tunnel leftright today quote", result)
    }

    @Test
    fun `cleanRecordFileNameForSave trims Windows-invalid trailing dots and spaces`() {
        val result = cleanRecordFileNameForSave("trip notes...   ")

        assertEquals("trip notes", result)
    }

    @Test
    fun `cleanRecordFileNameForSave rejects Windows reserved device names`() {
        val result = cleanRecordFileNameForSave("CON.txt")

        assertEquals("", result)
    }
}
