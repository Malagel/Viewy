package com.malagel.viewy.domain

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownHeightDistanceTest {
    private val top = ViewingDirection(10.0, 0.0, 2.0)
    private val bottom = ViewingDirection(10.0, 0.0, 0.0)

    private fun estimate(
        height: Double = 2.0,
        top: ViewingDirection = this.top,
        bottom: ViewingDirection = this.bottom,
        uncertainty: KnownHeightUncertainty = KnownHeightUncertainty(),
    ) = KnownHeightDistance.estimate(height, top, bottom, uncertainty)

    private fun success(result: KnownHeightResult) = result as KnownHeightResult.Estimate

    private fun fails(reason: DistanceFailureReason, result: KnownHeightResult) {
        assertEquals(KnownHeightResult.Failure(reason), result)
    }

    private fun ray(slope: Double, bearing: Double = 0.0) =
        ViewingDirection(cos(bearing), sin(bearing), slope)

    @Test fun twoMeterObjectAtTenMeters() {
        val result = success(estimate())
        assertEquals(10.0, result.horizontalDistanceMeters, 1e-12)
        assertEquals(atan(0.2), result.diagnostics.topElevationRadians, 1e-15)
        assertEquals(0.0, result.diagnostics.bottomElevationRadians, 0.0)
        assertEquals(atan(0.2), result.diagnostics.elevationSeparationRadians, 1e-15)
        assertEquals(52.0, result.diagnostics.topSensitivityMetersPerRadian!!, 1e-12)
        assertEquals(50.0, result.diagnostics.bottomSensitivityMetersPerRadian!!, 1e-12)
    }

    @Test fun endpointsAboveCamera() {
        assertEquals(10.0, success(estimate(top = ray(0.5), bottom = ray(0.3)))
            .horizontalDistanceMeters, 1e-12)
    }

    @Test fun endpointsBelowCamera() {
        assertEquals(10.0, success(estimate(top = ray(-0.3), bottom = ray(-0.5)))
            .horizontalDistanceMeters, 1e-12)
    }

    @Test fun endpointsStraddleCamera() {
        assertEquals(10.0, success(estimate(top = ray(0.1), bottom = ray(-0.1)))
            .horizontalDistanceMeters, 1e-12)
    }

    @Test fun scalingIncludingExtremeMagnitudesDoesNotChangeDistance() {
        for (scale in listOf(1e-300, 0.01, 17.0, 1e300)) {
            val result = success(estimate(
                top = ViewingDirection(scale, 0.0, scale * 0.2),
                bottom = ViewingDirection(scale * 0.5, 0.0, 0.0),
            ))
            assertEquals(10.0, result.horizontalDistanceMeters, 1e-12)
        }
        assertEquals(10.0, success(estimate(
            top = ViewingDirection(Double.MAX_VALUE, 0.0, Double.MAX_VALUE * 0.2),
            bottom = ViewingDirection(Double.MIN_VALUE, 0.0, 0.0),
        )).horizontalDistanceMeters, 1e-12)
    }

    @Test fun sharedHorizontalRotationPreservesGeometry() {
        for (bearing in listOf(-PI, -1.2, 0.8, PI)) {
            val result = success(estimate(top = ray(0.2, bearing), bottom = ray(0.0, bearing)))
            assertEquals(10.0, result.horizontalDistanceMeters, 1e-12)
            assertEquals(0.0, result.diagnostics.alignmentErrorRadians, 1e-15)
        }
    }

    @Test fun misalignmentIsDiagnosticWithoutRejection() {
        val result = success(estimate(top = ray(0.2, 0.7), bottom = ray(0.0, -0.3)))
        assertEquals(10.0, result.horizontalDistanceMeters, 1e-12)
        assertEquals(1.0, result.diagnostics.signedBearingDifferenceRadians, 1e-15)
    }

    @Test fun bearingDifferenceWrapsInBothDirections() {
        val degree = PI / 180.0
        for (sign in listOf(-1.0, 1.0)) {
            val result = success(estimate(
                top = ray(0.2, sign * -179.0 * degree),
                bottom = ray(0.0, sign * 179.0 * degree),
            ))
            assertEquals(sign * 2.0 * degree, result.diagnostics.signedBearingDifferenceRadians, 1e-14)
            assertEquals(2.0 * degree, result.diagnostics.alignmentErrorRadians, 1e-14)
        }
    }

    @Test fun alignmentDiagnosticIsInvariantUnderSharedRotation() {
        for (rotation in listOf(-3.0, 0.0, 3.0)) {
            val result = success(estimate(
                top = ray(0.2, rotation + 0.4), bottom = ray(0.0, rotation - 0.2),
            ))
            assertEquals(0.6, result.diagnostics.signedBearingDifferenceRadians, 1e-14)
        }
        val antipodal = success(estimate(top = ray(0.2, PI)))
        assertEquals(PI, antipodal.diagnostics.alignmentErrorRadians, 1e-15)
    }

    @Test fun reversedEndpointsFail() {
        fails(DistanceFailureReason.REVERSED_ENDPOINTS, estimate(top = bottom, bottom = top))
    }

    @Test fun invalidHeightsFail() {
        for (height in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            fails(DistanceFailureReason.INVALID_HEIGHT, estimate(height = height))
        }
    }

    @Test fun invalidVectorsFailForEitherEndpoint() {
        val invalid = listOf(
            ViewingDirection(0.0, 0.0, 0.0),
            ViewingDirection(Double.NaN, 0.0, 1.0),
            ViewingDirection(1.0, Double.POSITIVE_INFINITY, 1.0),
            ViewingDirection(1.0, 0.0, Double.NEGATIVE_INFINITY),
        )
        for (direction in invalid) {
            fails(DistanceFailureReason.INVALID_DIRECTION, estimate(top = direction))
            fails(DistanceFailureReason.INVALID_DIRECTION, estimate(bottom = direction))
        }
    }

    @Test fun verticalRaysHaveNoHorizontalBearing() {
        for (direction in listOf(ViewingDirection(0.0, 0.0, 1.0), ViewingDirection(0.0, 0.0, -1.0))) {
            fails(DistanceFailureReason.UNDEFINED_HORIZONTAL_BEARING, estimate(top = direction))
            fails(DistanceFailureReason.UNDEFINED_HORIZONTAL_BEARING, estimate(bottom = direction))
        }
    }

    @Test fun nearVerticalRaysRetainDistanceSeparationAndBearing() {
        for (horizontal in listOf(1e-16, 1e-160)) {
            for (sign in listOf(-1.0, 1.0)) {
                // Positive slopes: 2/h and 1/h. Negative slopes: -1/h and -2/h.
                val topHorizontal = if (sign > 0.0) horizontal else 2.0 * horizontal
                val bottomHorizontal = if (sign > 0.0) 2.0 * horizontal else horizontal
                val result = success(estimate(
                    top = ViewingDirection(0.0, topHorizontal, sign * 2.0),
                    bottom = ViewingDirection(bottomHorizontal, 0.0, sign * 2.0),
                ))
                assertEquals(2.0 * horizontal, result.horizontalDistanceMeters, horizontal * 1e-14)
                assertEquals(horizontal / 2.0, result.diagnostics.elevationSeparationRadians, horizontal * 1e-14)
                assertEquals(PI / 2.0, result.diagnostics.signedBearingDifferenceRadians, 1e-15)
                assertEquals(if (sign > 0.0) 8.0 else 2.0,
                    result.diagnostics.topSensitivityMetersPerRadian!!, 1e-12)
                assertEquals(if (sign > 0.0) 2.0 else 8.0,
                    result.diagnostics.bottomSensitivityMetersPerRadian!!, 1e-12)
            }
        }
    }

    @Test fun unrepresentableNearVerticalSlopesFailExplicitly() {
        fails(DistanceFailureReason.UNUSABLE_DISTANCE,
            estimate(top = ViewingDirection(Double.MIN_VALUE, 0.0, 1.0)))
    }

    @Test fun nearVerticalSensitivityAvoidsIntermediateUnderflowAndOverflow() {
        val result = success(estimate(height = 1e58, top = ray(1e308), bottom = ray(1e200)))
        assertEquals(1e58, result.diagnostics.topSensitivityMetersPerRadian!!, 1e46)
        assertEquals(1e-158, result.diagnostics.bottomSensitivityMetersPerRadian!!, 1e-170)
        val tinyDistance = success(estimate(top = ray(1e308)))
        assertEquals(2.0, tinyDistance.diagnostics.topSensitivityMetersPerRadian!!, 1e-12)
    }

    @Test fun nearlyEqualElevationsAreDegenerateInEitherOrder() {
        for (offset in listOf(-1e-15, 1e-15)) {
            fails(DistanceFailureReason.DEGENERATE_ELEVATIONS,
                estimate(top = ray(1.0 + offset), bottom = ray(1.0)))
        }
        fails(DistanceFailureReason.DEGENERATE_ELEVATIONS, estimate(top = ray(-1e-16)))
        fails(DistanceFailureReason.REVERSED_ENDPOINTS,
            estimate(top = ray(1.0 - 1e-8), bottom = ray(1.0)))
    }

    @Test fun equalAndNumericallyIndistinguishableSlopesFail() {
        fails(DistanceFailureReason.DEGENERATE_ELEVATIONS, estimate(top = bottom))
        fails(DistanceFailureReason.DEGENERATE_ELEVATIONS, estimate(top = ray(1e-16)))
        fails(DistanceFailureReason.DEGENERATE_ELEVATIONS,
            estimate(top = ray(1.0 + 1e-15), bottom = ray(1.0)))
    }

    @Test fun smallButResolvableSeparationExposesLargeSensitivity() {
        val result = success(estimate(top = ray(1e-8)))
        assertEquals(2e8, result.horizontalDistanceMeters, 1e-6)
        assertTrue(result.diagnostics.topSensitivityMetersPerRadian!! >= 1.9e16)
    }

    @Test fun nonfiniteAndUnderflowedDistanceFail() {
        fails(DistanceFailureReason.UNUSABLE_DISTANCE, estimate(height = Double.MAX_VALUE))
        fails(DistanceFailureReason.UNUSABLE_DISTANCE,
            estimate(height = Double.MIN_VALUE, top = ray(10.0)))
    }

    private fun bounds(
        height: InputBounds = InputBounds(1.8, 2.2),
        top: InputBounds = InputBounds(atan(0.15), atan(0.25)),
        bottom: InputBounds = InputBounds(atan(-0.05), atan(0.05)),
    ) = KnownHeightUncertainty(height, top, bottom)

    @Test fun uncertaintyExtremaMatchIndependentSlopeCalculation() {
        val result = success(estimate(uncertainty = bounds()))
        val interval = result.uncertainty as DistanceUncertainty.Bounded
        // Minimum: 1.8 / (0.25 - (-0.05)); maximum: 2.2 / (0.15 - 0.05).
        assertEquals(6.0, interval.lowerMeters, 1e-12)
        assertEquals(22.0, interval.upperMeters, 1e-12)
    }

    @Test fun pointBoundsReturnNominalDistance() {
        val interval = success(estimate(uncertainty = bounds(
            InputBounds(2.0, 2.0), InputBounds(atan(0.2), atan(0.2)), InputBounds(0.0, 0.0),
        ))).uncertainty as DistanceUncertainty.Bounded
        assertEquals(10.0, interval.lowerMeters, 1e-12)
        assertEquals(10.0, interval.upperMeters, 1e-12)
    }

    @Test fun touchingAndCrossingZeroDenominatorAreUnbounded() {
        for (lowerSlope in listOf(0.05, -0.1)) {
            val interval = success(estimate(uncertainty = bounds(
                top = InputBounds(atan(lowerSlope), atan(0.25)),
            ))).uncertainty as DistanceUncertainty.Unbounded
            assertEquals(6.0, interval.lowerMeters, 1e-12)
        }
    }

    @Test fun adjacentPositiveAngularBoundsDoNotLoseTheirFiniteUpperLimit() {
        // Reference values from 90-digit Decimal Taylor series evaluated at the exact Double
        // inputs: 2 / (tan(angle) - tan(nextDown(angle))). No production formula is reused.
        for ((angle, expectedUpper) in listOf(
            0.3 to 32882323180533290.0,
            0.5 to 27747619562982680.0,
        )) {
            val interval = success(estimate(
                top = ray(kotlin.math.tan(0.6)), bottom = ray(kotlin.math.tan(0.1)),
                uncertainty = bounds(
                    height = InputBounds(2.0, 2.0),
                    top = InputBounds(angle, 0.7),
                    bottom = InputBounds(0.0, Math.nextDown(angle)),
                ),
            )).uncertainty as DistanceUncertainty.Bounded
            assertEquals(expectedUpper, interval.upperMeters, 16.0)
        }
    }

    @Test fun adjacentNegativeAngularBoundsAvoidCancellationToo() {
        val interval = success(estimate(
            top = ray(kotlin.math.tan(-0.1)), bottom = ray(kotlin.math.tan(-0.6)),
            uncertainty = bounds(
                height = InputBounds(2.0, 2.0),
                top = InputBounds(-Math.nextDown(0.3), 0.0),
                bottom = InputBounds(-0.7, -0.3),
            ),
        )).uncertainty as DistanceUncertainty.Bounded
        assertEquals(32882323180533290.0, interval.upperMeters, 16.0)
    }

    @Test fun positiveDenominatorWithOverflowingUpperLimitIsIndeterminate() {
        val result = success(estimate(uncertainty = bounds(
            top = InputBounds(Double.MIN_VALUE, 0.3), bottom = InputBounds(-0.1, 0.0),
        )))
        assertEquals(DistanceUncertainty.NumericallyIndeterminate, result.uncertainty)
    }

    @Test fun oppositeNearPoleBoundsAvoidSineCancellation() {
        val pole = Math.nextDown(PI / 2.0)
        val interval = success(estimate(uncertainty = bounds(
            height = InputBounds(2.0, 2.0),
            top = InputBounds(0.1, pole), bottom = InputBounds(-pole, 0.0),
        ))).uncertainty as DistanceUncertainty.Bounded
        // 2 / (tan(pole) - tan(-pole)) = cot(pole).
        assertEquals(2.83276944882399e-16, interval.lowerMeters, 1e-30)
    }

    @Test fun absentOrIncompleteBoundsDoNotInventAnInterval() {
        val complete = bounds()
        for (uncertainty in listOf(
            KnownHeightUncertainty(), complete.copy(heightMeters = null),
            complete.copy(topElevationRadians = null), complete.copy(bottomElevationRadians = null),
        )) {
            val result = success(estimate(uncertainty = uncertainty))
            assertEquals(10.0, result.horizontalDistanceMeters, 1e-12)
            assertEquals(DistanceUncertainty.Unavailable, result.uncertainty)
        }
    }

    @Test fun invalidBoundsFailEvenWhenIncomplete() {
        val invalid = listOf(
            bounds(height = InputBounds(0.0, 3.0)), bounds(height = InputBounds(3.0, 4.0)),
            bounds(height = InputBounds(2.2, 1.8)), bounds(height = InputBounds(1.0, Double.NaN)),
            bounds(top = InputBounds(-PI / 2.0, PI / 2.0)),
            bounds(bottom = InputBounds(0.1, 0.2)),
            bounds(top = InputBounds(0.0, Double.POSITIVE_INFINITY)),
            KnownHeightUncertainty(heightMeters = InputBounds(-1.0, 2.0)),
        )
        for (uncertainty in invalid) {
            fails(DistanceFailureReason.INVALID_UNCERTAINTY_BOUNDS, estimate(uncertainty = uncertainty))
        }
    }

    @Test fun overflowingIntervalIsExplicitlyIndeterminate() {
        val result = success(estimate(uncertainty = bounds(height = InputBounds(1.0, Double.MAX_VALUE))))
        assertEquals(DistanceUncertainty.NumericallyIndeterminate, result.uncertainty)
    }

    @Test fun stableMethodIdentifierBelongsToLongDistance() {
        assertEquals("long_distance", MeasurementMode.LONG_DISTANCE.id)
        assertEquals("known_object_height", MeasurementMethod.KNOWN_OBJECT_HEIGHT.id)
        assertEquals(MeasurementMode.LONG_DISTANCE, MeasurementMethod.KNOWN_OBJECT_HEIGHT.mode)
    }
}
