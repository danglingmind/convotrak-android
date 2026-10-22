package com.danglingmind.convotrack.ui.ride

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danglingmind.convotrack.data.audio.SirenPlayer
import com.danglingmind.convotrack.data.audio.NavVoiceGuide
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import com.danglingmind.convotrack.data.audio.VoicePriority
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.danglingmind.convotrack.data.net.LiveLeaderboardEntry
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RegroupEvent
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
import kotlin.math.abs
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
    /** False for a row seeded from the roster: that rider has not posted a location yet, so their
     *  `distanceToGoalKm` is the full route length and says nothing about where they are. */
    val hasPosition: Boolean = true,
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
    private val _hasSpeedFix = MutableStateFlow(false)
    val hasSpeedFix: StateFlow<Boolean> = _hasSpeedFix.asStateFlow()

    /**
     * Whether the rider is actually moving, independent of the display band.
     *
     * Separate from `mySpeedKmh > 0` because that band flattens everything under 4 km/h to zero to
     * stop a parked bike's readout flickering — a display concern. Using it to decide whether the
     * map keeps up with someone crawling through traffic silently raised the follow threshold to
     * 4 km/h, so between 2 and 4 the map stopped re-centring and a rider who had panned never had
     * free-look auto-cleared: the viewport just froze behind them.
     */
    private val _isMoving = MutableStateFlow(false)
    val isMoving: StateFlow<Boolean> = _isMoving.asStateFlow()

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

    private val voiceGuide = NavVoiceGuide()
    private var destinationName = ""

    /** The maneuver after the one on the banner, set only when it follows within ~150 m. */
    private val _thenManeuver = MutableStateFlow<String?>(null)
    val thenManeuver: StateFlow<String?> = _thenManeuver.asStateFlow()

    private val _isOffRoute = MutableStateFlow(false)
    val isOffRoute: StateFlow<Boolean> = _isOffRoute.asStateFlow()

    /** Flags the rider as off route. Shown as the turn card's status line only, not spoken. */
    private fun markOffRoute() {
        if (_isOffRoute.value) return
        _isOffRoute.value = true
    }

    init {
        // Switching voice back on repeats the next maneuver, so the rider hears that it took.
        viewModelScope.launch {
            var wasMuted = VoiceAnnouncer.muted.value
            VoiceAnnouncer.muted.collect { muted ->
                if (wasMuted && !muted) repeatInstruction()
                wasMuted = muted
            }
        }
    }

    override fun onCleared() {
        VoiceAnnouncer.stop()
        voiceGuide.reset()
        super.onCleared()
    }

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
    private val _timeLeftSeconds = MutableStateFlow<Double?>(null)
    /** Remaining travel time to the next stop, in seconds; null until there is enough to estimate. */
    val timeLeftSeconds: StateFlow<Double?> = _timeLeftSeconds.asStateFlow()

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

    /** Forward-only progress index into [navRoute], except when [locateAnywhereOnRoute] re-acquires. */
    private var navSegmentIndex = 0

    /**
     * Where the rider sits on [navRoute], as of the last fix — segment, foot of the perpendicular,
     * and offset.
     *
     * ONE search per fix, consumed by the off-route test, the turn banner's progress, the polyline
     * trim and distance-to-goal alike. Those four used to run their own searches over three
     * different windows (and, in the trim's case, over a single segment with no search at all),
     * which is how the drawn line came to disagree with everything else about where the rider was.
     */
    private var navLocation: RouteLocation? = null

    /** Perpendicular distance from the last fix to [navRoute]. The undebounced truth behind
     *  [isOffRoute], and what gates polyline trimming. */
    private var lastRouteOffsetMeters = Double.MAX_VALUE

    /**
     * This fix's raw verdict from [isAgainstRouteDirection] — the rider is sitting on the line but
     * pointed back down it. Undebounced on purpose: it gates the polyline trim and the road-bearing
     * override, both of which must stop the moment the direction disagrees. Snapping a chevron to
     * the road's bearing while the rider drives the other way is what makes the marker flip between
     * the way they are going and the way the route goes, once per fix.
     */
    private var isWrongWayFix = false
    private var wrongWayCount = 0

    /** Along-route progress at the previous fix, for the regression half of the wrong-way test. */
    private var previousProgressMeters: Double? = null

    /** [isWrongWayFix] held for [WRONG_WAY_CONFIRM_COUNT] consecutive fixes — the point at which
     *  the route is treated as abandoned and a reroute is asked for. */
    private var isWrongWay = false

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

    /** Anchor for the displacement fallback in [updateMovementState]. Held only while speed-less
     *  fixes are arriving. */
    private var movementAnchor: LatLng? = null
    private var movementAnchorAtMs = 0L

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

        /** Speed at which the rider counts as moving. Deliberately BELOW the stationary band:
         *  that band exists to steady the readout, not to decide whether the camera follows. */
        const val MOVING_SPEED_KMH = 2.0

        /** Displacement that counts as movement when a fix carries no speed solution. Well past
         *  the few metres a stationary fix wanders by, so a parked bike can't fake it — a
         *  jitter-derived km/h on a parked bike routinely clears 2 km/h and would yank the camera
         *  back from a rider who deliberately panned. */
        const val MOVING_DISPLACEMENT_METERS = 15.0

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
        this.destinationName = ride.destinationName
        this.totalDistanceMeters = ride.distanceMeters

        seedGeometry(ride.waypoints)

        // Pre-populate the leaderboard so it shows immediately, before any socket updates.
        val active = ride.participants.filter { it.status != "LEFT" }
        _leaderboardRows.value = active.mapIndexed { index, p ->
            NavLeaderboardRow(
                id = p.userId, rank = index + 1, name = p.name, avatarUrl = p.avatarUrl,
                distanceToGoalKm = ride.distanceMeters / 1000, isMe = p.userId == myUserId,
                hasPosition = false,
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
     * rider leaving inside that window would otherwise stay in everyone else's leaderboard,
     * map pins and off-screen indicators until somebody's GPS moved.
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
                        hasPosition = false,
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
        VoiceAnnouncer.announce("Ride updated, rerouting", delayMs = SirenPlayer.Sound.REGROUP.durationMs + 200)

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
        _timeLeftSeconds.value = computeTimeLeftSeconds()
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
        navLocation = null
        // The verdict belonged to the route we just abandoned; the new one starts at the rider,
        // and progress is measured in a new frame, so the previous figure means nothing here.
        isWrongWayFix = false
        wrongWayCount = 0
        isWrongWay = false
        previousProgressMeters = null
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
        val displaySpeed = displaySpeedKmh(location)
        // Nil means the fix carries no speed solution at all — the readout shows "--", not a
        // confident "0 KM/H".
        _mySpeedKmh.value = displaySpeed ?: 0.0
        _hasSpeedFix.value = displaySpeed != null
        updateMovementState(location, fix)
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
        updateCurrentStep()
        refreshEtaIfNeeded(fix)

        if (needsInitialRouteCheck && navRoute.size >= 2) {
            needsInitialRouteCheck = false
            if (lastRouteOffsetMeters > OFF_ROUTE_THRESHOLD_METERS && !isRerouteInFlight) {
                markOffRoute()
                consecutiveOffCount = OFF_ROUTE_CONFIRM_COUNT
                launchReroute(fix, validCourse(location))
            }
        }

        // Gated on the MEASURED offset, not on the debounced isOffRoute flag. Clearing that flag
        // takes four consecutive on-route readings, so for ~4 s after a reroute landed — or after
        // a jitter spike cleared — the head would stay frozen wherever it was last cut while the
        // rider drove on, detaching it from the marker for exactly as long as the debounce ran.
        // Proximity to the route we are following is the real precondition for trimming it.
        //
        // Direction gates it too, not just the offset. A rider riding the route backwards is within
        // the offset by definition, and trimming to them re-cuts the bright line backwards fix by
        // fix while the road-bearing override spins the chevron to face the way the route goes
        // rather than the way they are going — the two fighting over the marker once per fix.
        // Freezing the line where it was, exactly as off-route does, leaves the marker on the
        // honest raw fix until the reroute lands.
        val navLoc = navLocation
        if (navLoc != null && lastRouteOffsetMeters <= OFF_ROUTE_THRESHOLD_METERS && !isWrongWayFix) {
            val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
            trimActiveRoute(navRoute, fix, navLoc, speedMps, COURSE_VALID_SPEED_MPS)
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

        // Drive DIST from the locally-measured position on the route rather than the server's
        // `progress`. The server value is a nearest-point projection that reads ~full (zeroing
        // distance to goal) whenever the rider isn't cleanly on-route.
        //
        // Taken from the same [navLocation] the polyline was cut with — total route length minus
        // progress — so the readout cannot disagree with the drawn line. Measuring the trimmed
        // polyline's own length instead (the previous approach) ran a second, independent
        // full-route nearest-point search per fix and went stale for as long as trimming was
        // gated off.
        val navLength = navRouteCumulative.lastOrNull() ?: 0.0
        val located = navLocation
        if (located != null && navLength > 0) {
            val progress = progressAlongRoute(navRouteCumulative, navRoute, located)
            _myDistanceToGoalKm.value = max(0.0, navLength - progress) / 1000
        } else {
            val active = _activeRouteCoordinates.value
            if (active.size >= 2) _myDistanceToGoalKm.value = remainingRouteLength(active, fix) / 1000
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
    private fun displaySpeedKmh(location: Location): Double? {
        if (!location.hasSpeed() || location.speed < 0) return null
        val kmh = location.speed * 3.6
        return if (kmh < STATIONARY_SPEED_KMH) 0.0 else kmh
    }

    /** Refreshes [isMoving] from whichever signal this fix carries. */
    private fun updateMovementState(location: Location, fix: LatLng) {
        if (location.hasSpeed() && location.speed >= 0) {
            movementAnchor = null
            _isMoving.value = location.speed * 3.6 >= MOVING_SPEED_KMH
            return
        }
        // No speed solution. Reading that as stopped left the follow camera parked for the whole
        // ride on any fix source that omits speed, so fall back to displacement, which needs none.
        val anchor = movementAnchor
        if (anchor == null) {
            movementAnchor = fix
            movementAnchorAtMs = System.currentTimeMillis()
            return
        }
        val moved = metersBetween(anchor, fix)
        if (moved >= MOVING_DISPLACEMENT_METERS) {
            _isMoving.value = true
            movementAnchor = fix
            movementAnchorAtMs = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - movementAnchorAtMs > 10_000) {
            // Held under the bar for long enough that this is a genuine stop, not a slow crawl.
            _isMoving.value = false
            movementAnchor = fix
            movementAnchorAtMs = System.currentTimeMillis()
        }
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
    private fun updateCurrentStep() {
        if (navSteps.isEmpty()) return
        val location = navLocation ?: return
        val progress = progressAlongRoute(navRouteCumulative, navRoute, location)

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
        _thenManeuver.value = banner.thenManeuver
        speakNextManeuver(banner.thenManeuver)
    }

    /**
     * Feeds the voice guide the maneuver being approached. A new route (reroute, edited ride) or a
     * new step starts a fresh set of prompts.
     */
    private fun speakNextManeuver(then: String?) {
        val instruction = _currentInstruction.value
        if (instruction.isEmpty()) return
        val sentence = voiceGuide.update(
            NavVoiceGuide.Input(
                key = _routeVersion.value * 10_000 + currentStepIndex,
                distanceMeters = _distanceToNextTurnMeters.value,
                speedKmh = _mySpeedKmh.value,
                instruction = instruction,
                maneuver = _currentManeuver.value,
                thenManeuver = then,
                destinationName = destinationName,
            ),
        ) ?: return
        VoiceAnnouncer.announce(sentence, priority = VoicePriority.TURN)
    }

    /** Voice was just switched back on: say where the next turn is so the rider knows it worked. */
    private fun repeatInstruction() {
        voiceGuide.repeatCurrent()?.let { VoiceAnnouncer.announce(it, priority = VoicePriority.TURN) }
    }

    /**
     * Finds the rider on [navRoute] and publishes the result into [navLocation],
     * [navSegmentIndex] and [lastRouteOffsetMeters].
     *
     * Two passes. The windowed one is the normal case and keeps progress monotone, which is what
     * stops a noisy fix from throwing the index onto the other carriageway of a divided road or
     * the return leg of an out-and-back. The full-route pass runs only once the window has failed
     * to put the rider on the road at all, and its result is taken only if it does — that is the
     * recovery the old code had no equivalent of, so a single GPS gap wider than the fixed
     * 200-segment window froze the index for the remainder of the ride and left the bright line
     * anchored wherever it had last been cut.
     */
    private fun locateOnNavRoute(fix: LatLng) {
        val windowed = locateOnRoute(navRoute, navRouteCumulative, fix, navSegmentIndex)
        val accepted = when {
            windowed != null && windowed.distanceMeters <= OFF_ROUTE_THRESHOLD_METERS -> windowed
            else -> reacquireOnNavRoute(fix) ?: windowed
        } ?: return

        // Detection always sees the fresh measurement...
        lastRouteOffsetMeters = accepted.distanceMeters
        // ...but progress is committed only while the rider is genuinely on the road. A fix that
        // has left the route still has a nearest point on it, and that point runs forward along
        // the line as the rider drives away parallel to it — committing it would walk the index
        // (and with it the turn banner and distance-to-goal) down a road nobody is on, and on a
        // ride with no stops left to reroute through it would eventually report arrival. Holding
        // the last on-route position is also what the frozen polyline already implies.
        if (accepted.distanceMeters <= OFF_ROUTE_THRESHOLD_METERS) {
            navLocation = accepted
            navSegmentIndex = accepted.segmentIndex
        }
    }

    /**
     * The full-route re-acquire, accepted only when it lands AHEAD of the progress already
     * committed.
     *
     * The re-acquire exists for one failure: a gap in the fixes — a tunnel, a doze window, the app
     * coming back from the background — carrying the rider past the end of the windowed search.
     * That gap always moves them FORWARD. A match found behind the committed progress is therefore
     * never the case this is for; it is the rider riding back down the route, and taking it is what
     * made that invisible. The lateral offset it reports is a couple of metres — the rider is
     * exactly on the road, just facing the other way — so [checkOffRoute] saw a rider comfortably
     * on-route, never rerouted, and the index walked backwards down the line while the turn banner
     * (forward-only by construction) stayed where it was.
     *
     * Refusing it leaves the windowed distance standing, which grows as the rider rides away from
     * the window's tail, so the ordinary off-route test trips within a hundred-odd metres and the
     * reroute follows. That is the second net under [isWrongWay], and the one that still works when
     * the fixes carry no usable course at all — crawling in traffic, or a GPS with no bearing.
     *
     * [ROUTE_LOOKBEHIND_METERS] of slack, the same the windowed search allows, so jitter around a
     * vertex is not mistaken for a reversal.
     */
    private fun reacquireOnNavRoute(fix: LatLng): RouteLocation? {
        val candidate = locateAnywhereOnRoute(navRoute, fix) ?: return null
        if (candidate.distanceMeters > OFF_ROUTE_THRESHOLD_METERS) return null
        val here = navLocation ?: return candidate
        val committed = progressAlongRoute(navRouteCumulative, navRoute, here)
        val found = progressAlongRoute(navRouteCumulative, navRoute, candidate)
        return if (found >= committed - ROUTE_LOOKBEHIND_METERS) candidate else null
    }

    /**
     * Refreshes [isWrongWayFix], [wrongWayCount] and [isWrongWay] from this fix.
     *
     * Only asked while the rider is laterally ON the followed route: [navLocation] holds the last
     * on-route position, so once they have left the line it describes a stretch of road they are no
     * longer on, and the bearing there says nothing about where they are pointed now. Off the line
     * the distance test already has them anyway — this is the case only it can see, a rider dead on
     * the route and going the other way.
     */
    private fun updateWrongWay(location: Location) {
        val here = navLocation
        val course = validCourse(location)
        val progress = here?.let { progressAlongRoute(navRouteCumulative, navRoute, it) }
        val previous = previousProgressMeters
        if (progress != null) previousProgressMeters = progress

        // Corroborated by the rider's own progress falling, which is the other half of what
        // "backwards" means and what tells this apart from a projection that has momentarily
        // latched onto the far side of a route that doubles back on itself: such a flip jumps
        // progress FORWARD, never back. A metre of slack absorbs the jitter of a fix that is
        // barely moving.
        val regressed = progress != null && previous != null && progress < previous - 1.0

        val against = here != null && course != null && progress != null && regressed &&
            lastRouteOffsetMeters <= OFF_ROUTE_THRESHOLD_METERS &&
            !isNearUTurn(navSteps, progress) &&
            isAgainstRouteDirection(navRoute, here, course)

        isWrongWayFix = against
        wrongWayCount = if (against) wrongWayCount + 1 else 0
        isWrongWay = wrongWayCount >= WRONG_WAY_CONFIRM_COUNT
    }

    /** The fix's course, or null when it carries none worth believing. A bearing derived from
     *  successive fixes wanders wildly below a walking pace. */
    private fun validCourse(location: Location): Double? =
        if (location.hasBearing() && location.hasSpeed() && location.speed >= COURSE_VALID_SPEED_MPS) {
            location.bearing.toDouble()
        } else {
            null
        }

    private fun checkOffRoute(fix: LatLng, location: Location) {
        // Pop stops we've already passed (within 80 m).
        while (remainingStops.isNotEmpty() && metersBetween(fix, remainingStops.first()) < 80) {
            remainingStops = remainingStops.drop(1)
        }

        if (navRoute.size < 2) return

        locateOnNavRoute(fix)
        updateWrongWay(location)
        // Two ways to have left the route, and the second is invisible to the first. Distance
        // answers "am I on this road"; [isWrongWay] answers "am I going down it" — riding the
        // planned road in reverse holds the offset at a few metres indefinitely, so the distance
        // test alone waits for the rider to stray sideways before it admits anything is wrong.
        val rawOffRoute = lastRouteOffsetMeters > OFF_ROUTE_THRESHOLD_METERS || isWrongWay

        if (rawOffRoute) {
            consecutiveOffCount++
            consecutiveOnCount = 0
        } else {
            consecutiveOnCount++
            consecutiveOffCount = 0
        }

        // [isWrongWay] carries its own confirmation count, so it flags immediately rather than
        // waiting out a second three-fix debounce on top of the one it has already served.
        if (!_isOffRoute.value && (isWrongWay || consecutiveOffCount >= OFF_ROUTE_CONFIRM_COUNT)) {
            markOffRoute()
        } else if (_isOffRoute.value && !isWrongWay && consecutiveOnCount >= ON_ROUTE_CLEAR_COUNT) {
            _isOffRoute.value = false
        }

        if (_isOffRoute.value) {
            if (isRerouteInFlight) return
            // Throttle: only recalculate every 200 m of travel while off-route.
            val last = lastRerouteOrigin
            if (last != null && metersBetween(fix, last) < REROUTE_TRAVEL_THRESHOLD_M) return
            launchReroute(fix, validCourse(location))
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
     *
     * [originHeading] is the rider's own direction of travel, handed to the Routes API so the
     * reroute leaves from the side of the road they are actually on. Without it a reroute asked for
     * while riding the wrong way comes back running straight back down the road the rider has just
     * come along, as if they could reverse — an instruction nobody can follow, which puts them
     * against the new route on the very next fix and rerouting again 200 m later. With it, Google
     * returns the U-turn.
     */
    private fun launchReroute(origin: LatLng, originHeading: Double?) {
        if (remainingStops.isEmpty()) return
        isRerouteInFlight = true
        lastRerouteOrigin = origin
        rerouteJob?.cancel()
        rerouteJob = viewModelScope.launch {
            val result = calculateFullReroute(origin, remainingStops, originHeading)
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
        _timeLeftSeconds.value = computeTimeLeftSeconds()

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
            _timeLeftSeconds.value = computeTimeLeftSeconds()
        }
    }

    /**
     * Remaining travel time to the NEXT stop, in seconds.
     *
     * The live traffic-aware value wins whenever we have one; the planned-duration estimate below
     * is only the bootstrap for the seconds before the first refresh lands.
     */
    private fun computeTimeLeftSeconds(): Double? {
        _etaSecondsRemaining.value?.let { return it }
        if (routeExpectedTravelTime <= 0 || totalDistanceMeters <= 0) return null
        val remainingKm = _myDistanceToGoalKm.value
        if (remainingKm < 0) return null
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
        return secsToNextStop
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

    /** False once the rider has driven past the stop at [stop]. */
    fun isStopAhead(stop: LatLng): Boolean =
        remainingStops.any { abs(it.latitude - stop.latitude) < 1e-6 && abs(it.longitude - stop.longitude) < 1e-6 }

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

    /** Only the rider who called the regroup or the ride leader may clear it early (the server
     *  enforces the same rule); it otherwise stays until every active rider has arrived. */
    fun canDismissRegroup(regroup: RegroupEvent): Boolean =
        regroup.createdBy == myUserId || amILeader
}

/**
 * "45m", "1h", "2h 15m" — hours once ≥ 60 min, minutes below.
 *
 * Deliberately NOT [com.danglingmind.convotrack.ui.common.RideDuration]: this is remaining travel
 * time in the live HUD, which is read at a glance at road speed and wants a compact "1h 15m".
 * RideDuration renders a finished ride's total, where "1:15 H:MM" disambiguates a value that sits
 * next to distances and speeds. Same arithmetic, different job — sharing one would force one of the
 * two surfaces into the wrong shape.
 */
fun formatRemainingTime(seconds: Double): String {
    val totalMins = max(0, Math.round(seconds / 60).toInt())
    if (totalMins < 60) return "${totalMins}m"
    val h = totalMins / 60
    val m = totalMins % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
