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

package com.dimowner.audiorecorder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dimowner.audiorecorder.AppConstants
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.verify
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
@SmallTest
class FileRepositoryImplTest {

    @MockK
    lateinit var prefs: Prefs

    private lateinit var context: Context
    private lateinit var fileRepository: FileRepositoryImpl
    private lateinit var testDir: File
    private var filesToDelete: MutableList<File> = mutableListOf()

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        context = ApplicationProvider.getApplicationContext()

        // Default prefs stubs — private storage, M4A format, counted naming
        every { prefs.isStoreDirPublic } returns false
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_RECORD
        every { prefs.recordCounter } returns 1L
        every { prefs.settingSampleRate } returns AppConstants.RECORD_SAMPLE_RATE_44100
        every { prefs.settingBitrate } returns AppConstants.RECORD_ENCODING_BITRATE_128000
        every { prefs.settingChannelCount } returns AppConstants.RECORD_AUDIO_STEREO

        // Create a fresh temp directory for each test
        testDir = File(context.cacheDir, "test_recordings_${System.nanoTime()}")
        testDir.mkdirs()

        fileRepository = FileRepositoryImpl(context, prefs)
        // Point the recording dir to our test directory
        fileRepository.updateRecordingDir(context, prefs)
    }

    @After
    fun tearDown() {
        // Clean up test files
        testDir.deleteRecursively()
        filesToDelete.forEach {
            it.deleteRecursively()
        }
    }

    // ── provideRecordFile() — naming formats ────────────────────────────────────

    @Test
    fun provideRecordFile_countedNaming_m4a_createsFileWithCorrectExtension() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_RECORD
        every { prefs.recordCounter } returns 43L
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith(AppConstants.BASE_RECORD_NAME))
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_M4A}"))
        assertTrue(file.name.contains("-43"))
        // Clean up
        file.delete()
    }

    @Test
    fun provideRecordFile_countedNaming_wav_createsFileWithCorrectExtension() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_RECORD
        every { prefs.recordCounter } returns 7L
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_WAV

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith(AppConstants.BASE_RECORD_NAME))
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_WAV}"))
        assertTrue(file.name.contains("-7"))
        // Clean up
        file.delete()
    }

    @Test
    fun provideRecordFile_countedNaming_3gp_createsFileWithCorrectExtension() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_RECORD
        every { prefs.recordCounter } returns 3L
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_3GP

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith(AppConstants.BASE_RECORD_NAME))
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_3GP}"))
        assertTrue(file.name.contains("-3"))
        file.delete()
    }

    @Test
    fun provideRecordFile_dateNaming_createsFile() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_DATE
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_M4A}"))
        file.delete()
    }

    @Test
    fun provideRecordFile_dateUSNaming_createsFile() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_DATE_US
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_M4A}"))
        file.delete()
    }

    @Test
    fun provideRecordFile_dateISO8601Naming_createsFile() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_DATE_ISO8601
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_M4A}"))
        file.delete()
    }

    @Test
    fun provideRecordFile_timestampNaming_createsFile() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_TIMESTAMP
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        val file = fileRepository.provideRecordFile()
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".${AppConstants.FORMAT_M4A}"))
        file.delete()
    }

    @Test
    fun provideRecordFile_incrementsRecordCounter() {
        justRun { prefs.incrementRecordCounter() }

        fileRepository.provideRecordFile()
        verify { prefs.incrementRecordCounter() }
    }

    // ── provideRecordFile(name) ─────────────────────────────────────────────────

    @Test
    fun provideRecordFileByName_createsFileWithGivenName() {
        val fileName = "custom_record.m4a"
        val file = fileRepository.provideRecordFile(fileName)
        filesToDelete.add(file)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.equals(fileName))
        file.delete()
    }

    // ── getRecordingDir ─────────────────────────────────────────────────────────

    @Test
    fun getRecordingDir_returnsNonNullDirectory() {
        val dir = fileRepository.recordingDir
        assertNotNull(dir)
    }

    // ── getPrivateDirFiles ──────────────────────────────────────────────────────

    @Test
    fun getPrivateDirFiles_returnsNonNullArray() {
        val files = fileRepository.getPrivateDirFiles(context)
        filesToDelete.addAll(files)
        assertNotNull(files)
    }

    // ── getPrivateDir ───────────────────────────────────────────────────────────

    @Test
    fun getPrivateDir_returnsNonNull() {
        val dir = fileRepository.getPrivateDir(context)
        assertNotNull(dir)
    }

    // ── deleteRecordFile ────────────────────────────────────────────────────────

    @Test
    fun deleteRecordFile_existingFile_returnsTrue() {
        val file = File(testDir, "to_delete.m4a")
        file.createNewFile()
        assertTrue(file.exists())
        filesToDelete.add(file)

        val result = fileRepository.deleteRecordFile(file.absolutePath)

        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun deleteRecordFile_nullPath_returnsFalse() {
        val result = fileRepository.deleteRecordFile(null)
        assertFalse(result)
    }

    @Test
    fun deleteRecordFile_nonExistentFile_returnsFalse() {
        val result = fileRepository.deleteRecordFile(File(testDir, "does_not_exist.m4a").absolutePath)
        assertFalse(result)
    }

    // ── markAsTrashRecord / unmarkTrashRecord ───────────────────────────────────

    @Test
    fun markAsTrashRecord_addsTrashExtension() {
        val file = File(testDir, "record.m4a")
        file.createNewFile()
        assertTrue(file.exists())
        filesToDelete.add(file)

        val trashPath = fileRepository.markAsTrashRecord(file.absolutePath)

        assertNotNull(trashPath)
        assertTrue(trashPath!!.endsWith(".${AppConstants.TRASH_MARK_EXTENSION}"))
        val file2 = File(trashPath)
        filesToDelete.add(file2)
        assertTrue(file2.exists())
        assertFalse(file.exists())
    }

    @Test
    fun unmarkTrashRecord_removesTrashExtension() {
        // Create the trash-marked file
        val originalPath = File(testDir, "record.m4a").absolutePath
        val trashPath = "$originalPath.${AppConstants.TRASH_MARK_EXTENSION}"
        val trashFile = File(trashPath)
        trashFile.createNewFile()
        assertTrue(trashFile.exists())
        filesToDelete.add(trashFile)

        val restoredPath = fileRepository.unmarkTrashRecord(trashPath)

        assertNotNull(restoredPath)
        assertEquals(originalPath, restoredPath)
        val file2 = File(restoredPath!!)
        filesToDelete.add(file2)
        assertTrue(file2.exists())
        assertFalse(trashFile.exists())
    }

    @Test
    fun markAsTrashRecord_nonExistentFile_returnsNull() {
        val result = fileRepository.markAsTrashRecord(File(testDir, "ghost.m4a").absolutePath)
        assertNull(result)
    }

    @Test
    fun unmarkTrashRecord_nonExistentFile_returnsNull() {
        val result = fileRepository.unmarkTrashRecord(File(testDir, "ghost.m4a.del").absolutePath)
        assertNull(result)
    }

    // ── deleteAllRecords ────────────────────────────────────────────────────────

    @Test
    fun deleteAllRecords_returnsFalse() {
        // Current implementation always returns false
        assertFalse(fileRepository.deleteAllRecords())
    }

    // ── renameFile ──────────────────────────────────────────────────────────────

    @Test
    fun renameFile_existingFile_returnsTrue() {
        val file = File(testDir, "old_name.m4a")
        file.createNewFile()
        assertTrue(file.exists())
        filesToDelete.add(file)

        val result = fileRepository.renameFile(file.absolutePath, "new_name", AppConstants.FORMAT_M4A)

        assertTrue(result)
        val renamedFile = File(testDir, "new_name.${AppConstants.FORMAT_M4A}")
        filesToDelete.add(renamedFile)
        assertTrue(renamedFile.exists())
        assertFalse(file.exists())
        renamedFile.delete()
    }

    @Test
    fun renameFile_nonExistentFile_returnsFalse() {
        val result = fileRepository.renameFile(
            File(testDir, "no_such_file.m4a").absolutePath,
            "new_name",
            AppConstants.FORMAT_M4A
        )
        assertFalse(result)
    }

    // ── updateRecordingDir ──────────────────────────────────────────────────────

    @Test
    fun updateRecordingDir_privateStorage_setsRecordingDir() {
        every { prefs.isStoreDirPublic } returns false
        fileRepository.updateRecordingDir(context, prefs)

        val dir = fileRepository.recordingDir
        assertNotNull(dir)
    }

    @Test
    fun updateRecordingDir_publicStorage_setsRecordingDir() {
        every { prefs.isStoreDirPublic } returns true
        fileRepository.updateRecordingDir(context, prefs)

        val dir = fileRepository.recordingDir
        assertNotNull(dir)
    }

    // ── hasAvailableSpace ───────────────────────────────────────────────────────

    @Test
    fun hasAvailableSpace_privateStorage_returnsTrue() {
        every { prefs.isStoreDirPublic } returns false
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A
        every { prefs.settingSampleRate } returns AppConstants.RECORD_SAMPLE_RATE_44100
        every { prefs.settingBitrate } returns AppConstants.RECORD_ENCODING_BITRATE_128000
        every { prefs.settingChannelCount } returns AppConstants.RECORD_AUDIO_STEREO

        // On a real device / emulator there should be available space
        val result = fileRepository.hasAvailableSpace(context)
        assertTrue(result)
    }

    @Test
    fun hasAvailableSpace_publicStorage_returnsTrue() {
        every { prefs.isStoreDirPublic } returns true
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_WAV
        every { prefs.settingSampleRate } returns AppConstants.RECORD_SAMPLE_RATE_44100
        every { prefs.settingBitrate } returns AppConstants.RECORD_ENCODING_BITRATE_128000
        every { prefs.settingChannelCount } returns AppConstants.RECORD_AUDIO_STEREO

        val result = fileRepository.hasAvailableSpace(context)
        assertTrue(result)
    }

    @Test
    fun hasAvailableSpace_3gpFormat_returnsTrue() {
        every { prefs.isStoreDirPublic } returns false
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_3GP
        every { prefs.settingSampleRate } returns AppConstants.RECORD_SAMPLE_RATE_8000
        every { prefs.settingBitrate } returns AppConstants.RECORD_ENCODING_BITRATE_12000
        every { prefs.settingChannelCount } returns AppConstants.RECORD_AUDIO_MONO

        val result = fileRepository.hasAvailableSpace(context)
        assertTrue(result)
    }

    // ── Round-trip: mark → unmark trash ─────────────────────────────────────────

    @Test
    fun markAndUnmarkTrash_roundTrip_restoresOriginalPath() {
        val file = File(testDir, "roundtrip.wav")
        file.createNewFile()
        filesToDelete.add(file)
        val originalPath = file.absolutePath

        val trashPath = fileRepository.markAsTrashRecord(originalPath)
        assertNotNull(trashPath)
        assertFalse(File(originalPath).exists())
        assertTrue(File(trashPath!!).exists())

        val restoredPath = fileRepository.unmarkTrashRecord(trashPath)
        assertNotNull(restoredPath)
        assertEquals(originalPath, restoredPath)
        assertTrue(File(restoredPath!!).exists())
        assertFalse(File(trashPath).exists())
    }

    // ── provideRecordFile creates unique files on successive calls ──────────────

    @Test
    fun provideRecordFile_successiveCalls_createsDifferentFiles() {
        every { prefs.settingNamingFormat } returns AppConstants.NAME_FORMAT_RECORD
        every { prefs.settingRecordingFormat } returns AppConstants.FORMAT_M4A

        var counter = 0L
        every { prefs.recordCounter } answers { ++counter }

        val file1 = fileRepository.provideRecordFile()
        val file2 = fileRepository.provideRecordFile()
        filesToDelete.add(file1)
        filesToDelete.add(file2)

        assertNotNull(file1)
        assertNotNull(file2)
        assertTrue(file1.absolutePath != file2.absolutePath)

        file1.delete()
        file2.delete()
    }

    // ── getPublicDir ────────────────────────────────────────────────────────────

    @Test
    fun getPublicDir_returnsFileOrNull() {
        // getPublicDir may return null if external storage is unavailable;
        // on most test devices it should be non-null.
        // Just ensure no crash.
        fileRepository.publicDir
    }
}

