# AGENTS.md

This file provides guidance to AI coding agents working with this repository.

## Build Commands

```bash
# Build
./gradlew build

# Unit tests (debug config flavor)
./gradlew testDebugConfigDebugUnitTest

# Instrumented tests (requires connected device/emulator)
./gradlew connectedDebugConfigDebugAndroidTest

# Coverage reports
./gradlew jacocoTestReport          # Unit test coverage → app/build/reports/jacoco/html/
./gradlew jacocoFullReport          # Combined coverage → app/build/reports/jacoco/htmlFull/
./gradlew jacocoTestCoverageVerification  # Enforce 5% line coverage minimum
```

**Build flavors:** `debugConfig` (debug, signed) and `releaseConfig` (minified/shrunk). Most tasks require the flavor in the task name (e.g., `testDebugConfigDebugUnitTest`).

## Architecture Overview

### Dual Codebase (V1 → V2 Migration in Progress)

The app contains two parallel implementations:
- **V2** (`app/src/main/java/.../v2/`) — active development target: Kotlin, Jetpack Compose, Hilt, Room, MVVM + Clean Architecture
- **V1** (`app/src/main/java/.../` root level) — legacy: Java, Views, manual DI via `Injector.java`, MVP pattern

The user can toggle between them via settings. `HomeActivity` is the V2 entry point; `MainActivity` is the V1 entry point.

**All new development should target the `v2/` package.**

### V2 Package Structure

```
v2/
├── app/          # UI layer — one subfolder per screen (home, records, settings, deleted, lostrecords, info, welcome)
│   └── components/   # Reusable Compose components
├── audio/        # Recording and playback logic + foreground services
├── data/         # Data layer — Room DB, data sources, file I/O, prefs
│   ├── room/     # AppDatabase, DAOs, entities
│   ├── model/    # Domain models (Record, SortOrder, etc.)
│   └── Mappers.kt
├── navigation/   # RecorderNavigationGraph.kt + Routes.kt
├── di/           # Hilt modules (AppModule, DatabaseModule, DataSourceModule) + qualifiers
└── theme/        # Compose theme (dark/dynamic color)
```

### Core Patterns

- **Language/UI:** Kotlin, Jetpack Compose, Material3
- **DI:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- **Database:** Room
- **Async:** Coroutines with `@IoDispatcher` / `@MainDispatcher` qualifiers
- **Navigation:** Navigation Compose
- **State management:** ViewModels expose immutable state data classes via `mutableStateOf()`. One-shot events use `SharedFlow`.

### Service-Based Audio Architecture

Recording and playback run as **foreground services** (`AudioRecordingService`, `AudioPlaybackService`). ViewModels bind via `ServiceConnection` and receive state via `StateFlow`/`SharedFlow`.

### Entry Points

- `HomeActivity` — V2 entry point
- `MainActivity` — V1 legacy entry point

### Key Non-Obvious Behaviors

- **Broken record recovery:** `BrokenRecordRestorer.kt` detects/repairs WAV/M4A/3GP files interrupted by crash or reboot.
- **Waveform data:** See the dedicated [Waveform Visualization](#waveform-visualization) section below.
- **Bluetooth mic:** `AudioManagerHelper` monitors Bluetooth state reactively; `HomeViewModel` manages source selection.
- **Preferences:** `PrefsV2` wraps SharedPreferences with Flow-based reactive updates. `Prefs` and `PrefsV2` share the same SharedPreferences file.

## Waveform Visualization

The waveform UI has two distinct lifecycles: a **live** waveform that scrolls during active recording, and a **static** waveform rendered from a persisted amplitude array for finished records. Both V1 and V2 follow the same conceptual pipeline but with different recorder/UI stacks. The post-recording decoder is **shared** between V1 and V2.

### Live waveform (during recording)

The live waveform deliberately keeps in memory **only the slice currently visible on screen**. Older amplitudes that have scrolled off the left edge are dropped — they are not part of any persistent buffer. The full waveform of the finished file is reconstructed afterwards by decoding the audio (see next section).

**V2 — `v2/audio/`**

- `MediaRecorderBase.kt` polls `mediaRecorder.getMaxAmplitude()` from a scheduled `recordingTimeUpdateRunnable`, accumulating samples into `IntArrayList amplitudesBuffer`. The timer emits `RecorderEvent.OnRecordingProgress(durationMills, amplitude)` every `AppConstants.RECORDING_VISUALIZATION_INTERVAL_NEW` (~10ms).
- `AudioRecordingService.kt` keeps a fixed-size sliding window in `LinkedList<Int> recordingAmplitudes`. The size cap is computed from screen width by `calculateRecordingAmplitudeBufferSize()` (`AudioRecordingService.kt:781`) so the buffer holds exactly the half-screen-worth of samples that are visible on the right side of the scrubber. On every new sample, `handleRecordingProgress()` appends to the tail; once the cap is exceeded it calls `recordingAmplitudes.removeFirst()` — this is the "delete what's no longer visible" behavior. Samples are also scaled by ~1.2× for visual amplification.
- The service publishes `RecordingServiceState.amplitudes: IntArray` plus `totalSampleCount` / `waveformDataOffset` (absolute timeline position) via `StateFlow`. `HomeViewModel` collects this and feeds the Compose state.
- Rendering: `v2/app/components/WaveformComposeView.kt` uses `Canvas.drawLines()` to draw the visible amplitudes around a centered scrubber. Grid spacing comes from `AppConstantsV2.RECORDING_GRID_STEP` (2000ms). Layout math: `pxPerMill = screenWidth × DEFAULT_WIDTH_SCALE / SHORT_RECORD`.

**V1 — root-level package**

- `AudioRecorder.java` schedules `recorder.getMaxAmplitude()` polls every `AppConstants.RECORDING_VISUALIZATION_INTERVAL` (13ms) and invokes `onRecordProgress(durationMills, amplitude)` on its callback.
- `MainActivity` forwards each tick to `RecordingWaveformView.addRecordAmp(amp, mills)` (`app/widget/RecordingWaveformView.kt:96`). The view owns the sliding window directly: `MutableList<Int> recordingData` is trimmed via `if (recordingData.size > pxToSample(viewWidthPx / 2)) recordingData.removeAt(0)`. Same principle as V2 — the buffer length is tied to the visible viewport in pixels.
- Rendering is canvas-based (`onDraw` → `drawGrid` + `drawRecordingWaveform`), accumulating segments into `FloatArray drawLinesArray`.

### Static waveform (after recording stops)

When recording finishes the **entire** audio file is decoded once to extract a downsampled amplitude array, which is persisted alongside the record. The UI then reads that array to draw the full-duration waveform of any saved recording.

**Decoding pipeline (shared by V1 and V2):**

1. `audio/AudioWaveformVisualization.kt` is a thin Kotlin wrapper around `audio/AudioDecoder.java`.
2. `AudioDecoder` uses Android's `MediaExtractor` + `MediaCodec` to stream PCM frames from the recorded file (works for WAV, M4A, 3GP, etc.). For each frame it reads 16-bit shorts, takes the per-frame max across channels, applies a sqrt gain curve, and appends one integer to `IntArrayList gains`. Frame size is derived from `sampleRate / dpPerSec` (`calculateSamplesPerFrame()`), so the output is **already a UI-sized, simplified amplitude array** — not the raw PCM.
3. `DecodeService` orchestrates the work as a foreground service. Decoding is **skipped for very long recordings** (longer than `AppConstants.DECODE_DURATION` = 2 hours, see `DecodeService.kt:251`) — those records never get a persisted `amps` array and fall back to a non-waveform UI.

**V2 storage and display:**

- `DecodeService.startNotificationV2()` is invoked from `AudioRecordingService` when recording stops. After decode, it writes the amplitude array into the Room entity via `recordsDataSource.updateRecord(record.copy(amps = data, isWaveformProcessed = true))`. The amplitudes live on `v2/data/room/Record.amps: IntArray`.
- For display, `v2/app/info/widget/WaveformWidget.kt` (and `WaveformStaticWidget`) reads `Record.amps` and resamples it to the canvas width: `samplePerPx = durationSample / canvasWidth`, then for each pixel picks `amps[(index * samplePerPx).toInt()]` and normalizes against `AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE` (32767f).

**V1 storage and display:**

- `DecodeService` updates the legacy SQLite-backed `LocalRepository`. The V1 entity `Record.java` has its own `int[] amps` field that mirrors the V2 schema conceptually (the V1→V2 Room migration carries this field across — see `041131f` and surrounding migration commits).
- For display, `app/widget/WaveformViewNew.kt` (the newer V1 view) and the older `WaveformView.java` consume the stored `amps` via `setWaveform(frameGains)`. They adjust `pxPerSecond` based on duration (`ARApplication.getDpPerSecond`) and draw one line per sample. `SimpleWaveformView.java` is a stripped-down variant used in list rows.

### Short-record width scaling

The waveform's total drawn width is **not constant** — it depends on the record's duration relative to a "short record" threshold. The intent: a long record stretches across a fixed number of screens (`DEFAULT_WIDTH_SCALE`, currently `1.5` — i.e., the waveform spans 1.5 screen widths and the user scrolls/scrubs through it), while a short record uses a **proportionally smaller width** so its samples don't get visually stretched. This applies to both the live recording view (as duration grows) and the static post-recording view.

**Constants:**
- `AppConstantsV2.SHORT_RECORD = 18000L` (18s, V2) and `WaveformViewNew.SHORT_RECORD = 18000` (V1 newer view) — duration threshold above which the waveform reaches its maximum width.
- `AppConstantsV2.DEFAULT_WIDTH_SCALE = 1.5f` — describes how many screen widths the full waveform takes when the record is long enough. `1.0` would mean exactly one screen width; `1.5` means 1.5 screens.
- `AppConstants.LONG_RECORD_THRESHOLD_SECONDS = 20` and `AppConstants.SHORT_RECORD_DP_PER_SECOND = 25` — the older V1 equivalents used by `ARApplication.getDpPerSecond()` and the legacy `WaveformView.java`.

**The rule (V2 + V1 `WaveformViewNew`):**

```
widthScale = if (durationMills >= SHORT_RECORD) DEFAULT_WIDTH_SCALE
             else durationMills * (DEFAULT_WIDTH_SCALE / SHORT_RECORD)
```

So a 9s record renders at `widthScale = 0.75` (about three-quarters of one screen); an 18s+ record renders at the full `1.5` screens. **Short records are not scaled down to `DEFAULT_WIDTH_SCALE`** — they sit at a smaller scale that grows linearly with duration. This keeps short recordings dense and readable instead of being smeared across 1.5 screens of mostly empty waveform.

- V2 live recording: computed every tick in `AudioRecordingService.handleRecordingProgress()` (`AudioRecordingService.kt:302`) and published as `RecordingServiceState.widthScale`. `WaveformComposeView` uses it for layout math.
- V1 newer view: `WaveformViewNew.calculateScale()` (`WaveformViewNew.kt:234`) implements the same branch.
- V1 legacy view (`WaveformView.java`): uses an older but conceptually equivalent rule via `ARApplication.getDpPerSecond(durationSec)` (`ARApplication.kt:234`). For `durationSec > LONG_RECORD_THRESHOLD_SECONDS` (20s), it computes `dpPerSec = WAVEFORM_WIDTH × screenWidthDp / durationSec` so the whole record fits 1.5 screens; otherwise it uses the fixed `SHORT_RECORD_DP_PER_SECOND` (25 dp/s), which gives short records a natural, un-stretched density. The threshold (20s) and short-record formula differ from the newer code (18s, linear interpolation), but the intent is identical.

When working on waveform layout, **never hardcode width assumptions** — always derive from `widthScale` / `dpPerSec` so short and long records behave correctly.

### Practical notes

- The live recording buffer and the persisted `amps` array are **independent**. Live samples are sized for the viewport (~hundreds of ints); persisted `amps` is sized to dpPerSec across the whole file (typically a few hundred to a few thousand ints regardless of audio length).
- Buffer-sizing math in `AudioRecordingService.calculateRecordingAmplitudeBufferSize()` mirrors the rendering math in `WaveformComposeView`. If you change one, change both.
- `AudioWaveformVisualization` and `AudioDecoder` are **shared** legacy code that V2 still depends on — they live at the root level, not under `v2/`. Treat them as part of the V2 contract until a V2-native replacement exists.
- V1 has two parallel short-record thresholds: `LONG_RECORD_THRESHOLD_SECONDS = 20` (used by the old `WaveformView.java` + `ARApplication.getDpPerSecond`) and the newer `WaveformViewNew.SHORT_RECORD = 18000` (matches V2). Make sure you're touching the right one for the view you're modifying.

## Testing

- Unit tests: `app/src/test/` — use **MockK** (not Mockito) and **Robolectric**
- Instrumented tests: `app/src/androidTest/`
- JaCoCo excludes generated code, Hilt classes, Activities, and Compose components from coverage

## Key Dependencies

| Purpose | Library |
|---|---|
| DI | Hilt 2.59 |
| UI | Jetpack Compose BOM 2026.02, Material3 |
| Navigation | Navigation Compose 2.9 |
| Database | Room 2.8 |
| Playback | android.media.MediaPlayer |
| Audio metadata | jaudiotagger 3.0, mp4parser 1.9 |
| Async | Coroutines 1.10 |
| Logging | Timber 5.0 |
| Test mocking | MockK 1.14 |

