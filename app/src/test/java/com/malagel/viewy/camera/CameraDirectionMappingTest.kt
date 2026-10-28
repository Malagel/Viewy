package com.malagel.viewy.camera

import com.malagel.viewy.orientation.*
import com.malagel.viewy.domain.ViewingDirection
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CameraDirectionMappingTest {
    @Test fun nonSelfInversePoseUsesTheInverseRatherThanForwardRotation() {
        val angle = Math.toRadians(150.0)
        val m = CameraDirectionMapping.forRearCamera("rear", 1,
            floatArrayOf(sin(angle/2).toFloat(), 0f, 0f, cos(angle/2).toFloat()))
        assertEquals(0.0, m.deviceDirection.x, 1e-7)
        assertEquals(sin(angle), m.deviceDirection.y, 1e-7)
        assertEquals(cos(angle), m.deviceDirection.z, 1e-7)
    }

    @Test fun rollAboutTheSelectedPhysicalAxisPreservesDirection() {
        val m = CameraDirectionMapping.forRearCamera("rear", 1,
            floatArrayOf(sin(Math.toRadians(150.0)/2).toFloat(), 0f, 0f, cos(Math.toRadians(150.0)/2).toFloat()))
        val a = m.deviceDirection
        val halfRoll = 0.4
        val rotation = OrientationRotation.fromComponents(a.x*sin(halfRoll), a.y*sin(halfRoll), a.z*sin(halfRoll), cos(halfRoll))!!
        val rolled = rotation.rotate(a)
        assertEquals(a.x, rolled.x, 1e-12)
        assertEquals(a.y, rolled.y, 1e-12)
        assertEquals(a.z, rolled.z, 1e-12)
    }
    @Test fun inversePoseMapsCameraOpticalAxisIntoDeviceCoordinates() {
        // 180° device Y followed by a small mount tilt in device X.
        val a = Math.toRadians(2.0)
        val pose = floatArrayOf(0f, cos(a/2).toFloat(), -sin(a/2).toFloat(), 0f)
        val mapping = CameraDirectionMapping.forRearCamera("camera", 1, pose)
        assertEquals(0.0, mapping.deviceDirection.x, 1e-7)
        assertEquals(-sin(a), mapping.deviceDirection.y, 1e-7)
        assertEquals(-cos(a), mapping.deviceDirection.z, 1e-7)
        val rotation = OrientationRotation.fromRotationVector(pose)!!
        val optical = rotation.rotate(mapping.deviceDirection)
        assertEquals(0.0, optical.x, 1e-7)
        assertEquals(0.0, optical.y, 1e-7)
        assertEquals(1.0, optical.z, 1e-7)
        assertTrue(mapping.usesLensPose)
    }
    @Test fun unsuitableReferencesAndMalformedPoseFallBackWithoutInventedCorrection() {
        val rearPose = floatArrayOf(0f, 1f, 0f, 0f)
        for ((reference, pose) in listOf(null to rearPose, 2 to rearPose, 3 to rearPose, 99 to rearPose,
            1 to null, 1 to floatArrayOf(0f, 0f), 0 to floatArrayOf(Float.NaN, 0f, 0f, 1f),
            1 to floatArrayOf(0f, 0f, 0f, 2f), 1 to floatArrayOf(0f, 0f, 0f, 1f))) {
            val m = CameraDirectionMapping.forRearCamera("rear", reference, pose)
            assertFalse(m.usesLensPose)
            assertEquals(RearCameraAxis.assumedDeviceDirection, m.deviceDirection)
            assertTrue(m.id.contains(RearCameraAxis.mappingId))
        }
    }
    @Test fun explicitPrimaryReferenceUsesSharedDeviceAxes() {
        val m = CameraDirectionMapping.forRearCamera("rear", 0, floatArrayOf(0f, 1f, 0f, 0f))
        assertTrue(m.id.contains("pose-0"))
        assertEquals(ViewingDirection(0.0, 0.0, -1.0), m.deviceDirection)
    }
    @Test fun differentSelectedLensesHaveDifferentMappingIdentityEvenWithFallback() {
        assertNotEquals(CameraDirectionMapping.forRearCamera("a", null, null).id,
            CameraDirectionMapping.forRearCamera("b", null, null).id)
    }
    @Test fun acquisitionAppliesSelectedMappingBeforeQualityAndCapture() {
        val angle = Math.toRadians(2.0)
        val mapping = CameraDirectionMapping.forRearCamera("rear", 1,
            floatArrayOf(0f, cos(angle/2).toFloat(), -sin(angle/2).toFloat(), 0f))
        val session = OrientationSession(OrientationSource.GAME_ROTATION_VECTOR,
            deviceDirection = mapping.deviceDirection, cameraMappingId = mapping.id)
        val q = floatArrayOf(sqrt(0.5).toFloat(), 0f, 0f, sqrt(0.5).toFloat())
        repeat(13) { val t = 1_000_000_000L+it*40_000_000L; session.add(q, t, t) }
        val captured = (session.capture(1_480_000_000L) as CaptureResult.Captured).observation
        assertEquals(mapping.id, captured.cameraMappingId)
        assertEquals(cos(angle), captured.direction.y, 1e-7)
        assertEquals(-sin(angle), captured.direction.z, 1e-7)
        assertEquals(0.0, captured.quality.maximumVariationRadians!!, 1e-7)
    }
}
