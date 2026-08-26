package com.danglingmind.convotrack.ui.ride

import android.location.Location
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure route math for live navigation — the Kotlin port of the geometry half of iOS
 * `NavigationViewModel`. Nothing here touches Compose, the socket, or the map; everything is a
 * function of a polyline, a set of steps and a position, so it can be reasoned about (and tested)
 * on its own.
 *
 * The organising idea, and the thing the old Android implementation lacked: **every decision is
 * made in terms of distance ALONG the route**, not straight-line proximity to a point. Proximity
 * triggers are one-shot — miss the window once and they can never fire again, because the distance
 * only grows as the rider drives away. Along-route progress is a pure function of position, so it
 * self-corrects: driving past a junction advances the step by itself, and several steps can be
 * crossed in a single GPS fix.
 */

// MARK: - Distances and projection

/** Metres between two coordinates (great-circle, via the platform's own geodesic). */
fun metersBetween(a: LatLng, b: LatLng): Double {
    val out = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
    return out[0].toDouble()
}

/**
 * A point projected onto the segment a→b in a local flat-earth frame.
 *
 * [t] matters to callers as much as [point] does: `t == 1` means the projection ran off the far
 * end, i.e. the rider is already PAST this segment — which is how [advanceRouteProgress] tells
 * "sitting on this segment" apart from "near its end, heading away".
 *
 * One implementation for all three former copies of this math (progress tracking, route trimming,
 * off-route distance), so they cannot disagree about where the rider is.
 */
data class SegmentHit(val point: LatLng, val t: Double, val distanceMeters: Double)

fun segmentProjection(p: LatLng, a: LatLng, b: LatLng): SegmentHit {
    val cosLat = cos(a.latitude * Math.PI / 180)
    val ax = a.longitude * cosLat; val ay = a.latitude
    val bx = b.longitude * cosLat; val by = b.latitude
    val px = p.longitude * cosLat; val py = p.latitude
    val dx = bx - ax; val dy = by - ay
    val lenSq = dx * dx + dy * dy
    val t = if (lenSq < 1e-18) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
    val foot = LatLng(a.latitude + t * (b.latitude - a.latitude), a.longitude + t * (b.longitude - a.longitude))
    return SegmentHit(foot, t, metersBetween(p, foot))
}

/** Perpendicular distance in metres from [p] to segment a→b, clamped to the segment ends. */
fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double =
    segmentProjection(p, a, b).distanceMeters

/** Prefix distances: `result[i]` is metres from the route start to vertex `i`. */
fun cumulativeDistances(route: List<LatLng>): DoubleArray {
    if (route.isEmpty()) return DoubleArray(0)
    val out = DoubleArray(route.size)
    for (i in 1 until route.size) out[i] = out[i - 1] + metersBetween(route[i - 1], route[i])
    return out
}

/** Total great-circle length of a polyline, in metres. */
fun routeLengthMeters(route: List<LatLng>): Double {
    if (route.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until route.size - 1) total += metersBetween(route[i], route[i + 1])
    return total
}

/**
 * Nearest point on [route] to [point], projected onto the closest SEGMENT (not just the nearest
 * vertex). Returns the segment's start index and the projected coordinate.
 *
 * [from] bounds the search to segments at or after that index — see the monotone stamping in
 * [stampAlongRouteDistances].
 */
data class RouteProjection(val segmentStart: Int, val projected: LatLng)

fun nearestPointOnRoute(route: List<LatLng>, point: LatLng, from: Int = 0): RouteProjection {
    val first = from.coerceIn(0, maxOf(0, route.size - 2))
    var bestIdx = first
    var bestProj = route[first]
    var bestDist = Double.MAX_VALUE
    for (i in first until route.size - 1) {
        val hit = segmentProjection(point, route[i], route[i + 1])
        if (hit.distanceMeters < bestDist) {
            bestDist = hit.distanceMeters
            bestIdx = i
            bestProj = hit.point
        }
    }
    return RouteProjection(bestIdx, bestProj)
}

/**
 * Distance still to travel along [route] from wherever the rider currently is on it.
 *
 * Measuring the polyline's TOTAL length only works while the line is being re-cut to start at the
 * rider — and that is deliberately skipped while off-route, so the bright line keeps showing the
 * reroute being followed. The side effect was that DIST froze off-route while ETA kept counting
 * down. Projecting the rider onto the line and summing what is left keeps the two consistent in
 * both states, and costs one projection per fix.
 */
fun remainingRouteLength(route: List<LatLng>, from: LatLng): Double {
    if (route.size < 2) return 0.0
    val (segIdx, projected) = nearestPointOnRoute(route, from)
    val remaining = ArrayList<LatLng>(route.size - segIdx)
    remaining.add(projected)
    if (segIdx + 1 < route.size) remaining.addAll(route.subList(segIdx + 1, route.size))
    return routeLengthMeters(remaining)
}

/**
 * Walks [route] forward from [projected] (a point on segment [segmentStart]), accumulating segment
 * lengths, and returns the point [meters] further on; clamps to the route's end.
 */
fun pointAlong(route: List<LatLng>, projected: LatLng, segmentStart: Int, meters: Double): LatLng? {
    if (route.size < 2) return null
    var remaining = meters
    var prev = projected
    var idx = min(segmentStart + 1, route.size - 1)
    while (idx < route.size) {
        val seg = metersBetween(prev, route[idx])
        if (seg >= remaining) {
            val frac = if (seg > 0) remaining / seg else 0.0
            return LatLng(
                prev.latitude + (route[idx].latitude - prev.latitude) * frac,
                prev.longitude + (route[idx].longitude - prev.longitude) * frac,
            )
        }
        remaining -= seg
        prev = route[idx]
        idx++
    }
    return route.lastOrNull()
}

/** Initial great-circle bearing from [a] to [b], degrees clockwise from true north — the same
 *  convention as `Location.bearing` and `Marker.rotation`. */
fun bearingBetween(a: LatLng, b: LatLng): Double {
    val lat1 = a.latitude * Math.PI / 180
    val lat2 = b.latitude * Math.PI / 180
    val dLon = (b.longitude - a.longitude) * Math.PI / 180
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (atan2(y, x) * 180 / Math.PI + 360) % 360
}

/** Great-circle point [meters] from [origin] along [bearingDegrees]. Used only when no route
 *  geometry is available, so a distance-ahead regroup still lands ahead of the rider. */
fun coordinateFrom(origin: LatLng, bearingDegrees: Double, meters: Double): LatLng {
    val earthRadius = 6_371_000.0
    val angular = meters / earthRadius
    val bearing = (if (bearingDegrees.isFinite()) bearingDegrees else 0.0) * Math.PI / 180
    val lat1 = origin.latitude * Math.PI / 180
    val lon1 = origin.longitude * Math.PI / 180
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angular) * cos(lat1),
        cos(angular) - sin(lat1) * sin(lat2),
    )
    return LatLng(lat2 * 180 / Math.PI, lon2 * 180 / Math.PI)
}

/** Smallest absolute difference between two compass bearings, in degrees (0..180). */
fun headingDelta(a: Double, b: Double): Double {
    val raw = abs(a - b) % 360.0
    return min(raw, 360.0 - raw)
}

// MARK: - Step stamping

/**
 * Stamps each step with where its end falls along [route], by projecting the end coordinate onto
 * the polyline.
 *
 * Two invariants, both of which the naive version gets wrong:
 *
 * 1. **Forward-only search.** A whole-route scan puts each step end on whichever pass of a route
 *    that comes back on itself — a loop, an out-and-back, a cloverleaf, a pair of parallel
 *    carriageways — happens to lie nearest, which can land a late turn behind an early one. Steps
 *    are ordered along the route by definition, so step k's match cannot precede step k−1's.
 * 2. **Non-decreasing clamp.** A projection that still landed behind its predecessor would make
 *    that step unreachable and strand the banner, which is the failure mode this whole path exists
 *    to avoid.
 *
 * The last step ends at the destination by definition; snapping it there stops a slightly short
 * projection from leaving the final instruction unreachable.
 */
fun stampAlongRouteDistances(
    steps: List<DirectionsStep>,
    route: List<LatLng>,
    cumulative: DoubleArray,
): List<DirectionsStep> {
    if (route.size < 2 || cumulative.size != route.size) return steps
    val total = cumulative[cumulative.size - 1]
    val stamped = ArrayList<DirectionsStep>(steps.size)
    var previous = 0.0
    var searchFrom = 0
    for (step in steps) {
        val (segIdx, projected) = nearestPointOnRoute(route, step.end, searchFrom)
        val along = cumulative[segIdx] + metersBetween(route[segIdx], projected)
        val clamped = along.coerceIn(previous, total)
        previous = clamped
        searchFrom = segIdx
        stamped.add(step.copy(endDistanceAlongRoute = clamped))
    }
    if (stamped.isNotEmpty()) {
        stamped[stamped.size - 1] = stamped[stamped.size - 1].copy(endDistanceAlongRoute = total)
    }
    return stamped
}

// MARK: - The banner

/** What the turn banner should say, and how far along the route the maneuver is performed. */
data class TurnBanner(
    val instruction: String,
    val maneuver: String,
    val junctionDistanceAlongRoute: Double,
)

/**
 * The maneuver to announce next, and where it happens.
 *
 * A Routes API step's `navigationInstruction` describes the maneuver taken to ENTER that step, not
 * one taken at its end: "Turn right onto Ring Rd" belongs to the step that runs ALONG Ring Rd, and
 * the turn itself sits at that step's START — which is the END of the step before it.
 *
 * Showing the CURRENT step's own instruction therefore announces the turn the rider has already
 * made, while pairing it with the distance to the NEXT junction: the banner reads "Head north on
 * MG Rd — 400 m" exactly where Google Maps reads "Turn right onto Ring Rd — 400 m". Every
 * instruction on the ride is one maneuver behind. That is precisely what the old Android
 * implementation did.
 *
 * So the text comes from the step AFTER the current one, and the distance from the end of the step
 * immediately before that text's step — the junction where it is actually performed. The two are
 * returned together because steps with no instruction text still occupy real distance: skipping
 * forward over them moves the junction too, and reading the distance off `currentIndex`
 * independently would re-introduce the same mispairing at a smaller scale.
 */
fun upcomingBanner(steps: List<DirectionsStep>, currentIndex: Int): TurnBanner {
    val last = steps.lastOrNull() ?: return TurnBanner("", "", 0.0)
    if (currentIndex + 1 < steps.size) {
        for (j in (currentIndex + 1) until steps.size) {
            if (steps[j].instruction.isNotEmpty()) {
                return TurnBanner(
                    instruction = steps[j].instruction,
                    maneuver = steps[j].maneuver.orEmpty(),
                    junctionDistanceAlongRoute = steps[j - 1].endDistanceAlongRoute,
                )
            }
        }
    }
    // No maneuver left ahead — what remains is the arrival. The last step ends at the destination,
    // so its along-route end is genuinely the distance still to ride.
    return TurnBanner("Arrive at destination", "ARRIVE", last.endDistanceAlongRoute)
}

/**
 * Distance-to-maneuver, rounded the way Google Maps rounds it.
 *
 * A raw `%.0f m` reports every metre, so the banner ticks through "347 m", "339 m", "331 m" —
 * three digits of churn implying a precision GPS does not have, and nothing like the
 * "350 m → 300 m → 250 m" cadence beside it on Google. Coarser as the number grows: 10 m steps up
 * close where the rounding is a small share of the distance, 50 m out to a kilometre, tenths of a
 * km beyond. Under 10 m the maneuver is happening, so it is named rather than measured.
 */
fun turnDistanceText(meters: Double): String {
    if (meters <= 0) return ""
    if (meters < 10) return "Now"
    if (meters < 500) return "${(Math.round(meters / 10) * 10)} m"
    val toFifty = Math.round(meters / 50) * 50
    if (toFifty < 1000) return "$toFifty m"   // 975 m rounds to 1000, which belongs in km
    return String.format(java.util.Locale.US, "%.1f km", meters / 1000)
}

// MARK: - Route matching

/** How far apart two routes can sit and still count as the same roads. Comfortably above the few
 *  metres two responses for one road differ by (HIGH_QUALITY vertices, lane snapping,
 *  traffic-shifted geometry), and far below the separation of two genuinely different
 *  alternatives, which part company by whole blocks. */
const val ROUTE_MATCH_TOLERANCE_METERS = 60.0

/**
 * How closely [candidate] traces [reference]: the 90th-percentile distance, in metres, from points
 * sampled evenly along [candidate] to the nearest point on [reference].
 *
 * A percentile rather than the maximum, so one clipped corner or a differently-modelled junction
 * approach can't condemn an otherwise identical route.
 *
 * Runs its own flat-earth arithmetic rather than reusing [nearestPointOnRoute]: this compares every
 * sample against every segment of the reference — 64 × several thousand on a long ride — and that
 * helper takes a geodesic distance per segment. Half a million of those at the moment navigation
 * opens is a visible stall, to decide a question whose answer is "same road or a different one".
 * Metres here are equirectangular about the route's mid-latitude: good to a fraction of a percent
 * over a ride's span, against a 60 m tolerance.
 */
fun routeDeviation(candidate: List<LatLng>, reference: List<LatLng>): Double {
    if (candidate.size < 2 || reference.size < 2) return Double.MAX_VALUE

    val anchor = reference[reference.size / 2]
    val metresPerLat = 111_132.0
    val metresPerLng = 111_320.0 * cos(anchor.latitude * Math.PI / 180)
    fun projX(c: LatLng) = (c.longitude - anchor.longitude) * metresPerLng
    fun projY(c: LatLng) = (c.latitude - anchor.latitude) * metresPerLat

    val refX = DoubleArray(reference.size) { projX(reference[it]) }
    val refY = DoubleArray(reference.size) { projY(reference[it]) }

    val sampleCount = 64
    val deviations = DoubleArray(sampleCount)
    for (k in 0 until sampleCount) {
        val idx = ((k.toDouble() / (sampleCount - 1)) * (candidate.size - 1)).toInt()
        val px = projX(candidate[idx]); val py = projY(candidate[idx])
        var bestSquared = Double.MAX_VALUE
        for (i in 0 until reference.size - 1) {
            val ax = refX[i]; val ay = refY[i]
            val dx = refX[i + 1] - ax; val dy = refY[i + 1] - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq < 1e-9) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
            val ex = px - (ax + t * dx); val ey = py - (ay + t * dy)
            val squared = ex * ex + ey * ey
            if (squared < bestSquared) bestSquared = squared
        }
        deviations[k] = sqrt(bestSquared)
    }
    deviations.sort()
    return deviations[((deviations.size - 1) * 0.9).toInt()]
}

// MARK: - Progress along the followed route

/**
 * Advance the progress index to the closest upcoming segment, never backwards.
 *
 * The early break tests that the rider is sitting ON this segment (`t < 0.999`) — not merely close
 * to its far endpoint. A plain `distance < 5` proximity break stalls on dense curve geometry
 * (Google polylines run 2–3 m per vertex through a bend): the very first segment tried, the one
 * already behind the rider, keeps satisfying it and the index never advances. The polyline head is
 * then clamped to a vertex the rider has already driven through, so the bright line's start stalls
 * while the chevron carries on, then jumps to catch up — the disjointed, uneven-pace head this
 * search feeds.
 */
fun advanceRouteProgress(route: List<LatLng>, from: LatLng, segmentIndex: Int): Int {
    if (route.size < 2) return segmentIndex
    val lookEnd = min(route.size - 2, segmentIndex + 200)
    if (lookEnd < segmentIndex) return segmentIndex
    var minDist = Double.MAX_VALUE
    var bestIdx = segmentIndex
    for (i in segmentIndex..lookEnd) {
        val hit = segmentProjection(from, route[i], route[i + 1])
        if (hit.distanceMeters < minDist) { minDist = hit.distanceMeters; bestIdx = i }
        if (hit.distanceMeters < 5 && hit.t < 0.999) break
    }
    // Only advance if the rider is actually near the route — prevents a far-away GPS fix from
    // jumping the index to the end of a short route (the old straight-line bug).
    return if (bestIdx > segmentIndex && minDist <= 80) bestIdx else segmentIndex
}

/**
 * The rider's distance along [route], measured from [segmentIndex].
 *
 * Built on the forward-only index rather than a global nearest-point search, so progress cannot
 * jump backwards (or leap ahead) where a route passes close to itself.
 */
fun progressAlongRoute(route: List<LatLng>, cumulative: DoubleArray, from: LatLng, segmentIndex: Int): Double {
    if (route.size < 2 || cumulative.size != route.size) return 0.0
    val i = min(segmentIndex, route.size - 2)
    val projected = segmentProjection(from, route[i], route[i + 1]).point
    return cumulative[i] + metersBetween(route[i], projected)
}

/** Minimum perpendicular distance to any segment in a window around the current progress index. */
fun minDistanceToUpcomingRoute(route: List<LatLng>, from: LatLng, segmentIndex: Int): Double {
    if (route.size < 2) return 0.0
    val start = maxOf(0, segmentIndex - 5)
    val end = min(route.size - 2, segmentIndex + 100)
    if (end < start) return 0.0
    var minDist = Double.MAX_VALUE
    for (i in start..end) {
        val d = distanceToSegmentMeters(from, route[i], route[i + 1])
        if (d < minDist) minDist = d
        if (minDist < 5) return minDist
    }
    return minDist
}

// MARK: - Trimming (marker and polyline head are the same point, by construction)

/** How far off the drawn route a fix may sit and still be snapped onto it. */
const val SNAP_MAX_OFFSET_METERS = 25.0

/** Look-ahead along the route used to derive the road's bearing for the rider marker. */
const val ROUTE_BEARING_LOOKAHEAD_METERS = 20.0

/**
 * The result of cutting the active route to start underneath the rider.
 *
 * [head] is BOTH the rider marker's position and the polyline's first vertex. They used to be
 * decided separately: the polyline always started at the projected foot of the perpendicular,
 * while the marker took that projection only when the fix sat within [SNAP_MAX_OFFSET_METERS] and
 * otherwise kept the raw GPS coordinate. Every fix that failed that test — routine on a wide road,
 * a flyover, or beside a service lane, where lane offset plus GPS error clears 25 m easily —
 * detached the marker from the line's head, and because the raw fix jitters while the snapped head
 * glides, the two visibly moved at different paces.
 *
 * When the fix is too far off the line to claim a snap, the marker honestly stays on the raw
 * coordinate and the polyline is drawn from there back onto the road: still one connected line
 * starting at the chevron, rather than two things floating apart.
 */
data class TrimResult(
    val head: LatLng,
    val trimmed: List<LatLng>,
    val snapped: Boolean,
    /** Bearing of the road ahead, or null when off the line (where the road ahead is not the
     *  direction the rider is going). */
    val roadBearing: Double?,
)

fun trimActiveRoute(
    route: List<LatLng>,
    from: LatLng,
    segmentIndex: Int,
    speedMps: Double,
    courseValidSpeedMps: Double,
): TrimResult? {
    if (route.size < 2) return null
    val i = min(segmentIndex, route.size - 2)
    val next = min(i + 1, route.size - 1)

    val hit = segmentProjection(from, route[i], route[next])
    // A wild fix inside an on-route run must not be yanked onto the line — the off-route flag needs
    // three consecutive readings to trip, so this bound is what stops us claiming precision we
    // don't have in between.
    val snapped = hit.distanceMeters <= SNAP_MAX_OFFSET_METERS
    val head = if (snapped) hit.point else from

    // Point along the ROAD, not along the course. A GPS-derived bearing weaves with the lane and
    // with noise — the arrow sat a few degrees askew on a line it was sitting exactly on. The
    // camera takes its bearing from this same value, so the map steadies with it.
    val roadBearing = if (snapped && speedMps >= courseValidSpeedMps) {
        routeBearingAhead(route, hit.point, segmentIndex)
    } else {
        null
    }

    val trimmed = ArrayList<LatLng>(route.size - next + 2)
    trimmed.add(head)
    if (!snapped) trimmed.add(hit.point)   // short leader from the marker back to the road
    if (next < route.size) trimmed.addAll(route.subList(next, route.size))
    return TrimResult(head, trimmed, snapped, roadBearing)
}

/**
 * Bearing of the road ahead: from [projected] to a point roughly [ROUTE_BEARING_LOOKAHEAD_METERS]
 * further along [route].
 *
 * Measured over a look-ahead rather than from the single segment under the rider because route
 * polylines are dense — a 2–3 m segment's bearing swings hard from vertex to vertex, which would
 * make the chevron (and the camera) twitch on every fix.
 */
fun routeBearingAhead(route: List<LatLng>, projected: LatLng, segmentIndex: Int): Double? {
    if (route.size < 2) return null
    var remaining = ROUTE_BEARING_LOOKAHEAD_METERS
    var cursor = projected
    var i = segmentIndex + 1
    while (i < route.size) {
        val next = route[i]
        val step = metersBetween(cursor, next)
        if (step >= remaining || i == route.size - 1) return bearingBetween(projected, next)
        remaining -= step
        cursor = next
        i++
    }
    return null
}
