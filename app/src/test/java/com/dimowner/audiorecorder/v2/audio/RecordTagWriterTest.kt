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

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordTagWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var audioFile: AudioFile
    private lateinit var tag: Tag
    private lateinit var testFile: java.io.File

    @Before
    fun setUp() {
        mockkStatic(AudioFileIO::class)
        audioFile = mockk(relaxed = true)
        tag = mockk(relaxed = true)
        testFile = tempFolder.newFile("test.mp3")

        every { AudioFileIO.read(testFile) } returns audioFile
        every { audioFile.tagOrCreateAndSetDefault } returns tag
        every { AudioFileIO.write(audioFile) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(AudioFileIO::class)
    }

    // ── writeCommentTag ───────────────────────────────────────────────────────

    @Test
    fun `writeCommentTag deletes COMMENT field when description is empty string`() {
        testFile.writeCommentTag("")

        verify { tag.deleteField(FieldKey.COMMENT) }
        verify(exactly = 0) { tag.setField(FieldKey.COMMENT, any()) }
        verify { AudioFileIO.write(audioFile) }
    }

    @Test
    fun `writeCommentTag sets COMMENT field when description is non-empty`() {
        testFile.writeCommentTag("My recording notes")

        verify { tag.setField(FieldKey.COMMENT, "My recording notes") }
        verify(exactly = 0) { tag.deleteField(FieldKey.COMMENT) }
        verify { AudioFileIO.write(audioFile) }
    }

    @Test
    fun `writeCommentTag still writes file after deleting COMMENT field`() {
        testFile.writeCommentTag("")

        verify(ordering = io.mockk.Ordering.ORDERED) {
            tag.deleteField(FieldKey.COMMENT)
            AudioFileIO.write(audioFile)
        }
    }
}