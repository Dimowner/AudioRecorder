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
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the RecordInfoScreen.
 * Loads the record's author name asynchronously from file metadata.
 */
@HiltViewModel
class RecordInfoViewModel @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Null means the author name is still loading.
     * An empty string means no author tag was found.
     * A non-empty string is the resolved author name.
     */
    private val _authorName = mutableStateOf<String?>(null)
    val authorName: State<String?> = _authorName

    fun loadAuthorName(filePath: String) {
        if (_authorName.value != null) return // already loaded
        viewModelScope.launch {
            val name = withContext(ioDispatcher) {
                File(filePath).readAuthorName()
            }
            _authorName.value = name
        }
    }
}


