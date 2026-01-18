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

package com.dimowner.audiorecorder.v2.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dimowner.audiorecorder.util.PermissionHelper

/**
 * State holder for Bluetooth recording permissions.
 *
 * @property requestPermissions Function to request the permissions.
 * @property hasAllPermissions True if all required permissions are granted.
 */
class BluetoothPermissionState(
    val requestPermissions: () -> Unit,
    val hasAllPermissions: Boolean
)

/**
 * Creates and remembers a BluetoothPermissionState for requesting Bluetooth recording permissions.
 * This handles multiple permissions including RECORD_AUDIO, MODIFY_AUDIO_SETTINGS,
 * and BLUETOOTH_CONNECT (on API 31+).
 *
 * **Note:** The `hasAllPermissions` property is evaluated when the composable is first created
 * and will not automatically update when permissions change. After using `requestPermissions()`,
 * rely on the `onAllGranted` and `onDenied` callbacks to respond to permission changes.
 * The parent composable should recompose (e.g., by updating state) if you need the
 * `hasAllPermissions` value to refresh.
 *
 * @param context The application context.
 * @param onAllGranted Callback invoked when all permissions are granted.
 * @param onDenied Callback invoked when any permission is denied. Receives list of denied permissions.
 * @return BluetoothPermissionState for managing permission requests.
 */
@Composable
fun rememberBluetoothRecordingPermissionState(
    context: Context,
    onAllGranted: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {}
): BluetoothPermissionState {
    val permissionsArray = remember { PermissionHelper.getBluetoothRecordingPermissionsArray() }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()
        if (deniedPermissions.isEmpty()) {
            onAllGranted()
        } else {
            onDenied(deniedPermissions)
        }
    }

    // Note: This checks permission state when the composable is first created.
    // Permission state won't update automatically - use the launcher callbacks
    // or re-compose the parent to refresh the state.
    val hasAllPermissions = remember {
        PermissionHelper.hasBluetoothRecordingPermissions(context)
    }

    return remember(hasAllPermissions) {
        BluetoothPermissionState(
            requestPermissions = { launcher.launch(permissionsArray) },
            hasAllPermissions = hasAllPermissions
        )
    }
}

/**
 * Opens the application settings page where users can manually grant permissions.
 *
 * @param context The application context.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

/**
 * State holder for a single permission.
 *
 * @property requestPermission Function to request the permission.
 * @property hasPermission True if the permission is granted.
 */
class SinglePermissionState(
    val requestPermission: () -> Unit,
    val hasPermission: Boolean
)

/**
 * Creates and remembers a SinglePermissionState for requesting a single permission.
 *
 * **Note:** The `hasPermission` property is evaluated when the composable is first created
 * or when the permission parameter changes, and will not automatically update when the
 * permission state changes. After using `requestPermission()`, rely on the `onGranted`
 * and `onDenied` callbacks to respond to permission changes. The parent composable should
 * recompose (e.g., by updating state) if you need the `hasPermission` value to refresh.
 *
 * @param permission The permission string to request.
 * @param context The application context.
 * @param onGranted Callback invoked when the permission is granted.
 * @param onDenied Callback invoked when the permission is denied.
 * @return SinglePermissionState for managing the permission request.
 */
@Composable
fun rememberSinglePermissionState(
    permission: String,
    context: Context,
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {}
): SinglePermissionState {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            onDenied()
        }
    }

    // Note: This checks permission state when the composable is first created or when
    // the permission parameter changes. Permission state won't update automatically -
    // use the launcher callbacks or re-compose the parent to refresh the state.
    val hasPermission = remember(permission) {
        when (permission) {
            PermissionHelper.RECORD_AUDIO -> PermissionHelper.hasRecordAudioPermission(context)
            PermissionHelper.MODIFY_AUDIO_SETTINGS -> PermissionHelper.hasModifyAudioSettingsPermission(context)
            PermissionHelper.BLUETOOTH_CONNECT -> PermissionHelper.hasBluetoothConnectPermission(context)
            else -> false
        }
    }

    return remember(hasPermission) {
        SinglePermissionState(
            requestPermission = { launcher.launch(permission) },
            hasPermission = hasPermission
        )
    }
}
