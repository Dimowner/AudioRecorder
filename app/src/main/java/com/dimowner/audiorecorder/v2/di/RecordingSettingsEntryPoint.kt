package com.dimowner.audiorecorder.v2.di

import com.dimowner.audiorecorder.v2.data.PrefsV2
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Exposes the recording preferences to classes Hilt cannot inject into.
 *
 * The one caller today is [com.dimowner.audiorecorder.app.TransparentRecordingActivity], the
 * widget and shortcut entry point: it is a plain [android.app.Activity], yet it has to know
 * whether the next recording captures system audio, because collecting MediaProjection consent
 * needs an Activity.
 *
 * Usage:
 * ```kotlin
 * val entryPoint = EntryPointAccessors.fromApplication(
 *     context.applicationContext,
 *     RecordingSettingsEntryPoint::class.java
 * )
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordingSettingsEntryPoint {
    fun prefsV2(): PrefsV2
}
