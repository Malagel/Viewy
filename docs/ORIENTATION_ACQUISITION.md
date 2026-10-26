# Phase B: orientation acquisition

Phase B is implemented as a reusable foreground sensor adapter plus a debug-only
panel appended below the existing placeholder controls. No camera preview,
permission request, height selection, distance workflow, storage, or networking
is introduced. Release builds retain the foundation screen and do not start the
orientation listener. Phase A domain code is unchanged.

## Sources and Android API decisions

`AndroidOrientationAcquisition` requests `TYPE_GAME_ROTATION_VECTOR` first.
This platform-fused orientation suits short relative endpoint comparisons: it
avoids magnetic-field corrections but permits heading drift. Its horizontal Y
reference is arbitrary, not north. `TYPE_ROTATION_VECTOR` is a startup fallback
when game rotation is absent or registration fails. It has a magnetic reference
and can change with magnetic interference/corrections; it is not interchangeable
with game rotation in an existing measurement. No raw gyro integration or custom
fusion is used.

Source selection and registration happen before a session begins. Both source
availability and the active sensor name are visible. Failed registration is
reported; if no candidate succeeds, acquisition stays unavailable. Once running,
a stalled or unreliable sensor never triggers an automatic source switch. Debug
buttons can explicitly begin a new session with either available source, or retry
the preferred/fallback strategy. These actions clear the capture pair. Comparison
between separate source sessions is deliberately not supported: repeat the same
physical procedure separately and record the results externally.

Sampling requests 40,000 microseconds (25 Hz), with zero batch latency, on the
main handler. This is a hint, not a guaranteed frequency. Short bounded callbacks
parse quaternions and append observations; presentation snapshots update every
100 ms, including when no events arrive. No high-rate permission is needed. The
buffer retains at most 64 readings and at most 500 ms of sensor time. Every
event is validated, while retained/transformed samples are spaced at least 10 ms
apart (at most 100 Hz). This preserves window coverage when delivery exceeds the
requested rate, instead of filling the count cap with a very short burst.

The adapter accepts Android's three-, four-, and five-element rotation vectors.
The fourth element is scalar w; the fifth, if present, is heading accuracy rather
than rotation data and is not used to invent error bounds. Three-element vectors
reconstruct the nonnegative w. Nonfinite components, malformed lengths, impossible
vector parts, and grossly non-unit quaternions are rejected. A 1% norm guard and
1e-6 squared-vector tolerance allow normalization of platform floating-point
roundoff, not arbitrary inputs.

## Frames, signs, and composition

All frames use right-handed coordinates and column vectors:

1. **Device sensor frame D:** Android's native device axes: +X to the device's
   right, +Y toward its top, +Z out of the screen. These axes do not change when
   the display rotates. Natural landscape devices still use native sensor axes.
2. **Orientation reference W:** the rotation quaternion describes `vW = R_WD vD`.
   Its rotation matrix is the active Hamilton quaternion matrix used by
   `SensorManager.getRotationMatrixFromVector`. W has +Z up. Rotation vector uses
   East/North/Up; game rotation uses an arbitrary, potentially drifting horizontal
   reference while retaining gravity alignment. Viewy does not relabel its
   game-bearing readout as a compass heading.
3. **Camera frame C:** +Z is the optical axis, from the lens into the scene.
   Phase B assumes a conventional rear camera looking along `(0, 0, -1)` in D.
   This is an **uncalibrated physical approximation**, identified in every capture
   by `rear-negative-device-z-assumption-v1`. It does not imply a particular lens
   has been selected or its axis has been measured.
4. **Display/image frame:** rotating the display or image around the optical axis
   changes image axes, not the physical center aiming direction. No display
   remapping is applied to this device-space ray. `SENSOR_ORIENTATION` specifies
   output-image rotation, not camera-to-device optical-axis alignment.
5. **Phase A input:** `direction = R_WD * (0, 0, -1)` supplies its unchanged
   `ViewingDirection` type. Both captures must have the same session reference.
   With identity rotation the rear camera looks down (−WZ). A +90° device-X
   rotation aims along +WY. Pitch above that increases elevation; a +90° WZ turn
   moves +WY toward −WX. Rotation about local device Z preserves the assumed ray.

Elevation is `atan2(z, hypot(x,y))`; bearing is `atan2(y,x)`, positive from +X
toward +Y, **not compass azimuth**. Signed bearing differences use the short
circular arc in `[-pi,pi]`; antipodal sign is ambiguous. Heading is suppressed
when the unit ray's horizontal norm is below 1e-4, a provisional numerical/display
guard rather than calibrated heading reliability. Elevation and the ray remain
available near vertical. Phase A independently handles its geometry failures.
No Euler-angle averaging or pitch-only direction reconstruction is used.

## Camera metadata assessment and Phase C handoff

Android Camera2 documents `LENS_POSE_ROTATION` as a quaternion from device sensor
coordinates into camera coordinates. If suitable metadata is later available,
its optical ray is `vD = transpose(R_CD) * (0,0,1)`, so the complete composition
is `vW = R_WD * transpose(R_CD) * (0,0,1)`. Matrix order and the inverse matter.
JVM tests cover these rotation conventions and the implemented negative-Z mapping.

Pose rotation and pose reference are permission-protected, optional metadata;
`LENS_POSE_REFERENCE` is available from API 28. `UNDEFINED` can accompany default
facing values rather than an accurate pose. PRIMARY_CAMERA and GYROSCOPE change
the translation origin while sharing device sensor axes; translation does not
change a direction. An automotive frame or unknown pose convention must not be
assumed to be a handset calibration.

Phase B deliberately declares no camera permission and opens no camera, so no
lens pose was obtained from a Galaxy S22. It uses the explicit fallback above
on every device. There is no automatic metadata-based correction to validate yet.
Phase C must select the actual camera/physical lens, inspect its metadata after
permission, verify the convention and calibration on hardware, and either retain
the labeled assumption or install a validated axis mapping under a different
mapping identity/new session. Missing, malformed, undefined, or unsuitable pose
metadata must retain a labeled fallback rather than claim exact calibration.

The preview's center may differ from the calibrated optical principal point.
Phase C must account for crop, intrinsics/distortion if needed, stabilization,
zoom/lens switching, and reticle-to-ray alignment. Captured timestamps are
orientation sample times, not synchronized camera exposure times. These issues
are not solved by applying image rotation or by adding CameraX to Phase B.

## Acquisition boundary, lifecycle, and time

The pure Kotlin `OrientationSession` accepts platform-independent rotation data
and exposes latest observations, quality, and typed capture rejection.
`OrientationObservation` contains a gravity-aligned unit ray, original monotonic
sensor timestamp, source, reference-frame UUID, camera mapping identity, and the
quality snapshot at capture. Downstream methods can pass its ray to Phase A and
use `compareObservations` to reject incompatible frames/time gaps first.

Sensor timestamps use the `elapsedRealtimeNanos` time base. They are preserved,
not replaced with UI receipt time or wall-clock time. Invalid, non-increasing,
future, or excessively old events invalidate the frame. A gap over 250 ms or a
full-quaternion step over 30° also starts a fresh frame and requires settling.
The latter is a conservative abrupt-motion/discontinuity indicator; it cannot
distinguish legitimate fast movement from a sensor reset. Ordinary pitch/yaw
changes below this per-event guard retain the frame and settle normally.

A fresh UUID prevents reuse after adapter restart, source change, interruption,
malformed input, timestamp fault, or reliability loss/recovery. On API 33+, an
explicit platform discontinuity flag also invalidates the frame if reported
(orientation sensor types generally do not provide this flag). No comparison is
made across registrations even if the platform might retain its reference.
Accuracy code 0 (`SENSOR_STATUS_UNRELIABLE`) blocks capture. Other platform codes
are displayed as raw diagnostics; unknown accuracy is not a promised confidence
level. Transition into or out of unreliable state clears the window/frame.

The debug ViewModel holds immutable StateFlow snapshots. Compose collects them
with lifecycle awareness. Activity resume registers; pause unregisters, removes
the freshness timer, invalidates captures, and stops acquisition. Configuration
recreation also starts a new session, even if it was caused by display rotation.
Queued events carry a registration token and cannot populate a later session.
The ViewModel releases the adapter when cleared. Acquisition uses application
context only; there is no activity retention or background sensor service.

## Quality, capture policy, and drift limitations

Quality exposes sample count/time coverage, latest age, directional RMS/max
angular variation, latest full-orientation step, platform accuracy code, and
capture status. Directional statistics normalize the vector sum on the unit
sphere and measure geodesic angles to that mean. Near-cancelling sums have no
usable mean and cannot pass stability. Statistics evaluate **aiming direction**:
a gradual pure roll does not create aiming instability, while abrupt full-device
rotation still triggers the discontinuity guard. Averages never replace the
captured ray; capture records the latest actual accepted sensor observation.

Provisional operational policy, defined separately in `AcquisitionPolicy`:

| Check | Default |
| --- | --- |
| Recent window / hard count cap | 500 ms / 64 |
| Minimum coverage / count | 350 ms / 8 |
| Maximum event age | 250 ms |
| Maximum consecutive event gap | 250 ms |
| Maximum deviation from recent mean | 0.75° |
| Abrupt full-orientation step | over 30° |
| Maximum A-to-B sensor time gap | 15 s |

Statuses distinguish waiting, stale, unreliable, settling, moving, and ready.
These are engineering gates, **not angular accuracy, confidence bounds, or
validated distance precision**. The 15 s pair limit merely limits exposure to
drift; it does not bound drift. Source, frame, and mapping must match; B's sensor
time must be strictly later than A's. Failed comparison returns a typed reason.
Recording A again clears B. Freshness is rechecked at the button press, not just
when the last UI snapshot was displayed.

The A/B tool shows measured direction separation, signed elevation change,
wrapped bearing change, and elapsed sensor time. Returning to a mechanically
fixed aim and observing a discrepancy provides evidence to investigate possible
drift/relocalization; the readout is not an independent drift estimator. Slow
heading drift can pass short-window stability. Magnetic corrections can also be
smooth. Orientation alone cannot distinguish these from real rotation and cannot
detect translation or guarantee the camera stayed stationary. No accelerometer
position tracking, stationary-camera claim, or default uncertainty is introduced.

## Galaxy S22 manual verification (not yet executed)

Build/install with the existing `./scripts/deploy.sh` workflow on an authorized
Galaxy S22. Scroll below the disabled production controls to the **Phase B**
panel. There is no live preview: use a rigid fixture with an external sight line
parallel to the rear camera axis; do not infer optical calibration from this
panel. Record device model, Android version, active sensor name/source, mounting
method, frame, times, RMS/max, and A/B differences externally. Avoid changing
unrelated device settings. A second person or stable fixture helps press capture
without moving the phone. Allow about one second to settle before each capture.

| Scenario | Procedure and expected behavior to check |
| --- | --- |
| Stationary orientations | Fix the phone flat screen-up, screen-down, upright and tilted. Check fresh times, low variation and READY after settling. Flat rear-down is −90° elevation; rear-up is +90°. Bearing may be unavailable. Report actual variation rather than asserting a sensor-accuracy pass. |
| Pitch signs | From upright aim, slowly tilt the rear axis upward/downward. Elevation should rise/fall. Record A then B within 15 s and verify the sign of B−A. |
| Roll invariance | Rotate around the rear axis while keeping its physical aim fixed (portrait, landscape, upside down). The ray should stay approximately constant after settling. If configuration recreation occurs, verify captures clear; establish a new A/B pair afterward. |
| Left/right turns | At a level fixed position, yaw slowly. Check continuous circular bearing and signed A/B change. Bearing uses atan2(Y,X); relate signs to the displayed axes, not a compass. |
| Return / possible drift | Fix aim, record A, move away and return within 15 s, then B. Repeat without moving at all for 5/10/15 s intervals. Log discrepancies separately for each source. A discrepancy cannot establish its cause. |
| Wraparound | Slowly cross +180°/−180° bearing. Capture on either side while stationary. The comparison should be a small signed angle rather than approximately 360°. |
| Endpoints | Use two points on a plumb line at one horizontal position. Fix the camera location, record lower A and upper B. Elevation change should be positive; wrapped bearing change should be small. No distance is computed by this UI. |
| Rapid motion / settling | Quickly move the phone. Expect MOVING/SETTLING or a new frame with ABRUPT_ROTATION; prior A/B must clear on a frame change. Stop and verify readiness recovers only after sufficient new samples. |
| Temporary interruption | In a disposable emulator/sensor test environment, stop delivery for >250 ms, then resume. Check age grows to STALE, capture is blocked, and resumed events start a new frame. If the S22 already has a Sensors Off tile configured, it can serve the same check; configuring it is not required. |
| Background / resume | Record A; Home/lock the device, then resume/unlock. Check captures clear, new frame ID, fresh sample times, settling before READY. Repeat several times; inspect logs for listener/lifecycle errors. |
| Display/device rotation | With normal auto-rotation behavior, repeat portrait and both landscapes. Coordinate signs must follow physical aim, not screen rotation. Recreation must clear the pair; no pair may span a restart. Also check dark/light theme, font scale and scroll accessibility. |
| Unsupported sensor / registration failure | On a sensor-limited emulator/device, check missing game falls back to rotation vector; neither sensor yields unavailable state and disabled captures. Forced single-source selection never silently switches. Registration failure and timestamp faults require a platform test harness or fault injection; JVM tests cover the pure rejection rules, not Android registration success. |
| Near vertical | Approach straight up/down from several bearings. Elevation/ray stay finite; bearing becomes unavailable near the pole. A/B bearing delta is unavailable when either ray is near vertical. Do not interpret unstable near-pole bearing as a calibrated accuracy result. |

Use the explicit new-session source buttons to repeat the checks separately for
game and rotation vector, if available. Keep magnetic objects away for the initial
rotation-vector run; a controlled nearby magnetic disturbance can be recorded as
an additional comparison, without treating absence of visible effects as proof
of immunity. No fragile live-sensor assertions are automated.

## Automated coverage and references

JVM tests exercise known-axis rotations, multiplication order/signs, local-roll
invariance, quaternion sign equivalence, vector formats/validation, poles,
wraparound, sphere-based window statistics, timestamps/freshness, gaps,
malformed data, discontinuities, unreliable status/recovery, bounded buffering,
frame/source/mapping identity, pair time limits, and feeding transformed rays
into the unchanged Phase A calculator. Android listener registration and actual
Galaxy S22 behavior require device verification.

Results and hardware availability are recorded in [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md).

Primary Android guidance reviewed for Phase B:

- [Position sensors: game rotation vector and drift](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)
- [Motion sensors: rotation vectors and reference axes](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- [SensorManager: quaternion/matrix convention, registration and sampling](https://developer.android.com/reference/android/hardware/SensorManager)
- [SensorEvent: data formats and monotonic timestamp base](https://developer.android.com/reference/android/hardware/SensorEvent)
- [Sensor coordinates and lifecycle practices](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- [CameraCharacteristics: lens pose/reference and image orientation](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
