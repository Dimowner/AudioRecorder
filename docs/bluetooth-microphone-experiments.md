# Bluetooth microphone experiment

Use **V2 → Settings → Enhanced Bluetooth microphone sound**. Enable the Bluetooth microphone
switch on Home before recording. Choose WAV first; direct M4A also supports the experiment.
Settings persist across app restart and apply to subsequent recordings. Stop recording before
changing a dimension.

| Selection | Behavior |
|---|---|
| Disabled | Restores DEFAULT Bluetooth source, STANDARD_SCO, NORMAL, SYSTEM_DEFAULT preprocessing and gain OFF. |
| HFP Clear Voice preset | Applies the user-tested VOICE_RECOGNITION Bluetooth source, HFP_VOICE_RECOGNITION route, NORMAL mode, AGC_ONLY preprocessing and AUTO_LEVEL gain together. |
| Custom | Keeps the current values and directly shows all selectors under **Bluetooth microphone routing and processing**. |

The individual experimental controls are visible only in Custom. Every selector has a
right-hand dropdown arrow. The section heading is plain text, and the setup guidance appears
below it only in Custom mode. There is no additional expand/collapse action.
The existing **Audio source** selector remains in recording settings for phone/other microphone
capture or System Audio. **Bluetooth audio source** is a separate Custom selector and never
offers System Audio. Editing it or another profile value marks the enhancement Custom; editing
the external source does not. Presets never overwrite the external source. System Audio takes
precedence even when the Bluetooth switch is enabled. Diagnostics remain available in all modes.

**Apply only to Bluetooth mic** is a binary switch, ON by default, shown below the enhancement
selector in every mode. It persists independently of presets:

| Setting | Switch ON | Switch OFF |
|---|---|---|
| Bluetooth source and route | Bluetooth microphone only | Bluetooth microphone only |
| Android audio mode, preprocessing, software gain | Bluetooth microphone only | All microphones |
| External Audio source | Other microphones or System Audio | Other microphones or System Audio |
| System-playback capture | No microphone mode/effects/gain overrides | No microphone mode/effects/gain overrides |

On non-Bluetooth recordings with the switch ON, the app leaves audio mode alone, requests
system-default preprocessing and bypasses software gain. With the switch OFF, it owns the selected
Android audio mode for the recording and restores the previous mode on stop/failure, without
requesting Bluetooth routing. Settings are snapshotted before startup; editing is locked through
route preparation, native startup, recording and saving. Diagnostics show selected and effective
processing separately, including overrides skipped by scope.

Upgrading snapshots the old microphone source once into the Bluetooth source while preserving the
external source; System Audio is never copied. The exact HFP combination is recognized as HFP Clear
Voice preset, the standard combination as Disabled, and any other combination as Custom. Selecting Custom
keeps the currently active combination; it does not restore an earlier custom snapshot.
Preset changes do not alter the external source, scope switch, recording format, sample rate, or Bluetooth switch preference.

Fresh installs, Disabled and “Reset recording settings” use NORMAL mode, standard routing,
system-default effects and gain off. Reset also restores both sources to DEFAULT and scope ON.
Legacy Disabled/default combinations migrate to NORMAL; custom combinations retain their stored
mode (including an implicit old IN_COMMUNICATION default). Explicit IN_COMMUNICATION remains
available. The change follows user reports that communication mode selected the phone mic across
multiple headsets while NORMAL selected Bluetooth; it is not an Android-version guarantee.

## Compare continuity first

Select **Custom** to run the matrix below. Keep headset position, speaking volume, sample rate,
channels, and a short spoken passage
constant. Include a few seconds of silence, normal speech, and quieter speech. Name each take
with its test letter. Compare **continuous intelligible speech**, not just loudness.

| Test | Route | Mode | Source | Android preprocessing | Gain |
|---|---|---|---|---|---|
| A | STANDARD_SCO | IN_COMMUNICATION | MIC | SYSTEM_DEFAULT | OFF |
| B | STANDARD_SCO | IN_COMMUNICATION | VOICE_RECOGNITION | SYSTEM_DEFAULT | OFF |
| C | STANDARD_SCO | IN_COMMUNICATION | VOICE_RECOGNITION | DISABLE_NS_AEC_AGC | OFF |
| D | STANDARD_SCO | NORMAL | VOICE_RECOGNITION | DISABLE_NS_AEC_AGC | OFF |
| E | HFP_VOICE_RECOGNITION | IN_COMMUNICATION | VOICE_RECOGNITION | DISABLE_NS_AEC_AGC | OFF |
| F | Best continuous A–E route | same | same | same | DB_PLUS_6 |
| G | Best continuous A–E route | same | same | same | DB_PLUS_12 |
| H | Best continuous A–E route | same | same | same | AUTO_LEVEL |

Also available independently: DB_PLUS_18, DISABLE_NS, DISABLE_NS_AEC, AGC_ONLY
(NS off, AEC off, AGC on), and the existing microphone sources.

Gain is applied **after** Android/headset capture. It cannot reconstruct speech replaced by
silence upstream. If A–E remain chopped, louder output from F–H does not solve that problem.
Auto level targets approximately −18 dBFS RMS with at most +18 dB gain, 50 ms gain-reduction
and 500 ms gain-increase time constants, saturation at PCM16 limits, and gain relaxing to
unity below −60 dBFS. It never mutes samples as a noise gate would.

## Read the diagnostics for every take

“Show current diagnostics” reports routing, the device, requested/observed mode, HFP request
result, connection state, current/last recording backend, source, session ID, format, and
effect availability/control/result. You can select and copy this text. It lasts for the
current app process; settings themselves are persisted. Reopening the process starts fresh
diagnostic evidence. A successful routing request is not readiness: legacy SCO/HFP must
report connected audio, with an eight-second timeout and explicit failure.

After the link is connected, the selected headset is matched to an actual Android input port.
The recorder requests that port explicitly and checks the observed input and audio mode.
AudioRecord-backed paths allow up to 1.5 seconds to confirm the requested input/mode, checking
before and after each read. Unverified/transition buffers are discarded, and a full client-buffer
capacity is drained after verification to exclude previously queued audio. Routing changes are monitored
during capture, and a mismatch stops capture rather than silently continuing on the phone mic.
MediaRecorder cannot discard startup PCM, so Bluetooth recording there requires an immediate
verified input on Android 9+; use WAV/direct M4A on older versions. Diagnostics also append
saved-file metadata so platform encoding outcomes can be compared with the request.

The route selector is literal: STANDARD_SCO requests `startBluetoothSco()`,
HFP_VOICE_RECOGNITION requests `BluetoothHeadset.startVoiceRecognition()`, and
COMMUNICATION_DEVICE explicitly requests `setCommunicationDevice()` (Android 12+).
The selected NORMAL or IN_COMMUNICATION mode is requested independently on every route.
An unsupported route or an observed mode mismatch is reported instead of substituting another
mechanism. HFP requests the classic headset profile and requires
Nearby devices / BLUETOOTH_CONNECT permission on Android 12+. No scan or location
permission is used. HFP may be rejected by a headset or Android version. It never silently
falls back to SCO. With multiple HFP headsets and no unambiguous device match, disconnect
the others. Legacy standard SCO device choice remains controlled by Android.

The NS/AEC/AGC controls report only Java audio-effect state. They cannot establish whether
all headset, HAL, or vendor DSP is bypassed. UNPROCESSED is rejected if Android does not
advertise support; even an advertised capability is not proof that all vendor DSP is absent.
SYSTEM_DEFAULT leaves the effect enabled states untouched.

3GP and M4A's MediaRecorder fallback do not expose PCM or these input effect controls.
They are rejected when a non-default preprocessing policy or enabled software gain is effective for this recording,
instead of dropping that choice. A default-processing/gain-off M4A fallback remains possible
and is identified in diagnostics. Dormant Bluetooth-only processing never blocks phone-mic 3GP
or its M4A fallback; compatibility checks resolve scope first. System-audio setup failure never falls back to a microphone;
unsupported source/format combinations are rejected without rewriting preferences. An AAC
bitrate exceeding the format/encoder limit is also rejected rather than silently lowered.
System-playback capture bypasses microphone-only effects and gain. A requested file sample
rate does not prove a Bluetooth link sample rate or codec.

## Verification

```text
gradlew.bat testDebugConfigDebugUnitTest --console=plain
gradlew.bat assembleDebugConfigDebug --console=plain
gradlew.bat connectedDebugConfigDebugAndroidTest --console=plain
```

The repository currently ignores unit-test failures at Gradle task level: inspect
`app/build/test-results/testDebugConfigDebugUnitTest/TEST-*.xml` for failures/errors.
Unit tests exercise PCM math, settings persistence, effect policies/control failures,
and simulated SCO/HFP lifecycle. Emulator testing exercises app settings and recording;
it cannot validate the Fresh ’n Rebel/Huawei Bluetooth audio path. Run A–H on the actual
headset/phone to determine whether any route produces continuous normal-volume speech.

### LDPlayer validation notes (2026-10-07)

On LDPlayer Android 9 / API 28, the experimental selectors persisted across a process
restart. A WAV take with VOICE_RECOGNITION, DISABLE_NS_AEC_AGC and +12 dB saved successfully.
The UI correctly identified the built-in input, Bluetooth routing not applied, and all
three Java effects unavailable. The microphone test captures PCM across every preprocessing
policy; all six existing direct-AAC recording/lifecycle tests also pass on this target.
Fresh-default M4A recording and direct M4A with AUTO_LEVEL also saved successfully. Selecting
AUTO_LEVEL with 3GP saved a normal recording and explicitly displayed “Effects and software
gain NOT APPLIED”. The final unit run passed 529 tests, and the debug APK assembled.

The full connected suite ran 332 tests with six locale-sensitive failures on this French
emulator: `v2.app.AppExtensionsTest` expects English `year/day`, and five
`v2.app.records.RecordsExtensionsTest` assertions look up literal English keys such as
`Jun 22, 2025`. These files and their date-formatting implementation are outside this change.
The focused audio package passes all seven tests:

```text
gradlew.bat connectedDebugConfigDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.dimowner.audiorecorder.v2.audio --console=plain --max-workers=2
```

Do not interpret those emulator checks as evidence about the Fresh ’n Rebel Bluetooth link.

### Preset UI follow-up (2026-10-08)

The preset/collapsible-settings update passes 557 unit tests and builds the debug APK.
LDPlayer checks cover migration of existing choices, all three selections, preservation of
the HFP selection across process restart and app update, Custom expand/collapse, visible
dropdown arrows, and diagnostics remaining accessible with the controls collapsed. Live
recorder-state guards also prevent source edits or Reset from changing the preset if recording
starts through the floating button after Settings has already opened.

### Selection-fidelity follow-up

The literal-routing and capture-validation update passes 593 unit tests. All eight audio
instrumented tests pass on an isolated Android 10 emulator, including explicit AAC bitrate
rejection, recording/stop/recovery, effect inspection, and rejected system-audio starts while
the service remains bound. The bound-service foreground-start regression also passes on an
Android 14 emulator. Physical Bluetooth headset behavior still requires device testing;
the tests validate the requests and observable input/mode checks, not vendor DSP internals.

### Independent source and processing scope follow-up (2026-10-10)

The update passes **618 unit tests** (zero failures/errors/skips in the XML reports), and both
debug APKs build. **Nine audio instrumented tests pass on the isolated Android 10 emulator**,
including native phone PCM capture under both scope settings and audio-mode restoration.

Emulator UI checks confirm the default-ON binary switch, its persistence after process restart,
independent external/Bluetooth sources, the five microphone-only Bluetooth source options,
NORMAL mode, visible Custom controls/chevrons, and diagnostics accessibility while controls
are recording-locked. With AGC_ONLY/AUTO_LEVEL selected, phone 3GP records successfully when
scope is Bluetooth-only and is explicitly rejected with all-microphone scope. Phone WAV with
all-microphone scope saves successfully; diagnostics report NORMAL applied, the built-in input,
the effective gain/policy and saved-file metadata. The emulator reports Java effects unavailable;
that result is exposed in diagnostics rather than treated as successful effect activation.

Lifecycle regressions cover a preview enabled between snapshot and preparation, system playback
blocking microphone previews, an already-open Settings screen following start/stop, and format
controls respecting the startup lock. The emulator crash buffer was empty after these checks.
Physical Bluetooth routing/ENC quality for this update still requires the requester's hardware.

### Already-connected Standard SCO regression (2026-10-11)

The review was limited to the ten Bluetooth commits in
`3c8dcf0930a7a1bd31be3ab78386fa312b61c528..dd8f1f210692baa10c94eb4dc8826b8d4b790fa5`
(56 files), plus the related SCO implementation in upstream PR #181. The parent overlay
work in #182 was not re-audited.

At `dd8f1f21`, a Robolectric regression with SCO already connected before receiver registration
reached the eight-second timeout without a new CONNECTED event. A separate fixture returns
the registration snapshot without delivering an initial receiver callback, exercising the
specific boundary that #181 handled explicitly. Before the fix, the controller suite had
23 tests with five assertion failures, zero errors, and all 15 original tests passing.

Standard SCO now consumes a returned CONNECTED snapshot only after acquiring its own SCO
request and enabling routing. It uses the existing readiness handler, so requested-mode
validation remains active. Actual recorder input verification, transition-buffer rejection,
disconnect observation and independently guarded resource cleanup remain unchanged.
Initial DISCONNECTED snapshots still wait for a new connection; the snapshot does not make
HFP or communication-device routing ready.

After the fix, all 59 focused tests passed with zero failures/errors/skips, including the
controller's 23 tests, `InputRoutingGuardTest`, `AudioManagerHelperTest`,
`MicrophoneModeScopeTest` and `CaptureProcessingSessionTest`. The eight new regressions cover
sticky/return-only readiness beyond the timeout, owned-request release, later disconnection,
initial disconnection, failed startup, mode rejection, HFP separation and phone-input rejection.
This is simulated regression evidence, not a new physical-headset validation. The earlier
618-unit-test, nine-instrumented-test and hardware results above remain historical evidence
for the preceding source tree.

The complete unit suite then passed **626 tests**, with zero failures/errors/skips across
49 XML reports. This environment required preloading the project's existing Byte Buddy test
agent through an external Gradle init script because dynamic MockK attachment was unavailable;
no build configuration or test assertions were changed to accommodate it.

#### What remains distinct in PR #181

Do not merge #181's old helper wholesale into the controller architecture. Its readiness wait,
request ownership, cleanup, persistent disconnect observation and initial CONNECTED handling
are covered by the newer architecture and this fix. The following protections still need
separate targeted tests/adaptation before claiming complete equivalence:

- Fail immediately on CONNECTING → DISCONNECTED: #183 currently waits for timeout unless READY.
- Clean up immediately when the headset disappears during CONNECTING: #183's removal handler
  currently fails only READY routes.
- Reject stale callbacks from a previous SCO receiver registration: #181 uses receiver identity;
  #183's generation checks protect HFP/modern callbacks, but it reuses its SCO receiver.
- Verify cancellation and ownership through the recording service: #181 suspends in its enable
  method, whereas #183's UI enable is asynchronous and the service owns stop/destroy cleanup.
- Preserve the original request/mode across repeated enable calls (deduplication already exists
  in #183), and avoid writing audio mode when no headset exists (#183 currently sets/restores it).

These are pre-existing differences, outside the minimal already-connected SCO correction.
