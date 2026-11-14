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

import android.content.Context
import com.dimowner.audiorecorder.app.DecodeService
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.v2.app.getNewRecordName
import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Outcome of [ReplayBufferSaver.save]. */
sealed interface ReplayBufferSaveResult {
    data class Saved(val recordId: Long, val recordName: String) : ReplayBufferSaveResult
    data object NothingBuffered : ReplayBufferSaveResult
    data object NotEnoughSpace : ReplayBufferSaveResult
    data object Failed : ReplayBufferSaveResult
}

/**
 * Turns the current contents of the instant replay buffer into a normal record: a WAV file in
 * the recording directory, named by the user's naming format, with a row in the records table
 * and its waveform queued for decoding - the same steps a regular recording goes through.
 *
 * Buffering continues during and after the save.
 */
class ReplayBufferSaver @Inject constructor(
    private val recorder: ReplayBufferRecorder,
    private val prefs: PrefsV2,
    private val fileDataSource: FileDataSource,
    private val recordsDataSource: RecordsDataSource,
    @param:ApplicationContext private val context: Context,
) {

    /** Does blocking file I/O; call from a background dispatcher. */
    suspend fun save(): ReplayBufferSaveResult {
        val buffered = recorder.bufferedBytes()
        if (buffered <= 0) return ReplayBufferSaveResult.NothingBuffered
        if (fileDataSource.getAvailableSpace() < buffered + ReplayBufferConfig.FREE_SPACE_MARGIN_BYTES) {
            Timber.e("ReplayBufferSaver: not enough space to save $buffered bytes")
            return ReplayBufferSaveResult.NotEnoughSpace
        }
        val recordName = prefs.settingNamingFormat.getNewRecordName(prefs)
        val file = try {
            fileDataSource.createRecordFile("$recordName.${RecordingFormat.Wav.value}")
        } catch (e: CantCreateFileException) {
            Timber.e(e, "ReplayBufferSaver: can't create record file")
            return ReplayBufferSaveResult.Failed
        }
        recorder.setSaving(true)
        try {
            val dataBytes = recorder.snapshotToWav(file)
            if (dataBytes <= 0) {
                file.delete()
                return ReplayBufferSaveResult.NothingBuffered
            }
            val record = newRecordEntity(
                recordFile = file,
                format = RecordingFormat.Wav,
                sampleRate = ReplayBufferConfig.SAMPLE_RATE,
                channelCount = ReplayBufferConfig.CHANNEL_COUNT,
                bitrate = 0,
            ).completeFromFile(file, prefs.recordAuthorName)
            val id = recordsDataSource.insertRecord(record)
            if (id < 0) {
                file.delete()
                return ReplayBufferSaveResult.Failed
            }
            DecodeService.startNotificationV2(context, id, record.path, record.durationMills)
            Timber.d("ReplayBufferSaver: saved ${record.name} ($dataBytes bytes)")
            recorder.notifySaved(id)
            return ReplayBufferSaveResult.Saved(id, record.name)
        } catch (e: IOException) {
            Timber.e(e, "ReplayBufferSaver: snapshot failed")
            deleteQuietly(file)
            return ReplayBufferSaveResult.Failed
        } catch (e: IllegalStateException) {
            // AudioDecoder.readRecordInfo could not parse the file it was handed.
            Timber.e(e, "ReplayBufferSaver: can't read the saved file")
            deleteQuietly(file)
            return ReplayBufferSaveResult.Failed
        } finally {
            recorder.setSaving(false)
        }
    }

    private fun deleteQuietly(file: File) {
        if (file.exists() && !file.delete()) Timber.w("ReplayBufferSaver: can't delete $file")
    }
}
