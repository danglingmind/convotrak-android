package com.danglingmind.convotrack.data.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot of the live ride, published by the navigation screen and rendered by
 * [RideLocationService] into its ongoing notification (the Android counterpart of the iOS Live
 * Activity). Text is pre-formatted by the publisher so the service stays free of UI code.
 */
data class RideStatus(
    val rideId: String,
    /** Routes API maneuver enum ("TURN_LEFT", ...), "" when unknown. Picks the glyph. */
    val maneuverKey: String,
    /** Distance to the next turn as shown in the banner ("350 m"), "" when none. */
    val turnDistanceText: String,
    val instruction: String,
    /** "10:42 AM" */
    val arrivalText: String,
    /** Arrival without am/pm ("10:42"), short enough for the Live Update status chip. */
    val arrivalShort: String,
    /** "42 min left" */
    val timeLeftText: String,
    /** "Last rider -2.4 km", "" when riding alone. */
    val convoyGapText: String,
    /** Reporter's display name while an emergency is open ("You" for the rider's own). */
    val emergencyName: String?,
    val destinationName: String,
    /** Route covered so far, 0..1. */
    val progress: Float,
)

/** Process-wide holder: the screen writes, the foreground service reads. */
object RideStatusNotifier {
    private val _status = MutableStateFlow<RideStatus?>(null)
    val status: StateFlow<RideStatus?> = _status

    fun publish(status: RideStatus) { _status.value = status }
    fun clear() { _status.value = null }
}
