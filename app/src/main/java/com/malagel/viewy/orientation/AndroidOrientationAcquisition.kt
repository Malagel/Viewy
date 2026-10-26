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

/** Immutable snapshots. Sensor callbacks and publication are confined to the main looper. */
data class AcquisitionState(
    val availableSources: Set<OrientationSource> = emptySet(),
    val activeSource: OrientationSource? = null,
    val sensorName: String? = null,
    val running: Boolean = false,
    val message: String = "Stopped",
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
    private var generation = 0L
    private val refresh = object : Runnable {
        override fun run() {
            publish()
            if (listener != null) handler.postDelayed(this, 100L)
        }
    }

    /** Fallback is attempted only before a registration/session starts. Explicit restarts reset it. */
    fun start(requestedSource: OrientationSource? = null, mapping: CameraDirectionMapping? = null) {
        check(Looper.myLooper() == Looper.getMainLooper())
        stop()
        val sensors = OrientationSource.entries.mapNotNull { source ->
            manager?.getDefaultSensor(source.androidType())?.let { source to it }
        }.toMap()
        state = AcquisitionState(availableSources = sensors.keys.toSet())
        val candidates = if (requestedSource == null) OrientationSource.entries else listOf(requestedSource)
        val failed = mutableListOf<String>()
        for (source in candidates) {
            val sensor = sensors[source] ?: continue
            val newSession = if (mapping == null) OrientationSession(source) else OrientationSession(
                source, deviceDirection = mapping.deviceDirection, cameraMappingId = mapping.id,
            )
            val token = generation
            val newListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (token != generation || event.sensor != sensor) return
                    if (Build.VERSION.SDK_INT >= 33 && event.firstEventAfterDiscontinuity) {
                        newSession.interrupt(Interruption.PLATFORM_DISCONTINUITY)
                    }
                    newSession.updateAccuracy(event.accuracy)
                    newSession.add(event.values, event.timestamp, SystemClock.elapsedRealtimeNanos())
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (token == generation) newSession.updateAccuracy(accuracy)
                }
            }
            val registered = try {
                // 25 Hz requested; actual rate is a platform hint. No batched/stale delivery requested.
                manager?.registerListener(newListener, sensor, 40_000, 0, handler) == true
            } catch (error: RuntimeException) {
                failed += "${source.name}: ${error.javaClass.simpleName}"
                false
            }
            if (registered) {
                session = newSession
                listener = newListener
                state = state.copy(
                    activeSource = source, sensorName = sensor.name, running = true,
                    message = if (failed.isEmpty()) "Listening" else "Listening; startup fallback after ${failed.joinToString()}",
                )
                handler.post(refresh)
                return
            }
            manager?.unregisterListener(newListener)
            generation++
            failed += "${source.name}: registration failed"
        }
        state = state.copy(message = if (failed.isEmpty()) "Requested orientation sensors unavailable" else failed.joinToString())
        publish()
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        generation++ // Discard callbacks already queued by an old registration.
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        handler.removeCallbacks(refresh)
        session?.interrupt(Interruption.STOPPED)
        session = null
        state = state.copy(running = false, message = "Stopped", observation = null, quality = null, referenceFrameId = null)
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
