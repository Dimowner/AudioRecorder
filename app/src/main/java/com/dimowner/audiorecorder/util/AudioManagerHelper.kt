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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class to wrap AudioDeviceInfo with display name
 *
 * @property id Unique identifier for the device
 * @property productName Display name of the device
 * @property type AudioDeviceInfo type constant
 * @property audioDeviceInfo Original AudioDeviceInfo object
 */
data class BluetoothDeviceInfo(
    val id: Int,
    val productName: String,
    val type: Int,
    val audioDeviceInfo: AudioDeviceInfo
)

/**
 * Data class representing the state of Bluetooth microphone availability and routing.
 *
 * @property isAvailable Whether a Bluetooth microphone is currently connected and available.
 * @property isEnabled Whether Bluetooth audio routing is currently enabled for recording.
 * @property deviceName The product name of the connected Bluetooth device, if available.
 * @property connectedDevices List of all currently connected Bluetooth microphones.
 * @property selectedDevice The currently selected Bluetooth device, if any.
 */
data class BluetoothMicState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val deviceName: String? = null,
    val connectedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedDevice: BluetoothDeviceInfo? = null
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
    @param:ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _bluetoothMicState = MutableStateFlow(BluetoothMicState())
    val bluetoothMicState: StateFlow<BluetoothMicState> = _bluetoothMicState.asStateFlow()

    private var isBluetoothScoActive = false
    private var previousAudioMode = AudioManager.MODE_NORMAL

    private var selectedBluetoothDevice: BluetoothDeviceInfo? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            Timber.d("Audio devices added: ${addedDevices.size}")
            updateBluetoothDeviceState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            Timber.d("Audio devices removed: ${removedDevices.size}")
            val removedDeviceIds = removedDevices.map { it.id }.toSet()
            // Clear selection if the selected device was removed
            if (selectedBluetoothDevice != null && removedDeviceIds.contains(selectedBluetoothDevice!!.id)) {
                Timber.d("Selected Bluetooth device was removed, clearing selection")
                selectedBluetoothDevice = null
                // Disable Bluetooth routing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        audioManager.clearCommunicationDevice()
                        audioManager.mode = previousAudioMode
                    } catch (e: Exception) {
                        Timber.e(e, "Error clearing communication device")
                    }
                } else {
                    if (isBluetoothScoActive) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.mode = previousAudioMode
                        isBluetoothScoActive = false
                    }
                }
            }
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
    suspend fun enableBluetoothMic(enable: Boolean) {
        Timber.d("enableBluetoothMic: $enable")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            enableBluetoothMicApi31Plus(enable)
        } else {
            enableBluetoothMicLegacy(enable)
        }

        delay(500)
        updateBluetoothDeviceState()
    }

    /**
     * Returns a list of all currently connected Bluetooth input devices.
     * Includes TYPE_BLUETOOTH_SCO and TYPE_BLE_HEADSET (API 31+) devices.
     * Uses BLUETOOTH_CONNECT permission to retrieve actual product names.
     *
     * @return List of AudioDeviceInfo for all connected Bluetooth microphones
     */
    fun getConnectedBluetoothInputDevices(): List<AudioDeviceInfo> {
        return try {
            audioManager.availableCommunicationDevices.filter {
                isBluetoothInputDevice(it)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting connected Bluetooth input devices")
            emptyList()
        }
    }

    /**
     * Selects a specific Bluetooth device for audio input.
     * For API 31+: Uses AudioManager.setCommunicationDevice(selectedDevice)
     * For API < 31: Uses startBluetoothSco() (specific device routing limited by system)
     *
     * @param device The BluetoothDeviceInfo to select, or null to clear selection
     */
    fun selectBluetoothDevice(device: BluetoothDeviceInfo?) {
        Timber.d("selectBluetoothDevice: ${device?.productName}")
        selectedBluetoothDevice = device
        updateBluetoothDeviceState()
    }

    /**
     * Modern API (31+) implementation for Bluetooth microphone routing.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun enableBluetoothMicApi31Plus(enable: Boolean) {
        try {
            if (enable) {
                // Use selected device if available, otherwise use first available device
                val bluetoothDevice = selectedBluetoothDevice?.audioDeviceInfo
                    ?: getBluetoothAudioInputDevice()
                
                if (bluetoothDevice != null) {
                    previousAudioMode = audioManager.mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val success = audioManager.setCommunicationDevice(bluetoothDevice)
                    Timber.d("setCommunicationDevice result: $success for device: ${bluetoothDevice.productName}")
                    if (!success) {
                        Timber.w("Failed to set communication device")
                        audioManager.mode = previousAudioMode
                    } else {
                        Timber.d("Success to set communication device!")
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

        return getAvailableCommunicationDevices().firstOrNull()?.productName?.toString()
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
        val connectedDevices = getConnectedBluetoothInputDevices().map { deviceInfo ->
            val productName = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!PermissionHelper.hasBluetoothConnectPermission(context)) {
                        "Bluetooth Device"
                    } else {
                        deviceInfo.productName.toString().ifEmpty { "Bluetooth Device" }
                    }
                } else {
                    deviceInfo.productName.toString().ifEmpty { "Bluetooth Device" }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting product name")
                "Bluetooth Device"
            }
            
            BluetoothDeviceInfo(
                id = deviceInfo.id,
                productName = productName,
                type = deviceInfo.type,
                audioDeviceInfo = deviceInfo
            )
        }
        
        val isAvailable = connectedDevices.isNotEmpty()
        val deviceName = if (isAvailable) {
            selectedBluetoothDevice?.productName ?: connectedDevices.firstOrNull()?.productName
        } else {
            null
        }
        
        val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isBluetoothEnabledApi31Plus()
        } else {
            isBluetoothScoActive
        }
        
        // Validate selected device is still in connected devices
        val validatedSelectedDevice = if (selectedBluetoothDevice != null) {
            connectedDevices.find { it.id == selectedBluetoothDevice!!.id }
        } else {
            null
        }
        
        // If selected device is no longer valid, clear it
        if (selectedBluetoothDevice != null && validatedSelectedDevice == null) {
            selectedBluetoothDevice = null
        }

        _bluetoothMicState.value = BluetoothMicState(
            isAvailable = isAvailable,
            isEnabled = isEnabled,
            deviceName = deviceName,
            connectedDevices = connectedDevices,
            selectedDevice = validatedSelectedDevice
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
        return getAvailableCommunicationDevices().isNotEmpty()
    }

    /**
     * Gets the first available Bluetooth audio input device on API 31+.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun getBluetoothAudioInputDevice(): AudioDeviceInfo? {
        return getAvailableCommunicationDevices().firstOrNull()
    }

    private fun getAvailableCommunicationDevices(): List<AudioDeviceInfo> {
        return try {
            audioManager.availableCommunicationDevices.filter {
                isBluetoothInputDevice(it)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting Bluetooth audio input device")
            emptyList()
        }
    }

    /**
     * Checks if the given audio device is a Bluetooth input device (microphone).
     *
     * @param device The audio device to check.
     * @return true if the device is a Bluetooth input device with microphone capability.
     */
    private fun isBluetoothInputDevice(device: AudioDeviceInfo): Boolean {
// This code commented out because of this logic is filtering out actual bluetooth headset MIC.
//        // Must be an input source (has microphone)
//        if (!device.isSource) {
//            return false
//        }

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
