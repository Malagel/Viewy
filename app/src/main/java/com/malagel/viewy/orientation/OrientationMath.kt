package com.malagel.viewy.orientation

import com.malagel.viewy.domain.ViewingDirection
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Unit Hamilton quaternion, active rotation of column vectors. Components are x,y,z,w. */
class OrientationRotation private constructor(val x: Double, val y: Double, val z: Double, val w: Double) {
    fun rotate(v: ViewingDirection): ViewingDirection = ViewingDirection(
        (1 - 2 * y * y - 2 * z * z) * v.x + (2 * x * y - 2 * z * w) * v.y + (2 * x * z + 2 * y * w) * v.z,
        (2 * x * y + 2 * z * w) * v.x + (1 - 2 * x * x - 2 * z * z) * v.y + (2 * y * z - 2 * x * w) * v.z,
        (2 * x * z - 2 * y * w) * v.x + (2 * y * z + 2 * x * w) * v.y + (1 - 2 * x * x - 2 * y * y) * v.z,
    )

    fun inverse() = OrientationRotation(-x, -y, -z, w)

    /** q and -q describe the same orientation. */
    fun separation(other: OrientationRotation): Double =
        2 * acos(abs(x * other.x + y * other.y + z * other.z + w * other.w).coerceIn(0.0, 1.0))

    companion object {
        /** Reject grossly non-unit inputs; normalize small norm deviations. */
        fun fromComponents(x: Double, y: Double, z: Double, w: Double): OrientationRotation? {
            if (!listOf(x, y, z, w).all { it.isFinite() }) return null
            val norm = hypot(hypot(x, y), hypot(z, w))
            if (abs(norm - 1.0) > 0.01) return null
            return OrientationRotation(x / norm, y / norm, z / norm, w / norm)
        }

        /** Android vector: x,y,z = axis*sin(theta/2); optional w, then optional accuracy. */
        fun fromRotationVector(values: FloatArray): OrientationRotation? {
            if (values.size !in 3..5) return null
            val x = values[0].toDouble()
            val y = values[1].toDouble()
            val z = values[2].toDouble()
            val squared = x * x + y * y + z * z
            if (!squared.isFinite() || squared > 1.000001) return null
            val w = if (values.size >= 4) values[3].toDouble() else sqrt((1 - squared).coerceAtLeast(0.0))
            // values[4] is optional heading accuracy (can be -1); it is not a quaternion component.
            return fromComponents(x, y, z, w)
        }
    }
}

/** Phase B deliberately uses this uncalibrated physical rear-camera axis assumption. */
object RearCameraAxis {
    val assumedDeviceDirection = ViewingDirection(0.0, 0.0, -1.0)
    const val mappingId = "rear-negative-device-z-assumption-v1"
}

fun ViewingDirection.elevationRadians(): Double = atan2(z, hypot(x, y))

/** Suppress numerically fragile near-pole heading; 1e-4 is a provisional display guard. */
fun ViewingDirection.bearingRadians(): Double? =
    if (hypot(x, y) < 1e-4) null else atan2(y, x)

fun angularSeparation(a: ViewingDirection, b: ViewingDirection): Double {
    val crossX = a.y * b.z - a.z * b.y
    val crossY = a.z * b.x - a.x * b.z
    val crossZ = a.x * b.y - a.y * b.x
    return atan2(hypot(hypot(crossX, crossY), crossZ), a.x * b.x + a.y * b.y + a.z * b.z)
}

fun wrappedDifference(a: Double, b: Double): Double = atan2(kotlin.math.sin(a - b), kotlin.math.cos(a - b))
