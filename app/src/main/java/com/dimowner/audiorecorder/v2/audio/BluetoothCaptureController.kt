package com.dimowner.audiorecorder.v2.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal enum class BluetoothRoutePhase { IDLE, CONNECTING, READY, FAILED }
internal data class BluetoothRouteState(
    val phase: BluetoothRoutePhase = BluetoothRoutePhase.IDLE,
    val message: String = "Bluetooth routing idle",
)

/** Platform routing is kept outside the recorder. A successful request is NOT audio readiness. */
@Suppress("DEPRECATION")
internal class BluetoothCaptureController(
    private val context: Context,
    private val diagnostics: CaptureDiagnostics,
    private val onStateChanged: () -> Unit,
) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(BluetoothRouteState())
    val state = mutable.asStateFlow()
    private var route = BluetoothCaptureRoute.STANDARD_SCO
    private var mode = BluetoothAudioMode.NORMAL
    private var targetId: Int? = null
    private var targetAddress: String? = null
    private var targetDeviceType: Int? = null
    private var previousMode: Int? = null
    private var generation = 0
    private var receiverRegistered = false
    private var standardScoStarted = false
    private var modernRequested = false
    private var profile: BluetoothHeadset? = null
    private var headset: BluetoothDevice? = null
    private var voiceRequest: Boolean? = null
    private var hfpInputRouting = false
    private var deviceName: String? = null
    private var modernListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var timeout: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> if (standardScoStarted) {
                    when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> publish(BluetoothRoutePhase.READY, "SCO audio CONNECTED")
                        AudioManager.SCO_AUDIO_STATE_CONNECTING -> publish(BluetoothRoutePhase.CONNECTING, "SCO audio CONNECTING")
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> if (mutable.value.phase == BluetoothRoutePhase.READY) fail("SCO audio disconnected")
                        AudioManager.SCO_AUDIO_STATE_ERROR -> fail("SCO audio error")
                    }
                }
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> if (route == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION && headset != null) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != headset) return
                    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                        BluetoothHeadset.STATE_AUDIO_CONNECTED -> hfpConnected()
                        BluetoothHeadset.STATE_AUDIO_CONNECTING -> publish(BluetoothRoutePhase.CONNECTING, "HFP audio CONNECTING")
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> if (mutable.value.phase == BluetoothRoutePhase.READY) fail("HFP audio disconnected")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // CONNECT is checked before using the profile/device APIs.
    fun start(requestedRoute: BluetoothCaptureRoute, requestedMode: BluetoothAudioMode, device: AudioDeviceInfo?) {
        if (route == requestedRoute && mode == requestedMode && targetId == device?.id &&
            mutable.value.phase in listOf(BluetoothRoutePhase.CONNECTING, BluetoothRoutePhase.READY)) return
        stop()
        route = requestedRoute
        mode = requestedMode
        targetId = device?.id
        targetAddress = if (Build.VERSION.SDK_INT >= 28) device?.address else null
        targetDeviceType = device?.type
        deviceName = device?.productName?.toString()
        voiceRequest = null
        val attempt = generation
        publish(BluetoothRoutePhase.CONNECTING, "Requesting route")
        try {
            if (route == BluetoothCaptureRoute.COMMUNICATION_DEVICE && Build.VERSION.SDK_INT < 31) {
                fail("Communication-device routing requires Android 12 or newer; no route substitution")
                return
            }
            if (route == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION && Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                fail("BLUETOOTH_CONNECT permission required; grant it in experimental settings")
                return
            }
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).apply {
                addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            }
            // Bluetooth broadcasts originate in the privileged Bluetooth process, not this app.
            val initialIntent = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
            previousMode = manager.mode
            // The selected mode is independent of the route and Android version.
            manager.mode = mode.value
            timeout = Runnable { if (attempt == generation && mutable.value.phase == BluetoothRoutePhase.CONNECTING) fail("Timed out waiting for connected Bluetooth audio; no fallback") }
                .also { handler.postDelayed(it, ROUTE_TIMEOUT_MS) }
            if (route == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION) {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                if (adapter == null || !adapter.isEnabled) { fail("Bluetooth adapter unavailable or disabled"); return }
                val accepted = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                        if (attempt != generation) { adapter.closeProfileProxy(profileId, proxy); return }
                        val hfp = proxy as? BluetoothHeadset
                        if (hfp == null) { adapter.closeProfileProxy(profileId, proxy); fail("HFP profile unavailable"); return }
                        profile = hfp
                        try {
                            val devices = hfp.connectedDevices
                            // AudioDeviceInfo.address is only available from API 28. With multiple
                            // headsets, never pretend a different HFP device is the selected input.
                            val address = if (Build.VERSION.SDK_INT >= 28) device?.address else null
                            val selected = if (address.isNullOrBlank()) devices.singleOrNull()
                                else devices.firstOrNull { it.address == address }
                            if (selected == null && !address.isNullOrBlank() && devices.isNotEmpty()) {
                                fail("Selected device has no connected HFP profile; no device fallback")
                                return
                            }
                            if (selected == null) { fail(if (devices.isEmpty()) "No connected HFP headset" else "Multiple HFP headsets; disconnect the others to select unambiguously"); return }
                            headset = selected
                            targetAddress = selected.address
                            deviceName = selected.name ?: deviceName
                            voiceRequest = hfp.startVoiceRecognition(selected)
                            if (voiceRequest != true) { fail("startVoiceRecognition returned false; no fallback"); return }
                            if (hfp.isAudioConnected(selected)) hfpConnected()
                            else publish(BluetoothRoutePhase.CONNECTING, "HFP start accepted; waiting for audio")
                        } catch (e: Exception) { fail("HFP request failed: ${e.message}") }
                    }
                    override fun onServiceDisconnected(profileId: Int) {
                        if (attempt == generation) fail("HFP profile disconnected")
                    }
                }, BluetoothProfile.HEADSET)
                if (!accepted) fail("HFP profile proxy request rejected")
            } else if (route == BluetoothCaptureRoute.COMMUNICATION_DEVICE && Build.VERSION.SDK_INT >= 31) {
                if (device == null) { fail("No Bluetooth communication device"); return }
                modernListener = AudioManager.OnCommunicationDeviceChangedListener { current ->
                    if (attempt == generation) {
                        if (current?.id == targetId) publish(BluetoothRoutePhase.READY, "Communication device selected via setCommunicationDevice")
                        else if (mutable.value.phase == BluetoothRoutePhase.READY) fail("Communication device changed away from Bluetooth")
                    }
                }.also { manager.addOnCommunicationDeviceChangedListener(ContextCompat.getMainExecutor(context), it) }
                modernRequested = true
                if (!manager.setCommunicationDevice(device)) { fail("setCommunicationDevice returned false"); return }
                if (manager.communicationDevice?.id == device.id) publish(BluetoothRoutePhase.READY, "Communication device selected via setCommunicationDevice")
            } else {
                if (device == null) { fail("No connected Bluetooth microphone"); return }
                standardScoStarted = true
                manager.startBluetoothSco()
                manager.isBluetoothScoOn = true
                publish(BluetoothRoutePhase.CONNECTING, "SCO requested; waiting for audio (legacy device selection is system-managed)")
                // An existing SCO link may not emit another CONNECTED event. Retain our
                // own request first, then use the snapshot without bypassing mode validation.
                if (initialIntent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED &&
                    initialIntent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    receiver.onReceive(context, initialIntent)
                }
            }
        } catch (e: Exception) { fail("${e.javaClass.simpleName}: ${e.message}") }
    }

    suspend fun awaitReady(): Boolean {
        val result = withTimeoutOrNull(ROUTE_TIMEOUT_MS + 500) {
            state.first { it.phase != BluetoothRoutePhase.CONNECTING }
        }
        if (result == null) fail("Timed out waiting for Bluetooth audio; no fallback")
        return result?.phase == BluetoothRoutePhase.READY
    }

    private fun hfpConnected() {
        try {
            // Opening the HFP link and routing Android's input to it are distinct operations.
            // This does not call startBluetoothSco(), which would change the experiment route.
            hfpInputRouting = true
            manager.isBluetoothScoOn = true
            publish(BluetoothRoutePhase.READY, "HFP audio CONNECTED; SCO input routing requested")
        } catch (e: Exception) { fail("HFP input routing rejected: ${e.message}") }
    }

    fun inputDisconnected() {
        if (mutable.value.phase == BluetoothRoutePhase.READY) fail("Bluetooth input device disconnected; no fallback")
    }

    /** Resolve the input port, not the output port returned by availableCommunicationDevices. */
    fun inputSelection(): BluetoothInputSelection {
        check(mutable.value.phase == BluetoothRoutePhase.READY) { "Bluetooth link is not ready for capture." }
        val type = if (route == BluetoothCaptureRoute.COMMUNICATION_DEVICE) targetDeviceType
            else AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource && it.type == type }
        val address = targetAddress?.takeIf { it.isNotBlank() }
        val byId = inputs.firstOrNull { it.id == targetId &&
            (address == null || Build.VERSION.SDK_INT < 28 || it.address.isBlank() || it.address.equals(address, true)) }
        val byAddress = if (address != null && Build.VERSION.SDK_INT >= 28)
            inputs.filter { it.address.equals(address, true) }.singleOrNull() else null
        val input = byId ?: byAddress
        checkNotNull(input) { "Android did not expose an unambiguous input port for selected Bluetooth device ${deviceName ?: "unknown"}. No microphone substitution." }
        return BluetoothInputSelection(input, mode)
    }

    private fun publish(phase: BluetoothRoutePhase, detail: String) {
        val actualMode = runCatching { manager.mode }.getOrNull()
        if (phase == BluetoothRoutePhase.READY && actualMode != mode.value) {
            fail("Android did not apply requested mode $mode (${mode.value}); observed mode=${actualMode ?: "unknown"}")
            return
        }
        if (phase != BluetoothRoutePhase.CONNECTING) timeout?.let { handler.removeCallbacks(it) }
        val message = "Route: $route\nRequested mode: $mode; actual mode: ${actualMode ?: "unknown"}\n" +
            "Device: ${deviceName ?: "none"}\nHFP startVoiceRecognition: ${voiceRequest ?: "not attempted"}\n$phase: $detail"
        mutable.value = BluetoothRouteState(phase, message)
        diagnostics.route(message)
        onStateChanged()
    }

    private fun fail(message: String) {
        // Cleanup first but preserve the attempted route and its failure as tester evidence.
        cleanup()
        publish(BluetoothRoutePhase.FAILED, message)
    }

    fun stop() {
        val previous = mutable.value
        cleanup()
        if (previous.phase == BluetoothRoutePhase.FAILED || previous.phase == BluetoothRoutePhase.IDLE) return
        mutable.value = BluetoothRouteState(BluetoothRoutePhase.IDLE, previous.message + "\nRoute released; audio mode restored")
        diagnostics.route(mutable.value.message)
        onStateChanged()
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        generation++ // Invalidates profile callbacks arriving after cancel/failure.
        timeout?.let { handler.removeCallbacks(it) }; timeout = null
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }.onFailure { Timber.w(it, "Unregistering Bluetooth receiver") }
            receiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= 31) {
            modernListener?.let { listener -> runCatching { manager.removeOnCommunicationDeviceChangedListener(listener) } }
            modernListener = null
            if (modernRequested) runCatching { manager.clearCommunicationDevice() }.onFailure { Timber.w(it) }
        }
        if (standardScoStarted) {
            runCatching { manager.stopBluetoothSco() }.onFailure { Timber.w(it) }
            runCatching { manager.isBluetoothScoOn = false }.onFailure { Timber.w(it) }
        }
        if (hfpInputRouting) runCatching { manager.isBluetoothScoOn = false }.onFailure { Timber.w(it) }
        val hfp = profile
        val device = headset
        if (hfp != null) {
            if (device != null && voiceRequest == true) runCatching { hfp.stopVoiceRecognition(device) }.onFailure { Timber.w(it) }
            runCatching { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.closeProfileProxy(BluetoothProfile.HEADSET, hfp) }
                .onFailure { Timber.w(it) }
        }
        profile = null; headset = null; standardScoStarted = false; modernRequested = false; hfpInputRouting = false
        previousMode?.let { original -> runCatching { manager.mode = original }.onFailure { Timber.w(it) } }
        previousMode = null
    }

    companion object { private const val ROUTE_TIMEOUT_MS = 8_000L }
}
