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

package com.dimowner.audiorecorder.v2.data.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.core.net.toUri
import com.dimowner.audiorecorder.v2.DefaultValues.DELETED_RECORD_MARK
import timber.log.Timber
import java.io.File
import java.io.IOException

private const val RETRY_COUNT = 3

/**
 * Create a file.
 * Also create parent directories if they are not exist.
 * If file with specified name already exists, add suffix (-1 or -2 or -3...) to the file name.
 * @param directory Path to directory.
 * @param fileName File name.
 */
@Throws(IOException::class)
fun createFile(directory: File, fileName: String): File {
    if (!directory.exists()) {
        directory.mkdirs() // Create the directory if it doesn't exist
    }

    var newFileName = fileName
    var suffix = 1

    // Check if the file with the same name already exists
    while (File(directory, newFileName).exists()) {
        // Append a numeric suffix to the file name
        newFileName = "${fileName.substringBeforeLast('.')}-$suffix.${fileName.substringAfterLast('.')}"
        suffix++
    }

    val file = File(directory, newFileName)
    try {
        file.createNewFile()
    } catch (e: IOException) {
        // Handle any exceptions related to file creation
        Timber.e(e)
        throw e
    }
    file.verifyCanReadWrite()
    return file
}

@Throws(IOException::class)
fun File.verifyCanReadWrite() {
    if (!this.canRead()) {
        throw IOException("Can't read file")
    } else if (!this.canWrite()) {
        throw IOException("Can't write file")
    }
}

fun deleteFileAndChildren(file: File): Boolean {
    if (!file.exists()) {
        // File doesn't exist, so nothing to delete
        return false
    }

    if (file.isDirectory) {
        // Recursively delete all files and subdirectories
        file.listFiles()?.forEach { child ->
            if (!deleteFileAndChildren(child)) {
                // Failed to delete a child file or directory
                return false
            }
        }
    }

    // Delete the current file or empty directory
    return file.delete()
}

/**
 * Rename a file
 * Example:
 * fileToRename: /data/Files/FileName.txt newName: RenamedFile
 * return: /data/Files/RenamedFile.txt
 * @param fileToRename File that needs to be renamed.
 * @param newName New file name
 * @return Renamed file
 */
fun renameFileWithExtension(fileToRename: File, newName: String): File? {
    if (!fileToRename.exists() || fileToRename.nameWithoutExtension == newName) {
        // Source file doesn't exist
        return null
    }

    // Get the original extension
    val originalExtension = fileToRename.extension

    // Append the original extension to the renamed file
    val newFileName = "${newName}.$originalExtension"
    val newFile = File(fileToRename.parentFile, newFileName)

    // Try renaming the file up to 3 times
    repeat(RETRY_COUNT) {
        if (fileToRename.renameTo(newFile)) {
            return newFile
        }
    }
    return null
}

fun markFileAsDeleted(file: File): File? {
    if (!file.exists()) {
        // File doesn't exist, so nothing to mark as deleted
        return null
    }

    val trashSuffix = DELETED_RECORD_MARK
    val originalName = file.name
    val trashName = "${originalName.removeSuffix(trashSuffix)}$trashSuffix"

    val trashFile = File(file.parentFile, trashName)

    // Rename the file to move it to the trash
    repeat(RETRY_COUNT) {
        if (file.renameTo(trashFile)) {
            return trashFile
        }
    }
    return null
}

fun unmarkFileAsDeleted(trashFile: File): File? {
    if (!trashFile.exists()) {
        // Trash file doesn't exist, nothing to unmark
        return null
    }

    val trashSuffix = DELETED_RECORD_MARK
    val originalName = trashFile.name.removeSuffix(trashSuffix)
    val restoredFile = File(trashFile.parentFile, originalName)

    // Rename the trash file back to its original name
    if (!trashFile.renameTo(restoredFile)) {
        if (!trashFile.renameTo(restoredFile)) {
            return if (trashFile.renameTo(restoredFile)) {
                restoredFile
            } else {
                null
            }
        }
    }
    return restoredFile
}

@SuppressLint("SdCardPath")
fun getPrivateMusicStorageDir(context: Context, directoryName: String): File? {
    // Get the app-specific directory for music files
    val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: context.filesDir // Fallback to internal storage if external storage is not available

    val directory = File(musicDir, directoryName)
    // Create the directory if it doesn't exist
    val result: File? = if (!directory.exists() && !directory.mkdirs()) {
        //App dir now is not available.
        //If nothing helped then hardcode recording dir
        val lastResortDirectory = File("/data/data/${context.packageName}/files/$directoryName")
        if (!lastResortDirectory.exists() && !lastResortDirectory.mkdirs()) {
            null
        } else {
            lastResortDirectory
        }
    } else {
        directory
    }
    return result
}

/**
 * Request system for free space. Call this function request system clear cached files
 * belonging to other apps (as needed) to meet request.
 * */
fun requestAllocateSpace(context: Context, file: File, requiredSpace: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val parcelFileDescriptor: ParcelFileDescriptor? =
            context.contentResolver.openFileDescriptor(file.toUri(), "r")
        try {
            storageManager.allocateBytes(parcelFileDescriptor?.fileDescriptor, requiredSpace)
        } catch (e: IOException) {
            Timber.e(e)
        }
        parcelFileDescriptor?.close()
    }
}
