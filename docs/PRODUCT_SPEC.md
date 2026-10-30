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

- Each fresh application session opens Home with Viewy's wordmark and Settings,
  without an active method, camera preview, capture controls, permission prompt or
  acquisition. A centered surface-container card with 28 dp corners and a maximum
  width of 480 dp lists modes and descriptions; its contents scroll in constrained
  windows. Unavailable modes remain disabled and labeled **Coming soon**.
  Temporary interaction locks during navigation or Settings preserve the card's
  normal colors; only unavailable modes use dimmed text.
  Legacy saved method IDs are ignored. Activity recreation retains in-memory
  navigation; process death starts at Home again.
- Long Distance slides the card contents left to reveal Known Object Height and
  its description. Card Back and Android Back reverse the 220 ms slide to modes.
  Back on Home follows normal Android exit behavior.
- Selecting Known Object Height fades and subtly moves the entire measurement
  destination into view over 240 ms. Home and Android Back from measurement
  return directly to the initial mode list with complementary 280 ms motion.
  Outgoing content is immediately blocked from input and accessibility. Motion
  follows system animation scaling and does not replay on sensor or preference
  updates; recreated destinations appear settled.
- Returning Home immediately releases camera and orientation ownership and
  discards unfinished endpoint pairs, independently of animation completion.
  Configured height and completed results survive within the session; entering
  the same method displays its retained result without restarting acquisition.
  A different method cannot inherit captures or results.
- Each implemented method has a contextual **?** control immediately left of the
  settings gear. It is absent while no method is active. It opens one
  centered, rounded, scrollable tutorial card over the stationary measurement
  interface. **For nerds** replaces the practical content with a technical
  explanation with formatted equations in the same card. **Go back** appears at
  the top left only in the detailed view, opposite **Close**, and restores the
  practical content.
  Switching content starts at the top. Outside tap, Android Back and **Close**
  dismiss the entire card from either page; every reopening starts with basic
  content. Help suspends camera and orientation acquisition. Entered height and completed
  results remain available; an incomplete endpoint pair is discarded.
- The first activation of each method automatically opens its tutorial after the
  measurement entrance has completed. Only tutorial dismissal records completion,
  locally by stable method ID across launches and process death. Termination
  before dismissal leaves first-use help eligible on the next activation.
  Manual help remains available after completion. Tutorial completion is
  independent of fresh-session Home startup and method selection.
- Known Object Height basic help gives a brief usage procedure and describes the
  approximate distance result. Before the numbered instructions, a looping
  illustration shows known height, top and bottom observations from a fixed
  camera position, and horizontal distance. Disabled system motion shows a static
  distance diagram; the illustration has one localized accessible description.
  The three steps are: enter the object height, capture
  the top, then tilt the phone without moving its position and capture the bottom.
  The detailed view explains the geometry with formatted fractions and endpoint
  subscripts, then covers sensor/camera/timing limitations and unquantified
  accuracy. Steadiness is not presented as measurement accuracy.
- The circular bottom-right **Home** control uses a house icon and returns to
  the initial Home card. Restart and Capture retain their existing behavior.
- A circular Settings control with a standard rounded gear sits at top right,
  using a slightly richer purple than the other tonal controls.
  It opens full-screen dark Material 3 settings with or without an active method.
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
- Units default to Metric (meters); Imperial uses decimal feet for heights and
  short distances; long results automatically use kilometers or miles as described
  below. Tapping the units row switches immediately, including existing
  results. Height entry follows this preference without an independent selector.
  Stored physical values remain in meters; changing units neither edits height nor
  recalculates geometry or resets captures. Presentation rounding never changes stored values.
- Language defaults to **Use device language**, with **English** and **Español**
  overrides. Unsupported device languages fall back to English. Android per-app
  language settings and the in-app picker share the same override on Android 13+;
  AppCompat supports older versions. Language changes update text and number
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
  completed and does not relaunch the app. Manual reopening starts at Home with
  Metric, diagnostics off, device language and first-use help eligible.
- Restart is a circular
  arrow control matching Home on the left of capture; the control row is raised
  above the navigation inset for easier thumb access. Restart and Home sit
  symmetrically closer to the large, solid shutter-style Capture button.
- After method selection, until a positive finite height is saved, a tonal rounded
  rectangle offers
  a prominent **Enter object height** action without starting camera or
  orientation acquisition or requesting camera permission. Entry uses the global
  unit system (meters or decimal feet), accepts decimal
  point or comma, without grouping separators. A rounded, borderless preview-edge
  control edits height during acquisition or recovery. Its label uses grouped plain
  decimals, omits zero padding, keeps one decimal for whole values and retains the
  six-significant-digit display cap; stored height remains
  unchanged. Zoom and height share a row with a 16 dp gap. The height action is
  limited to the remaining width; where its full label cannot fit, it shows the
  height cue, a single-line value with ellipsis when needed, and the unit. Its
  accessible edit description retains the complete display value. Both height actions focus the decimal field and request
  its keyboard automatically. The height-entry card fades in 20 dp above the
  keyboard’s initially observed maximum height and keeps that position for the
  entire opening, including when the keyboard is hidden or reopened. It waits
  briefly for stable keyboard placement; if unavailable, it fades in after about
  one second near the bottom inset and keeps that fallback position. Save, Cancel,
  outside tap and Back fade the card out before closing it. Its contents handle
  keyboard space without moving the measurement screen.
- Camera and orientation acquisition run only with valid height, permission and
  an unfinished measurement in the focused, resumed viewfinder. Settings, diagnostics,
  tutorials (including pending first-use help), mode/method selection, height editing
  and status dialogs suspend acquisition. A collapsed exceptional workspace does
  not acquire. Intentional visible suspension briefly fades the last preview image
  after releasing hardware; streaming fades back in on recovery. Activity inactivity
  releases resources immediately. Completed results keep acquisition stopped until
  Restart or saving height.
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
  Diagnostics. The height row is an accessible edit action; saving restarts.
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
  The panel can scroll in constrained windows or at large text sizes; exceptional
  magnitudes can scroll horizontally without reducing readability. Restart remains
  the existing bottom-left action: it resets immediately with height retained,
  makes acquisition eligible immediately, and fades the whole outgoing result
  over 225 ms. The single live preview fades in only once actually ready, so a
  slow camera cannot expose an old frame as live.
- Explicit unquantified uncertainty for nominal captures. There is no validated
  angular-error input or automatic uncertainty range.
- Restart and reset when saving height. The result has no separate retry control.
  An incomplete pair is cleared by acquisition interruptions or incompatible
  camera/sensor sessions. Completed results retain their captured geometry after
  shutdown, overlays, backgrounding and Activity recreation until Restart or
  saving height; measurements are not persisted across process death.
- Camera permission explanation, retry/settings access, and visible camera or
  orientation unavailability with captures blocked.
- Material 3 baseline dark colors across the app, launch window and system bars,
  independent of the system theme. Neutral surfaces use restrained lavender accents.
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
