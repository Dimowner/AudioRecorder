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
package com.dimowner.audiorecorder.v2.data.extensions

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import androidx.core.net.toUri

private const val CONTENT_URI_PREFIX = "content://"

/**
 * SAF documents are created with a generic MIME type so DocumentsProvider implementations
 * don't append their own extension to the display name (the real audio MIME type is derived
 * from the file extension by the provider afterwards).
 */
private const val GENERIC_MIME_TYPE = "application/octet-stream"

/**
 * Returns true when this record path is a SAF document Uri (record stored in a user-selected
 * public directory) rather than an absolute file path in the app-private storage.
 */
fun String.isContentUri(): Boolean = startsWith(CONTENT_URI_PREFIX)

/**
 * Returns true when the app still holds a persisted read+write permission for [treeUri],
 * granted via Intent.ACTION_OPEN_DOCUMENT_TREE + takePersistableUriPermission.
 */
fun hasPersistedTreePermission(context: Context, treeUri: Uri): Boolean {
    return context.contentResolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isReadPermission && it.isWritePermission
    }
}

/**
 * Creates a new document with name [fileName] inside the SAF tree [treeUri].
 * The DocumentsProvider resolves display-name collisions itself (appends " (1)", " (2)"…).
 *
 * @return the created document, or null when the tree is not accessible/writable.
 */
fun createDocumentInTree(context: Context, treeUri: Uri, fileName: String): DocumentFile? {
    return try {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
        if (tree == null || !tree.isDirectory || !tree.canWrite()) {
            Timber.e("SAF tree is not accessible or not writable: $treeUri")
            null
        } else {
            tree.createFile(GENERIC_MIME_TYPE, fileName)
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to create document $fileName in tree: $treeUri")
        null
    }
}

fun documentFileExists(context: Context, uriString: String): Boolean {
    return try {
        DocumentFile.fromSingleUri(context, uriString.toUri())?.exists() == true
    } catch (e: Exception) {
        Timber.e(e, "Failed to check document existence: $uriString")
        false
    }
}

fun getDocumentLength(context: Context, uriString: String): Long {
    return try {
        DocumentFile.fromSingleUri(context, uriString.toUri())?.length() ?: 0L
    } catch (e: Exception) {
        Timber.e(e, "Failed to read document length: $uriString")
        0L
    }
}

fun getDocumentName(context: Context, uriString: String): String? {
    return try {
        DocumentFile.fromSingleUri(context, uriString.toUri())?.name
    } catch (e: Exception) {
        Timber.e(e, "Failed to read document name: $uriString")
        null
    }
}

fun getDocumentLastModified(context: Context, uriString: String): Long {
    return try {
        DocumentFile.fromSingleUri(context, uriString.toUri())?.lastModified() ?: 0L
    } catch (e: Exception) {
        Timber.e(e, "Failed to read document lastModified: $uriString")
        0L
    }
}

fun deleteDocument(context: Context, uriString: String): Boolean {
    return try {
        DocumentFile.fromSingleUri(context, uriString.toUri())?.delete() == true
    } catch (e: Exception) {
        Timber.e(e, "Failed to delete document: $uriString")
        false
    }
}

/**
 * Renames the document keeping its original extension, mirroring [renameFileWithExtension].
 *
 * @return the Uri of the renamed document as String, or null on failure.
 */
fun renameDocumentWithExtension(context: Context, uriString: String, newName: String): String? {
    return try {
        val uri = uriString.toUri()
        val currentName = getDocumentName(context, uriString) ?: return null
        if (currentName.substringBeforeLast('.') == newName) {
            return null
        }
        val extension = currentName.substringAfterLast('.', "")
        val newFileName = if (extension.isEmpty()) newName else "$newName.$extension"
        DocumentsContract.renameDocument(context.contentResolver, uri, newFileName)?.toString()
    } catch (e: Exception) {
        Timber.e(e, "Failed to rename document: $uriString to $newName")
        null
    }
}

/**
 * Returns the available space in bytes on the filesystem hosting the given document,
 * queried via fstatvfs on an opened file descriptor. Returns 0 when the document
 * cannot be opened (missing permission, deleted folder, …).
 */
fun getAvailableSpaceForDocument(context: Context, uriString: String): Long {
    return try {
        context.contentResolver.openFileDescriptor(uriString.toUri(), "r")?.use { pfd ->
            val stat = Os.fstatvfs(pfd.fileDescriptor)
            stat.f_bavail * stat.f_bsize
        } ?: 0L
    } catch (e: Exception) {
        Timber.e(e, "Failed to read available space for document: $uriString")
        0L
    }
}

/**
 * Human-readable name of a SAF tree directory for display in Settings,
 * e.g. "Recordings" for content://…/tree/primary%3ARecordings.
 */
fun getTreeDisplayName(context: Context, treeUriString: String): String? {
    return try {
        val treeUri = treeUriString.toUri()
        DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':')
                .substringAfterLast('/').ifEmpty { null }
    } catch (e: Exception) {
        Timber.e(e, "Failed to get tree display name: $treeUriString")
        null
    }
}
