package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos

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

/** Route coordinates paired with their turn-by-turn steps (for the navigation banner). */
data class RouteWithSteps(val coords: List<LatLng>, val steps: List<DirectionsStep>)

/**
 * Like [computeRoute] but also returns the stitched turn-by-turn steps across all legs —
 * used by the navigation screen to drive the maneuver banner (iOS calculateRoute parity).
 */
suspend fun computeRouteWithSteps(waypoints: List<Waypoint>): RouteWithSteps {
    val sorted = waypoints.sortedBy { it.order }
    if (sorted.size < 2) return RouteWithSteps(emptyList(), emptyList())
    val coords = mutableListOf<LatLng>()
    val steps = mutableListOf<DirectionsStep>()
    for (i in 0 until sorted.size - 1) {
        val origin = LatLng(sorted[i].lat, sorted[i].lng)
        val dest = LatLng(sorted[i + 1].lat, sorted[i + 1].lng)
        val result = runCatching { DirectionsService.route(origin, dest) }.getOrNull() ?: continue
        coords += if (i == 0) result.coordinates else result.coordinates.drop(1)
        steps += result.steps
    }
    return RouteWithSteps(coords, steps)
}

/** Start / waypoint / destination markers for a ride, labelled with their names. */
fun rideMarkers(ride: Ride): List<MapMarker> = buildList {
    val sorted = ride.waypoints.sortedBy { it.order }
    sorted.forEach { wp ->
        val kind = when (wp.type) {
            "START" -> MapMarkerKind.START
            "DESTINATION" -> MapMarkerKind.DESTINATION
            else -> MapMarkerKind.WAYPOINT
        }
        add(MapMarker("wp_${wp.order}", LatLng(wp.lat, wp.lng), kind, wp.name))
    }
    if (sorted.none { it.type == "DESTINATION" }) {
        add(MapMarker("dest", LatLng(ride.destinationLat, ride.destinationLng), MapMarkerKind.DESTINATION, ride.destinationName))
    }
}

/**
 * Trims [route] so it begins at the user's projected position on the nearest segment — the
 * bright active route then starts exactly at the rider (iOS trimActiveRoute).
 */
fun trimRouteToUser(route: List<LatLng>, user: LatLng): List<LatLng> {
    if (route.size < 2) return route
    var bestIdx = 0
    var bestDist = Double.MAX_VALUE
    var bestProj = route.first()
    for (i in 0 until route.size - 1) {
        val (proj, dist) = projectOntoSegment(user, route[i], route[i + 1])
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = i
            bestProj = proj
        }
    }
    return buildList {
        add(bestProj)
        addAll(route.subList(bestIdx + 1, route.size))
    }
}

/** Projects [p] onto segment a→b in a flat-earth frame; returns the projected point + distance². */
private fun projectOntoSegment(p: LatLng, a: LatLng, b: LatLng): Pair<LatLng, Double> {
    val cosLat = cos(a.latitude * Math.PI / 180)
    val ax = a.longitude * cosLat; val ay = a.latitude
    val bx = b.longitude * cosLat; val by = b.latitude
    val px = p.longitude * cosLat; val py = p.latitude
    val dx = bx - ax; val dy = by - ay
    val lenSq = dx * dx + dy * dy
    val proj = if (lenSq < 1e-18) {
        a
    } else {
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        LatLng(a.latitude + tc * (b.latitude - a.latitude), a.longitude + tc * (b.longitude - a.longitude))
    }
    val ex = proj.longitude * cosLat - px; val ey = proj.latitude - py
    return proj to (ex * ex + ey * ey)
}
