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

package com.dimowner.audiorecorder.v2.data

import android.content.Context
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.v2.data.model.RecordTarget
import java.io.File

interface FileDataSource {

    fun getRecordingDir(): File?

    @Throws(CantCreateFileException::class)
    fun createRecordFile(fileName: String): File

    /**
     * Creates the destination for a new recording honoring the user-selected public directory
     * setting: a SAF document in the picked directory when set (falling back to the private
     * directory when the picked directory is no longer accessible), a private file otherwise.
     */
    @Throws(CantCreateFileException::class)
    fun createRecordTarget(fileName: String): RecordTarget

    /** Deletes a record file addressed by an absolute path or a content:// document Uri string. */
    fun deleteRecordFile(path: String): Boolean

    @Deprecated("Not used anymore as redundant complexity logic")
    fun markAsRecordDeleted(path: String): String?

    @Deprecated("Not used anymore as redundant complexity logic")
    fun unmarkRecordAsDeleted(path: String): String?

    fun renameFile(path: String, newName: String): File?

    /**
     * Renames a record file addressed by an absolute path or a content:// document Uri string,
     * keeping the original extension.
     * @return the new path/Uri string, or null on failure.
     */
    fun renameRecordFile(pathOrUri: String, newName: String): String?

    @Throws(IllegalArgumentException::class)
    fun getAvailableSpace(): Long

    /**
     * Available space in bytes at the storage hosting [pathOrUri] (file path or document Uri).
     * Null falls back to the private records directory.
     */
    fun getAvailableSpace(pathOrUri: String?): Long

    fun requestSystemMoreMemory(context: Context, file: File, requiredSpace: Long)
}
