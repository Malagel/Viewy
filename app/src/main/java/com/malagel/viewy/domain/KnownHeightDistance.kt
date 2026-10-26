package com.malagel.viewy.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** A direction in a shared gravity-aligned frame: Z is up, X/Y are horizontal.
 * Magnitude is irrelevant. Sensor/camera frame conversion belongs to a future adapter.
 */
data class ViewingDirection(val x: Double, val y: Double, val z: Double)

/** Inclusive absolute bounds, in meters for height and radians for elevation. */
data class InputBounds(val lower: Double, val upper: Double)

data class KnownHeightUncertainty(
    val heightMeters: InputBounds? = null,
    val topElevationRadians: InputBounds? = null,
    val bottomElevationRadians: InputBounds? = null,
)

sealed interface DistanceUncertainty {
    data object Unavailable : DistanceUncertainty
    data class Bounded(val lowerMeters: Double, val upperMeters: Double) : DistanceUncertainty
    /** Allowed geometry approaches a zero positive denominator; no finite upper bound exists. */
    data class Unbounded(val lowerMeters: Double) : DistanceUncertainty
    /** Bounds are valid, but their arithmetic exceeds usable floating-point range. */
    data object NumericallyIndeterminate : DistanceUncertainty
}

data class DistanceDiagnostics(
    val topElevationRadians: Double,
    val bottomElevationRadians: Double,
    val elevationSeparationRadians: Double,
    /** Signed top minus bottom bearing, wrapped to [-PI, PI]; antipodal sign is ambiguous. */
    val signedBearingDifferenceRadians: Double,
    /** Absolute first-order derivatives; null means the derivative overflowed. */
    val topSensitivityMetersPerRadian: Double?,
    val bottomSensitivityMetersPerRadian: Double?,
) {
    val alignmentErrorRadians: Double get() = abs(signedBearingDifferenceRadians)
}

enum class DistanceFailureReason {
    INVALID_HEIGHT,
    INVALID_DIRECTION,
    UNDEFINED_HORIZONTAL_BEARING,
    REVERSED_ENDPOINTS,
    DEGENERATE_ELEVATIONS,
    UNUSABLE_DISTANCE,
    INVALID_UNCERTAINTY_BOUNDS,
}

sealed interface KnownHeightResult {
    data class Estimate(
        val horizontalDistanceMeters: Double,
        val diagnostics: DistanceDiagnostics,
        val uncertainty: DistanceUncertainty,
    ) : KnownHeightResult

    data class Failure(val reason: DistanceFailureReason) : KnownHeightResult
}

/**
 * Computes H / (top.z / hypot(top.x, top.y) - bottom.z / hypot(bottom.x, bottom.y)).
 * Assumes a stationary camera, a vertical object and endpoints on the same horizontal
 * position. The result is horizontal distance to that vertical line, not slant range.
 * Bearing mismatch is diagnostic only; no sensor accuracy or alignment threshold is assumed.
 */
object KnownHeightDistance {
    // Floating-point resolution guard, not a calibrated measurement quality threshold.
    private val resolution = 32.0 * Math.ulp(1.0)

    fun estimate(
        heightMeters: Double,
        top: ViewingDirection,
        bottom: ViewingDirection,
        uncertainty: KnownHeightUncertainty = KnownHeightUncertainty(),
    ): KnownHeightResult {
        if (!heightMeters.isFinite() || heightMeters <= 0.0) {
            return failure(DistanceFailureReason.INVALID_HEIGHT)
        }
        val topRay = normalize(top) ?: return failure(DistanceFailureReason.INVALID_DIRECTION)
        val bottomRay = normalize(bottom) ?: return failure(DistanceFailureReason.INVALID_DIRECTION)
        if ((top.x == 0.0 && top.y == 0.0) || (bottom.x == 0.0 && bottom.y == 0.0)) {
            return failure(DistanceFailureReason.UNDEFINED_HORIZONTAL_BEARING)
        }
        val topSlope = topRay.z / topRay.horizontal
        val bottomSlope = bottomRay.z / bottomRay.horizontal
        val difference = topSlope - bottomSlope
        if (!difference.isFinite()) return failure(DistanceFailureReason.UNUSABLE_DISTANCE)
        if (abs(difference) <= resolution * max(1.0, max(abs(topSlope), abs(bottomSlope)))) {
            return failure(DistanceFailureReason.DEGENERATE_ELEVATIONS)
        }
        if (difference < 0.0) return failure(DistanceFailureReason.REVERSED_ENDPOINTS)
        val distance = heightMeters / difference
        if (!distance.isFinite() || distance <= 0.0) {
            return failure(DistanceFailureReason.UNUSABLE_DISTANCE)
        }
        val topElevation = atan2(topRay.z, topRay.horizontal)
        val bottomElevation = atan2(bottomRay.z, bottomRay.horizontal)
        val interval = interval(heightMeters, topElevation, bottomElevation, uncertainty)
            ?: return failure(DistanceFailureReason.INVALID_UNCERTAINTY_BOUNDS)
        // Bearing depends only on the original horizontal components, however small.
        val bearingDelta = atan2(top.y, top.x) - atan2(bottom.y, bottom.x)
        val wrappedDelta = when {
            bearingDelta > PI -> bearingDelta - 2.0 * PI
            bearingDelta < -PI -> bearingDelta + 2.0 * PI
            else -> bearingDelta
        }
        return KnownHeightResult.Estimate(
            distance,
            DistanceDiagnostics(
                topElevation, bottomElevation,
                // Avoid subtracting elevations that may both round to +/-PI/2.
                atan2(
                    topRay.z * bottomRay.horizontal - topRay.horizontal * bottomRay.z,
                    topRay.horizontal * bottomRay.horizontal + topRay.z * bottomRay.z,
                ),
                wrappedDelta,
                sensitivity(distance, topSlope, difference),
                sensitivity(distance, bottomSlope, difference),
            ),
            interval,
        )
    }

    private fun sensitivity(distance: Double, slope: Double, difference: Double): Double? {
        // Factoring sec^2 avoids overflowing slope*slope for near-vertical rays.
        val secant = hypot(1.0, slope)
        val ratio = secant / difference
        val factor = secant * ratio
        // Scale by distance first only when the factor itself overflows.
        return (if (factor.isFinite()) distance * factor else (distance * ratio) * secant)
            .takeIf { it.isFinite() }
    }

    private data class Ray(val x: Double, val y: Double, val z: Double) {
        val horizontal: Double get() = hypot(x, y)
    }

    private fun normalize(direction: ViewingDirection): Ray? {
        val (x, y, z) = direction
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        val scale = max(abs(x), max(abs(y), abs(z)))
        if (scale == 0.0) return null
        // Scaling by the largest component avoids norm overflow/underflow; unit length is unnecessary.
        return Ray(x / scale, y / scale, z / scale)
    }

    private fun interval(
        height: Double,
        top: Double,
        bottom: Double,
        uncertainty: KnownHeightUncertainty,
    ): DistanceUncertainty? {
        val h = uncertainty.heightMeters
        val t = uncertainty.topElevationRadians
        val b = uncertainty.bottomElevationRadians
        if (h != null && (!h.contains(height) || h.lower <= 0.0)) return null
        if (t != null && !t.validAngle(top)) return null
        if (b != null && !b.validAngle(bottom)) return null
        if (h == null || t == null || b == null) return DistanceUncertainty.Unavailable

        // tan is increasing on the validated open interval (-PI/2, PI/2).
        val largestDenominator = tangentDifference(t.upper, b.lower)
        val smallestDenominator = tangentDifference(t.lower, b.upper)
        if (!largestDenominator.isFinite() || largestDenominator <= 0.0 ||
            !smallestDenominator.isFinite()
        ) return DistanceUncertainty.NumericallyIndeterminate
        val lower = h.lower / largestDenominator
        if (!lower.isFinite() || lower <= 0.0) return DistanceUncertainty.NumericallyIndeterminate
        // Ordering the angles determines whether a pole is admitted, independently of rounding.
        if (t.lower <= b.upper) return DistanceUncertainty.Unbounded(lower)
        if (smallestDenominator <= 0.0) return DistanceUncertainty.NumericallyIndeterminate
        val upper = h.upper / smallestDenominator
        if (!upper.isFinite() || upper <= 0.0) return DistanceUncertainty.NumericallyIndeterminate
        return DistanceUncertainty.Bounded(lower, upper)
    }

    private fun tangentDifference(top: Double, bottom: Double): Double =
        if ((top >= 0.0 && bottom >= 0.0) || (top <= 0.0 && bottom <= 0.0)) {
            // tan(a)-tan(b) = sin(a-b)/(cos(a)*cos(b)); no close-tangent cancellation.
            sin(top - bottom) / (cos(top) * cos(bottom))
        } else {
            // Opposite signs cannot cancel; subtraction avoids sin near +/-PI.
            tan(top) - tan(bottom)
        }

    private fun InputBounds.contains(value: Double): Boolean =
        lower.isFinite() && upper.isFinite() && lower <= value && value <= upper

    private fun InputBounds.validAngle(value: Double): Boolean =
        contains(value) && lower > -PI / 2.0 && upper < PI / 2.0

    private fun failure(reason: DistanceFailureReason) = KnownHeightResult.Failure(reason)
}
