# Validation

## Reproducible automated checks

From the repository root with the [README toolchain](../README.md#development-setup),
run relevant Android checks in one bounded invocation:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The API 31 minimum configuration passes JVM tests, debug assembly and lint with
JDK 17 and the API 37 SDK using the command above. The merged debug manifest
retains optional camera features and the existing permissions. Lint has no errors;
warnings cover dependency/tool updates, the API 33 locale-config attribute,
portrait orientation, backup guidance, typography and KTX suggestions.
Android 12 and recent-Android camera rendering, sensor operation, accessibility
timeouts and language/reset behavior still require developer-controlled manual
validation; no device or emulator was used.

For deployment-script changes, use checks that mock ADB and Gradle:

```sh
for script in scripts/deploy.sh scripts/deploy-release.sh scripts/deploy-common.sh; do bash -n "$script" || break; done
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_deploy.py'
```

For both build types and release optimization, run one bounded build/check:

```sh
./gradlew --no-daemon --max-workers=1 --no-watch-fs -Pviewy.localReleaseSigning=true :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
VIEWY_VERIFY_APKS=1 PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_apks.py'
```

The APK checks require exported `ANDROID_HOME` and `JAVA_HOME` from the README
setup. They inspect the actual APKs with Build Tools 36.0.0, checking application
IDs, launcher labels in all packaged locales, launcher activity, debuggability,
valid signatures and matching development certificates. R8 output checks cover
obfuscation, removed code, resource analysis and enabled optimization. These
host-only checks do not invoke ADB. The deployment suite mocks ADB/Gradle and
runs its discovery, environment, ordering and failure checks for both entry points,
including signing mismatch rejection without uninstalling or clearing data.

The combined build/check command above passes with JDK 17, including both APK
assemblies, JVM tests and release lint. Lint has no errors; warnings cover tool/
dependency updates, platform guidance, typography and KTX suggestions. Inspection
of the locally signed artifacts confirms separate IDs and labels, the common
launcher activity, release debugging disabled, and valid development signatures.
R8 includes `proguard-android-optimize.txt`, obfuscates application classes and
reports removed code and unreachable resources without additional app keep rules.
The mocked deployment and APK checks pass. Configured production signing has not
been exercised with a production key; none was supplied or created.

Release runtime validation remains manual: deploy both apps with the README
commands, confirm both launcher entries coexist and retain independent onboarding,
permissions, units and language across updates. In the optimized release, exercise
onboarding, tutorial/navigation/animations, language persistence, height entry,
camera preview and zoom, sensor capture, result correction and diagnostics, including
background/resume. Build-time dependency resolution and R8 success cannot establish
runtime camera/sensor correctness or usability. Changing a release certificate
must not trigger automatic uninstall or data deletion.

For documentation changes, check links/references and run `git diff --check`;
Android builds are unnecessary. Follow [AGENTS.md](../AGENTS.md) for resource and
device restrictions. Run only checks relevant to the change; recorded results below do not imply
that these commands have been rerun on every revision.

Onboarding JVM coverage exercises three-page forward/Back state, stale callback
suppression, retained temporary navigation versus fresh-process state, permission
grant/denial/skip/resume refresh, inactive acquisition and later contextual grant.
Real temporary Preferences DataStores cover incomplete/completed restart, fresh
storage after reset, legacy preference preservation, regional initialization
before the first snapshot, Settings overrides and completion-version retention.
Injected storage failures and suspended/canceled writes cover retry and ensure
completion is not visible before commit. Regional-policy tests cover Metric,
Imperial, mixed/unknown regions, ICU mappings/failure and
language-independent regional extensions. Welcome can advance without a hardware
discovery result. These tests do not recreate an Android
Activity, execute permission dialogs or render Compose animations.

Camera-permission regression tests distinguish pending requests from denial,
prevent duplicate requests, retain the missing-permission explanation through
pause/focus loss, preserve navigation/editor coverage, and keep acquisition gated
until a real grant in the foreground. They cover explicit retry, settings recovery
after an unrequestable denial, resume-time grant refresh and later revocation.
On a developer-controlled device, skip camera access in setup, enter Known Object
Height, dismiss its tutorial and save height. Tap Allow camera: the underlying
explanation and single button must stay in place while Android prompts, with no
retry label or second action flashing. Deny and retry, then grant; recovery should
fade out only after the grant. The preview height action must remain absent while
permission is missing and appear with zoom once the preview is ready. During the
panel's exit, no outgoing action may be tapped, focused or read by TalkBack.
Repeat in compact/status-dialog and exceptional
viewfinder layouts, through recreation, and with permanent denial followed by
Allow camera → Android app settings → grant → return. No camera or orientation
work should start while permission is missing or Android owns focus.

Developer device validation for onboarding remains required:

- With fresh app data, verify Welcome → Permissions → Completion → Home, including
  Back, rapid repeated taps, recreation on each page, and process termination before
  the final action. Restart after completion must go directly to Home. Confirm
  Home has no entrance animation behind onboarding or blank completion frame.
  Forward steps must slide the entire page left by the full viewport width with
  no page fade; Back must reverse the motion, including the footer.
- Exercise already-granted, granted, denied and skipped Camera access. No prompt
  should launch automatically. After skipping/denying, enter Known Object Height,
  dismiss its tutorial and save height; use its existing permission recovery, then
  return from Android app settings and confirm the grant is refreshed.
- Check English/Spanish on compact windows, maximum font size, TalkBack, gesture
  and button navigation, and normal/slow/disabled animator duration scales.
  All prose/actions must remain reachable and moving pages must be inaccessible.
  Confirm the enlarged wordmark stays on one line and the rounded page headings
  wrap readably. Setup should fade upward once, with no entrance replay after
  recreation. Permissions must show one primary button: Not now until all grants,
  then Continue, including after revocation and return from Android Settings.
  Not now must use opaque muted palette colors with readable text, retaining the
  same appearance when Allow camera is tapped and while Android prompts. Denial
  must retain that appearance; grant must crossfade the label and fade the container
  and text to normal Continue colors without
  moving the card or footer. A pending request must still block navigation.
  Check that Allowed matches Allow camera in font, weight and size. The final
  Continue to Viewy button must retain its colors while saving and exiting, with
  no disabled-color flash. Storage-error Retry must still leave setup unfinished.
  Compare the camera card before/after granting, denying and granting through
  Android Settings: its position and bounds must remain identical while labels
  and explanations crossfade. Hidden permission actions must not be accessible or
  focusable. Repeat with disabled motion.
- Check regional initialization on Android 12 (API 31) and a recent Android
  version with US, Chile and UK regions, including a different app-language override. Change units in Settings and relaunch;
  the explicit choice must survive. Reset app data only when intentionally testing
  deletion; it should restore onboarding and initialize units again.
- Confirm Welcome shows only the static accuracy disclaimer, with no compatibility
  status or wait. Setup must leave camera privacy indicators and sensor acquisition
  inactive.

JVM coverage in `app/src/test` exercises geometry/invariance, typed failures,
conditioning and conditional bounds; quaternion signs/order/formats and poles;
spherical steadiness statistics, timestamps, discontinuities, reliability and
source/frame/mapping isolation; inverse camera-pose mapping and fallbacks; height
conversion; global unit presentation without workflow mutation; real Preferences
DataStore defaults, edits and file reopening; language tag selection, Spanish
resource completeness/placeholders and locale formatting; the manifest
contract for cold default-language changes without suppressing orientation/size/density
recreation; settings Back
hierarchy/loading/write gates; embedded keypad drafts, decimal/zero/backspace
handling, m/cm and ft/in conversion without cumulative drift, localized display,
confirmation/cancellation and duplicate suppression;
fake-only reset confirmation, cancellation, duplicate
suppression, rejection/exception and locale rollback; preview ownership across disposal, retries and late initialization;
inactive/height-gated camera and foreground acquisition eligibility, focused
viewfinder visibility, repeated overlay cycles, incomplete-pair invalidation,
completed-result preservation after shutdown, fresh-session restart, rapid
suspend/resume ownership, obsolete camera/sensor callbacks and workflow errors;
silence across live sensor-quality statuses and left/right alignment, persistent
near-vertical restriction visibility targets with capture gates preserved; one-time correction identities and expiry, clearing across
restart/stages/suspension, silent no-capture shutdown and the exact 15-second boundary;
silent startup/waiting versus actionable failures; three progressive retries,
exhaustion, manual/new-session/success reset, cancellation and stale retry callbacks;
monotonic startup/interruption deadlines without sleeps; localized height-label formatting, whole/fractional values, display
precision and decimal-only notation; selection availability, inactive startup,
Home mode presentation, mode/method navigation, Back, disabled modes,
activation, Home return and reselection; tutorial first-use eligibility and stable-ID
preference keys, store/coordinator reconstruction, dismissal and manual reopening,
two-page navigation and retained documentation coverage, duplicate documentation opens,
obsolete callbacks after leaving documentation, reversed-transition coverage, method-content registration, retained presentation, settled method-entry acknowledgement,
duplicate/stale callbacks, rapid navigation and acquisition gating through method entry; acquisition observations through workflow/calculator transitions,
alignment/wraparound, interruption, correction and retry; diagnostics tap/hold
routing against persisted preference snapshots, modal gates, retained navigation
without result mutation, stopped acquisition versus discovery/failure,
quality/readiness reasons, invalid/stale/incompatible endpoint suppression, stored
geometry, degree-based sensitivities and Metric/Imperial diagnostic conversion.
Resource tests cover synchronous height-prompt cleanup with and without a
requested decorative fade, rejecting obsolete preview/sensor callbacks and requiring
fresh readiness on reentry. Preview-presentation tests reject streaming before
startup controls settle and reject obsolete draw callbacks after loss/closure/rebind;
later user zooms retain presentation. Workspace tests cover direct initial
confirmation, draft cancellation, prompt return, reversed transitions and restored
cover/acquisition gates. Release-order tests cover image retention through
suspension followed by disposal for height editing, completion, Home, Settings,
tutorial, prompt return and diagnostics; disposal-first cleanup without copying;
and late disposal after a replacement owner starts. These checks do not execute
native preview rendering. Frame-retention tests cover workspace/result coverage,
interrupted entry, ordinary suspensions and Home's destination-owned exit, including
incoming Home inactivity versus actual app backgrounding.
Navigation motion tests cover destination retention, distinct method-list/Home
return segments, card-only navigation, input gates through exit or canceled entry,
settings forward/Back direction and settled restored destinations. These JVM tests
do not execute AnimatedContent or verify retained callbacks and rendered motion.
Alignment presentation tests cover direction/wraparound, filtered correction and
one-time recovery identities, threshold chatter without changing capture gates,
fresh moving/settling readings, invalid/stale observations, session compatibility,
new top/restart, completion and suspension/resumption. Animation rendering and
physical correction usefulness remain manual checks.
Home presentation regression coverage checks a stable outgoing method list on
measurement entry, Android Back returning to methods before height entry, and Home
returning directly to modes with or without saved height. Configured camera stages
route Back to the height prompt; completed results route it to Restart. Preview suspension and guidance
changes pass the bounded JVM, debug assembly and lint checks; these checks do not
render Compose frames. Manually verify that tutorial and Settings openings fade
from the last camera frame without a dark flash, and that Android Back before
height entry returns to the method list, a second Back returns to Select mode,
and Home returns directly to Select mode. Repeat rapid Back during both card
and measurement transitions, with reduced/disabled system motion.

The shared tutorial passed JVM tests, debug Kotlin/resource compilation and
Android lint with `:app:testDebugUnitTest :app:lintDebug`. No device or emulator
was used; rendered slides, constrained layouts and TalkBack remain manual checks
in the method-help row below.

Source and tests define the individual cases. Mocked deployment tests verify script decisions using
fictional devices/configuration, not real wireless reliability.

## Animation responsiveness and rendering

The animation and rendering changes passed the bounded JVM tests, debug assembly
and Android lint invocation above using JDK 17 and API 37. Lint reported warnings
in existing manifest/resources, dependency declarations and bitmap drawable calls,
with no errors. No device or emulator was used. Existing navigation, tutorial, zoom
and resource-release tests cover state contracts; they do not execute Compose
animations or native preview rendering.

Automated checks cannot establish smoother rendered frames or lower tap latency.
On a developer-owned device, compare repeated tutorial buttons with short drags
and flings, then repeat forward/Back menu and measurement navigation. Exercise
interrupted paging, committed/canceled predictive Back, rapid taps, TalkBack,
large text and both normal/exceptional workspaces. Check normal, slow and disabled
system animation scales and available high-refresh modes. Profile frame times
and recomposition counts during Home entry, alignment nudges and live sensor
updates; include opening/closing Settings, Help and the height editor with a live
preview. Inspect main-thread time in synchronous bitmap retention separately
from animation duration. Bitmap retention still precedes cover/unbind on main;
its device-specific cost and overall frame pacing remain unmeasured.

## Known Object Height tutorial illustration

The integrated illustration passed debug Kotlin/resource compilation, the JVM
suite and Android lint using the bounded Gradle invocation with
`:app:testDebugUnitTest :app:lintDebug`. No device or emulator was used.

The supplied Compose frame previews cover height, top observation, rotation,
bottom observation, distance and reset at 320 and 400 dp widths, plus a 240 dp
static preview and an interactive loop, using Viewy's theme. Compilation and JVM
checks do not verify rendered frames, lifecycle frame work or TalkBack behavior;
visual and runtime validation remain unverified.

Manually check the illustration appears after the introduction and before the
numbered steps only on Instructions. Its enlarged container should use more of the
card width without clipping or changing proportions; prose retains its usual margins. Inspect narrow widths and large text with
scrolling; watch several loops for clipping and a fixed lens position during
phone rotation. Close mid-loop, reopen, advance to Important, open/return from documentation, then use the back chevron, and
background/foreground the app: the Instructions sequence should restart. Verify no frame
work below STARTED or after dismissal with a developer-owned profiler. With system
motion disabled, including a live toggle, expect a stable distance diagram;
restore normal motion and confirm looping resumes. In English and Spanish,
TalkBack should announce one complete image description without frame updates.

## Embedded height editor

The embedded editor passed the JVM suite, debug assembly and Android lint with
the bounded invocation above using JDK 17 and API 37. No device or emulator was used.

Focused JVM checks cover numeric drafts, positive finite validation, leading zeroes,
decimal/backspace behavior, locale separators, precise m/cm and ft/in switches,
empty/zero drafts, ten-digit rejection identities, bounded converted text without
physical rounding, global-system changes, cancellation, duplicate confirmation and
unchanged-height no-ops.
Workflow/resource checks cover atomic completed-height correction, original endpoint
and metadata retention, updated diagnostics, failure preservation, acquisition gates
through editor exit, stale preview callbacks and interrupted-pair notices; overlay
preservation of drafts and completed evidence; activation-scoped presentation
identity across Home/Back exits and fresh reentry; complete method clearing, camera
metadata/zoom removal, pending retry cancellation and height-gated reentry. Result
identity checks distinguish a new completion from height correction, including host
restoration. These checks do not render Compose or prove hardware cleanup.

Manual validation remains required; do not infer device results from JVM/build checks.
The camera flash remains a device-reported issue until these rendered paths are
rechecked; successful JVM/lint results alone cannot confirm its resolution.

| Transition | Expected presentation and retained state |
| --- | --- |
| Prompt → keypad → confirm | One cover transition at each step; no prompt button between keypad and camera. Fresh camera image and controls fade in together after startup readiness. |
| Camera → Edit height → cancel/confirm | Last available image remains beneath the incoming keypad until opaque; no immediate solid-color replacement. Saved height survives cancellation; confirmation updates it. Return uses a fresh preview reveal. |
| Camera → Android Back → prompt | Acquisition releases immediately; prompt fades over the last image. Height/captures clear; the next Back returns to methods. |
| Result → keypad → cancel/correct | Result stays composed and returns directly, without another result reveal/count or camera binding. |
| Result → Restart/Android Back | Height remains; result fades out and a fresh camera starts. A subsequent camera Back returns to the prompt. |
| Camera/keypad/result → Settings/Help/diagnostics → return | Covering surface keeps its existing navigation/Back priority. Draft/saved height/result survive; camera resumes only when eligible, with a fresh reveal. |
| Any method state → Home/method exit → reentry | Whole-screen exit retains its outgoing workspace appearance; resources release immediately. Reentry starts with the prompt and no old image/draft/result. |
| Cancel/reverse during entry; background/recreate mid-fade | No stuck snapshot/cover, exposed prompt button or double navigation. Recreated covers/results appear settled; preview startup still waits for readiness. |

Repeat the matrix at normal, slow and disabled system animation scales, in the
normal and exceptional workspaces, including rapid repeated Back, English/Spanish,
permission denial/retry, bright/dim/fully dark scenes and camera recovery. Dark
scenes must not remain hidden behind a brightness-based readiness rule. Use a
frame recording and developer-owned profiling to assess actual image/control
fades and frame pacing; these are unverified by local compilation.

For release handoffs, compare a quick tap with pressing and holding before release
on Edit height, Home, Settings and Help while camera acquisition is active. Complete
a valid second capture as well. Inspect the first frames before each destination's
fade: no one-frame waiting/keypad/result-color fill may replace the retained camera
image. The native preview host should remain attached during stop, completion and
cover transitions; only a new binding replaces its camera child. Use Layout Inspector
or developer-owned tracing to check this separately from visible frame recording. Repeat after camera startup has fully settled and during repeated entries,
at normal and slow motion scales. Repeat the controls from the height prompt,
keypad and completed result to distinguish live-preview teardown from button feedback.

1. On first use, dismiss the tutorial. Check the centered height icon, heading,
   explanation and **Enter height** action inside the existing rounded workspace.
   Check English/Spanish, compact and landscape windows, and larger accessibility
   font sizes: text must wrap without clipping, and the group must scroll when it
   cannot fit. TalkBack must skip the decorative icon, identify the heading and
   reach the description and button. Tap **Enter height**. Expect an empty embedded keypad and no permission prompt, camera or IME.
   Cancel with the arrow/Android Back; reopen and confirm a positive fraction.
   Empty and zero must disable Enter immediately and fade it dim over 150 ms;
   a valid input fades it back, while number/unit controls keep their colors.
   Repeated confirmation must apply once. Enter ten digits, then another: retain the value,
   shake only the number within its reserved margin, never under the unit label,
   and expose the localized limit to TalkBack. Backspace
   must permit another digit; the decimal separator must not consume a digit.
2. Switch 1.75 m ↔ 175 cm and, with Imperial selected in Settings, 5.5 ft ↔ 66 in.
   Repeat switches and cancel/confirm; the selection surface must slide between
   fixed labels in neutral surface colors, and local changes must leave Settings
   unchanged. Unit buttons should be rounded rectangles. Tap each unit near the
   edge and center: no touch ripple or pressed feedback should appear, while the
   selection surface still moves and TalkBack announces the selected unit. Converted text
   must have at most four decimals while untouched confirmation retains stored
   precision. Check English point and Spanish comma display, tiny converted values and
   hardware digits, decimal, Backspace, Enter, Escape and Tab/directional navigation.
3. Edit live camera height, cancel and confirm, then repeat after top capture.
   Acquisition must stop immediately, controls become inaccessible and the pending
   pair clear. The shutter caption must fade out while editing, fade back on return
   and crossfade between top/bottom instructions without moving the workspace.
   Return must explain the lost capture once through existing guidance;
   capture requires a fresh session. Exercise camera permission/retry recovery too.
4. Edit a completed result. Cancel must preserve its value and observations;
   doubling height should double distance without recapture, camera startup or a
   repeated result entrance/count-up. Unchanged/unit-only confirmation is a no-op.
   An unusable correction must leave the previous measurement available.
5. Repeatedly open/close, press Back during entry, tap rapidly, background/resume,
   recreate the Activity with a draft and return through method navigation. Open
   Settings, Help and diagnostics from the keypad: retain the draft, physical value
   and local unit; prevent covered edits and keep acquisition stopped. Return to the
   same editor. Change global units in Settings: convert the retained draft into
   the new base unit with at most four decimals without committing it. Repeat from
   camera and completed results: retain saved height/results; an unfinished top
   still clears under the existing interruption rules. Home from all three states
   must clear height, draft, captures, result and camera diagnostics. Reenter with
   no acquisition and an empty first height entry. Global preferences and tutorial
   completion must remain. Restart clears the draft/captures/result but keeps height.
   From live camera, use Android gesture Back before and after top capture: return
   directly to Enter height, clear height/captures and release acquisition.
   Watch frame-by-frame for the 225 ms prompt cover over the last available image,
   with controls fading out and no immediate solid-color replacement or black flash.
   Without an available image, expect the prompt over a themed waiting surface. Repeat
   during startup, slow/disabled animations,
   repeated reentry and alternative developer theme palettes. A new preview must
   not retain the shutdown cover. The next Back exits to the method list. Separately open the camera's Edit height
   keypad, change the draft and use Back/arrow: retain saved height and return to
   fresh camera acquisition, without the initial prompt. Initial-entry cancellation
   returns to its prompt; result editor cancellation retains its result. Repeat in
   both units, during startup/recovery and after Settings/Help or Activity recreation.
   Home still clears the entire method without crashes or null-height labels.
   Repeat Home after a result, including during its entrance, then reenter rapidly: the
   first frame must be fresh height entry, with no brief old distance or result exit.
   Repeat in the exceptional workspace and with slow/disabled system motion.
   Check rapid taps, late callbacks and overlay returns retain their existing state.
6. Watch camera ↔ keypad and result ↔ keypad transitions at normal, slow and
   disabled animation scale. Repeat initial height confirmation and camera Edit
   height cancellation/confirmation, including rapid reopenings and slow startup.
   Expect stable bounds and an opaque native themed cover while waiting for the camera;
   image and controls must fade in after startup readiness (including zoom settling), without a black flash or
   an abrupt image replacement. No previous preview may appear as a new live stream.
   Background or open Help/Settings during the fade, then return; check that no
   cover or snapshot remains stuck. These rendered frame checks require a device.
7. Repeat in small/split windows, landscape, large text, cutouts/system insets and
   the exceptional workspace. The keypad must adapt proportionally, remain fully
   visible and never move or stretch under finger swipes. All keys and Enter must
   remain reachable without overlap or scrolling. Type and convert
   long values rapidly: the unit label must stay fixed and never receive overflowing
   numeric pixels. With TalkBack verify value, units, decimal, distinct backspace and
   cancel actions; underlying camera/result/Capture targets must be absent or disabled,
   while header, Home and Restart remain usable.

## Measurement result presentation

The result presentation passed the JVM suite, debug assembly and lint with the
bounded Gradle invocation above using JDK 17 and API 37. Lint reported no errors;
remaining warnings concern existing platform and dependency declarations. No
device, emulator or instrumentation validation was performed by the agent.
The developer reports that the result flow works as intended after manual testing;
device/OS and specific coverage were not recorded. The distance thresholds, control
spacing and adjusted timing still need the targeted device checks below.

Focused JVM coverage checks localized decimal result/height formatting, HALF_UP
rounding, integer versus non-integer padding, tiny positive values, large grouped
magnitudes, exact kilometer/mile thresholds and Metric/Imperial conversion (including display overflow beyond
Double in feet). Counter-frame tests check monotonic display, stable decimal scale
and exact final formatting with the destination unit fixed throughout counting.
Primary-distance regression checks cover padded one-decimal/whole values, rounding
carry, English/Spanish formatting and independent compact supporting quantities. Presentation policy tests check once-per-completion
entry across height corrections, settled host restoration, equal-valued new-result identity during exit,
and count/height/separation/disclaimer ordering.
Existing workflow/resource tests cover rejected second captures, immediate release,
completed-result retention across suspension, Restart with saved height, height-save
reset and rejection of obsolete camera callbacks. These tests do not execute
Compose rendering, system animation scaling, Android restoration or CameraX.

Developer device checks remain required, in English and Spanish, both units,
normal/compact/exceptional windows and large system fonts:

1. Capture top then a valid bottom. Verify immediate hardware release and removal of
   reticle, zoom, guidance and the preview-edge editor. The result color should fade
   directly over the last available preview image without a black gap; discard the
   retained image when the result entrance finishes. Confirm the rounded container and
   bottom controls do not move. Check the large approximate horizontal distance
   appears after the surface fills over 450 ms, then counts from zero over 750 ms with deceleration.
   Short values should use the enlarged numeric type; long/grouped values, all unit
   labels and English/Spanish decimals must fit or scroll inside the section,
   with no clipped glyphs, changing counter width or overflow. Confirm disabled
   system motion still shows the final value immediately. With normal motion,
   height, elevation difference and disclaimer fade in separately after counting,
   in that order. Compare the separation with the
   stored Diagnostics value; individual endpoint angles stay out of the panel.
   A rejected bottom must retain the acquisition/correction interface.
2. Tap Restart or use Android gesture Back during the counter, during each reveal
   and after completion. Both must retain the method and saved height, clear the result
   and prompt a new top capture. A subsequent Back from acquisition reopens the
   height prompt. Home from results still clears the whole method.
   Repeat in the exceptional workspace. With Settings, Help, Diagnostics or the height
   keypad open over a result, Back must close/cancel that surface and preserve the result.
   When restarting, the entire visible result must fade out together over about 225 ms while a single
   fresh acquisition starts immediately, retaining height. With deliberately slow
   camera readiness, verify no old preview frame is presented as live; streaming
   fades in only when ready. Repeated Restart, overlays and rapid background/return
   must not create competing bindings or leave partial/stuck results.
3. Open Settings/help/Diagnostics and return; change units and language with a
   result displayed. Formatting must update without replaying entry or acquiring.
   Background/resume and recreate the Activity with a completed result, including
   during entry: retain the measurement and show it settled after recreation.
   Collapse/reopen the exceptional workspace without replaying the counter.
4. Edit the result’s object-height row: Cancel retains the result; Enter recalculates
   with the original pair, preserving completion and keeping acquisition stopped.
   Restart, including Android Back from the result, stays the sole retry action;
   Capture must remain disabled throughout a completed result.
5. With TalkBack, check one final approximate horizontal-distance announcement,
   no spoken counter frames, hidden rows absent until reveal, meaningful elevation
   text and a clearly labeled height-edit action. Outgoing camera/result controls
   must not accept touch, keyboard focus or accessibility actions. With animation
   scale zero, final distance and all three supporting elements appear together;
   repeat with reduced and increased system duration scales.
6. Check primary distances such as 9.80 and 9.00 with animations enabled and
   disabled: the final number must fill the decimal width reserved during counting,
   with balanced spacing to the approximation marker and unit. Repeat in Spanish
   (9,80), feet, kilometers/miles and with large fonts. Supporting height/angle and
   editable height input must retain their compact formatting.
   Check readable distance/unit placement, localized grouping/decimals, bottom
   disclaimer, scrolling when constrained, and large/tiny displayed values. The
   approximation marker and disclaimer must remain visible, without confidence or
   accuracy claims. Check results just below/at/above 10,000 m and one mile
   (5,280 ft), including unit/language changes; units must stay fixed throughout
   counting. Check long heights and Spanish labels: height and zoom must keep a
   gap; the height action must shorten only its display and retain an accessible
   edit description. Verify opening/saving untouched height input preserves the
   full stored value.

## Camera zoom verification

JVM tests cover the 1× floor and full CameraX maximum (including above 20×),
fixed/invalid/entirely-sub-1× ranges, clamping of repeated pinch and button requests,
button/pinch target composition, latest-request
ownership, acknowledgement and settling deadlines, 1× startup/reset independent
of reported minimum (including a 0.6×–30× regression case), a higher camera
minimum when 1× is unavailable, closure/replacement callback rejection, top-capture locking,
Restart and pair-expiry unlocking, completion rejection, capture enablement and
preservation of camera/sensor ownership through zoom publications.

Galaxy S22 physical validation remains required; no device was accessed for this
change. With Known Object Height configured:

1. On fresh activation and after each camera rebind/reopen, verify the label starts
   at 1.0× and compare the view with an explicitly selected 1.0×. Check returning
   after both minimum and high zoom. A higher supported minimum must be used and
   displayed when necessary. Verify minus is disabled at 1× and repeated zoom-out
   pinches cannot select 0.6× or any sub-1× ratio. The upper limit must match the
   configuration's CameraX maximum, including above 20× where supported; check both
   limits, rapid alternating button taps and continuous pinch. Confirm buttons,
   bounds and persistent ratio label stay synchronized. Confirm the label remains
   visible after waiting and the nearly opaque controls remain readable. Check TalkBack labels,
   current-ratio descriptions and touch targets in English and Spanish.
2. Keep the phone fixed and center a distant landmark under the reticle. Sweep
   zoom slowly and rapidly, checking preview-center drift, responsiveness and any
   delayed/cropped-frame jumps after the shutter re-enables. Repeat in low light.
   Acknowledgement alone does not prove visual magnification. The 350 ms guard is
   not a validated preview latency bound or proof of stability.
3. Compare logical/physical IDs and mapping in Developer Options → diagnostics at
   different ratios. Diagnostics intentionally releases the camera; each return
   resets zoom. Check that reported lens changes update the mapping and clear old
   orientation samples without an orientation-unavailable flash. Fresh readings
   and settling must precede capture. Test transitions in both zoom directions;
   actual lens choice and switch timing remain device-controlled. Missing/unknown
   active IDs must block acquisition; actual sensor failures must still offer Retry.
4. Verify capture stays disabled throughout zoom application/settling; capture top
   and confirm buttons and pinch cannot change zoom. Complete bottom capture and
   confirm zoom controls disappear. Restart, pair expiry and invalidation must
   permit zoom again; the displayed result and alignment behavior must be unchanged.
   An automatic reported lens change after top must discard the incomplete pair,
   even when the requested zoom stays fixed.
5. Background/resume, rotate/recreate, open/close Settings/help/height editing and
   exercise permission loss and camera contention. Confirm immediate release,
   safe reset at rebinding, bounded recovery and rejection of obsolete zoom work.
   Check repeated overlay cycles for preview freezes and resource leaks.
6. Open/close Settings, modes, help and height editing, and lose/restore window
   focus. Check that zoom buttons/ratio and the height editor start fading with the
   preview, all taking 225 ms in either direction. The outgoing ratio must not
   jump to 1× before fading out. Reverse transitions rapidly and try tapping or
   focusing outgoing/entering controls: they must not act until fully visible.
   Repeat in compact/exceptional layouts and with system animations disabled.
   Confirm height can still be edited after completion or during permission/camera
   recovery. JVM visibility-policy tests cover shared startup/suspension gates and
   these idle editing exceptions; actual animation timing and touch/accessibility
   rendering remain manual checks.

## Recorded verification and its scope

Alignment chevrons, their presentation-only transition filter and the 0.45° capture
alignment gate passed the JVM suite (including workflow/capture eligibility checks), debug assembly
and lint using the bounded invocation above with JDK 17 and API 37. Lint found
no errors; warnings concern existing manifest and dependency declarations.
No device, emulator or instrumentation validation was performed. Direction,
animation rendering and handheld usefulness require the guidance checks below.

CameraX zoom controls with a 1× floor and uncapped supported maximum, the
persistent ratio label, 1× startup/reset policy, preview-control visibility gates
and capture/session guards passed the JVM suite, debug assembly and lint
using the bounded invocation above with installed JDK 17 and
API 37 SDK. Lint reported no errors and retained warnings for dependency updates,
backup guidance, portrait orientation, the locale-config attribute and a discouraged
platform API.
No device, emulator or instrumentation validation was performed. The Galaxy S22
checks above remain outstanding.

Quiet aiming and event-driven workflow errors, including progress-aware
interruption handling, passed the JVM suite, debug assembly and lint with bounded,
sequential Gradle execution.
Lint reported no errors; warnings concern dependency updates, backup guidance,
the intentional portrait request, the API-33-only locale-config attribute (older
versions use AppCompat), and an existing discouraged platform API. This used the
installed JDK 17/API 37 SDK; debug/release assembly also passes for the unpinned
CameraX preview. A clean build was not verified.
The previously recorded merged-manifest inspection found CAMERA plus an
app-private signature permission, with no Internet, network-state, microphone,
location or storage permissions. Deployment-script mocked unit tests and Bash
syntax checks were also previously reported passing; they do not verify a
physical connection.

Deployment-script results are retained reports. Instrumentation tests are not
implemented. Tutorial persistence tests use a preference-API fake to verify keys
and reconstruction; they do not exercise Android disk writes. Pure JVM tests do not prove
Android registration, camera behavior, rendering or measurement accuracy. Navigation
rendering, Home/tutorial Back dispatch, outside-card hit testing, animation/inset
behavior, scroll resets, launcher/control-row positions, actual Activity retention,
Android tutorial preference-file durability and legacy-preference upgrade behavior require
the manual checks below; there is no instrumentation harness. DataStore persistence tests exercise real temporary JVM files; they do not establish
Android disk behavior or platform data-clear outcomes.
Locale resolution tests cover ordered device preferences, English fallback, explicit
override precedence, empty legacy overrides and selection without navigation.
The focused Settings/localization and navigation-motion JVM checks and debug
assembly and debug lint pass; lint retains existing warnings. APK inspection
confirms explicit English and Spanish resource
configurations without dependency languages. These checks do not exercise Android
locale storage or a real process restart.
Settings lifecycle, actual
in-place locale updates across supported OS versions, unrelated Activity recreation,
per-app system integration,
modal rendering and
permission revocation require the manual checks below. Fresh-storage defaults in
JVM tests are not evidence that Android completed a clear request. Only one method
is implemented, so switching between
different real workflows cannot yet be exercised; JVM ownership tests cover
revocation of both pending and ready previews.

The developer confirmed that the cold **automatic device language → Español** change
completes without a black frame on Android 16. This visual check on the reported
device does not establish the broader locale/lifecycle matrix below.

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
Use [README deployment instructions](../README.md#install-and-run). Enable
**Developer Options → Enable diagnostics** and hold **?** for read-only snapshots.
A/B recording and sensor-source controls are absent. Controlled sensor comparisons
or fault injection require a separately authorized external harness; diagnostics
itself cannot start discovery, switch sources or restart acquisition; opening it
suspends acquisition, so hardware sections show stopped/last-known information
and live sensor readings become unavailable. The live source, timestamp, ray and
quality checks below require a separately authorized external harness or tracing;
the overlay cannot verify an ongoing session. Completed endpoint diagnostics remain
available from their stored observations. Avoid changing unrelated device settings. Use a fixture or helper to reduce capture-button motion.
Stop for about one second before capturing and remain still briefly afterward.
For sensor-only checks, use an external sight line parallel to the rear axis;
the diagnostics screen alone cannot establish optical calibration.

Record device/OS and app build, active sensor name/source, logical/physical camera
IDs, pose/reference or fallback, fixture/handheld setup, lighting/focus and preview
behavior. For each run record target height and horizontal reference distance with
their uncertainties, displayed estimate and diagnostic values, endpoint elevations,
separation, bearing error, captured RMS/max variation, duration, and all rejections.
Keep records externally; the app has no history/export feature.

| Area | Procedure and expected behavior to check |
| --- | --- |
| Height-gated resources | On a fresh workflow, confirm a colored height-entry rectangle, no preview, no orientation session and no camera permission prompt. Cancel/invalid input must leave resources stopped. Confirm in the global Metric/Imperial system to start preview; editing must suspend acquisition and discard incomplete captures; Cancel retains height/results; Enter resets unfinished captures or recalculates completed results from the same pair. Repeat during pending initialization and with camera retry/background/resume; stale callbacks must not restore readiness after release. |
| Permission and camera recovery | Deny/retry/grant, permanent denial/settings recovery, background during prompt, and revoke/resume. Confirm disabled captures while unavailable; retry camera initialization/errors. No photos, audio or storage access should be requested. |
| Preview and physical lens | Fixture a distant plumb line/cross. Check centered reticle, symmetric crop, no stretching and rounded clipping in portrait. Verify the portrait request during rotation; exercise other orientations only where the platform overrides it. Slowly cover rear lenses without moving the phone to identify the imager; compare IDs/metadata. Vary light and target distance and watch for lens/field-of-view jumps. Matching IDs do not establish calibration. |
| Optical-center behavior | Rotate around a known fixed sight line and record target movement at the reticle. Repeat with focus/light changes. Movement combines fixture, axis, principal-point, distortion and processing effects; it does not isolate their causes. |
| Stationary sensor directions | Fix screen-up, screen-down, upright and tilted. Check fresh times, settling then READY, and actual variation. Under fallback mapping rear-down is −90° elevation and rear-up +90°; bearing may be unavailable. Do not interpret low variation as angular accuracy. |
| Pitch, roll and display | Tilt rear aim up/down: elevation should rise/fall. Roll about a fixed sight direction; ray should remain approximately constant after settling. Rotate while portrait is requested; where the platform overrides it, repeat both landscapes and 180° rotation. Check image rotation and physical signs; recreation/rebinding must clear captures. Natural-landscape/foldable devices need separate checks. |
| Bearing and alignment | Slowly yaw left/right, then return to the top endpoint's vertical line. Check silent aiming and capture gating, including crossing ±180° (small wrapped difference, not 360°). Roll must not change the alignment gate. Interpret bearing using atan2(Y,X), not compass azimuth. |
| Endpoint geometry and controls | Use a measured plumb height; fix camera position and capture top then bottom. Try reversed/same endpoints, very small separation, near-vertical aim and duplicate taps: expect correction/blocking. Test Restart, height editing and unit changes; results should have no separate bottom-recapture action. Diagnostics should show the production top/bottom elevations and stored geometry; unavailable endpoints must remain pending or unavailable. |
| Handheld readiness | Repeat supported/unsupported handheld captures with ordinary aiming, tremor and taps. Observe moving/settling/ready and blocked attempts. Near straight up/down, elevation/ray should remain finite and bearing should be suppressed; production can block before the numerical pole. Provisional gates are not calibrated acceptance thresholds. |
| Interruptions and resources | Capture top, then lock/unlock, display rotation, camera retry, normal camera privacy/access interruption and orientation recovery retry. Check incomplete pairs clear, entered height remains, fresh preview/settling restores readiness and stale captures cannot finish. Repeat after a completed result: its value and captured diagnostics must remain available and acquisition must stay stopped until Restart, including after correcting height. Home separately clears height, captures/results and hardware metadata; reenter through fresh height entry. Repeat pause/resume and inspect for listener/lifecycle errors. Wait past 15 s before bottom and check restart guidance. |
| Foreground visibility and resource fades | On Galaxy S22, repeatedly open/close Settings, diagnostics, both tutorial pages and documentation, selection and height editing, including rapid reversal during startup or fades. Verify a subtle last-image fade after immediate camera release and fade-in only when streaming resumes, without preview resizing, duplicate binds or a late obsolete preview. Use the Android camera privacy indicator and developer-owned profiler/registration tracing to verify camera closure and listener/timer removal; diagnostic flags alone cannot prove physical cleanup. Home, lock, task switching, notification shade/focus loss and unrelated Activity recreation must release immediately without waiting for animation. Return while an overlay or completed result remains visible: acquisition must stay stopped. Repeat in multiwindow, with reduced/disabled animation scale, and in the exceptional workspace: collapsed means stopped, its focused open dialog may acquire, and its overlays suspend. Permission denial/revocation/settings recovery and camera/orientation retry must still work; fresh observations/settling must precede capture after recovery. Confirm TalkBack cannot reach covered controls or decorative frozen images. |
| Delivery failure | In a disposable emulator/fault harness stop sensor delivery beyond the freshness limit, then resume. Expect STALE and blocked capture, followed by a new frame and settling. An already configured Sensors Off tile can help; configuring it is not required. JVM rules do not prove platform callback behavior. |
| Missing hardware and registration | On sensor-/camera-limited hardware or a harness, test no rear camera, game-sensor absence with rotation-vector fallback, neither sensor available, and failed registration. A running source must not silently switch on stalls. Timestamp/registration fault injection needs a harness; use pure tests only as evidence for rejection logic. |
| Repeatability and return | Use a separately authorized external harness to record fixed-aim differences over 5/10/15 s and return-to-aim comparisons for each available source. The read-only app has no A/B or source-selection controls; no measurement pair may span sources. Initial rotation-vector runs should avoid magnetic objects; record controlled magnetic disturbance separately. Differences combine return, fixture and sensor effects, not independently measured drift. |
| Reference distances and repetition | Independently measure horizontal lens-to-target-line distance with tape/range instrument at several safe distances/heights and positive/negative endpoint elevations. Collect multiple full measurements with independent re-aims (for example ten per setup), recording every result/rejection, signed/absolute relative error and spread. Compare short/long capture durations and fixture/handheld results. Viewy output must not define its own reference. |
| Lighting and focus | Repeat bright/dim/backlit scenes. Observe autofocus, lens breathing, center movement and preview delay. Wait for a clear stationary image; no claim ties the captured orientation to an earlier displayed frame. |
| Home and method navigation | Start a new process, including an upgrade with a legacy saved method ID: expect Home, wordmark, Settings and a centered rounded card fading in over 300 ms without vertical movement. Returning from measurement with Home retains the existing vertical slide/fade; Android Back before height entry slides measurement right while the method list fades and slides in from the left over 270 ms. Confirm the launch fade does not replay on Activity recreation, background/resume or in-app returns; check normal, slow and disabled system animation scales. Android controls the separate launcher/task window transition. No camera placeholder, capture controls, permission request or acquisition. Long Distance slides left to Known Object Height; card Back and Android Back reverse to modes. Compare frame pacing on repeated forward/Back taps; the menu slide remains 220 ms and its reported unevenness requires device profiling. Back on Home exits normally. Unavailable modes stay visible, disabled and labeled Coming soon. Select the method: the outgoing method list stays in place and fades without switching to modes; the entire workspace fades and subtly enters from the right over 270 ms; Home fades it out and restores all modes. Android Back before height entry restores the method list; another Back restores modes. With saved height, camera Back clears height and returns directly to Enter height; Back from Edit height cancels the draft and returns to camera; result Back restarts measurement; the height editor and status dialogs must dismiss first and tutorial Back must retrace its pages. Capture a top endpoint, return Home and reenter: height and all captures are cleared, and camera remains stopped until fresh height confirmation. Repeat after completion and while editing: no old result or draft may return. Android Back leaving the method also clears its session. Settings and tutorial returns preserve saved height/results and a draft; incomplete pairs still clear on suspension. Open/close Settings from Home, Methods and Measurement: restore the correct destination without flashing another screen. Exercise rapid taps/Back, interrupted navigation, Activity recreation at each level and mid-transition, background/resume, compact/split windows, large text, English/Spanish and TalkBack. Moving/outgoing destinations must reject touch, keyboard and accessibility actions; lists must scroll within stable card bounds. Sensor/unit/locale updates must not replay navigation. Recreated destinations appear settled; reduced/disabled animation scale remains functional. Use privacy indicators and developer-owned tracing to verify immediate physical camera/listener release before exit finishes, no duplicate bindings and no stale callback revival. After process death, launch Home again. JVM tests verify state/resource policies, not rendered smoothness, frame pacing or physical cleanup. |
| Method help and completion | On fresh data, the two-page tutorial opens only after method entry. Compare the 220 ms button slide with finger-driven motion after reopening and on repeated visits; both pages should be ready when movement starts and offscreen Instructions should stop its illustration clock once settled. Follow the tutorial navigation checks below, including interactive drags, settled page retention, documentation coverage and completion only on dismissal. Reopening starts at Instructions. Check English/Spanish, normal/reduced/disabled motion, narrow windows, large text, TalkBack, rapid input and Activity recreation. Camera/sensor acquisition stays stopped, saved height/results remain and unfinished captures clear. |
| Help layout and measurement preservation | Confirm **?** immediately left of the settings gear, with the existing spacing, a smaller visible circle, a 48 dp touch target and a TalkBack **How to use** click label. A normal tap always opens help regardless of diagnostics preference; disabled holds open neither help nor diagnostics. With valid height and a live preview, open/read/dismiss manual help: preview bounds, header, stage and bottom controls must stay stationary; the Home control stays composed in its usual position. Repeat with a top capture and result: preserve height and completed results, discard incomplete pairs immediately, and verify acquisition stays stopped on both help pages. Dismissal starts a fresh camera/sensor session only for an unfinished measurement. Pending first-use help must prevent camera initialization. Test small/split windows, large fonts/display scale, TalkBack, cutouts and gesture/three-button insets; content must scroll, page navigation remain reachable, titles remain centered and the two pages use the same card/window. The documentation screen must show localized reference sections with a fixed header while its body scrolls independently. Repeat from the exceptional viewfinder workspace and its collapsed presentation. JVM state checks do not establish these rendering or physical-session properties. |
| Diagnostics access, state and layout | With diagnostics off, hold and release **?**: no tutorial, diagnostics or haptic. Enable it in Settings: tap still opens Instructions; hold opens exactly one dark full-screen Diagnostics surface with method name and Back, a haptic and a separate TalkBack long-click action. Repeat quick taps/holds and after relaunch; tutorials, Settings and mode selection must never stack with diagnostics. Before height entry, inspect inactive prerequisites and undiscovered sources without a permission prompt or acquisition. With live preview, top capture, correction and result, open/close repeatedly: verify unchanged preview bounds, stopped camera/sensor work, preserved height/completed result, immediate incomplete-pair invalidation and blocked background controls. Background/recreate with diagnostics open: navigation and completed results remain while acquisition stays stopped. Completed endpoint geometry must stay readable without a live frame. Disable the preference and verify access is removed. Check all four sections, missing values, unavailable physical IDs, mapping evidence, stale/moving/unreliable versus failure, platform accuracy and poles. Verify stored geometry and degree-based derivatives in Metric/Imperial and English/Spanish. Repeat in compact and exceptional workspace layouts, large fonts, TalkBack, small/split windows, cutouts and system navigation insets; Back and all rows must be reachable by scrolling. No recording, source selection, restart, export or threshold controls should appear. JVM checks exercise routing and retained snapshots, not physical gesture arbitration, haptics, TalkBack, actual Activity retention or camera composition. |
| Screen navigation motion | Open and close Settings and diagnostics from inactive/height-gated/live measurement as applicable: check a smooth short fade/vertical movement, stationary preview and no blank exit frame. Enter Language and Developer Options, then use in-app and Android Back: check opposite horizontal motion, stable bounds and no measurement flash. Rapid taps, repeated Back and taps on outgoing/background controls must not navigate twice, edit preferences or dismiss the exceptional workspace. Check the same transitions from the exceptional viewfinder with its matching Activity backdrop. Change units, diagnostics preference and language in place: these updates must not replay navigation. Recreate with a destination open: it should appear settled while normal hardware interruption rules still apply. Repeat with animation scale disabled/reduced, large fonts, small/split windows, TalkBack and system insets; input and accessibility must recover after completion. Rendering, frame pacing and actual input/Back arbitration require manual validation; JVM tests verify the presentation policy only. |
| Settings and units | Open Settings from Home, before height entry, with live preview, after top capture and after a result. Confirm the full-screen dark surface, Back hierarchy through Developer Options/language selection, suspended camera/sensor work and no overlapping help/selector interaction. Repeat from the exceptional workspace dialog. Toggle units repeatedly: all height/result labels and the next height entry use meters or decimal feet; saved physical height and completed results survive; an incomplete pair was invalidated on Settings entry. Local m/cm or ft/in entry controls must not change Settings. Check point/comma input and the decimal result presentation policy. Restart the process to verify saved units/diagnostics and inactive startup. Check slow initial preference loading does not flash incorrect units. |
| Language and recreation | On API 31–32 and 33+, test fresh storage and an existing installation with an empty app-locale override. Before making any manual selection, verify device Spanish → Español selected, English → English selected, unsupported-only languages → English, French/Spanish/English → Español and French/English/Spanish → English. Only English and Español may appear in the picker and summary. Change device languages and preference order without selecting an override: effective text and radio selection must follow, including after a process restart. Select the already active language, then change the device language: the explicit selection must remain. Repeat with each manual language, restart the process, and verify persistence. On Android 13+, select each per-app language in system Settings and verify the in-app selection; clear the system override and verify automatic behavior resumes. Cold-launch with no override, open Language and select Español: no black frame, window replacement, Activity recreation, interrupted transition or page reset. Repeat switching English/Spanish with Home, a selected method, a height draft and a completed result, including in the exceptional workspace: preserve destination, method, height, draft and completed result; acquisition stays suspended and incomplete pairs were invalidated on Settings entry. Check tutorials/documentation, permissions, guidance, errors, accessibility and decimal formatting independently of units. Back must return to main Settings. Separately recreate for an unrelated configuration change: retain navigation and height while normal hardware interruption rules apply. Repeat with background/resume, rapid Back and large text. Android persistence, resource matching and rendered continuity require these manual checks; JVM tests cover resolution and state policy only. |
| Developer Options and full reset | On a disposable test installation, set Imperial, Spanish, diagnostics enabled and tutorial completion; grant camera access and create app-private test files/cache if a harness is available. Open Erase all app data, cancel/outside-dismiss/Back and confirm all data, language and permissions remain. Only an explicit Erase all data confirmation may request deletion; duplicate taps must be blocked. Confirm Android closes the process without relaunching or a success claim and leaves the app installed. Manually reopen: Home, Metric, diagnostics off, device language, tutorials eligible and camera permission requested only after valid height. Verify app-private files/cache are gone and permissions revoked in system settings. Repeat on pre-33 AppCompat storage and 33+ system-managed locales with a device language different from the override. Platform rejection/error behavior is covered by injected JVM fakes; do not invoke the real deletion API in automated tests. |
| Layout and accessibility | Check the consistent dark palette under both system themes, large font/display sizes, TalkBack labels, small/split windows, platform-overridden orientations, proportionally fitted static keypad and navigation insets. On the normal screen, capture/navigation and the stage area must stay visible without scrolling the screen; constrained result content can scroll inside its container. Workflow errors should remain readable near the preview edge without moving controls or covering the reticle; moving/settling/alignment changes must remain silent. Verify persistent recovery actions and status dialogs in compact layouts. Exceptional windows/text scaling offer **Open viewfinder** with scrollable details and fixed controls. Check centered stage instructions and the in-container ≈ horizontal result in both units, a blank reserved caption area before height entry and unchanged preview bounds as top/bottom instructions appear or switch (also with large text), closer symmetric Restart/Home placement, the solid shutter disc, enlarged initial height action, the rounded borderless height editor and the raised control row. Check the larger circular gear retains its size and position, uses a subtly richer purple than other tonal buttons, looks enabled and opens Settings. Whole heights should display one decimal, fractional heights should retain meaningful digits up to the existing precision cap, and opening height editing should cap converted text at four decimals while preserving the exact stored physical value. Check the bold rounded Viewy header and reduced preview at normal/large text sizes. No separate result-retry action should appear. Diagnostics must not push Capture offscreen. |

## Measurement guidance checks

Physical rendering and handheld usefulness remain unverified. With valid height,
check both English and Spanish, normal/compact layouts, large text, TalkBack and
reduced/disabled motion:

- Move, pan continuously, settle and wait for readiness: only the stable top/bottom
  instruction should remain as text. Alignment changes must not produce left/right
  messages. Before top capture there must be no chevrons. After top capture, pan
  physically left and verify a right-pointing cue, then pan right and verify a
  left-pointing cue. Check the gentle outward nudge and smooth direction changes
  during both slow and rapid movement; abrupt motion may invalidate the pair under
  existing session rules. Recover alignment: both chevrons must briefly appear
  and fade together once, leaving only the reticle. Initial aligned top capture
  and sustained aligned readings must not replay success.
- Oscillate around the 0.45° alignment boundary: brief changes must not flash cues
  or success, while Capture still follows the actual alignment/stability gates.
  Repeat at 1× and high supported zoom (set before top capture); chevrons must
  remain clear of the aiming point and must not move or resize the preview. Check
  white interiors and black outlines on bright/dark scenes, the crosshair's rounder
  tips and unchanged center/bounds, reduced/disabled system motion (static direction
  when disabled), and TalkBack: no separate chevron focus targets or repeated
  sensor announcements; Capture must expose the correction direction.
- Aim near straight up/down, then leave the restricted range: **Cannot align at
  this angle.** / **No se puede alinear en este ángulo.** must appear immediately,
  remain visible throughout the restriction and clear immediately on leaving it.
  Capture must remain blocked while invalid. Missing, stale and unreliable
  readings must also stay silent while still blocking capture. Hardware failures
  must retain diagnostic information and recovery controls.
- Before any top capture, background/return and open/close Settings, tutorials,
  mode selection and height editing: no interruption correction. Repeat after top:
  discard the pair and show one capture-cleared correction on return. Genuine
  camera loss should explain camera interruption. Neither should affect a result.
  Repeat while a directional cue and while the success fade are visible, including
  focus loss, Restart and a new top: hide/cancel immediately, with no stale
  confirmation on return. Near-vertical, stale/unreliable data and completed
  measurements must hide chevrons; returning to aligned data alone must not replay
  success. Physical rendering, frame pacing and hardware interruptions remain
  unverified by JVM tests.
- Trigger reversed/too-close/unusable endpoint rejections in a controlled harness:
  corrections must appear once and expire without reappearing on later readings.
  A fresh rejection may show the same reason again. Restart, successful stage
  changes, suspension and completion must clear obsolete messages. Check retained
  error text in compact status dialogs, alignment explanations in Capture
  semantics and no repeated TalkBack announcements for unchanged text.
- Confirm preview dimensions, reticle and controls remain fixed when errors appear
  and clear; large text should use the existing status dialog instead of covering
  the reticle. Confirm Android accessibility timeout preferences extend correction
  visibility, and language changes cannot restore an old error. Error
  presentation should fade in and out over 150 ms without changing the existing
  correction interval or accessibility timeout. With motion disabled, messages
  should appear and clear immediately. Check rapid restriction entry/exit and
  repeated corrections during a fade; no stale text should reappear.

## Galaxy S22 camera startup and recovery checks

These checks require developer validation; JVM policy tests do not establish
CameraX callback timing, actual binding, hardware cleanup or rendered fades.

- Cold-start an unfinished measurement with valid height and permission. Wait for
  preview frames: no Starting/Waiting text, warning icon or camera Retry should
  appear. Confirm a smooth streaming fade-in and no avoidable black flash.
- Open and return from Settings and both tutorial pages and documentation; repeat rapidly, then
  repeat with selection, diagnostics and height editing. Check fade-out, immediate
  hardware release, silent reactivation and no stale error flashes or queued retry.
- Interrupt the camera using another camera client or a controlled debug fault.
  Confirm quiet bounded recovery, a fade-in on success, and a concise localized
  error plus manual Retry only after the initial attempt and three retries fail.
  Confirm manual Retry starts a new cycle. Brief stream transitions should not
  repeatedly bind; sustained loss should recover after the three-second grace.
- During a pending retry, open Settings/tutorial or background the Activity.
  Leave it covered/backgrounded beyond the retry delay: acquisition must remain
  stopped. Return and verify one fresh session. Repeat rapid foreground/background
  and overlay transitions; verify no competing bindings or revived stale preview.
- Revoke camera permission in Android settings and return. Confirm permission
  controls, blocked captures and no automatic retry loop. Restore permission and
  verify silent startup. Check missing/unknown active-camera identity separately
  if reproducible; recovery must never use an unknown aiming-axis mapping.
- Interrupt after a top capture: discard the incomplete pair and require fresh
  observations while preserving height. Complete a measurement, open overlays,
  background/return and wait: preserve the result with camera/sensors stopped and
  no warnings/retries. Restart should silently resume with height retained.
- Repeat failure/permission controls and preview transitions in English/Spanish,
  compact windows, large text and TalkBack. In compact status dialogs, the error
  must remain readable while acquisition is suspended; Retry returns to the
  measurement surface and starts a fresh cycle.

## Outstanding reliability limits

The reported trial does not complete the manual matrix. Outstanding verification
includes real camera initialization, active-lens metadata, actual
stabilization/autofocus behavior, permissions, lifecycle cleanup, UI/accessibility,
handheld usefulness of provisional gates, repeated reference measurements and
portability beyond the Galaxy S22 (including missing active-camera metadata).
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

## Tutorial navigation manual checks

- Instructions and Important have larger centered titles fixed at the card top.
  Instructions has only circular Next at bottom right; Important has circular
  **<**, **X** in that order, plus a 14 sp documentation link near the lower-left
  corner. Hover, hold and press the link: its text color/brightness must remain
  constant, with no ripple; keyboard/TalkBack activation must still work.
  Compare English/Spanish, narrow windows and large text. Controls remain reachable
  while prose scrolls vertically below the title and above the footer.
- Slowly drag partway in each direction: both pages must follow the finger. Release
  below the positional threshold to return; drag beyond it or fling to select the
  adjacent page. Next and the back chevron should slide over 220 ms; touch flings
  retain their prior behavior. Verify vertical scroll, direction reversals, boundary swipes and
  cancellation. Button navigation must animate the same pager without a second slide.
  Recreate during a drag or after settling: retain the settled section rather than
  an intermediate offset. Moving pages must not offer clickable/keyboard/TalkBack actions.
- Scroll Important before opening documentation, including tapping the link during
  a fling. Verify the reference slides from the right with a fade over approximately
  260 ms, and the tutorial shifts slightly left.
  Scroll documentation independently; its **How does it work?** header must stay
  fixed (localized in Spanish). On Android 12–14 and Android 15+, verify the status
  and gesture navigation areas match the documentation surface instead of black,
  with readable light icons. Repeat with three-button navigation, cutouts, Activity
  recreation and entry/exit reversal; the header and last paragraph must stay clear
  of system controls and returning to the tutorial must restore its dimmed backdrop.
  The top-left arrow and Android Back must reverse to the exact Important page
  and scroll position.
  Repeat with rapid taps, Back during entrance/exit, cancelled predictive gestures,
  narrow/split windows, cutouts, largest fonts, English/Spanish and normal/slow/zero
  animation scale. Back during exit must not also page or dismiss help. TalkBack and
  keyboard focus must not reach covered/moving controls. Recreate both while reading
  and during entrance/exit: retain the logical destination settled and preserve each
  mounted page's scroll. Camera/sensors stay stopped and completion stays unwritten;
  outside taps must not dismiss through documentation. Rendered motion, scroll restoration,
  focus behavior and system Back dispatch require device validation; JVM tests do
  not execute Compose UI. Future equation assets need aspect-ratio, spoken-description
  and theme-readability checks when supplied.
- On both pages, slowly start an inward touch from each Android-reported physical
  back edge. Watch the entire gesture before the system Back indicator appears:
  the pager must remain still, with no initial displacement followed by a snap back.
  Commit and cancel from each edge; repeat with different system Back sensitivity,
  portrait/landscape and RTL layout. Interior-origin drags must still follow the
  finger even if they subsequently cross an edge. Three-button navigation/zero
  gesture insets must not introduce a dead strip; mouse/stylus behavior is unchanged.
- On both Instructions and Important, start Android Back from each physical screen
  edge while idle, partly dragged in either direction, or settling from an interrupted
  button animation. A committed Back must dismiss Instructions or return Important
  to Instructions even if the pager had moved slightly. Before dismissal, the page
  must be fully aligned, with no sideways slide or boundary stretch/rebound; retain
  the intentional downward exit. Cancelling Back must restore the captured source
  page at zero offset, with no navigation/completion and normal paging restored. Repeat
  with gesture and three-button navigation on older and current Android versions.
  In-card swipes must still navigate normally. The entry should fade upward and
  every dismissal (Back, X, outside tap) should fade downward. The backdrop must
  dim and clear with equal 240 ms fades, without a brightness jump on window removal
  or residual dimming. Controls become inactive
  immediately and acquisition stays stopped until exit completes. Recreate during entrance
  and dismissal; repeat with slow/disabled system motion.
- Android Back follows documentation → Important → Instructions → dismissed. **X** and
  outside-card taps dismiss the tutorial itself. Reopening starts on Instructions.
  Repeat from camera, result and keypad, with normal/reduced/disabled system motion.
- Compare the same back-arrow button in documentation, every Settings page and the method
  selector: compact wide visual, smaller arrow, full touch target, localized Back/destination label, keyboard and TalkBack
  operation, existing navigation and reset-lock behavior intact. Device gesture
  arbitration, visual smoothness and accessibility rendering remain manual checks.

Verify the method-selector back control retains its normal icon/container colors
during navigation locks while remaining inactive. The Important documentation link
should sit slightly lower and larger while fitting its left footer slot. Watch shutter readiness
changes in both directions: its disc and border should fade together over 150 ms
between the existing colors, without changing the background, size or capture gate.

Check the method-list header shows the localized Select a method heading directly
to the right of Back, with no repeated mode name. Test English/Spanish, large text
and narrow windows for wrapping without overlap. Compare the shared top spacing
in Settings, Language, Developer Options, documentation and Diagnostics: navigation is slightly
lower below the system inset, with the same back touch target and unchanged Back
hierarchy. Repeat tutorial scrim dismissal at normal, slow and disabled motion scales,
including recreation during entry/exit and return from documentation. These visual checks
require a developer-controlled device; JVM checks do not render window dim layers.

Verify camera → Enter height → method list on consecutive Back actions,
and camera → Edit height → keypad → Back → camera as a separate path. Repeat after
a pending top, overlays and Activity recreation. Check that
Restart retains saved height even with the keypad open, and result-height cancellation
keeps the result. Match the wordmark and Settings positions between Home and methods
in normal/compact widths and text scales; no hidden Help/subtitle accessibility nodes
should appear on Home. Check the tutorial link's small right/up spacing adjustment.

During eligible capture, sustain movement for six seconds: expect the localized
steady-camera popup with existing fades, timeout and accessibility extension. Hold
steady before six seconds or interrupt with zoom settling, a sensor/frame change,
Settings/help, keypad, pause or restart: the next episode must count from zero.
Sustained motion must not repeatedly show the same hint after it expires. Verify
existing capture-error and near-vertical messages keep priority and capture gates
are unchanged. JVM tests cover the time boundary, resets, priority and expiry;
physical motion, rendered popups and header alignment remain developer checks.
