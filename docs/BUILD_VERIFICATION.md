# Build verification

Latest recorded verification: October 25–26, 2026. This summary retains the
reported results; builds and deployment tests were not rerun for the documentation
cleanup.

## Verified results

| Area | Latest recorded result | Scope |
| --- | --- | --- |
| Android debug build | `./gradlew :app:assembleDebug` succeeded October 25 | JDK 17 and the configured SDK; existing cached outputs, all tasks up to date. No clean rebuild. |
| Deployment script tests | 16 Python unit tests passed October 26 | Mocked ADB/Gradle; selection, reconnection, transport aliases, timeouts, and build/install/launch failure handling. |
| Shell syntax | `bash -n scripts/deploy.sh` passed October 26 | Syntax only. |
| Live device discovery | Exited with no reachable device after the 15-second discovery window October 26 | Confirmed early exit before build/install; successful reconnection was not exercised. |
| VSCodium task configuration | JSON and script command checked October 25 | Task was not run interactively. |

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Unverified functionality and limitations

- A clean Android rebuild has not been verified.
- No Android application tests have run, and no JVM or instrumentation test suite
  exists yet. `testDebugUnitTest` reporting `NO-SOURCE` would not establish test
  coverage.
- Successful wireless reconnection, device installation, and app launch remain
  unverified. Mocked deployment tests do not establish device runtime correctness.
- UI rendering, light/dark themes, font scaling, rotation, and system insets have
  not been validated on a device or emulator.
- Camera, sensors, measurements, presets, persistence, and navigation are not
  implemented and cannot yet be validated.

See the [README](../README.md) for build, test, and installation commands.
