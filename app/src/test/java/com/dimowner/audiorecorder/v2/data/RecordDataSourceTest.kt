package com.dimowner.audiorecorder.v2.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.data.RecordDataSource
import com.dimowner.audiorecorder.data.database.LocalRepository
import com.dimowner.audiorecorder.data.database.Record
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

//@RunWith(AndroidJUnit4::class)
//@SmallTest
class RecordDataSourceTest {

    private lateinit var localRepository: LocalRepository
    private lateinit var prefs: Prefs
    private lateinit var recordDataSource: RecordDataSource

    private fun createRecord(id: Int, name: String = "Record $id"): Record {
        return Record(
            id,
            name,
            1000_000L, //1 second
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            0L,
            "/path/to/$name",
            "mp3",
            1024L,
            44100,
            2,
            128,
            false,
            false,
            IntArray(0)
        )
    }

    @Before
    fun setUp() {
        localRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        recordDataSource = RecordDataSource(localRepository, prefs)
    }

    @Test
    fun getActiveRecord_returnsNull_whenActiveRecordIdIsNegative() {
        every { prefs.activeRecord } returns -1L

        val result = recordDataSource.getActiveRecord()

        Assert.assertNull(result)
    }

    @Test
    fun getActiveRecord_loadsFromRepository_whenNoCache() {
        val record = createRecord(1)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record

        val result = recordDataSource.getActiveRecord()

        Assert.assertEquals(record, result)
        verify(exactly = 1) { localRepository.getRecord(1) }
    }

    @Test
    fun getActiveRecord_returnsCached_whenCalledTwiceWithSameId() {
        val record = createRecord(1)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record

        val result1 = recordDataSource.getActiveRecord()
        val result2 = recordDataSource.getActiveRecord()

        Assert.assertEquals(record, result1)
        Assert.assertEquals(record, result2)
        // Repository should be called only once; second call uses cache
        verify(exactly = 1) { localRepository.getRecord(1) }
    }

    @Test
    fun getActiveRecord_reloadsFromRepository_whenActiveIdChanges() {
        val record1 = createRecord(1)
        val record2 = createRecord(2)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record1

        val result1 = recordDataSource.getActiveRecord()
        Assert.assertEquals(record1, result1)

        every { prefs.activeRecord } returns 2L
        every { localRepository.getRecord(2) } returns record2

        val result2 = recordDataSource.getActiveRecord()
        Assert.assertEquals(record2, result2)

        verify(exactly = 1) { localRepository.getRecord(1) }
        verify(exactly = 1) { localRepository.getRecord(2) }
    }

    @Test
    fun getActiveRecord_returnsNull_whenRepositoryReturnsNull() {
        every { prefs.activeRecord } returns 5L
        every { localRepository.getRecord(5) } returns null

        val result = recordDataSource.getActiveRecord()

        Assert.assertNull(result)
    }

    @Test
    fun clearActiveRecord_clearsCacheAndForcesReload() {
        val record = createRecord(1)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record

        // Load into cache
        recordDataSource.getActiveRecord()
        verify(exactly = 1) { localRepository.getRecord(1) }

        // Clear cache
        recordDataSource.clearActiveRecord()

        // Should reload from repository
        recordDataSource.getActiveRecord()
        verify(exactly = 2) { localRepository.getRecord(1) }
    }

    @Test
    fun clearActiveRecord_thenNegativeId_returnsNull() {
        val record = createRecord(1)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record

        recordDataSource.getActiveRecord()
        recordDataSource.clearActiveRecord()

        every { prefs.activeRecord } returns -1L

        val result = recordDataSource.getActiveRecord()
        Assert.assertNull(result)
    }

    @Test
    fun recordingRecord_defaultsToNull() {
        Assert.assertNull(recordDataSource.recordingRecord)
    }

    @Test
    fun recordingRecord_canBeSetAndRetrieved() {
        val record = createRecord(10, "Recording")
        recordDataSource.recordingRecord = record

        Assert.assertEquals(record, recordDataSource.recordingRecord)
        Assert.assertEquals(10, recordDataSource.recordingRecord?.id)
    }

    @Test
    fun recordingRecord_canBeSetToNull() {
        val record = createRecord(10, "Recording")
        recordDataSource.recordingRecord = record
        recordDataSource.recordingRecord = null

        Assert.assertNull(recordDataSource.recordingRecord)
    }

    @Test
    fun getActiveRecord_isThreadSafe() {
        val record = createRecord(1)
        every { prefs.activeRecord } returns 1L
        every { localRepository.getRecord(1) } returns record

        // Call from multiple threads concurrently
        val threads = (1..10).map {
            Thread {
                val result = recordDataSource.getActiveRecord()
                Assert.assertEquals(record, result)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test
    fun getActiveRecord_withZeroId_loadsFromRepository() {
        val record = createRecord(0)
        every { prefs.activeRecord } returns 0L
        every { localRepository.getRecord(0) } returns record

        val result = recordDataSource.getActiveRecord()

        Assert.assertEquals(record, result)
        verify(exactly = 1) { localRepository.getRecord(0) }
    }
}