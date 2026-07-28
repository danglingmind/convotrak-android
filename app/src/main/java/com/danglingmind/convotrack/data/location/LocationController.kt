package com.danglingmind.convotrack.data.location

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared handle for the rider's own live location. The foreground [RideLocationService] pushes
 * fixes in via [update]; the UI reads [current] for the map camera, HUD, and regroup coordinates.
 * The Android analogue of iOS LocationService's published state.
 */
class LocationController {

    private val _current = MutableStateFlow<Location?>(null)
    val current: StateFlow<Location?> = _current

    fun update(location: Location) { _current.value = location }

    /** Starts broadcasting position for a ride (foreground service). Caller must hold FINE_LOCATION. */
    fun start(context: Context, rideId: String) {
        val intent = Intent(context, RideLocationService::class.java)
            .putExtra(RideLocationService.EXTRA_RIDE_ID, rideId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, RideLocationService::class.java))
        _current.value = null
    }
}
