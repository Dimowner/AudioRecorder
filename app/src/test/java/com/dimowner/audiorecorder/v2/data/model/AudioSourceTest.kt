/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceTest {

    @Test
    fun `every persisted value is unique`() {
        val values = AudioSource.entries.map { it.value }
        assertEquals("two sources share a stored value", values.size, values.toSet().size)
    }

    /**
     * SYSTEM_AUDIO is not a platform capture source, so its stored value is a sentinel. Keeping it
     * negative is what guarantees it cannot collide with a `MediaRecorder.AudioSource` constant,
     * now or when the platform adds more of them.
     */
    @Test
    fun `the system audio sentinel cannot collide with a platform source`() {
        assertTrue(AudioSource.SYSTEM_AUDIO.value < 0)
        AudioSource.entries.filterNot { it.isSystemAudio }.forEach {
            assertTrue("${it.name} is not a platform source value", it.value >= 0)
        }
    }

    @Test
    fun `fromValue round-trips every source`() {
        AudioSource.entries.forEach {
            assertEquals(it, AudioSource.fromValue(it.value))
        }
    }

    @Test
    fun `an unknown stored value falls back to the default`() {
        assertEquals(AudioSource.DEFAULT, AudioSource.fromValue(Int.MIN_VALUE))
    }

    @Test
    fun `only SYSTEM_AUDIO reports isSystemAudio`() {
        assertTrue(AudioSource.SYSTEM_AUDIO.isSystemAudio)
        AudioSource.entries.filter { it != AudioSource.SYSTEM_AUDIO }.forEach {
            assertFalse("${it.name} must not report as system audio", it.isSystemAudio)
        }
    }
}
