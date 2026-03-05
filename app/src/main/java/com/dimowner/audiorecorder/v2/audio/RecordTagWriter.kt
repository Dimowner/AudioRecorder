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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordTagWriter @Inject constructor() {

    /**
     * Writes metadata tags to the recorded audio file.
     * Sets the artist/author tag to the given authorName and the title tag to the record name.
     *
     * @param file The recorded audio file to tag.
     * @param recordName The name of the record to set as the title tag.
     * @param authorName The author/artist name to set as the artist tag. Defaults to "Audio Recorder".
     */
    fun writeTags(file: File, recordName: String, authorName: String = DefaultValues.DEFAULT_RECORD_AUTHOR_NAME) {
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            if (authorName.isNotBlank()) {
                tag.setField(FieldKey.ARTIST, authorName)
            }
            tag.setField(FieldKey.TITLE, recordName)
            AudioFileIO.write(audioFile)
            Timber.d("Tags written successfully for file: ${file.name}, title: $recordName, artist: $authorName")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write tags for file: ${file.name}")
        }
    }
}
