# Viewy

An offline Android app intended to estimate distance using the camera, phone
orientation, and an estimated object height. Application ID: `com.malagel.viewy`.

The app retains its Compose placeholder screen and disabled production controls.
Phase A provides pure Kotlin known-height distance calculations. Phase B adds
foreground orientation acquisition and a debug-only sensor/capture diagnostic
panel below the controls. Camera preview, measurement UI, presets, persistence,
and navigation remain planned. The sensor foundation and domain have JVM tests.

- [Product requirements](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Orientation conventions and Galaxy S22 validation](docs/ORIENTATION_ACQUISITION.md)
- [Build and testing status](docs/BUILD_VERIFICATION.md)

## Development setup

Use JDK 17 or newer and an Android SDK with Platform 37, Build Tools 36.0.0,
and Platform Tools. Accept SDK licenses through Android Studio or `sdkmanager`.
Set `JAVA_HOME` and `ANDROID_HOME` for your installation, or configure the SDK
with `sdk.dir` in an untracked `local.properties`.

Use the included Gradle wrapper; no system Gradle is required. The first build
needs network access for dependencies; the app itself operates offline. Toolchain
and library versions are defined in `gradle/libs.versions.toml`, the Gradle build
files, and `gradle/wrapper/gradle-wrapper.properties`. Minimum Android API is 23;
compile and target API are 37. Keep local paths and signing keys out of Git.

## Build and test

From the repository root:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_deploy.py'
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. JVM tests cover the
known-height domain, orientation transforms, and acquisition quality/time rules;
instrumentation tests are not implemented.
The Python tests mock ADB and Gradle to check the deployment script.
Debug builds expose orientation diagnostics by scrolling below the production
controls. Follow the Galaxy S22 validation guide before relying on sensor results.
Device checks should also cover both themes, font scaling, rotation, and insets.

## Install and run

With an authorized API 23+ device or emulator connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.malagel.viewy/.MainActivity
```

Alternatively, `./scripts/deploy.sh` builds, installs while preserving app data,
and launches Viewy. VSCodium's **Viewy: Build & Deploy** task runs the same script.
Optional machine settings belong in `scripts/deploy.local.conf` (ignored by Git).
This is a trusted Bash file; for example:

```sh
JAVA_HOME='/path/to/jdk-17'
ANDROID_HOME="$HOME/Android/Sdk"
PHONE_SERIAL='hardware-serial-from-your-phone'
# Optional: ANDROID_USER_HOME and GRADLE_USER_HOME
```

Explicit environment variables override local settings. Set `JAVA_HOME` and
`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) through either source. If both SDK variables
are explicitly set, they must refer to the same SDK. Android configuration and
Gradle caches default to `$HOME/.android` and `$HOME/.gradle`; set
`ANDROID_USER_HOME` and `GRADLE_USER_HOME` to preserve custom locations.
`PHONE_SERIAL` enables automatic discovery of that phone; alternatively select
an authorized target with `ANDROID_SERIAL`. The script requires Bash and GNU
`timeout`.

To choose another authorized target, use the identifier from `adb devices -l`:

```sh
ANDROID_SERIAL='serial-from-adb-devices' ./scripts/deploy.sh
```

For wireless debugging, enable it in the device's Developer options. Use
`adb pair PHONE_IP:PAIRING_PORT` if pairing is needed, then
`adb connect PHONE_IP:DEBUGGING_PORT` with the current address shown on the
phone. Pairing and debugging ports differ. The script retries discovery for
up to 15 seconds and stops before building if no eligible device is reachable.

Follow [AGENTS.md](AGENTS.md) for development rules.
