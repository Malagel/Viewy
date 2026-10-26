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
Captures**. Additional modes and methods are planned, but their scope and
implementations are not defined in repository requirements.

## Implemented behavior

- Rear-camera preview in a rounded rectangle with a centered aiming reticle.
- Positive finite object-height entry in meters or decimal feet. Decimal point
  and comma are accepted; grouping separators are not supported.
- Top capture followed by bottom capture within 15 s, with guidance for fresh,
  stable aiming and horizontal alignment. The camera location must stay fixed
  and the endpoints must lie on one vertical line.
- Approximate horizontal distance to that line, displayed with two significant
  digits. This is not slant range. Reversed, insufficiently separated,
  misaligned or unusable observations require correction.
- Explicit unquantified uncertainty for nominal captures. There is no validated
  angular-error input or automatic uncertainty range.
- Restart, bottom recapture while the top remains valid, and reset when saving
  height. Interruptions or incompatible camera/sensor sessions clear captures
  and results; measurements are not persisted across process death.
- Camera permission explanation, retry/settings access, and visible camera or
  orientation unavailability with captures blocked.
- Minimal Material 3 light/dark interface, circular capture button, bounded width
  and scrolling for constrained layouts. The Mode control explains the single
  available method. Optional diagnostics are restricted to debug builds.

No photos, recording, measurement history, persistent settings, network access,
analytics or ads are implemented. Hardware correctness and usability still need
validation; see [Validation](VALIDATION.md).

## Approved product direction, not yet implemented

- Built-in and custom object-height presets.
- Estimated distances with uncertainty ranges when justified input bounds exist.
- Broader metric/imperial support beyond the current meters/decimal-feet entry
  and result display.
- Locally stored settings and a settings control at bottom left.
- A mode selector and navigation supporting additional modes and methods.

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
