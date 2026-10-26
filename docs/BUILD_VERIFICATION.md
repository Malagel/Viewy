# Build verification

## Phase C automated verification — October 26, 2026

| Check | Actual result | Scope |
| --- | --- | --- |
| `./gradlew :app:testDebugUnitTest` | Passed: 97 tests, zero failures/errors/skips | 31 unchanged Phase A, 14 unchanged orientation math, 21 orientation-session, 7 camera-mapping, 4 height-input and 20 measurement-workflow tests. Includes actual acquisition observations through the complete workflow/calculator, inverse pose signs, selected-axis roll, wraparound, invalid frames/source/mapping, camera loss, stale/future readings, duplicate taps, retries, geometry correction and exact foot conversion. |
| `./gradlew :app:assembleDebug` | Passed | Debug APK at `app/build/outputs/apk/debug/app-debug.apk`. Incremental build using installed JDK 17, API 37 SDK and existing developer caches. No clean rebuild or release build claimed. |
| `./gradlew :app:lintDebug` | Passed: zero errors, five warnings | Existing Gradle/AGP/Lifecycle update suggestions and backup-rule guidance. Camera2 interop uses the AndroidX opt-in annotation. No new camera/lifecycle lint errors remain. |
| `git diff --check` | Passed | Tracked diff checked; new source/test/document whitespace also inspected. |
| Merged debug manifest inspection | CAMERA plus the existing app-private signature permission only | No Internet, network-state, microphone, location or storage permission. camera-view's transitive network-state declaration is removed. Only Preview is instantiated. |
| `adb devices -l` | Succeeded; empty device list | No authorized physical device or emulator was connected. No Phase C install, launch, camera metadata capture, live sensor assertion, optical calibration or physical distance measurements were performed. |

All three Gradle tasks passed together after the final code/test changes. The
final correction pass added geometry-independent alignment coverage and extended
reset-step diagnostics assertions. Production messages now use string resources,
and focused light/dark result/control previews compile without camera hardware. Initial
sandbox execution could not write the external Gradle cache; approved access to
the existing SDK/cache configuration enabled verification. No local deployment
configuration or deployment script was changed. No commits or pushes were made. The correction pass did not invoke ADB or
deployment tools; the discovery row above records the original Phase C run.
Native CameraX/Compose libraries were packaged as supplied when host stripping
was unavailable; the debug build succeeded.

## Physical-device evidence and pending validation

The developer previously built, installed and launched Viewy on a Galaxy S22.
They reported preliminary Phase B fixed-aim separation of approximately 0.029°
over 14.1 s with Game Rotation Vector and 0.028° over 14.8 s with Rotation Vector.
These are stationary repeatability observations, not independently calibrated
angular accuracy, uncertainty bounds, optical alignment or Phase C results.
Wireless ADB reconnection had mixed developer-reported reliability previously.

The [Phase C manual procedure](CAMERA_MEASUREMENT.md#galaxy-s22-manual-validation-procedure-pending)
covers permission recovery, preview/reticle, physical lens identity, pitch/roll/
display changes, two-point captures, alignment, handheld gating, interruptions,
reference distances, repeated runs, drift-like discrepancies, lighting/focus and
accessibility. It includes a local measurement worksheet and distinguishes
measurable combined effects from causes orientation alone cannot observe.

Remaining unverified behavior:

- Real Android camera initialization, physical-output pinning, autofocus and
  requested stabilization behavior, permissions and lifecycle/resource cleanup.
- Actual selected-lens metadata and optical principal-point/preview-center mapping.
- Preview latency and exposure-to-orientation correspondence; no synchronization
  accuracy is asserted.
- Device UI rendering, light/dark themes, font scaling, TalkBack, rotation and insets.
- Handheld usefulness of provisional stability/alignment/separation gates, distance
  errors against independent references and portability beyond the reference S22.
- Instrumentation tests are not implemented; JVM tests do not establish hardware
  behavior or absolute measurement accuracy.

The implementation explicitly exposes approximate aiming and unquantified
uncertainty. No bounded confidence interval is inferred from sample variation.

## Previous verification retained for context

Phase B's last recorded October 26, 2026 verification passed 66 JVM tests, debug
assembly, lint (the same five warnings) and whitespace checks. Its device discovery
also returned no connected devices. Phase C retains those 66 tests unchanged.

The deployment-script work previously passed 24 mocked Python unit tests and Bash
syntax checks. Those tests exercise fictional devices/configuration, not real
wireless reliability. The script and VSCodium deployment task remain unchanged
in Phase C; their tests were not rerun for this app-only change.

See the [README](../README.md) for build/install commands and the
[orientation guide](ORIENTATION_ACQUISITION.md) for sensor contracts and source-specific checks.
