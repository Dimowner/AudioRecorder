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
package com.dimowner.audiorecorder.v2.data.model

/**
 * Outcome of a successful record file rename.
 *
 * [nameWithoutExtension] is the name the file actually got, which differs from the requested one
 * when the destination resolved a name collision (a DocumentsProvider renames to "Record (1).m4a"
 * instead of failing). It is what [Record.name] must be set to so the record name keeps matching
 * the file it points to.
 */
data class RenamedRecordFile(
    val pathOrUri: String,
    val nameWithoutExtension: String,
)
