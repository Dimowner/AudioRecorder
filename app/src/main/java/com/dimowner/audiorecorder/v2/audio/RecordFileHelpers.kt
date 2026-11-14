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

import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.audio.AudioDecoder
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.convertToRecordingFormat
import java.io.File

/*
 * Record bookkeeping shared by everything that turns an audio file into a row in the records
 * table: the recording service (one file per recording or part) and the instant replay buffer
 * (one file per save). Keeping it in one place means both paths name, size and tag records the
 * same way.
 */

/**
 * Builds the database row for a record file that was just created by
 * [com.dimowner.audiorecorder.v2.data.FileDataSource.createRecordFile].
 *
 * The name is taken from the file rather than the requested name, because the data source may
 * have added a suffix to avoid a collision. Duration and size start at zero; they are filled in
 * by [completeFromFile] once the audio is written.
 */
internal fun newRecordEntity(
    recordFile: File,
    format: RecordingFormat,
    sampleRate: Int,
    channelCount: Int,
    bitrate: Int,
): Record = Record(
    id = 0,
    name = recordFile.nameWithoutExtension,
    durationMills = 0,
    created = recordFile.lastModified(),
    added = System.currentTimeMillis(),
    removed = Long.MAX_VALUE,
    path = recordFile.absolutePath,
    format = format.value,
    size = 0,
    sampleRate = sampleRate,
    channelCount = channelCount,
    bitrate = if (format.hasBitrate) bitrate else 0,
    isBookmarked = false,
    isWaveformProcessed = false,
    isMovedToRecycle = false,
    amps = IntArray(ARApplication.longWaveformSampleCount),
    description = "",
)

/**
 * Reads the finished audio file's real metadata, writes the TITLE/ARTIST tags, and returns this
 * record updated with what the file actually contains.
 *
 * @throws IllegalStateException or [OutOfMemoryError] as thrown by [AudioDecoder.readRecordInfo]
 */
internal fun Record.completeFromFile(file: File, authorName: String): Record {
    val info = AudioDecoder.readRecordInfo(file)
    file.writeTags(name, authorName)
    return copy(
        durationMills = info.duration / 1000,
        format = info.format,
        size = info.size,
        sampleRate = info.sampleRate,
        channelCount = info.channelCount,
        bitrate = if (format.convertToRecordingFormat()?.hasBitrate == true) info.bitrate else 0,
    )
}
