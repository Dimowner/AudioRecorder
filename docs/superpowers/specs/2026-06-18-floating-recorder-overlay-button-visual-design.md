# Floating Recorder Overlay Button Visual Design

## Goal

Improve the floating recorder overlay button so it is easier to recognize peripherally while driving, without changing the overlay service's recording behavior or touch model.

This spec supersedes the earlier app-icon bubble concept. The square launcher icon looked visually awkward in the small circular overlay and should not be used for the final button.

## Approved Approach

Use the existing native Android `View`/`Drawable` implementation in `FloatingRecorderOverlayService`. This keeps the change small and avoids introducing a custom drawing surface for a button that already exists and already handles drag/tap behavior.

## Static Button States

The overlay button remains the current movable 56dp touch target.

Idle state:

- The button background is a rounded/circular grey bubble.
- The bubble has no contour stroke.
- The centered content is a small red circular disc with its own white contour.

Recording state:

- The same inner red disc and white contour remain visible.
- The outer bubble background changes to red.

The inner red disc and its white contour must remain stable during state changes and animations. Only the outer bubble background color and the already-existing scale pulse animate after saving.

## Save Feedback Animation

When the user taps the overlay while recording and the recording stops/saves, two animations start at the same time:

- A short scale pulse reuses the existing inflate/deflate behavior for immediate tactile feedback.
- A 3-second continuous background color animation cycles through rainbow hues and progressively desaturates until the bubble returns to the idle grey background.

The rainbow/desaturation animation must be long enough to be visible from peripheral vision, but it must not block input, recording service events, or the rename overlay. The final background after the animation is the idle grey state.

## Implementation Boundaries

Keep the change localized to `FloatingRecorderOverlayService` unless a tiny pure helper is useful for testing color interpolation.

Expected units:

- A helper to create/update the outer button bubble drawable with fill color and no stroke.
- A helper to create the inner red disc drawable with a white stroke.
- A helper to compute the save-animation background color from animation progress.
- A save-animation method that starts the existing scale pulse and the new color animator together.

Do not change:

- Overlay permission behavior.
- Overlay drag/tap semantics.
- Recording start/stop behavior.
- Rename overlay behavior.

## Testing

Add or update JVM unit tests for any pure color/progress helper that is introduced. Verify with the targeted overlay tests and a debug build:

- `./gradlew testDebugConfigDebugUnitTest --tests "com.dimowner.audiorecorder.v2.app.overlay.*"`
- `./gradlew assembleDebugConfigDebug`

Manual visual validation is still needed on a device or emulator because the key success criterion is perceptual visibility of the overlay animation.
