package com.dimowner.audiorecorder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioUtilsTest {

    @Test
    fun `sumOfAmplitudes returns zero for empty data`() {
        val data = ByteArray(0)
        assertEquals(0L, data.sumOfAmplitudes(0))
    }

    @Test
    fun `sumOfAmplitudes ignores single trailing byte`() {
        val data = byteArrayOf(0x7F)
        assertEquals(0L, data.sumOfAmplitudes(1))
    }

    @Test
    fun `sumOfAmplitudes single positive sample`() {
        // Little-endian: low=0x01, high=0x00 -> sample = 1
        val data = byteArrayOf(0x01, 0x00)
        assertEquals(1L, data.sumOfAmplitudes(2))
    }

    @Test
    fun `sumOfAmplitudes single negative sample`() {
        // Little-endian: low=0xFF, high=0xFF -> sample = -1, abs = 1
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        assertEquals(1L, data.sumOfAmplitudes(2))
    }

    @Test
    fun `sumOfAmplitudes max positive sample`() {
        // Little-endian: low=0xFF, high=0x7F -> sample = 32767
        val data = byteArrayOf(0xFF.toByte(), 0x7F)
        assertEquals(32767L, data.sumOfAmplitudes(2))
    }

    @Test
    fun `sumOfAmplitudes min negative sample`() {
        // Little-endian: low=0x00, high=0x80 -> sample = -32768, abs = 32768
        val data = byteArrayOf(0x00, 0x80.toByte())
        assertEquals(32768L, data.sumOfAmplitudes(2))
    }

    @Test
    fun `sumOfAmplitudes multiple samples`() {
        // Sample 1: low=0x00, high=0x01 -> 256
        // Sample 2: low=0x00, high=0xFF -> -256, abs = 256
        val data = byteArrayOf(0x00, 0x01, 0x00, 0xFF.toByte())
        assertEquals(512L, data.sumOfAmplitudes(4))
    }

    @Test
    fun `sumOfAmplitudes respects bytesRead less than array length`() {
        // Only first 2 bytes should be read (sample = 1)
        val data = byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0x7F)
        assertEquals(1L, data.sumOfAmplitudes(2))
    }

    @Test
    fun `sumOfAmplitudes ignores trailing odd byte when bytesRead is odd`() {
        // bytesRead=3: only first 2 bytes form a sample (sample = 1), 3rd byte ignored
        val data = byteArrayOf(0x01, 0x00, 0xFF.toByte())
        assertEquals(1L, data.sumOfAmplitudes(3))
    }

    @Test
    fun `sumOfAmplitudes with zero samples`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        assertEquals(0L, data.sumOfAmplitudes(4))
    }

    @Test
    fun `sumOfAmplitudes large buffer`() {
        // 100 samples of value 1 (low=0x01, high=0x00)
        val data = ByteArray(200)
        for (i in 0 until 200 step 2) {
            data[i] = 0x01
            data[i + 1] = 0x00
        }
        assertEquals(100L, data.sumOfAmplitudes(200))
    }

    @Test
    fun `sumOfAmplitudes mixed positive and negative samples`() {
        // Sample 1: low=0x00, high=0x01 -> 256
        // Sample 2: low=0x00, high=0xFF -> -256, abs = 256
        // Sample 3: low=0x05, high=0x00 -> 5
        val data = byteArrayOf(0x00, 0x01, 0x00, 0xFF.toByte(), 0x05, 0x00)
        assertEquals(517L, data.sumOfAmplitudes(6))
    }
}

