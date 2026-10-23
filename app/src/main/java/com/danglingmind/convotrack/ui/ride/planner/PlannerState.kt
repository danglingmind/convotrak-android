package com.danglingmind.convotrack.ui.ride.planner

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.danglingmind.convotrack.data.location.CurrentLocation
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.PlacesService
import com.danglingmind.convotrack.data.maps.RecentDestinations
import com.danglingmind.convotrack.data.maps.RouteAvoidances
import com.danglingmind.convotrack.data.maps.RouteOption
import com.danglingmind.convotrack.data.maps.reverseGeocode
import com.danglingmind.convotrack.data.net.CreateWaypoint
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.MapFraming
import com.danglingmind.convotrack.ui.map.NavCamera
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos

// State behind the map-first ride planner — the Android port of iOS RidePlannerModel
// (`design/CREATE_RIDE_REDESIGN.md` in the iOS repo): the stops, the route through them, the
// alternatives Google offers, and the ride's own details. Create Ride and Edit Ride share it;
// saving and deleting stay in the two screens.

/**
 * One stop on the ride. The first is the start and the last the destination; anything between is
 * a stop on the way. One list is what lets swap, reorder and remove treat every row alike.
 */
data class PlannerStop(
    val id: Long,
    /** The chosen place. Null while the rider is still searching for it, or for "Your location". */
    val place: PlaceResult? = null,
    /** Follows the rider's own position instead of a fixed place. */
    val isCurrentLocation: Boolean = false,
) {
    val isChosen: Boolean get() = place != null || isCurrentLocation
}

enum class StopRole { START, STOP, DESTINATION }

@Stable
class PlannerState(
    private val scope: CoroutineScope,
    private val appContext: Context,
    ride: Ride? = null,
) {
    companion object {
        /** Start and destination plus up to eight stops between. The Routes API allows 25
         *  intermediates, but past this the stops card stops being readable over the map. */
        const val MAX_STOPS = 10
    }

    private var nextId = 1L
    private fun newId() = nextId++

    // MARK: Ride details

    var title by mutableStateOf("")
    var maxRiders by mutableIntStateOf(5)

    /** Road types to keep off. Not stored with the ride: the route it produces is what's saved. */
    var avoid by mutableStateOf(RouteAvoidances.NONE)
        private set

    // MARK: Stops

    /** Always at least two: start first, destination last. */
    val stops = mutableStateListOf(PlannerStop(newId(), isCurrentLocation = true), PlannerStop(newId()))

    /** The rider's last known position, for "Your location" and the map's opening view. */
    var userLocation by mutableStateOf<LatLng?>(null)
        private set

    // MARK: Route

    /** Alternatives for a direct start → destination route; empty once there are stops between. */
    var routes by mutableStateOf<List<RouteOption>>(emptyList())
        private set
    var selectedRouteIndex by mutableIntStateOf(0)
        private set
    /** Where each alternative's time bubble sits, by index into [routes]. */
    var bubbleAnchors by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var routeCoords by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var distanceMeters by mutableStateOf(0.0)
        private set
    var durationSeconds by mutableStateOf(0.0)
        private set
    var isRouting by mutableStateOf(false)
        private set
    var routeFailed by mutableStateOf(false)
        private set

    var camera by mutableStateOf<MapCamera>(MapCamera.Free())
        private set
    private var cameraSeq = 1L

    /** The title we filled in from the destination. While the field still holds it, a new
     *  destination replaces it; once the rider types their own, it is theirs. */
    private var suggestedTitle: String? = null
    private var routeJob: Job? = null

    // MARK: Edit tracking

    private data class Snapshot(val title: String, val maxRiders: Int, val stops: List<String>, val avoid: RouteAvoidances)

    private val snapshot: Snapshot
        get() = Snapshot(
            title.trim(), maxRiders,
            stops.map { if (it.isCurrentLocation) "me" else it.place?.id.orEmpty() },
            avoid,
        )

    /** What was saved, for Edit Ride's discard prompt and its Save button. Null when creating. */
    private var savedSnapshot: Snapshot? = null

    /** The rider picked an alternative themselves. Tracked apart from the snapshot because a
     *  re-fetched route can differ from the saved one by a few metres without anyone touching it. */
    var riderPickedRoute by mutableStateOf(false)
        private set

    /** Edit Ride: something differs from what was saved. */
    val hasChanges: Boolean
        get() {
            val saved = savedSnapshot ?: return false
            return riderPickedRoute || snapshot != saved
        }

    init {
        if (ride != null) {
            title = ride.title
            maxRiders = ride.maxAllowedParticipants
            val saved = ride.waypoints.sortedBy { it.order }.map { wp ->
                PlannerStop(newId(), PlaceResult(id = "wp_${wp.order}", name = wp.name, latLng = LatLng(wp.lat, wp.lng), address = null))
            }
            if (saved.size >= 2) {
                stops.clear()
                stops.addAll(saved)
            }
            DirectionsService.decodedRoute(ride.routePolyline)?.let { stored ->
                routeCoords = stored
                distanceMeters = ride.distanceMeters
                durationSeconds = ride.estimatedDurationSeconds.toDouble()
            }
            savedSnapshot = snapshot
        }
    }

    // MARK: Derived

    fun role(stop: PlannerStop): StopRole {
        val i = stops.indexOfFirst { it.id == stop.id }
        return when (i) {
            0 -> StopRole.START
            stops.lastIndex -> StopRole.DESTINATION
            else -> StopRole.STOP
        }
    }

    fun coordinate(stop: PlannerStop): LatLng? = if (stop.isCurrentLocation) userLocation else stop.place?.latLng

    fun name(stop: PlannerStop): String = if (stop.isCurrentLocation) "Your location" else stop.place?.name.orEmpty()

    /** The planner leaves the empty map once the destination is chosen. */
    val hasDestination: Boolean get() = stops.lastOrNull()?.isChosen == true

    /** The chosen stops, skipping a row whose search is still open. */
    val chosenStops: List<PlannerStop> get() = stops.filter { it.isChosen }

    /** "Your location" is on the ride, but there's no fix for it. */
    val isWaitingForLocation: Boolean get() = userLocation == null && chosenStops.any { it.isCurrentLocation }

    private val routeStopCoordinates: List<LatLng>?
        get() {
            val chosen = chosenStops
            val coords = chosen.mapNotNull(::coordinate)
            return coords.takeIf { it.size == chosen.size }
        }

    val canAddStop: Boolean get() = stops.size < MAX_STOPS
    val canRemoveStops: Boolean get() = stops.size > 2
    val destinationId: Long get() = stops.last().id
    val hasRoute: Boolean get() = routeCoords.isNotEmpty() && distanceMeters > 0

    /** "Your location" is offered for a row as long as no other row already uses it. */
    fun offersCurrentLocation(id: Long): Boolean = stops.none { it.isCurrentLocation && it.id != id }

    fun canCommit(busy: Boolean): Boolean =
        title.isNotBlank() && stops.all { it.isChosen } && routeStopCoordinates != null && hasRoute && !isRouting && !busy

    // MARK: Location

    /** Picks up the rider's position for the map and "Your location". */
    fun locateUser() {
        scope.launch {
            val fix = CurrentLocation.get(appContext) ?: return@launch
            val hadLocation = userLocation != null
            userLocation = fix
            if (hadLocation) return@launch
            if (!hasDestination) focusOnUser(animated = false)
            else if (stops.any { it.isCurrentLocation }) recalculateRoute()
        }
    }

    fun focusOnUser(animated: Boolean = true) {
        val me = userLocation ?: return
        focus(me, zoom = 13f, animated = animated)
    }

    fun focus(target: LatLng, zoom: Float, animated: Boolean = true) {
        camera = MapCamera.Navigate(cameraSeq++, target, zoom, 0f, 0f, animated, MapFraming.CENTERED)
    }

    // MARK: Editing stops

    fun choose(place: PlaceResult, id: Long) {
        val i = stops.indexOfFirst { it.id == id }
        if (i < 0) return
        stops[i] = stops[i].copy(place = place, isCurrentLocation = false)
        if (i == stops.lastIndex) RecentDestinations.remember(appContext, place)
        stopsChanged()
    }

    fun useCurrentLocation(id: Long) {
        val i = stops.indexOfFirst { it.id == id }
        if (i < 0) return
        stops[i] = stops[i].copy(place = null, isCurrentLocation = true)
        locateUser()
        stopsChanged()
    }

    /** Adds an empty stop just before the destination, the usual place for a fuel or food stop,
     *  and returns it so its search can open. Reorder moves it anywhere else. */
    fun addStop(): Long? {
        if (!canAddStop) return null
        val stop = PlannerStop(newId())
        stops.add(stops.lastIndex, stop)
        return stop.id
    }

    /** Drops a row the rider opened a search for and then backed out of. */
    fun discardIfEmpty(id: Long) {
        val stop = stops.firstOrNull { it.id == id } ?: return
        if (!stop.isChosen && canRemoveStops) stops.remove(stop)
    }

    fun removeStop(id: Long) {
        if (!canRemoveStops) return
        stops.removeAll { it.id == id }
        stopsChanged()
    }

    /** Start and destination trade places. Only offered for a two-stop ride. */
    fun swapEnds() {
        if (stops.size != 2) return
        val (a, b) = stops[0] to stops[1]
        stops[0] = b
        stops[1] = a
        stopsChanged()
    }

    /** Moves a stop one row up (-1) or down (+1). While a drag is in progress [reroute] is false
     *  and the route waits for [finishReorder], so a drag across three rows asks Google once. */
    fun moveStop(id: Long, delta: Int, reroute: Boolean = true) {
        val i = stops.indexOfFirst { it.id == id }
        val target = i + delta
        if (i < 0 || target !in stops.indices) return
        val moving = stops[i]
        stops[i] = stops[target]
        stops[target] = moving
        if (reroute) stopsChanged()
    }

    fun finishReorder() = stopsChanged()

    /** Back from the route preview: forget every stop but the start, keep the details. */
    fun clearDestination() {
        val start = stops.first()
        stops.clear()
        stops.addAll(listOf(start, PlannerStop(newId())))
        recalculateRoute()
    }

    fun updateAvoid(next: RouteAvoidances) {
        if (next == avoid) return
        avoid = next
        recalculateRoute()
    }

    private fun stopsChanged() {
        refreshSuggestedTitle()
        recalculateRoute()
    }

    private fun refreshSuggestedTitle() {
        val place = stops.lastOrNull()?.place ?: return
        val trimmed = title.trim()
        if (trimmed.isNotEmpty() && trimmed != suggestedTitle) return
        val suggestion = "Ride to ${place.name}"
        title = suggestion
        suggestedTitle = suggestion
    }

    // MARK: Route calculation

    /** Re-routes through the current stops. A newer call cancels an older one still in flight, so
     *  a quick change of destination can't be overwritten by the route it replaced. */
    fun recalculateRoute(preferStoredMatch: Boolean = false) {
        routeJob?.cancel()
        routeJob = scope.launch { computeRoute(preferStoredMatch) }
    }

    private suspend fun computeRoute(preferStoredMatch: Boolean) {
        val coords = routeStopCoordinates
        if (!hasDestination || coords == null || coords.size < 2) {
            applyNoRoute()
            val dest = stops.lastOrNull()?.let(::coordinate)
            if (dest != null) focus(dest, 13f) else if (!hasDestination) focusOnUser()
            return
        }
        val storedDistance = distanceMeters
        val thisJob = currentCoroutineContext()[Job]
        isRouting = true
        routeFailed = false
        // `runCatching` also catches cancellation, so after each request check this job is still
        // the current one before writing anything: a superseded route must not land.
        try {
            // Direct start → destination: offer the alternatives. Google returns none once there
            // are intermediate stops.
            if (coords.size == 2) {
                val options = runCatching { DirectionsService.alternativeRoutes(coords[0], coords[1], avoid = avoid) }.getOrNull()
                currentCoroutineContext().ensureActive()
                if (options != null && options.size > 1) {
                    routes = options
                    bubbleAnchors = bubbleAnchors(options.map { it.coordinates })
                    applyRoute(if (preferStoredMatch) closestIndex(options, storedDistance) else 0)
                    fitCameraToRoute()
                    return
                }
            }
            val result = runCatching { DirectionsService.routeThrough(coords, avoid = avoid) }.getOrNull()
            currentCoroutineContext().ensureActive()
            if (result == null) {
                applyNoRoute()
                routeFailed = true
                return
            }
            routes = emptyList()
            bubbleAnchors = emptyList()
            selectedRouteIndex = 0
            routeCoords = result.coordinates
            distanceMeters = result.distanceMeters
            durationSeconds = result.durationSeconds
            fitCameraToRoute()
        } finally {
            if (routeJob == thisJob) isRouting = false
        }
    }

    private fun applyNoRoute() {
        routes = emptyList()
        bubbleAnchors = emptyList()
        selectedRouteIndex = 0
        routeCoords = emptyList()
        distanceMeters = 0.0
        durationSeconds = 0.0
        isRouting = false
    }

    /** The rider picked the alternative at [index]. Doesn't move the camera: every alternative
     *  shares the same two ends, so the view already shows it. */
    fun selectRoute(index: Int) {
        if (index !in routes.indices || index == selectedRouteIndex) return
        riderPickedRoute = true
        applyRoute(index)
    }

    private fun applyRoute(index: Int) {
        val option = routes.getOrNull(index) ?: return
        selectedRouteIndex = index
        routeCoords = option.coordinates
        distanceMeters = option.distanceMeters
        durationSeconds = option.durationSeconds
    }

    /** Frames the route and every alternative. The planner's map padding keeps it clear of the
     *  chrome; the extra top inset leaves room for the destination pin, drawn above its point. */
    fun fitCameraToRoute(animated: Boolean = true) {
        val coords = if (routes.isEmpty()) routeCoords else routes.flatMap { it.coordinates }
        if (coords.isEmpty()) return
        camera = MapCamera.Fit(cameraSeq++, coords, NavCamera.STOP_PIN_TOP_EXTENT_DP * 0.6f, 40f, 32f, 40f, animated)
    }

    /** Edit Ride: the alternative closest to the saved distance, so the leader's pick stays. */
    private fun closestIndex(options: List<RouteOption>, target: Double): Int {
        if (target <= 0) return 0
        return options.indices.minByOrNull { abs(options[it].distanceMeters - target) } ?: 0
    }

    // MARK: Saving

    /** Waypoints in the order the API expects: START → WAYPOINT* → DESTINATION. "Your location" is
     *  first given a real name, since every rider sees it and to them it would mean their own. */
    suspend fun resolvedWaypoints(): List<CreateWaypoint>? {
        val chosen = chosenStops
        if (chosen.size < 2 || chosen.size != stops.size) return null
        return chosen.mapIndexed { i, stop ->
            val at = coordinate(stop) ?: return null
            var name = stop.place?.name.orEmpty()
            if (stop.isCurrentLocation) name = PlacesService.reverseGeocode(at)?.name.orEmpty()
            if (name.isEmpty()) name = if (i == 0) "Starting point" else "Pinned location"
            CreateWaypoint(
                order = i,
                name = name,
                type = when (i) {
                    0 -> "START"
                    chosen.lastIndex -> "DESTINATION"
                    else -> "WAYPOINT"
                },
                lat = at.latitude,
                lng = at.longitude,
            )
        }
    }
}

/**
 * For each route, the point furthest from every other route: where it has split off on its own
 * road, so its bubble points at a line no other route shares (iOS `bubbleAnchors(for:)`). The first
 * and last tenth are skipped, since every alternative leaves from and arrives at the same place.
 * Works on an even sample with flat-projection distances — plenty to rank points kilometres apart.
 */
internal fun bubbleAnchors(routes: List<List<LatLng>>): List<LatLng> {
    fun sample(coords: List<LatLng>, count: Int): List<LatLng> =
        if (coords.size <= count) coords else (0 until count).map { coords[(coords.size - 1) * it / (count - 1)] }
    val sampled = routes.map { sample(it, 120) }
    val lngScale = cos(Math.toRadians(sampled.firstOrNull()?.firstOrNull()?.latitude ?: 0.0))
    fun dist2(a: LatLng, b: LatLng): Double {
        val dLat = a.latitude - b.latitude
        val dLng = (a.longitude - b.longitude) * lngScale
        return dLat * dLat + dLng * dLng
    }
    return sampled.indices.map { i ->
        val points = sampled[i]
        if (points.isEmpty()) return@map LatLng(0.0, 0.0)
        val others = sampled.indices.filter { it != i }.flatMap { sampled[it] }
        val candidates = if (points.size >= 10) points.subList(points.size / 10, points.size * 9 / 10 + 1) else points
        candidates.maxByOrNull { p -> others.minOfOrNull { dist2(p, it) } ?: 0.0 } ?: points[points.size / 2]
    }
}
