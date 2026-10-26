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

Phase A implements pure Kotlin known-height horizontal-distance calculations,
typed geometry failures, alignment and sensitivity diagnostics, and conditional
bounds when complete valid input bounds are supplied. Stable mode/method IDs
remain separate from the method-specific capture workflow.

Phase B implements foreground orientation acquisition, timestamp/reference-frame
validation, recent aiming stability, and typed captures/comparisons. Game Rotation
Vector is preferred; Rotation Vector is a startup fallback. Both production and
debug tools now share this acquisition in Phase C.

Phase C implements rear-camera preview and permission recovery, a centered reticle,
meters/feet height entry, top/bottom captures, provisional stability/alignment
checks, horizontal-distance results, recapture/restart, and lifecycle recovery.
The mode button explains the single available method. Debug-only diagnostics
remain separate from the main workflow. See [Camera measurement](CAMERA_MEASUREMENT.md).

No error bounds are inferred from stationary sample variation. Nominal-only input
shows unquantified uncertainty; the domain still supports conditional bounds for
future explicitly bounded input. Lens pose is optional; preview-center direction
and preview/sensor timing remain approximate and require physical validation.

Built-in/custom preset management, persistent settings/history, additional
methods and navigation remain planned. No network, image capture, recording,
analytics or storage features are introduced. Automated and physical verification
are tracked separately in [Build verification](BUILD_VERIFICATION.md).
