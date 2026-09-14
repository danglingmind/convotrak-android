package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.data.maps.DirectionsResult
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.google.android.gms.maps.model.LatLng

/**
 * Ride → map geometry. The pure route math lives in `NavRouteEngine.kt`; this file is about
 * turning a [Ride] into something drawable, and about the Directions calls that back it.
 */

/**
 * Stitches Directions routes between consecutive waypoints into one polyline. Returns empty if
 * there aren't at least two stops, or if any leg fails to route — see [computeRouteWithSteps] for
 * why a partial answer is worse than none.
 */
suspend fun computeRoute(waypoints: List<Waypoint>): List<LatLng> =
    computeRouteWithSteps(waypoints).coords

/**
 * The route geometry to draw for a ride: the leader-selected [Ride.routePolyline] if present,
 * otherwise a freshly stitched route from the waypoints. Preferring the stored polyline is what
 * keeps every rider's lobby/home/navigation map showing the *same* line the leader chose.
 */
suspend fun rideRoute(ride: Ride): List<LatLng> =
    DirectionsService.decodedRoute(ride.routePolyline) ?: computeRoute(ride.waypoints)

/** Route coordinates paired with their turn-by-turn steps + total drive time (for the ETA). */
data class RouteWithSteps(
    val coords: List<LatLng>,
    val steps: List<DirectionsStep>,
    val durationSeconds: Double = 0.0,
    /** Running travel time on arrival at each stop, in route order — one entry per leg. Used to
     *  target the ETA at the NEXT stop on a multi-stop ride rather than the final destination. */
    val cumulativeStopSeconds: List<Double> = emptyList(),
)

/**
 * Navigation's route: the geometry to follow, plus the turn instructions that genuinely belong to
 * it.
 *
 * A ride stores the leader's pick as a bare polyline and nothing else — no instructions. The
 * leader may well have chosen an alternative rather than Google's default, and a plain recompute
 * always asks for the default. Pairing the two blindly (which is what Android did) describes one
 * road with another road's turns: stamping those step ends onto this polyline projects each turn
 * onto whatever point of the leader's route happens to lie nearest, producing instructions for
 * roads the rider is never on, at distances that mean nothing.
 *
 * A recompute can also drift on its own: traffic moves Google's first choice between the moment a
 * ride is created and the moment it starts.
 *
 * So: recompute, and if the result traces the stored shape, use it. If it doesn't, ask for the
 * same alternative set the leader chose from and take the steps of the one that matches. If none
 * matches — the stored polyline may predate a road change, or have been trimmed — then navigating
 * the route we DO have instructions for beats reciting another road's turns over this one; the
 * leader's choice stays on screen as the dim planned layer, so the divergence is visible rather
 * than silent.
 */
suspend fun rideRouteWithSteps(ride: Ride): RouteWithSteps {
    val sorted = ride.waypoints.sortedBy { it.order }
    val computed = computeRouteWithSteps(ride.waypoints)
    val stored = DirectionsService.decodedRoute(ride.routePolyline)
        ?: return computed

    if (computed.coords.isEmpty()) return computed.copy(coords = stored)

    if (routeDeviation(computed.coords, stored) <= ROUTE_MATCH_TOLERANCE_METERS) {
        // Same roads — draw the leader's exact geometry, keep the recompute's instructions.
        return computed.copy(coords = stored)
    }

    // Only meaningful on a single-leg ride: Google returns no alternatives once intermediate
    // waypoints are present, which is also the only case the create/edit screens offer a choice in.
    val matched: DirectionsResult? = if (sorted.size == 2) {
        alternativeMatching(
            stored,
            LatLng(sorted[0].lat, sorted[0].lng),
            LatLng(sorted[1].lat, sorted[1].lng),
        )
    } else {
        null
    }

    return if (matched != null) {
        RouteWithSteps(
            coords = stored,
            steps = matched.steps,
            durationSeconds = matched.durationSeconds,
            cumulativeStopSeconds = listOf(matched.durationSeconds),
        )
    } else {
        // No instruction set could be matched to the line the leader drew — always the case on a
        // multi-stop ride, where there is no alternative set to search. Drop the TURNS, never the
        // geometry.
        //
        // The backend measures every rider against the STORED polyline: `progressEngine`
        // derives both `progress` and `offRoute` from `ride.route_polyline`. Following the
        // recompute instead put the client in disagreement with the server about where everyone
        // is — riders dead on the drawn line reported off-route, ranked off a progress figure
        // measured on a different road, and charged the gap as `detourMeters` in the summary.
        //
        // With no steps the banner falls back to "Follow route" (upcomingBanner returns empty for
        // an empty list, and updateCurrentStep early-returns). Reciting no turns beats reciting
        // another road's turns, and both beat silently disagreeing with the server.
        //
        // `durationSeconds` stays the recompute's: it is only the bootstrap ETA until the first
        // traffic-aware refresh lands, and both roads run between the same stops.
        computed.copy(coords = stored, steps = emptyList())
    }
}

/**
 * The alternative for [origin]→[destination] that traces [stored], or null when none of them does.
 *
 * Null is a real answer, not just a failure: guessing an alternative would put the wrong turns on
 * screen — the exact failure this lookup exists to prevent.
 */
private suspend fun alternativeMatching(
    stored: List<LatLng>,
    origin: LatLng,
    destination: LatLng,
): DirectionsResult? {
    // Traffic-aware FIRST, because that is the set the leader actually chose from (the create and
    // edit screens both request alternatives traffic-aware). But Google's alternative set varies
    // with traffic and time of day, so hours after a ride was created the leader's pick may simply
    // not be offered any more — hence the traffic-unaware second pass, which asks the
    // time-invariant road-network set. Strictly more matches than either alone; the extra call is
    // only ever made on a ride that would otherwise lose turn guidance entirely.
    for (trafficAware in listOf(true, false)) {
        val options = runCatching {
            DirectionsService.alternativeRoutesWithSteps(origin, destination, trafficAware)
        }.getOrNull() ?: continue

        options
            .filter { it.steps.isNotEmpty() }
            .map { it to routeDeviation(it.coordinates, stored) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= ROUTE_MATCH_TOLERANCE_METERS }
            ?.let { return it.first }
    }
    return null
}

/**
 * Like [computeRoute] but also returns the stitched turn-by-turn steps, the summed drive time, and
 * the running time on arrival at each stop.
 *
 * All-or-nothing: `cumulativeStopSeconds` gets one entry per leg and stays index-aligned with the
 * remaining-stops list the ETA reads, because a leg that fails aborts the whole computation rather
 * than leaving a hole in either.
 */
suspend fun computeRouteWithSteps(waypoints: List<Waypoint>): RouteWithSteps {
    val sorted = waypoints.sortedBy { it.order }
    if (sorted.size < 2) return RouteWithSteps(emptyList(), emptyList())
    val coords = mutableListOf<LatLng>()
    val steps = mutableListOf<DirectionsStep>()
    val cumulativeStopSeconds = mutableListOf<Double>()
    var totalTime = 0.0
    for (i in 0 until sorted.size - 1) {
        val origin = LatLng(sorted[i].lat, sorted[i].lng)
        val dest = LatLng(sorted[i + 1].lat, sorted[i + 1].lng)
        // A skipped leg is not a degraded route, it is a WRONG one: the polyline then runs
        // dead-straight from one stop to the next, across whatever happens to be in between, and
        // that geometry goes on to back progress, off-route detection and step stamping. Failing
        // the whole computation instead lets the caller fall back to the stored polyline, which is
        // also the geometry the server measures everyone against.
        val result = routeLegOrNull(origin, dest, trafficAware = true)
            ?: return RouteWithSteps(emptyList(), emptyList())
        // Drop the first point of later legs to avoid duplicating the shared vertex.
        coords += if (i == 0) result.coordinates else result.coordinates.drop(1)
        steps += result.steps
        totalTime += result.durationSeconds
        cumulativeStopSeconds += totalTime
    }
    return RouteWithSteps(coords, steps, totalTime, cumulativeStopSeconds)
}

/**
 * One Directions leg, retried once before giving up.
 *
 * The retry is what makes failing closed affordable: most leg failures are a transient 429 or a
 * dropped connection on a moving bike, and without a second attempt a single blip would discard an
 * otherwise good reroute.
 */
private suspend fun routeLegOrNull(
    origin: LatLng,
    destination: LatLng,
    trafficAware: Boolean,
    originHeading: Double? = null,
): DirectionsResult? {
    repeat(2) { attempt ->
        runCatching { DirectionsService.route(origin, destination, trafficAware, originHeading) }
            .getOrNull()
            ?.let { return it }
        if (attempt == 0) kotlinx.coroutines.delay(300)
    }
    return null
}

/** The remaining stops a rider must still pass through: waypoints + destination, in order. */
fun rideStops(ride: Ride): List<LatLng> {
    val sorted = ride.waypoints.sortedBy { it.order }
    val stops = sorted.filter { it.type == "WAYPOINT" || it.type == "DESTINATION" }
        .map { LatLng(it.lat, it.lng) }
    return stops.ifEmpty { listOf(LatLng(ride.destinationLat, ride.destinationLng)) }
}

/**
 * Recalculates the full remaining route from [origin] through all [stops] to the destination — the
 * off-route reroute.
 *
 * [originHeading] is the rider's direction of travel, passed through to the Routes API so the
 * reroute starts on the side of the road they are actually riding on — the difference between a
 * reroute that opens with a U-turn and one that assumes they can set off back the way they came.
 *
 * Returns the reroute's OWN turn instructions alongside the geometry. Discarding them (which is
 * what Android did) leaves the banner reciting maneuvers from a route the rider has already left —
 * "turn left" at a junction that is no longer on the path.
 */
suspend fun calculateFullReroute(
    origin: LatLng,
    stops: List<LatLng>,
    originHeading: Double? = null,
): RouteWithSteps {
    if (stops.isEmpty()) return RouteWithSteps(emptyList(), emptyList())
    val chain = listOf(origin) + stops
    val coords = mutableListOf<LatLng>()
    val steps = mutableListOf<DirectionsStep>()
    var totalTime = 0.0
    for (i in 0 until chain.size - 1) {
        // Every leg or none. Skipping one used to splice a dead-straight line between two stops
        // into the followed route; skipping the FIRST one was worse still, because the reroute
        // then no longer began at the rider — navigation adopted a polyline starting at some stop
        // down the road, reset progress to its head, immediately declared the rider off-route, and
        // re-rerouted every 200 m for the rest of the ride. An abandoned reroute simply leaves the
        // previous route in place and lets the next fix try again.
        // The heading applies to the FIRST leg only — it describes where the rider is pointed now,
        // and every later leg starts from a stop they have yet to reach.
        val result = routeLegOrNull(
            chain[i], chain[i + 1],
            trafficAware = false,
            originHeading = if (i == 0) originHeading else null,
        ) ?: return RouteWithSteps(emptyList(), emptyList())
        coords += if (i == 0) result.coordinates else result.coordinates.drop(1)
        steps += result.steps
        totalTime += result.durationSeconds
    }
    return RouteWithSteps(coords, steps, totalTime)
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

/** Ordered stop coordinates (start → waypoints → destination), for route end-caps and connectors. */
fun rideStopCoords(ride: Ride): List<LatLng> =
    ride.waypoints.sortedBy { it.order }.map { LatLng(it.lat, it.lng) }
