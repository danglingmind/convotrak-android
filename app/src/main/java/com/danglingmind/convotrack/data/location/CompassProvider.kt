package com.danglingmind.convotrack.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
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

    private val appContext = context.applicationContext

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
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

        // getOrientation() reads the azimuth off the device's NATURAL axes — for a phone, the
        // portrait ones — and that is true no matter how the screen is currently turned. Without
        // the remap below the compass answered "where is the top of the phone pointing?" when the
        // question navigation actually asks is "where is the RIDER pointing?". Those are the same
        // question only while the screen is upright. The navigation screen unlocks rotation
        // (SCREEN_ORIENTATION_USER), so the moment the rider turns the phone sideways on the
        // mount the two differ by exactly 90° — and by 90° the other way round in the other
        // landscape — leaving the heading-up camera and the direction chevron, which both read
        // this one value, a quarter-turn askew for as long as the rider stayed there.
        //
        // Display rotation rather than raw accelerometer tilt is the right input: it is what the
        // UI is actually drawn at, so it already accounts for the user's rotation lock and for a
        // tablet whose natural orientation is landscape to begin with.
        val matrix = remapForDisplay()

        SensorManager.getOrientation(matrix, orientation)
        val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        _heading.value = (degrees + 360f) % 360f
    }

    /**
     * Rotates the sensor frame into the displayed frame, returning the matrix to read the azimuth
     * from. Re-read per event rather than cached: rotation can change at any moment, the value is
     * a cheap local field read, and this removes any chance of the cache and the screen disagreeing
     * across a configuration change.
     */
    private fun remapForDisplay(): FloatArray {
        val (axisX, axisY) = when (display?.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            // ROTATION_0, and the null display we can do nothing about, are already the frame
            // getOrientation() wants — remapping them would be an identity transform.
            else -> return rotationMatrix
        }
        return if (SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) {
            remappedMatrix
        } else {
            rotationMatrix
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
