# Viewy

An offline Android app intended to estimate distance using a camera, phone
orientation, and an estimated object height. Application ID: `com.malagel.viewy`.

Task 1 creates the Android foundation and a placeholder screen. Camera access,
sensors, distance calculations, presets, persistence, and navigation are planned;
none is implemented. See [product specification](docs/PRODUCT_SPEC.md),
[architecture](docs/ARCHITECTURE.md), and [verification status](docs/BUILD_VERIFICATION.md).

## Technology

- Kotlin 2.4.21 with AGP built-in Kotlin and the matching Compose compiler plugin.
- Jetpack Compose BOM 2026.09.00 (UI/foundation 1.12.1, Material 3 1.4.0).
- Activity Compose 1.13.0; Gradle Kotlin DSL and a version catalog.
- Android Gradle Plugin 9.4.0; Gradle wrapper 9.6.0.
- Minimum Android 6.0 (API 23); compile/target Android 17 (API 37).
- SDK Build Tools 36.0.0; Java/Kotlin JVM target 17.

API 23 provides a broad baseline compatible with this stack. API 37 is the
current stable major Android SDK. The static screen needs no additional
coroutine library; asynchronous work can use structured coroutines in later tasks.

## Development tools and setup

Use JDK 17 or newer (JDK 17 is the baseline) and an Android Studio version
supporting AGP 9.4, such as Quail 4 or Rabbit 1. Command-line builds need no IDE
or system Gradle installation. Install Android SDK Command-line Tools, Platform
37, Build Tools 36.0.0, and Platform Tools via Android Studio's SDK Manager.
Alternatively, with `sdkmanager` on your PATH:

```sh
export JAVA_HOME=/absolute/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Android/Sdk"
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-37" "build-tools;36.0.0" "platform-tools"
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
```

Set `ANDROID_HOME` to your actual SDK directory, or create an untracked
`local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.
The first build requires network access to download Gradle and Maven dependencies;
the application itself operates offline. Do not commit SDK paths or signing keys.

## Build, test, and install

From the repository root:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.malagel.viewy/.MainActivity
```

Enable USB debugging on an API 23+ device, or start an emulator before installing.
The debug APK is automatically signed with a local debug key. Android Studio can
also run the `app` configuration. There is no domain logic or JVM test suite yet;
a successful unit-test task may report `NO-SOURCE` and is not evidence of UI
validation. Check both themes, font scaling, rotation, and system insets on a device.

Read [AGENTS.md](AGENTS.md) before future milestones. Keep changes within the
current task and run relevant verification before reporting results.
