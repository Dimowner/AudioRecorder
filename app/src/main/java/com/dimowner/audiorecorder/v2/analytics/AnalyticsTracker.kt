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

/**
 * Analytics abstraction for tracking key product events.
 *
 * Implementations should be thread-safe; call sites may invoke these methods
 * from background threads (e.g. [DatabaseMigrationService]).
 *
 * The default wiring provides a [NoOpAnalyticsTracker] so nothing is sent until
 * a real SDK is plugged in. To integrate an analytics provider:
 *  1. Create a class implementing this interface (e.g. `FirebaseAnalyticsTracker`).
 *  2. Replace the [NoOpAnalyticsTracker] binding in [AnalyticsModule] with the new class.
 */
interface AnalyticsTracker {

    // ── Database migration ───────────────────────────────────────────────────

    /**
     * Fired when the background migration service kicks off the SQLite → Room
     * data transfer. Useful for measuring how many users were in a "needs
     * migration" state versus fresh installs.
     */
    fun trackDbMigrationStarted()

    /**
     * Fired after every record (regular + trash) has been written to Room and
     * the migration-complete preference flag has been set.
     *
     * @param migratedRecordCount Total number of records (including trash items)
     *                            that were written to Room in this run.
     * @param durationMs          Wall-clock time the migration took, in milliseconds.
     */
    fun trackDbMigrationSuccess(migratedRecordCount: Int, durationMs: Long)

    /**
     * Fired when an unrecoverable exception interrupts the migration.
     * The service retries at most once per week (controlled by
     * [Prefs.getLastMigrationToRoomFailedTime]).
     *
     * @param error The exception that caused the failure.
     */
    fun trackDbMigrationFailed(error: Throwable)

    // ── App version switching ────────────────────────────────────────────────

    /**
     * Fired when the user deliberately switches from the legacy V1 app to V2
     * via the V1 settings screen ("Try New App" button).
     */
    fun trackSwitchToAppV2()

    /**
     * Fired when a V2 user switches back to the legacy V1 app via the V2
     * settings screen ("Switch to Legacy App" toggle).
     */
    fun trackSwitchToLegacyApp()

    // ── Broken record recovery ───────────────────────────────────────────────

    /**
     * Fired when one or more broken records are found at startup (i.e. recordings
     * that were interrupted by a crash or reboot before the recorder was properly stopped).
     *
     * @param format Audio format of the broken record shown to the user (e.g. "m4a", "wav", "3gp").
     * @param count  Total number of broken records found in this scan.
     */
    fun trackBrokenRecordDetected(format: String, count: Int)

    /**
     * Fired when a broken record is successfully repaired.
     *
     * @param format Audio format of the restored file.
     */
    fun trackBrokenRecordRestoreSuccess(format: String)

    /**
     * Fired when an attempt to repair a broken record fails.
     *
     * @param format Audio format of the file that could not be restored.
     */
    fun trackBrokenRecordRestoreFailed(format: String)

    // ── Lost records ─────────────────────────────────────────────────────────

    /**
     * Fired when records are found in the database whose audio files no longer
     * exist on disk (e.g. deleted externally, storage permission changed, SD card removed).
     *
     * @param count Number of lost records discovered in this scan.
     */
    fun trackLostRecordsDetected(count: Int)
}
