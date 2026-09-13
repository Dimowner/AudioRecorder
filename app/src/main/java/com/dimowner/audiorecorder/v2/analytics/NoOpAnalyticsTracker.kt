package com.dimowner.audiorecorder.v2.analytics

import timber.log.Timber
import javax.inject.Inject

/**
 * No-operation [AnalyticsTracker] used as the default binding until a real
 * analytics SDK is integrated. Replace the binding in AnalyticsModule when ready.
 */
class NoOpAnalyticsTracker @Inject constructor() : AnalyticsTracker {

    override fun trackDbMigrationStarted() {
        Timber.v("NoOpAnalyticsTracker: trackDbMigrationStarted")
    }

    override fun trackDbMigrationSuccess(migratedRecordCount: Int, durationMs: Long) {
        Timber.v("NoOpAnalyticsTracker: trackDbMigrationSuccess: migratedRecordCount=$migratedRecordCount, durationMs=$durationMs")
    }

    override fun trackDbMigrationFailed(error: Throwable) {
        Timber.v(error, "NoOpAnalyticsTracker: trackDbMigrationFailed")
    }

    override fun trackSwitchToAppV2() {
        Timber.v("NoOpAnalyticsTracker: trackSwitchToAppV2")
    }

    override fun trackSwitchToLegacyApp() {
        Timber.v("NoOpAnalyticsTracker: trackSwitchToLegacyApp")
    }

    override fun trackBrokenRecordDetected(format: String, count: Int) {
        Timber.v("NoOpAnalyticsTracker: trackBrokenRecordDetected: format=$format, count=$count")
    }

    override fun trackBrokenRecordRestoreSuccess(format: String) {
        Timber.v("NoOpAnalyticsTracker: trackBrokenRecordRestoreSuccess: format=$format")
    }

    override fun trackBrokenRecordRestoreFailed(format: String) {
        Timber.v("NoOpAnalyticsTracker: trackBrokenRecordRestoreFailed: format=$format")
    }

    override fun trackLostRecordsDetected(count: Int) {
        Timber.v("NoOpAnalyticsTracker: trackLostRecordsDetected: count=$count")
    }

    override fun trackRecordingStartFailed(failure: RecordingStartFailure) {
        Timber.v(
            failure.error,
            "NoOpAnalyticsTracker: trackRecordingStartFailed: reason=%s, format=%s, sampleRate=%d," +
                    " bitrate=%d, channelCount=%d, audioSource=%s, availableSpaceBytes=%d," +
                    " errorClass=%s, errorMessage=%s",
            failure.reason.label,
            failure.format,
            failure.sampleRate,
            failure.bitrate,
            failure.channelCount,
            failure.audioSource,
            failure.availableSpaceBytes,
            failure.errorClass,
            failure.errorMessage,
        )
    }

    override fun trackPlaybackStartFailed(failure: PlaybackStartFailure) {
        Timber.v(
            failure.error,
            "NoOpAnalyticsTracker: trackPlaybackStartFailed: reason=%s, format=%s, uriScheme=%s," +
                    " fileExists=%s, fileSizeBytes=%d, playerErrorCode=%d, playerErrorName=%s," +
                    " errorClass=%s, errorMessage=%s",
            failure.reason.label,
            failure.format,
            failure.uriScheme,
            failure.fileExists,
            failure.fileSizeBytes,
            failure.playerErrorCode,
            failure.playerErrorName,
            failure.errorClass,
            failure.errorMessage,
        )
    }
}
