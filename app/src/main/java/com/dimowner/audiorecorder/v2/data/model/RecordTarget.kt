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

import android.net.Uri
import java.io.File

/**
 * Destination of a new recording: either a file in the app-private records directory (default)
 * or a SAF document inside the user-selected public directory (no storage permission required).
 *
 * [pathOrUri] is what gets persisted in [Record.path] — an absolute file path or a
 * content:// document Uri string.
 */
sealed class RecordTarget {

    abstract val pathOrUri: String

    /** File name including extension. May differ from the requested one on name collision. */
    abstract val name: String

    /** Creation timestamp in milliseconds. */
    abstract val created: Long

    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')

    data class LocalFile(val file: File) : RecordTarget() {
        override val pathOrUri: String get() = file.absolutePath
        override val name: String get() = file.name
        override val created: Long get() = file.lastModified()
    }

    data class PublicDocument(
        val uri: Uri,
        override val name: String,
        override val created: Long,
    ) : RecordTarget() {
        override val pathOrUri: String get() = uri.toString()
    }
}
