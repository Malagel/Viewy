# Viewy

Viewy is an offline Android app for approximate physical measurements using phone
cameras, orientation sensors and other supported hardware. The current method
estimates horizontal distance to a vertical object from its assumed height and
two aiming directions. Release application ID: `com.malagel.viewy`.

## Basic usage

1. On first launch, continue through Welcome, Permissions and Completion. Camera
   access is optional here and can be allowed later in a measurement method.
   Units initialize from the device region and remain changeable in Settings.
   Subsequent launches open Home with no active measurement. Tap **Long Distance**,
   then **Known Object Height**. Before height entry, Android Back returns to the method list;
   **Home** returns directly to all modes.
   Mode/method lists slide inside the card. Measurement fades in from the right;
   Back to the method list reverses the horizontal motion; Home uses a vertical fade/slide.
   Android Back from the camera returns directly to Enter height;
   from a distance result it restarts measurement, retaining height.
   On first activation, read and dismiss the method tutorial. The **?** button
   beside the settings gear reopens it. Advance from **Instructions** with **>**,
   or swipe left; swipe right from **Important** to return. Read the tips and use
   the bottom-right **<** to go back or **X** to close. Android Back returns one
   tutorial page at a time and closes from Instructions. **How does it work?** opens
   a WIP screen; its top-left back arrow or Android Back returns to Important. Tap **Enter height** and save the vertical distance
   between the object's endpoints with the embedded keypad. Metric offers m/cm;
   Imperial offers decimal ft/in, following Settings without changing global units.
   Tap **Enter** to confirm. Back from the camera returns to **Enter height**;
   the next Back exits the method. Back from the camera's height editor instead
   discards the draft and returns to the camera with saved height. Then allow camera access when
   prompted by the viewfinder controls. Editing stays inside the rounded workspace
   without opening the software keyboard.
2. Keep the camera location fixed. Aim the centered reticle at the top, stop for
   about one second, then capture while still.
3. Aim at the bottom on the same vertical line, settle, and capture within 15 s
   of the top capture. Remain still briefly after each capture.
4. Read the approximate horizontal distance inside the former camera container.
   Long results use kilometers from 10,000 m or miles from 5,280 ft.
   The distance counts up, followed by editable object height, elevation difference
   and an approximation disclaimer. Use the circular **Restart** arrow at bottom
   left to try again; the result fades out as acquisition resumes. Tap the height
   row to correct it; confirmation recalculates from the same captured directions
   without another capture or count-up. Camera and orientation
   acquisition stop immediately on completion.

The app uses Material 3's baseline dark palette. The normal portrait screen keeps
capture/navigation visible without scrolling; the static embedded keypad fits
proportionally inside the measurement container. Settings and Help preserve a
height draft; Home clears the method completely so its next opening starts fresh.
Small windows or large text offer status/details dialogs; **Open viewfinder**
opens a scrollable workspace in exceptionally constrained layouts. Help stays
scrollable in its centered card. The top-right gear opens Settings at any time
outside another modal. Tap **Measurement units** to switch Metric/Imperial, or
**Language** to select **English**, **Español**, or **Use device language** (default).
Changes save automatically. Settings, diagnostics, tutorials, selection and height
editing suspend camera/sensor work and discard incomplete endpoint pairs. Entered
height, keypad draft and completed results remain available through Settings and
Help. Backgrounding retains saved height/results but invalidates unfinished captures.
Home clears the method, including height/results, while keeping app preferences and
tutorial completion.
Language and unit changes update the current screen in place. **Developer Options** contains
a persistent **Enable diagnostics** preference (off by default) and **Erase all app data**.
When enabled, hold **?** to open read-only method diagnostics; a normal tap always
opens the tutorial. Diagnostics shows current readiness, camera/sensor information
and completed endpoint geometry while acquisition remains stopped.
Only confirming **Erase all data** requests Android to clear storage and permissions and close Viewy; the app remains
installed. Reopen it manually for fresh defaults and tutorials. The rounded
Viewy wordmark uses bundled [Nunito Bold](https://github.com/google/fonts/blob/c7e2740188205a85323c7385547f6f59b4f2245a/ofl/nunito/Nunito-Bold.ttf)
([SIL Open Font License](app/src/main/assets/licenses/nunito_ofl.txt));
no fonts are downloaded during use.

Follow the top/bottom instructions and keep the phone still for each capture.
Preview-center aiming and preview/sensor timing are approximate, and uncertainty is unquantified. No photos, recordings,
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
files, and `gradle/wrapper/gradle-wrapper.properties`. Minimum Android version is
Android 12 (API 31); compile and target API are 37. Keep local paths and signing keys out of Git.

## Build and test

From the repository root, build the debug APK with bounded resource use:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It uses standard
Android debug signing, is debuggable, and installs as **ViewyDebug**
(`com.malagel.viewy.debug`). Release installs as **Viewy** (`com.malagel.viewy`),
is non-debuggable, and uses AGP 9.4's `optimization { enable = true }` for R8 code
and resource optimization with the standard Android release rules. Only the
standard `debug` and `release` build types exist; both apps can coexist with
independent permissions, preferences, onboarding and other application data.

To build a locally signed release APK without deploying:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs -Pviewy.localReleaseSigning=true :app:assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Local release
signing explicitly reuses the Android development/debug key; this changes only
the certificate, not release optimization or debuggability. That certificate is
for local testing and is not Viewy's eventual production identity.

Without the local-signing flag, release uses `signing.local.properties` at the
repository root when configured, or produces `app-release-unsigned.apk` otherwise.
The properties file is ignored by Git. Keep it readable only by your account
(for example, `chmod 600 signing.local.properties`), store the keystore securely
outside the repository, and never commit credentials or keys. Its format is:

```properties
storeFile=/absolute/path/to/private-release-key.jks
storePassword=YOUR_LOCAL_STORE_PASSWORD
keyAlias=YOUR_RELEASE_ALIAS
keyPassword=YOUR_LOCAL_KEY_PASSWORD
```

Use Java properties escaping for special characters (including backslashes).
A configured signing file must contain all four values. A release built without
`-Pviewy.localReleaseSigning=true` uses this configured signing identity; the flag
explicitly bypasses the file for local development signing. Production key custody
and distribution signing must be decided before publishing. Preserve a consistent
certificate when updating an installed application; a locally signed release cannot
be updated in place with a different production certificate.

Reproducible test/lint commands, recorded results and hardware procedures are in
[Validation](docs/VALIDATION.md). Run only checks relevant to your change;
[AGENTS.md](AGENTS.md) defines resource and device restrictions.

## Install and run

With an authorized API 31+ device connected, choose one command:

```sh
./scripts/deploy.sh          # ViewyDebug: :app:assembleDebug
./scripts/deploy-release.sh  # Viewy: :app:assembleRelease, development-key signing
```

Both commands share device discovery, local configuration and environment handling.
They build with bounded Gradle resource use, install with `-r` to preserve data,
and launch the matching app. Neither uninstalls apps nor clears data. A signing
mismatch stops deployment with an explanation; use the installed app's certificate
or explicitly decide how to migrate its data before changing signing identity.
Existing installations of `com.malagel.viewy` now correspond to release; debug uses
its new separate ID. VSCodium's **Viewy: Build & Deploy (DEBUG)** and
**Viewy: Build & Deploy (RELEASE)** tasks run the respective scripts.

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
