# Floating Recorder Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a default-off, permission-gated floating recorder overlay that can start/stop V2 recordings above other apps.

**Architecture:** Add small testable permission/settings helpers, persist overlay preference and position in `PrefsV2`, wire a V2 settings switch and startup hook, then implement a dedicated foreground `FloatingRecorderOverlayService` that owns only the `WindowManager` UI while delegating recording to `AudioRecordingService`.

**Tech Stack:** Kotlin, Android Services, Hilt, SharedPreferences, Jetpack Compose settings UI, Robolectric/MockK unit tests.

---

### Task 1: Preference And Permission Helpers

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/data/PrefsV2.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/data/PrefsV2Impl.kt`
- Create: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayPermission.kt`
- Create: `app/src/test/java/com/dimowner/audiorecorder/v2/data/PrefsV2ImplTest.kt`
- Create: `app/src/test/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayPermissionTest.kt`

- [ ] Step 1: Write failing preference and permission tests.
- [ ] Step 2: Run targeted tests and verify they fail for missing APIs/classes.
- [ ] Step 3: Add `PrefsV2` overlay enabled/position properties and permission helper.
- [ ] Step 4: Re-run targeted tests and verify they pass.

### Task 2: Settings Switch And Permission Dialog

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/settings/SettingsState.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/settings/SettingsComponents.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_floating_record.xml`
- Create: `app/src/test/java/com/dimowner/audiorecorder/v2/app/settings/FloatingRecorderOverlaySettingsDecisionTest.kt`

- [ ] Step 1: Write failing decision-helper tests for proceed/request-microphone/open-overlay-settings.
- [ ] Step 2: Implement the decision helper and settings state/action wiring.
- [ ] Step 3: Add switch UI, permission dialog, overlay settings launcher, and microphone request launcher.
- [ ] Step 4: Re-run targeted tests and compile if needed.

### Task 3: Overlay Service

**Files:**
- Create: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/audio/AudioRecordingService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Step 1: Add minimal service manifest/strings and compile check.
- [ ] Step 2: Implement foreground idle notification and overlay icon window.
- [ ] Step 3: Bind to `AudioRecordingService`, start recording on idle tap, stop on recording tap, and update icon from actual recorder state.
- [ ] Step 4: Persist drag position and clamp restored coordinates.
- [ ] Step 5: Implement success animation and rename overlay panel using `RecordsDataSource.renameRecord`.

### Task 4: Startup And Service Control

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/HomeActivity.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`

- [ ] Step 1: Start overlay service when app opens and preference/permission are both true.
- [ ] Step 2: Start service when settings switch is enabled and stop service when disabled.
- [ ] Step 3: Reconcile revoked overlay permission by disabling preference and not starting service.

### Task 5: Verification

**Files:**
- All touched implementation and test files.

- [ ] Step 1: Run targeted new tests.
- [ ] Step 2: Run `./gradlew testDebugConfigDebugUnitTest`.
- [ ] Step 3: Inspect `git status --short` and summarize changes.
