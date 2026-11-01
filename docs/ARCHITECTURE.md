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
`adjustNothing` and measurement content excludes IME padding. `EmbeddedHeightEditor`
occupies the existing rounded workspace without a new window, text field or input
connection. `HeightEditorViewport` measures the compact rectangular keypad at a
bounded reference width, then centers and proportionally scales the whole layout
to fit the actual workspace. It has no scroll or overscroll owner. Reference keys
are 56 dp high; bounds and font scale determine the final proportional size.
The exceptional workspace's parent also stops scrolling while the editor owns its
remaining height. Disabled number/unit controls retain their colors; Enter instead
animates to 40% opacity over 150 ms for an invalid draft, with logical eligibility
changing immediately. All controls retain correct disabled semantics. The value
uses fitted tabular text in a clipped numeric slot with 8 dp internal margins for
its 6 dp rejection motion; the fixed unit slot cannot receive displaced digits. Buttons,
unit selection, display and decimal/backspace actions expose localized semantics;
a focused non-text container handles hardware digits, decimal, Backspace, Enter
and Escape while Tab/directional navigation remains with Compose.

`measurement/HeightEditor` owns one nullable `HeightDraft`, published by the
ViewModel and retained across Activity recreation, never process death. Opening
publishes the acquisition gate before synchronous resource release; camera and
orientation stay suspended through the host's 225 ms editor exit. Covered camera
and result controls lose touch, focus and accessibility immediately. Header, Home
and Restart remain available; Capture stays disabled. Settings, Help and diagnostics
retain the same draft object, physical anchor and local unit; coverage blocks
editing, Back and acquisition until return. Their existing capture-interruption
rules still discard unfinished pairs. Method exit cancels the draft and clears
saved inputs/evidence. Settings marks coverage before changing destination;
Help/diagnostics use their existing coverage gates. The covered editor cannot
consume overlay Back. Restart cancels the draft and resets measurement with height retained. Input callbacks also check
current ViewModel coverage, rejecting stale actions after navigation.
Back and the editor arrow call the draft-only `HeightEditor.cancel`, preserving
saved height and completed evidence. Camera editing returns to acquisition after
editor exit; initial-entry cancellation returns to the height prompt. Restart also
cancels only the draft before resetting captures with height retained. Confirmation consumes the draft
once; failed correction retains it with an inline error and preserves saved evidence.
Method navigation cancels an outstanding draft. First-use tutorials retain their existing gate.

`HeightEntryUnit` separates local m/cm and ft/in entry from global meters/feet
presentation. Each opening defaults to the global base unit. Drafts keep canonical
point-decimal text and independent decimal meters; unit switches format from that
anchor with at most four fractional digits (DOWN), never reparsing converted text.
Each new opening follows the current global Metric/Imperial preference. Global
preference changes while Settings covers a draft convert its anchor into the new
system's base unit without committing it; unchanged preferences keep its local unit.
A sub-0.0001 positive converted value displays an explicit less-than value.
Only a numeric edit changes the anchor; untouched/unit-only confirmation restores
exact stored Double meters. Decimal multiplication uses exact international
foot/inch and centimeter factors. Positive finite meter validation shares the
existing supported global-unit range; display localization never changes parsing.
Input accepts ten numeric digits, excluding the separator. Rejected digits retain
text and physical meters while incrementing a feedback identity consumed by the
number's short Compose shake. Recreated/covered editors do not replay old rejection
feedback; animation scaling applies. Legacy and conversion-generated values remain
representable even when they exceed the typing limit. The local unit selector
moves a neutral 44 × 40 dp rounded rectangle between stable accessible labels over
180 ms. Each 56 × 48 dp target retains radio-button semantics and has no touch
indication; the selected position remains visible without pressed feedback.

`MeasurementWorkspaceMotion` selects one cover: height prompt, height editor or
none over camera/results. One destination-keyed 225 ms opacity transition owns
both the surface and its controls; the camera/result layer stays mounted outside
it. The base waiting color is constant. Confirming the initial keypad goes directly
from editor to no cover, without an independently exiting height-prompt button.
Prompt return retains the last display height only through the incoming cover.
Moving covers block workspace input and acquisition, including a reversed editor
entrance whose initial/target states are both uncovered. Editor Back/arrow still
cancel the draft; camera Back clears height, result Back restarts, and overlays keep
their own Back priority. Method exit retains its outgoing cover for the existing
whole-screen navigation instead of also navigating the local workspace.

`LivePreview` keeps one native FrameLayout for the workspace lifetime. It owns
the preview child, reveal cover and decorative bitmap foreground together. A
preview-epoch change can stop/retry acquisition without replacing that host. Only
an actual new binding creates a fresh PreviewView child, beneath the same cover
and any retained image. Camera stop never inserts a new opaque waiting hierarchy.
The release callback captures and installs the image synchronously before covering
and unbinding the texture; no image crosses from a native view to a Compose Image.

Prompt/editor entry retains native pixels until its cover is opaque. Result entry
retains them until the result overlay acknowledges actual fade completion. Reversing
either transition fades the retained image instead. Settings/Help/diagnostics fade
the native foreground to the waiting color. Home keeps it for the destination's
existing whole-screen exit, without a second image fade. Incoming Home's inactive
workspace publication cannot clear this outgoing decorative image; Activity pause
still clears it immediately. Result count/reveal
identity and editor Back rules are independent of these decorative pixels.
Background cleanup does not allocate a bitmap. No image is stored, and no animation
extends hardware ownership.

Camera startup waits until the editor exit completes. Preview reveal and camera
controls wait for OPEN/STREAMING, physical-lens identity, initial zoom settling and
a subsequent native draw confirmation. Motion follows Compose duration scaling,
and recreated covers appear settled. The reserved stage caption retains its
150 ms top/bottom/empty crossfade and is empty during editing; outgoing captions
lose semantics immediately.

Home and measurement use the same compact predicate, maximum width, outer top/side
padding and header geometry. `ViewyHeader` reserves the same help slot and noncompact
subtitle line on Home while hiding them from drawing/accessibility. This keeps the
wordmark and Settings anchored identically when a method becomes active.

`HomeScreen` presents the existing header without contextual Help and a centered,
480 dp maximum-width, 28 dp rounded selection card. The card has stable bounded
height and independently scrollable mode/method lists. Destination-keyed Compose
`AnimatedContent` slides forward left and backward right over 220 ms. Moving and
outgoing lists cannot receive input; repeated Back is consumed during motion.
The method-list header places the localized Select a method heading beside Back
and does not repeat the mode name.
The destination host owns Android Back navigation for both the card and measurement;
the card only consumes repeated Back while its own transition is running. During
measurement entry, the outgoing Home presentation maps the active method to its
method list so the card does not navigate while fading out. Android Back from
measurement before height confirmation requests that mode's method list; Home requests the mode list directly.
Both regular and exceptional-workspace Android Back handlers dispatch through the
ViewModel's `systemBack`: an active completed result invokes the existing `restart`
action without navigating, retaining height and reconciling acquisition immediately.
Back from unfinished camera acquisition calls `returnToHeightPrompt`: it cancels
any draft, clears the workflow and publishes the height gate before clearing camera
and orientation resources. The method selection stays active until the next Back.
The explicit Edit height button still opens the ordinary draft editor; cancelling
that draft retains height and returns to acquisition. No navigation-history flag or
copy of the height is stored.
Editor and overlay Back handlers retain priority and their existing cancellation/dismissal behavior.

`MeasurementDestinationHost` animates Home and the entire measurement presentation
with fades and 20 dp movement: measurement enters from the right over 270 ms
while the method menu fades in place. Android Back leaving measurement before
height confirmation uses the shared method-return transition: measurement exits right and the method list enters from the left over
270 ms. Both destinations belong to the transition segment, so retained Compose
callbacks distinguish Android Back from Home without captured navigation state.
Returning Home retains its 280 ms vertical entrance and the measurement's downward
exit. Content keys distinguish selection from measurement, leaving Home/Methods
animation to the selection card. Sensor and preference publications do not change
the transition target. Compose duration scaling handles reduced
or disabled motion. Recreated hosts initialize settled. Input guards cover moving
and outgoing destinations. Hardware eligibility changes synchronously with logical
navigation, never with retained animation content. An open exceptional workspace
shares the destination transition in its own window, with a matching inaccessible
Activity backdrop; returning Home completes before that workspace window is removed.

On fresh Activity creation, the initial Home surface has a separate 300 ms
opacity-only entrance. Its Animatable belongs to `ViewyScreen`, outside the
destination content, so returning Home does not replay it. Activity recreation
opts out; system duration scaling applies and Home input/accessibility remains
blocked until the fade finishes. The launcher/task window transition remains
controlled by Android.

Method help uses one centered edge-to-edge Compose Dialog with a bounded-width,
fixed-height Material 3 card constrained by system bars, cutouts and available
window size. One shared page renderer and footer serve every registered method.
A Foundation `HorizontalPager` owns finger tracking, velocity handling, snapping and
programmatic navigation between Instructions and Important. A 10% positional threshold
makes slow drags easier to complete while standard fling behavior handles faster gestures.
Only settled pages publish to the coordinator. Buttons animate that pager locally,
without publishing a destination through the ViewModel before motion starts; Android
Back also animates it before publishing. Both small pages are composed ahead of the
slide, with the illustration clock stopped when Instructions is offscreen and settled.
Programmatic page movement uses a 220 ms FastOutSlowIn tween; touch fling behavior is
unchanged. Boolean interaction gates use derived state to avoid recomposing the card
on every entrance-opacity frame. No touch-consuming overlay is inserted during a drag. Moving pages
lose semantics/focus and their callbacks cannot act. Vertical prose scrolling remains
independent; the larger centered headline and fixed bottom actions remain outside it.
Instructions has a 56 dp circular Next; Important has matching bottom-right back/close
controls and a 14 sp documentation link aligned near the bottom-left corner.
The link keeps a 48 dp touch target and button semantics; a null indication
removes hover/press brightness changes without changing its normal theme color. Its decorative
6 sp bullet markers align with the first body-text line.

The documentation link opens an opaque full-screen WIP surface in the existing modal
host. The tutorial stays mounted with its page and scroll position, hidden from input,
focus and accessibility. `TutorialCard.documentationOpen` is retained in the ViewModel;
only Important can open it. The coordinator rejects covered page changes and obsolete
card IDs. Android Back or the placeholder's `NavigationBackButton` closes only that
surface, retaining Important and leaving completion unwritten. The shared back-arrow
component reserves a 48 dp touch target around a 44 × 32 dp rounded visual and a
20 dp arrow, with localized destination labels. The method selector preserves both
container and icon colors while disabled; interaction stays blocked. No documentation prose or equation-rendering code remains.

A full-window pointer ancestor reserves Android-reported left/right system-gesture
insets before content padding. For a touch starting in either physical edge region,
it consumes movement in the Initial event pass before the pager sees it, continuing
through release/cancellation. Interior starts retain normal paging, including when
the finger later crosses an edge. Zero insets reserve no space, and mouse/stylus
input is unaffected. Stationary taps remain available. No hardcoded edge width or
system-gesture exclusion is used. This prevents the pre-callback page displacement
that aligning the pager only after Android recognizes Back cannot prevent.

The Dialog disables default Back dismissal and owns a `PredictiveBackHandler`.
A system Back starting on either tutorial page captures the settled source page,
suspends pager input/publication and acquires scrolling with `PreventUserInput`.
Within that mutation it aligns the source page at zero offset and collects the
platform gesture, so a stopped partial drag cannot drift during dismissal. The
same path handles Back from either screen edge, without requiring idle paging.
Committed Back dismisses Instructions or animates Important to Instructions before
publishing that destination. Cancellation realigns and restores the captured source
before pager ownership returns. Pager overscroll is disabled so boundary stretching
cannot leave an independent visual rebound. WIP Back returns to Important. Explicit close
and outside taps request tutorial dismissal; the WIP surface blocks outside hits.

The transient dialog window disables platform window animations and native dimming.
A decorative Compose scrim uses the original theme dim amount and animates opacity
over 240 ms in both directions, covering the complete window including system insets.
This avoids a native dim layer disappearing abruptly when the dialog window is removed.
One local Animatable
fades and translates the card from 24 dp below over 240 ms on entry, and back down
over 180 ms on dismissal. Input and accessibility are revoked immediately on closing;
the retained coordinator card continues suspending acquisition until both the card
exit and scrim fade finish, then the ID-checked dismissal runs. Recreated visible cards appear settled;
a retained closing request completes its exit. Motion follows system duration scaling.
The underlying measurement stays composed; pending/visible help suspends acquisition
through the shared resource policy.

Tutorial content distinguishes resource-backed prose, bullet lists and the Known
Object Height illustration. Instructions places it between
the introduction and steps as an explicit content block. Its transparent 2:1
Canvas scales uniformly within a centered 440 dp maximum width, with 12 dp
horizontal illustration margins while prose retains 24 dp margins. One draw-phase
animation clock runs only while STARTED and is canceled on removal; page/card
keys restart the sequence on reopening or returning to Instructions. Compose system
duration scaling applies, with a static distance frame when motion is disabled.
The illustration owns no hardware or measurement state and exposes one localized
image description without changing announcements.

A pure `TutorialCoordinator`, owned by the ViewModel, retains pending requests,
card identity and current section across Activity recreation. A navigation
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
| `camera/CameraPreview.kt`, `camera/PreviewPresentationGate.kt`, `camera/PreviewDraw.kt` | CameraX preview, lens mapping, zoom, startup presentation readiness, cancelable draw confirmation and unbinding. |
| `ui/MeasurementResources.kt`, `orientation/OrientationRegistration.kt` | Shared resource reconciliation, synchronous preview release and sensor registration ownership. |
| `camera/PreviewSession.kt` | Ownership tokens reject callbacks/disposal from obsolete previews and clear readiness on release. |
| `measurement/HeightInput.kt`, `measurement/HeightEditor.kt` | Supported-height validation, temporary keypad drafts, precise local unit conversion and atomic confirmation/cancellation. |
| `ui/EmbeddedHeightEditor.kt` | Embedded responsive keypad, local unit selector, accessibility and hardware-key input without IME. |
| `ui/LivePreview.kt`, `ui/MeasurementWorkspaceMotion.kt` | Native preview/reveal cover, synchronous image handoff and typed prompt/editor cover motion with input/acquisition gates. |
| `measurement/MeasurementSelection.kt` | Pure Home/Methods/Measurement destination transitions and derived active method. |
| `ui/HomeScreen.kt` | Home header, centered selection card and shared Home control. |
| `ui/MeasurementSelection.kt` | Reusable animated card lists with scrollable entries, disabled modes and input gates. |
| `tutorial/TutorialCoordinator.kt` | Pure help coordination, navigation/card callback identity and temporary presentation state; no measurement or hardware access. |
| `tutorial/LocalTutorialSeenStore.kt` | Small local completion store keyed by stable method ID. |
| `ui/MethodTutorialContent.kt` and `ui/MethodTutorialDialog.kt` | Resource-based prose/equation blocks and reusable single-card presentation with three shared sections. |
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
snapshots, with OPEN/STREAMING/readiness cleared; a new binding clears them. A
preview epoch forces a new binding and camera child even if an overlay opens and
closes between compositions; it does not replace the visible native host. Stable effect keys avoid rebinding on ordinary
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

Capture requires a presented OPEN/STREAMING preview, a verified active ID on
multi-camera devices, settled zoom and current orientation readiness. Permission
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

A visible intentional suspension can copy one transient PreviewView bitmap and
close the camera immediately. `LivePreview` installs it as the stable host's native
foreground before unbind can clear pixels, sampling the live reveal opacity. It
remains in that same native hierarchy for its entire hold/fade; neither acquisition
ineligibility nor a preview-epoch change replaces it with a waiting view. New
bindings replace only the closed camera child beneath the cover. Snapshot identity
rejects stale alpha/clear callbacks, including when a rapid reversal reuses the same
bitmap. An old reveal coroutine also cannot uncover a stopped or replacement child.
Failure to copy skips the image; backgrounding/disposal never waits for it.

`PreviewFrameMotion` distinguishes a local workspace cover, result cover, ordinary
suspension and destination exit. The result overlay's settled callback releases
its native backdrop; Restart before that callback fades it instead. Native drawable
alpha uses Compose animation scaling. The waiting cover stays drawable and opaque
until readiness, then its alpha reveals the stream over 225 ms. No presentation
callback delays binding or resource release.

`PreviewRelease` runs snapshot publication and camera close once per owner. Resource
suspension and Compose disposal share it, so late disposal cannot overwrite retained
pixels or change the release decision. Disposal-first cleanup closes immediately
without an animated snapshot.

`PreviewPresentationGate` keeps readiness false through initial zoom acknowledgement
and the existing settling interval, then requests a draw confirmation. API 29+
hardware rendering uses a frame-commit callback; older/software rendering posts
completion after draw dispatch. Generations reject confirmation after readiness
loss, closure or rebinding. This is a presentation fence, not an exposure timestamp
or a brightness/exposure-quality test; dark scenes remain valid. Once presented,
ordinary user zooms retain the image while the independent zoom-settled capture
gate remains in force. Cleanup cancels pending draw callbacks. No arbitrary startup
sleep or image-brightness heuristic is used.

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
The controls and preview reveal share a 225 ms duration. Local opaque covers own
image exit; independent visible suspensions fade the snapshot over the same duration. Outgoing zoom retains its last visible ratio and
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
process death starts at Home. Leaving Measurement for Home or Methods immediately
calls `KnownHeightWorkflow.clear`, removes the height editor, revokes resources and
clears camera/orientation diagnostics. `restart` retains height; `clear` removes all
physical inputs, observations, result identity and live readiness while keeping
notice IDs monotonic. `MeasurementResources.clear` additionally drops camera
metadata/zoom and pending retry ownership; the orientation adapter clears discovery
snapshots after stopping. Reentry is height-gated from a fresh state. Outgoing
measurement content may retain only its presentation through navigation motion.
Camera labels receive the retained snapshot's non-null height explicitly, while
visibility, interaction and resource eligibility use the current cleared state.
`MeasurementUiState.methodActivationId` increments on method entry, stays stable
through overlays and exit, and keys the workspace composition, retained draft,
measurement snapshot and result-entry memory. A subsequent activation cannot
reuse an outgoing result panel or its exit animation; its first frame is fresh. Global preferences,
permission grants and tutorial completion do not belong to a method session.
Unavailable modes have no activation path.

Known Object Height requires a positive finite saved height before offering camera
permission recovery. Orientation additionally requires the method's orientation
capability and a ready camera mapping. Every interruption clears live orientation,
its frame and an incomplete pair; source/frame/mapping changes and pair expiry
still invalidate incomplete captures. Completed top/bottom observations and the
stored estimate are immutable measurement evidence, independent of current
hardware readiness. Camera loss, sensor shutdown and later frame changes never
clear them while the method remains active; method exit clears them explicitly.
Acquisition stays stopped behind results, including height corrections.
Restart clears the result and allows a fresh eligible session with entered height retained.
No hardware session or measurement survives process death.

`MeasurementResultOverlay` reads RESULT plus the stored estimate, retaining only
that estimate and its saved height through a 225 ms fade on Restart.
The existing LivePreview remains the only native-preview/binding owner, outside the
animated result subtree. Workflow reset and acquisition reconciliation run
immediately; result exit never gates startup. Live imagery is rendered only for
eligible, presented camera readiness. Completion retains a best-effort native last-frame
foreground beneath the incoming result color, while releasing hardware immediately.
The actual result entrance transition acknowledges when opaque and releases that
foreground; Restart before coverage fades it with the outgoing result. It is presentation-only,
never persisted and never used for measurement. Existing suspension snapshots
and recovery fades are preserved.

`MeasurementState.resultOrigin` retains the original successful estimate as the
completion identity across corrections; Restart clears it and a new capture replaces it.
`ResultEntryMemory` is local to the screen host and compares these original references;
ordinary publications, units, locale and overlay navigation cannot claim a second
entry. Composition keys compare current estimate references, so corrections
replace value content directly while a new equal-valued measurement can replace
even an unfinished exit at long system animation scales. It initializes with an existing completion identity as already presented, including
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
Distance fitting measures the final number, approximation glyph and unit at their
actual separate font sizes, including gaps and a glyph-edge allowance. It selects
up to 96 sp, with proportional line heights; the 28 sp minimum and horizontal
scroll fallback retain very long values without drawing outside the result area.

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
compatibility checks and retry. `setHeight` restarts unfinished measurements.
`correctCompletedHeight` validates the stored compatible pair and calls the existing
`KnownHeightDistance.estimate` with new meters and original viewing directions.
Success atomically replaces height, estimate and diagnostics while preserving stage,
observations, metadata, completion identity and notice identity. Equal height is a
no-op; invalid height/geometry/arithmetic leaves the entire state untouched.
No capture/completion event or acquisition restart is emitted. Nominal uncertainty
remains unavailable. Editing after a pending top still invalidates that pair on
suspension and reuses the existing capture-cleared notice on return, including
changed-height confirmation. The workflow retains
bottom-recapture support with frame/time checks; the measurement UI exposes
Restart (also invoked by Android Back from results) as its only retry action. Alignment works independently of height
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
settling, freshness, readiness and left/right alignment updates are silent.
MOVING observations in one sensor frame accumulate six continuous seconds only
while acquisition/preview and zoom are ready. One Keep the camera steady hint then
uses the same correction timeout and renderer, without repeating within the same
movement episode. Any other quality status, suspension, frame change, near-vertical
restriction, restart or capture-stage change resets it. Workflow corrections take
priority and can defer its first display. No extra timer or sensor registration is
introduced. The near-vertical message has priority while the existing angle
restriction applies, with immediate visibility targets and no display timeout. Corrections last
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

Capture renders its shutter and border with one 150 ms animated color, using the
same enabled/disabled theme colors as before. Both Button color states read that
animated value so the availability gate changes immediately without a color jump;
the container, dimensions, semantics and capture mathematics are unchanged.

`ScreenHeader` gives Settings and its Language/Developer pages, WIP and Diagnostics
one shared title/back layout: 16 dp top, 8 dp bottom/horizontal spacing below the
host-owned system insets. It keeps the existing full back-button touch target;
Diagnostics retains its intentionally scrollable header in constrained workspaces.

## Settings, locales and application-data reset

`SettingsViewModel` exposes immutable preference snapshots and explicit initial
loading, write and storage-error states. Measurement UI waits for the first saved
snapshot instead of briefly showing default units. DataStore edits are serialized;
rows are disabled while writing and failures expose Retry. Units and diagnostics
are independent of tutorial completion. Language is never copied into DataStore.
The global presentation unit is passed into height/result rendering; the workflow
owns only physical height in meters. Unit changes have no workflow/reconciliation
call, preserving capture/result identity and internal precision. Editable height
uses the display decimal separator and full plain-decimal precision with point/comma
key input. Saving untouched input reuses the exact stored meter value.

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
