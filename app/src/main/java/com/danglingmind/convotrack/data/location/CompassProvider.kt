package com.danglingmind.convotrack.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Device compass heading, in degrees clockwise from true-ish north — the Android analogue of iOS
 * `CLLocationManager.startUpdatingHeading` / `CLHeading`.
 *
 * This exists only as a **fallback for when the rider is stopped**. A GPS-derived bearing is
 * computed from successive fixes, so below a walking pace it wanders wildly — precisely when a
 * rider is sitting at a light and would notice the map spinning. Above that threshold the GPS
 * bearing is the true direction of travel and outranks this.
 *
 * The reason it is only ever a fallback (and never allowed to fight a fresh road bearing) is that
 * a compass reports where the PHONE points, which on a bike is wherever the mount or the jacket
 * pocket happens to face. Because the same value also drives the camera bearing, letting the
 * compass win would swing the entire map with the handlebars instead of following the road.
 */
class CompassProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading

    private var started = false

    fun start() {
        val sensor = rotationVector ?: return
        if (started) return
        started = true
        // GAME delay (~50 Hz) rather than UI: the value is smoothed downstream by the 5° gate in
        // the navigation view model, and a slower rate makes a stopped rider's arrow lag visibly
        // when they turn the bike by hand.
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager?.unregisterListener(this)
        _heading.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        _heading.value = (degrees + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
