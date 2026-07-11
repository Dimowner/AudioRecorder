# Floating Recorder Overlay Disc Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the square app icon inside the floating recorder overlay with a small red disc with a white contour, while keeping the continuous outer-bubble save animation.

**Architecture:** Keep the change localized to `FloatingRecorderOverlayService`. The outer bubble remains the animated state surface; the inner disc is a static child view that does not participate in the RGB save animation.

**Tech Stack:** Kotlin, Android Views, `GradientDrawable`, existing Gradle debugConfig flavor.

---

## File Structure

- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`
  - Remove the launcher-icon `ImageView` from the overlay button.
  - Add a centered static red disc view with white stroke.
  - Remove the white stroke from the outer bubble drawable.
- Modify: `docs/superpowers/specs/2026-06-18-floating-recorder-overlay-button-visual-design.md`
  - Keep documentation aligned with the approved mockup.
- Keep: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayGeometry.kt`
  - The existing continuous save color helper remains valid.

### Task 1: Implement Disc-Only Button

**Files:**
- Modify: `app/src/main/java/com/dimowner/audiorecorder/v2/app/overlay/FloatingRecorderOverlayService.kt`
- Modify: `docs/superpowers/specs/2026-06-18-floating-recorder-overlay-button-visual-design.md`

- [ ] **Step 1: Update overlay button child view**

In `addIconOverlay()`, replace the `ImageView` with a plain centered `View` using `recordDiscDrawable()` and a 30dp square layout. The 30dp view includes the white stroke, leaving a visually smaller red center.

- [ ] **Step 2: Update outer bubble drawable**

Change `iconBubbleDrawable(color)` so it only fills the circular outer bubble and no longer calls `setStroke(...)`.

- [ ] **Step 3: Add red disc drawable helper**

Add `recordDiscDrawable()` returning an oval `GradientDrawable` with red fill and 4dp white stroke.

- [ ] **Step 4: Verify**

Run: `./gradlew testDebugConfigDebugUnitTest --tests "com.dimowner.audiorecorder.v2.app.overlay.FloatingRecorderOverlayGeometryTest"`

Expected: pass.

Run: `./gradlew assembleDebugConfigDebug`

Expected: build succeeds and writes `app/build/outputs/apk/debugConfig/debug/app-debugConfig-debug.apk`.

## Self-Review

- Spec coverage: implements the approved no-app-icon, no-outer-contour, smaller red-disc mockup while preserving save animation behavior.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: only service drawable helpers are changed; no new public API is introduced.
