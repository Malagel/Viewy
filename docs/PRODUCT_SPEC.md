# Viewy product specification

## Purpose and principles

Viewy is an offline Android application for approximate physical measurements
using phone cameras, orientation sensors and other supported hardware. It is
intended to support multiple modes and methods as capabilities grow. Results must
communicate their assumptions and uncertainty honestly; short-term steadiness
must not be presented as calibrated accuracy. Operation must remain local,
without analytics or ads.

Modes describe **what** the user measures; methods describe **how**. The current
mode is **Long Distance** and its method is **Known Object Height — Two Endpoint
Captures**. Short Distance and Height appear as unavailable mode placeholders;
two empty method cards are reserved for future validated methods. They cannot start a
measurement.

## Implemented behavior

- A single circular Mode control opens vertically aligned floating pills with
  staggered spring motion and consistent spacing. Long Distance opens a centered
  method-card carousel with horizontal swiping and a page indicator. The
  measurement background blurs on Android 12+ while cards remain sharp; older
  Android versions retain dimming. Tap a method card to activate it; outside taps
  or system Back dismiss selection. Each implemented card has a separate
  **Know more** action showing a documentation placeholder.
  Browsing, dismissing or reselecting the active method preserves its workflow,
  subject to existing session interruption and capture-expiry rules.
- Startup restores the last selected method from its locally stored stable ID,
  defaulting to Known Object Height on fresh installs or unavailable saved IDs.
  Captures, results and sensor sessions are never restored.
- A circular configurations placeholder with a standard rounded gear sits at
  top right and remains disabled. Restart is a circular
  arrow control matching Mode on the left of capture; the control row is raised
  above the navigation inset for easier thumb access. Restart and Mode sit
  symmetrically closer to the large, solid shutter-style Capture button.
  No settings screen is implemented.
- Until a positive finite height is saved, a tonal rounded rectangle offers
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
  Android may override portrait on large screens. Debug-only diagnostics open
  in a separate dialog and occupy no measurement-content space.

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
