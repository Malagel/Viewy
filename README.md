# Viewy

Viewy is an offline Android app for approximate physical measurements using phone
cameras, orientation sensors and other supported hardware. The current method
estimates horizontal distance to a vertical object from its assumed height and
two aiming directions. Application ID: `com.malagel.viewy`.

## Basic usage

1. Allow camera access and enter the vertical distance between the object's
   endpoints in meters or decimal feet.
2. Keep the camera location fixed. Aim the centered reticle at the top, stop for
   about one second, then capture while still.
3. Aim at the bottom on the same vertical line, settle, and capture within 15 s
   of the top capture. Remain still briefly after each capture.
4. Read the approximate horizontal distance. Use recapture bottom or restart
   to try again; saving a height starts a new measurement.

Follow alignment/stability guidance. Preview-center aiming and preview/sensor
timing are approximate, and uncertainty is unquantified. No photos, recordings,
measurement history or telemetry are stored; the app operates entirely offline.

- [Product specification](docs/PRODUCT_SPEC.md): implemented behavior and future requirements.
- [Architecture](docs/ARCHITECTURE.md): component boundaries and technical contracts.
- [Validation](docs/VALIDATION.md): evidence, limitations and verification procedures.
- [Development rules](AGENTS.md): repository engineering and resource restrictions.

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

From the repository root, build the debug APK with bounded resource use:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Reproducible test/lint
commands, recorded results and hardware procedures are in
[Validation](docs/VALIDATION.md). Run only checks relevant to your change;
[AGENTS.md](AGENTS.md) defines resource and device restrictions.

## Install and run

With an authorized API 23+ device or emulator connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.malagel.viewy/.MainActivity
```

Alternatively, `./scripts/deploy.sh` builds, installs while preserving app data,
and launches Viewy. The script invokes a standard Gradle assembly without the
resource flags above. VSCodium's **Viewy: Build & Deploy** task runs the same script.
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
