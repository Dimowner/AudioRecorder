/*
 * Copyright 2026 Dmytro Ponomarenko
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.dimowner.audiorecorder.v2.DefaultValues.DELETED_RECORD_MARK
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class FileDataSourceImplTest {

    private lateinit var fileDataSource: FileDataSourceImpl
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        fileDataSource = FileDataSourceImpl(context)
    }

    @After
    fun tearDown() {
        // Clean up any files created during tests
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        createdFiles.clear()
    }

    @Test
    fun test_getRecordingDir_returnsNonNullDirectory() {
        val dir = fileDataSource.getRecordingDir()
        assertNotNull(dir)
        assertTrue(dir!!.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun test_createRecordFile_createsFile() {
        val fileName = "test_record.m4a"
        val file = fileDataSource.createRecordFile(fileName)
        createdFiles.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertEquals(fileName, file.name)
    }

    @Test
    fun test_createRecordFile_duplicateNameGetsSuffix() {
        val fileName = "test_duplicate.m4a"

        val file1 = fileDataSource.createRecordFile(fileName)
        createdFiles.add(file1)

        val file2 = fileDataSource.createRecordFile(fileName)
        createdFiles.add(file2)

        val file3 = fileDataSource.createRecordFile(fileName)
        createdFiles.add(file3)

        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())
        assertEquals("test_duplicate.m4a", file1.name)
        assertEquals("test_duplicate-1.m4a", file2.name)
        assertEquals("test_duplicate-2.m4a", file3.name)
    }

    @Test
    fun test_deleteRecordFile_deletesExistingFile() {
        val file = fileDataSource.createRecordFile("test_delete.m4a")
        createdFiles.add(file)
        assertTrue(file.exists())

        val result = fileDataSource.deleteRecordFile(file.absolutePath)
        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun test_deleteRecordFile_returnsFalseForNonExistentFile() {
        val fakePath = fileDataSource.getRecordingDir()!!.absolutePath + "/non_existent_file.m4a"
        val result = fileDataSource.deleteRecordFile(fakePath)
        assertFalse(result)
    }

    @Test
    fun test_markAsRecordDeleted_addsDeletedSuffix() {
        val name = "test_mark_deleted.m4a"
        val file = fileDataSource.createRecordFile(name)
        createdFiles.add(file)
        assertTrue(file.exists())

        val deletedPath = fileDataSource.markAsRecordDeleted(file.absolutePath)
        assertNotNull(deletedPath)
        assertTrue(deletedPath!!.endsWith(DELETED_RECORD_MARK))
        val deletedFile = File(deletedPath)
        assertEquals(name+DELETED_RECORD_MARK, deletedFile.name)

        createdFiles.add(deletedFile)
        assertTrue(deletedFile.exists())
        assertFalse(file.exists())
    }

    @Test
    fun test_markAsRecordDeleted_returnsNullForNonExistentFile() {
        val fakePath = fileDataSource.getRecordingDir()!!.absolutePath + "/non_existent.m4a"
        val result = fileDataSource.markAsRecordDeleted(fakePath)
        assertNull(result)
    }

    @Test
    fun test_unmarkRecordAsDeleted_removesDeletedSuffix() {
        val name = "test_unmark.m4a"
        val file = fileDataSource.createRecordFile(name)
        createdFiles.add(file)

        val deletedPath = fileDataSource.markAsRecordDeleted(file.absolutePath)
        assertNotNull(deletedPath)

        val deletedFile = File(deletedPath!!)
        createdFiles.add(deletedFile)

        val restoredPath = fileDataSource.unmarkRecordAsDeleted(deletedPath)
        assertNotNull(restoredPath)
        assertFalse(restoredPath!!.endsWith(DELETED_RECORD_MARK))

        val restoredFile = File(restoredPath)
        createdFiles.add(restoredFile)
        assertEquals(name, restoredFile.name)
        assertTrue(restoredFile.exists())
        assertFalse(deletedFile.exists())
    }

    @Test
    fun test_unmarkRecordAsDeleted_returnsNullForNonExistentFile() {
        val fakePath = fileDataSource.getRecordingDir()!!.absolutePath + "/non_existent.m4a.deleted"
        val result = fileDataSource.unmarkRecordAsDeleted(fakePath)
        assertNull(result)
    }

    @Test
    fun test_renameFile_renamesSuccessfully() {
        val file = fileDataSource.createRecordFile("test_rename_original.m4a")
        createdFiles.add(file)
        assertTrue(file.exists())

        val renamedFile = fileDataSource.renameFile(file.absolutePath, "test_rename_new")
        assertNotNull(renamedFile)
        createdFiles.add(renamedFile!!)

        assertEquals("test_rename_new.m4a", renamedFile.name)
        assertTrue(renamedFile.exists())
        assertFalse(file.exists())
    }

    @Test
    fun test_renameFile_returnsNullForNonExistentFile() {
        val fakePath = fileDataSource.getRecordingDir()!!.absolutePath + "/non_existent.m4a"
        val result = fileDataSource.renameFile(fakePath, "new_name")
        assertNull(result)
    }

    @Test
    fun test_renameFile_returnsNullWhenNameUnchanged() {
        val file = fileDataSource.createRecordFile("test_same_name.m4a")
        createdFiles.add(file)

        val result = fileDataSource.renameFile(file.absolutePath, "test_same_name")
        assertNull(result)
        // Original file should still exist
        assertTrue(file.exists())
    }

    @Test
    fun test_getAvailableSpace_returnsPositiveValue() {
        val space = fileDataSource.getAvailableSpace()
        assertTrue(space > 0)
    }

    @Test
    fun test_markAndUnmarkRoundTrip_preservesOriginalName() {
        val originalName = "test_roundtrip.m4a"
        val file = fileDataSource.createRecordFile(originalName)
        createdFiles.add(file)

        val deletedPath = fileDataSource.markAsRecordDeleted(file.absolutePath)
        assertNotNull(deletedPath)
        createdFiles.add(File(deletedPath!!))

        val restoredPath = fileDataSource.unmarkRecordAsDeleted(deletedPath)
        assertNotNull(restoredPath)
        val restoredFile = File(restoredPath!!)
        createdFiles.add(restoredFile)

        assertEquals(originalName, restoredFile.name)
        assertTrue(restoredFile.exists())
    }
}

