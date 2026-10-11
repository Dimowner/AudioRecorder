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
import com.dimowner.audiorecorder.v2.audio.BluetoothCaptureController
import com.dimowner.audiorecorder.v2.audio.CaptureDiagnostics
import com.dimowner.audiorecorder.v2.audio.MicrophoneCaptureSettings
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    @param:ApplicationContext private val context: Context,
    private val prefs: PrefsV2 = PrefsV2Impl(context),
    private val diagnostics: CaptureDiagnostics = CaptureDiagnostics(),
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _bluetoothMicState = MutableStateFlow(BluetoothMicState())
    val bluetoothMicState: StateFlow<BluetoothMicState> = _bluetoothMicState.asStateFlow()

    // The switch expresses intent; readiness is a separate observed state owned through capture.
    @Volatile private var bluetoothRequested = false
    @Volatile private var recordingRouteOwned = false
    @Volatile private var recordingUsesBluetooth = false
    private var previousMicrophoneMode: Int? = null
    private var microphoneMode: BluetoothAudioMode? = null
    private val routeController by lazy {
        BluetoothCaptureController(context, diagnostics) { updateBluetoothDeviceState() }
    }
    internal val routeState get() = routeController.state
    // Unlike recorder amplitude flags, this covers preparation and native-start transitions.
    internal val ownsBluetoothRecordingRoute: Boolean get() = recordingRouteOwned && recordingUsesBluetooth
    internal val useBluetoothForRecording: Boolean get() = bluetoothRequested ||
        (prefs.alwaysUseBluetoothMic && hasBluetoothAudioInputDevice())
    internal val isRouteReadyForRecording: Boolean get() =
        if (if (recordingRouteOwned) recordingUsesBluetooth else bluetoothRequested)
            routeController.state.value.phase == com.dimowner.audiorecorder.v2.audio.BluetoothRoutePhase.READY
        else microphoneMode?.let { audioManager.mode == it.value } ?: true

    internal fun bluetoothInputForRecording(): com.dimowner.audiorecorder.v2.audio.BluetoothInputSelection? =
        if (ownsBluetoothRecordingRoute) routeController.inputSelection() else null

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
            }
            // Disable routing if it was enabled and no Bluetooth input device remains
            if (bluetoothRequested && !hasBluetoothAudioInputDevice()) {
                Timber.d("Last Bluetooth input device removed, disabling routing")
                routeController.inputDisconnected()
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
     * Uses the selected SCO, HFP voice-recognition, or communication-device API.
     * An unavailable API is reported as unsupported, never replaced by another route.
     *
     * @param enable true to enable Bluetooth microphone, false to disable.
     */
    suspend fun enableBluetoothMic(enable: Boolean) {
        Timber.d("enableBluetoothMic: $enable")
        withContext(Dispatchers.Main.immediate) {
            if (recordingRouteOwned) return@withContext
            bluetoothRequested = enable
            if (enable) requestRoute() else disableBluetoothRouting()
            updateBluetoothDeviceState()
        }
    }

    private fun requestRoute(route: BluetoothCaptureRoute = prefs.bluetoothCaptureRoute,
        mode: BluetoothAudioMode = prefs.bluetoothAudioMode) {
        val device = selectedBluetoothDevice?.audioDeviceInfo ?: getAvailableCommunicationDevices().firstOrNull()
        routeController.start(route, mode, device)
    }

    /** Service preflight: never create an experiment file before Bluetooth audio is ready. */
    internal suspend fun prepareRecording(settings: MicrophoneCaptureSettings = MicrophoneCaptureSettings.from(prefs),
        useBluetooth: Boolean = useBluetoothForRecording): Boolean = withContext(Dispatchers.Main.immediate) {
        recordingRouteOwned = true
        recordingUsesBluetooth = useBluetooth
        if (!useBluetooth) {
            // A preview may have been enabled after the service froze its non-Bluetooth intent.
            // Release it before saving the mode to restore; otherwise its communication mode
            // becomes this session's baseline and its input can leak into phone capture.
            disableBluetoothRouting()
            if (settings.onlyBluetooth) {
                diagnostics.route("Non-Bluetooth microphone: Bluetooth-only scope; Android audio mode, preprocessing and software gain overrides skipped.")
                true
            } else try {
                // Mode is global Android policy, so own/restore it even with no Bluetooth route.
                if (previousMicrophoneMode == null) previousMicrophoneMode = audioManager.mode
                microphoneMode = settings.mode
                audioManager.mode = settings.mode.value
                val ready = audioManager.mode == settings.mode.value
                diagnostics.route("Non-Bluetooth microphone: all-microphone scope.\nRequested Android audio mode: ${settings.mode}; actual mode: ${audioManager.mode}" +
                    if (ready) "\nMode applied; Bluetooth routing not requested." else "\nMode rejected; capture not started.")
                if (!ready) restoreMicrophoneMode()
                ready
            } catch (e: Exception) {
                diagnostics.route("Android audio mode request failed: ${e.message}")
                restoreMicrophoneMode()
                false
            }
        } else {
            bluetoothRequested = true
            requestRoute(settings.route, settings.mode)
            routeController.awaitReady()
        }
    }

    /** Main-thread system-capture ownership: release previews and block new microphone overrides. */
    internal fun prepareSystemPlayback() {
        recordingRouteOwned = true
        recordingUsesBluetooth = false
        disableBluetoothRouting()
        restoreMicrophoneMode()
        diagnostics.route("System playback capture: Bluetooth microphone routing and processing not applied.")
    }

    /** Keep the switch intent for the next comparison, but release this session's audio route. */
    internal fun finishRecordingRoute() {
        recordingRouteOwned = false
        recordingUsesBluetooth = false
        disableBluetoothRouting()
        restoreMicrophoneMode()
    }

    private fun restoreMicrophoneMode() {
        previousMicrophoneMode?.let { original -> runCatching { audioManager.mode = original }.onFailure { Timber.w(it) } }
        previousMicrophoneMode = null
        microphoneMode = null
    }

    /**
     * Selects a specific Bluetooth device for audio input.
     * The selected capture route determines which API is requested. Standard SCO link
     * establishment is system-managed; capture input selection is verified separately.
     *
     * @param device The BluetoothDeviceInfo to select, or null to clear selection
     */
    fun selectBluetoothDevice(device: BluetoothDeviceInfo?) {
        if (recordingRouteOwned) return
        Timber.d("selectBluetoothDevice: ${device?.productName}")
        selectedBluetoothDevice = device
        // If routing is already enabled, switch to the newly selected device immediately
        if (device != null && bluetoothRequested) requestRoute()
        updateBluetoothDeviceState()
    }

    /** Releases only routing requested by this helper. */
    private fun disableBluetoothRouting() {
        routeController.stop()
    }

    /**
     * Releases resources and resets audio routing to normal state.
     * Should be called when the helper is no longer needed.
     */
    fun release() {
        Timber.d("Releasing AudioManagerHelper")
        if (recordingRouteOwned) {
            unregister()
            return
        }

        try {
            // Disable Bluetooth routing if this helper enabled it
            disableBluetoothRouting()

            // Unregister callback
            unregister()

            // Reset state
            selectedBluetoothDevice = null
            bluetoothRequested = false
            _bluetoothMicState.value = BluetoothMicState()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing AudioManagerHelper")
        }
    }

    /**
     * Updates the Bluetooth device state and notifies observers via StateFlow.
     */
    private fun updateBluetoothDeviceState(defaultName: String = "Bluetooth Device") {
        val connectedDevices = getAvailableCommunicationDevices().map { deviceInfo ->
            val productName = deviceInfo.productName.toString().ifEmpty { defaultName }
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
        
        val isEnabled = bluetoothRequested
        
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.availableCommunicationDevices.filter {
                    isBluetoothInputDevice(it)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting Bluetooth audio input device")
                emptyList()
            }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { device ->
                isBluetoothInputDevice(device)
            }
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
