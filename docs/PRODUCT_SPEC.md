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
  Browsing, dismissing or reselecting it preserves inputs, captures and results,
  subject to existing interruption and capture-expiry rules. The measurement
  surface stays stationary, blurs gradually on Android 12+ and dims while the
  sheet opens; older versions use dimming. The sheet stays sharp.
- Each implemented method has a contextual **?** control immediately left of the
  unchanged settings gear. It is absent while no method is active. It opens one
  centered, rounded, scrollable tutorial card over the stationary measurement
  interface. **Know more** replaces the practical content with a technical
  explanation with formatted equations in the same card. **Go back** appears at
  the top left only in the detailed view, opposite **Close**, and restores the
  practical content.
  Switching content starts at the top. Outside tap, Android Back and **Close**
  dismiss the entire card from either page; every reopening starts with basic
  content. Opening or dismissing help does not reset inputs, captures, results or
  acquisition; ordinary interruption and capture-expiry rules still apply.
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
- A circular configurations placeholder with a standard rounded gear sits at
  top right and remains disabled. Restart is a circular
  arrow control matching Mode on the left of capture; the control row is raised
  above the navigation inset for easier thumb access. Restart and Mode sit
  symmetrically closer to the large, solid shutter-style Capture button.
  No settings screen is implemented.
- After method selection, until a positive finite height is saved, a tonal rounded
  rectangle offers
  a prominent **Enter object height** action without starting camera or
  orientation acquisition or requesting camera permission. Entry accepts meters or decimal feet, decimal
  point or comma, without grouping separators. A rounded, borderless preview-edge
  control edits height. Its label omits zero padding, keeps one decimal for whole
  values and retains the six-significant-digit display cap; stored height remains
  unchanged. Both height actions focus the decimal field and request
  its keyboard automatically. Height entry uses a top-anchored dialog; its
  contents handle keyboard space without moving the measurement screen.
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
  Interruptions or incompatible camera/sensor sessions clear captures
  and results; measurements are not persisted across process death.
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
  Android may override portrait on large screens. Diagnostic acquisition and
  presentation remain in source for future developer access; there is currently
  no diagnostics entry in the interface. Developer Options and long-press help
  actions are not implemented.

No photos, recording, measurement history, configurable settings, network access,
analytics or ads are implemented. Hardware correctness and usability still need
validation; see [Validation](VALIDATION.md).

## Approved product direction, not yet implemented

- Built-in and custom object-height presets.
- Estimated distances with uncertainty ranges when justified input bounds exist.
- Broader metric/imperial support beyond the current meters/decimal-feet entry
  and result display.
- Locally stored settings and a settings control at bottom left.

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
