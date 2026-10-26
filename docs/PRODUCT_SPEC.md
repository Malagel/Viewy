# Viewy product specification

Viewy is an offline Android app intended to estimate object distances using
the phone's camera, orientation sensors, and an estimated physical object height.

## Product requirements

Modes describe what the user measures; methods describe how. The first mode is
**Long Distance**, with **Known Object Height — Two Endpoint Captures** as its
first method. Additional modes and methods are planned, without defined implementations.

- Live camera preview in a rounded rectangle with a centered aiming reticle.
- Capture orientation at two vertical endpoints of an object.
- Estimate distance from angular elevations and the selected object height,
  accounting for pitch, roll, and horizontal alignment.
- Built-in and custom object-height presets.
- Display estimated distances with uncertainty ranges.
- Support metric and imperial units.
- Minimalist Material 3 interface with light and dark themes, a circular
  measurement button, settings at bottom left, and a planned mode selector.
- Store settings locally and operate entirely offline, without analytics or ads.

## Current implementation

The UI foundation is implemented: light/dark themes, a neutral preview
placeholder, reticle, and disabled measurement, settings, and pinned-object
controls labeled as coming soon. No camera or network permissions are declared.

Phase A implements pure Kotlin known-height horizontal-distance calculations,
typed geometry failures, alignment and sensitivity diagnostics, and conditional
distance bounds when complete input bounds are explicitly supplied. Stable mode
and method identifiers exist; these calculations are not connected to the UI.

Phase B implements foreground orientation acquisition, gravity-aligned rear-axis
transforms, timestamp/reference-frame validation, recent aiming stability, and
typed captures/comparisons. Debug builds append a development diagnostic panel
below the foundation controls. The rear optical axis is an explicitly uncalibrated
negative-device-Z assumption; physical sensor validation remains pending. See
[Orientation acquisition](ORIENTATION_ACQUISITION.md).

The existing pinned-object placeholder remains until a later UI phase replaces
it with the mode selector. Camera preview, production measurement UI integration,
presets, settings persistence, and navigation remain unimplemented. Runtime
verification is tracked in [Build verification](BUILD_VERIFICATION.md).
