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
import java.io.File

interface FileDataSource {

    fun getRecordingDir(): File?

    @Throws(CantCreateFileException::class)
    fun createRecordFile(fileName: String): File

    fun deleteRecordFile(path: String): Boolean

    fun markAsRecordDeleted(path: String): String?

    fun unmarkRecordAsDeleted(path: String): String?

    fun renameFile(path: String, newName: String): File?

    @Throws(IllegalArgumentException::class)
    fun getAvailableSpace(): Long

    fun requestSystemMoreMemory(context: Context, file: File, requiredSpace: Long)
}
