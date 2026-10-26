# Validation

## Reproducible automated checks

From the repository root with the [README toolchain](../README.md#development-setup),
run relevant Android checks in one bounded invocation:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

For deployment-script changes, use checks that mock ADB and Gradle:

```sh
bash -n scripts/deploy.sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_deploy.py'
```

For documentation changes, check links/references and run `git diff --check`;
Android builds are unnecessary. Follow [AGENTS.md](../AGENTS.md) for resource and
device restrictions. Run only checks relevant to the change; recorded results below do not imply
that these commands have been rerun on every revision.

JVM coverage in `app/src/test` exercises geometry/invariance, typed failures,
conditioning and conditional bounds; quaternion signs/order/formats and poles;
spherical steadiness statistics, timestamps, discontinuities, reliability and
source/frame/mapping isolation; inverse camera-pose mapping and fallbacks; height
conversion; and acquisition observations through workflow/calculator transitions,
alignment/wraparound, interruption, correction and retry. Source and tests define
the individual cases. Mocked deployment tests verify script decisions using
fictional devices/configuration, not real wireless reliability.

## Recorded verification and its scope

The recorded verification for the current implementation passed JVM tests,
debug assembly and debug lint together. Lint reported no errors and five warnings
about dependency updates and backup guidance. This used installed JDK 17/API 37
SDK and existing caches; a clean or release build was not verified. Inspection of
the merged debug manifest found CAMERA plus an app-private signature permission,
with no Internet, network-state, microphone, location or storage permissions.
Deployment-script mocked unit tests and Bash syntax checks were also previously
reported passing; they do not verify a physical connection.

These are retained repository verification reports, not independently rerun
results. Instrumentation tests are not implemented. Pure JVM tests do not prove
Android registration, camera behavior, rendering or measurement accuracy.

### Developer-reported physical observations

- On a Samsung Galaxy S22, fixed-aim separation was approximately 0.029° over
  14.1 s with Game Rotation Vector and 0.028° over 14.8 s with Rotation Vector.
  These are preliminary repeatability observations, not absolute angular
  accuracy, optical calibration or uncertainty bounds.
- One preliminary Galaxy S22 trial used an object approximately 1.2 m tall at a
  reference distance of approximately 3.05 m; the displayed estimate was 3.1 m.
  This single developer-reported trial is not an independently controlled study
  or a calibrated accuracy guarantee. Repeated measurements, setup details and
  reference uncertainties have not been established.

## Essential manual hardware procedures

These procedures remain for developer execution on explicitly authorized devices.
Use [README deployment instructions](../README.md#install-and-run); debug builds
expose **Development diagnostics** below the measurement controls. Avoid changing
unrelated device settings. Use a fixture or helper to reduce capture-button motion.
Stop for about one second before capturing and remain still briefly afterward.
For sensor-only checks, use an external sight line parallel to the rear axis;
the diagnostics panel alone cannot establish optical calibration.

Record device/OS and app build, active sensor name/source, logical/physical camera
IDs, pose/reference or fallback, fixture/handheld setup, lighting/focus and preview
behavior. For each run record target height and horizontal reference distance with
their uncertainties, displayed and unrounded estimate, endpoint elevations,
separation, bearing error, captured RMS/max variation, duration, and all rejections.
Keep records externally; the app has no history/export feature.

| Area | Procedure and expected behavior to check |
| --- | --- |
| Permission and camera recovery | Deny/retry/grant, permanent denial/settings recovery, background during prompt, and revoke/resume. Confirm disabled captures while unavailable; retry camera initialization/errors. No photos, audio or storage access should be requested. |
| Preview and physical lens | Fixture a distant plumb line/cross. Check centered reticle, symmetric crop, no stretching and rounded clipping in portrait, both landscapes and upside down. Slowly cover rear lenses without moving the phone to identify the imager; compare IDs/metadata. Vary light and target distance and watch for lens/field-of-view jumps. Matching IDs do not establish calibration. |
| Optical-center behavior | Rotate around a known fixed sight line and record target movement at the reticle. Repeat with focus/light changes. Movement combines fixture, axis, principal-point, distortion and processing effects; it does not isolate their causes. |
| Stationary sensor directions | Fix screen-up, screen-down, upright and tilted. Check fresh times, settling then READY, and actual variation. Under fallback mapping rear-down is −90° elevation and rear-up +90°; bearing may be unavailable. Do not interpret low variation as angular accuracy. |
| Pitch, roll and display | Tilt rear aim up/down: elevation should rise/fall. Roll about a fixed sight direction; ray should remain approximately constant after settling. Repeat portrait, both landscapes and 180° rotation. Check image rotation and physical signs; recreation/rebinding must clear captures. Natural-landscape/foldable devices need separate checks. |
| Bearing and alignment | Slowly yaw left/right, then return to the top endpoint's vertical line. Check guidance sign and capture gating, including crossing ±180° (small wrapped difference, not 360°). Roll must not reverse physical guidance. Interpret bearing using atan2(Y,X), not compass azimuth. |
| Endpoint geometry and controls | Use a measured plumb height; fix camera position and capture top then bottom. Try reversed/same endpoints, very small separation, near-vertical aim and duplicate taps: expect correction/blocking. Test bottom recapture, restart, height editing and unit changes. Debug A/B lower-then-upper captures should show positive elevation change; they are separate from production endpoints. |
| Handheld readiness | Repeat supported/unsupported handheld captures with ordinary aiming, tremor and taps. Observe moving/settling/ready and blocked attempts. Near straight up/down, elevation/ray should remain finite and bearing should be suppressed; production can block before the numerical pole. Provisional gates are not calibrated acceptance thresholds. |
| Interruptions and resources | Capture top, then Home/lock/unlock, display rotation, camera retry, normal camera privacy/access interruption and sensor-source restart. Check endpoints/results clear, fresh preview/settling restores readiness and stale captures cannot finish. Repeat pause/resume and inspect for listener/lifecycle errors. Wait past 15 s before bottom and check restart guidance. |
| Delivery failure | In a disposable emulator/fault harness stop sensor delivery beyond the freshness limit, then resume. Expect STALE and blocked capture, followed by a new frame and settling. An already configured Sensors Off tile can help; configuring it is not required. JVM rules do not prove platform callback behavior. |
| Missing hardware and registration | On sensor-/camera-limited hardware or a harness, test no rear camera, game-sensor absence with rotation-vector fallback, neither sensor available, and failed registration. Forced single-source selection must not silently switch. Timestamp/registration fault injection needs a harness; use pure tests only as evidence for rejection logic. |
| Repeatability and return | Fixture fixed aim and compare debug A/B over 5/10/15 s, then move away and return within the pair limit. Repeat separately for each available sensor using new-session controls; no pair may span sources. Initial rotation-vector runs should avoid magnetic objects; record controlled magnetic disturbance separately. Differences combine return, fixture and sensor effects, not independently measured drift. |
| Reference distances and repetition | Independently measure horizontal lens-to-target-line distance with tape/range instrument at several safe distances/heights and positive/negative endpoint elevations. Collect multiple full measurements with independent re-aims (for example ten per setup), recording every result/rejection, signed/absolute relative error and spread. Compare short/long capture durations and fixture/handheld results. Viewy output must not define its own reference. |
| Lighting and focus | Repeat bright/dim/backlit scenes. Observe autofocus, lens breathing, center movement and preview delay. Wait for a clear stationary image; no claim ties the captured orientation to an earlier displayed frame. |
| Layout and accessibility | Check light/dark themes, large font/display sizes, TalkBack capture labels, small screens/landscape, height-dialog keyboard and system insets. Controls/results must remain reachable by scrolling. |

## Outstanding reliability limits

The reported trial does not complete the manual matrix. Outstanding verification
includes real camera initialization/pinning, selected-lens metadata, actual
stabilization/autofocus behavior, permissions, lifecycle cleanup, UI/accessibility,
handheld usefulness of provisional gates, repeated reference measurements and
portability beyond the Galaxy S22 (including unsupported pinned previews).
Wireless reconnection has had mixed developer-reported reliability; mocks do not
resolve that limitation.

There is no calibrated preview-center mapping or exposure-to-sensor timing bound.
Orientation alone cannot observe translation, absolute fused-sensor bias, the true
camera/device axis, principal-point offset, per-tap latency or causes of drift-like
discrepancies. Independent fixtures, timing instrumentation or calibration are
needed to isolate them. Repeated runs can quantify observed error/spread for a
particular setup, not universal error bounds. Low short-window variation and the
pair time limit do not bound drift or distance error. Current nominal results
therefore retain unquantified uncertainty; see the
[technical contracts](ARCHITECTURE.md).
