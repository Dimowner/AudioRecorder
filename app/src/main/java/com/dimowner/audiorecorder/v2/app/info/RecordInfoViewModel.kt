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

package com.dimowner.audiorecorder.v2.app.info

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimowner.audiorecorder.v2.audio.readAuthorName
import com.dimowner.audiorecorder.v2.audio.readDescription
import com.dimowner.audiorecorder.v2.audio.writeCommentTag
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the RecordInfoScreen.
 * Loads the record's author name asynchronously from file metadata and allows
 * saving a description that is persisted to the database and written as a COMMENT tag.
 */
@HiltViewModel
class RecordInfoViewModel @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val recordsDataSource: RecordsDataSource,
) : ViewModel() {

    /**
     * Null means the author name is still loading.
     * An empty string means no author tag was found.
     * A non-empty string is the resolved author name.
     */
    private val _authorName = mutableStateOf<String?>(null)
    val authorName: State<String?> = _authorName

    /**
     * Null means the description is still loading.
     * Holds the loaded or saved description.
     */
    private val _description = mutableStateOf<String?>(null)
    val description: State<String?> = _description

    /** True while a save operation is in progress. */
    private val _isSaving = mutableStateOf(false)
    val isSaving: State<Boolean> = _isSaving

    fun loadAuthorName(filePath: String) {
        if (_authorName.value != null) return // already loaded
        viewModelScope.launch {
            val name = withContext(ioDispatcher) {
                File(filePath).readAuthorName()
            }
            _authorName.value = name
        }
    }

    /**
     * Loads the description dynamically. It attempts to read it from database first,
     * and as a fallback reads directly from the physical file tags via [readDescription].
     * Synchronizes database if tag is found and DB description is blank.
     */
    fun loadDescription(recordId: Long, filePath: String, fallback: String) {
        if (_description.value != null) return // already loaded
        viewModelScope.launch {
            val desc = withContext(ioDispatcher) {
                val dbRecord = recordsDataSource.getRecord(recordId)
                if (dbRecord != null && dbRecord.description.isNotBlank()) {
                    dbRecord.description
                } else {
                    val fileDesc = File(filePath).readDescription()
                    if (fileDesc.isNotBlank() && dbRecord != null) {
                        try {
                            recordsDataSource.updateRecord(dbRecord.copy(description = fileDesc))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to cache file description in DB")
                        }
                        fileDesc
                    } else {
                        dbRecord?.description ?: fallback
                    }
                }
            }
            _description.value = desc
        }
    }

    /**
     * Saves the description to the Room database and writes it as the COMMENT tag
     * in the audio file metadata.
     *
     * @param recordId  The database id of the record to update.
     * @param filePath  Absolute path to the audio file.
     * @param description The new description text.
     * @param onDone    Called on the main thread when the save completes (success or failure).
     */
    fun saveDescription(
        recordId: Long,
        filePath: String,
        description: String,
        onDone: (success: Boolean) -> Unit = {},
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            val success = withContext(ioDispatcher) {
                try {
                    val record = recordsDataSource.getRecord(recordId)
                    if (record != null) {
                        recordsDataSource.updateRecord(record.copy(description = description))
                        val file = File(filePath)
                        file.writeCommentTag(description)
                        true
                    } else {
                        Timber.w("saveDescription: record $recordId not found")
                        false
                    }
                } catch (e: Exception) {
                    Timber.e(e, "saveDescription failed for record $recordId")
                    false
                }
            }
            if (success) {
                _description.value = description
            }
            _isSaving.value = false
            onDone(success)
        }
    }
}
