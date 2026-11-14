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
package com.dimowner.audiorecorder.v2.audio

/**
 * Fixed audio format and sizing rules of the instant replay buffer.
 *
 * The buffer runs for hours in the background, so it records the cheapest format that still
 * carries speech well: 16 kHz mono 16-bit PCM, about 1.9 MB per minute. It ignores the user's
 * recording settings on purpose - a 48 kHz stereo buffer would be six times larger for audio
 * that is mostly kept "just in case".
 */
object ReplayBufferConfig {

    const val SAMPLE_RATE = 16_000
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16

    /** Bytes per PCM frame: one 16-bit sample per channel. Every buffer position is a multiple. */
    const val FRAME_SIZE = CHANNEL_COUNT * BITS_PER_SAMPLE / 8
    const val BYTES_PER_SECOND = SAMPLE_RATE * FRAME_SIZE

    /** Lengths the user can pick, in minutes. */
    val DURATION_OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15, 30, 60)
    const val DEFAULT_DURATION_MINUTES = 5

    /** Free space kept on top of the estimate, so the buffer never fills a device to the last byte. */
    const val FREE_SPACE_MARGIN_BYTES = 20L * 1024 * 1024

    /** Size of the ring buffer file for [minutes] of audio. */
    fun bufferSizeBytes(minutes: Int): Long = minutes * 60L * BYTES_PER_SECOND

    /** Snaps an unknown stored value back to a supported option. */
    fun sanitizeMinutes(minutes: Int): Int =
        if (minutes in DURATION_OPTIONS_MINUTES) minutes else DEFAULT_DURATION_MINUTES

    /**
     * Whether there is room for the buffer and for one saved copy of it.
     *
     * The two may live on different volumes - the buffer in app-private internal storage, saved
     * records in the user's recording directory - so each is checked against its own free space.
     */
    fun hasEnoughSpace(minutes: Int, bufferDirFreeBytes: Long, recordsDirFreeBytes: Long): Boolean {
        val size = bufferSizeBytes(minutes)
        return bufferDirFreeBytes >= size + FREE_SPACE_MARGIN_BYTES &&
            recordsDirFreeBytes >= size + FREE_SPACE_MARGIN_BYTES
    }
}
