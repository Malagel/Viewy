package com.malagel.viewy.camera

import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.orientation.OrientationRotation
import com.malagel.viewy.orientation.RearCameraAxis

/** Device-native sensor axes, independent of display rotation and SENSOR_ORIENTATION. */
data class CameraDirectionMapping(
    val id: String,
    val deviceDirection: ViewingDirection,
    val usesLensPose: Boolean = false,
) {
    companion object {
        /**
         * Only explicit PRIMARY_CAMERA (0) / GYROSCOPE (1) references share Android
         * sensor axes. Pre-28, undefined, automotive and unknown references are not used.
         * R_CD maps device into camera, so invert it to map camera +Z into device.
         * This is an optical-axis estimate, NOT a calibrated preview-center ray.
         */
        fun forRearCamera(cameraIdentity: String, poseReference: Int?, pose: FloatArray?): CameraDirectionMapping {
            val rotation = if (poseReference in listOf(0, 1) && pose?.size == 4) {
                OrientationRotation.fromComponents(pose[0].toDouble(), pose[1].toDouble(), pose[2].toDouble(), pose[3].toDouble())
            } else null
            val axis = rotation?.inverse()?.rotate(ViewingDirection(0.0, 0.0, 1.0))
            // A rear-facing optical axis must point into the rear device hemisphere.
            if (axis != null && axis.z < 0.0) return CameraDirectionMapping(
                "$cameraIdentity:pose-$poseReference:${pose!!.joinToString()}", axis,
                usesLensPose = true,
            )
            return CameraDirectionMapping(
                "$cameraIdentity:${RearCameraAxis.mappingId}", RearCameraAxis.assumedDeviceDirection,
            )
        }
    }
}
