package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.ui.map.EdgeKind
import com.danglingmind.convotrack.ui.map.EdgeTarget
import com.danglingmind.convotrack.data.audio.NavVoiceGuide
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import com.danglingmind.convotrack.data.audio.VoicePriority
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.DsBottomSheet
import com.danglingmind.convotrack.ui.designsystem.DsColor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danglingmind.convotrack.data.audio.SirenPlayer
import com.danglingmind.convotrack.data.maps.AvatarCache
import com.danglingmind.convotrack.data.socket.SocketEmergency
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.LocationDeniedDialog
import com.danglingmind.convotrack.ui.common.LocationDisclosureDialog
import com.danglingmind.convotrack.ui.map.EdgeChipModel
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.MapFraming
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.danglingmind.convotrack.ui.map.NavCamera
import com.danglingmind.convotrack.ui.map.OffscreenIndicatorsOverlay
import com.danglingmind.convotrack.ui.map.UserPuck
import com.danglingmind.convotrack.ui.map.clusterIndicators
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live navigation.
 *
 * Two phases: a **route preview** (whole route framed, destination + ETA, START NAVIGATION), then
 * **guidance** (follow camera, turn banner, leaderboard, stat bar). Landscape moves the entire HUD
 * into a right-hand side panel and gives the map the remaining two thirds — landscape has ~390dp
 * of height and ~850dp of width, so a full-width bottom bar spends the scarce dimension and wastes
 * the plentiful one.
 *
 * All realtime state comes from the graph-scoped [RideSessionViewModel]; everything derived from a
 * GPS fix comes from [NavigationViewModel]. This composable is arrangement only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideNavigationScreen(
    rideId: String,
    sessionVm: RideSessionViewModel,
    onExit: () -> Unit,
    onEndRide: () -> Unit,
) {
    val auth = remember { ServiceLocator.auth }
    val locationController = remember { ServiceLocator.location }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val myId = auth.userState.value?.id.orEmpty()

    val vm: NavigationViewModel = viewModel()
    val session = sessionVm.session

    // --- Session state -------------------------------------------------------------------------
    val ride by sessionVm.ride.collectAsState()
    val liveState by session.liveState.collectAsState()
    val participants by session.participants.collectAsState()
    val onlineIds by session.onlineUserIds.collectAsState()
    val split by session.split.collectAsState()
    val sessionRegroup by session.latestRegroup.collectAsState()
    val sessionRegroupResolvedId by session.latestRegroupResolvedId.collectAsState()
    val sessionEmergency by session.latestEmergency.collectAsState()
    val sessionEmergencyResolvedId by session.latestEmergencyResolvedId.collectAsState()
    val sessionRideUpdated by session.rideUpdated.collectAsState()
    val wasRemoved by session.wasRemoved.collectAsState()
    val wasDeleted by session.wasDeleted.collectAsState()
    val connectionState by session.connectionState.collectAsState()

    // --- Navigation state ----------------------------------------------------------------------
    val riders by vm.riders.collectAsState()
    val leaderboardRows by vm.leaderboardRows.collectAsState()
    val thenManeuver by vm.thenManeuver.collectAsState()
    val mySpeedKmh by vm.mySpeedKmh.collectAsState()
    val hasSpeedFix by vm.hasSpeedFix.collectAsState()
    val isRiderMoving by vm.isMoving.collectAsState()
    val myDistanceKm by vm.myDistanceToGoalKm.collectAsState()
    val routeCoords by vm.routeCoordinates.collectAsState()
    val activeRoute by vm.activeRouteCoordinates.collectAsState()
    val routeVersion by vm.routeVersion.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val userHeading by vm.userHeading.collectAsState()
    val locationTick by vm.locationTick.collectAsState()
    val instruction by vm.currentInstruction.collectAsState()
    val maneuver by vm.currentManeuver.collectAsState()
    val distToTurn by vm.distanceToNextTurnMeters.collectAsState()
    val isOffRoute by vm.isOffRoute.collectAsState()
    val isEnding by vm.isEnding.collectAsState()
    val endError by vm.endError.collectAsState()
    val rideCompleted by vm.rideCompleted.collectAsState()
    val timeLeftSeconds by vm.timeLeftSeconds.collectAsState()
    // Clock arrival, in the system's 12/24-hour setting so it matches the status bar.
    val is24h = android.text.format.DateFormat.is24HourFormat(ctx)
    val arrival = timeLeftSeconds?.let { arrivalFor(it, is24h) }
    val destination by vm.destinationCoordinate.collectAsState()
    val middleWaypoints by vm.middleWaypoints.collectAsState()

    val myLoc by locationController.current.collectAsState()
    val compassHeading by locationController.compassHeading.collectAsState()

    // --- Local UI state ------------------------------------------------------------------------
    // rememberSaveable, not remember: rotating into (or out of) the landscape panel must not
    // drop a rider who is mid-ride back into the route preview.
    var isNavigationActive by rememberSaveable { mutableStateOf(false) }
    var isFreeLooking by remember { mutableStateOf(false) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }
    var selectedRiderId by remember { mutableStateOf<String?>(null) }
    var showRegroup by remember { mutableStateOf(false) }
    var isBroadcasting by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var showEndError by remember { mutableStateOf(false) }

    var activeRegroup by remember(rideId) { mutableStateOf<com.danglingmind.convotrack.data.net.RegroupEvent?>(null) }
    var hasMarkedArrived by remember(rideId) { mutableStateOf(false) }
    var showRegroupResolved by remember(rideId) { mutableStateOf(false) }
    var activeEmergency by remember(rideId) { mutableStateOf<SocketEmergency?>(null) }
    var splitAlertDismissed by remember(rideId) { mutableStateOf(false) }
    // Real rider + gap for the split alert; hidden once dismissed. The alert slot, not this
    // flag, decides what shows first when a regroup or an emergency is live too.
    val splitAlert = split
        ?.takeIf { !splitAlertDismissed }
        ?.let { splitAlertInfo(it, leaderboardRows, participants, myId) }

    // Which transient events this screen has already acted on. The session holds each as state, so
    // it replays on every re-entry and recomposition — without these, walking back into navigation
    // from the lobby would re-fire the siren for a regroup called ten minutes ago.
    var handledRegroupId by remember(rideId) { mutableStateOf<String?>(null) }
    var handledEmergencyId by remember(rideId) { mutableStateOf<String?>(null) }
    var handledRideUpdateKey by remember(rideId) { mutableStateOf<String?>(null) }

    // Camera bookkeeping.
    var cameraCommand by remember { mutableStateOf<MapCamera>(MapCamera.Free()) }
    var cameraCommandId by remember { mutableLongStateOf(0L) }
    var lastCameraCoord by remember { mutableStateOf<LatLng?>(null) }
    var lastCameraHeading by remember { mutableFloatStateOf(0f) }
    // Low-passed speed driving the camera zoom. Raw GPS speed swings several km/h between fixes;
    // fed straight into a continuous zoom curve it makes the map breathe. The SPEED readout still
    // shows the raw value.
    var cameraSpeedKmh by remember { mutableFloatStateOf(0f) }

    // Measured chrome, so the map and the edge indicators know what is covering them.
    var topChromeHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    var panelWidthPx by remember { mutableStateOf(0) }
    var edgeIndicators by remember { mutableStateOf<List<com.danglingmind.convotrack.ui.map.EdgeIndicator>>(emptyList()) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val reduceMotion = com.danglingmind.convotrack.ui.designsystem.rememberReduceMotion()

    fun issueCamera(build: (Long) -> MapCamera) {
        cameraCommandId += 1
        val command = build(cameraCommandId)
        // "Remove animations": every camera move (follow, resume, peek, focus, fit) jumps instead.
        cameraCommand = if (!reduceMotion) command else when (command) {
            is MapCamera.Navigate -> command.copy(animated = false)
            is MapCamera.Fit -> command.copy(animated = false)
            else -> command
        }
    }

    // Zoom for the guidance camera, pulled back slightly when a maneuver is approaching so the
    // upcoming road is revealed before the rider reaches it.
    fun navZoom(): Float {
        val base = NavCamera.zoomForSpeed(cameraSpeedKmh.toDouble())
        // Scales with speed so fast riders see the maneuver framed sooner: ~7s of travel, floor 140 m.
        val approachMeters = maxOf(140.0, cameraSpeedKmh / 3.6 * 7)
        // Half a level; a full level reads as the map lurching.
        return if (vm.hasUpcomingTurn && distToTurn <= approachMeters) base - 0.5f else base
    }

    fun resumeNavigation() {
        isFreeLooking = false
        lastInteractionAt = 0L
        val coord = userLocation ?: return
        issueCamera { id ->
            MapCamera.Navigate(id, coord, navZoom(), userHeading.toFloat(), NavCamera.TILT, true, MapFraming.ROAD_AHEAD)
        }
    }

    // --- Setup ---------------------------------------------------------------------------------

    LaunchedEffect(rideId, myId) { sessionVm.load(myId) }

    // Hand the ride to the navigation VM once, when it lands.
    var didSetup by remember(rideId) { mutableStateOf(false) }
    LaunchedEffect(ride, myId) {
        val r = ride ?: return@LaunchedEffect
        if (didSetup) return@LaunchedEffect
        didSetup = true
        vm.setup(rideId, r, myId, session)
    }

    LaunchedEffect(participants) { vm.onRosterChanged(participants) }
    LaunchedEffect(liveState) { liveState?.let { vm.handleStateUpdate(it) } }
    LaunchedEffect(rideCompleted) { if (rideCompleted) onEndRide() }
    LaunchedEffect(endError) { showEndError = endError != null }

    // Location permission → broadcast my position (foreground service) + compass.
    //
    // The system prompt is never the first thing the rider sees. It describes none of what
    // actually happens to the fix — that it is broadcast to the other riders, that it reaches our
    // server, that it carries on with the screen off — so a disclosure saying all three comes
    // first, and the prompt is launched from its accept button. Declining is a supported outcome
    // rather than a dead end: the ride still opens, the rider is simply not on anyone's map.
    var showLocationDisclosure by remember(rideId) { mutableStateOf(false) }
    var showLocationDenied by remember(rideId) { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationController.start(ctx, rideId)
        } else {
            showLocationDenied = true
        }
    }
    LaunchedEffect(rideId) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            locationController.start(ctx, rideId)
        } else {
            showLocationDisclosure = true
        }
        locationController.startCompass(ctx, this)
    }

    if (showLocationDisclosure) {
        LocationDisclosureDialog(
            onAccept = {
                showLocationDisclosure = false
                permLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
                )
            },
            onDecline = {
                showLocationDisclosure = false
                showLocationDenied = true
            },
        )
    }

    if (showLocationDenied) {
        LocationDeniedDialog(onDismiss = { showLocationDenied = false })
    }

    // Granting the permission in system settings is a round trip out of the app, and the OS does
    // not restart the screen on the way back — so without this the rider does everything asked of
    // them, returns, and is still invisible to the pack until they leave the ride and re-enter it.
    // Re-checking on resume is what makes "Open Settings" an actual fix rather than a suggestion.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, rideId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted && !locationController.active.value) {
                showLocationDenied = false
                locationController.start(ctx, rideId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the screen awake and allow landscape while navigating; both are restored on the way out.
    DisposableEffect(Unit) {
        val activity = ctx as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            previousOrientation?.let { activity.requestedOrientation = it }
            locationController.stopCompass()
            locationController.stop(ctx)
        }
    }

    // Every accepted fix drives the whole navigation model.
    LaunchedEffect(myLoc) { myLoc?.let { vm.onLocationUpdate(it, compassHeading) } }

    // --- Transient events ----------------------------------------------------------------------

    fun startRegroup(ev: com.danglingmind.convotrack.data.net.RegroupEvent, isMine: Boolean) {
        activeRegroup = ev
        hasMarkedArrived = false
        // The camera stays on the road: the banner's Show action and the edge chip let the rider
        // look at the meet point when they choose to. Riders who didn't broadcast it get the
        // two-tone siren, clearly distinct from the emergency wail; the initiator already knows.
        if (!isMine) {
            SirenPlayer.play(SirenPlayer.Sound.REGROUP)
            val stop = when (ev.type) {
                "FUEL" -> "the fuel stop"
                "FOOD" -> "the food stop"
                "SCENIC" -> "the scenic stop"
                else -> "the meeting point"
            }
            VoiceAnnouncer.announce("Regroup called. Head to $stop.", delayMs = 1_800, priority = VoicePriority.ALERT)
        }
    }

    LaunchedEffect(sessionRegroup) {
        val ev = sessionRegroup ?: return@LaunchedEffect
        if (handledRegroupId == ev.regroupId) return@LaunchedEffect
        handledRegroupId = ev.regroupId
        startRegroup(ev, ev.createdBy == myId)
    }
    LaunchedEffect(sessionRegroupResolvedId) {
        val id = sessionRegroupResolvedId ?: return@LaunchedEffect
        if (activeRegroup?.regroupId != id) return@LaunchedEffect
        activeRegroup = null
        hasMarkedArrived = false
        showRegroupResolved = true
    }
    LaunchedEffect(showRegroupResolved) { if (showRegroupResolved) { delay(2_500); showRegroupResolved = false } }

    LaunchedEffect(sessionEmergency) {
        val ev = sessionEmergency ?: return@LaunchedEffect
        if (handledEmergencyId == ev.emergencyId) return@LaunchedEffect
        handledEmergencyId = ev.emergencyId
        activeEmergency = ev
        VoiceAnnouncer.announce(
            "Emergency. ${vm.emergencyReporterName(ev)} needs help.",
            delayMs = 1_800,
            priority = VoicePriority.EMERGENCY,
        )
    }
    // Said once per split, whether or not the banner is showing: a rider who dismissed it, or
    // whose banner is hidden behind a regroup, should still hear that the pack has spread out.
    LaunchedEffect(split) {
        val ev = split ?: return@LaunchedEffect
        val info = splitAlertInfo(ev, leaderboardRows, participants, myId)
        VoiceAnnouncer.announce(
            "Group split. ${info.riderName} is ${NavVoiceGuide.spokenDistance(info.gapMeters)} behind the leader.",
            priority = VoicePriority.ALERT,
        )
    }
    LaunchedEffect(sessionEmergencyResolvedId) {
        val id = sessionEmergencyResolvedId ?: return@LaunchedEffect
        if (activeEmergency?.emergencyId == id) activeEmergency = null
    }
    // An unresolved emergency keeps nagging the whole group: siren every 10s until the raiser or
    // the leader dismisses it (clearing activeEmergency cancels this effect).
    LaunchedEffect(activeEmergency?.emergencyId) {
        if (activeEmergency == null) return@LaunchedEffect
        while (true) {
            SirenPlayer.play(SirenPlayer.Sound.EMERGENCY)
            delay(10_000)
        }
    }

    // Also adopt any regroup / emergency already open when we joined, from the state snapshot.
    LaunchedEffect(liveState) {
        val update = liveState ?: return@LaunchedEffect
        if (activeRegroup == null) {
            update.openRegroup?.let {
                if (handledRegroupId != it.regroupId) {
                    handledRegroupId = it.regroupId
                    startRegroup(it, it.createdBy == myId)
                }
            }
        }
        if (activeEmergency == null) {
            update.openEmergency?.let {
                if (handledEmergencyId != it.emergencyId) {
                    handledEmergencyId = it.emergencyId
                    activeEmergency = it
                }
            }
        }
    }

    // Leader edited the ride mid-flight → re-seed geometry and reroute live.
    LaunchedEffect(sessionRideUpdated) {
        val ev = sessionRideUpdated ?: return@LaunchedEffect
        val key = "${ev.rideId}|${ev.distanceMeters}|${ev.waypoints.size}|${ev.routePolyline}"
        if (handledRideUpdateKey == key) return@LaunchedEffect
        handledRideUpdateKey = key
        val current = ride ?: return@LaunchedEffect
        val updated = current.copy(
            title = ev.title,
            destinationName = ev.destinationName,
            destinationLat = ev.destinationLat,
            destinationLng = ev.destinationLng,
            distanceMeters = ev.distanceMeters,
            routePolyline = ev.routePolyline,
            waypoints = ev.waypoints,
        )
        sessionVm.setRide(updated)
        vm.handleRideUpdated(ev, updated)
    }

    // The split gap is server state (and clears on ride:split_resolved), so "I dismissed this
    // alert" is tracked separately. Reset when the split resolves, so the next one alerts again.
    LaunchedEffect(split) { if (split == null) splitAlertDismissed = false }

    // Removed by the leader, or the ride deleted — either way there is nothing left to navigate.
    LaunchedEffect(wasRemoved, wasDeleted) { if (wasRemoved || wasDeleted) onExit() }

    // --- Camera ------------------------------------------------------------------------------

    // Preview: frame the whole route inside the band the chrome actually leaves visible. Keyed on
    // the measured chrome too — those read 0 until the first layout pass, so a route that resolved
    // before then was framed with no insets at all.
    LaunchedEffect(routeVersion, topChromeHeightPx, bottomBarHeightPx, isNavigationActive) {
        if (isNavigationActive) return@LaunchedEffect
        val coords = routeCoords.ifEmpty { activeRoute }
        if (coords.isEmpty()) {
            destination?.let { d -> issueCamera { id -> MapCamera.Navigate(id, d, 11f, 0f, 0f, true, MapFraming.CENTERED) } }
            return@LaunchedEffect
        }
        val topDp = with(density) { topChromeHeightPx.toDp().value } + NavCamera.STOP_PIN_TOP_EXTENT_DP + 8
        val bottomDp = maxOf(with(density) { bottomBarHeightPx.toDp().value }, 200f) + 8
        issueCamera { id -> MapCamera.Fit(id, coords, topDp = topDp, leftDp = 44f, bottomDp = bottomDp, rightDp = 44f) }
    }

    // Guidance: follow the rider, but only move when they've actually moved or turned enough.
    LaunchedEffect(locationTick) {
        if (!isNavigationActive) return@LaunchedEffect
        val coord = userLocation ?: return@LaunchedEffect
        val wasFirst = lastCameraCoord == null

        // Track speed on every tick — including the ones that don't move the camera — so the zoom
        // is already correct whenever a camera command does go out.
        cameraSpeedKmh = if (wasFirst) {
            mySpeedKmh.toFloat()
        } else {
            cameraSpeedKmh + 0.25f * (mySpeedKmh.toFloat() - cameraSpeedKmh)
        }

        // The real movement signal, not the display band. `mySpeedKmh > 0` reads as ">0" but means
        // ">4 km/h", so a rider creeping forward got no camera follow and no free-look clear.
        val isMoving = isRiderMoving
        val idleMs = System.currentTimeMillis() - lastInteractionAt
        val shouldFollow = wasFirst || (isMoving && idleMs >= 3_000)
        if (isFreeLooking && shouldFollow) isFreeLooking = false
        if (!shouldFollow) return@LaunchedEffect

        // Avoids re-animating on every GPS tick from jittery stationary fixes. The first fix always
        // snaps into place.
        if (!wasFirst) {
            val last = lastCameraCoord
            if (last != null) {
                val moved = metersBetween(last, coord)
                val turned = headingDelta(userHeading, lastCameraHeading.toDouble())
                if (moved < 5 && turned < 3) return@LaunchedEffect
            }
        }
        lastCameraCoord = coord
        lastCameraHeading = userHeading.toFloat()
        issueCamera { id ->
            MapCamera.Navigate(id, coord, navZoom(), userHeading.toFloat(), NavCamera.TILT, !wasFirst, MapFraming.ROAD_AHEAD)
        }
    }

    BackHandler { onExit() }

    // --- Derived display ------------------------------------------------------------------------

    val destinationName = ride?.destinationName ?: "Destination"

    val navMarkers: List<MapMarker> = remember(riders, middleWaypoints, destination, activeRegroup, activeEmergency, onlineIds, selectedRiderId, userLocation) {
        buildList {
            riders.filterNot { it.isMe }.forEach { rider ->
                val distText = userLocation?.let { me -> formatRiderDistance(metersBetween(me, rider.coordinate)) }
                add(
                    MapMarker(
                        id = rider.id,
                        position = rider.coordinate,
                        kind = MapMarkerKind.RIDER,
                        label = rider.name,
                        isOffline = !onlineIds.contains(rider.id),
                        isSelected = selectedRiderId == rider.id,
                        avatarUrl = rider.avatarUrl,
                        distanceText = distText,
                    ),
                )
            }
            middleWaypoints.forEach { wp ->
                // A stop already passed keeps its marker but loses its label.
                val at = LatLng(wp.lat, wp.lng)
                add(MapMarker("wp_${wp.order}", at, MapMarkerKind.WAYPOINT, wp.name.takeIf { vm.isStopAhead(at) }))
            }
            destination?.let { add(MapMarker("dest", it, MapMarkerKind.DESTINATION, destinationName)) }
            activeRegroup?.let {
                add(MapMarker("regroup", LatLng(it.lat, it.lng), MapMarkerKind.REGROUP, regroupType = it.type))
            }
            activeEmergency?.let {
                add(MapMarker("emergency", LatLng(it.lat, it.lng), MapMarkerKind.EMERGENCY))
            }
        }
    }

    // Avatars for the riders currently off-screen. Read from the shared cache the map markers
    // already populate, so a chip shows the same photo as the pin without a second download; a
    // miss just falls back to the initial until the fetch lands.
    val edgeAvatars = remember { mutableStateMapOf<String, ImageBitmap>() }
    // Keyed on the stable SET of avatar URLs, never on `edgeIndicators` — the map emits those at
    // 10 Hz, so an effect keyed on them is cancelled roughly every 100 ms, far shorter than any
    // download, and a chip would only ever show a photo if the map's own marker pass happened to
    // win the race first.
    val riderAvatarUrls = remember(riders) { riders.mapNotNull { it.avatarUrl }.toSet() }
    LaunchedEffect(riderAvatarUrls) {
        riderAvatarUrls.forEach { url ->
            if (edgeAvatars.containsKey(url)) return@forEach
            AvatarCache.get(url)?.let { edgeAvatars[url] = it.asImageBitmap() }
        }
    }

    // Off-screen riders joined with their display metadata, then clustered by screen proximity.
    val edgeClusters = remember(edgeIndicators, riders, onlineIds, density, edgeAvatars.size, activeRegroup, activeEmergency) {
        val byId = riders.associateBy { it.id }
        val models = edgeIndicators.mapNotNull { ind ->
            if (ind.id == EdgeTarget.REGROUP_ID) {
                val type = activeRegroup?.type ?: return@mapNotNull null
                return@mapNotNull EdgeChipModel(
                    id = ind.id, name = "", avatar = null, isOffline = false,
                    edgeX = ind.edgeX, edgeY = ind.edgeY, distanceMeters = ind.distanceMeters,
                    kind = EdgeKind.REGROUP, regroupType = type,
                )
            }
            if (ind.id == EdgeTarget.EMERGENCY_ID) {
                if (activeEmergency == null) return@mapNotNull null
                return@mapNotNull EdgeChipModel(
                    id = ind.id, name = "", avatar = null, isOffline = false,
                    edgeX = ind.edgeX, edgeY = ind.edgeY, distanceMeters = ind.distanceMeters,
                    kind = EdgeKind.EMERGENCY,
                )
            }
            val rider = byId[ind.id] ?: return@mapNotNull null
            EdgeChipModel(
                id = ind.id,
                name = rider.name,
                avatar = rider.avatarUrl?.let { edgeAvatars[it] },
                isOffline = !onlineIds.contains(ind.id),
                edgeX = ind.edgeX,
                edgeY = ind.edgeY,
                distanceMeters = ind.distanceMeters,
            )
        }
        clusterIndicators(models, radiusPx = density.density * 60f)
    }

    /**
     * Flies to a point the rider asked to see. The follow camera returns on its own after ~10 s of
     * movement, the same way it does after a pan.
     */
    fun peek(at: LatLng, zoom: Float = 16f) {
        isFreeLooking = true
        // Future-dated so the "3 s since last touch" auto-resume doesn't cut the peek short.
        lastInteractionAt = System.currentTimeMillis() + 7_000
        issueCamera { cid -> MapCamera.Navigate(cid, at, zoom, 0f, 0f, true, MapFraming.CENTERED) }
    }

    fun focusRider(id: String) {
        // Edge chips for the regroup point and the emergency share the rider tap path.
        if (id == EdgeTarget.REGROUP_ID) { activeRegroup?.let { peek(LatLng(it.lat, it.lng), 15f) }; return }
        if (id == EdgeTarget.EMERGENCY_ID) { activeEmergency?.let { peek(LatLng(it.lat, it.lng)) }; return }
        val rider = riders.firstOrNull { it.id == id } ?: return
        isFreeLooking = true
        lastInteractionAt = System.currentTimeMillis()
        selectedRiderId = id
        issueCamera { cid -> MapCamera.Navigate(cid, rider.coordinate, 16f, 0f, 0f, true, MapFraming.CENTERED) }
    }

    // --- Layout --------------------------------------------------------------------------------

    Box(Modifier.fillMaxSize().background(DsColor.bgBase)) {
        com.danglingmind.convotrack.ui.map.ConvoTrackMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    end = if (isLandscape) with(density) { panelWidthPx.toDp() } else 0.dp,
                    bottom = if (isLandscape) 0.dp else with(density) { bottomBarHeightPx.toDp() },
                ),
            routeCoords = activeRoute,
            fullRouteCoords = routeCoords,
            stopCoords = ride?.let { rideStopCoords(it) } ?: emptyList(),
            markers = navMarkers,
            user = userLocation?.let { UserPuck(it, userHeading.toFloat()) },
            camera = cameraCommand,
            interactive = true,
            // The active route is trimmed to start under the chevron every fix, so its head is the
            // rider — not an endpoint that wants a cap of its own.
            isNavigating = isNavigationActive,
            onUserPan = {
                lastInteractionAt = System.currentTimeMillis()
                if (!isFreeLooking) isFreeLooking = true
            },
            onEdgeIndicators = { edgeIndicators = it },
            // Landscape moves the chrome off the map entirely, so there is nothing to clamp under.
            topOverlayDp = if (isNavigationActive && !isLandscape) with(density) { topChromeHeightPx.toDp() } else 0.dp,
        )

        if (isNavigationActive) {
            OffscreenIndicatorsOverlay(clusters = edgeClusters, onTapRider = ::focusRider)
        }

        if (isLandscape) {
            LandscapeHud(
                isNavigationActive = isNavigationActive,
                instruction = instruction,
                maneuver = maneuver,
                thenManeuver = thenManeuver,
                distToTurn = distToTurn,
                leaderboardRows = leaderboardRows,
                onlineIds = onlineIds,
                selectedRiderId = selectedRiderId,
                onSelectRider = { id -> if (id == myId) resumeNavigation() else focusRider(id) },
                connectionState = connectionState,
                isOffRoute = isOffRoute,
                activeEmergency = activeEmergency,
                emergencyReporter = activeEmergency?.let { vm.emergencyReporterName(it) }.orEmpty(),
                canDismissEmergency = activeEmergency?.let { vm.canDismissEmergency(it) } == true,
                onDismissEmergency = { activeEmergency?.let { session.dismissEmergency(it.emergencyId) } },
                onShowEmergency = { activeEmergency?.let { peek(LatLng(it.lat, it.lng)) } },
                splitAlert = splitAlert,
                onDismissSplit = { splitAlertDismissed = true },
                showRegroupResolved = showRegroupResolved,
                activeRegroupType = activeRegroup?.type,
                hasMarkedArrived = hasMarkedArrived,
                onShowRegroup = { activeRegroup?.let { peek(LatLng(it.lat, it.lng), 15f) } },
                onMarkArrived = {
                    activeRegroup?.let { session.markArrivedAtRegroup(it.regroupId) }
                    hasMarkedArrived = true
                },
                speedText = if (hasSpeedFix) String.format(java.util.Locale.US, "%.0f", mySpeedKmh) else "--",
                arrival = arrival,
                distText = formatDistToGoal(myDistanceKm),
                destinationName = destinationName,
                myDistanceKm = myDistanceKm,
                isEnding = isEnding,
                onRegroup = { showRegroup = true },
                isLeader = vm.amILeader,
                onEndRide = { if (vm.amILeader) showEndConfirm = true else onExit() },
                onStartNavigation = {
                    isNavigationActive = true
                    resumeNavigation()
                },
                onBack = onExit,
                onPanelWidth = { panelWidthPx = it },
            )
            // RESUME floats over the MAP, where a rider looks for it after panning.
            if (isNavigationActive && isFreeLooking) {
                ResumeNavigationPill(
                    onClick = ::resumeNavigation,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(end = with(density) { panelWidthPx.toDp() }, bottom = 18.dp),
                )
            }
        } else {
            PortraitHud(
                isNavigationActive = isNavigationActive,
                isFreeLooking = isFreeLooking,
                onResume = ::resumeNavigation,
                instruction = instruction,
                maneuver = maneuver,
                thenManeuver = thenManeuver,
                distToTurn = distToTurn,
                leaderboardRows = leaderboardRows,
                onlineIds = onlineIds,
                selectedRiderId = selectedRiderId,
                onSelectRider = { id -> if (id == myId) resumeNavigation() else focusRider(id) },
                connectionState = connectionState,
                isOffRoute = isOffRoute,
                activeEmergency = activeEmergency,
                emergencyReporter = activeEmergency?.let { vm.emergencyReporterName(it) }.orEmpty(),
                canDismissEmergency = activeEmergency?.let { vm.canDismissEmergency(it) } == true,
                onDismissEmergency = { activeEmergency?.let { session.dismissEmergency(it.emergencyId) } },
                onShowEmergency = { activeEmergency?.let { peek(LatLng(it.lat, it.lng)) } },
                splitAlert = splitAlert,
                onDismissSplit = { splitAlertDismissed = true },
                showRegroupResolved = showRegroupResolved,
                activeRegroupType = activeRegroup?.type,
                hasMarkedArrived = hasMarkedArrived,
                onShowRegroup = { activeRegroup?.let { peek(LatLng(it.lat, it.lng), 15f) } },
                onMarkArrived = {
                    activeRegroup?.let { session.markArrivedAtRegroup(it.regroupId) }
                    hasMarkedArrived = true
                },
                speedText = if (hasSpeedFix) String.format(java.util.Locale.US, "%.0f", mySpeedKmh) else "--",
                arrival = arrival,
                distText = formatDistToGoal(myDistanceKm),
                destinationName = destinationName,
                myDistanceKm = myDistanceKm,
                isEnding = isEnding,
                onRegroup = { showRegroup = true },
                isLeader = vm.amILeader,
                onEndRide = { if (vm.amILeader) showEndConfirm = true else onExit() },
                onStartNavigation = {
                    isNavigationActive = true
                    resumeNavigation()
                },
                onBack = onExit,
                onTopChromeHeight = { topChromeHeightPx = it },
                onBottomBarHeight = { bottomBarHeightPx = it },
            )
        }
    }

    if (showRegroup) {
        DsBottomSheet(onDismiss = { showRegroup = false; isBroadcasting = false }) {
            RegroupSheet(
                isBroadcasting = isBroadcasting,
                onCancel = { showRegroup = false },
                onBroadcast = { reason, aheadMeters ->
                    if (isBroadcasting) return@RegroupSheet
                    isBroadcasting = true
                    // The meet point is always derived from the rider's own position plus the
                    // chosen distance ahead ON THE ROUTE.
                    val target = if (aheadMeters > 0) vm.coordinateAhead(aheadMeters) else null
                    val point = target ?: userLocation ?: myLoc?.let { LatLng(it.latitude, it.longitude) }
                    if (point != null) {
                        if (reason.isEmergency) {
                            session.broadcastEmergency(point.latitude, point.longitude)
                        } else {
                            scope.launch { session.broadcastRegroup(reason.name, point.latitude, point.longitude) }
                        }
                    }
                    showRegroup = false
                    isBroadcasting = false
                },
            )
        }
    }

    // Two outcomes, not one. Exit is listed first and is not destructive: it is the common case,
    // and since the navigation screen carries no back button while guidance is running, it is the
    // ONLY way for a leader to step off this screen without ending everyone's ride.
    if (showEndConfirm) {
        ConfirmDialog(
            title = "Leave navigation or end the ride?",
            onDismiss = { showEndConfirm = false },
            dismissLabel = null,
            extraAction = "Exit navigation" to { showEndConfirm = false; onExit() },
            confirmLabel = "End ride",
            destructive = true,
            onConfirm = {
                showEndConfirm = false
                vm.endRide(onEndRide)
            },
        ) {
            DialogText(
                "Exit closes navigation on your phone — the ride keeps running and you can resume from " +
                    "the lobby. End Ride stops location sharing for every rider and generates the summary; " +
                    "that can't be undone.",
            )
        }
    }

    if (showEndError) {
        ConfirmDialog(
            title = "Couldn't end ride",
            onDismiss = { showEndError = false; vm.clearEndError() },
            dismissLabel = null,
            confirmLabel = "OK",
            onConfirm = { showEndError = false; vm.clearEndError() },
        ) { DialogText(endError ?: "An error occurred.") }
    }
}

/**
 * Distance-to-goal for the stat bar.
 *
 * A tenth of a kilometre is meaningful at 9.4 km and noise at 1438 km — and the four-digit form
 * plus a decimal is wide enough to be clipped out of the bar's stat column. Dropping the decimal
 * past 100 km keeps the value honest and the column narrow.
 */
private fun formatDistToGoal(km: Double): String = when {
    km <= 0 -> "--"
    km >= 100 -> String.format(java.util.Locale.US, "%.0f", km)
    else -> String.format(java.util.Locale.US, "%.1f", km)
}

/** Compact distance label for rider markers. Quantized (10 m under 1 km, 0.1 km above) so the
 *  marker only re-bakes when the shown value actually changes. */
private fun formatRiderDistance(meters: Double): String =
    if (meters < 1000) "${(Math.round(meters / 10) * 10)} m"
    else String.format(java.util.Locale.US, "%.1f km", meters / 1000)
