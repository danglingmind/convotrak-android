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
 * end, i.e. the rider is already PAST this segment — which is how [locateOnRoute] tells "sitting
 * on this segment" apart from "near its end, heading away".
 *
 * One implementation for all four former copies of this math (progress tracking, route trimming,
 * off-route distance, remaining length), so they cannot disagree about where the rider is.
 */
data class SegmentHit(val point: LatLng, val t: Double, val distanceMeters: Double)

/** Metres per degree of latitude. Constant enough at any latitude for the spans measured here. */
private const val METERS_PER_DEG_LAT = 111_132.0

/** Metres per degree of longitude at the equator; scaled by cos(latitude) at the point of use. */
private const val METERS_PER_DEG_LNG = 111_320.0

fun segmentProjection(p: LatLng, a: LatLng, b: LatLng): SegmentHit {
    val cosLat = cos(a.latitude * Math.PI / 180)
    val ax = a.longitude * cosLat; val ay = a.latitude
    val bx = b.longitude * cosLat; val by = b.latitude
    val px = p.longitude * cosLat; val py = p.latitude
    val dx = bx - ax; val dy = by - ay
    val lenSq = dx * dx + dy * dy
    val t = if (lenSq < 1e-18) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
    val foot = LatLng(a.latitude + t * (b.latitude - a.latitude), a.longitude + t * (b.longitude - a.longitude))
    // Equirectangular rather than geodesic. This runs hundreds of times per GPS fix inside
    // [locateOnRoute], and `Location.distanceBetween` — an iterative Vincenty solve — is two
    // orders of magnitude more expensive than the arithmetic above it. Over the tens of metres a
    // perpendicular offset ever spans the two agree to well under a centimetre, which is noise
    // against a 40 m off-route threshold. It is also what made the old code afford only a
    // 200-segment window; the cheap distance is what lets the window be sized in metres instead.
    val dLat = (p.latitude - foot.latitude) * METERS_PER_DEG_LAT
    val dLng = (p.longitude - foot.longitude) * METERS_PER_DEG_LNG * cosLat
    return SegmentHit(foot, t, sqrt(dLat * dLat + dLng * dLng))
}

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
 * The fallback for distance-to-goal when there is no followed route to measure against — normally
 * that figure comes from the route's total length minus [progressAlongRoute], which reuses the
 * single per-fix [locateOnRoute] result and so cannot disagree with the drawn line.
 *
 * Measuring the polyline's TOTAL length would only work while the line is being re-cut to start at
 * the rider, and that is deliberately skipped while off-route so the bright line keeps showing the
 * route being followed; the side effect was that DIST froze off-route while ETA kept counting down.
 * Projecting the rider onto the line and summing what is left keeps the two consistent in both
 * states.
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

// MARK: - Direction of travel

/**
 * How far the rider's course may diverge from the direction the route RUNS before this counts as
 * driving the wrong way down it, in degrees.
 *
 * Well past a right angle, so nothing a rider does while genuinely following the route can trip
 * it: a hairpin is entered and left at a hundred-odd degrees to the road's mean bearing, and the
 * look-ahead [routeBearingAhead] measures over smooths those anyway. Only travel that is
 * substantially the reverse of the line clears 120 deg, and travel that is substantially the
 * reverse of the line is precisely what nothing else here can see.
 */
const val WRONG_WAY_HEADING_DELTA_DEGREES = 120.0

/** Consecutive wrong-way fixes before the followed route is treated as abandoned. Matches the
 *  lateral off-route debounce: three seconds of agreement, not one noisy bearing. */
const val WRONG_WAY_CONFIRM_COUNT = 3

/**
 * True when the rider is travelling AGAINST the direction [route] runs at [location].
 *
 * Every other test in this file measures distance, and distance alone cannot tell "riding along
 * the route" from "riding back down it". Ride the planned road in reverse — the U-turn still
 * kilometres ahead — and the perpendicular offset stays at a few metres for as long as you keep
 * doing it, which is well inside the off-route threshold. Detection therefore never fires, the
 * bright line is re-cut backwards fix by fix, and no reroute is ever requested; the route only
 * updates once the rider happens to stray far enough SIDEWAYS for the distance test to notice,
 * which is the "stale path until I reach a point where the reroute can be drawn" symptom.
 *
 * Compared against the road's bearing rather than the raw segment's, for the reason
 * [routeBearingAhead] exists: a 2-3 m segment's bearing swings hard from vertex to vertex, and a
 * single vertex pointing back at the rider must not be read as the rider pointing back at it.
 *
 * [courseDegrees] must be a course the caller has already vouched for (see COURSE_VALID_SPEED_MPS)
 * — a bearing derived from successive fixes is meaningless at a standstill, and reading noise as a
 * reversal would tear up the route of every rider waiting at a light.
 */
/**
 * How far, along the route, a U-turn's own junction still counts as being performed.
 *
 * The one place where riding against the line is exactly what the route asked for. A U-turn's two
 * legs run within metres of each other — on an undivided road, within nothing at all — so through
 * the turn the nearest segment can be either of them, and half of those readings put the road's
 * bearing 180 deg from the rider while they do precisely as instructed. Sixty metres either side of
 * the junction covers the approach, the apex and the exit at any speed a U-turn is taken at.
 */
const val UTURN_SUPPRESSION_METERS = 60.0

/**
 * True when [progressMeters] falls within [UTURN_SUPPRESSION_METERS] of a U-turn junction on
 * [steps].
 *
 * A step's maneuver is the one taken to ENTER it (see [upcomingBanner]), so the junction where a
 * U-turn is performed is the END of the step before it.
 */
fun isNearUTurn(steps: List<DirectionsStep>, progressMeters: Double): Boolean {
    for (j in 1 until steps.size) {
        if (steps[j].maneuver?.startsWith("UTURN") != true) continue
        if (abs(progressMeters - steps[j - 1].endDistanceAlongRoute) <= UTURN_SUPPRESSION_METERS) {
            return true
        }
    }
    return false
}

fun isAgainstRouteDirection(
    route: List<LatLng>,
    location: RouteLocation,
    courseDegrees: Double,
): Boolean {
    val road = routeBearingAhead(route, location.point, location.segmentIndex) ?: return false
    return headingDelta(courseDegrees, road) > WRONG_WAY_HEADING_DELTA_DEGREES
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
 * Projects the reference ONCE into a local frame rather than reusing [nearestPointOnRoute]: this
 * compares every sample against every segment of the reference — 64 × several thousand on a long
 * ride — so hoisting the per-vertex trigonometry out of the inner loop is worth the duplicated
 * arithmetic. Metres here are equirectangular about the route's mid-latitude: good to a fraction
 * of a percent over a ride's span, against a 60 m tolerance.
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

// MARK: - Locating the rider on the followed route

/**
 * How far AHEAD of the current progress index the per-fix search looks, in metres.
 *
 * Sized in metres rather than in vertices, which is what the old fixed 200-segment window got
 * wrong. Google's HIGH_QUALITY geometry runs 2-3 m per vertex through a bend and tens of metres
 * on an open highway, so 200 segments is anywhere between 500 m and 8 km depending on where the
 * rider happens to be. At the dense end that is less than twenty seconds of motorway driving: one
 * skipped fix — a tunnel, a doze window, the app coming back from the background — put the true
 * position outside the window, nothing inside it was within the acceptance bound, and the index
 * then froze for the REST OF THE RIDE. A metre-based window is the same amount of road everywhere.
 */
const val ROUTE_LOOKAHEAD_METERS = 3_000.0

/** How far BEHIND the index the search may reach, to absorb GPS jitter around a vertex. */
const val ROUTE_LOOKBEHIND_METERS = 60.0

/** Where the rider is on the route: the segment, the foot of the perpendicular, and the offset. */
data class RouteLocation(
    val segmentIndex: Int,
    val point: LatLng,
    /** Position along the segment, 0..1. `1` means the projection ran off the far end. */
    val t: Double,
    /** Perpendicular distance from the fix to [point], in metres. */
    val distanceMeters: Double,
)

/**
 * The single answer to "where is the rider on this route", computed once per GPS fix.
 *
 * Everything downstream — the off-route test, the along-route progress that drives the turn
 * banner, and above all the polyline trim that decides what is DRAWN — now reads this one result.
 * They used to run three different searches with three different windows: `advanceRouteProgress`
 * over 200 segments with an 80 m acceptance bound, `minDistanceToUpcomingRoute` over a *different*
 * 105-segment window, and the trim over exactly ONE segment, the one at the index, with no search
 * at all.
 *
 * That last one is what drew the straight lines. The moment the index lagged reality — and the
 * two paragraphs above list several ways it could — the trim projected the rider onto a segment
 * hundreds of metres behind, then emitted `rider → that stale point → the route onwards from
 * there`. On screen: a long straight chord shooting off the chevron across the map, with the route
 * doubling back to meet it. Meanwhile the off-route gate, searching its own wider window, found
 * the rider comfortably on the road and let the bad geometry through.
 *
 * Returns null only when [route] is too short to have a segment.
 */
fun locateOnRoute(
    route: List<LatLng>,
    cumulative: DoubleArray,
    from: LatLng,
    fromIndex: Int,
): RouteLocation? {
    if (route.size < 2) return null
    val lastSegment = route.size - 2
    val anchor = fromIndex.coerceIn(0, lastSegment)
    // Window bounds in metres along the route, resolved to vertex indices through the prefix sums
    // we already keep. Falls back to the anchor alone if the cumulative array doesn't match.
    val (start, end) = if (cumulative.size == route.size) {
        val here = cumulative[anchor]
        indexRangeCovering(cumulative, here - ROUTE_LOOKBEHIND_METERS, here + ROUTE_LOOKAHEAD_METERS, lastSegment)
    } else {
        anchor to min(lastSegment, anchor + 200)
    }
    return scanSegments(route, from, start, end)
}

/**
 * Full-route search, used only to RE-ACQUIRE after the windowed search has lost the rider.
 *
 * Progress is forward-only by design, because a route that touches itself — a loop, an
 * out-and-back, a cloverleaf, the two carriageways of a divided highway — would otherwise let a
 * single noisy fix throw the index onto the wrong pass. But forward-only with no escape hatch is
 * how the index came to freeze permanently after one GPS gap. This is the escape hatch: the caller
 * runs it only when the window has already failed, and accepts the result only when it lands the
 * rider genuinely on the road.
 */
fun locateAnywhereOnRoute(route: List<LatLng>, from: LatLng): RouteLocation? {
    if (route.size < 2) return null
    return scanSegments(route, from, 0, route.size - 2)
}

/** Inclusive vertex-index range whose cumulative distances span [lowMeters, highMeters]. */
private fun indexRangeCovering(
    cumulative: DoubleArray,
    lowMeters: Double,
    highMeters: Double,
    lastSegment: Int,
): Pair<Int, Int> {
    // Hand-rolled rather than `cumulative.toList().binarySearch { }`: this runs on every GPS fix
    // and that idiom boxes the whole prefix-sum array into a List<Double> first.
    fun search(target: Double): Int {
        var lo = 0
        var hi = cumulative.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }
    val start = (search(lowMeters) - 1).coerceIn(0, lastSegment)
    val end = search(highMeters).coerceIn(start, lastSegment)
    return start to end
}

/**
 * Nearest segment to [from] in `[start, end]`, by true minimum — no early exit.
 *
 * The old scan broke out at the FIRST segment closer than 5 m whose `t < 0.999`, which is not the
 * nearest segment, only the first acceptable one. Where a route runs back alongside itself the
 * outbound and return carriageways are both within 5 m in polyline terms, so the scan could latch
 * onto whichever pass came first in the vertex order and stay there. Taking the real minimum costs
 * a few hundred multiply-adds per fix now that [segmentProjection] no longer calls a geodesic.
 *
 * Ties go to the LATER segment when the rider is interior to it (`t` strictly inside 0..1). Two
 * segments meeting at the vertex the rider is standing on report the same distance; picking the
 * one the rider is on rather than the one they have finished is what keeps the polyline head
 * moving at the chevron's pace instead of stalling on a vertex and then jumping to catch up.
 */
private fun scanSegments(route: List<LatLng>, from: LatLng, start: Int, end: Int): RouteLocation? {
    if (end < start) return null
    var best: RouteLocation? = null
    for (i in start..end) {
        val hit = segmentProjection(from, route[i], route[i + 1])
        val current = best
        val interior = hit.t > 0.001 && hit.t < 0.999
        val better = when {
            current == null -> true
            hit.distanceMeters < current.distanceMeters - 0.01 -> true
            hit.distanceMeters <= current.distanceMeters + 0.01 && interior -> true
            else -> false
        }
        if (better) best = RouteLocation(i, hit.point, hit.t, hit.distanceMeters)
    }
    return best
}

/**
 * The rider's distance along [route] at [location].
 *
 * Reads the location the caller already computed rather than re-projecting, so progress cannot
 * disagree with the drawn polyline about which segment the rider is on.
 */
fun progressAlongRoute(cumulative: DoubleArray, route: List<LatLng>, location: RouteLocation): Double {
    if (cumulative.size != route.size || route.size < 2) return 0.0
    val i = min(location.segmentIndex, route.size - 2)
    return cumulative[i] + metersBetween(route[i], location.point)
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
    location: RouteLocation,
    speedMps: Double,
    courseValidSpeedMps: Double,
): TrimResult? {
    if (route.size < 2) return null
    val next = min(location.segmentIndex + 1, route.size - 1)

    // A wild fix inside an on-route run must not be yanked onto the line — the off-route flag needs
    // three consecutive readings to trip, so this bound is what stops us claiming precision we
    // don't have in between. The leader drawn below is now bounded by the caller's off-route gate
    // (trimming stops entirely past OFF_ROUTE_THRESHOLD_METERS) because [location] is a genuine
    // nearest-point search; when it was a projection onto one stale segment the "short leader"
    // could be any length at all.
    val snapped = location.distanceMeters <= SNAP_MAX_OFFSET_METERS
    val head = if (snapped) location.point else from

    // Point along the ROAD, not along the course. A GPS-derived bearing weaves with the lane and
    // with noise — the arrow sat a few degrees askew on a line it was sitting exactly on. The
    // camera takes its bearing from this same value, so the map steadies with it.
    val roadBearing = if (snapped && speedMps >= courseValidSpeedMps) {
        routeBearingAhead(route, location.point, location.segmentIndex)
    } else {
        null
    }

    val trimmed = ArrayList<LatLng>(route.size - next + 2)
    trimmed.add(head)
    if (!snapped) trimmed.add(location.point)   // short leader from the marker back to the road
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
