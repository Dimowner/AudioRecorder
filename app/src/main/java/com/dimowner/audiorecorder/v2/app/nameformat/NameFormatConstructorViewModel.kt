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

package com.dimowner.audiorecorder.v2.app.nameformat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.dimowner.audiorecorder.util.FileUtil
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.NameFormatToken
import com.dimowner.audiorecorder.v2.data.model.NameFormatTokenType
import com.dimowner.audiorecorder.v2.data.model.formatRecordName
import com.dimowner.audiorecorder.v2.data.model.matchingPreset
import com.dimowner.audiorecorder.v2.data.model.presetTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Upper bound on the element count, a longer name format would not fit a readable file name. */
const val MAX_NAME_FORMAT_TOKENS = 20

/** Upper bound on the length of a single text element. */
const val MAX_NAME_FORMAT_TEXT_LENGTH = 30

/**
 * Upper bound on the rendered name length. Needed in addition to [MAX_NAME_FORMAT_TOKENS] and
 * [MAX_NAME_FORMAT_TEXT_LENGTH], since those alone still allow a format that renders to a
 * unreadably long name, e.g. [MAX_NAME_FORMAT_TOKENS] text elements [MAX_NAME_FORMAT_TEXT_LENGTH]
 * characters long each.
 */
const val MAX_NAME_FORMAT_LENGTH = 100

private const val PREVIEW_EXTENSION = ".m4a"

@HiltViewModel
internal class NameFormatConstructorViewModel @Inject constructor(
    private val prefs: PrefsV2,
) : ViewModel() {

    private val _state: MutableState<NameFormatConstructorState> =
        mutableStateOf(NameFormatConstructorState())

    val state: State<NameFormatConstructorState> = _state

    fun onAction(action: NameFormatConstructorAction) {
        when (action) {
            NameFormatConstructorAction.InitScreen -> initScreen()
            is NameFormatConstructorAction.AddToken -> addToken(action.token)
            is NameFormatConstructorAction.RemoveToken -> removeToken(action.index)
            is NameFormatConstructorAction.ApplyPreset -> applyPreset(action.preset)
            NameFormatConstructorAction.ClearTokens -> updateTokens(emptyList())
            NameFormatConstructorAction.Save -> save()
        }
    }

    /**
     * Loads the format that is currently in use, so that the user edits what they already have
     * rather than starting from an empty screen.
     */
    private fun initScreen() {
        val format = prefs.settingNamingFormat
        updateTokens(format.presetTokens() ?: prefs.customNameFormat)
    }

    private fun addToken(token: NameFormatToken) {
        if (_state.value.tokens.size >= MAX_NAME_FORMAT_TOKENS) return
        val sanitized = if (token.type == NameFormatTokenType.Text) {
            token.copy(value = token.value.trim().take(MAX_NAME_FORMAT_TEXT_LENGTH))
        } else {
            token
        }
        //A text element without text would silently render to nothing.
        if (sanitized.type == NameFormatTokenType.Text && sanitized.value.isEmpty()) return
        val newTokens = _state.value.tokens + sanitized
        if (newTokens.formatRecordName(counter = prefs.recordCounter).length > MAX_NAME_FORMAT_LENGTH) return
        updateTokens(newTokens)
    }

    private fun removeToken(index: Int) {
        val tokens = _state.value.tokens
        if (index !in tokens.indices) return
        updateTokens(tokens.filterIndexed { i, _ -> i != index })
    }

    private fun applyPreset(preset: NameFormat) {
        updateTokens(preset.presetTokens() ?: return)
    }

    private fun updateTokens(tokens: List<NameFormatToken>) {
        _state.value = NameFormatConstructorState(
            tokens = tokens,
            preview = tokens.toPreview(),
            selectedPreset = tokens.matchingPreset(),
        )
    }

    /**
     * Renders the name the current tokens produce for the next recording. Sanitized the same way as
     * an actual record name, so that the preview cannot promise a name the file system rejects.
     */
    private fun List<NameFormatToken>.toPreview(): String {
        if (isEmpty()) return ""
        val name = FileUtil.removeUnallowedSignsFromName(
            formatRecordName(counter = prefs.recordCounter)
        )
        return if (name.isBlank()) "" else name + PREVIEW_EXTENSION
    }

    /**
     * Stores the constructed format. A format matching a preset is stored as that preset instead of
     * a custom one, so that the settings dropdown does not end up with two identical entries.
     */
    private fun save() {
        val tokens = _state.value.tokens
        if (tokens.isEmpty()) return
        val preset = tokens.matchingPreset()
        if (preset != null) {
            prefs.customNameFormat = emptyList()
            prefs.settingNamingFormat = preset
        } else {
            prefs.customNameFormat = tokens
            prefs.settingNamingFormat = NameFormat.Custom
        }
    }
}
