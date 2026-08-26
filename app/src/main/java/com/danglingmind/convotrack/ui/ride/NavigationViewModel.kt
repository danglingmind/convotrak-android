package com.danglingmind.convotrack.ui.ride

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danglingmind.convotrack.data.audio.SirenPlayer
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.danglingmind.convotrack.data.net.LiveLeaderboardEntry
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.RideStateUpdate
import com.danglingmind.convotrack.data.net.RideUpdatedEvent
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.data.socket.SocketEmergency
import com.danglingmind.convotrack.di.ServiceLocator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** One rider on the live map. */
data class LiveRider(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val rank: Int,
    val distanceToGoalKm: Double,
    val coordinate: LatLng,
    val isMe: Boolean,
)

/** One row of the live leaderboard strip. */
data class NavLeaderboardRow(
    val id: String,
    val rank: Int,
    val name: String,
    val avatarUrl: String?,
    val distanceToGoalKm: Double,
    val isMe: Boolean,
)

/**
 * Live navigation state — the Android port of iOS `NavigationViewModel`.
 *
 * Observes the shared [RideRealtimeSession] rather than the socket directly, so navigation
 * self-heals after a drop and sees an always-fresh roster (mid-ride joiners get real names, not
 * "Rider"). It owns everything derived from a GPS fix: route progress, the turn banner, off-route
 * detection and rerouting, ETA, and the rider's own distance to goal.
 */
class NavigationViewModel : ViewModel() {

    // MARK: - Published state

    private val _riders = MutableStateFlow<List<LiveRider>>(emptyList())
    val riders: StateFlow<List<LiveRider>> = _riders.asStateFlow()

    private val _leaderboardRows = MutableStateFlow<List<NavLeaderboardRow>>(emptyList())
    val leaderboardRows: StateFlow<List<NavLeaderboardRow>> = _leaderboardRows.asStateFlow()

    private val _mySpeedKmh = MutableStateFlow(0.0)
    val mySpeedKmh: StateFlow<Double> = _mySpeedKmh.asStateFlow()

    /**
     * Whether any usable fix has arrived yet.
     *
     * Separates "not moving" from "nothing known", which a bare `mySpeedKmh == 0` cannot: both
     * used to render as "--", so the readout showed the same thing at a red light as it did before
     * the GPS had said anything at all.
     */
    private val _hasLocationFix = MutableStateFlow(false)
    val hasLocationFix: StateFlow<Boolean> = _hasLocationFix.asStateFlow()

    private val _myDistanceToGoalKm = MutableStateFlow(0.0)
    val myDistanceToGoalKm: StateFlow<Double> = _myDistanceToGoalKm.asStateFlow()

    /** Full planned route — the dim layer. A reroute deliberately never touches this. */
    private val _routeCoordinates = MutableStateFlow<List<LatLng>>(emptyList())
    val routeCoordinates: StateFlow<List<LatLng>> = _routeCoordinates.asStateFlow()

    /** The bright line: trimmed to start under the rider, or the reroute while off-route. */
    private val _activeRouteCoordinates = MutableStateFlow<List<LatLng>>(emptyList())
    val activeRouteCoordinates: StateFlow<List<LatLng>> = _activeRouteCoordinates.asStateFlow()

    /** Increments whenever a route is adopted, so the preview camera can re-fit. */
    private val _routeVersion = MutableStateFlow(0)
    val routeVersion: StateFlow<Int> = _routeVersion.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _userHeading = MutableStateFlow(0.0)
    val userHeading: StateFlow<Double> = _userHeading.asStateFlow()

    /** Increments on each accepted fix; drives the follow camera. */
    private val _locationTick = MutableStateFlow(0)
    val locationTick: StateFlow<Int> = _locationTick.asStateFlow()

    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    /** Routes API maneuver for the current step (`TURN_LEFT`, `ROUNDABOUT_RIGHT`, …). Drives the
     *  banner arrow, so the arrow can't disagree with the instruction text. */
    private val _currentManeuver = MutableStateFlow("")
    val currentManeuver: StateFlow<String> = _currentManeuver.asStateFlow()

    private val _distanceToNextTurnMeters = MutableStateFlow(0.0)
    val distanceToNextTurnMeters: StateFlow<Double> = _distanceToNextTurnMeters.asStateFlow()

    private val _isOffRoute = MutableStateFlow(false)
    val isOffRoute: StateFlow<Boolean> = _isOffRoute.asStateFlow()

    /**
     * Live remaining travel time to the next stop, in seconds.
     *
     * Refreshed from the traffic-aware Routes API and counted down in between, so it moves every
     * second and reacts to traffic — unlike the planned-duration estimate it supersedes, which
     * could only change when distance changed and always assumed the pace the route was planned at.
     */
    private val _etaSecondsRemaining = MutableStateFlow<Double?>(null)
    val etaSecondsRemaining: StateFlow<Double?> = _etaSecondsRemaining.asStateFlow()

    /**
     * The ETA as it should be shown.
     *
     * Published as state rather than computed on demand: the value changes every second from the
     * countdown, and a plain function would only be re-read when some *other* state happened to
     * trigger a recomposition — so the number on screen would sit still for seconds at a time and
     * then jump.
     */
    private val _etaText = MutableStateFlow("--")
    val etaText: StateFlow<String> = _etaText.asStateFlow()

    private val _destinationCoordinate = MutableStateFlow<LatLng?>(null)
    val destinationCoordinate: StateFlow<LatLng?> = _destinationCoordinate.asStateFlow()

    private val _middleWaypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val middleWaypoints: StateFlow<List<Waypoint>> = _middleWaypoints.asStateFlow()

    private val _isEnding = MutableStateFlow(false)
    val isEnding: StateFlow<Boolean> = _isEnding.asStateFlow()

    private val _endError = MutableStateFlow<String?>(null)
    val endError: StateFlow<String?> = _endError.asStateFlow()

    private val _rideCompleted = MutableStateFlow(false)
    val rideCompleted: StateFlow<Boolean> = _rideCompleted.asStateFlow()

    // MARK: - Private route state

    private var rideId = ""
    private var myUserId = ""
    private var leaderId = ""
    private var staticParticipants: List<RideParticipant> = emptyList()
    private var totalDistanceMeters = 0.0

    /**
     * The polyline the rider is actually following: the planned route at first, and the reroute
     * once one lands.
     *
     * Progress, off-route detection, polyline trimming and step positions are all measured against
     * THIS. [_routeCoordinates] keeps its separate meaning — the dim planned layer, which a reroute
     * deliberately never touches — because navigation must not measure itself against a plan the
     * rider has already abandoned.
     */
    private var navRoute: List<LatLng> = emptyList()
    private var navRouteCumulative: DoubleArray = DoubleArray(0)
    private var navSteps: List<DirectionsStep> = emptyList()
    private var currentStepIndex = 0

    /** Forward-only progress index into [navRoute]. */
    private var navSegmentIndex = 0

    /** Perpendicular distance from the last fix to [navRoute]. The undebounced truth behind
     *  [isOffRoute], and what gates polyline trimming. */
    private var lastRouteOffsetMeters = Double.MAX_VALUE

    private var consecutiveOffCount = 0
    private var consecutiveOnCount = 0
    private var isRerouteInFlight = false
    private var lastRerouteOrigin: LatLng? = null
    private var needsInitialRouteCheck = false
    private var remainingStops: List<LatLng> = emptyList()
    private var stopCumulativeSeconds: List<Double> = emptyList()
    private var totalStopCount = 0
    private var routeExpectedTravelTime = 0.0

    // Last-known map position for every rider, kept so a rider who goes offline stays pinned at
    // their final spot (dimmed) instead of vanishing when they stop posting location ticks.
    private val lastKnownRiderPositions = mutableMapOf<String, LatLng>()

    // ETA bookkeeping.
    private var etaBaseSeconds: Double? = null
    private var etaBaseAtMs = 0L
    private var lastEtaRefreshMs = 0L
    private var isEtaRefreshInFlight = false

    // Heading bookkeeping. `lastCourseFixAtMs` gates the compass fallback.
    private var lastCourseFixAtMs = 0L

    private var cameraTickAtMs = 0L
    private var rerouteJob: Job? = null

    private companion object {
        const val OFF_ROUTE_THRESHOLD_METERS = 40.0
        const val OFF_ROUTE_CONFIRM_COUNT = 3   // consecutive off-route readings before flagging
        const val ON_ROUTE_CLEAR_COUNT = 4      // consecutive on-route readings before clearing
        const val REROUTE_TRAVEL_THRESHOLD_M = 200.0

        /** Speed below which a GPS bearing stops being trustworthy (~5.4 km/h). */
        const val COURSE_VALID_SPEED_MPS = 1.5

        /** How long a course fix stays fresh before the compass takes over. */
        const val COURSE_STALE_MS = 5_000L

        /**
         * Below this the rider is treated as stopped and the readout shows a flat 0.
         *
         * A GPS speed at rest does not settle on zero — it wanders by a metre or so a second, which
         * is a couple of km/h, so a parked bike reads "3" then "1" then "4" forever. The band is
         * set under a brisk walk, well below anything this app is ridden at.
         */
        const val STATIONARY_SPEED_KMH = 4.0

        /** One Routes API call per rider per minute while navigating. Frequent enough that a jam is
         *  reflected quickly, rare enough not to be a per-second billing drain. */
        const val ETA_REFRESH_INTERVAL_MS = 60_000L

        const val CAMERA_TICK_MIN_MS = 100L   // cap camera updates at ~10 Hz
    }

    val amILeader: Boolean get() = myUserId.isNotEmpty() && myUserId == leaderId

    /** True while a real turn instruction is still ahead (not yet at the final step / destination).
     *  Drives the maneuver-aware camera pull-back. */
    val hasUpcomingTurn: Boolean
        get() = currentStepIndex < navSteps.size - 1 && _currentInstruction.value.isNotEmpty()

    // MARK: - Setup

    fun setup(rideId: String, ride: Ride, myUserId: String, session: RideRealtimeSession) {
        this.rideId = rideId
        this.myUserId = myUserId
        this.leaderId = ride.leaderId
        this.staticParticipants = ride.participants
        this.totalDistanceMeters = ride.distanceMeters

        seedGeometry(ride.waypoints)

        // Pre-populate the leaderboard so it shows immediately, before any socket updates.
        val active = ride.participants.filter { it.status != "LEFT" }
        _leaderboardRows.value = active.mapIndexed { index, p ->
            NavLeaderboardRow(
                id = p.userId, rank = index + 1, name = p.name, avatarUrl = p.avatarUrl,
                distanceToGoalKm = ride.distanceMeters / 1000, isMe = p.userId == myUserId,
            )
        }
        _myDistanceToGoalKm.value = ride.distanceMeters / 1000

        viewModelScope.launch { calculateRoute(ride) }
    }

    private fun seedGeometry(waypoints: List<Waypoint>) {
        val sorted = waypoints.sortedBy { it.order }
        sorted.lastOrNull()?.let { _destinationCoordinate.value = LatLng(it.lat, it.lng) }
        _middleWaypoints.value = sorted.filter { it.type == "WAYPOINT" }
        remainingStops = sorted
            .filter { it.type == "WAYPOINT" || it.type == "DESTINATION" }
            .map { LatLng(it.lat, it.lng) }
    }

    /** Keeps the roster fresh so rider name/avatar lookup stays accurate as people join and leave. */
    fun onRosterChanged(participants: List<RideParticipant>) {
        staticParticipants = participants
        pruneDepartedRiders(participants)
    }

    /**
     * Drops riders who have left the ride from the derived state that doesn't otherwise rebuild
     * from the roster.
     *
     * [handleStateUpdate] reconciles both the leaderboard and the rider pins against the roster
     * already — but only inside `if (leaderboard.isNotEmpty())`, and the server's leaderboard is
     * empty for the entire window between a ride starting and the first location tick landing. A
     * rider leaving inside that window would otherwise stay in everyone else's leaderboard, live
     * rider count, map pins and off-screen indicators until somebody's GPS moved.
     */
    private fun pruneDepartedRiders(roster: List<RideParticipant>) {
        // An empty roster is a transient socket state (a reconnect mid-flight), not "everyone
        // left". Never prune on it — that would blank the HUD for a moment on every drop.
        if (roster.isEmpty()) return
        val present = roster.map { it.userId }.toMutableSet()
        present += myUserId
        _leaderboardRows.value = _leaderboardRows.value.filter { present.contains(it.id) }
        _riders.value = _riders.value.filter { present.contains(it.id) }
        // Also forget their last position, so the "keep offline riders pinned where they dropped"
        // path below can't resurrect a ghost pin for someone who has left.
        lastKnownRiderPositions.keys.retainAll(present)
    }

    // MARK: - Live state

    fun handleStateUpdate(update: RideStateUpdate) {
        // Only overwrite the pre-populated leaderboard when the server actually has data. The
        // initial ride:state_update emitted on start has an empty leaderboard before any location
        // updates have been processed — don't let that clear what we pre-seeded.
        if (update.leaderboard.isNotEmpty()) {
            val rows = update.leaderboard.map { entry ->
                val participant = staticParticipants.firstOrNull { it.userId == entry.userId }
                NavLeaderboardRow(
                    id = entry.userId,
                    rank = entry.rank,
                    name = entry.name,
                    avatarUrl = participant?.avatarUrl,
                    distanceToGoalKm = max(0.0, (totalDistanceMeters - entry.progress) / 1000),
                    isMe = entry.userId == myUserId,
                )
            }.toMutableList()

            // The server leaderboard only carries riders who've posted a location tick, so
            // joined-but-offline riders (never broadcast, or dropped) would vanish. Keep every
            // joined participant present as a trailing row; the view renders it disabled via
            // presence, matching how a rider who drops mid-navigation stays visible.
            val ranked = rows.map { it.id }.toSet()
            var nextRank = (rows.maxOfOrNull { it.rank } ?: 0) + 1
            staticParticipants
                .filter { it.status != "LEFT" && !ranked.contains(it.userId) }
                .forEach { p ->
                    rows += NavLeaderboardRow(
                        id = p.userId, rank = nextRank, name = p.name, avatarUrl = p.avatarUrl,
                        distanceToGoalKm = totalDistanceMeters / 1000, isMe = p.userId == myUserId,
                    )
                    nextRank++
                }
            _leaderboardRows.value = rows
        }

        // Riders with a live position this tick — and cache each position so a rider who later
        // goes offline can stay pinned at their final spot instead of disappearing.
        val live = update.participants.mapNotNull { p ->
            val lat = p.lat ?: return@mapNotNull null
            val lng = p.lng ?: return@mapNotNull null
            val coord = LatLng(lat, lng)
            lastKnownRiderPositions[p.userId] = coord
            val participant = staticParticipants.firstOrNull { it.userId == p.userId }
            val lbEntry: LiveLeaderboardEntry? = update.leaderboard.firstOrNull { it.userId == p.userId }
            LiveRider(
                id = p.userId,
                name = participant?.name ?: lbEntry?.name ?: "Rider",
                avatarUrl = participant?.avatarUrl,
                rank = lbEntry?.rank ?: 99,
                distanceToGoalKm = max(0.0, (totalDistanceMeters - p.progress) / 1000),
                coordinate = coord,
                isMe = p.userId == myUserId,
            )
        }.toMutableList()

        // Retain any joined rider who has no position this tick at their last-known spot. The pin
        // renders dimmed via presence (the same rule as the leaderboard row), so an offline rider
        // remains visible where they dropped instead of vanishing from the map.
        val present = live.map { it.id }.toSet()
        staticParticipants
            .filter { it.status != "LEFT" && it.userId != myUserId && !present.contains(it.userId) }
            .forEach { p ->
                val coord = lastKnownRiderPositions[p.userId] ?: return@forEach
                val row = _leaderboardRows.value.firstOrNull { it.id == p.userId }
                live += LiveRider(
                    id = p.userId, name = p.name, avatarUrl = p.avatarUrl,
                    rank = row?.rank ?: 99,
                    distanceToGoalKm = row?.distanceToGoalKm ?: (totalDistanceMeters / 1000),
                    coordinate = coord, isMe = false,
                )
            }
        _riders.value = live

        // Until the first local GPS fix arrives, fall back to the server's route progress. Once
        // GPS is driving, distance-to-goal is owned by the local route-trim path, which is robust
        // to off-route projection.
        if (_userLocation.value == null) {
            update.leaderboard.firstOrNull { it.userId == myUserId }?.let { entry ->
                _myDistanceToGoalKm.value = max(0.0, (totalDistanceMeters - entry.progress) / 1000)
            }
        }

        if (update.status == "COMPLETED") _rideCompleted.value = true
    }

    /**
     * The leader edited the ride while it's live (destination / stops / route changed).
     *
     * Re-seeds the waypoint geometry and recomputes the route so the map and turn steps follow the
     * new plan, and alerts riders with a chime plus a spoken "rerouting" cue. The backend has
     * already swapped the authoritative route geometry, so progress and the leaderboard re-sync on
     * the next state update.
     */
    fun handleRideUpdated(event: RideUpdatedEvent, updatedRide: Ride) {
        totalDistanceMeters = event.distanceMeters
        seedGeometry(event.waypoints)

        // Attention siren, then a spoken cue once the siren has cleared the audio session.
        SirenPlayer.play(SirenPlayer.Sound.REGROUP)
        VoiceAnnouncer.announce("Ride updated, rerouting", delayMs = 1_800)

        viewModelScope.launch { calculateRoute(updatedRide) }
    }

    // MARK: - Route

    private suspend fun calculateRoute(ride: Ride) {
        val result = rideRouteWithSteps(ride)
        if (result.coords.isEmpty()) return

        adoptNavRoute(result.coords, result.steps)

        // The dim layer stays the leader's plan even where navigation had to fall back onto the
        // route it holds instructions for: the two lines then diverge on screen, which is the
        // honest picture and the only way anyone would notice.
        _routeCoordinates.value = DirectionsService.decodedRoute(ride.routePolyline) ?: result.coords
        _activeRouteCoordinates.value = result.coords   // full route until the first GPS fix trims it
        routeExpectedTravelTime = result.durationSeconds
        stopCumulativeSeconds = result.cumulativeStopSeconds
        totalStopCount = result.cumulativeStopSeconds.size
        consecutiveOffCount = 0
        consecutiveOnCount = 0
        needsInitialRouteCheck = true
        _routeVersion.value += 1
        // The preview bar shows an ETA before the first GPS fix arrives, so seed it from the
        // planned duration as soon as the route is known.
        _etaText.value = computeEtaText()
    }

    /**
     * Adopts [route] as the polyline being followed: rebuilds its prefix distances, restamps the
     * steps onto it, and rewinds progress to its start. Used for the initial route AND for every
     * reroute, so the two paths can't drift apart.
     */
    private fun adoptNavRoute(route: List<LatLng>, steps: List<DirectionsStep>) {
        // The old ETA described a route we're no longer on; drop it and let the next fix refresh.
        etaBaseSeconds = null
        _etaSecondsRemaining.value = null
        lastEtaRefreshMs = 0L

        navRoute = route
        navRouteCumulative = cumulativeDistances(route)
        navSegmentIndex = 0
        navSteps = stampAlongRouteDistances(steps, route, navRouteCumulative)
        currentStepIndex = 0

        val banner = upcomingBanner(navSteps, 0)
        _currentInstruction.value = banner.instruction
        _currentManeuver.value = banner.maneuver
        _distanceToNextTurnMeters.value = banner.junctionDistanceAlongRoute
    }

    // MARK: - Location

    fun onLocationUpdate(location: Location, compassHeading: Float?) {
        val fix = LatLng(location.latitude, location.longitude)
        _mySpeedKmh.value = displaySpeedKmh(location)
        _hasLocationFix.value = true
        _userLocation.value = fix

        // A GPS bearing is derived from successive fixes, so below a walking pace it wanders
        // wildly — precisely when a rider is stopped at a light and would notice the map spinning.
        // Above the threshold it is the true direction of travel and outranks the compass.
        val now = System.currentTimeMillis()
        if (location.hasBearing() && location.hasSpeed() && location.speed >= COURSE_VALID_SPEED_MPS) {
            _userHeading.value = location.bearing.toDouble()
            lastCourseFixAtMs = now
        } else if (compassHeading != null && now - lastCourseFixAtMs > COURSE_STALE_MS) {
            // Course has gone stale — you're stopped or crawling, and the compass is the only
            // heading signal left worth showing. Gated at 5° so sensor noise doesn't jitter the
            // arrow (and, through it, the camera).
            if (headingDelta(compassHeading.toDouble(), _userHeading.value) >= 5) {
                _userHeading.value = compassHeading.toDouble()
            }
        }

        // Order matters: checkOffRoute advances navSegmentIndex, which is what updateCurrentStep
        // measures progress from. Reversed, every step decision would use the previous fix's
        // progress.
        checkOffRoute(fix, location)
        updateCurrentStep(fix)
        refreshEtaIfNeeded(fix)

        if (needsInitialRouteCheck && navRoute.isNotEmpty()) {
            needsInitialRouteCheck = false
            if (minDistanceToUpcomingRoute(navRoute, fix, navSegmentIndex) > OFF_ROUTE_THRESHOLD_METERS &&
                !isRerouteInFlight
            ) {
                _isOffRoute.value = true
                consecutiveOffCount = OFF_ROUTE_CONFIRM_COUNT
                launchReroute(fix)
            }
        }

        // Gated on the MEASURED offset, not on the debounced isOffRoute flag. Clearing that flag
        // takes four consecutive on-route readings, so for ~4 s after a reroute landed — or after
        // a jitter spike cleared — the head would stay frozen wherever it was last cut while the
        // rider drove on, detaching it from the marker for exactly as long as the debounce ran.
        // Proximity to the route we are following is the real precondition for trimming it.
        if (lastRouteOffsetMeters <= OFF_ROUTE_THRESHOLD_METERS) {
            val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
            trimActiveRoute(navRoute, fix, navSegmentIndex, speedMps, COURSE_VALID_SPEED_MPS)
                ?.let { trim ->
                    // The marker and the polyline head are now the same coordinate by construction.
                    _userLocation.value = trim.head
                    _activeRouteCoordinates.value = trim.trimmed
                    trim.roadBearing?.let {
                        _userHeading.value = it
                        // Counts as a fresh heading, otherwise the compass fallback would wake up
                        // five seconds in and start fighting the road bearing.
                        lastCourseFixAtMs = now
                    }
                }
        }

        // Drive DIST from the locally-trimmed remaining route rather than the server's `progress`.
        // The server value is a nearest-point projection that reads ~full (zeroing distance to
        // goal) whenever the rider isn't cleanly on-route.
        val active = _activeRouteCoordinates.value
        if (active.size >= 2) {
            _myDistanceToGoalKm.value = remainingRouteLength(active, fix) / 1000
        }

        if (now - cameraTickAtMs >= CAMERA_TICK_MIN_MS) {
            cameraTickAtMs = now
            _locationTick.value += 1
        }
    }

    /**
     * Speed for display, in km/h.
     *
     * `Location.speed` is meaningless when the fix carries no valid speed at all — not slow, but
     * unknown — and treating that as zero states a confident falsehood.
     */
    private fun displaySpeedKmh(location: Location): Double {
        if (!location.hasSpeed() || location.speed < 0) return 0.0
        val kmh = location.speed * 3.6
        return if (kmh < STATIONARY_SPEED_KMH) 0.0 else kmh
    }

    /**
     * Which maneuver comes next, derived from how far along the route the rider actually is — the
     * model Google Maps uses.
     *
     * This replaces a proximity trigger that advanced only when a fix landed within 30 m
     * (straight-line) of the step's end coordinate. Miss that window once — GPS scatter, a fix
     * interval that straddles the junction at speed, a step end snapped to the far side of a
     * divided road — and the distance only grows as the rider drives away, so the step could never
     * advance again: the very first instruction ("Head west on …") stuck for the whole ride, which
     * is exactly the reported symptom. It also advanced at most one step per fix, so a cluster of
     * short steps (roundabout exits, quick doubles) left it permanently behind.
     */
    private fun updateCurrentStep(fix: LatLng) {
        if (navSteps.isEmpty()) return
        val progress = progressAlongRoute(navRoute, navRouteCumulative, fix, navSegmentIndex)

        // First step that still ends ahead of us. No slack: a rider parked exactly on a step
        // boundary can't flicker, because the forward-only clamp below makes it impossible — and
        // any slack would cost the banner its last few metres before every junction, moving the
        // instruction on at the moment it matters most. Google holds the current maneuver until
        // you are through the junction.
        val computed = navSteps.indexOfFirst { it.endDistanceAlongRoute > progress }
            .takeIf { it >= 0 } ?: (navSteps.size - 1)
        currentStepIndex = max(currentStepIndex, computed)

        val banner = upcomingBanner(navSteps, currentStepIndex)
        // Along-route rather than straight-line: "300 m" should mean 300 m of driving, which is
        // what Google shows and what actually matters around a curve.
        _distanceToNextTurnMeters.value = max(0.0, banner.junctionDistanceAlongRoute - progress)
        _currentInstruction.value = banner.instruction
        _currentManeuver.value = banner.maneuver
    }

    private fun checkOffRoute(fix: LatLng, location: Location) {
        // Pop stops we've already passed (within 80 m).
        while (remainingStops.isNotEmpty() && metersBetween(fix, remainingStops.first()) < 80) {
            remainingStops = remainingStops.drop(1)
        }

        if (navRoute.size < 2) return

        navSegmentIndex = advanceRouteProgress(navRoute, fix, navSegmentIndex)
        lastRouteOffsetMeters = minDistanceToUpcomingRoute(navRoute, fix, navSegmentIndex)
        val rawOffRoute = lastRouteOffsetMeters > OFF_ROUTE_THRESHOLD_METERS

        if (rawOffRoute) {
            consecutiveOffCount++
            consecutiveOnCount = 0
        } else {
            consecutiveOnCount++
            consecutiveOffCount = 0
        }

        if (!_isOffRoute.value && consecutiveOffCount >= OFF_ROUTE_CONFIRM_COUNT) {
            _isOffRoute.value = true
        } else if (_isOffRoute.value && consecutiveOnCount >= ON_ROUTE_CLEAR_COUNT) {
            _isOffRoute.value = false
        }

        if (_isOffRoute.value) {
            if (isRerouteInFlight) return
            // Throttle: only recalculate every 200 m of travel while off-route.
            val last = lastRerouteOrigin
            if (last != null && metersBetween(fix, last) < REROUTE_TRAVEL_THRESHOLD_M) return
            launchReroute(fix)
        } else {
            isRerouteInFlight = false
            lastRerouteOrigin = null
        }
    }

    /**
     * Recalculates the full remaining route from the rider's position through all remaining stops
     * to the destination.
     *
     * Navigation then switches wholesale onto the reroute: progress, off-route checks, trimming
     * and — crucially — the turn steps all now refer to it. Without adopting the steps, the banner
     * keeps reciting maneuvers from a route the rider has already left; without adopting the route,
     * the rider stays "off" the abandoned plan forever and re-reroutes every 200 m.
     *
     * The planned [_routeCoordinates] is never touched — it stays as the dim planned layer.
     */
    private fun launchReroute(origin: LatLng) {
        if (remainingStops.isEmpty()) return
        isRerouteInFlight = true
        lastRerouteOrigin = origin
        rerouteJob?.cancel()
        rerouteJob = viewModelScope.launch {
            val result = calculateFullReroute(origin, remainingStops)
            isRerouteInFlight = false
            if (result.coords.isEmpty()) return@launch
            _activeRouteCoordinates.value = result.coords
            adoptNavRoute(result.coords, result.steps)
        }
    }

    /**
     * Keeps the ETA moving on every fix, and re-asks the Routes API for a traffic-aware figure
     * once per [ETA_REFRESH_INTERVAL_MS].
     *
     * The countdown between refreshes is what makes it feel live: subtracting elapsed wall time
     * from the last known figure is self-correcting — if the rider is stopped, the number keeps
     * falling until the next refresh puts it back up, which is the honest reflection of losing
     * time in traffic. The estimate it replaces scaled the PLANNED duration by remaining distance,
     * so standing still changed nothing at all.
     */
    private fun refreshEtaIfNeeded(fix: LatLng) {
        val now = System.currentTimeMillis()
        etaBaseSeconds?.let { base ->
            _etaSecondsRemaining.value = max(0.0, base - (now - etaBaseAtMs) / 1000.0)
        }
        _etaText.value = computeEtaText()

        if (isEtaRefreshInFlight) return
        if (lastEtaRefreshMs != 0L && now - lastEtaRefreshMs < ETA_REFRESH_INTERVAL_MS) return
        // Same target as the planned estimate: the next stop on a multi-stop ride, otherwise the
        // destination.
        val target = remainingStops.firstOrNull() ?: _destinationCoordinate.value ?: return

        lastEtaRefreshMs = now
        isEtaRefreshInFlight = true
        viewModelScope.launch {
            val result = runCatching {
                DirectionsService.route(fix, target, trafficAware = true)
            }.getOrNull()
            isEtaRefreshInFlight = false
            val seconds = result?.durationSeconds ?: return@launch
            if (seconds <= 0) return@launch
            etaBaseSeconds = seconds
            etaBaseAtMs = System.currentTimeMillis()
            _etaSecondsRemaining.value = seconds
            _etaText.value = computeEtaText()
        }
    }

    /**
     * Remaining travel time to the NEXT stop, as text.
     *
     * The live traffic-aware value wins whenever we have one; the planned-duration estimate below
     * is only the bootstrap for the seconds before the first refresh lands.
     */
    private fun computeEtaText(): String {
        _etaSecondsRemaining.value?.let { return formatDuration(it) }
        if (routeExpectedTravelTime <= 0 || totalDistanceMeters <= 0) return "--"
        val remainingKm = _myDistanceToGoalKm.value
        if (remainingKm < 0) return "--"
        val remainingFraction = ((remainingKm * 1000) / totalDistanceMeters).coerceIn(0.0, 1.0)
        val elapsed = routeExpectedTravelTime * (1 - remainingFraction)

        val secsToNextStop = if (stopCumulativeSeconds.isNotEmpty()) {
            // How many stops we've already passed → index of the next one.
            val passed = max(0, totalStopCount - remainingStops.size)
            val idx = min(passed, stopCumulativeSeconds.size - 1)
            max(0.0, stopCumulativeSeconds[idx] - elapsed)
        } else {
            routeExpectedTravelTime * remainingFraction
        }
        return formatDuration(secsToNextStop)
    }

    // MARK: - Regroup meet point

    /**
     * A coordinate [meters] further along the route from the rider's current position.
     *
     * Measured against [navRoute] — the route actually being followed (planned, or the reroute) —
     * and not against the trimmed active line, which is re-cut every GPS tick and, as progress
     * nears the end, collapses to `[projected, destination]`: walking forward along that would
     * overrun and clamp onto the destination.
     *
     * Falls back to a heading offset when there's no usable route.
     */
    fun coordinateAhead(meters: Double): LatLng? {
        val userLoc = _userLocation.value
        val route = if (navRoute.size >= 2) navRoute else _activeRouteCoordinates.value

        if (route.size < 2) {
            return userLoc?.let { coordinateFrom(it, _userHeading.value, meters) }
        }
        if (userLoc == null) return pointAlong(route, route[0], 0, meters)

        val (segIdx, projected) = nearestPointOnRoute(route, userLoc)
        return pointAlong(route, projected, segIdx, meters)
    }

    // MARK: - End ride

    fun endRide(onEnded: () -> Unit) {
        if (_isEnding.value) return
        _isEnding.value = true
        _endError.value = null
        viewModelScope.launch {
            try {
                apiCall { ServiceLocator.api.endRide(rideId) }
                onEnded()
            } catch (e: Exception) {
                _endError.value = e.riderMessage
            } finally {
                _isEnding.value = false
            }
        }
    }

    fun clearEndError() { _endError.value = null }

    // MARK: - Emergency naming

    fun emergencyReporterName(emergency: SocketEmergency): String {
        if (emergency.userId == myUserId) return "You"
        return _leaderboardRows.value.firstOrNull { it.id == emergency.userId }?.name
            ?: staticParticipants.firstOrNull { it.userId == emergency.userId }?.name
            ?: "A rider"
    }

    /** Only the rider who raised the emergency or the ride leader may clear it (the server enforces
     *  the same rule); everyone else sees the persistent pin + siren until one of them dismisses. */
    fun canDismissEmergency(emergency: SocketEmergency): Boolean =
        emergency.userId == myUserId || amILeader

    /**
     * Live rider count for the top-bar pill.
     *
     * Uses the SAME presence rule as the leaderboard rows (self is always live; every other rider
     * must have a current heartbeat), so the number can never disagree with the greyed-out rows.
     * Derived from the roster + presence rather than the fluctuating ride:state_update participant
     * array, which only carries riders that have posted a location tick.
     */
    fun liveRiderCount(onlineUserIds: Set<String>): Int =
        _leaderboardRows.value.count { it.isMe || onlineUserIds.contains(it.id) }
}

/** "45m", "1h", "2h 15m" — hours once ≥ 60 min, minutes below. */
fun formatDuration(seconds: Double): String {
    val totalMins = max(0, Math.round(seconds / 60).toInt())
    if (totalMins < 60) return "${totalMins}m"
    val h = totalMins / 60
    val m = totalMins % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
