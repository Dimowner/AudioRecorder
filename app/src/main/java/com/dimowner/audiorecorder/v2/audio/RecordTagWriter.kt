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

import com.dimowner.audiorecorder.v2.DefaultValues
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File

/**
 * Writes ARTIST and TITLE metadata tags to the recorded audio file.
 *
 * @receiver The recorded audio file to tag.
 * @param recordName The name of the record to set as the title tag.
 * @param authorName The author/artist name to set as the artist tag. Defaults to "Audio Recorder".
 */
fun File.writeTags(
    recordName: String,
    authorName: String = DefaultValues.DEFAULT_RECORD_AUTHOR_NAME,
) {
    try {
        val audioFile = AudioFileIO.read(this)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(FieldKey.ARTIST, authorName)
        tag.setField(FieldKey.TITLE, recordName)
        AudioFileIO.write(audioFile)
        Timber.d("Tags written successfully for file: ${name}, title: $recordName, artist: $authorName")
    } catch (e: Exception) {
        Timber.e(e, "Failed to write tags for file: ${name}")
    }
}

/**
 * Writes or removes the COMMENT metadata tag on an audio file.
 * When [description] is empty the COMMENT field is deleted from the file;
 * otherwise it is set to [description].
 *
 * @receiver The audio file to tag.
 * @param description The description text to store as the COMMENT tag, or empty to remove it.
 */
fun File.writeCommentTag(description: String) {
    try {
        val audioFile = AudioFileIO.read(this)
        val tag = audioFile.tagOrCreateAndSetDefault
        if (description.isEmpty()) {
            tag.deleteField(FieldKey.COMMENT)
        } else {
            tag.setField(FieldKey.COMMENT, description)
        }
        AudioFileIO.write(audioFile)
        Timber.d("Comment tag written successfully for file: $name")
    } catch (e: Exception) {
        Timber.e(e, "Failed to write comment tag for file: $name")
    }
}

