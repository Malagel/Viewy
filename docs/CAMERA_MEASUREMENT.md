# Phase C: camera-based known-height measurement

## Implemented experience

The first method is available on launch: **Long Distance → Known Object Height →
Two Endpoint Captures**. Grant CAMERA permission, enter vertical endpoint height
in meters or decimal feet, aim at the top, pause, capture, then aim at the bottom,
pause and capture within 15 seconds. Height must be positive and finite; decimal
point/comma are accepted without grouping separators. No category-based height
presets or arbitrary height-error intervals are supplied. Saving a height starts
a new pair; unit conversions retain Double precision independently of display.

The result is approximate horizontal distance to the vertical line through the
object, not slant range. The object must be vertical, endpoints must share a
horizontal position, and the camera location must remain approximately fixed.
The control is gated by fresh/stable sensor observations and live camera state.
Restart recaptures the top; recapture bottom retains the top only while its frame
and 15-second time limit remain valid. Backgrounding, camera loss/reconfiguration,
configuration recreation, source/frame/mapping changes clear captures/results.
No session survives process death. No measurement persistence is implemented.

A short duplicate-tap guard prevents two quick presses advancing both steps.
Reversed, nearly coincident or otherwise unusable geometry requests correction.
The minimal Mode button explains the single available mode/method. Optional debug
A/B tools share acquisition but have separate captures; restarting their source
also invalidates measurement endpoints. All diagnostics are local/offline.

## Camera selection and preview strategy

CameraX 1.6.2 is the stable release selected for the existing API 23–37 project.
Only `Preview` is bound. No image analysis, photo capture, recording, microphone,
image storage, location, Internet or network-state permission is needed. The
transitive network-state declaration from camera-view is removed in the manifest.
CAMERA is requested at runtime; denial leaves an explanation, retry, and a link
to app settings. No rear camera, binding/initialization failures, CameraX errors
and a 12-second startup timeout yield disabled capture and a retry control.
The timeout only runs while the lifecycle is STARTED; background initialization
waits for foreground startup without reporting a spurious camera failure.

Use CameraX's first default rear camera. When it is logical (API 28+ physical ID
set nonempty), inspect its physical rear cameras, choose the largest reported
sensor area (stable camera-ID tie order), and pin that physical ID using Camera2
interop. This typically chooses a main wide imager, but is not a field-of-view or
accuracy guarantee. If a logical output cannot be pinned/inspected/bound, fail
visibly rather than silently permit automatic lens switching. On older/nonlogical
devices retain the selected camera ID. No zoom/lens-switch UI is exposed and no
zoom command is sent; a fixed physical output prevents logical zoom-based switches.
Unsupported pinned-preview configurations remain a portability limitation; test
additional devices before broad deployment.

Request a 4:3 stream with supported-resolution fallback. `PreviewView` uses
`COMPATIBLE` (TextureView) for dependable rounded clipping/overlays, and
`FILL_CENTER` to preserve aspect ratio with a symmetric centered crop. The centered
reticle therefore refers to the transformed preview's center regardless of the
view's aspect ratio, roll or image/display rotation. CameraX handles preview
image rotation. A display change, including 180° without Activity recreation,
rebinds the preview and clears the pair. Startup waits until the view is attached
before choosing display rotation. The binding/configuration stays fixed during
a pair. CameraX binds to the Activity lifecycle; disposal unbinds only this Preview
and removes observers/listeners. Capture requires both OPEN and STREAMING.

EIS/preview stabilization and OIS are requested OFF to reduce moving crop/axis
changes. These are requests, not independently verified hardware behavior. Default
autofocus/exposure remain active for ordinary lighting; no focus lock, touch-focus,
auto endpoint detection or camera extensions are enabled.

## Optical-axis mapping and evidence

Android device sensor axes do not rotate with the display. Orientation acquisition
accepts a fixed unit direction in those native axes and records a camera mapping
identity with every observation. The existing default remains device `(0,0,-1)`.

After permission, read the **selected physical camera's** Camera2 characteristics
(or the selected camera itself for a nonlogical output). For API 28+, accept only
an explicit `LENS_POSE_REFERENCE_PRIMARY_CAMERA` or `GYROSCOPE`, exactly four finite
near-unit pose quaternion components, and an inverse-transformed optical axis in
the rear device hemisphere. These references share Android sensor axes; their
translation origins differ, which is irrelevant for a direction. Missing reference
(including pre-28), UNDEFINED/default-facing, AUTOMOTIVE, unknown reference,
malformed quaternion or a contradictory forward axis selects the labeled −Z
fallback. No handset brand, hardware ID or calibration constant is hard-coded.

Camera2 defines pose rotation as **device → camera**, not camera → device.
With column vectors the implemented composition is:

```
vD = inverse(R_CD) * (0,0,1)
vW = R_WD * vD
```

`R_WD` is the existing Phase B rotation-vector quaternion into a gravity-aligned
frame. The selected camera ID, physical ID, reference and quaternion (or fallback
identity) label the mapping; changing it requires a new sensor session. Pure JVM
tests cover inverse/sign convention, unsuitable metadata, identity isolation and
application before sensor quality statistics/capture.

`SENSOR_ORIENTATION` describes image rotation; it is only diagnostic and is never
used as an optical-axis correction. Pose metadata is manufacturer-reported,
not an independently measured Viewy calibration. PRIMARY_CAMERA does not create a
shared positional origin with orientation sensors, and none is needed or assumed.

**Residual center-ray uncertainty:** the optical principal point need not equal
sensor-array center or preview center. Intrinsic calibration, if present, is
shown in debug diagnostics but is not inverted into a ray: a verified stream crop,
distortion/rectification model, focus dependence and metadata correspondence are
not established. Applying a pinhole correction from a static optional matrix alone
would imply unsupported precision. Centered scaling/cropping and fixed lens reduce
avoidable differences but do not prove optical-axis correspondence. OIS behavior,
optical misalignment, distortion, lens breathing and vendor processing can still
change the visible center ray. Validate these effects on a fixture; a future
calibration path must establish the whole pixel-to-device transform before using
intrinsics, not only move the reticle or apply image rotation.

## Orientation, timing, guidance and uncertainty

Reuse Phase B's one acquisition adapter. Game Rotation Vector is preferred,
Rotation Vector is a startup fallback only; no sensor switch occurs within a pair.
Capture recomputes freshness at the button press and records the latest actual
accepted sensor observation, preserving its `elapsedRealtimeNanos` timestamp,
source, reference UUID, mapping ID and quality. Main-thread serialization and frame
checks at capture prevent queued UI actions from mixing incompatible sessions.

There is **no exact preview/exposure synchronization**. Neither the tap time nor
the latest orientation timestamp identifies the exposure of the preview frame
the user saw. TextureView rendering, camera processing, exposure and UI scheduling
add unmeasured latency. No artificial latency constant, extrapolation, camera
exposure timestamp, RMS-derived accuracy bound or second orientation pipeline is
introduced. Practical first-version strategy: move slowly, stop for about one
second, confirm the reticle remains on the endpoint, capture while still, and
remain still briefly afterward. The Phase B 500 ms window/350 ms minimum coverage
helps reject motion but cannot bound preview lag. Low light may increase lag.

Phase B freshness (250 ms), aiming variation (0.75° maximum deviation from recent
mean), and pair duration (15 s) gates remain unchanged. Phase C adds provisional
usability parameters: bearing mismatch ≤2°, elevation separation ≥0.5°, horizontal
unit-ray norm ≥cos(85°), and a 600 ms duplicate-tap guard. These are **uncalibrated
engineering tolerances**, not accuracy requirements, probability statements or
measurement-error bounds. They can require adjustment after handheld validation.
Bearing is suppressed for guidance near vertical even before its numerical pole.

Alignment uses Phase B's bearing and wrapped-difference utilities directly,
independently of object height or whether distance geometry is valid. A positive
current-minus-top bearing means turn physically right (decrease bearing); negative
means turn left. This follows atan2(Y,X), not compass azimuth or screen X, and
works across ±180°. Roll does not change the physical meaning of left/right about
gravity. No guidance implies detection of camera translation. A user can move the
camera and still pass angular alignment and stability.

The unchanged `KnownHeightDistance` calculates the estimate and diagnostics.
Nominal-only input yields `DistanceUncertainty.Unavailable`; the production UI
states that uncertainty is unquantified. The UI can render existing domain bounded,
unbounded and indeterminate conditional results, but this version has no validated
angular bounds or bounded-input editor, so nominal captures do not generate ranges.
Sample variation measures short-term steadiness, not bias/absolute accuracy.
Display estimates with two significant digits; calculations retain full precision.
Debug diagnostics expose unrounded result, separation, bearing error, sensitivities,
measurement duration and captured endpoint quality. Sensor-only A/B timestamps
and quality are also available separately.

## Galaxy S22 manual validation procedure (pending)

Use the unchanged deployment script on an authorized device. Record device/OS,
app build, sensor source/name, camera and physical ID, pose/reference/fallback,
stream behavior and fixture/handheld setup. Open **Development diagnostics** for
local readouts. Do not change unrelated device settings or send diagnostics online.
The developer's Phase B stationary results (≈0.029° / 14.1 s game; ≈0.028° / 14.8 s
rotation vector) are promising repeatability only. No Phase C physical distances
or camera calibration have been verified during implementation.

| Scenario | Procedure and observations to record |
| --- | --- |
| Permission | Fresh permission state: deny, retry, grant; also deny permanently and use app settings. Background while prompting. Confirm preview/capture reflect access and no photos/audio/storage are requested. Revoke permission in settings and resume. |
| Preview/reticle | Use a distant high-contrast plumb line or cross target with the phone in a fixture. Check centered reticle in portrait/both landscapes/upside down, symmetric crop, no stretching, correct rear scene and rounded clipping. Rotate around a known fixed optical sight line and record center-target movement; this can expose combined axis/principal-point/fixture offsets, not isolate their causes. |
| Identity/lens | Record logical/physical IDs and mapping diagnostics. Slowly cover individual rear lenses to identify the selected imager without moving the phone. Change lighting/target distance, check no unexpected field-of-view or lens jump during a pair. Compare metadata to observed output. Do not infer calibration from a matching ID alone. |
| Pitch/roll/display | Aim up/down: elevation must rise/fall. Keep the sight direction fixed while rolling; repeat portrait, landscapes and 180° display changes. Check preview rotates correctly, alignment follows physical aim, and any reconfiguration clears captures. Natural-landscape/foldable devices require additional checks. |
| Two endpoints | Use an independently measured vertical height with distinct endpoints. Keep camera location fixed; record top then bottom. Try reversed order, same endpoint, very small angular separation, near-vertical aim and rapid duplicate taps. Expect correction instead of a misleading result. Recapture bottom, restart, edit height and switch units. |
| Alignment | After top, yaw slowly left/right then return to its vertical line. Check guidance sign and capture gating, including crossing ±180° using debug bearings. Check roll does not reverse physical guidance. Record actual bearing errors; 2° is not a calibrated accuracy requirement. |
| Handheld stability | Repeat supported/unsupported handheld captures. Observe settling/moving/ready during ordinary aiming, tremor and button presses. Stop about one second before capture. Record RMS/max variation and blocked attempts; assess usability without declaring low variation proof of accuracy. |
| Interruptions | Capture top, Home/lock/unlock, rotate the display, retry camera and restart sensor source. Cover camera privacy access/use another camera app if available through normal controls. Ensure endpoints clear, no stale capture can complete, and fresh preview/sensor settling restores the workflow. Wait past 15 s and verify restart guidance. |
| Reference distances | Choose several safely accessible distances independently measured horizontally from the camera lens position to the target's vertical line, with different object heights and positive/negative endpoint elevations. A tape/independent range instrument establishes reference; Viewy's output must not define its own reference. Use a plumb target, note height and reference-distance uncertainty separately. |
| Repetition | At each setup collect multiple complete measurements (for example ten), including independent re-aims. Report every result and rejection, signed/absolute relative error, spread and setup conditions. No arbitrary error threshold is a calibrated acceptance criterion. |
| Drift/return | Use a rigid fixture and repeat fixed-aim A/B after short intervals up to 15 s, then move away/return and compare. Repeat each sensor source separately. Also compare short vs long endpoint capture durations. A discrepancy shows combined return/fixture/sensor effects; orientation alone cannot isolate drift from real movement. |
| Lighting/focus | Repeat bright/dim/backlit targets with clear endpoint visibility. Observe autofocus changes, lens breathing, exposure delay, preview center movement and stale/moving gates. If the scene blurs, pause until clear rather than claiming captured angle corresponds to an old preview frame. |
| Layout/accessibility | Check light/dark themes, large fonts/display size, TalkBack capture labels, small screens/landscape, keyboard height dialog and system insets. Controls/results must remain reachable by scrolling. |
| Unavailable hardware | On a camera-/sensor-limited emulator or second device, verify permission explanation, missing-rear-camera and missing-orientation recovery. JVM tests do not establish Android camera/registration success. |

Suggested local worksheet (no in-app history/export is added):

```
run, source, logical_id, physical_id, mapping, height_m, reference_distance_m,
estimate_m, top_elevation_deg, bottom_elevation_deg, elevation_separation_deg,
bearing_error_deg, top_rms_max_deg, bottom_rms_max_deg, duration_s,
lighting_focus, fixture_or_handheld, rejection_or_notes
```

Repeated reference measurements can quantify observed error/spread for that setup
and reveal sensitivity to duration, lighting, roll or lens changes. They do not
establish universal error bounds. Unobserved translation, true camera-to-device
axis, principal-point offset, preview latency at each tap, absolute fused-sensor
bias and the cause of drift-like discrepancies remain unobservable from orientation
alone. External fixtures, timing instrumentation or independent calibration would
be required to isolate them. Test additional Android devices before treating S22
behavior as portable validation.

## Primary API references

- [CameraX stable releases](https://developer.android.com/jetpack/androidx/releases/camera)
- [PreviewView scale/implementation behavior](https://developer.android.com/media/camera/camerax/preview)
- [Camera2 lens pose, reference, intrinsics and image orientation](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
- [Pose reference definitions](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata)
- [Camera2 interop physical camera selection](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender)
- [PreviewView sensor-to-view transform and readiness](https://developer.android.com/reference/androidx/camera/view/PreviewView)

The documented pose/inverse rotation and centered preview behavior support the
implementation strategy; they do not constitute physical validation of any lens,
preview-center mapping or overall distance accuracy.
