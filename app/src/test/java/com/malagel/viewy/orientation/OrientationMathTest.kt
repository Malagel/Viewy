package com.malagel.viewy.orientation

import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.domain.ViewingDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class OrientationMathTest {
    private fun q(x: Double, y: Double, z: Double, w: Double) = OrientationRotation.fromComponents(x,y,z,w)!!
    private fun ray(q: OrientationRotation) = q.rotate(RearCameraAxis.assumedDeviceDirection)
    private fun assertRay(x: Double, y: Double, z: Double, actual: ViewingDirection) {
        assertEquals(x, actual.x, 1e-12)
        assertEquals(y, actual.y, 1e-12)
        assertEquals(z, actual.z, 1e-12)
    }
    @Test fun identityRearAxisPointsDownAndHasNoBearing() {
        val direction = ray(q(0.0,0.0,0.0,1.0))
        assertRay(0.0,0.0,-1.0,direction)
        assertEquals(-PI/2, direction.elevationRadians(), 0.0)
        assertNull(direction.bearingRadians())
    }
    @Test fun uprightPhoneRearAxisPointsAlongPositiveY() {
        assertRay(0.0,1.0,0.0,ray(q(sqrt(0.5),0.0,0.0,sqrt(0.5))))
    }
    @Test fun pitchSignsAgreeWithCameraElevation() {
        for (elevation in listOf(-0.6,0.0,0.6)) {
            val angle = PI/2 + elevation
            assertEquals(elevation, ray(q(sin(angle/2),0.0,0.0,cos(angle/2))).elevationRadians(),1e-12)
        }
    }
    @Test fun yawSignAndHorizontalHandedness() {
        // Rz(90) Rx(90): rear ray +Y becomes -X.
        assertRay(-1.0,0.0,0.0,ray(q(0.5,0.5,0.5,0.5)))
        assertEquals(PI/2, wrappedDifference(PI, PI/2), 1e-15)
    }
    @Test fun localRollAndAllDisplayRotationsPreservePhysicalRay() {
        // Rx(90) Rz(roll); display rotation changes no physical sensor coordinates.
        for (roll in listOf(0.0, PI/2, PI, 3*PI/2, -0.7)) {
            val s = sqrt(0.5)
            val rotation = q(s*cos(roll/2), -s*sin(roll/2), s*sin(roll/2), s*cos(roll/2))
            assertRay(0.0,1.0,0.0,ray(rotation))
        }
    }
    @Test fun mixedYawPitchAndRollMatchIndependentPhysicalRay() {
        // Rz(yaw) Rx(pitch) Rz(localRoll); expected ray from matrix application.
        for (yaw in listOf(-2.7,0.0,1.2)) for (pitch in listOf(0.2,1.1,2.8)) {
            for (roll in listOf(-2.0,0.7,2.9)) {
                val a = sin(pitch/2)
                val b = cos(pitch/2)
                val rotation = q(
                    a*cos((yaw-roll)/2), a*sin((yaw-roll)/2),
                    b*sin((yaw+roll)/2), b*cos((yaw+roll)/2),
                )
                val actual = ray(rotation)
                assertRay(-sin(yaw)*sin(pitch), cos(yaw)*sin(pitch), -cos(pitch), actual)
                val original = rotation.inverse().rotate(actual)
                assertRay(0.0,0.0,-1.0,original)
            }
        }
    }
    @Test fun inverseCameraPoseRequiresTransposeNotForwardRotation() {
        // Illustrates the documented Phase C pose rule: device-to-camera metadata must be inverted.
        val pose = q(0.0,sqrt(0.5),0.0,sqrt(0.5))
        assertRay(-1.0,0.0,0.0,pose.inverse().rotate(ViewingDirection(0.0,0.0,1.0)))
        assertRay(1.0,0.0,0.0,pose.rotate(ViewingDirection(0.0,0.0,1.0)))
    }
    @Test fun quaternionSignDoesNotChangeRayOrOrientationDistance() {
        val positive = q(0.5,0.5,0.5,0.5)
        val negative = q(-0.5,-0.5,-0.5,-0.5)
        assertEquals(ray(positive),ray(negative))
        assertEquals(0.0,positive.separation(negative),1e-15)
    }
    @Test fun threeFourAndFiveComponentPlatformVectors() {
        val three = OrientationRotation.fromRotationVector(floatArrayOf(0.5f,0.5f,0.5f))!!
        val four = OrientationRotation.fromRotationVector(floatArrayOf(0.5f,0.5f,0.5f,0.5f))!!
        val five = OrientationRotation.fromRotationVector(floatArrayOf(0.5f,0.5f,0.5f,0.5f,-1f))!!
        assertEquals(ray(three),ray(four))
        assertEquals(ray(four),ray(five))
    }
    @Test fun invalidPlatformVectorsFailWithoutFabricatingDirection() {
        for (values in listOf(floatArrayOf(),floatArrayOf(0f,0f),floatArrayOf(2f,0f,0f),
            floatArrayOf(0f,0f,0f,0f),floatArrayOf(Float.NaN,0f,0f),
            floatArrayOf(0f,0f,0f,Float.POSITIVE_INFINITY),FloatArray(6))) {
            assertNull(OrientationRotation.fromRotationVector(values))
        }
    }
    @Test fun smallFloatNormErrorIsNormalized() {
        assertRay(0.0,0.0,-1.0,ray(q(0.0,0.0,0.0,1.000001)))
    }
    @Test fun nearVerticalRayIsFiniteButBearingSuppressed() {
        val direction = ray(q(sin(1e-6),0.0,0.0,cos(1e-6)))
        assertTrue(direction.elevationRadians().isFinite())
        assertNull(direction.bearingRadians())
    }
    @Test fun transformedEndpointsFeedPhaseAWithoutAdapterChanges() {
        fun aimed(elevation: Double): ViewingDirection {
            val angle = PI/2 + elevation
            return ray(q(sin(angle/2),0.0,0.0,cos(angle/2)))
        }
        val result = KnownHeightDistance.estimate(2.0, aimed(kotlin.math.atan(0.2)), aimed(0.0)) as KnownHeightResult.Estimate
        assertEquals(10.0,result.horizontalDistanceMeters,1e-12)
        assertEquals(0.0,result.diagnostics.alignmentErrorRadians,1e-12)
    }
    @Test fun bearingWrapUsesShortArc() {
        assertEquals(Math.toRadians(2.0),wrappedDifference(Math.toRadians(-179.0),Math.toRadians(179.0)),1e-15)
        assertEquals(Math.toRadians(-2.0),wrappedDifference(Math.toRadians(179.0),Math.toRadians(-179.0)),1e-15)
    }
}
