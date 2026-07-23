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

import android.os.Parcelable
import com.dimowner.audiorecorder.v2.data.model.NameFormat
import com.dimowner.audiorecorder.v2.data.model.NameFormatToken
import kotlinx.parcelize.Parcelize

/**
 * @param tokens Elements the format is built from, in the order they are rendered.
 * @param preview The record name [tokens] currently produce, extension included.
 * @param selectedPreset The preset [tokens] match exactly, or `null` when the format is custom.
 */
@Parcelize
data class NameFormatConstructorState(
    val tokens: List<NameFormatToken> = emptyList(),
    val preview: String = "",
    val selectedPreset: NameFormat? = null,
) : Parcelable

sealed class NameFormatConstructorAction {
    data object InitScreen : NameFormatConstructorAction()
    data class AddToken(val token: NameFormatToken) : NameFormatConstructorAction()
    data class RemoveToken(val index: Int) : NameFormatConstructorAction()
    data class MoveToken(val fromIndex: Int, val toIndex: Int) : NameFormatConstructorAction()
    data class ApplyPreset(val preset: NameFormat) : NameFormatConstructorAction()
    data object ClearTokens : NameFormatConstructorAction()
    data object Save : NameFormatConstructorAction()
}
