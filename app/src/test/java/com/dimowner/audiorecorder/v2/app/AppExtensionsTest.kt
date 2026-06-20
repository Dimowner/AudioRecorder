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

package com.dimowner.audiorecorder.v2.app

import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppExtensionsTest {

    // -------------------------------------------------------------------------
    // isDescriptionFileWriteSupported
    // -------------------------------------------------------------------------

    @Test
    fun `isDescriptionFileWriteSupported returns false for 3gp lowercase`() {
        assertFalse(isDescriptionFileWriteSupported(RecordingFormat.ThreeGp.value))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns false for 3GP uppercase`() {
        assertFalse(isDescriptionFileWriteSupported("3GP"))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns false for 3Gp mixed case`() {
        assertFalse(isDescriptionFileWriteSupported("3Gp"))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns true for m4a`() {
        assertTrue(isDescriptionFileWriteSupported(RecordingFormat.M4a.value))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns true for wav`() {
        assertTrue(isDescriptionFileWriteSupported(RecordingFormat.Wav.value))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns true for empty string`() {
        assertTrue(isDescriptionFileWriteSupported(""))
    }

    @Test
    fun `isDescriptionFileWriteSupported returns true for unknown format`() {
        assertTrue(isDescriptionFileWriteSupported("mp3"))
    }
}