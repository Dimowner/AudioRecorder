package com.dimowner.audiorecorder.v2.app.overlay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Looper
import android.view.WindowManager
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.v2.audio.AudioRecordingService
import com.dimowner.audiorecorder.v2.audio.AudioRecordingServiceEvent
import com.dimowner.audiorecorder.v2.audio.RecordingServiceState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class FloatingRecorderOverlayServiceTest {

    private val dispatcher = StandardTestDispatcher()
    private val component = ComponentName("com.dimowner.audiorecorder", AudioRecordingService::class.java.name)
    private lateinit var overlay: FloatingRecorderOverlayService
    private lateinit var connection: ServiceConnection
    private var destroyed = false

    private class RecordingServiceFixture {
        val state = MutableStateFlow(RecordingServiceState())
        val events = MutableSharedFlow<AudioRecordingServiceEvent?>()
        val service = mockk<AudioRecordingService>()
        val binder = mockk<AudioRecordingService.ServiceBinder>()

        init {
            every { service.recordingState } returns state
            every { service.event } returns events
            every { binder.getService() } returns service
            coEvery { service.stopForDismissal() } returns true
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Build/attach without onCreate: inject only the dependencies used by binding/dismissal,
        // without starting the foreground notification, overlay windows, or Hilt application.
        overlay = Robolectric.buildService(FloatingRecorderOverlayService::class.java).get()
        overlay.ioDispatcher = dispatcher
        overlay.audioPlayer = mockk<PlayerContractNew.Player>(relaxed = true)
        val windowManager = overlay.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ReflectionHelpers.setField(overlay, "windowManager", windowManager)
        ReflectionHelpers.setField(overlay, "dismissTarget", OverlayDismissTarget(overlay, windowManager))
        connection = ReflectionHelpers.getField(overlay, "recordingServiceConnection")
        // Real Android does not deliver onServiceDisconnected for an explicit unbind.
        shadowOf(RuntimeEnvironment.getApplication()).setUnbindServiceCallsOnServiceDisconnected(false)
    }

    @After
    fun tearDown() {
        if (!destroyed) destroyOverlay()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `dismissal after reconnect stops the replacement service and waits for its save`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        val replacement = RecordingServiceFixture()
        val saved = CompletableDeferred<Boolean>()
        coEvery { replacement.service.stopForDismissal() } coAnswers { saved.await() }
        bind(first)
        advanceUntilIdle()

        connection.onServiceDisconnected(component)
        advanceUntilIdle()
        assertEquals(0, first.state.subscriptionCount.value)
        assertEquals(0, first.events.subscriptionCount.value)
        connection.onServiceConnected(component, replacement.binder)
        dismiss()
        advanceUntilIdle()

        coVerify(exactly = 0) { first.service.stopForDismissal() }
        coVerify(exactly = 1) { replacement.service.stopForDismissal() }
        assertFalse(shadowOf(overlay).isStoppedBySelf)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).boundServiceConnections.contains(connection))

        saved.complete(true)
        advanceUntilIdle()
        assertTrue(shadowOf(overlay).isStoppedBySelf)
        destroyOverlay()
        advanceUntilIdle()
        assertReleased()
        assertEquals(0, replacement.state.subscriptionCount.value)
        assertEquals(0, replacement.events.subscriptionCount.value)
    }

    @Test
    fun `dismissal while disconnected waits for the replacement connection`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        val replacement = RecordingServiceFixture()
        bind(first)
        connection.onServiceDisconnected(component)
        dismiss()
        advanceUntilIdle()

        coVerify(exactly = 0) { first.service.stopForDismissal() }
        assertFalse(shadowOf(overlay).isStoppedBySelf)

        connection.onServiceConnected(component, replacement.binder)
        advanceUntilIdle()
        coVerify(exactly = 0) { first.service.stopForDismissal() }
        coVerify(exactly = 1) { replacement.service.stopForDismissal() }
        assertTrue(shadowOf(overlay).isStoppedBySelf)
    }

    @Test
    fun `destruction while disconnected releases the registered binding and cancels dismissal`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        bind(first)
        connection.onServiceDisconnected(component)
        dismiss()
        advanceUntilIdle()
        assertFalse(shadowOf(overlay).isStoppedBySelf)
        destroyOverlay()
        advanceUntilIdle()

        assertReleased()
        coVerify(exactly = 0) { first.service.stopForDismissal() }
        assertFalse(shadowOf(overlay).isStoppedBySelf)
        assertEquals(0, first.state.subscriptionCount.value)
        assertEquals(0, first.events.subscriptionCount.value)
    }

    @Test
    fun `destruction after reconnect releases the binding and cancels replacement collectors`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        val replacement = RecordingServiceFixture()
        bind(first)
        connection.onServiceDisconnected(component)
        connection.onServiceConnected(component, replacement.binder)
        advanceUntilIdle()
        assertEquals(1, replacement.state.subscriptionCount.value)
        assertEquals(1, replacement.events.subscriptionCount.value)

        destroyOverlay()
        advanceUntilIdle()

        assertReleased()
        assertEquals(0, replacement.state.subscriptionCount.value)
        assertEquals(0, replacement.events.subscriptionCount.value)
    }

    @Test
    fun `disconnect before the first callback keeps an existing dismissal waiter`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        val replacement = RecordingServiceFixture()
        bind(first, deliverConnection = false)
        dismiss()
        advanceUntilIdle()
        connection.onServiceDisconnected(component)
        advanceUntilIdle()
        assertFalse(shadowOf(overlay).isStoppedBySelf)

        connection.onServiceConnected(component, replacement.binder)
        advanceUntilIdle()

        coVerify(exactly = 0) { first.service.stopForDismissal() }
        coVerify(exactly = 1) { replacement.service.stopForDismissal() }
        assertTrue(shadowOf(overlay).isStoppedBySelf)
    }

    @Test
    fun `dismissal rechecks the current service when connection changes before its waiter resumes`() = runTest(dispatcher) {
        val first = RecordingServiceFixture()
        val replacement = RecordingServiceFixture()
        bind(first, deliverConnection = false)
        dismiss()
        advanceUntilIdle()

        connection.onServiceConnected(component, first.binder)
        connection.onServiceDisconnected(component)
        connection.onServiceConnected(component, replacement.binder)
        advanceUntilIdle()

        coVerify(exactly = 0) { first.service.stopForDismissal() }
        coVerify(exactly = 1) { replacement.service.stopForDismissal() }
        assertTrue(shadowOf(overlay).isStoppedBySelf)
    }

    private fun bind(fixture: RecordingServiceFixture, deliverConnection: Boolean = true) {
        val application = shadowOf(RuntimeEnvironment.getApplication())
        application.setComponentNameAndServiceForBindService(component, fixture.binder)
        call("bindRecordingService")
        if (deliverConnection) shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dismiss() = call("dismissOverlayAndApp")

    private fun destroyOverlay() {
        overlay.onDestroy()
        destroyed = true
    }

    private fun assertReleased() {
        val application = shadowOf(RuntimeEnvironment.getApplication())
        assertFalse(application.boundServiceConnections.contains(connection))
        assertEquals(1, application.unboundServiceConnections.count { it === connection })
    }

    private fun call(method: String) {
        ReflectionHelpers.callInstanceMethod<Unit>(overlay, method)
    }
}
