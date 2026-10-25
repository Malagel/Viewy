# Architecture

Task 1 uses one `app` module and one `ComponentActivity`. `MainActivity` owns
Android lifecycle entry and edge-to-edge setup; `ui/ViewyScreen.kt` contains
the stateless placeholder screen and small private composables; `ui/theme`
contains Material 3 light/dark palettes. Strings and vector assets are Android
resources. The layout respects system insets, caps its width on large displays,
and scrolls on short windows or large font scales. Android Studio previews
cover both palettes.

No ViewModel is needed until there is UI state or asynchronous work. When
future tasks introduce state, expose immutable UI state from a ViewModel and
collect it with lifecycle awareness. Use structured Kotlin coroutines for
asynchronous work when needed; no extra coroutine dependency is necessary for
this static screen.

Add pure Kotlin mathematical code under `domain` when calculations arrive,
with meaningful JVM tests. Add camera/sensor adapters and local settings under
appropriate implementation packages only as their milestones require. Keep
Android sensor types and Compose out of distance calculations. Introduce
repositories or interfaces only when a concrete boundary warrants them.

Versions are pinned in `gradle/libs.versions.toml` and wrapper properties.
The root buildscript explicitly upgrades AGP's built-in Kotlin Gradle plugin
to 2.4.21, aligned with the Compose compiler plugin; do not apply the separate
Kotlin Android plugin with AGP 9. Build Tools are pinned to 36.0.0. There are
no dynamic dependency versions. The wrapper distribution has a SHA-256 pin.
No camera, Internet, analytics, advertising, navigation, DI, or storage library
is included.

Version references (checked October 25, 2026):
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Kotlin stable releases](https://kotlinlang.org/docs/releases.html)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity)
