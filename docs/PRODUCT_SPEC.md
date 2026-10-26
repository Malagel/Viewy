# Viewy product specification

Viewy is an offline Android app intended to estimate object distances using
the phone's camera, orientation sensors, and an estimated physical object height.

## Product requirements

- Live camera preview in a rounded rectangle with a centered aiming reticle.
- Capture orientation at two vertical endpoints of an object.
- Estimate distance from angular elevations and the selected object height,
  accounting for pitch, roll, and horizontal alignment.
- Built-in and custom object-height presets, with a pinned preferred height.
- Display estimated distances with uncertainty ranges.
- Support metric and imperial units.
- Minimalist Material 3 interface with light and dark themes, a circular
  measurement button, settings at bottom left, and pinned object at bottom right.
- Store settings locally and operate entirely offline, without analytics or ads.

## Current implementation

Only the UI foundation is implemented: light/dark themes, a neutral preview
placeholder, reticle, and disabled measurement, settings, and pinned-object
controls labeled as coming soon. No camera or network permissions are declared.

Camera preview, sensor acquisition, measurement calculations, presets, settings
persistence, and navigation remain unimplemented. Runtime verification is tracked
in [Build verification](BUILD_VERIFICATION.md).
