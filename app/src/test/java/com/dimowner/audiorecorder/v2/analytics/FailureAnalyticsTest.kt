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

package com.dimowner.audiorecorder.v2.analytics

import com.dimowner.audiorecorder.exception.AlreadyRecordingException
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.CantProcessRecord
import com.dimowner.audiorecorder.exception.InvalidOutputFile
import com.dimowner.audiorecorder.exception.NoSpaceAvailableException
import com.dimowner.audiorecorder.exception.PlayerDataSourceException
import com.dimowner.audiorecorder.exception.PlayerInitException
import com.dimowner.audiorecorder.exception.RecorderInitException
import com.dimowner.audiorecorder.exception.RecordingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FailureAnalyticsTest {

    // -------------------------------------------------------------------------
    // RecordingStartFailureReason.fromException
    // -------------------------------------------------------------------------

    @Test
    fun `recording reason maps every exception a recorder can report`() {
        assertEquals(
            RecordingStartFailureReason.RECORDER_INIT,
            RecordingStartFailureReason.fromException(RecorderInitException())
        )
        assertEquals(
            RecordingStartFailureReason.INVALID_OUTPUT_FILE,
            RecordingStartFailureReason.fromException(InvalidOutputFile())
        )
        assertEquals(
            RecordingStartFailureReason.CANT_CREATE_FILE,
            RecordingStartFailureReason.fromException(CantCreateFileException())
        )
        assertEquals(
            RecordingStartFailureReason.ALREADY_RECORDING,
            RecordingStartFailureReason.fromException(AlreadyRecordingException())
        )
        assertEquals(
            RecordingStartFailureReason.NOT_ENOUGH_SPACE,
            RecordingStartFailureReason.fromException(NoSpaceAvailableException())
        )
        assertEquals(
            RecordingStartFailureReason.RECORDING_ERROR,
            RecordingStartFailureReason.fromException(RecordingException())
        )
    }

    @Test
    fun `recording reason is unknown for an unrelated exception`() {
        assertEquals(
            RecordingStartFailureReason.UNKNOWN,
            RecordingStartFailureReason.fromException(CantProcessRecord())
        )
    }

    @Test
    fun `recording reason is unknown when there is no exception`() {
        assertEquals(
            RecordingStartFailureReason.UNKNOWN,
            RecordingStartFailureReason.fromException(null)
        )
    }

    // -------------------------------------------------------------------------
    // PlaybackStartFailureReason.fromException
    // -------------------------------------------------------------------------

    @Test
    fun `playback reason maps every exception the player can report`() {
        assertEquals(
            PlaybackStartFailureReason.DATA_SOURCE,
            PlaybackStartFailureReason.fromException(PlayerDataSourceException())
        )
        assertEquals(
            PlaybackStartFailureReason.PLAYER_INIT,
            PlaybackStartFailureReason.fromException(PlayerInitException())
        )
    }

    @Test
    fun `playback reason is unknown for an unrelated exception`() {
        assertEquals(
            PlaybackStartFailureReason.UNKNOWN,
            PlaybackStartFailureReason.fromException(RecorderInitException())
        )
    }

    // -------------------------------------------------------------------------
    // Reported error details
    // -------------------------------------------------------------------------

    @Test
    fun `error details fall back to none without an exception`() {
        val failure = recordingFailure(error = null)
        assertEquals(ANALYTICS_VALUE_NONE, failure.errorClass)
        assertEquals(ANALYTICS_VALUE_NONE, failure.errorMessage)
    }

    @Test
    fun `error class is the simple name of the exception`() {
        val failure = recordingFailure(error = IOException("prepare failed"))
        assertEquals("IOException", failure.errorClass)
        assertEquals("prepare failed", failure.errorMessage)
    }

    @Test
    fun `error message falls back to the cause when the wrapper has none`() {
        // The exceptions the recorders and the player raise are markers with no message of
        // their own, so without the cause these reports would say nothing at all.
        val failure = recordingFailure(error = RuntimeException(IllegalStateException("no codec")))
        val message = failure.errorMessage
        assertTrue(message, message.contains("IllegalStateException"))
        assertTrue(message, message.contains("no codec"))
    }

    @Test
    fun `error message keeps both the message and the cause`() {
        val error = IOException("start failed", IllegalStateException("no codec"))
        val message = recordingFailure(error = error).errorMessage
        assertTrue(message, message.contains("start failed"))
        assertTrue(message, message.contains("no codec"))
    }

    @Test
    fun `playback failure reports the details of its source`() {
        val failure = PlaybackStartFailure(
            reason = PlaybackStartFailureReason.DATA_SOURCE,
            format = "m4a",
            uriScheme = "file",
            fileExists = false,
        )
        assertEquals(ANALYTICS_VALUE_UNKNOWN_NUMBER, failure.fileSizeBytes)
        assertEquals(ANALYTICS_VALUE_UNKNOWN_NUMBER.toInt(), failure.playerErrorCode)
        assertEquals(ANALYTICS_VALUE_NONE, failure.playerErrorName)
        assertEquals(ANALYTICS_VALUE_NONE, failure.errorClass)
    }

    private fun recordingFailure(error: Throwable?) = RecordingStartFailure(
        reason = RecordingStartFailureReason.RECORDER_INIT,
        format = "m4a",
        sampleRate = 44100,
        bitrate = 128000,
        channelCount = 2,
        audioSource = "mic",
        error = error,
    )
}
