# Viewy product specification

## Purpose and principles

Viewy is an offline Android application for approximate physical measurements
using phone cameras, orientation sensors and other supported hardware. It is
intended to support multiple modes and methods as capabilities grow. Results must
communicate their assumptions and uncertainty honestly; short-term steadiness
must not be presented as calibrated accuracy. Operation must remain local,
without analytics or ads.

Modes describe **what** the user measures; methods describe **how**. The
implemented mode is **Long Distance** and its method is **Known Object Height**.
Short Distance, Height and Calibration remain visible, disabled placeholders for
future validated features; they have no selectable methods.

## Implemented behavior

- Before onboarding is completed, startup presents exactly three screens:
  **Welcome to Viewy**, **Permissions**, and **You're all set!** Welcome introduces
  approximate real-world measurement, with a prominent rounded Viewy wordmark and
  a static disclaimer that accuracy depends on the device and measurement conditions.
  Setup performs no hardware compatibility check or acquisition, and Continue is
  available immediately after its entrance animation. Permissions and Completion
  use larger rounded headings. Setup enters with a short fade and subtle upward
  motion; Activity recreation shows the retained page directly.
  Permissions lists Camera with an explicit Android permission action and its
  granted state, matching the request action's typography. A single navigation
  button reads **Not now** until all permissions are granted, then **Continue**.
  Skipping or denying still allows progress; no request
  launches automatically. Contextual camera recovery remains available in methods.
  Camera permission changes crossfade within stable card bounds, with one fixed
  navigation-button slot so the card stays anchored after a grant.
  Only **Continue to Viewy** persists completion before entering Home. Write failures
  retain onboarding and expose storage Retry. The final button retains its colors
  while saving and fading out to Home, without briefly flashing disabled colors.
  Back returns one screen, exiting
  normally from Welcome. Activity recreation retains the current screen; an
  unfinished process restart begins at Welcome. English/Spanish, system insets,
  scrollable prose, large text and system-scaled short slides are supported.
- After completed onboarding, each fresh application session opens Home with Viewy's wordmark and Settings,
  fading in over 300 ms without vertical movement. In-app returns retain their
  existing navigation motion; Activity recreation does not replay the launch fade.
  Home opens without an active method, camera preview, capture controls, permission prompt or
  acquisition. A centered surface-container card with 28 dp corners and a maximum
  width of 480 dp lists modes and descriptions; its contents scroll in constrained
  windows. Unavailable modes remain disabled and labeled **Coming soon**.
  Temporary interaction locks during navigation or Settings preserve the card's
  normal colors; only unavailable modes use dimmed text.
  Legacy saved method IDs are ignored. Activity recreation retains in-memory
  navigation; process death starts at Home again.
- Long Distance slides the card contents left to reveal Known Object Height and
  its description. The methods header says **Select a method** beside the back
  button, without repeating the mode name. Card Back and Android Back reverse the
  220 ms slide to modes.
  Back on Home follows normal Android exit behavior.
- Selecting Known Object Height fades and subtly moves the entire measurement
  destination in from the right over 270 ms. The outgoing method list stays in
  place while fading out. Before a height is saved, Android Back returns to that mode's
  method list, with measurement sliding right and the list sliding in from the
  left over a 270 ms fade/slide transition shared by all methods. Home returns
  directly to the initial mode list with the existing 280 ms fade and vertical motion.
  Outgoing content is immediately blocked from input and accessibility. Motion
  follows system animation scaling and does not replay on sensor or preference
  updates; recreated destinations appear settled.
- Returning Home immediately releases camera and orientation ownership and clears
  the entire method session: height, keypad draft, captures, results, notices and
  camera/sensor diagnostics. Reentering the method starts at **Enter height**
  without acquisition. Leaving measurement for the method list also ends the session.
  Global settings, permissions and completed tutorials are unaffected. Temporary
  Settings and Help surfaces preserve saved height, draft and completed results;
  unfinished captures still clear on acquisition interruption.
- Each implemented method has a contextual **?** control immediately left of the
  settings gear. It is absent while no method is active. It opens one
  centered, rounded, scrollable tutorial card over the stationary measurement
  interface. Each card has **Instructions** and **Important** pages, with larger,
  centered titles fixed at the top. Instructions has a circular **>** at bottom right;
  Important has matching **<** and **X** controls for returning and closing. Its compact
  **How does it work?** link sits near the bottom-left corner, without hover or press
  brightening, and opens a full-screen **WIP**
  placeholder. The placeholder has a tonal back-arrow button at top left; it and
  Android Back return to Important without dismissing help. No technical explanation
  text is included in this destination yet.
  Horizontal swipes track the finger between Instructions and Important, then settle
  to a page using distance and fling velocity. Short drags can return to the same page;
  vertical scrolling remains independent. Buttons use a short 220 ms page slide;
  touch-driven drags and flings retain normal pager behavior. Android Back takes
  priority over the pager on both pages, from either screen edge. Touches starting
  in Android's back-gesture edge regions cannot move the pager, including before
  the system recognizes Back; swipes starting inside the card still track normally.
  Any interrupted prior drag is aligned before Back proceeds;
  cancellation restores the source page. Boundary swipes do not stretch the pages. The card enters upward with a fade and dismisses downward with
  a fade, blocking controls immediately on dismissal. The backdrop dims and clears
  with matching 240 ms fades, returning fully to the underlying screen before the
  dialog is removed.
  Important retains larger, font-scaled bullet markers. Card bounds stay fixed and
  motion follows system animation scaling. Android Back from Important returns to
  Instructions and dismisses from Instructions; outside taps and **X** close the
  tutorial. Every reopening starts with Instructions. Page or placeholder navigation
  alone never records tutorial completion.
  Help suspends camera and orientation acquisition. Entered height and completed
  results remain available; an incomplete endpoint pair is discarded.
- The first activation of each method automatically opens its tutorial after the
  measurement entrance has completed. Only tutorial dismissal records completion,
  locally by stable method ID across launches and process death. Termination
  before dismissal leaves first-use help eligible on the next activation.
  Manual help remains available after completion. Tutorial completion is
  independent of fresh-session Home startup and method selection.
- Known Object Height Instructions gives a brief usage procedure and describes the
  approximate distance result. Before the numbered instructions, a looping
  illustration shows known height, top and bottom observations from a fixed
  camera position, and horizontal distance. Disabled system motion shows a static
  distance diagram; the illustration has one localized accessible description.
  The three steps are: enter the object height, capture
  the top, then tilt the phone without moving its position and capture the bottom.
  Important gives four practical tips about fixed position, vertical aiming, zoom
  and using a known height. Steadiness is not presented as measurement accuracy.
- The circular bottom-right **Home** control uses a house icon and returns to
  the initial Home card and clears the method completely. Restart retains height
  while clearing captures/results. Android Back while the distance result is displayed
  performs the same Restart action, retaining height and prompting a fresh top capture.
  From the unfinished camera workspace, Android Back clears height/captures and
  returns directly to **Enter height** with acquisition stopped; the next
  Back exits the method. The last available camera image and controls fade into
  the height prompt over 225 ms; acquisition stops immediately. Without an
  available image, the prompt fades over the themed waiting surface. The camera's Edit height button opens a draft keypad;
  Back from that editor discards draft changes and returns to the camera with the
  saved height retained. Initial-entry cancellation returns to Enter height;
  completed-result editor cancellation preserves the result. Covering
  surfaces keep their existing Back hierarchy.
  Capture retains its existing behavior.
- A circular Settings control with a standard rounded gear sits at top right,
  using a slightly richer purple than the other tonal controls.
  It opens full-screen dark Material 3 settings with or without an active method.
  Settings and the method selector use the same compact, wide tonal back-arrow
  button as the WIP screen, with a 48 dp touch target. Temporary method-selector
  navigation locks preserve its normal colors.
  Full-screen Settings, Language, Developer Options, WIP and Diagnostics share
  header spacing below the system insets, with a modest top gap above navigation.
  Settings provides global Metric/Imperial units, language selection and a separate
  Developer Options destination. Changes save automatically. Back from a child
  destination returns to Settings; Back from Settings restores the underlying
  Home or measurement destination. Covered navigation and help cannot be operated through Settings.
  Settings and diagnostics enter and leave with a short fade and subtle vertical
  motion over the stationary measurement surface. Settings child destinations,
  including Language and Developer Options, use horizontal fade/slide transitions
  with the opposite direction on Back. Outgoing controls and the covered
  measurement remain blocked until transitions finish. Motion follows the system
  animation scale; restored destinations appear directly without replaying entry.
  Opening Settings preserves the selected method, height and completed result,
  suspends acquisition and discards an incomplete endpoint pair. Acquisition
  resumes only after the covering surface finishes closing.
- Units initialize once from the device's regional measurement preferences,
  independently of app language, using Android ICU. Mixed-system regions such as
  the UK and unknown regions default to Metric. Existing saved preferences and later Settings choices always take
  priority. Metric uses meters; Imperial uses decimal feet for heights and
  short distances; long results automatically use kilometers or miles as described
  below. Tapping the units row switches immediately, including existing
  results. Height entry defaults to meters or decimal feet and offers a local
  m/cm or ft/in toggle without changing the global preference.
  Stored physical values remain in meters; changing units neither edits height nor
  recalculates geometry or resets captures. Presentation rounding never changes stored values.
- Language automatically follows the first supported device language in preference
  order, falling back to English when none is supported. Settings lists only
  **English** and **Español**, with the effective language selected. An explicit
  choice, even of the currently active language, persists across restarts and
  overrides subsequent device language changes. Without an override, device
  language changes continue to apply, including for existing installations. Android per-app
  language settings and the in-app picker share the same override on Android 13+;
  AppCompat supports Android 12 and 12L. Language changes update text and number
  formatting in place, without restarting the Activity or blanking the screen.
  The language selector, method, height and completed results stay in
  place; acquisition remains suspended throughout Settings. Back
  returns to main Settings. Language and units are independent.
- Developer Options persists **Enable diagnostics**, off by default. A normal **?**
  tap always opens the method tutorial; an enabled long press opens a full-screen,
  scrollable, read-only Diagnostics surface with haptic feedback and a separate
  accessible action. Disabled holds do nothing. Diagnostics shows the active
  method, measurement/capture readiness, camera identity and mapping, sensor source
  and quality, and Known Object Height endpoint geometry, sensitivities and
  unquantified uncertainty. Missing prerequisites are inactive, not hardware
  failures; missing/invalid values remain unavailable. Angles use degrees and
  lengths/sensitivities follow global units. Recent stability is not calibrated
  accuracy, and derivatives are not measured errors. Back restores the stationary
  measurement interface; diagnostics does not request permissions, initialize or
  restart hardware, record observations, or edit/recompute measurements. Opening it
  suspends acquisition and discards an incomplete endpoint pair while preserving
  entered height and completed results. Settings, selection,
  tutorials and diagnostics cannot be operated together.
- **Erase all app data** requires explicit confirmation explaining irreversibility,
  preferences/tutorial removal and app closure. Cancel changes nothing. Android
  performs the full storage clear and permission revocation without uninstalling.
  A rejected/failed request shows an error; an accepted request is not reported as
  completed and does not relaunch the app. Manual reopening starts onboarding with
  regionally initialized units, diagnostics off, device language and first-use help eligible.
- Restart is a circular
  arrow control matching Home on the left of capture; the control row is raised
  above the navigation inset for easier thumb access. Capture availability changes
  fade its existing shutter/border colors over 150 ms; capture is disabled immediately. Restart and Home sit
  symmetrically closer to the large, solid shutter-style Capture button.
- After method selection, until a positive finite height is saved, a tonal rounded
  workspace centers a decorative vertical-dimension icon, an object-height heading,
  a short explanation and a primary **Enter height** action.
  The group scrolls when space or text size requires it, without starting camera or
  orientation acquisition or requesting camera permission. Entry uses the global
  unit system (meters or decimal feet), accepts decimal
  point or comma, without grouping separators. A rounded, borderless preview-edge
  control edits height during acquisition or recovery. Its label uses grouped plain
  decimals, omits zero padding, keeps one decimal for whole values and retains the
  six-significant-digit display cap; stored height remains
  unchanged. Zoom and height share a row with a 16 dp gap. The height action is
  limited to the remaining width; where its full label cannot fit, it shows the
  height cue, a single-line value with ellipsis when needed, and the unit. Its
  accessible edit description retains the complete display value. All three height actions open the same embedded keypad in the rounded
  camera/result container, retaining its ordinary size and placement. The initial
  **Enter height** action never opens the editor automatically.
  A new height starts empty; existing heights open in meters or decimal feet
  according to Settings, with converted text truncated to four fractional digits.
  Unchanged confirmation still retains the exact stored physical value. Local m/cm and ft/in toggles convert the
  physical height without cumulative rounding or changing global units. Imperial
  entry uses decimal feet or inches, never combined feet-and-inches. A neutral
  surface-colored rounded rectangle slides between the two unit labels. Unit
  buttons have rounded rectangular visuals and larger hit targets, without touch
  ripple or pressed-state feedback. Numeric entry accepts up
  to ten digits, excluding the decimal separator; another digit leaves the value
  unchanged and briefly shakes the number, with localized accessibility feedback.
  Conversion-generated values may exceed ten digits without losing physical precision.
  Digits, the localized decimal separator and backspace edit a temporary draft;
  **Enter** confirms only supported positive finite heights. The back arrow and
  Android Back discard the draft and return to its underlying camera, result or
  initial height prompt, preserving any previously saved height/result. No text field,
  separate height dialog or software keyboard is used. Hardware digits, point/comma,
  Backspace, Enter and Escape are supported. Smaller rounded rectangular number
  keys keep the same colors when disabled. Invalid input, including empty or zero,
  disables **Enter** immediately and dims it through a short 150 ms fade; valid
  input fades it back to full emphasis. The complete keypad adapts proportionally
  to the rounded workspace and remains static, without scrolling or overscroll
  stretching. Numbers fit a clipped slot with reserved shake travel, separate from
  the stationary unit label, so rejection feedback cannot pass beneath that label.
  Exceptionally small converted values show <0.0001 rather than a misleading zero.
  Camera controls immediately lose input and accessibility on editor opening.
  Camera and editor share a restrained 225 ms opacity transition over an opaque
  themed surface. The prompt and keypad share one cover transition, so initial
  confirmation fades directly from keypad to the waiting camera without showing
  Enter height again. Opening the camera's editor retains the last image
  beneath the incoming keypad instead of replacing it with a solid color first.
  On return from the keypad, Settings or Help, the preview draws beneath its native
  themed cover until streaming, initial zoom settling and a subsequent draw
  confirmation, then the cover fades away over 225 ms to reveal live imagery.
  The native preview host stays in place through suspension; the retained image
  remains inside it until its fade or the incoming opaque surface finishes.
  Results remain underneath the editor, so cancellation or
  correction returns directly without replaying the result entrance. Motion follows
  system animation scaling. The draft survives Activity recreation within the session.
  Settings, Help, Home and enabled diagnostics remain available during editing.
  Settings, Help and diagnostics preserve the unconfirmed draft and its selected
  entry unit, saved height and completed result while acquisition stays stopped.
  Changing global units converts a retained draft into that system's base unit
  without committing it. Home discards the draft and clears the entire method.
  Restart also discards the draft and starts a new measurement with saved height.
  The reserved shutter-caption area fades its instruction out during editing;
  normal top/bottom instruction changes also crossfade.
- Camera and orientation acquisition run only with valid height, permission and
  an unfinished measurement in the focused, resumed viewfinder. Settings, diagnostics,
  tutorials (including pending first-use help), mode/method selection, height editing
  and status dialogs suspend acquisition. A collapsed exceptional workspace does
  not acquire. Intentional visible suspension briefly fades the last preview image
  after releasing hardware; streaming fades back in on recovery. Activity inactivity
  releases resources immediately. Completed results keep acquisition stopped until
  Restart. Correcting a completed height keeps acquisition stopped.
- Rear-camera preview in a rounded rectangle with a thin, centered plus-shaped
  aiming reticle with rounded white strokes and a black outline. Workflow failures
  and near-vertical restrictions receive preview-edge messages; normal aiming
  stays silent. Startup, first-frame waiting, intentional suspension and return
  from overlays stay silent, with no camera warning or Retry control. Recoverable
  failures receive up to three automatic retries with short increasing delays;
  only exhausted retries or conditions requiring user action show camera recovery
  controls. Permission recovery remains available. Unexpected sustained preview
  loss is recoverable; brief stream transitions do not trigger rebinding.
- Known Object Height offers minimalist **+** and **−** controls inside the
  rounded preview and pinch-to-zoom. Both use the same CameraX zoom ratio across
  a range starting at 1× (or the camera's higher supported minimum) and ending
  at the CameraX-reported maximum, including above 20× when supported. Neither
  buttons nor pinch can select a ratio below 1×. Buttons
  have 48 dp touch targets, localized accessibility descriptions and current-ratio
  semantics; the current ratio label remains visible with the controls. Button
  and ratio backgrounds are nearly opaque for readability. Zoom is
  available before top capture, locked while that capture remains valid and hidden
  after completion. Restart or capture invalidation unlocks it. Recreated camera
  sessions explicitly request and display 1.0×, independently of the supported
  minimum. A higher supported minimum is requested and displayed instead where
  necessary; a reported range entirely below 1× cannot start acquisition.
  Capture is blocked during asynchronous zoom application and a short settling
  interval. Zoom does not establish camera-center stability or calibrated accuracy.
  The logical rear camera is unpinned so Android can select its lens. On
  multi-camera devices, acquisition requires a readable reported active rear lens.
  A lens change updates the aiming-axis mapping, restarts orientation and zoom
  settling, and invalidates an incomplete pair. Expected orientation restarts
  stay quiet; actual sensor discovery/registration failures retain recovery.
  Zoom controls, the ratio label and the preview-edge height editor fade with
  preview suspension and streaming recovery, including Settings, mode selection
  and focus loss. Camera and controls share 225 ms fade-ins and fade-outs;
  outgoing controls stop accepting input immediately. Height editing remains
  available through the result’s object-height row after completion and through
  the preview-edge editor on actionable recovery screens.
- Top capture followed by bottom capture within 15 s. **Aim at the top** and
  **Aim at the bottom** remain the stable primary instructions. Routine movement,
  settling, startup, readiness, missing/stale/unreliable sensor readings, small
  left/right adjustments produce no preview text, even when sustained.
  Near-vertical aiming shows **Cannot align at this angle.** for as long as the aim
  remains in the restricted range. Preview messages fade in and out over 150 ms;
  their visibility targets update immediately without changing correction timeouts.
  Sensor quality, freshness, near-vertical and alignment gates
  still block invalid captures immediately. Hardware failures retain recovery
  actions. The camera location must stay fixed and the endpoints must lie on one
  vertical line; the horizontal alignment gate permits up to 0.45°
  deviation from the captured top's bearing.
- After top capture, small rounded white chevrons with black outlines flank the central
  reticle when horizontal correction is needed. The indicated physical left/right
  correction gently nudges outward. Recovering alignment briefly shows both
  chevrons, fading together; initial alignment and repeated aligned readings do
  not replay confirmation. A short presentation-only transition filter avoids
  flashing around the threshold without delaying capture validation. Guidance
  requires fresh, reliable observations in the captured top's camera/sensor
  session; it can guide during movement or settling without allowing capture.
  Near-vertical aim, invalid/stale readings, suspension, restart and completion
  immediately hide and reset it. System animation scaling applies; disabled motion
  leaves static directional cues. Chevrons add no text or accessibility focus
  targets; Capture semantics retain the correction direction without a live
  announcement on each reading.
- Six uninterrupted seconds of movement-blocked capture show **Keep the camera
  steady.** (**Mantén la cámara fija.** in Spanish), once per movement episode.
  The hint uses the existing popup, 150 ms fades and five-second readable interval
  with accessibility timeout extension. Becoming ready, settling, missing/stale or
  unreliable readings, zoom settling, frame changes, overlays, restart and stage
  changes reset the interval. Existing corrections and near-vertical restrictions
  have priority; the hint never changes capture eligibility.
- New rejected captures and lost pending captures receive concise, event-driven
  corrections. Each failure is shown once for a readable interval, extended by
  Android accessibility timeout preferences where supported; sensor updates do
  not repeat it. Restart, successful stage changes, suspension and completion
  clear obsolete messages. A new interruption with a recorded top explains the
  cleared capture on return; genuine camera loss uses the camera interruption
  notice. Suspension before any capture is silent, and completed results show no
  guidance. One static preview-edge surface leaves the reticle and controls fixed;
  constrained layouts retain the existing status dialog. Capture semantics explain
  a blocked horizontal alignment without left/right preview messages.
- Before height entry, the aiming-caption area stays blank but reserves space
  for both top and bottom instructions at the current text size and width. This
  prevents the preview from shrinking when instructions appear or change.
  Before completion the area shows only the top/bottom instruction; after
  completion it stays blank with the same reserved height. Reversed, insufficiently
  separated, misaligned or unusable observations require correction.
- Successful completion immediately releases camera/orientation acquisition and
  replaces camera controls and guidance with a clean results surface inside the
  same rounded container, preserving its size and position. The new color fades
  directly over the last available preview image instead of an intermediate black
  frame; the retained image is discarded when covered. A large
  **Horizontal distance** value with an ≈ marker counts up from zero over about
  750 ms with smooth deceleration after the surface finishes its 450 ms fade-in,
  followed by three sequential 180 ms fades after counting finishes:
  editable object height, captured elevation difference in degrees, then a short
  approximation disclaimer near the bottom. This is horizontal distance to the
  object’s vertical line, not slant range; individual endpoint angles remain in
  Diagnostics. The height row is an accessible edit action. Confirming a changed
  height recalculates distance and diagnostics from the original observations,
  retaining their metadata and completed state without recapture or a new count-up.
  Unchanged confirmation and cancellation preserve the result; unusable corrections
  keep the previous measurement and allow the draft to be corrected or cancelled.
- Result distances use meters below 10,000 m and kilometers at or above that
  threshold. Imperial results use decimal feet below one mile (5,280 ft) and miles
  at or above it. The unit is chosen from the unrounded final distance and remains
  fixed throughout counting; object heights retain meters or feet. Results use
  locale-specific grouping and decimal separators with HALF_UP rounding. The primary
  distance uses two fractional digits for ordinary values, including trailing zeros
  (9.8 → 9.80, 9 → 9.00), throughout counting and at completion. This fills its
  reserved numeric width without an extra gap before short results. Positive values
  below 0.01 use two significant digits in plain decimal form so they do not become
  zero. Large magnitudes remain grouped plain decimals without scientific notation.
  Supporting height and elevation remain compact: at most two fractional digits for
  ordinary values, no decimals for integers, and at least one fractional digit for
  non-integers even when rounding reaches a whole value. These display digits are
  not calibrated accuracy; the approximation marker and disclaimer remain.
- Result animation is presentation-only: ordinary updates, unit/language changes
  and overlay navigation do not replay it. A recreated host shows a retained
  completed result directly. System animation scaling applies; disabled motion
  shows final distance and supporting information together. Screen readers receive
  the final approximate horizontal distance, never intermediate counter values.
  The distance number grows up to 96 sp where it fits; the approximation sign,
  value and smaller unit are measured together to stay within the result width.
  The panel can scroll in constrained windows or at large text sizes; exceptional
  magnitudes can scroll horizontally without reducing readability. Restart remains
  the existing bottom-left action: it resets immediately with height retained,
  makes acquisition eligible immediately, and fades the whole outgoing result
  over 225 ms. The single live preview fades in only once actually ready, so a
  slow camera cannot expose an old frame as live.
- Explicit unquantified uncertainty for nominal captures. There is no validated
  angular-error input or automatic uncertainty range.
- Restart clears captures/results while retaining height. Height changes during an
  unfinished measurement reset captures; completed corrections retain evidence.
  The result has no separate retry control.
  An incomplete pair is cleared by acquisition interruptions or incompatible
  camera/sensor sessions. Completed results retain their captured geometry after
  shutdown, overlays, backgrounding, Activity recreation and height correction
  until Restart or method exit; measurements are not persisted across process death.
- Camera permission recovery has one **Allow camera** action. Its explanation and
  button stay visible behind Android's permission dialog, without changing labels
  or adding actions. Repeated taps are blocked while a request is pending. A denial
  leaves recovery available; if another system prompt is unavailable, the same action
  opens Android app settings on the next tap. Camera or orientation unavailability
  remains visible with captures blocked.
  Granting access fades out the inline permission panel instead of removing it
  abruptly. The fading panel cannot receive input or accessibility focus. The
  preview-edge height editor is absent until camera permission is granted; it
  appears with the preview/zoom controls or granted-camera recovery.
- Material 3 baseline dark colors across the app, launch window and system bars,
  independent of the system theme. Neutral surfaces use restrained lavender accents.
- Home and methods share exact wordmark/Settings positions: common compact sizing
  and outer padding, with subtitle and help space reserved on Home without showing
  or exposing those method-only elements to accessibility.
- A larger, bold, rounded Viewy wordmark has a more spacious header above the
  flexible preview. The rounded font is bundled for offline use.
- Portrait Activity request, compact header, flexible preview and fixed bottom
  controls within system insets. The normal measurement screen does not scroll.
  Constrained layouts offer a status dialog; exceptionally small windows or text
  scaling open a scrollable viewfinder workspace with fixed capture/navigation.
  Android may override portrait on large screens. Diagnostics uses the same
  stationary full-screen overlay approach in normal and exceptional workspaces.

No photos, recording, measurement history, network access,
analytics or ads are implemented. Hardware correctness and usability still need
validation; see [Validation](VALIDATION.md).

## Approved product direction, not yet implemented

- Built-in and custom object-height presets.
- Estimated distances with uncertainty ranges when justified input bounds exist.

Preserve the minimalist interface, offline operation and honest accuracy claims
as these capabilities are added.

## Open scope and exploratory ideas

The repository does not define additional measurement algorithms, other hardware
integrations, a calibration workflow, or a policy for deriving reliable uncertainty
inputs. Those require product decisions and research before implementation.
Measurement history is mentioned in existing plans but has no defined requirement
or persistence design. Do not assume it is approved scope.

Broader product vision and exploratory concepts are maintained separately from
this repository. They are not replaced by this specification and must not be
promoted to requirements without an explicit decision.

[Architecture](ARCHITECTURE.md) defines the implemented mathematical and sensor
contracts; [README](../README.md) covers setup and use.
