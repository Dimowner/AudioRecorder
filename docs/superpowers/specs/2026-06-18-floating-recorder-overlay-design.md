# Floating Recorder Overlay Design

## Goal

Add an optional, default-off floating recorder control for the V2 Android app so a user can start and stop recordings while another app, such as Waze, remains visible. The overlay must be minimalist, draggable, and must reuse the existing V2 recording pipeline so recording names, file creation, metadata, waveform processing, notifications, and saving behavior stay consistent with the main app.

## Current Context

- The active implementation target is V2 under `app/src/main/java/com/dimowner/audiorecorder/v2/`.
- `AudioRecordingService` already owns V2 recording start/stop, generated names via `NameFormat.getNewRecordName(prefs)`, file/database creation, save finalization, foreground recording notification, max-duration splitting, and decode handoff.
- `SettingsScreen`, `SettingsState`, `SettingsViewModel`, and `PrefsV2` are the canonical V2 settings path.
- The app does not currently declare `android.permission.SYSTEM_ALERT_WINDOW`.
- The project targets SDK 37, so the design must assume modern Android background execution and foreground-service restrictions.

## User-Approved Behavior

- Add a setting named conceptually as `Floating recorder button`; the default is off.
- Enabling the setting checks overlay permission and microphone permission.
- If overlay permission is missing, show a dialog explaining that Android requires display-over-other-apps permission, with a direct button to this app's overlay permission page.
- The setting remains off until the needed permission is granted.
- The overlay starts only after the app is opened; it must not auto-start after reboot.
- The overlay uses a persistent idle foreground-service notification for reliability.
- A tap while idle starts a new recording.
- A tap while recording stops and saves the recording.
- A long press/drag moves the icon; the final position persists across service restarts.
- The icon changes color only when recording state confirms the recording actually started.
- Stopping a recording shows a small success animation on the icon, not a toast/snackbar.
- If `askToRenameAfterRecordingStopped` is disabled, stopping from the overlay saves silently.
- If `askToRenameAfterRecordingStopped` is enabled, stopping from the overlay shows a compact translucent rename overlay over the current app. The panel has no timeout and remains until the user taps save/cancel.

## Architecture

Create a dedicated `FloatingRecorderOverlayService` in the V2 audio package. This service owns the overlay window and idle foreground notification. It delegates actual recording to the existing `AudioRecordingService` instead of duplicating recording logic.

The overlay service should bind to `AudioRecordingService` to observe `RecordingServiceState` and `AudioRecordingServiceEvent`. It should also call `AudioRecordingService.startServiceForeground(context)` to start recording and call the bound service's `stopRecording()` to stop. If the recording service is not bound when the user taps stop, the overlay service should bind and defer the stop request until the service connection is available, rather than creating a parallel stop path.

The overlay service should be started from settings only when the user has enabled the preference and `Settings.canDrawOverlays(context)` is true. The app's normal startup path should also start the overlay service once after opening the app if the preference is enabled and permission is still granted. If permission was revoked, the service should not start and the setting can be reconciled to off.

## Overlay UI

Use `WindowManager` with `TYPE_APPLICATION_OVERLAY` and `FLAG_NOT_FOCUSABLE` for the idle icon so the underlying app remains interactive. The icon should be a small circular view using existing vector assets where practical:

- Idle: neutral surface/background with recorder glyph.
- Recording: red or high-emphasis recording color after confirmed service state.
- Saving/saved: brief scale/check animation on the icon.

Drag handling should use touch slop to distinguish taps from moves. During drag, update `WindowManager.LayoutParams.x` and `y`; persist the final coordinates to `PrefsV2` on release. Clamp saved/restored coordinates to the current display bounds so the icon cannot be restored off-screen after rotation or screen-size changes.

When rename is needed, add a second overlay view or replace/expand the icon view with a compact panel. The panel should use focusable overlay flags so the text field can receive input and show the keyboard. Waze or any other current app remains visible behind it. The panel should include the generated saved name, an editable field, `Save`, and `Cancel`/`Keep default` behavior that preserves the already-saved record name.

## Settings And Permissions

Add `PrefsV2.isFloatingRecorderOverlayEnabled` with default `false`. Add persisted icon position fields such as `floatingRecorderOverlayX` and `floatingRecorderOverlayY`, with safe defaults near an edge of the screen.

Add a permission/helper unit that provides:

- `canDrawOverlays(context): Boolean` wrapping `Settings.canDrawOverlays(context)`.
- `overlayPermissionSettingsIntent(context): Intent` using `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` and `Uri.parse("package:${context.packageName}")`.

Settings behavior:

- On switch on, if overlay permission is missing, show the permission explanation dialog and leave the persisted setting off.
- On returning to settings after Android permission UI, re-check permission. If granted and the user was enabling the feature, enable preference and start the overlay service.
- If microphone permission is missing, request it before enabling or starting overlay-driven recording. The overlay cannot request runtime permissions directly because it is not an Activity.
- On switch off, persist off and stop `FloatingRecorderOverlayService`.

Manifest changes:

- Add `android.permission.SYSTEM_ALERT_WINDOW`.
- Register `FloatingRecorderOverlayService` with `android:exported="false"`.
- Declare the idle overlay service as a `specialUse` foreground service with `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` and an `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value describing the always-visible floating recorder control. The overlay service itself does not use the microphone; the microphone foreground-service type remains on `AudioRecordingService`.

## Recording And Rename Data Flow

1. User enables the setting in V2 settings.
2. App verifies permission and starts the overlay foreground service.
3. Overlay service adds the floating icon window.
4. User taps idle icon.
5. Overlay service calls `AudioRecordingService.startServiceForeground(context)`.
6. Overlay service observes `RecordingServiceState.isRecording()` and changes icon to recording color only after state confirms start.
7. User taps recording icon.
8. Overlay service calls `AudioRecordingService.stopRecording()` on the bound service.
9. Existing `AudioRecordingService` finalizes the record, updates Room, sets `prefs.activeRecordId`, emits `RecordingStopped`, and starts decode.
10. Overlay service receives the stop event/state transition and runs the icon success animation.
11. If rename-after-recording is enabled, overlay service loads the active record and displays the rename panel.
12. On save, overlay service calls the existing `RecordsDataSource.renameRecord(record, newName)` path so file and database rename behavior remains consistent.

## Error Handling

- If overlay permission is missing when service starts, the service should stop itself and leave no overlay view attached.
- If recording start fails, `AudioRecordingService` already emits an error event and cleans up invalid records. The overlay should return to idle color and avoid showing success animation.
- If stop/save fails, the overlay should return to idle or recording state based on actual service state and avoid showing success animation.
- If rename fails because the target file exists or is invalid, keep the rename panel open and show a concise inline error in the overlay panel, not a toast.
- If the system removes the overlay service, opening the app again should restart it when the setting is enabled and permission is granted.

## Testing Strategy

- Add a Robolectric unit test for `PrefsV2Impl` covering `isFloatingRecorderOverlayEnabled`, `floatingRecorderOverlayX`, and `floatingRecorderOverlayY` defaults and persistence.
- Add a unit test for the overlay permission helper covering the package-specific `ACTION_MANAGE_OVERLAY_PERMISSION` intent.
- Add a unit test for a small settings decision helper that returns whether enabling should proceed, request microphone permission, or open overlay permission settings. This keeps the permission decision testable without needing to instrument the Compose screen.
- Run `./gradlew testDebugConfigDebugUnitTest` after implementation.
- Manual or emulator validation should cover enabling permission, persistent notification, overlay over another app, tap-to-start, tap-to-stop, drag/persist position, rename overlay, and disabling the setting.

## Non-Goals

- No boot receiver and no automatic overlay start after device reboot before the user opens the app.
- No duplicate recording implementation in the overlay service.
- No toast notification for successful overlay recording start/stop.
- No forced timeout for the rename overlay.
- No changes to V1/legacy behavior unless required by shared manifest or permission declarations.

## Open Risks

- OEM Android builds can impose extra overlay restrictions or battery-management kills. The persistent foreground notification mitigates this but cannot guarantee behavior on every device.
- Text input from an overlay is more fragile than a normal Activity dialog, especially around keyboard focus. The design keeps the rename panel small and optional because users can disable rename-after-recording for driving.
- Android can suppress overlays on sensitive system screens. The feature is expected to work over navigation apps like Waze, but not necessarily over all system permission/security screens.
