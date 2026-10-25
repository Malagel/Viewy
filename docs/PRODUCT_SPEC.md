# Viewy product specification

Viewy (`com.malagel.viewy`) is an offline Android app intended to estimate
object distances from the phone's camera and orientation sensors.

## Task 1: implemented foundation

A single Kotlin/Compose activity, Material 3 light and dark themes, a large
rounded neutral preview placeholder, centered reticle, circular measurement
button, settings icon at bottom left, and pinned-object icon at bottom right.
All controls are disabled and labeled as coming soon. No camera permissions
or network permissions are declared. Compilation and runtime verification
status is recorded in `BUILD_VERIFICATION.md`.

## Planned features — not implemented

- Live camera preview inside the rounded rectangle.
- Aim at two vertical endpoints and record orientation at each endpoint.
- Estimate distance from angular elevations and an estimated physical height.
- Account for pitch, roll, and horizontal alignment.
- Built-in and custom object-height presets, including a pinned preferred height.
- Estimated distances with uncertainty ranges.
- Metric and imperial units.
- Minimalist Material 3 interactions and local settings.
- Completely offline operation, without analytics or advertising.

Future milestones must keep camera acquisition, sensor acquisition, distance
mathematics, presentation state, and local storage responsibilities separate.
Task 1 does not implement measurement workflows, sensors, persistence, presets,
or navigation.
