package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.google.android.gms.maps.model.LatLng

/**
 * Stitches Directions routes between consecutive waypoints into one polyline — the Kotlin
 * port of iOS ActiveRideCard.computeRoute. Legs that fail are skipped so a bad segment
 * doesn't blank the whole route. Returns empty if there aren't at least two stops.
 */
suspend fun computeRoute(waypoints: List<Waypoint>): List<LatLng> {
    val sorted = waypoints.sortedBy { it.order }
    if (sorted.size < 2) return emptyList()
    val all = mutableListOf<LatLng>()
    for (i in 0 until sorted.size - 1) {
        val origin = LatLng(sorted[i].lat, sorted[i].lng)
        val dest = LatLng(sorted[i + 1].lat, sorted[i + 1].lng)
        val result = runCatching { DirectionsService.route(origin, dest) }.getOrNull() ?: continue
        // Drop the first point of later legs to avoid duplicating the shared vertex.
        all += if (i == 0) result.coordinates else result.coordinates.drop(1)
    }
    return all
}

/** Destination + middle-waypoint markers for a ride. */
fun rideMarkers(ride: Ride): List<MapMarker> {
    val markers = mutableListOf(
        MapMarker(
            id = "dest",
            position = LatLng(ride.destinationLat, ride.destinationLng),
            kind = MapMarkerKind.DESTINATION,
            label = ride.destinationName,
        ),
    )
    ride.waypoints
        .filter { it.type == "WAYPOINT" }
        .forEach { wp ->
            markers += MapMarker(
                id = "wp_${wp.order}",
                position = LatLng(wp.lat, wp.lng),
                kind = MapMarkerKind.WAYPOINT,
                label = wp.name,
            )
        }
    return markers
}
