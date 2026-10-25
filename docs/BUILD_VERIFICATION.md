# Task 1 verification — October 25, 2026

## Environment inspected before building

- Existing Git repository was clean; only an empty README existed.
- Installed Java: OpenJDK 11.0.32.1; `javac` is available.
- No system Gradle executable or existing Gradle cache was found.
- `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `JAVA_HOME` were unset.
- No SDK found in the usual user, `/opt`, or `/usr/local` locations.
- No system packages were installed and no sudo commands were used.

## Executed build commands

Both required commands were attempted with an isolated temporary Gradle cache:

```sh
GRADLE_USER_HOME=/tmp/viewy-gradle-home ./gradlew testDebugUnitTest
GRADLE_USER_HOME=/tmp/viewy-gradle-home ./gradlew assembleDebug
```

The initial sandboxed attempts could not download Gradle because networking was
restricted. Both commands were retried with network access. Gradle 9.6.0 was
successfully downloaded and its distribution checksum was checked by the wrapper.
Both commands exited with failure before project configuration:

```text
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.
```

Tests did not execute. Kotlin/Compose compilation did not execute. No APK was
generated. The screen has not been rendered or tested on an emulator/device.
There is no JVM test suite yet because this milestone introduces no domain logic.
Do not treat a future `testDebugUnitTest NO-SOURCE` result as passing tests.

## Other checks executed

- Official wrapper JAR SHA-256 verified against Gradle's published checksum:
  `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.
- `bash -n gradlew` passed.
- Wrapper ZIP/JAR integrity and main class presence checked with Python.
- All Android XML resources/manifest parsed successfully with Python.
- Version catalog parsed successfully with Python `tomllib`.
- `git diff --check` passed.
- Official AGP 9.4.0, Compose BOM 2026.09.00, and Kotlin Compose compiler
  2.4.21 Maven POMs were fetched to verify pinned artifact availability.

These static checks do not establish Android build or runtime correctness.

## Required local setup and next verification

Follow the setup commands in the README: install/configure JDK 17+, Android SDK
Command-line Tools, `platforms;android-37`, `build-tools;36.0.0`, and
`platform-tools`; accept SDK licenses. Set `JAVA_HOME` and `ANDROID_HOME` (or
`sdk.dir` in untracked `local.properties`). No global Gradle installation is needed.
Rerun the two build commands with network access for the first dependency fetch.
The expected APK is `app/build/outputs/apk/debug/app-debug.apk`.
Install on an API 23+ device/emulator and check both themes, font sizes, rotation,
and insets. Build verification remains blocked until this tooling is available.
