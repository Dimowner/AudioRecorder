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

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the state of Bluetooth microphone availability and routing.
 *
 * @property isAvailable Whether a Bluetooth microphone is currently connected and available.
 * @property isEnabled Whether Bluetooth audio routing is currently enabled for recording.
 * @property deviceName The product name of the connected Bluetooth device, if available.
 */
data class BluetoothMicState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val deviceName: String? = null
)

/**
 * Helper class to manage Bluetooth microphone detection and audio routing.
 * Handles both modern (API 31+) and legacy Bluetooth SCO APIs.
 *
 * This class monitors connected Bluetooth audio devices and provides functionality to:
 * - Detect when Bluetooth headsets with microphones are connected/disconnected
 * - Enable/disable Bluetooth microphone routing
 * - Get the name of connected Bluetooth devices
 *
 * @property context Application context for accessing system services.
 */
@Singleton
class AudioManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _bluetoothMicState = MutableStateFlow(BluetoothMicState())
    val bluetoothMicState: StateFlow<BluetoothMicState> = _bluetoothMicState.asStateFlow()

    private var isBluetoothScoActive = false
    private var previousAudioMode = AudioManager.MODE_NORMAL

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            Timber.d("Audio devices added: ${addedDevices.size}")
            updateBluetoothDeviceState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            Timber.d("Audio devices removed: ${removedDevices.size}")
            updateBluetoothDeviceState()
        }
    }

    /**
     * Registers the audio device callback to monitor Bluetooth device changes.
     * Should be called when the component using this helper becomes active.
     */
    fun register() {
        Timber.d("Registering AudioDeviceCallback")
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateBluetoothDeviceState()
    }

    /**
     * Unregisters the audio device callback to stop monitoring Bluetooth device changes.
     * Should be called when the component using this helper becomes inactive.
     */
    fun unregister() {
        Timber.d("Unregistering AudioDeviceCallback")
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    /**
     * Enables or disables Bluetooth microphone routing for audio recording.
     *
     * On API 31+ (Android 12+), uses the modern setCommunicationDevice() API.
     * On older versions, falls back to startBluetoothSco() / stopBluetoothSco().
     *
     * @param enable true to enable Bluetooth microphone, false to disable.
     */
    fun enableBluetoothMic(enable: Boolean) {
        Timber.d("enableBluetoothMic: $enable")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableBluetoothMicApi31Plus(enable)
        } else {
            enableBluetoothMicLegacy(enable)
        }

        updateBluetoothDeviceState()
    }

    /**
     * Modern API (31+) implementation for Bluetooth microphone routing.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun enableBluetoothMicApi31Plus(enable: Boolean) {
        try {
            if (enable) {
                val bluetoothDevice = getBluetoothAudioInputDevice()
                if (bluetoothDevice != null) {
                    previousAudioMode = audioManager.mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val success = audioManager.setCommunicationDevice(bluetoothDevice)
                    Timber.d("setCommunicationDevice result: $success for device: ${bluetoothDevice.productName}")
                    if (!success) {
                        Timber.w("Failed to set communication device")
                        audioManager.mode = previousAudioMode
                    }
                } else {
                    Timber.w("No Bluetooth audio input device available")
                }
            } else {
                audioManager.clearCommunicationDevice()
                audioManager.mode = previousAudioMode
                Timber.d("Cleared communication device and restored audio mode")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enabling/disabling Bluetooth mic (API 31+)")
        }
    }

    /**
     * Legacy API implementation for Bluetooth microphone routing using SCO.
     */
    private fun enableBluetoothMicLegacy(enable: Boolean) {
        try {
            if (enable) {
                if (!isBluetoothScoActive && hasBluetoothAudioInputDevice()) {
                    previousAudioMode = audioManager.mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    isBluetoothScoActive = true
                    Timber.d("Started Bluetooth SCO")
                }
            } else {
                if (isBluetoothScoActive) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.mode = previousAudioMode
                    isBluetoothScoActive = false
                    Timber.d("Stopped Bluetooth SCO")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enabling/disabling Bluetooth mic (legacy)")
        }
    }

    /**
     * Returns the product name of the connected Bluetooth audio input device.
     * Requires BLUETOOTH_CONNECT permission on API 31+.
     *
     * @return The device name, or null if no device is connected or permission is missing.
     */
    fun getConnectedBluetoothDeviceName(): String? {
        // Check permission on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!PermissionHelper.hasBluetoothConnectPermission(context)) {
                Timber.w("BLUETOOTH_CONNECT permission not granted")
                return null
            }
        }

        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.firstOrNull { isBluetoothInputDevice(it) }?.productName?.toString()
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException getting Bluetooth device name")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting Bluetooth device name")
            null
        }
    }

    /**
     * Releases resources and resets audio routing to normal state.
     * Should be called when the helper is no longer needed.
     */
    fun release() {
        Timber.d("Releasing AudioManagerHelper")
        
        try {
            // Stop Bluetooth SCO if active
            if (isBluetoothScoActive) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                isBluetoothScoActive = false
            }

            // Clear communication device on API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }

            // Reset audio mode
            audioManager.mode = AudioManager.MODE_NORMAL

            // Unregister callback
            unregister()

            // Reset state
            _bluetoothMicState.value = BluetoothMicState()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing AudioManagerHelper")
        }
    }

    /**
     * Updates the Bluetooth device state and notifies observers via StateFlow.
     */
    private fun updateBluetoothDeviceState() {
        val isAvailable = hasBluetoothAudioInputDevice()
        val deviceName = if (isAvailable) getConnectedBluetoothDeviceName() else null
        val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isBluetoothEnabledApi31Plus()
        } else {
            isBluetoothScoActive
        }

        _bluetoothMicState.value = BluetoothMicState(
            isAvailable = isAvailable,
            isEnabled = isEnabled,
            deviceName = deviceName
        )

        Timber.d("Updated Bluetooth state: ${_bluetoothMicState.value}")
    }

    /**
     * Checks if Bluetooth routing is currently enabled on API 31+.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun isBluetoothEnabledApi31Plus(): Boolean {
        return try {
            val currentDevice = audioManager.communicationDevice
            currentDevice != null && isBluetoothInputDevice(currentDevice)
        } catch (e: Exception) {
            Timber.e(e, "Error checking if Bluetooth is enabled")
            false
        }
    }

    /**
     * Checks if any Bluetooth audio input device is currently available.
     */
    private fun hasBluetoothAudioInputDevice(): Boolean {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.any { isBluetoothInputDevice(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for Bluetooth audio input device")
            false
        }
    }

    /**
     * Gets the first available Bluetooth audio input device on API 31+.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun getBluetoothAudioInputDevice(): AudioDeviceInfo? {
        return try {
            audioManager.availableCommunicationDevices.firstOrNull { 
                isBluetoothInputDevice(it) 
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting Bluetooth audio input device")
            null
        }
    }

    /**
     * Checks if the given audio device is a Bluetooth input device (microphone).
     *
     * @param device The audio device to check.
     * @return true if the device is a Bluetooth input device with microphone capability.
     */
    private fun isBluetoothInputDevice(device: AudioDeviceInfo): Boolean {
        // Must be an input source (has microphone)
        if (!device.isSource) {
            return false
        }

        // Check for Bluetooth device types
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
            else -> {
                // Check for BLE headset on API 31+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } else {
                    false
                }
            }
        }
    }
}
