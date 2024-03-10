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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@LargeTest
class FileExtensionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test_createFile_Existing_Directory() {
        // Create a temporary directory for testing
        val directory = tempFolder.newFolder("testDir")

        // Create a file within the directory
        val fileName = "testFile.txt"
        val createdFile = createFile(directory, fileName)

        // Verify that the file was created
        assertTrue(createdFile.exists())
        assertEquals(fileName, createdFile.name)
    }

    @Test
    fun test_createFile_Existing_Directory_and_Existing_File() {
        // Create a temporary directory for testing
        val directory = tempFolder.newFolder("testDir")

        // Create a file within the directory
        val fileName = "testFile.txt"
        val createdFile = createFile(directory, fileName)

        // Verify that the file 1 was created and has correct name
        assertTrue(createdFile.exists())
        assertEquals(fileName, createdFile.name)

        val expectedFileName2 = "testFile-1.txt"
        val createdFile2 = createFile(directory, fileName)
        // Verify that the file 2 was created and has correct name
        assertTrue(createdFile2.exists())
        assertEquals(expectedFileName2, createdFile2.name)

        val expectedFileName3 = "testFile-2.txt"
        val createdFile3 = createFile(directory, fileName)
        // Verify that the file 3 was created and has correct name
        assertTrue(createdFile3.exists())
        assertEquals(expectedFileName3, createdFile3.name)
    }

    @Test
    fun test_createFile_Non_Existent_Directory() {
        // Create a non-existent directory
        val nonExistentDirectory = File("/path/to/non_existent_directory")
        val fileName = "testFile.txt"

        // Attempt to create a file in the non-existent directory
        assertThrows(IOException::class.java) {
            createFile(nonExistentDirectory, fileName)
        }
    }

    @Throws(IOException::class)
    fun File.verifyCanReadWrite() {
        if (!this.canRead()) {
            throw IOException("Can't read file")
        } else if (!this.canWrite()) {
            throw IOException("Can't write file")
        }
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun test_verifyCanReadWrite() {
        val file = mockk<File>()
        every { file.canRead() } returns false
        every { file.canWrite() } returns true

        assertThrows(IOException::class.java) {
            file.verifyCanReadWrite()
        }

        every { file.canRead() } returns true
        every { file.canWrite() } returns false

        assertThrows(IOException::class.java) {
            file.verifyCanReadWrite()
        }

        every { file.canRead() } returns false
        every { file.canWrite() } returns false

        assertThrows(IOException::class.java) {
            file.verifyCanReadWrite()
        }

        every { file.canRead() } returns true
        every { file.canWrite() } returns true

        try {
            file.verifyCanReadWrite()
        } catch (e: IOException) {
            fail("Should not have thrown any exception")
        }
    }

    @Test
    fun test_renameExistingFile() {
        val tempDir = createTempDir()
        val existingFile = File(tempDir, "existing_file.txt")
        existingFile.createNewFile()

        val renamed = "renamed_file"
        val expectedFile = "$renamed.txt"
        val renamedFile = File(tempDir, expectedFile)

        assertTrue(existingFile.exists())
        val result = renameFileWithExtension(existingFile, renamed)
        assertEquals(expectedFile, result?.name)
        assertFalse(existingFile.exists())
        assertTrue(renamedFile.exists())
    }

    @Test
    fun test_renameNonExistentFile() {
        val tempDir = createTempDir()
        val nonexistentFile = File(tempDir, "nonexistent_file.txt")

        val renamedFile = "renamed_file"

        assertFalse(nonexistentFile.exists())
        assertNull(renameFileWithExtension(nonexistentFile, renamedFile))
    }

    @Test
    fun test_renameWithSameName() {
        val tempDir = createTempDir()
        val existingFile = File(tempDir, "existing_file.txt")
        existingFile.createNewFile()

        val renamedFile = "existing_file"

        assertTrue(existingFile.exists())
        assertNull(renameFileWithExtension(existingFile, renamedFile))
        assertTrue(existingFile.exists())
    }

    @Test
    fun test_deleteExistingFile() {
        val tempDir = createTempDir()
        val existingFile = File(tempDir, "existing_file.txt")
        existingFile.createNewFile()

        assertTrue(existingFile.exists())
        assertTrue(deleteFileAndChildren(existingFile))
        assertFalse(existingFile.exists())
    }

    @Test
    fun test_deleteExistingDirectory() {
        val tempDir = createTempDir()
        val existingDir = File(tempDir, "existing_directory")
        val existingFile = File(existingDir, "existing_file.txt")
        existingFile.mkdirs()

        assertTrue(existingDir.isDirectory)
        assertTrue(existingFile.exists())
        assertTrue(deleteFileAndChildren(existingFile))
        assertFalse(existingFile.exists())
    }

    @Test
    fun test_deleteNonexistentFile() {
        val tempDir = createTempDir()
        val nonexistentFile = File(tempDir, "nonexistent_file.txt")

        assertFalse(nonexistentFile.exists())
        assertFalse(deleteFileAndChildren(nonexistentFile))
    }

    @Test
    fun test_markFileAsDeleted() {
        // Create a temporary file for testing
        val tempDir = createTempDir()
        val tempFile = File(tempDir, "Record.m4a")
        tempFile.createNewFile()

        val name = tempFile.name

        assertTrue(tempFile.exists())
        // Mark the file as deleted
        val trashFile = markFileAsDeleted(tempFile)

        // Verify that the file was renamed
        assertEquals("Record.m4a.deleted", trashFile?.name)
    }

    @Test
    fun test_markFileAsDeleted_with_non_existent_file() {
        // Create a non-existent file
        val nonExistentFile = File("/path/to/non_existent_file.m4a")

        assertFalse(nonExistentFile.exists())
        // Mark the file as deleted
        val restoredFile = markFileAsDeleted(nonExistentFile)

        // Verify that the result is null (file doesn't exist)
        assertNull(restoredFile)
    }

    @Test
    fun test_unmarkFileAsDeleted() {
        // Create a temporary trash file for testing
        val tempDir = createTempDir()
        val tempTrashFile = File(tempDir, "Record.m4a.deleted")
        tempTrashFile.createNewFile()

        val name = tempTrashFile.nameWithoutExtension

        assertTrue(tempTrashFile.exists())
        // Unmark the file (restore it)
        val restoredFile = unmarkFileAsDeleted(tempTrashFile)

        // Verify that the file was renamed back to its original name
        assertEquals(name, restoredFile?.name)
        assertEquals("Record.m4a", restoredFile?.name)
    }

    @Test
    fun test_unmarkFileAsDeleted_with_non_existent_file() {
        // Create a non-existent trash file
        val nonExistentTrashFile = File("/path/to/non_existent_file.deleted")

        assertFalse(nonExistentTrashFile.exists())
        // Attempt to unmark the file
        val restoredFile = unmarkFileAsDeleted(nonExistentTrashFile)

        // Verify that the result is null (file doesn't exist)
        assertNull(restoredFile)
    }

    @Test
    fun test_getPrivateMusicStorageDir_ExternalStorageAvailable() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val directoryName = "MyMusic"

        val result = getPrivateMusicStorageDir(context, directoryName)

        assertNotNull(result)
        assertTrue(result!!.exists())
        assertEquals(directoryName, result.name)
    }
}
