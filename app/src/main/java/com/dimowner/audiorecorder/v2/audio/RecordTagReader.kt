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

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File

/**
 * Reads the ARTIST metadata tag from an audio file.
 *
 * @receiver The audio file to read tags from.
 * @return The artist/author name stored in the file's tag, or an empty string if not present or
 *         unreadable.
 */
fun File.readAuthorName(): String {
    return try {
        val audioFile = AudioFileIO.read(this)
        val tag = audioFile.tag ?: return ""
        tag.getFirst(FieldKey.ARTIST) ?: ""
    } catch (e: Exception) {
        Timber.w(e, "Failed to read author tag from file: $name")
        ""
    }
}
