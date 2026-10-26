# Architecture

## Components and ownership

Viewy has one `app` module and one `ComponentActivity`. `MainActivity` owns
edge-to-edge setup and resume/pause forwarding. `ViewyScreen` renders Material 3
camera, height, guidance, capture and result controls; it collects state with
lifecycle awareness. Insets, bounded width and scrolling support constrained
layouts. Product behavior is defined in [Product specification](PRODUCT_SPEC.md).

Source paths below are relative to `app/src/main/java/com/malagel/viewy/`:

| Component | Responsibility |
| --- | --- |
| `domain/MeasurementCatalog.kt` | Stable mode/method identities, separate from method-specific workflows; no generic algorithm framework. |
| `domain/KnownHeightDistance.kt` | Pure Kotlin geometry, typed failures, conditioning diagnostics and conditional input-bound propagation. |
| `orientation/OrientationMath.kt` | Pure quaternion parsing/rotation, ray angles and circular differences. |
| `orientation/OrientationSession.kt` | Pure bounded recent-ray statistics, frame/time validity, captures and pair compatibility. |
| `orientation/AndroidOrientationAcquisition.kt` | Sensor discovery/registration, callback isolation, freshness publication and cleanup. |
| `camera/CameraDirectionMapping.kt` | Pure policy mapping the selected optical axis into native device axes. |
| `camera/CameraPreview.kt` | CameraX Preview selection/pinning, metadata, stream/errors and unbinding. |
| `measurement/HeightInput.kt` | Decimal validation and meters/feet conversion without presentation rounding. |
| `measurement/KnownHeightWorkflow.kt` | Immutable top/bottom/correction/result state, compatibility/alignment gates, retry and restart. |
| `ui/MeasurementViewModel.kt` | One shared foreground sensor adapter, workflow and immutable StateFlow snapshots. |
| `ui/OrientationDiagnosticsState.kt` and `ui/OrientationDiagnosticsPanel.kt` | Debug A/B captures, separate from measurement endpoints but sharing acquisition. |

Math and workflow logic are independent of Android/Compose. Sensor callbacks,
workflow actions and periodic freshness updates are serialized on main. The
adapter uses application context; there is no background sensor service.

## Measurement domain

`KnownHeightDistance.estimate` accepts positive height H in meters and two finite,
nonzero 3D rays in the same gravity-aligned frame: +Z up, X/Y horizontal. Ray
magnitude is irrelevant; pitch alone is insufficient. With elevation
`atan2(z, hypot(x,y))`, horizontal distance is:

```
distance = H / (topSlope - bottomSlope)
slope = z / hypot(x,y)
```

The object is assumed vertical, both endpoints share one horizontal position,
and the camera stays approximately stationary. This estimates horizontal distance
to that vertical line, not slant range or general 3D endpoint geometry. The domain
diagnoses bearing mismatch without rejecting it; the workflow applies usability
gates. Misaligned results remain conditional on the geometric assumptions.

Typed failures distinguish invalid height/rays/bounds, undefined horizontal
bearing, reversed endpoints, degenerate elevations and unusable distance. Exactly
vertical rays have undefined bearing; finite nonvertical rays near a pole may
still yield usable nominal geometry. Scaling and cancellation-resistant arithmetic
protect extreme inputs. A relative floating-point resolution guard distinguishes
degeneracy from reversed order; it is not a calibrated angular threshold.

Diagnostics include elevations, separation, wrapped signed **top minus bottom**
bearing and absolute first-order elevation sensitivities in meters/radian.
Sensitivity describes conditioning, not error. An overflowing derivative is null.
Bearing differences follow the short circular arc in `[-pi, pi]`; antipodal sign
is ambiguous. Near-pole bearing reliability depends on input accuracy.

Optional `InputBounds` are inclusive absolute height/elevation bounds. Each
supplied bound must be finite, ordered and contain its nominal value; height
bounds are positive and elevation bounds lie strictly inside `(-pi/2, pi/2)`.
Invalid partial bounds are rejected; valid incomplete bounds preserve the estimate
with `Unavailable` uncertainty. A usable extreme ray can have its nominal angle
round to a pole, where no permitted angle bounds can enclose it.

Complete bounds propagate by monotonic extrema. Results are `Bounded`, `Unbounded`
when allowed geometry approaches a zero positive denominator, or
`NumericallyIndeterminate` when floating-point range prevents a usable interval.
Nonpositive-denominator combinations cannot describe the assumed geometry. Bounds
use Double arithmetic, not formally outward-rounded interval arithmetic. They
are conditional on supplied bounds and assumptions, not confidence intervals;
unspecified sensor bias, object tilt, translation and calibration errors are not
included. Production supplies nominal inputs only and does not infer bounds from
stationary variation. The result renderer supports the domain's conditional
bounded, unbounded and indeterminate states, but there is no bounded-input editor.

## Coordinate systems and optical mapping

All rotations use right-handed frames, column vectors and active Hamilton
quaternions in x,y,z,w order. Quaternion sign reversal describes the same rotation.

- **Device D:** Android native +X right, +Y toward device top, +Z out of the
  screen. Display rotation does not change these axes, including on devices
  whose natural orientation is landscape.
- **Reference W:** `vW = R_WD * vD`. Rotation Vector uses East/North/Up;
  Game Rotation Vector retains +Z up with an arbitrary, potentially drifting
  horizontal reference. A game bearing is not compass heading.
- **Camera C:** +Z points from the lens into the scene. The labeled uncalibrated
  fallback is device `(0,0,-1)`, identified by
  `rear-negative-device-z-assumption-v1` within the selected camera identity.
- **Display/image:** image rotation changes image axes, not the physical center
  aiming direction. `SENSOR_ORIENTATION` is image rotation, not optical pose.

Camera2 pose rotation maps device to camera. For suitable selected-camera metadata:

```
vD = inverse(R_CD) * (0,0,1)
vW = R_WD * vD
```

For a rotation matrix the inverse is its transpose; composition order matters.
Only explicit PRIMARY_CAMERA or GYROSCOPE pose references on API 28+ are accepted,
with four finite near-unit quaternion components and a rear-hemisphere axis.
These references share sensor axes but differ in translation origin, irrelevant
for a direction. Missing/pre-28, undefined, automotive, unknown, malformed or
contradictory metadata uses the labeled fallback. Mapping identity incorporates
selected logical/physical camera, pose reference and quaternion or fallback;
a mapping change starts a new sensor session.

With fallback mapping, identity orientation aims down (−WZ); +90° device-X
rotation aims +WY, and a +90° WZ turn moves +WY toward −WX. Local device-Z roll
preserves that assumed ray. Elevation is `atan2(z, hypot(x,y))`; bearing is
`atan2(y,x)`, positive from +X toward +Y, not compass azimuth. Bearing is suppressed
near vertical while elevation/ray remain available. No Euler averaging or
pitch-only reconstruction is used.

Reported lens pose is not independent calibration. Preview center need not equal
the optical principal point. Intrinsics are diagnostic only: verified stream crop,
distortion/rectification, focus dependence and metadata correspondence are absent.
A valid future pixel-to-device transform must establish all of these together;
applying a static pinhole correction alone would imply unsupported precision.
Optical misalignment, stabilization, lens breathing and vendor processing can
still affect the visible center ray.

## Sensor sessions and observation compatibility

Game Rotation Vector is preferred for short relative comparisons: it avoids
magnetic corrections but permits heading drift. Rotation Vector is a startup
fallback when game is absent or registration fails; it can respond to magnetic
interference. There is no custom fusion or raw gyro integration. A running sensor
never silently switches on stalls/unreliability. Explicit debug source selection
or retry starts a new session and clears captures.

The adapter checks registration and reports unavailable sources/failure. Sampling
is a platform hint with no batching; short callbacks validate data, retained
samples are rate-limited in a bounded time/count window, and periodic publication
updates freshness even without events. Android three-/four-/five-element vectors
are supported: optional w is reconstructed nonnegative for three elements; the
fifth heading-accuracy field is not quaternion data or an inferred error bound.
Malformed/nonfinite rotation components and gross norm errors are rejected; small
platform roundoff is normalized.

Each observation retains a unit ray, original sensor timestamp on the
`elapsedRealtimeNanos` time base, source, reference UUID, mapping ID and quality.
Pairs require identical source/frame/mapping, strictly increasing sensor times
and a bounded elapsed interval. Wall-clock or UI receipt time never replaces
sensor time. Invalid/non-increasing/future/old events invalidate the frame;
long sample gaps, abrupt full-quaternion steps, platform discontinuity flags
when available, and transitions into/out of unreliable status also reset it.
Abrupt-motion detection cannot distinguish fast legitimate movement from reset.
No observation may span registrations, even if a platform reference persists.
Queued callbacks carry a registration token to prevent contamination of a new session.

Quality includes age, time coverage, sample count, directional RMS/max variation,
latest full-orientation step and raw platform accuracy. Unknown accuracy is not
promised confidence. Sphere-based directional statistics measure aiming steadiness;
near-cancelling rays cannot form a usable mean. Gradual roll about the aiming axis
can remain steady, while abrupt full-device rotation resets the frame. Capture
records the latest actual accepted ray, not the mean, and recomputes freshness
at the button press. The first sample in a new frame has no previous-step diagnostic.

`AcquisitionPolicy` and `MeasurementPolicy` in source define provisional operational
gates for freshness, settling, motion, pair duration, alignment, near-vertical aim,
separation and duplicate taps. These are not calibrated error or confidence bounds.
Slow drift or magnetic corrections can pass steadiness checks. Orientation cannot
detect translation or distinguish real rotation from reference drift.

## Camera, workflow and lifecycle

CameraX binds only Preview. The first default rear camera is selected; for a
logical output, readable physical rear candidates are ordered deterministically
and the one with largest reported sensor area is pinned through Camera2 interop.
This is not a field-of-view/accuracy guarantee. If inspection/pinning/binding fails,
report failure rather than silently allow lens switching. Nonlogical/older outputs
retain the selected camera ID. There is no zoom command or lens-switch UI.

A 4:3 stream is requested with supported-resolution fallback. PreviewView uses
COMPATIBLE/TextureView for rounded clipping/overlays and FILL_CENTER for
aspect-preserving centered crop. CameraX handles image rotation. Display changes,
including 180° without recreation, rebind and invalidate captures. EIS/preview
stabilization and OIS are requested off; autofocus/exposure remain at defaults.
Requests do not prove hardware behavior. There is no image analysis, photo capture,
recording, focus lock, automatic endpoint detection or camera extension.

Capture requires OPEN plus STREAMING and resumed Activity. Permission is rechecked
on resume; denial and camera errors expose recovery. Startup timeout runs only
while STARTED so background initialization does not spuriously fail. Compose owns
the preview through DisposableEffect; CameraX owns lifecycle start/stop. Disposal
removes observers/display listeners and unbinds only this Preview.

The ViewModel enables its single sensor adapter only while resumed and camera
ready. Pause, camera loss/reconfiguration, recreation or source/frame/mapping
changes invalidate endpoints/results. Pause removes the sensor listener/freshness
timer; ViewModel cleanup releases acquisition. No session survives process death.
Debug A/B captures share this pipeline without sharing measurement endpoints.

The workflow owns top/bottom/correction/result transitions, finite height,
compatibility checks and retry. Saving height restarts; bottom recapture reuses
top only while frame/time validity permits. Alignment works independently of height
or distance geometry: positive **current minus top** wrapped bearing means turn
physically right (decrease bearing), negative means left. Screen orientation and
roll do not change that gravity-relative meaning. Gates cannot establish fixed
camera position.

There is no preview-exposure/sensor synchronization: neither tap time nor latest
sensor timestamp identifies the displayed frame's exposure. Rendering, processing,
exposure and scheduling add unmeasured latency, potentially worse in low light.
No constant latency correction or extrapolation is applied. Settling before and
remaining still after capture is a mitigation, not a latency bound.

## Build contracts

Versions live in `gradle/libs.versions.toml` and Gradle build files; the wrapper
pins its distribution and checksum. AGP supplies built-in Kotlin, upgraded by the
root buildscript to match the Compose compiler; do not also apply Kotlin Android.
JUnit 4 supports JVM tests. Camera2 interop opt-in stays in the camera adapter.
The manifest requests CAMERA and removes the transitive network-state permission;
there are no Internet, microphone, location or storage permissions. No navigation,
dependency-injection or persistence framework is implemented.

See [README](../README.md) for setup and [Validation](VALIDATION.md) for test
coverage, recorded evidence and outstanding physical checks.

Platform references for the frame and preview contracts:
[Android rotation-vector sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion),
[sensor coordinates/lifecycle](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview),
[Camera2 lens characteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics),
and [CameraX PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView).
These API contracts do not validate any physical lens or measurement accuracy.
