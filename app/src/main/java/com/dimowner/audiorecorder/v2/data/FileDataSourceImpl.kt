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
import android.net.Uri
import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.v2.data.extensions.createDocumentInTree
import com.dimowner.audiorecorder.v2.data.extensions.createFile
import com.dimowner.audiorecorder.v2.data.extensions.deleteDocument
import com.dimowner.audiorecorder.v2.data.extensions.deleteFileAndChildren
import com.dimowner.audiorecorder.v2.data.extensions.getAvailableSpaceForDocument
import com.dimowner.audiorecorder.v2.data.extensions.getPrivateMusicStorageDir
import com.dimowner.audiorecorder.v2.data.extensions.hasPersistedTreePermission
import com.dimowner.audiorecorder.v2.data.extensions.isContentUri
import com.dimowner.audiorecorder.v2.data.extensions.markFileAsDeleted
import com.dimowner.audiorecorder.v2.data.extensions.renameDocumentWithExtension
import com.dimowner.audiorecorder.v2.data.extensions.renameFileWithExtension
import com.dimowner.audiorecorder.v2.data.extensions.requestAllocateSpace
import com.dimowner.audiorecorder.v2.data.extensions.unmarkFileAsDeleted
import com.dimowner.audiorecorder.v2.data.model.RecordTarget
import com.dimowner.audiorecorder.v2.data.model.RenamedRecordFile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class FileDataSourceImpl @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PrefsV2,
): FileDataSource {

    private val recordDirectory: File? by lazy {
        getPrivateMusicStorageDir(context, AppConstants.RECORDS_DIR)
    }

    override fun getRecordingDir(): File? {
        return recordDirectory
    }

    override fun createRecordFile(fileName: String): File {
        val recordFile = recordDirectory?.let {
            try {
                createFile(it, fileName)
            } catch (e: IOException) {
                Timber.e(e, "Failed to create record file with name: $fileName in directory: ${it.absolutePath}")
                throw CantCreateFileException()
            }
        }
        if (recordFile != null) {
            return recordFile
        }
        throw CantCreateFileException()
    }

    override fun createRecordTarget(fileName: String): RecordTarget {
        val publicDirUri = prefs.publicRecordingDirUri
        if (publicDirUri != null) {
            val treeUri = publicDirUri.toUri()
            if (hasPersistedTreePermission(context, treeUri)) {
                val document = createDocumentInTree(context, treeUri, fileName)
                val documentName = document?.name
                if (document != null && documentName != null) {
                    return RecordTarget.PublicDocument(
                        uri = document.uri,
                        name = documentName,
                        created = document.lastModified().takeIf { it > 0 }
                            ?: System.currentTimeMillis(),
                    )
                }
            }
            Timber.e(
                "Public recording dir is not accessible," +
                    " falling back to private storage: $publicDirUri"
            )
        }
        return RecordTarget.LocalFile(createRecordFile(fileName))
    }

    override fun deleteRecordFile(path: String): Boolean {
        return if (path.isContentUri()) {
            deleteDocument(context, path)
        } else {
            deleteFileAndChildren(File(path))
        }
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

    override fun renameRecordFile(pathOrUri: String, newName: String): RenamedRecordFile? {
        return if (pathOrUri.isContentUri()) {
            renameDocumentWithExtension(context, pathOrUri, newName)
        } else {
            renameFileWithExtension(File(pathOrUri), newName)?.let {
                RenamedRecordFile(it.absolutePath, it.nameWithoutExtension)
            }
        }
    }

    @SuppressLint("UsableSpace")
    override fun getAvailableSpace(): Long {
        return recordDirectory?.usableSpace ?: 0
    }

    override fun getAvailableSpace(pathOrUri: String?): Long {
        return if (pathOrUri != null && pathOrUri.isContentUri()) {
            getAvailableSpaceForDocument(context, pathOrUri)
        } else {
            getAvailableSpace()
        }
    }

    override fun requestSystemMoreMemory(context: Context, file: File, requiredSpace: Long) {
        requestAllocateSpace(context, file, requiredSpace)
    }
}
