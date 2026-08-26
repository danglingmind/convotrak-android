package com.danglingmind.convotrack.data.location

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared handle for the rider's own live location. The foreground [RideLocationService] pushes
 * fixes in via [update]; the UI reads [current] for the map camera, HUD, and regroup coordinates.
 * The Android analogue of iOS LocationService's published state.
 */
class LocationController {

    private val _current = MutableStateFlow<Location?>(null)
    val current: StateFlow<Location?> = _current

    // Compass heading, used only when the GPS-derived bearing has gone stale (rider stopped).
    private var compass: CompassProvider? = null
    private val _compassHeading = MutableStateFlow<Float?>(null)
    val compassHeading: StateFlow<Float?> = _compassHeading

    // True while a ride's foreground location service is running. Presence logic uses this to
    // keep the socket alive in the background during an active ride (vs. dropping it in a lobby).
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun update(location: Location) { _current.value = location }

    /** Starts broadcasting position for a ride (foreground service). Caller must hold FINE_LOCATION. */
    fun start(context: Context, rideId: String) {
        val intent = Intent(context, RideLocationService::class.java)
            .putExtra(RideLocationService.EXTRA_RIDE_ID, rideId)
        ContextCompat.startForegroundService(context, intent)
        _active.value = true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, RideLocationService::class.java))
        _current.value = null
        _active.value = false
    }

    /**
     * Begins compass updates. Separate from [start] because the compass is a UI concern (the
     * heading of a stopped rider's arrow) with no bearing on what gets broadcast, and it should
     * stop the moment navigation leaves the screen rather than living as long as the ride.
     */
    fun startCompass(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
        if (compass != null) return
        val provider = CompassProvider(context)
        compass = provider
        provider.start()
        scope.launch { provider.heading.collect { _compassHeading.value = it } }
    }

    fun stopCompass() {
        compass?.stop()
        compass = null
        _compassHeading.value = null
    }
}
