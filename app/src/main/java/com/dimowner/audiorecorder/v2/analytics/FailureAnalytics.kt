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

import com.dimowner.audiorecorder.exception.AppException

/** Value reported for a string detail that is not available in the failing scenario. */
const val ANALYTICS_VALUE_NONE = "none"

/** Value reported for a numeric detail that could not be determined. */
const val ANALYTICS_VALUE_UNKNOWN_NUMBER = -1L

/**
 * Why a recording never started.
 *
 * [label] is the value sent to the analytics backend: keep the labels stable, renaming one breaks
 * the history of the corresponding report in already collected data.
 */
enum class RecordingStartFailureReason(val label: String) {

    /** The platform recorder (MediaRecorder / AudioRecord / MediaCodec) could not be initialised. */
    RECORDER_INIT("recorder_init"),

    /** The output file disappeared or is not a regular file by the time the recorder opened it. */
    INVALID_OUTPUT_FILE("invalid_output_file"),

    /** The output file could not be created in the records directory. */
    CANT_CREATE_FILE("cant_create_file"),

    /** A start was requested while another recording was still running. */
    ALREADY_RECORDING("already_recording"),

    /** Not enough free space left to record for [com.dimowner.audiorecorder.AppConstants.MIN_REMAIN_RECORDING_TIME]. */
    NOT_ENOUGH_SPACE("not_enough_space"),

    /** The encoding pipeline failed before the first frame was written. */
    RECORDING_ERROR("recording_error"),

    /** Reading or writing the record was denied. */
    PERMISSION_DENIED("permission_denied"),

    /** An error that has no dedicated reason yet; check `error_class` on the report. */
    UNKNOWN("unknown");

    companion object {

        /** Maps the [AppException] a recorder reported to the reason to send with the report. */
        fun fromException(exception: AppException?): RecordingStartFailureReason =
            when (exception?.type) {
                AppException.RECORDER_INIT_EXCEPTION -> RECORDER_INIT
                AppException.INVALID_OUTPUT_FILE -> INVALID_OUTPUT_FILE
                AppException.CANT_CREATE_FILE -> CANT_CREATE_FILE
                AppException.ALREADY_RECORDING -> ALREADY_RECORDING
                AppException.NO_SPACE_AVAILABLE -> NOT_ENOUGH_SPACE
                AppException.RECORDING_ERROR -> RECORDING_ERROR
                AppException.READ_PERMISSION_DENIED -> PERMISSION_DENIED
                else -> UNKNOWN
            }
    }
}

/**
 * Everything worth knowing about a recording that failed to start, gathered at the moment of the
 * failure. Reported by [AnalyticsTracker.trackRecordingStartFailed].
 *
 * The settings fields describe what the recorder was asked to do, which is what makes these
 * reports actionable: a failure that only ever shows up for one format / sample rate / audio
 * source combination points straight at the codec configuration to fix.
 *
 * @param reason             Why the start failed, see [RecordingStartFailureReason].
 * @param format             Requested recording format (e.g. "m4a", "wav", "opus").
 * @param sampleRate         Requested sample rate in Hz.
 * @param bitrate            Requested bitrate in bit/s, `0` for formats without one.
 * @param channelCount       Requested channel count (1 = mono, 2 = stereo).
 * @param audioSource        Requested capture source (a [com.dimowner.audiorecorder.v2.data.model.AudioSource] name).
 * @param availableSpaceBytes Free space in the records directory, or [ANALYTICS_VALUE_UNKNOWN_NUMBER].
 * @param error              Exception behind the failure, when there was one. Implementations
 *                           should forward it to a crash reporter as a non-fatal, because the
 *                           analytics event itself can only carry a truncated message.
 */
data class RecordingStartFailure(
    val reason: RecordingStartFailureReason,
    val format: String,
    val sampleRate: Int,
    val bitrate: Int,
    val channelCount: Int,
    val audioSource: String,
    val availableSpaceBytes: Long = ANALYTICS_VALUE_UNKNOWN_NUMBER,
    val error: Throwable? = null,
) {
    /** Simple class name of [error], or [ANALYTICS_VALUE_NONE] when the failure carried no exception. */
    val errorClass: String get() = error.analyticsClassName()

    /** Message of [error], or [ANALYTICS_VALUE_NONE]. Implementations must truncate it if needed. */
    val errorMessage: String get() = error.analyticsMessage()
}

/**
 * Why a playback never started.
 *
 * [label] is the value sent to the analytics backend, see [RecordingStartFailureReason.label].
 */
enum class PlaybackStartFailureReason(val label: String) {

    /** The file behind the record could not be read: missing, empty, unreadable or an invalid uri. */
    DATA_SOURCE("data_source"),

    /** The player rejected the source: unsupported container, no decoder, malformed content. */
    PLAYER_INIT("player_init"),

    /** An error that has no dedicated reason yet; check `error_class` on the report. */
    UNKNOWN("unknown");

    companion object {

        /** Maps the [AppException] the player reported to the reason to send with the report. */
        fun fromException(exception: AppException?): PlaybackStartFailureReason =
            when (exception?.type) {
                AppException.PLAYER_DATA_SOURCE_EXCEPTION -> DATA_SOURCE
                AppException.PLAYER_INIT_EXCEPTION -> PLAYER_INIT
                else -> UNKNOWN
            }
    }
}

/**
 * Everything worth knowing about a playback that failed to start, gathered at the moment of the
 * failure. Reported by [AnalyticsTracker.trackPlaybackStartFailed].
 *
 * Only failures on the way *into* playback are reported here; an error raised while audio is
 * already playing is a different problem and is not part of this funnel.
 *
 * @param reason          Why the start failed, see [PlaybackStartFailureReason].
 * @param format          Extension of the file that was about to be played (e.g. "m4a"), lowercase.
 * @param uriScheme       Scheme of the source: "file" for records stored by path, "content" for
 *                        records in a SAF picked folder, [ANALYTICS_VALUE_NONE] when there is none.
 * @param fileExists      Whether the file was found on disk, `null` for sources that cannot be
 *                        stat-ed cheaply (content uris).
 * @param fileSizeBytes   Size of the file on disk, or [ANALYTICS_VALUE_UNKNOWN_NUMBER]. A zero
 *                        here is the signature of a recording that was interrupted before
 *                        anything was written to it.
 * @param playerErrorCode Player error code, or `-1` when the failure happened before the
 *                        player itself was involved.
 * @param playerErrorName Symbolic name of [playerErrorCode], or [ANALYTICS_VALUE_NONE].
 * @param error           Exception behind the failure, when there was one. Implementations should
 *                        forward it to a crash reporter as a non-fatal, see [RecordingStartFailure.error].
 */
data class PlaybackStartFailure(
    val reason: PlaybackStartFailureReason,
    val format: String,
    val uriScheme: String,
    val fileExists: Boolean? = null,
    val fileSizeBytes: Long = ANALYTICS_VALUE_UNKNOWN_NUMBER,
    val playerErrorCode: Int = ANALYTICS_VALUE_UNKNOWN_NUMBER.toInt(),
    val playerErrorName: String = ANALYTICS_VALUE_NONE,
    val error: Throwable? = null,
) {
    /** Simple class name of [error], or [ANALYTICS_VALUE_NONE] when the failure carried no exception. */
    val errorClass: String get() = error.analyticsClassName()

    /** Message of [error], or [ANALYTICS_VALUE_NONE]. Implementations must truncate it if needed. */
    val errorMessage: String get() = error.analyticsMessage()
}

/**
 * Class name to report for an exception. Anonymous classes have an empty simple name, hence the
 * fallback to the full one.
 */
private fun Throwable?.analyticsClassName(): String {
    val throwable = this ?: return ANALYTICS_VALUE_NONE
    return throwable.javaClass.simpleName.ifEmpty { throwable.javaClass.name }
}

/**
 * Message to report for an exception. The cause is included because the exceptions the recorders
 * and the player raise are app level wrappers whose own message is often empty.
 */
private fun Throwable?.analyticsMessage(): String {
    val throwable = this ?: return ANALYTICS_VALUE_NONE
    val message = throwable.message
    val cause = throwable.cause
    return when {
        !message.isNullOrEmpty() && cause != null -> "$message; caused by ${cause.analyticsClassName()}: ${cause.message}"
        !message.isNullOrEmpty() -> message
        cause != null -> "${cause.analyticsClassName()}: ${cause.message}"
        else -> ANALYTICS_VALUE_NONE
    }
}
