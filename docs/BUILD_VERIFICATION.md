# Build verification

## Automated verification

| Area | Latest recorded result | Scope |
| --- | --- | --- |
| Android debug build | `./gradlew :app:assembleDebug` succeeded October 26, 2026 | Phase A final review: ran together with JVM tests using installed JDK 17 and SDK from local developer configuration; 8 tasks executed, 34 up to date. No clean rebuild. |
| Mathematical domain JVM tests | `./gradlew :app:testDebugUnitTest` succeeded October 26, 2026 | 31 tests passed, zero failures/errors/skips. Includes adjacent uncertainty angles, near-vertical rays and sensitivity, symmetric degeneracy classification, and rotated alignment diagnostics. |
| Diff whitespace validation | `git diff --check` succeeded October 26, 2026 | Phase A final review. |
| Deployment script tests | 24 Python unit tests passed October 26, 2026 | Isolated configuration and fictional devices; configuration precedence, missing settings, selection, reconnection, transport aliases, timeouts, and build/install/launch failure handling. ADB and Gradle are mocked. |
| Shell syntax | `bash -n` passed for the deployment script and local configuration October 26, 2026 | Syntax only. |
| Live deployment attempt | No reachable device within the 15-second discovery window October 26, 2026 | Retried with access to the host ADB server; device list was empty. Stopped before build/install. Post-refactor physical deployment could not be verified. |
| VSCodium task configuration | Task still invokes `scripts/deploy.sh`; configuration unchanged | Task was not run interactively during this refactor. |

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Developer-reported physical device testing

The developer successfully built, installed, and launched Viewy on a physical
Samsung Galaxy S22. Automatic wireless ADB reconnection has worked under some
conditions, but has not been consistently reliable when wireless debugging
discovery stops. These are manually reported results, separate from the mocked
automated tests above.

## Unverified functionality and limitations

- A clean Android rebuild has not been verified.
- JVM domain tests have run; instrumentation tests are not implemented.
- Mocked deployment tests do not establish device runtime correctness or reliable
  wireless reconnection in all conditions.
- UI rendering, light/dark themes, font scaling, rotation, and system insets have
  not been validated on a device or emulator.
- Camera, sensors, measurement UI integration, presets, persistence, and navigation
  are not implemented and cannot yet be validated. Phase A mathematics has no
  device/sensor integration coverage.

The Phase A verification initially encountered a read-only Gradle cache, then
Java 11 and a missing SDK environment variable. Rerunning with cache access,
`JAVA_HOME` pointing to installed JDK 17, and `ANDROID_HOME` pointing to the SDK
already specified in local developer configuration passed both required tasks.
No local configuration files were changed. The build reported that
`libandroidx.graphics.path.so` could not be stripped and was packaged as supplied.

See the [README](../README.md) for build, test, and installation commands.
