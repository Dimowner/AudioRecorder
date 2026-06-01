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
}
