package com.malagel.viewy.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import com.malagel.viewy.camera.CameraDirectionMapping

enum class AcquisitionFailure { UNAVAILABLE, REGISTRATION_FAILED }

/** Immutable snapshots. Sensor callbacks and publication are confined to the main looper. */
data class AcquisitionState(
    val discoveryCompleted: Boolean = false,
    val failure: AcquisitionFailure? = null,
    val availableSources: Set<OrientationSource> = emptySet(),
    val activeSource: OrientationSource? = null,
    val sensorName: String? = null,
    val running: Boolean = false,
    val startupFallbackUsed: Boolean = false,
    val referenceFrameId: String? = null,
    val interruption: Interruption? = null,
    val observation: OrientationObservation? = null,
    val quality: OrientationQuality? = null,
)

/** Small platform boundary; no raw gyro integration, fusion, batching, camera or display dependency. */
class AndroidOrientationAcquisition(context: Context, private val onState: (AcquisitionState) -> Unit) {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: SensorEventListener? = null
    private var session: OrientationSession? = null
    private var state = AcquisitionState()
    private val registration = OrientationRegistration()
    private val refresh = object : Runnable {
        override fun run() {
            publish()
            if (listener != null) handler.postDelayed(this, 100L)
        }
    }

    /** Fallback is attempted only before a registration/session starts. Explicit restarts reset it. */
    fun start(mapping: CameraDirectionMapping? = null) {
        check(Looper.myLooper() == Looper.getMainLooper())
        stop()
        val sensors = OrientationSource.entries.mapNotNull { source ->
            manager?.getDefaultSensor(source.androidType())?.let { source to it }
        }.toMap()
        state = AcquisitionState(discoveryCompleted = true, availableSources = sensors.keys.toSet())
        var registrationFailed = false
        for (source in OrientationSource.entries) {
            val sensor = sensors[source] ?: continue
            val newSession = if (mapping == null) OrientationSession(source) else OrientationSession(
                source, deviceDirection = mapping.deviceDirection, cameraMappingId = mapping.id,
            )
            val token = registration.begin()
            val newListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!registration.accepts(token) || event.sensor != sensor) return
                    if (Build.VERSION.SDK_INT >= 33 && event.firstEventAfterDiscontinuity) {
                        newSession.interrupt(Interruption.PLATFORM_DISCONTINUITY)
                    }
                    newSession.updateAccuracy(event.accuracy)
                    newSession.add(event.values, event.timestamp, SystemClock.elapsedRealtimeNanos())
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (registration.accepts(token)) newSession.updateAccuracy(accuracy)
                }
            }
            val registered = try {
                // 25 Hz requested; actual rate is a platform hint. No batched/stale delivery requested.
                manager?.registerListener(newListener, sensor, 40_000, 0, handler) == true
            } catch (_: RuntimeException) {
                false
            }
            if (registered) {
                session = newSession
                listener = newListener
                state = state.copy(
                    activeSource = source, sensorName = sensor.name, running = true,
                    startupFallbackUsed = registrationFailed,
                )
                handler.post(refresh)
                return
            }
            manager?.unregisterListener(newListener)
            registration.invalidate()
            registrationFailed = true
        }
        state = state.copy(failure = if (registrationFailed) AcquisitionFailure.REGISTRATION_FAILED else AcquisitionFailure.UNAVAILABLE)
        publish()
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        registration.invalidate() // Discard callbacks already queued by an old registration.
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        handler.removeCallbacks(refresh)
        session?.interrupt(Interruption.STOPPED)
        val interruption = session?.lastInterruption ?: state.interruption
        session = null
        state = state.copy(running = false, failure = null, interruption = interruption, observation = null, quality = null, referenceFrameId = null)
        onState(state)
    }

    fun capture(): CaptureResult = session?.capture(SystemClock.elapsedRealtimeNanos())
        ?: CaptureResult.Rejected(CaptureStatus.WAITING)

    private fun publish() {
        val now = SystemClock.elapsedRealtimeNanos()
        state = state.copy(
            observation = session?.latest(now), quality = session?.quality(now),
            referenceFrameId = session?.referenceFrameId, interruption = session?.lastInterruption,
        )
        onState(state)
    }
}

private fun OrientationSource.androidType(): Int = when (this) {
    OrientationSource.GAME_ROTATION_VECTOR -> Sensor.TYPE_GAME_ROTATION_VECTOR
    OrientationSource.ROTATION_VECTOR -> Sensor.TYPE_ROTATION_VECTOR
}
