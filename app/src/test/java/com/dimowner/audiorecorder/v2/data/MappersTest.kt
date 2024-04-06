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

import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.room.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun test_toRecord() {
        val id = 1L
        val name = "TestName"
        val duration = 15000L
        val created = 123456788L
        val added = 123456789L
        val removed = 0L
        val path = "path/to/record"
        val format = "mp3"
        val size = 1024L
        val sampleRate = 44100
        val channelCount = 2
        val bitrate = 128
        val isBookmarked = true
        val isWaveformProcessed = false
        val isMovedToRecycle = false
        val amps: IntArray = intArrayOf(1, 2, 3)

        val record = RecordEntity(
            id, name, duration, created, added, removed,
            path, format, size, sampleRate, channelCount,
            bitrate, isBookmarked, isWaveformProcessed,
            isMovedToRecycle, amps
        )
        val result = record.toRecord()
        assertEquals(id, result.id)
        assertEquals(name, result.name)
        assertEquals(duration, result.durationMills)
        assertEquals(created, result.created)
        assertEquals(added, result.added)
        assertEquals(removed, result.removed)
        assertEquals(path, result.path)
        assertEquals(format, result.format)
        assertEquals(size, result.size)
        assertEquals(sampleRate, result.sampleRate)
        assertEquals(channelCount, result.channelCount)
        assertEquals(bitrate, result.bitrate)
        assertEquals(isBookmarked, result.isBookmarked)
        assertEquals(isWaveformProcessed, result.isWaveformProcessed)
        assertEquals(isMovedToRecycle, result.isMovedToRecycle)
        assertEquals(amps, result.amps)
    }

    @Test
    fun test_toRecordEntity() {
        val id = 1L
        val name = "TestName"
        val duration = 15000L
        val created = 123456788L
        val added = 123456789L
        val removed = 0L
        val path = "path/to/record"
        val format = "mp3"
        val size = 1024L
        val sampleRate = 44100
        val channelCount = 2
        val bitrate = 128
        val isBookmarked = true
        val isWaveformProcessed = false
        val isMovedToRecycle = false
        val amps: IntArray = intArrayOf(1, 2, 3)

        val record = Record(
            id, name, duration, created, added, removed,
            path, format, size, sampleRate, channelCount,
            bitrate, isBookmarked, isWaveformProcessed,
            isMovedToRecycle, amps
        )
        val result = record.toRecordEntity()
        assertEquals(id, result.id)
        assertEquals(name, result.name)
        assertEquals(duration, result.duration)
        assertEquals(created, result.created)
        assertEquals(added, result.added)
        assertEquals(removed, result.removed)
        assertEquals(path, result.path)
        assertEquals(format, result.format)
        assertEquals(size, result.size)
        assertEquals(sampleRate, result.sampleRate)
        assertEquals(channelCount, result.channelCount)
        assertEquals(bitrate, result.bitrate)
        assertEquals(isBookmarked, result.isBookmarked)
        assertEquals(isWaveformProcessed, result.isWaveformProcessed)
        assertEquals(isMovedToRecycle, result.isMovedToRecycle)
        assertEquals(amps, result.amps)
    }
}
