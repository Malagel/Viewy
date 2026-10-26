# Build verification

## Automated verification

| Area | Latest recorded result | Scope |
| --- | --- | --- |
| Android debug build | `./gradlew :app:assembleDebug` succeeded October 26, 2026 | Phase B: ran with JVM tests and lint using installed JDK 17 and SDK/cache paths from unchanged local developer configuration. Incremental build, no clean rebuild. |
| Domain and orientation JVM tests | `./gradlew :app:testDebugUnitTest` succeeded October 26, 2026 | 66 tests passed, zero failures/errors/skips: 31 unchanged Phase A tests, 14 orientation-math tests, 21 acquisition-session tests. Includes transform integration with Phase A, rotation signs/order, roll invariance, wraparound/poles, temporal validation, discontinuities, stability, high-rate retention, and comparison gates. |
| Android lint | `./gradlew :app:lintDebug` succeeded October 26, 2026 | Phase B: zero errors, five warnings (Gradle/AGP and two Lifecycle version suggestions, plus existing backup-rule guidance). No sensor/lifecycle API errors. Versions were kept aligned with the existing cached UI stack. |
| Diff whitespace validation | `git diff --check` succeeded October 26, 2026 | Phase B; new source/test/document files also checked for trailing whitespace. |
| Phase B device discovery | Host `adb devices -l` succeeded October 26, 2026; no devices listed | No emulator or authorized physical device was available. No Phase B install, launch, or live sensor assertions were performed. |
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
- Phase B sensor acquisition and debug diagnostics are implemented and JVM-tested,
  but Android registration, real sensor behavior, lifecycle cleanup on hardware,
  and Galaxy S22 optical-axis calibration have not been physically verified.
- Camera preview, production measurement UI integration, presets, persistence,
  and navigation remain unimplemented. Phase A now has deterministic transformed-ray
  integration coverage, but no live camera/sensor integration coverage.

The Phase A verification initially encountered a read-only Gradle cache, then
Java 11 and a missing SDK environment variable. Rerunning with cache access,
`JAVA_HOME` pointing to installed JDK 17, and `ANDROID_HOME` pointing to the SDK
already specified in local developer configuration passed both required tasks.
No local configuration files were changed. The build reported that
`libandroidx.graphics.path.so` could not be stripped and was packaged as supplied.

See the [README](../README.md) for build, test, and installation commands.

## Phase B validation handoff

Use the existing deployment script and scroll below the foundation controls in a
debug build. The [orientation validation guide](ORIENTATION_ACQUISITION.md)
contains exact Galaxy S22 procedures for all thirteen requested scenarios,
source comparison, coordinate conventions, provisional capture policy and
remaining camera calibration work. Its manual checks are pending, not passes.
No live orientation data or camera metadata was obtained during implementation.

The first Phase B Gradle attempt was blocked by a read-only external cache.
Rerunning with access to the existing caches passed the required checks. ADB
initially could not access its host server from the sandbox; the host retry
successfully started the server and returned an empty device list. No deployment
configuration or device settings were changed. No commits or pushes were made.
