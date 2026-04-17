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
- **Waveform data:** Amplitude samples stored in Room (`Record.amps: IntArray`), decoded via `DecodeService`.
- **Bluetooth mic:** `AudioManagerHelper` monitors Bluetooth state reactively; `HomeViewModel` manages source selection.
- **Preferences:** `PrefsV2` wraps SharedPreferences with Flow-based reactive updates. `Prefs` and `PrefsV2` share the same SharedPreferences file.

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

