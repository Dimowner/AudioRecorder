# Floating Recorder Overlay Button Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the simple floating recorder circle with an app-icon bubble and add a 3-second rainbow-to-grey save animation.

**Architecture:** Keep the implementation in the existing native Android overlay service. Add a small pure Kotlin color helper for deterministic JVM tests, then use `GradientDrawable`, `ImageView`, `AnimatorSet`, and `ValueAnimator` from `FloatingRecorderOverlayService`.

**Tech Stack:** Kotlin, Android Views, `GradientDrawable`, `ValueAnimator`, JUnit/Robolectric unit tests, Gradle debugConfig flavor.

---

## File Structure

- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometry.kt`
  - Responsibility: pure overlay geometry/color helper functions that do not depend on Android service state.
- Modify: `app/src/test/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometryTest.kt`
  - Responsibility: JVM tests for pure helpers, including save animation color milestones.
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`
  - Responsibility: actual overlay view construction, state updates, and animations.

### Task 1: Save Animation Color Helper

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometry.kt`
- Test: `app/src/test/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometryTest.kt`

- [ ] **Step 1: Write failing tests**

Add tests that prove `calculateSaveFeedbackColor(progress, idleColor)` starts vivid red, reaches a visible desaturated non-red rainbow color mid-animation, and ends exactly at idle grey.

```kotlin
@Test
fun `calculateSaveFeedbackColor starts with vivid red`() {
    val color = calculateSaveFeedbackColor(progress = 0f, idleColor = 0xFF444444.toInt())

    assertEquals(0xFFFF0000.toInt(), color)
}

@Test
fun `calculateSaveFeedbackColor produces desaturated rainbow color mid animation`() {
    val color = calculateSaveFeedbackColor(progress = 0.5f, idleColor = 0xFF444444.toInt())

    assertEquals(0xFF80FFFF.toInt(), color)
}

@Test
fun `calculateSaveFeedbackColor ends at idle color`() {
    val color = calculateSaveFeedbackColor(progress = 1f, idleColor = 0xFF444444.toInt())

    assertEquals(0xFF444444.toInt(), color)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugConfigDebugUnitTest --tests "com.dimowner.audiorecorder.v2.app.overlay.FloatingRecorderOverlayGeometryTest"`

Expected: compile failure with unresolved reference `calculateSaveFeedbackColor`.

- [ ] **Step 3: Implement the helper**

Add a pure function that clamps progress to `0f..1f`, maps hue from red around the color wheel, progressively desaturates, and blends the final part to the idle grey so the final frame is exact.

```kotlin
internal fun calculateSaveFeedbackColor(progress: Float, idleColor: Int): Int {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress >= 1f) return idleColor

    val hue = 360f * clampedProgress
    val saturation = (1f - clampedProgress).coerceIn(0f, 1f)
    val value = 1f
    val hsvColor = Color.HSVToColor(floatArrayOf(hue, saturation, value))
    val settleProgress = ((clampedProgress - 0.85f) / 0.15f).coerceIn(0f, 1f)
    return blendArgb(hsvColor, idleColor, settleProgress)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugConfigDebugUnitTest --tests "com.dimowner.audiorecorder.v2.app.overlay.FloatingRecorderOverlayGeometryTest"`

Expected: tests pass.

### Task 2: Bubble Icon and Save Animation

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`
- Test: `app/src/test/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometryTest.kt`

- [ ] **Step 1: Replace the simple vector circle content**

In `addIconOverlay()`, use a `FrameLayout` bubble with `roundedDrawable(fillColor, radius, strokeColor, strokeWidth)` and an `ImageView` set to `R.mipmap.audio_recorder_logo`.

- [ ] **Step 2: Preserve idle and recording state colors**

Update `updateIconAppearance(recording)` so it changes only the bubble background fill color while keeping the white stroke and app icon stable.

- [ ] **Step 3: Replace save feedback animation**

Update `runSavedAnimation()` so it starts the current scale pulse and a 3-second `ValueAnimator.ofFloat(0f, 1f)` together. On each color frame, call `calculateSaveFeedbackColor(progress, IDLE_ICON_COLOR)` and update the bubble drawable. On animation end, force idle grey.

- [ ] **Step 4: Run targeted tests and build**

Run: `./gradlew testDebugConfigDebugUnitTest --tests "com.dimowner.audiorecorder.v2.app.overlay.FloatingRecorderOverlayGeometryTest"`

Expected: tests pass.

Run: `./gradlew assembleDebugConfigDebug`

Expected: build succeeds and writes `app/build/outputs/apk/debugConfig/debug/app-debugConfig-debug.apk`.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometry.kt app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt app/src/test/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometryTest.kt docs/superpowers/plans/2026-06-18-floating-recorder-overlay-button-visuals.md
git commit -m "feat: improve floating recorder button feedback"
```

## Self-Review

- Spec coverage: covers app icon, white contour, grey idle, red recording, simultaneous scale and 3-second rainbow/desaturation save feedback, and no recording behavior changes.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: all new helpers are in the overlay package and called from the existing service.
