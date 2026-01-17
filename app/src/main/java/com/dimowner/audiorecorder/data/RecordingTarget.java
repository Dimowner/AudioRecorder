/*
 * Copyright 2024 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.data;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

import timber.log.Timber;

/**
 * Represents a recording target that can be either a regular File or a SAF-based URI.
 * This abstraction allows the recorder to work with both traditional file paths
 * and Storage Access Framework URIs transparently.
 */
public class RecordingTarget {

    private final File file;
    private final Uri safUri;
    private final String displayPath;
    private volatile ParcelFileDescriptor pfd;

    /**
     * Create a file-based recording target.
     */
    public RecordingTarget(File file) {
        this.file = file;
        this.safUri = null;
        this.displayPath = file != null ? file.getAbsolutePath() : null;
    }

    /**
     * Create a SAF-based recording target.
     * @param safUri The SAF document URI
     * @param displayPath A human-readable path for display/database purposes
     */
    public RecordingTarget(Uri safUri, String displayPath) {
        this.file = null;
        this.safUri = safUri;
        this.displayPath = displayPath;
    }

    /**
     * Check if this is a SAF-based target.
     */
    public boolean isSaf() {
        return safUri != null;
    }

    /**
     * Get the file for file-based targets.
     * @return The file, or null if this is a SAF target
     */
    public File getFile() {
        return file;
    }

    /**
     * Get the SAF URI for SAF-based targets.
     * @return The SAF URI, or null if this is a file target
     */
    public Uri getSafUri() {
        return safUri;
    }

    /**
     * Get the display path (for database and UI).
     * For file targets, this is the absolute path.
     * For SAF targets, this is a reconstructed path like /storage/XXXX-XXXX/AudioRecorder/file.m4a
     */
    public String getDisplayPath() {
        return displayPath;
    }

    /**
     * Get a file path string. For file targets, returns absolute path.
     * For SAF targets, returns the display path.
     */
    public String getPath() {
        return displayPath;
    }

    /**
     * Open a ParcelFileDescriptor for writing. Must be closed after use via close().
     * For file targets, opens the file directly.
     * For SAF targets, uses ContentResolver.
     */
    public synchronized ParcelFileDescriptor openForWriting(Context context) throws IOException {
        if (file != null) {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            return pfd;
        } else if (safUri != null) {
            try {
                pfd = context.getContentResolver().openFileDescriptor(safUri, "rw");
                if (pfd == null) {
                    throw new IOException("Failed to open SAF URI for writing: " + safUri);
                }
                return pfd;
            } catch (Exception e) {
                throw new IOException("Failed to open SAF URI: " + safUri, e);
            }
        }
        throw new IOException("No valid target");
    }

    /**
     * Get the FileDescriptor for this target. Opens it if not already open.
     */
    public FileDescriptor getFileDescriptor(Context context) throws IOException {
        ParcelFileDescriptor localPfd = openForWriting(context);
        return localPfd.getFileDescriptor();
    }

    /**
     * Close any open file descriptors.
     */
    public synchronized void close() {
        ParcelFileDescriptor localPfd = pfd;
        pfd = null;
        if (localPfd != null) {
            try {
                localPfd.close();
            } catch (IOException e) {
                Timber.e(e, "Failed to close ParcelFileDescriptor");
            }
        }
    }

    /**
     * Check if the target exists and is writable.
     */
    public boolean exists() {
        if (file != null) {
            return file.exists();
        }
        // For SAF, we assume it exists if we have a URI
        return safUri != null;
    }
}
