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

- Each fresh application session starts with no active method and automatically
  opens a Material 3 bottom sheet listing modes with short descriptions. Dismissing
  it leaves only the dark Viewy background, header and Mode launcher; there is no
  height entry, viewfinder placeholder, permission request or hardware acquisition.
  Legacy saved method IDs are ignored. Activity recreation and returning from the
  background retain the in-memory selection; process death starts inactive again.
- Tapping Long Distance slides the mode list left and its method list in from the
  right. Back returns to modes with the opposite transition; Back from modes or
  an outside tap dismisses the sheet. Every reopening begins at modes. Entries
  work by tapping and scroll when needed. There is no pager.
- Selecting Known Object Height closes the sheet and shows its existing workflow.
  Browsing suspends acquisition and invalidates any incomplete endpoint pair.
  Dismissing or reselecting preserves the entered height and completed result. The measurement
  surface stays stationary, blurs gradually on Android 12+ and dims while the
  sheet opens; older versions use dimming. The sheet stays sharp.
- Each implemented method has a contextual **?** control immediately left of the
  settings gear. It is absent while no method is active. It opens one
  centered, rounded, scrollable tutorial card over the stationary measurement
  interface. **Know more** replaces the practical content with a technical
  explanation with formatted equations in the same card. **Go back** appears at
  the top left only in the detailed view, opposite **Close**, and restores the
  practical content.
  Switching content starts at the top. Outside tap, Android Back and **Close**
  dismiss the entire card from either page; every reopening starts with basic
  content. Help suspends camera and orientation acquisition. Entered height and completed
  results remain available; an incomplete endpoint pair is discarded.
- The first activation of each method automatically opens its tutorial after the
  selector has finished closing. Only tutorial dismissal records completion,
  locally by stable method ID across launches and process death. Termination
  before dismissal leaves first-use help eligible on the next activation.
  Manual help remains available after completion. Tutorial completion is
  independent of fresh-session inactive startup and method selection.
- Known Object Height basic help gives a brief usage procedure and describes the
  approximate horizontal-distance result. It emphasizes fixed phone position,
  a vertical target, horizontal alignment and the 15-second capture limit.
  The detailed view explains the geometry with formatted fractions and endpoint
  subscripts, then covers sensor/camera/timing limitations and unquantified
  accuracy. Steadiness is not presented as measurement accuracy.
- The circular bottom-right Mode launcher uses an upward chevron and stays in
  place while the sheet opens and closes. The modal sheet blocks interaction
  with the background launcher while open.
- A circular Settings control with a standard rounded gear sits at top right,
  using a slightly richer purple than the other tonal controls.
  It opens full-screen dark Material 3 settings with or without an active method.
  Settings provides global Metric/Imperial units, language selection and a separate
  Developer Options destination. Changes save automatically. Back from a child
  destination returns to Settings; Back from Settings restores the measurement
  surface. Modal settings, help and mode selection cannot be operated together.
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
  distances. Tapping the units row switches immediately, including existing
  results. Height entry follows this preference without an independent selector.
  Stored physical values remain in meters; changing units neither edits height nor
  recalculates geometry or resets captures. Display precision is unchanged.
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
  completed and does not relaunch the app. Manual reopening starts inactive with
  mode selection, Metric, diagnostics off, device language and first-use help eligible.
- Restart is a circular
  arrow control matching Mode on the left of capture; the control row is raised
  above the navigation inset for easier thumb access. Restart and Mode sit
  symmetrically closer to the large, solid shutter-style Capture button.
- After method selection, until a positive finite height is saved, a tonal rounded
  rectangle offers
  a prominent **Enter object height** action without starting camera or
  orientation acquisition or requesting camera permission. Entry uses the global
  unit system (meters or decimal feet), accepts decimal
  point or comma, without grouping separators. A rounded, borderless preview-edge
  control edits height. Its label omits zero padding, keeps one decimal for whole
  values and retains the six-significant-digit display cap; stored height remains
  unchanged. Both height actions focus the decimal field and request
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
  aiming reticle. Contextual guidance fades inside the preview; normal readiness
  adds no warning. Hardware/permission failures retain recovery controls.
- Top capture followed by bottom capture within 15 s, with guidance for fresh,
  stable aiming and horizontal alignment. The camera location must stay fixed
  and the endpoints must lie on one vertical line.
- Before height entry, the aiming-caption area stays blank but reserves space
  for both top and bottom instructions at the current text size and width. This
  prevents the preview from shrinking when instructions appear or change.
  Once configured, a single centered stage label becomes the approximate
  horizontal distance to that line, displayed with two significant digits and
  an ≈ marker.
  This is not slant range. Reversed, insufficiently separated,
  misaligned or unusable observations require correction.
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
