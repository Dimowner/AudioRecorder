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

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import timber.log.Timber
import javax.inject.Inject

/**
 * [AnalyticsTracker] implementation that forwards events to Firebase Analytics.
 *
 * Each method maps to a dedicated custom event with snake_case parameter names
 * that conform to the Firebase 40-character name limit.
 *
 * Note: Firebase Analytics is only active in release builds (i.e. builds that
 * include a valid `google-services.json`). In debug builds the [NoOpAnalyticsTracker]
 * is bound instead via [com.dimowner.audiorecorder.v2.di.AnalyticsModule].
 */
class FirebaseAnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsTracker {

    // ── Database migration ───────────────────────────────────────────────────

    override fun trackDbMigrationStarted() {
        log(EVENT_DB_MIGRATION_STARTED)
        firebaseAnalytics.logEvent(EVENT_DB_MIGRATION_STARTED) {}
    }

    override fun trackDbMigrationSuccess(migratedRecordCount: Int, durationMs: Long) {
        log(EVENT_DB_MIGRATION_SUCCESS, "migrated_record_count=$migratedRecordCount, duration_ms=$durationMs")
        firebaseAnalytics.logEvent(EVENT_DB_MIGRATION_SUCCESS) {
            param(PARAM_MIGRATED_RECORD_COUNT, migratedRecordCount.toLong())
            param(PARAM_DURATION_MS, durationMs)
        }
    }

    override fun trackDbMigrationFailed(error: Throwable) {
        log(EVENT_DB_MIGRATION_FAILED, "error=${error.message}")
        firebaseAnalytics.logEvent(EVENT_DB_MIGRATION_FAILED) {
            param(PARAM_ERROR_MESSAGE, error.message?.take(100) ?: "unknown")
        }
    }

    // ── App version switching ────────────────────────────────────────────────

    override fun trackSwitchToAppV2() {
        log(EVENT_SWITCH_TO_APP_V2)
        firebaseAnalytics.logEvent(EVENT_SWITCH_TO_APP_V2) {}
    }

    override fun trackSwitchToLegacyApp() {
        log(EVENT_SWITCH_TO_LEGACY_APP)
        firebaseAnalytics.logEvent(EVENT_SWITCH_TO_LEGACY_APP) {}
    }

    // ── Broken record recovery ───────────────────────────────────────────────

    override fun trackBrokenRecordDetected(format: String, count: Int) {
        log(EVENT_BROKEN_RECORD_DETECTED, "format=$format, count=$count")
        firebaseAnalytics.logEvent(EVENT_BROKEN_RECORD_DETECTED) {
            param(PARAM_FORMAT, format)
            param(PARAM_COUNT, count.toLong())
        }
    }

    override fun trackBrokenRecordRestoreSuccess(format: String) {
        log(EVENT_BROKEN_RECORD_RESTORE_SUCCESS, "format=$format")
        firebaseAnalytics.logEvent(EVENT_BROKEN_RECORD_RESTORE_SUCCESS) {
            param(PARAM_FORMAT, format)
        }
    }

    override fun trackBrokenRecordRestoreFailed(format: String) {
        log(EVENT_BROKEN_RECORD_RESTORE_FAILED, "format=$format")
        firebaseAnalytics.logEvent(EVENT_BROKEN_RECORD_RESTORE_FAILED) {
            param(PARAM_FORMAT, format)
        }
    }

    // ── Lost records ─────────────────────────────────────────────────────────

    override fun trackLostRecordsDetected(count: Int) {
        log(EVENT_LOST_RECORDS_DETECTED, "count=$count")
        firebaseAnalytics.logEvent(EVENT_LOST_RECORDS_DETECTED) {
            param(PARAM_COUNT, count.toLong())
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun log(event: String, params: String = "") {
        if (params.isEmpty()) {
            Timber.d("FirebaseAnalyticsTracker: $event")
        } else {
            Timber.d("FirebaseAnalyticsTracker: $event [$params]")
        }
    }

    // ── Event / parameter name constants ─────────────────────────────────────

    companion object {
        const val EVENT_DB_MIGRATION_STARTED = "db_migration_started"
        const val EVENT_DB_MIGRATION_SUCCESS = "db_migration_success"
        const val EVENT_DB_MIGRATION_FAILED = "db_migration_failed"
        const val EVENT_SWITCH_TO_APP_V2 = "switch_to_app_v2"
        const val EVENT_SWITCH_TO_LEGACY_APP = "switch_to_legacy_app"
        const val EVENT_BROKEN_RECORD_DETECTED = "broken_record_detected"
        const val EVENT_BROKEN_RECORD_RESTORE_SUCCESS = "broken_record_restore_ok"
        const val EVENT_BROKEN_RECORD_RESTORE_FAILED = "broken_record_restore_fail"
        const val EVENT_LOST_RECORDS_DETECTED = "lost_records_detected"

        const val PARAM_MIGRATED_RECORD_COUNT = "migrated_record_count"
        const val PARAM_DURATION_MS = "duration_ms"
        const val PARAM_ERROR_MESSAGE = "error_message"
        const val PARAM_FORMAT = "format"
        const val PARAM_COUNT = "count"
    }
}

