/*
 * Copyright 2024 Dmytro Ponomarenko
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
package com.dimowner.audiorecorder.v2.data.model

/**
 * Record name formats. Every entry except [Custom] is a preset built from a fixed list of
 * [NameFormatToken]s, see `NameFormat.presetTokens()`. [Custom] renders the tokens the user built
 * in the name format constructor and stored in `PrefsV2.customNameFormat`.
 */
enum class NameFormat {
    Record, Timestamp, Date, DateUs, DateIso8601, DateLong, Custom
}

fun String.convertToNameFormat(): NameFormat? {
    return NameFormat.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
}
