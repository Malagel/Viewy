# Architecture

## Components and ownership

Viewy has one `app` module and one `AppCompatActivity`. `MainActivity` owns
edge-to-edge setup and resume/pause forwarding. `ViewyScreen` renders Material 3
camera, height, guidance, capture and result controls; it collects state with
lifecycle awareness. Insets, bounded width and a weighted preview reserve space
for capture/navigation without scrolling. Constrained status and exceptional
viewfinder dialogs preserve readable content; the latter keeps capture/navigation
fixed while details scroll. Stage instructions span the preview width and are
centered before completion; completed results occupy the rounded preview container. The stage area reserves the larger measured
height of the top/bottom captions even when empty, accounting for font size and
wrapping without adding hidden text or changing the weighted preview's size. Restart and Home share a circular action component in a centered,
bounded-width control row around the solid shutter disc. A spacious header and
bundled Nunito Bold wordmark reserve more room above the flexible preview. The
control row reserves space above the navigation inset. The manifest requests
portrait for the Activity; large-screen Android policies may override this. Product behavior is defined in
[Product specification](PRODUCT_SPEC.md).

The complete [Material 3 baseline dark scheme](https://m3.material.io/styles/color/the-color-system/key-colors-tones)
supplies all Compose color roles;
platform launch colors and system-bar icon styles match it. The Activity uses
`adjustNothing` and measurement content excludes IME padding. Height input owns
an edge-to-edge dialog with scrollable fields and fixed save/cancel actions, so
keyboard presentation does not resize or pan the preview. `HeightDialogPlacement`
records the largest current/target keyboard bottom inset during initial opening.
Reveal waits for positive inset reports to settle briefly, with a one-second
fallback for missing/floating/hardware keyboards, then freezes the observed peak.
The card is bottom-aligned 20 dp above that frozen keyboard boundary. No live IME
padding affects its visible position: hiding, reopening or resizing the keyboard
leaves the card at the same position until dismissal. A new opening measures
again; no keyboard height persists across openings.

The field stays composed/focusable with zero opacity while placement is pending.
One dialog-owned animation effect drives graphics-layer opacity after the frozen
layout is applied: entry is 175 ms and exit is 170 ms, with linear exit opacity. There
are no position or size transitions. Save, Cancel, outside tap and dialog Back
record one pending close action. This cancels entry and fades out from the current
opacity, retaining the modal window and frozen position until exit finishes.
Closing explicitly requests keyboard hiding while the decimal field remains
focused and attached. After the fade, it waits for both hidden IME visibility and
zero animated bottom inset, with a 750 ms bound for missing/unreliable IME reports.
It then clears focus and allows one frame of input cleanup before the caller
removes the dialog or applies the captured height. This avoids disposing a numeric
editor while its keyboard is still visibly closing; the ordering addresses a
possible input-connection restart flash, whose actual device/IME behavior still
requires manual verification.
The pending action rejects duplicate requests and cancels reveal/focus work.
Closing before the card is visible skips the opacity animation but still performs
keyboard/focus cleanup. Hidden/closing content is excluded from accessibility and its
save/cancel controls are disabled. On constrained displays the fields scroll while
actions remain available. Each opening requests decimal-field focus and keyboard
once after the dialog gains focus, without reopening a deliberately dismissed
keyboard during sensor-state recompositions. Actual Activity/window destruction
can still dispose a dialog immediately; pending animation work is cancelled.

`HomeScreen` presents the existing header without contextual Help and a centered,
480 dp maximum-width, 28 dp rounded selection card. The card has stable bounded
height and independently scrollable mode/method lists. Destination-keyed Compose
`AnimatedContent` slides forward left and backward right over 220 ms. Moving and
outgoing lists cannot receive input; repeated Back is consumed during motion.
The destination host owns Android Back navigation for both the card and measurement;
the card only consumes repeated Back while its own transition is running. Measurement maps its Home presentation
to the mode list rather than retaining the method submenu.

`MeasurementDestinationHost` animates Home and the entire measurement presentation
with complementary fades and 20 dp vertical movement (240 ms into measurement,
220 ms returning Home). The key is measurement-destination identity, independent
of sensor and preference publications. Compose duration scaling handles reduced
or disabled motion. Recreated hosts initialize settled. Input guards cover moving
and outgoing destinations. Hardware eligibility changes synchronously with logical
navigation, never with retained animation content. An open exceptional workspace
shares the destination transition in its own window, with a matching inaccessible
Activity backdrop; returning Home completes before that workspace window is removed.

Method help uses one centered edge-to-edge Compose Dialog with a bounded-width,
fixed-height Material 3 card constrained by system bars, cutouts and available
window size. Close stays accessible above the vertically scrollable content;
the detailed page also has Go back at the opposite end of that fixed control row.
Basic and detailed pages share the same card bounds and modal window. Outside
card taps, dialog Back and Close all dispatch a whole-card dismissal. Page-keyed
scroll state resets on content changes, while sensor-state publication leaves it
alone. The measurement surface remains composed in place; pending/visible help
suspends acquisition through the shared resource policy.

Tutorial content distinguishes resource-backed prose from display equations.
A small, pure TeX-style notation parser feeds native Compose fraction and
subscript/superscript layout; this supports authored equations without a WebView,
network access or rendering dependency. Unsupported/malformed notation falls
back to its source. Each equation has a localized spoken description instead of
fragmented symbol semantics. Equations retain font scaling and can scroll
horizontally in constrained widths, within the vertically scrollable page.

A pure `TutorialCoordinator`, owned by the ViewModel, retains pending requests,
card identity and basic/detailed page across Activity recreation. A navigation
token rejects obsolete entrance callbacks; card IDs reject obsolete page/dismissal
callbacks. Activation schedules eligible first-use help and sets a method-entry
acquisition gate. The host acknowledges the token only when its actual destination
transition is settled at Measurement. Only a matching active destination may clear
the gate and show pending help. A recreated measurement destination acknowledges
its settled state without replaying entrance. Repeated acknowledgements cannot
duplicate or reopen a card. Navigation cancels pending/visible help without marking
it seen. No timer coordinates navigation and tutorials.

`LocalTutorialSeenStore` uses the dedicated app-private `method_tutorials`
SharedPreferences file with boolean `seen.<stable method ID>` keys. Dismissal
writes completion using `apply`; activation, page changes and manual opening do
not. Only completion persists: process death starts inactive, and an undismissed
tutorial is eligible on the next method activation. No selection, height, capture,
result, card identity or page is restored from this file.

Source paths below are relative to `app/src/main/java/com/malagel/viewy/`:

| Component | Responsibility |
| --- | --- |
| `domain/MeasurementCatalog.kt` | Stable mode/method identities and implemented-method capability requirements; unavailable modes have no methods. |
| `domain/KnownHeightDistance.kt` | Pure Kotlin geometry, typed failures, conditioning diagnostics and conditional input-bound propagation. |
| `orientation/OrientationMath.kt` | Pure quaternion parsing/rotation, ray angles and circular differences. |
| `orientation/OrientationSession.kt` | Pure bounded recent-ray statistics, frame/time validity, captures and pair compatibility. |
| `orientation/AndroidOrientationAcquisition.kt` | Sensor discovery/registration, callback isolation, freshness publication and cleanup. |
| `camera/CameraDirectionMapping.kt` | Pure policy mapping the selected optical axis into native device axes. |
| `camera/CameraPreview.kt` | CameraX logical preview, active-lens mapping, zoom, stream/errors and unbinding. |
| `ui/MeasurementResources.kt`, `orientation/OrientationRegistration.kt` | Shared resource reconciliation, synchronous preview release and sensor registration ownership. |
| `camera/PreviewSession.kt` | Ownership tokens reject callbacks/disposal from obsolete previews and clear readiness on release. |
| `measurement/HeightInput.kt` | Decimal validation and meters/feet conversion without presentation rounding. |
| `measurement/MeasurementSelection.kt` | Pure Home/Methods/Measurement destination transitions and derived active method. |
| `ui/HomeScreen.kt` | Home header, centered selection card and shared Home control. |
| `ui/MeasurementSelection.kt` | Reusable animated card lists with scrollable entries, disabled modes and input gates. |
| `tutorial/TutorialCoordinator.kt` | Pure help coordination, navigation/card callback identity and temporary presentation state; no measurement or hardware access. |
| `tutorial/LocalTutorialSeenStore.kt` | Small local completion store keyed by stable method ID. |
| `ui/MethodTutorialContent.kt` and `ui/MethodTutorialDialog.kt` | Resource-based prose/equation blocks and reusable single-card presentation with basic/detailed pages. |
| `ui/TutorialMathNotation.kt` and `ui/TutorialEquation.kt` | Pure authored-notation parsing and native mathematical layout with localized spoken descriptions. |
| `ui/HeightLabelText.kt` | Locale-aware plain-decimal result formatting/counter frames and six-significant-digit height labels; full-precision editable input is untouched. |
| `ui/MeasurementResultPanel.kt` and `ui/ResultPresentation.kt` | Result layout, host-local entry identity, deterministic reveal timeline and retained result exit; no measurement or hardware ownership. |
| `ui/MeasurementGuidance.kt` | One-time workflow error presentation and expiry, plus the current near-vertical restriction. |
| `ui/AlignmentGuidance.kt` and `ui/AlignmentChevrons.kt` | Deterministic alignment presentation filtering and decorative Compose chevrons; independent of capture validation. |
| `measurement/KnownHeightWorkflow.kt` | Immutable top/bottom/correction/result state, compatibility/alignment gates, retry and restart. |
| `ui/MeasurementViewModel.kt` | Selection state, one shared foreground sensor adapter, workflow and immutable StateFlow snapshots. |
| `settings/AppPreferences.kt` | Typed global units and diagnostics preferences in one application-context Preferences DataStore, independent of tutorials. |
| `settings/SettingsViewModel.kt` and `ui/SettingsScreen.kt` | Observable load/write/error state and retained, temporary settings navigation with animated child destinations. |
| `ui/ScreenNavigationMotion.kt` | Shared host-window overlay motion, outgoing-destination retention and input/accessibility gates; never owns measurement or preference state. |
| `settings/AppLanguage.kt` | Android/AppCompat locale selection without duplicate preference storage. |
| `settings/AppDataReset.kt` and `settings/AndroidAppDataReset.kt` | Injectable, confirmation-gated reset policy and isolated Android data-clear/locale adapter. |
| `ui/HelpAction.kt`, `ui/DiagnosticsPresentation.kt` and `ui/DiagnosticsScreen.kt` | Preference-gated help routing and a pure, immutable, localized read-only projection of existing workflow/camera/acquisition snapshots. |

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
stationary variation. The main result is a concise approximate distance, with
unquantified uncertainty exposed through the result accessibility description; there is no bounded-input editor.

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
the optical principal point. Intrinsics alone cannot calibrate it: verified stream crop,
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
never silently switches on stalls/unreliability. The viewfinder recovery retry
starts a new preferred/fallback session and clears captures; diagnostics has no
source selection or restart controls.

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

CameraX binds only Preview to the default rear logical camera without pinning a
physical output. Readable rear physical cameras supply pose-based direction
mappings. The largest reported sensor supplies the provisional startup mapping;
for multi-camera devices, readiness requires a completed capture reporting a
readable active physical ID. Missing/unknown IDs fail explicitly. Metadata is
copied to the main thread with binding-generation and frame-order guards;
CameraX retains ownership of all session and zoom controls. Reported lens changes
update the mapping, restart zoom settling without resubmitting controls, and
restart orientation through the existing resource owner. Orientation restart
clears old observations and invalidates an incomplete pair. Expected stopped
snapshots remain quiet; explicit discovery/registration failures retain Retry.

A 4:3 stream is requested with supported-resolution fallback. PreviewView uses
COMPATIBLE/TextureView for rounded clipping/overlays and FILL_CENTER for
aspect-preserving centered crop. CameraX handles image rotation. Display changes,
including 180° without recreation, rebind and invalidate captures. EIS/preview
stabilization and OIS are requested off; autofocus/exposure remain at defaults.
Requests do not prove hardware behavior. There is no image analysis, photo capture,
recording, focus lock, automatic endpoint detection or camera extension.

Method hardware requirements, acquisition eligibility and completed measurement
state are separate contracts. `MeasurementUiState.acquisitionEligible` requires
an active camera method, saved height, permission, resumed Activity, a visible and
focused viewfinder window, no completed result, and no covering navigation/editor.
Settings/diagnostics remain covering through exit animation; method entry remains
covered until the tutorial coordinator accepts its tokened entrance-completion
acknowledgement. Home and Methods have no active hardware method. Pending first-use help also blocks startup. Height editing and
status dialogs suspend acquisition; a collapsed exceptional workspace is inactive.
The actual workspace window supplies focus, so an open exceptional dialog can
acquire even though its Activity window has lost focus.

`MeasurementResources` is the main-thread owner of preview tokens, platform release
callbacks and orientation mapping reconciliation. The ViewModel applies the same
eligibility policy to preview startup, callbacks and capture. Suspension revokes
preview ownership before closing CameraX, stopping orientation and publishing
unready state. Last-known camera metadata/failure details remain diagnostic
snapshots, with OPEN/STREAMING/readiness cleared; a new binding clears them. A preview epoch forces replacement even if an overlay opens and
closes between compositions. Stable effect keys avoid rebinding on ordinary
sensor publications or recomposition. Disposal/retry callbacks from an older
owner cannot stop or revive its replacement; asynchronous CameraX provider work
also checks disposal/failure. Sensor registration tokens reject queued data and
accuracy callbacks after stop, failed registration or retry.

`PreviewZoomSession` owns pure zoom limits, latest-request tokens and settling
state, outside the geometry layer. Each binding uses an effective minimum of
`max(1×, CameraX minimum)` and the full CameraX `ZoomState` maximum, with no upper
product cap. It explicitly requests 1.0× (or a higher supported minimum) through
`CameraControl.setZoomRatio` at initialization and reopening. The same effective
range drives button bounds, pinch/button request clamping, the request and the
label; sub-1× zoom is never selectable. A range entirely below 1× fails closed.
There is no Compose image scaling. Zoom changes do
not rebind the logical camera. Reported physical-lens changes update its direction
mapping and require fresh orientation before capture.

The 1× floor excludes requested zoom-out. Android's
[zoom-ratio contract](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_ZOOM_RATIO)
allows optical lens selection; fixed requested zoom does not promise a physical
lens lock. The active-ID check detects reported changes after top capture, which
invalidate the incomplete pair even though user zoom is locked. Compatibility
fallbacks for devices without active-camera metadata are not implemented.

The preview's native scale detector and localized Compose buttons route through
`MeasurementResources`, which rejects changes after top capture, on completion or
without eligible, ready acquisition. Resource ownership clears the request callback
before release; adapter generations reject futures and settling callbacks from
superseded requests, rebinding and closure.

CameraX zoom LiveData reports targets before application; capture instead waits
for the latest request future ([repeating-request acknowledgement](https://developer.android.com/reference/androidx/camera/core/CameraControl#setZoomRatio(float))), then a 350 ms
preview drain interval. This is an unvalidated usability guard, not an exposure
synchronization or latency bound. Zoom settling gates both shutter enablement and
the ViewModel capture entry point, separately from camera readiness, so ordinary
zoom changes preserve sensor acquisition, while a reported lens change restarts it. Failed,
closed or timed-out current requests enter existing camera recovery with capture
blocked; obsolete failures are ignored. Requests have a four-second deadline
(twelve seconds during startup). Every replacement binding resets zoom rather
than restoring a potentially incompatible target. An OPEN loss revokes pending
zoom work; reopening an otherwise still-bound camera also resets and acknowledges
zoom before capture can resume. Fixed 1× cameras remain usable
with disabled zoom controls; invalid capability ranges fail closed. The localized
ratio label stays visible with the controls, using nearly opaque surfaces.

Capture requires OPEN plus STREAMING, a verified active ID on multi-camera
devices and current orientation readiness. Permission
is rechecked on resume; denial retains permission recovery controls. Camera readiness
alone never requests recovery UI. `CameraRecovery` distinguishes quiet binding/retry
waiting from a failure requiring user action. `MeasurementResources` owns one
cancelable main-thread scheduled retry: at most three replacement bindings after
an initial failure, delayed 500, 1000 and 2000 ms. The short exponential backoff
allows temporary contention to clear without a tight loop. Exhaustion exposes the
localized failure and manual Retry, which starts a fresh bounded cycle. Streaming
success or a new eligible acquisition session resets the cycle. Initialization
exceptions, temporary camera contention and watchdog expiration are retryable;
permission/security failures, missing rear/consistent physical lenses, incompatible
stream configuration and system-policy/fatal errors skip automatic retries. CameraX
error codes follow its [state contract](https://developer.android.com/reference/androidx/camera/core/CameraState).
Retries retain the same default rear logical-camera selection.

Ineligibility cancels scheduled work immediately, resets the cycle and revokes
preview ownership before release. Retry generations reject even already-queued
callbacks after pause, completion or rapid overlay transitions. Only a due, current
retry advances the preview epoch; stable recomposition never requests a new binding.
Failure details remain diagnostic during silent recovery/suspension. The compact
status dialog retains the snapshot that prompted it while suspending acquisition;
its Retry closes the dialog so an eligible surface can start a fresh cycle.

`PreviewWatchdog` uses monotonic deadlines: 12 seconds for initial OPEN plus
STREAMING and 3 seconds for sustained readiness loss after streaming. Repeated
unready notifications do not move the deadline; a brief transition back to ready
cancels it. Readiness loss still blocks capture and invalidates incomplete pairs
immediately through the existing resource rules. Intentional lifecycle shutdown
cancels the watchdog rather than counting a failure; it runs only while STARTED.
CameraX binds only for eligible foreground work;
Activity pause and destruction close the adapter immediately and idempotently,
without waiting for Compose disposal or motion. Cleanup removes observers,
display listeners and watchdog callbacks and unbinds only the owned Preview.
Orientation stop unregisters its listener and removes the freshness timer.

A visible intentional suspension can copy one transient PreviewView bitmap, close
the camera immediately, and fade that decorative image over 225 ms. The snapshot
records the live opacity at release; its animation initializes at that opacity
before its first draw to avoid a transparent frame during the handoff. No image is
stored or exposed to accessibility. Failure to obtain the bitmap skips the fade;
backgrounding/disposal does not wait for it. The live preview fades in only after
OPEN plus STREAMING, over 225 ms. Presentation never extends a hardware session for an animation.

`MeasurementUiState.previewVisible` supplies the same eligible-and-ready target
for the live camera and zoom controls. The preview-edge height editor shares that
target during acquisition, with an uncovered, focused recovery exception
that keeps height editable without a live camera. On completion it is removed;
height editing belongs to the result panel’s object-height action. A shared
preview-control row reserves zoom’s measured width and a 16 dp gap before giving
the height action its remaining width. A full label falls back to a height cue,
ellipsized single-line number and separate unit when constrained; accessibility
retains the complete formatted height/edit description. This does not change
height storage or editable input. Common viewfinder interaction
gates hide both controls for overlays, height editing, focus loss and inactivity.
The controls and preview share one 225 ms duration for both fade-in and fade-out. Outgoing zoom retains its last visible ratio and
button appearance until exit completes, even when camera snapshots reset.
Transitioning controls exclude touch, focus and accessibility immediately and
become interactive after entry settles. Interrupted fades reverse in place;
system animation scaling applies. Fade state never delays hardware release or
changes binding keys, zoom/capture state or alignment. Completion removes these
controls immediately rather than retaining their outgoing camera visuals.

Selection is held in the ViewModel separately from the workflow.
`MeasurementSelection` has typed Home/Methods/Measurement destinations, with an
active method derived only from Measurement. A new ViewModel starts at Home;
legacy persisted selection is ignored. Tutorial completion cannot activate a method.
Activity recreation retains navigation, height and completed results in memory;
process death starts at Home. The ViewModel separately tracks the workflow's owning
method, so Home clears the active destination without losing completed evidence.
Returning Home invalidates incomplete pairs immediately via resource reconciliation.
Reselecting the owner preserves height/results; selecting a different method restarts
the workflow and revokes prior hardware ownership. Unavailable modes have no
activation path.

Known Object Height requires a positive finite saved height before offering camera
permission recovery. Orientation additionally requires the method's orientation
capability and a ready camera mapping. Every interruption clears live orientation,
its frame and an incomplete pair; source/frame/mapping changes and pair expiry
still invalidate incomplete captures. Completed top/bottom observations and the
stored estimate are immutable measurement evidence, independent of current
hardware readiness. Camera loss, sensor shutdown and later frame changes never
clear them. Acquisition stays stopped behind the result; Restart or saving height
clears it and allows a fresh eligible session while preserving the entered height.
No hardware session or measurement survives process death.

`MeasurementResultOverlay` reads RESULT plus the stored estimate, retaining only
that estimate and its saved height through a 225 ms fade on Restart or height save.
The existing LivePreview remains the only PreviewView/binding owner, outside the
animated result subtree. Workflow reset and acquisition reconciliation run
immediately; result exit never gates startup. Live imagery is rendered only for
eligible OPEN plus STREAMING readiness. Completion retains a best-effort last-frame
bitmap beneath the incoming result color, while releasing hardware immediately.
The actual result entrance transition discards that bitmap once opaque; result
identity checks also discard it on replacement or Restart. It is presentation-only,
never persisted and never used for measurement. Existing suspension snapshots
and recovery fades are preserved.

`ResultEntryMemory` is local to the screen host and compares estimate references;
ordinary publications, units, locale and overlay navigation cannot claim a second
entry. Composition keys use the same reference identity, so a new equal-valued
estimate replaces even an unfinished exit at long system animation scales. It initializes with an existing estimate as already presented, including
Activity recreation, and retains consumed identity across exceptional-workspace
remounts. No animation state enters the workflow or process-death persistence.
The result surface enters with a 450 ms fade before the distance appears;
restored visible results appear directly. One Compose Animatable drives a linear
1740 ms timeline: the first 450 ms allow the surface to fill, followed by a
750 ms clamped ease-out counter fraction, then height, separation and disclaimer each
fade over 180 ms. Leaving RESULT cancels entry; disposal cancels all remaining
work. Compose duration scaling reaches the final timeline directly when disabled.
Hidden rows reserve layout space but have no accessibility targets; the counter
exposes only its final localized approximate horizontal-distance description.
The object-height button has a localized edit action. Exit revokes result input,
focus and semantics while retaining its visual section. Scrollable content and a
minimum readable distance font handle constrained bounds without resizing the host.

Result distance units are chosen once per destination: meters below 10,000 m,
kilometers at or above it; feet below 1,609.344 m (one mile), miles at or above it.
The comparison uses unrounded meters, and the chosen unit stays fixed from zero
through completion. Height formatting uses base units independently.
Number presentation uses localized grouped plain decimals, HALF_UP to hundredths
for ordinary values and two significant digits below 0.01. Primary-distance
frames and final text retain the destination's fractional padding (at least two
places), matching the measured layout text rather than leaving unused decimal
space before a right-aligned value. Supporting height and elevation omit decimals
for integers and retain at least one fractional digit for non-integers even when
rounded to an integer. Display-only decimal arithmetic keeps large converted feet
finite without changing stored Double values or geometry. Counter frames share
the destination’s decimal scale and a measured fixed numeric slot with tabular
digits; the final frame uses exactly the normal formatted result. Height labels
retain their six-significant-digit cap in plain decimal form. Editable input
retains its independent full-precision, ungrouped representation.

The workflow owns top/bottom/correction/result transitions, finite height,
compatibility checks and retry. Saving height restarts. The workflow retains
bottom-recapture support with frame/time checks; the measurement UI exposes
Restart as its only retry action. Alignment works independently of height
or distance geometry: positive **current minus top** wrapped bearing means turn
physically right (decrease bearing), negative means left. Screen orientation and
roll do not change that gravity-relative meaning. Gates cannot establish fixed
camera position.

`MeasurementGuidance` is a deterministic presentation controller owned by the
ViewModel. It consumes monotonic milliseconds from existing state publications,
including the orientation adapter's freshness tick; it adds no timer or sensor
work. Workflow notice IDs identify new events even when the reason repeats, while
stored reasons remain available to diagnostics after presentation expires.
Workflow failure events and the current near-vertical restriction produce text;
movement, settling, freshness, readiness and left/right alignment updates are silent.
The near-vertical message has priority while the existing angle restriction applies,
with immediate visibility targets and no display timeout. Text has no movement
tracking, condition hysteresis, debounce or live-hint cooldown. Corrections last
five seconds, extended by Android's recommended text timeout on API 29+;
hardware recovery hides them and retains persistent actions. Presentation never
changes capture eligibility or sensor quality assessment.

`AlignmentGuidance` separately consumes existing snapshots and monotonic sensor
time. It uses the workflow's physical correction classification directly, requiring
180 ms of a changed classification before replacing the visible cue. Capture
continues to use the immediate 0.45° gate. Only an accepted directional
cue followed by accepted alignment produces a recovery identity; repeated aligned
publications retain that identity. A new top or invalid/covered acquisition clears
transition history immediately, including pending recovery. Current observations
must be fresh within the existing age and pair limits and share the top's frame,
source and camera mapping with active acquisition. Fresh MOVING and SETTLING data
can guide without satisfying capture stability; unavailable, unreliable and
near-vertical data cannot guide.

The centered 40 dp reticle uses fuller round-capped white strokes with a black
outline; inset endpoints keep its bounds and aiming point fixed.
`AlignmentChevrons` draws rounded white strokes outside the reticle, with a
fine black outline for camera-scene contrast. Direction changes crossfade over
140 ms; a Compose infinite transition nudges outward by 8 dp over 700 ms and
smoothly returns. Recovery stops movement and fades both strokes together over
450 ms, once per identity. The renderer owns fade completion, so ordinary sensor
updates cannot restart it and system duration scaling is honored. Disabled system
motion leaves static directional strokes. Hiding disposes the renderer and cancels
animations immediately. No new sensor subscriptions, presentation timers or camera
transformations are involved. Decorative strokes have no accessibility targets;
Capture's state description carries the current correction without a live region.

Suspension clears displayed errors. Only a new reset that
lost a pending capture can wait for eligible acquisition to resume; an old notice
cannot replay after returning. Restart and stage changes clear obsolete guidance,
and results suppress all guidance. Camera readiness loss emits a notice only with
a pending top; resource ineligibility identifies intentional suspension separately
from genuine camera interruption. Retrying orientation without a pending capture
does not claim lost progress. Compose renders one static preview-edge surface,
with localized resource lookup at render time and no preview resize. The message
surface fades in and out over 150 ms, retaining outgoing text only for exit;
logical correction timing and capture gates are unchanged. Outgoing text is hidden
from accessibility immediately. Animation state remains local to Compose. Text uses a polite accessibility live region; constrained layouts retain
the existing status dialog. Alignment restrictions are
also exposed through capture accessibility semantics. Permission/orientation
failures and camera failures requiring user action retain recovery controls. Diagnostics is a read-only full-screen surface in the current host
window, including the exceptional workspace; the Activity draws an inaccessible
backdrop while that workspace owns interaction. The shared overlay host preserves
measurement bounds, suspends acquisition, hides covered accessibility and focus, and
blocks background touch through exit completion. Diagnostics Back closes only that surface. A single
ViewModel boolean retains navigation across Activity recreation without DataStore
persistence; incomplete endpoints are invalidated on suspension.

The Settings snapshot is the sole authority for diagnostics access. The help
control has one 48 dp `combinedClickable` gesture owner: tap routes to the existing
tutorial, an enabled hold routes to diagnostics with haptics, and disabled holds
are consumed. Explicit semantics expose the separate enabled long-click action.
Opening is gated by active method and pending/visible tutorials or selection;
Settings and other modal controls cannot launch another overlay while covered.
Disabling the preference closes any retained diagnostics presentation.

Camera snapshots expose logical/physical IDs, OPEN/STREAMING and typed failure
state; mapping exposes whether usable pose metadata or the uncalibrated fallback
was used. Sensor snapshots distinguish discovery, registration failure and
running quality without interpreting raw debug strings. `DiagnosticsPresentation`
projects existing state without a clock, polling, hardware calls or geometry
estimation. It retains workflow notices, rejects invalid/incompatible residual
endpoints, uses stored `DistanceDiagnostics`, and converts meters/radians only
for locale-aware display. Sensitivities use distance units per degree. No A/B
recording, debug sensor selection or diagnostics restart actions remain;
`compareObservations` remains shared by production capture compatibility checks.

There is no preview-exposure/sensor synchronization: neither tap time nor latest
sensor timestamp identifies the displayed frame's exposure. Rendering, processing,
exposure and scheduling add unmeasured latency, potentially worse in low light.
No constant latency correction or extrapolation is applied. Settling before and
remaining still after capture is a mitigation, not a latency bound.

## Settings, locales and application-data reset

`SettingsViewModel` exposes immutable preference snapshots and explicit initial
loading, write and storage-error states. Measurement UI waits for the first saved
snapshot instead of briefly showing default units. DataStore edits are serialized;
rows are disabled while writing and failures expose Retry. Units and diagnostics
are independent of tutorial completion. Language is never copied into DataStore.
The global presentation unit is passed into height/result rendering; the workflow
owns only physical height in meters. Unit changes have no workflow/reconciliation
call, preserving capture/result identity and internal precision. Editable height
uses the display decimal separator, full plain-decimal precision and the existing
point/comma parser. Saving untouched input reuses the exact stored meter value.

Settings uses an opaque full-screen Compose surface with Main/Language/Developer
navigation retained in the ViewModel. It renders in the current host window (the
Activity or an already open exceptional workspace), so the first recreated frame
includes Settings without waiting for another dialog window to attach. An open
exceptional workspace also keeps a matching Settings backdrop in the Activity,
without duplicate accessibility, Back handling or reset dialogs, to cover the
workspace window's teardown/reattachment. The underlying measurement composition
and lifecycle owner stay mounted while acquisition is suspended. Covered controls are excluded from touch, focus and accessibility;
Settings opens from Home, Methods or Measurement; pending/visible tutorial help
blocks opening and moving destinations cannot receive input. Language selection applies the Android locale without navigating,
including when the selected override has the same effective language as the device.
Back from either child returns to main settings, then closes settings.
No settings navigation or measurement session is restored after process death.
Configuration recreation retains both ViewModels, but Activity pause and preview
rebinding still invalidate incomplete captures.

`ScreenOverlayHost` animates only the covering Settings/Diagnostics surface using
Compose transitions: a 240 ms entrance and 180 ms exit combine fading with 24 dp
vertical movement. Measurement stays outside animated content and never changes
bounds; resource eligibility follows coverage through exit completion. One transition coordinates the Activity and exceptional
workspace hosts. Outgoing destinations remain composed until exit completes,
including a reversed entrance; the Settings page remains visible after its logical
destination closes. During motion, input guards consume touch and exclude moving
or covered content from focus/accessibility; Back is consumed until a destination
settles, preventing duplicate navigation or dismissal of the workspace underneath.

Settings child pages use destination-keyed `AnimatedContent` with a 240 ms,
32 dp horizontal slide and fade. Forward and Back use opposite directions,
mirrored for layout direction; the opaque Settings surface and bounds stay fixed.
Each animation entry renders its own destination and scroll state, with outgoing
callbacks disabled. Motion is presentation-only: it neither delays nor writes
ViewModel navigation, and sensor publications, preference edits and in-place
locale updates do not change animation keys. Compose's duration scale handles
reduced/disabled system animations. Recreated hosts initialize at the retained
destination rather than replaying entrance; no animation state is persisted.

The platform launch theme inherits dark `Theme.AppCompat.NoActionBar`; Compose
keeps its existing Material 3 dark scheme and edge-to-edge setup. The locale config
advertises English and Spanish to Android 13+ system settings.
`AppCompatDelegate.setApplicationLocales` uses an empty list for device language;
`getApplicationLocales` supplies the current choice, including system changes.
AppCompat's `autoStoreLocales` metadata enables app-private storage on API 23–32
and migration to system-managed locales on newer Android. Text and formatting use
the Activity's localized resources/Compose locale; the application-context sensor
adapter and pure math do not depend on locale. English default resources provide
fallback for unsupported languages. See [Android per-app language support](https://developer.android.com/guide/topics/resources/app-languages).

The Activity handles `locale`, `layoutDirection`, `screenLayout` and, on API 34+,
`grammaticalGender` configuration changes in place. The first override from device
language can include `screenLayout` even when window bounds remain unchanged;
handling locale alone would still allow Android to recreate the Activity and
briefly expose its window background. Compose already adapts to layout/configuration
updates. Inherited AppCompat/Activity configuration dispatch updates Compose
resources, layout direction and `LocalLocale` without replacing the window,
Compose tree or preview. Strings are read through `stringResource`; numeric labels
read `LocalLocale`, and
the language picker also observes `LocalConfiguration`. No language callback
restarts acquisition or edits the workflow. Orientation, screen-size,
smallest-screen-size and density changes retain normal Activity recreation and
session invalidation. See Android's
[Compose configuration guidance](https://developer.android.com/guide/topics/resources/runtime-changes).

`ResetController` accepts an injectable `AppDataReset` and permits requests only
from confirmation, with separate rejected/failed and terminal accepted states.
Cancel has no deletion or locale effects. Before confirmation runs the adapter,
the settings ViewModel blocks mutations and stops its preference collection;
pending writes must finish before reset can be offered. Acceptance triggers no
further preference reads/writes, success claim or relaunch. Android's
[`ActivityManager.clearApplicationUserData`](https://developer.android.com/reference/android/app/ActivityManager#clearApplicationUserData())
performs the actual full storage clear and runtime-permission revocation, retaining
the installed APK. Tests use fakes and never call this platform operation.

On API 33+, the reset adapter explicitly clears `LocaleManager.applicationLocales`
before asking Android to erase data, since this override is system-owned rather
than a DataStore preference. If the operation is rejected or throws, it attempts
to restore the prior override before reporting failure. On older Android,
AppCompat's auto-storage file is cleared with app data; the adapter avoids queuing
compat locale writes/recreation during deletion. No manual preference-file deletion
substitutes for the system reset. Actual process termination, permission revocation
and locale reset across OS versions remain manual verification requirements.

## Build contracts

Versions live in `gradle/libs.versions.toml` and Gradle build files; the wrapper
pins its distribution and checksum. AGP supplies built-in Kotlin, upgraded by the
root buildscript to match the Compose compiler; do not also apply Kotlin Android.
JUnit 4 supports JVM tests. Camera2 interop opt-in stays in the camera adapter.
The manifest requests CAMERA and removes the transitive network-state permission;
there are no Internet, microphone, location or storage permissions. No navigation or dependency-injection framework is implemented. Preferences
DataStore stores units/diagnostics; only tutorial completion uses its dedicated
SharedPreferences. Android/AppCompat owns locale persistence. Selection, settings
navigation and workflow state remain in memory.

See [README](../README.md) for setup and [Validation](VALIDATION.md) for test
coverage, recorded evidence and outstanding physical checks.

Platform references for the frame and preview contracts:
[Android rotation-vector sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion),
[sensor coordinates/lifecycle](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview),
[Camera2 lens characteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics),
and [CameraX PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView).
These API contracts do not validate any physical lens or measurement accuracy.
