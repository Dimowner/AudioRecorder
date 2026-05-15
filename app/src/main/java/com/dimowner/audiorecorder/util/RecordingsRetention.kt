package com.dimowner.audiorecorder.util

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Opt-in auto-cleanup of old recordings.
 *
 * Stores a single integer setting `max_age_days` in `recordings_retention`
 * SharedPreferences. When non-zero, [sweepIfDue] deletes recording files older
 * than that many days from the given directory. Throttled to one sweep per
 * 24h via a `last_run` timestamp.
 *
 * Default `max_age_days = 0` means the feature is **disabled** and no files
 * are ever touched — drop-in safe for users who don't opt in.
 *
 * A Settings UI to set the threshold is intentionally not bundled with this
 * change; the engine is the load-bearing piece and the UI can be a follow-up.
 * Callers (or a future Settings row) configure it via [setMaxAgeDays].
 */
object RecordingsRetention {
    private const val PREFS_NAME = "recordings_retention"
    private const val KEY_MAX_AGE_DAYS = "max_age_days"
    private const val KEY_LAST_RUN = "last_run"
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    fun setMaxAgeDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_MAX_AGE_DAYS, days.coerceAtLeast(0)).apply()
    }

    fun getMaxAgeDays(context: Context): Int =
        prefs(context).getInt(KEY_MAX_AGE_DAYS, 0)

    /**
     * Delete recording files older than `max_age_days` from [recordingDir],
     * if a sweep hasn't run in the last 24h. No-op when `max_age_days = 0`
     * (the default) or when the directory is missing.
     */
    fun sweepIfDue(context: Context, recordingDir: File?) {
        val days = getMaxAgeDays(context)
        if (days <= 0 || recordingDir == null || !recordingDir.isDirectory) return
        val now = System.currentTimeMillis()
        val lastRun = prefs(context).getLong(KEY_LAST_RUN, 0L)
        if (now - lastRun < ONE_DAY_MS) return
        val cutoff = now - days.toLong() * ONE_DAY_MS
        recordingDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
        prefs(context).edit().putLong(KEY_LAST_RUN, now).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
