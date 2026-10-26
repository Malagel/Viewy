# Architecture

## Current structure

Viewy uses one `app` module and one `ComponentActivity`. `MainActivity` owns
Android lifecycle entry and edge-to-edge setup. `ui/ViewyScreen.kt` contains
the stateless placeholder screen and small private composables; `ui/theme`
contains Material 3 light/dark palettes. Strings and vector assets are Android
resources.

The layout applies system insets, caps its width on large displays, and scrolls
on short windows or at large font scales. Compose previews cover both palettes;
device behavior remains unverified.

## State and responsibility boundaries

The static screen does not need a ViewModel or an additional coroutine library.
When state or asynchronous work is introduced, expose immutable UI state from a
ViewModel, collect it with lifecycle awareness, and use structured coroutines.

Keep distance mathematics in pure Kotlin under `domain`, independent of Compose
and Android sensor types, with meaningful JVM tests. Keep camera acquisition,
sensor acquisition, presentation state, and local settings separate. Add adapters,
repositories, or interfaces only when a concrete boundary requires them.

## Build and dependencies

Dependency versions are pinned in `gradle/libs.versions.toml`; the Gradle wrapper
pins its distribution and SHA-256 checksum. Build Tools are pinned to 36.0.0.

AGP 9 supplies built-in Kotlin. The root buildscript upgrades its Kotlin Gradle
plugin to 2.4.21, matching the Compose compiler plugin. Do not apply the separate
Kotlin Android plugin alongside built-in Kotlin.

The foundation has no camera, analytics, advertising, navigation, dependency
injection, or storage libraries. Add dependencies only as features require them,
while preserving offline operation.
