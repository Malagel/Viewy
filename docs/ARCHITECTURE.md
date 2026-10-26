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

## Phase A measurement domain

`domain/MeasurementCatalog.kt` defines stable `long_distance` mode and
`known_object_height` method identifiers. The latter represents Known Object
Height — Two Endpoint Captures. This is a minimal catalog, without a generic
algorithm interface or framework.

`domain/KnownHeightDistance.kt` is pure Kotlin, independent of Android and Compose.
`KnownHeightDistance.estimate` takes height in meters and two 3D rays in one
gravity-aligned frame, with positive Z up and X/Y horizontal. Camera/sensor frame
transforms belong to a later acquisition adapter. Device pitch alone is not an
input to this algorithm.

For each ray, elevation is `atan2(z, hypot(x, y))`, and its tangent is computed
directly as `z / hypot(x, y)`. Components are first scaled by their largest absolute
value to avoid norm overflow/underflow. Horizontal distance is
`H / (topSlope - bottomSlope)`. The model assumes a vertical object, endpoints
at the same horizontal position, and an approximately stationary camera. It does
not estimate slant range or detect camera movement.

`KnownHeightResult` distinguishes estimates from typed failures, including invalid
height/rays, undefined horizontal bearing, reversed endpoints, degenerate
elevations, unusable distance and invalid uncertainty bounds. A zero horizontal
vector has undefined bearing; a nonzero, near-vertical ray is accepted when its
slopes and distance remain finite and usable. Slope overflow (including loss of
the normalized horizontal component) returns `UNUSABLE_DISTANCE`. The absolute
slope difference must exceed 32 times the unit Double ULP times
`max(1, abs(topSlope), abs(bottomSlope))`. Differences within this guard are
degenerate in either order; larger negative differences indicate reversed endpoints.
This protects numerical resolution, not calibrated sensor or usability thresholds.

Diagnostics expose both elevations, elevation separation, signed top-minus-bottom
bearing difference wrapped to `[-PI, PI]`, its absolute alignment error, and the
absolute first-order distance sensitivity to each elevation in meters per radian.
Separation uses an `atan2` determinant/dot-product formula in the elevation plane
to preserve small differences even when individual elevations round to `+/-PI/2`.
Sensitivities factor the secant squared to avoid intermediate slope-square
overflow; an overflowing derivative is null. Sensitivities indicate conditioning;
they are not measurement error estimates. Bearing uses the original X/Y
components independently of ray scaling. Its sign is ambiguous at an antipodal
separation, and circular differences have a branch at `+/-PI`; future guidance
must respect that wraparound. Misalignment does not reject an observation or
imply a calibrated quality classification. When bearings differ, a scalar distance
is conditional on the same-horizontal-position assumption; it is not a general
solution for endpoints at different horizontal positions. Bearing reliability near
vertical depends on input accuracy, which this domain does not infer.

Optional `InputBounds` are inclusive absolute bounds in meters or radians; all
supplied bounds must be finite, ordered and contain the nominal value. Height
bounds must be positive, and elevation bounds must lie strictly inside
`(-PI/2, PI/2)` to exclude poles and undefined bearings. Invalid partial inputs
are also rejected. Absent or incomplete bounds produce `Unavailable` uncertainty
while preserving a valid nominal estimate. An extreme near-vertical ray may have
an individual elevation rounded to `+/-PI/2`; no allowed Double angle bounds can
enclose that value, even though the vector-based nominal result remains usable.

For complete bounds, monotonicity gives the smallest distance as
`Hmin / (tan(topMax) - tan(bottomMin))` and the largest as
`Hmax / (tan(topMin) - tan(bottomMax))`. For same-sign angles the denominator uses
`sin(top - bottom) / (cos(top) * cos(bottom))`, avoiding cancellation between
nearly equal tangents. Opposite-sign tangents are subtracted directly to avoid
sine argument rounding near `+/-PI`. Angular endpoint ordering determines whether
the minimum denominator can reach zero or become negative; in that case the
positive-distance portion is `Unbounded`, with an explicit
finite lower bound and no finite upper bound. Nonpositive-denominator combinations
cannot describe the assumed positive-height geometry. Arithmetic overflow or
underflow that prevents a usable interval is `NumericallyIndeterminate`, rather
than a fabricated finite bound. Bounds are extrema evaluated in Double arithmetic,
not a formally outward-rounded interval arithmetic implementation.

Intervals are conditional on all supplied bounds and geometric assumptions.
They are not confidence intervals and do not include unspecified systematic
errors, movement, object tilt, or sensor calibration errors. No default uncertainty
or precision is invented. JVM tests in `app/src/test` cover geometry, invariance,
invalid inputs, diagnostics and deterministic uncertainty propagation.

## Build and dependencies

Dependency versions are pinned in `gradle/libs.versions.toml`; the Gradle wrapper
pins its distribution and SHA-256 checksum. Build Tools are pinned to 36.0.0.

AGP 9 supplies built-in Kotlin. The root buildscript upgrades its Kotlin Gradle
plugin to 2.4.21, matching the Compose compiler plugin. Do not apply the separate
Kotlin Android plugin alongside built-in Kotlin.

JUnit 4 is the only added test dependency. The foundation has no camera, analytics, advertising, navigation, dependency
injection, or storage libraries. Add dependencies only as features require them,
while preserving offline operation.
