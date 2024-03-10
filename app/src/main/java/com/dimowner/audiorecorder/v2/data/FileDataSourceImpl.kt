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

import android.annotation.SuppressLint
import android.content.Context
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.v2.data.extensions.createFile
import com.dimowner.audiorecorder.v2.data.extensions.deleteFileAndChildren
import com.dimowner.audiorecorder.v2.data.extensions.getPrivateMusicStorageDir
import com.dimowner.audiorecorder.v2.data.extensions.markFileAsDeleted
import com.dimowner.audiorecorder.v2.data.extensions.renameFileWithExtension
import com.dimowner.audiorecorder.v2.data.extensions.requestAllocateSpace
import com.dimowner.audiorecorder.v2.data.extensions.unmarkFileAsDeleted
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File

class FileDataSourceImpl(
    @ApplicationContext context: Context
): FileDataSource {

    private val recordDirectory: File? by lazy {
        getPrivateMusicStorageDir(context, AppConstants.RECORDS_DIR)
    }

    override fun getRecordingDir(): File? {
        return recordDirectory
    }

    override fun createRecordFile(fileName: String): File {
        val recordFile = recordDirectory?.let {
            createFile(it, fileName)
        }
        if (recordFile != null) {
            return recordFile
        }
        throw CantCreateFileException()
    }

    override fun deleteRecordFile(path: String): Boolean {
        return deleteFileAndChildren(File(path))
    }

    override fun markAsRecordDeleted(path: String): String? {
        return markFileAsDeleted(File(path))?.absolutePath
    }

    override fun unmarkRecordAsDeleted(path: String): String? {
        return unmarkFileAsDeleted(File(path))?.absolutePath
    }

    override fun renameFile(path: String, newName: String): File? {
        return renameFileWithExtension(File(path), newName)
    }

    @SuppressLint("UsableSpace")
    override fun getAvailableSpace(): Long {
        return recordDirectory?.usableSpace ?: 0
    }

    override fun requestSystemMoreMemory(context: Context, file: File, requiredSpace: Long) {
        requestAllocateSpace(context, file, requiredSpace)
    }
}
