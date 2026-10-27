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
conversion; preview ownership across disposal, retries and late initialization;
inactive/height-gated camera and foreground sensor eligibility and contextual
guidance; localized height-label formatting, whole/fractional values, display
precision and scientific notation; selection availability, inactive startup,
automatic initial mode presentation, mode/method navigation, Back, disabled modes,
activation, dismissal and reselection; tutorial first-use eligibility and stable-ID
preference keys, store/coordinator reconstruction, dismissal and manual reopening,
basic/detailed transitions, retained presentation, delayed sheet acknowledgement,
duplicate/stale callbacks, rapid navigation and unchanged measurement/resource
state; tutorial equation parsing, grouping, scripts and malformed-notation fallback; and acquisition observations through workflow/calculator transitions,
alignment/wraparound, interruption, correction and retry. Source and tests define
the individual cases. Mocked deployment tests verify script decisions using
fictional devices/configuration, not real wireless reliability.

## Recorded verification and its scope

The current implementation passed JVM tests,
debug assembly and debug lint together. Lint reported no errors; warnings concern
dependency updates, backup guidance and the intentional portrait request. This used installed JDK 17/API 37
SDK and existing caches; a clean or release build was not verified. Inspection of
the merged debug manifest found CAMERA plus an app-private signature permission,
with no Internet, network-state, microphone, location or storage permissions.
Deployment-script mocked unit tests and Bash syntax checks were also previously
reported passing; they do not verify a physical connection.

Deployment-script results are retained reports. Instrumentation tests are not
implemented. Tutorial persistence tests use a preference-API fake to verify keys
and reconstruction; they do not exercise Android disk writes. Pure JVM tests do not prove
Android registration, camera behavior, rendering or measurement accuracy. Navigation
rendering, sheet/tutorial Back dispatch, outside-card hit testing, animation/inset
behavior, scroll resets, launcher/control-row positions, actual Activity retention,
Android preference-file durability and legacy-preference upgrade behavior require
the manual checks below; there is no instrumentation harness. Only one method is implemented, so switching between
different real workflows cannot yet be exercised; JVM ownership tests cover
revocation of both pending and ready previews.

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
Use [README deployment instructions](../README.md#install-and-run). The header now
opens method help; diagnostic state/acquisition and its dialog implementation are
retained, but no diagnostics entry is exposed. Procedures requiring A/B diagnostics
or explicit source controls therefore need a separately authorized developer
harness or future developer access before execution. Avoid changing unrelated
device settings. Use a fixture or helper to reduce capture-button motion.
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
| Height-gated resources | On a fresh workflow, confirm a colored height-entry rectangle, no preview, no orientation session and no camera permission prompt. Cancel/invalid input must leave resources stopped. Save meters/feet to start preview; edit/save must clear captures/results. Repeat during pending initialization and with camera retry/background/resume; stale callbacks must not restore readiness after release. |
| Height-dialog keyboard | With Samsung Keyboard and another available IME, open both height entry and height editing: the field must focus and the decimal keyboard must open without tapping the field. Show/hide the keyboard repeatedly, including Back dismissal followed by sensor updates; it must stay dismissed until requested again or the dialog is reopened. The preview, header, stage and bottom controls must stay fixed; the input card remains anchored near the top. Check save/cancel and scrolling at large text sizes, small/split windows and with a hardware keyboard. Canceling height editing must preserve captures unless an actual camera/sensor interruption occurs. |
| Permission and camera recovery | Deny/retry/grant, permanent denial/settings recovery, background during prompt, and revoke/resume. Confirm disabled captures while unavailable; retry camera initialization/errors. No photos, audio or storage access should be requested. |
| Preview and physical lens | Fixture a distant plumb line/cross. Check centered reticle, symmetric crop, no stretching and rounded clipping in portrait. Verify the portrait request during rotation; exercise other orientations only where the platform overrides it. Slowly cover rear lenses without moving the phone to identify the imager; compare IDs/metadata. Vary light and target distance and watch for lens/field-of-view jumps. Matching IDs do not establish calibration. |
| Optical-center behavior | Rotate around a known fixed sight line and record target movement at the reticle. Repeat with focus/light changes. Movement combines fixture, axis, principal-point, distortion and processing effects; it does not isolate their causes. |
| Stationary sensor directions | Fix screen-up, screen-down, upright and tilted. Check fresh times, settling then READY, and actual variation. Under fallback mapping rear-down is −90° elevation and rear-up +90°; bearing may be unavailable. Do not interpret low variation as angular accuracy. |
| Pitch, roll and display | Tilt rear aim up/down: elevation should rise/fall. Roll about a fixed sight direction; ray should remain approximately constant after settling. Rotate while portrait is requested; where the platform overrides it, repeat both landscapes and 180° rotation. Check image rotation and physical signs; recreation/rebinding must clear captures. Natural-landscape/foldable devices need separate checks. |
| Bearing and alignment | Slowly yaw left/right, then return to the top endpoint's vertical line. Check guidance sign and capture gating, including crossing ±180° (small wrapped difference, not 360°). Roll must not reverse physical guidance. Interpret bearing using atan2(Y,X), not compass azimuth. |
| Endpoint geometry and controls | Use a measured plumb height; fix camera position and capture top then bottom. Try reversed/same endpoints, very small separation, near-vertical aim and duplicate taps: expect correction/blocking. Test Restart, height editing and unit changes; results should have no separate bottom-recapture action. Debug A/B lower-then-upper captures should show positive elevation change; they are separate from production endpoints. |
| Handheld readiness | Repeat supported/unsupported handheld captures with ordinary aiming, tremor and taps. Observe moving/settling/ready and blocked attempts. Near straight up/down, elevation/ray should remain finite and bearing should be suppressed; production can block before the numerical pole. Provisional gates are not calibrated acceptance thresholds. |
| Interruptions and resources | Capture top, then Home/lock/unlock, display rotation, camera retry, normal camera privacy/access interruption and sensor-source restart. Check endpoints/results clear, fresh preview/settling restores readiness and stale captures cannot finish. Repeat pause/resume and inspect for listener/lifecycle errors. Wait past 15 s before bottom and check restart guidance. |
| Delivery failure | In a disposable emulator/fault harness stop sensor delivery beyond the freshness limit, then resume. Expect STALE and blocked capture, followed by a new frame and settling. An already configured Sensors Off tile can help; configuring it is not required. JVM rules do not prove platform callback behavior. |
| Missing hardware and registration | On sensor-/camera-limited hardware or a harness, test no rear camera, game-sensor absence with rotation-vector fallback, neither sensor available, and failed registration. Forced single-source selection must not silently switch. Timestamp/registration fault injection needs a harness; use pure tests only as evidence for rejection logic. |
| Repeatability and return | Fixture fixed aim and compare debug A/B over 5/10/15 s, then move away and return within the pair limit. Repeat separately for each available sensor using new-session controls; no pair may span sources. Initial rotation-vector runs should avoid magnetic objects; record controlled magnetic disturbance separately. Differences combine return, fixture and sensor effects, not independently measured drift. |
| Reference distances and repetition | Independently measure horizontal lens-to-target-line distance with tape/range instrument at several safe distances/heights and positive/negative endpoint elevations. Collect multiple full measurements with independent re-aims (for example ten per setup), recording every result/rejection, signed/absolute relative error and spread. Compare short/long capture durations and fixture/handheld results. Viewy output must not define its own reference. |
| Lighting and focus | Repeat bright/dim/backlit scenes. Observe autofocus, lens breathing, center movement and preview delay. Wait for a clear stationary image; no claim ties the captured orientation to an earlier displayed frame. |
| Mode and method selection | Start a new process, including an upgrade with a legacy saved method ID: expect no active method and an automatically opened mode sheet. Dismiss via outside tap and Back: only the header and chevron Mode launcher should remain, without height/capture controls, camera placeholder, permission prompt or sensor acquisition. Reopen: always start at modes. Long Distance slides left to Known Object Height; in-sheet Back and Android Back slide back to modes, and Back from modes closes. Outside taps close at both levels. Short Distance, Height and Calibration remain visible and disabled. Select Known Object Height to enter its unchanged height-gated workflow. During live preview, open the sheet: confirm stationary content, gradual blur on Android 12+, dimming on all supported versions, sharp panel and uninterrupted preview. Confirm reverse effects on closing, the Mode launcher stays in place behind the modal sheet without disappearing on tap, background interaction is blocked while open, and the control row does not move. Capture a top endpoint or result, browse/dismiss/reselect promptly, and confirm preservation within existing session/expiry rules; wait past 15 s while browsing to confirm expiry still applies. Exercise rapid repeated taps, outside dismissals, Back, drag dismissal, compact/split windows, platform-overridden orientations, large text and TalkBack. Lists must scroll and all entries remain reachable. Recreate the Activity and background/resume with the selector both open and closed: retain selection/navigation while real hardware interruption still clears captures. After process death, start inactive again; saved height/status dialogs must not appear. Settings remains disabled; the old diagnostics overflow entry must be absent. |
| Method help and completion | On fresh app data, confirm no **?** on inactive startup. Activate Known Object Height: the sheet must fully finish closing before exactly one basic tutorial appears. Read all steps and limitations. Scroll down, tap **Know more**, then **Go back**: the same centered card must keep its bounds, replace content and start at the top each time. Go back must appear only in the detailed view, without an arrow, at the top left opposite Close; both remain available after scrolling. Dismiss from both pages using outside tap, Android Back and Close; each must close the entire card. Reselect and relaunch the process: no automatic repeat after dismissal, but **?** must always reopen basic content without erasing completion. Terminate before dismissing first-use help, relaunch inactive, then activate the method: basic help must appear again. Check rapid selection/Back/reopening and Activity recreation while the sheet closes, while either tutorial page is visible and after dismissal; no duplicate overlay, stale dismissal or unexpected reopening. Recreation retains the current tutorial page; ordinary hardware interruptions may still clear captures. Verify actual local preference durability across normal process restart. |
| Help layout and measurement preservation | Confirm **?** immediately left of the unchanged gear, with the existing spacing, a smaller visible circle, a 48 dp touch target and a TalkBack **How to use** label; no diagnostics or long-press action. With valid height and a live preview, open/read/dismiss manual help: preview bounds, header, stage and bottom controls must stay stationary; the Mode launcher stays composed in its usual position. Repeat with a top capture and result: preserve inputs/captures/results within normal expiry/session rules, and verify help does not restart camera/sensors. Wait beyond 15 seconds with help open after a top capture: normal expiry still applies. Test small/split windows, large fonts/display scale, TalkBack, cutouts and gesture/three-button insets; content must scroll, Close remain reachable, math remain readable and all pages use the same card/window. Confirm stacked fractions and lowered endpoint subscripts, no raw TeX commands, natural spoken equation descriptions, and horizontal equation scrolling when large text exceeds the card width. Repeat from the exceptional viewfinder workspace and its collapsed presentation. JVM state checks do not establish these rendering or physical-session properties. |
| Layout and accessibility | Check the consistent dark palette under both system themes, large font/display sizes, TalkBack labels, small/split windows, platform-overridden orientations, height-dialog keyboard and navigation insets. On the normal screen, capture/navigation and the stage/result must stay visible without scrolling. Guidance should fade near the preview edge, never cover the reticle or persist for ordinary readiness; moving/settling/alignment changes should not flash. Verify persistent recovery actions and status dialogs in compact layouts. Exceptional windows/text scaling offer **Open viewfinder** with scrollable details and fixed controls. Check centered stage/≈ results in both units, a blank reserved caption area before height entry and unchanged preview bounds as top/bottom instructions appear or switch (also with large text), closer symmetric Restart/Mode placement, the solid shutter disc, enlarged initial height action, the rounded borderless height editor and the raised control row. Check the larger circular gear placeholder remains disabled. Whole heights should display one decimal, fractional heights should retain meaningful digits up to the existing precision cap, and opening height editing should preserve the full stored input. Check the bold rounded Viewy header and reduced preview at normal/large text sizes. No separate result-retry action should appear. Diagnostics must not push Capture offscreen. |

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
