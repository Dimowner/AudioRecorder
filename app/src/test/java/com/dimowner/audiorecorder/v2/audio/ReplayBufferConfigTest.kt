/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayBufferConfigTest {

    @Test
    fun `format is 16 kHz mono 16-bit`() {
        assertEquals(2, ReplayBufferConfig.FRAME_SIZE)
        assertEquals(32_000, ReplayBufferConfig.BYTES_PER_SECOND)
    }

    @Test
    fun `buffer size grows linearly with minutes`() {
        assertEquals(9_600_000L, ReplayBufferConfig.bufferSizeBytes(5))
        assertEquals(19_200_000L, ReplayBufferConfig.bufferSizeBytes(10))
        assertEquals(115_200_000L, ReplayBufferConfig.bufferSizeBytes(60))
        ReplayBufferConfig.DURATION_OPTIONS_MINUTES.forEach {
            assertEquals(0L, ReplayBufferConfig.bufferSizeBytes(it) % ReplayBufferConfig.FRAME_SIZE)
        }
    }

    @Test
    fun `unknown durations fall back to the default`() {
        assertEquals(15, ReplayBufferConfig.sanitizeMinutes(15))
        assertEquals(ReplayBufferConfig.DEFAULT_DURATION_MINUTES, ReplayBufferConfig.sanitizeMinutes(0))
        assertEquals(ReplayBufferConfig.DEFAULT_DURATION_MINUTES, ReplayBufferConfig.sanitizeMinutes(7))
    }

    @Test
    fun `space check needs room for the buffer and a saved copy on each volume`() {
        val need = ReplayBufferConfig.bufferSizeBytes(10) + ReplayBufferConfig.FREE_SPACE_MARGIN_BYTES
        assertTrue(ReplayBufferConfig.hasEnoughSpace(10, need, need))
        assertFalse(ReplayBufferConfig.hasEnoughSpace(10, need - 1, need))
        assertFalse(ReplayBufferConfig.hasEnoughSpace(10, need, need - 1))
    }
}
