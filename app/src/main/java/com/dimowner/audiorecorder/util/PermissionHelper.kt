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

package com.dimowner.audiorecorder.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper object for managing audio recording and Bluetooth-related permissions.
 * Provides utilities for checking permissions and determining which permissions
 * are required based on the Android API level.
 */
object PermissionHelper {

    /**
     * Permission constant for recording audio.
     */
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

    /**
     * Permission constant for modifying audio settings.
     * This is a normal permission, typically granted at install time.
     */
    const val MODIFY_AUDIO_SETTINGS = Manifest.permission.MODIFY_AUDIO_SETTINGS

    /**
     * Permission constant for connecting to Bluetooth devices.
     * Required on Android 12+ (API 31+) for retrieving device names and managing connections.
     */
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

    /**
     * Returns the BLUETOOTH_CONNECT permission string on API 31+, null otherwise.
     */
    val PERMISSION_BLUETOOTH_CONNECT: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_CONNECT
        } else {
            null
        }

    /**
     * Checks if the RECORD_AUDIO permission is granted.
     *
     * @param context The application context.
     * @return true if the permission is granted, false otherwise.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the MODIFY_AUDIO_SETTINGS permission is granted.
     * This is a normal permission and should typically be granted at install time.
     *
     * @param context The application context.
     * @return true if the permission is granted, false otherwise.
     */
    fun hasModifyAudioSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            MODIFY_AUDIO_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the BLUETOOTH_CONNECT permission is granted.
     * On API levels below 31, this always returns true since the permission is not required.
     *
     * @param context The application context.
     * @return true if the permission is granted or not required, false otherwise.
     */
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Determines if the BLUETOOTH_CONNECT permission is required.
     *
     * @return true on Android 12+ (API 31+), false otherwise.
     */
    fun isBluetoothPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Checks if all basic recording permissions are granted.
     * Basic permissions include RECORD_AUDIO and MODIFY_AUDIO_SETTINGS.
     *
     * @param context The application context.
     * @return true if all basic permissions are granted, false otherwise.
     */
    fun hasBasicRecordingPermissions(context: Context): Boolean {
        return hasRecordAudioPermission(context) && hasModifyAudioSettingsPermission(context)
    }

    /**
     * Checks if all Bluetooth recording permissions are granted.
     * This includes basic recording permissions plus BLUETOOTH_CONNECT on API 31+.
     *
     * @param context The application context.
     * @return true if all Bluetooth recording permissions are granted, false otherwise.
     */
    fun hasBluetoothRecordingPermissions(context: Context): Boolean {
        return hasBasicRecordingPermissions(context) && hasBluetoothConnectPermission(context)
    }

    /**
     * Returns a list of missing basic recording permissions.
     *
     * @param context The application context.
     * @return List of permission strings that are not granted.
     */
    fun getMissingBasicRecordingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!hasRecordAudioPermission(context)) {
            missing.add(RECORD_AUDIO)
        }
        if (!hasModifyAudioSettingsPermission(context)) {
            missing.add(MODIFY_AUDIO_SETTINGS)
        }
        return missing
    }

    /**
     * Returns a list of missing Bluetooth recording permissions.
     * Only includes BLUETOOTH_CONNECT on API 31+.
     *
     * @param context The application context.
     * @return List of permission strings that are not granted.
     */
    fun getMissingBluetoothRecordingPermissions(context: Context): List<String> {
        val missing = getMissingBasicRecordingPermissions(context).toMutableList()
        if (isBluetoothPermissionRequired() && !hasBluetoothConnectPermission(context)) {
            missing.add(BLUETOOTH_CONNECT)
        }
        return missing
    }

    /**
     * Returns an array of all Bluetooth recording permissions.
     * Includes RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, and BLUETOOTH_CONNECT (on API 31+).
     *
     * @return Array of permission strings.
     */
    fun getBluetoothRecordingPermissionsArray(): Array<String> {
        return if (isBluetoothPermissionRequired()) {
            arrayOf(RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, BLUETOOTH_CONNECT)
        } else {
            arrayOf(RECORD_AUDIO, MODIFY_AUDIO_SETTINGS)
        }
    }

    /**
     * Data class representing the result of a permission check.
     *
     * @property allGranted true if all permissions are granted, false otherwise.
     * @property missingPermissions List of permissions that are not granted.
     */
    data class PermissionCheckResult(
        val allGranted: Boolean,
        val missingPermissions: List<String>
    )

    /**
     * Performs a comprehensive check of all Bluetooth recording permissions.
     *
     * @param context The application context.
     * @return PermissionCheckResult containing the check results.
     */
    fun checkBluetoothRecordingPermissions(context: Context): PermissionCheckResult {
        val missingPermissions = getMissingBluetoothRecordingPermissions(context)
        return PermissionCheckResult(
            allGranted = missingPermissions.isEmpty(),
            missingPermissions = missingPermissions
        )
    }
}
