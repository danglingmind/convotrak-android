package com.danglingmind.convotrack.ui.ride

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.maps.DirectionsStep
import com.danglingmind.convotrack.data.net.LiveLeaderboardEntry
import com.danglingmind.convotrack.data.net.LiveParticipant
import com.danglingmind.convotrack.data.net.RegroupEvent
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.danglingmind.convotrack.ui.map.UserPuck
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/** One row of the live leaderboard, sourced from socket data or the seeded roster. */
private data class LbRow(val rank: Int, val userId: String, val name: String, val isMe: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideNavigationScreen(
    rideId: String,
    onExit: () -> Unit,
    onEndRide: () -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val socket = remember { ServiceLocator.socket }
    val locationController = remember { ServiceLocator.location }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = auth.userState.value?.id

    var ride by remember { mutableStateOf<Ride?>(null) }
    var routeCoords by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val live: SnapshotStateMap<String, LiveParticipant> = remember { mutableStateMapOf() }
    var leaderboard by remember { mutableStateOf<List<LiveLeaderboardEntry>>(emptyList()) }
    var activeRegroup by remember { mutableStateOf<RegroupEvent?>(null) }
    var isEmergency by remember { mutableStateOf(false) }
    var splitGapMeters by remember { mutableStateOf<Double?>(null) }
    val myLoc by locationController.current.collectAsState()

    // Turn-by-turn steps (client-side via MKDirections/Routes API — no backend involvement).
    var navSteps by remember { mutableStateOf<List<DirectionsStep>>(emptyList()) }
    var currentStep by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var distToTurn by remember { mutableStateOf(0.0) }

    // Course-up heading: GPS bearing while moving, else derived from movement between fixes
    // (mock/indoor GPS often omits bearing). Free-look pauses the follow camera after a pan.
    var heading by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var prevLoc by remember { mutableStateOf<android.location.Location?>(null) }
    var isFreeLooking by remember { mutableStateOf(false) }
    LaunchedEffect(myLoc) {
        val loc = myLoc ?: return@LaunchedEffect
        val prev = prevLoc
        val moving = loc.hasSpeed() && loc.speed > 0.5f
        heading = when {
            loc.hasBearing() && moving -> loc.bearing
            prev != null && prev.distanceTo(loc) > 3f -> (prev.bearingTo(loc) + 360f) % 360f
            else -> heading
        }
        prevLoc = loc
    }

    // Advance the turn banner as the rider passes each step's end point (iOS updateCurrentStep).
    LaunchedEffect(myLoc, navSteps) {
        val loc = myLoc ?: return@LaunchedEffect
        if (navSteps.isEmpty() || currentStep >= navSteps.size) return@LaunchedEffect
        val step = navSteps[currentStep]
        val out = FloatArray(1)
        android.location.Location.distanceBetween(loc.latitude, loc.longitude, step.end.latitude, step.end.longitude, out)
        distToTurn = out[0].toDouble()
        if (distToTurn < 30 && currentStep + 1 < navSteps.size) currentStep++
    }

    var showRegroup by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Location permission -> start broadcasting my position (foreground service).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationController.start(ctx, rideId)
        }
    }
    LaunchedEffect(rideId) {
        val fineGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            locationController.start(ctx, rideId)
        } else {
            permLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { locationController.stop(ctx) }
    }

    // Load the ride (route + roster + steps), then go live over the socket.
    LaunchedEffect(rideId) {
        ride = runCatching { apiCall { api.getRide(rideId) } }.getOrNull()
        ride?.let {
            // Draw the leader-selected polyline (shared by all riders) but keep per-leg steps.
            val rws = rideRouteWithSteps(it)
            routeCoords = rws.coords
            navSteps = rws.steps
            currentStep = 0
        }
        socket.connect()
        socket.joinRoom(rideId)
        // Presence heartbeat (in addition to location broadcasts) so a stationary rider still
        // reads as online during navigation.
        launch {
            while (true) {
                socket.emitHeartbeat(rideId)
                kotlinx.coroutines.delay(8_000)
            }
        }
        launch {
            socket.stateUpdates.collect { update ->
                update.participants.forEach { p -> live[p.userId] = p }
                // Only overwrite the seeded roster once the server actually has leaderboard data.
                if (update.leaderboard.isNotEmpty()) leaderboard = update.leaderboard
            }
        }
        launch { socket.regroupStarted.collect { activeRegroup = it; isEmergency = false } }
        launch { socket.emergencyStarted.collect { isEmergency = true } }
        launch { socket.regroupResolved.collect { id -> if (activeRegroup?.regroupId == id) activeRegroup = null } }
        // Group-split alerts (engine detects the pack spreading past threshold).
        launch { socket.splitDetected.collect { splitGapMeters = it.gapMeters } }
        launch { socket.splitResolved.collect { splitGapMeters = null } }
        // If the leader removes me mid-ride, leave navigation (iOS wasRemoved exit).
        launch { socket.participantRemoved.collect { uid -> if (uid == myId) onExit() } }
    }

    BackHandler { onExit() }

    val namesById = remember(ride) { ride?.participants?.associate { it.userId to it.name } ?: emptyMap() }
    // My own speed comes from GPS directly (m/s -> km/h); fall back to the socket echo.
    val mySpeed = myLoc?.takeIf { it.hasSpeed() }?.speed?.toDouble()?.times(3.6)
        ?: myId?.let { live[it]?.speed?.times(3.6) }
    val riderCount = maxOf(live.size, ride?.participants?.size ?: 0)

    // Live leaderboard rows: real socket ranking when present, else the seeded roster order.
    val lbRows: List<LbRow> = remember(leaderboard, ride, myId) {
        if (leaderboard.isNotEmpty()) {
            leaderboard.sortedBy { it.rank }.map { LbRow(it.rank, it.userId, it.name, it.userId == myId) }
        } else {
            ride?.participants?.mapIndexed { i, p -> LbRow(i + 1, p.userId, p.name, p.userId == myId) } ?: emptyList()
        }
    }

    Box(Modifier.fillMaxSize().background(CT.surfaceDim)) {
        val myLatLng = myLoc?.let { LatLng(it.latitude, it.longitude) }
        // Speed-based zoom, matching iOS navZoom.
        val navZoom = when { (mySpeed ?: 0.0) < 20 -> 17.5f; (mySpeed ?: 0.0) < 60 -> 16.5f; else -> 16.0f }
        // Bright route starts at the rider; the full planned route stays dim underneath.
        val activeRoute = if (myLatLng != null && routeCoords.size >= 2) trimRouteToUser(routeCoords, myLatLng) else routeCoords

        // Live navigation map: 3D follow camera, heading puck, trimmed route, live riders.
        ConvoTrackMap(
            modifier = Modifier.fillMaxSize(),
            routeCoords = activeRoute,
            fullRouteCoords = routeCoords,
            markers = buildList {
                ride?.let { addAll(rideMarkers(it)) }
                live.values.filter { it.userId != myId }.forEach { p ->
                    val lat = p.lat; val lng = p.lng
                    if (lat != null && lng != null) {
                        add(MapMarker("rider_${p.userId}", LatLng(lat, lng), MapMarkerKind.RIDER, namesById[p.userId]))
                    }
                }
                activeRegroup?.let { add(MapMarker("regroup", LatLng(it.lat, it.lng), MapMarkerKind.REGROUP, "Regroup")) }
            },
            user = myLatLng?.let { UserPuck(it, heading) },
            camera = when {
                myLatLng == null -> MapCamera.Fit
                isFreeLooking -> MapCamera.Free
                else -> MapCamera.Nav(myLatLng, heading, navZoom)
            },
            interactive = true,
            onUserPan = { isFreeLooking = true },
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar: back • destination • tiny live indicator
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
                        .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape).clickable(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave navigation (ride keeps running)",
                        tint = CT.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("TO", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.5.sp)
                    Text(
                        ride?.destinationName ?: "Loading…",
                        style = CTType.bodyLg,
                        color = CT.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LiveIndicator(connected = socket.connected.value)
            }

            // Turn-by-turn maneuver pill (client-side directions).
            if (navSteps.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TurnByTurnPill(navSteps.getOrNull(currentStep)?.instruction.orEmpty(), distToTurn)
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.size(10.dp))

            // Live leaderboard strip.
            if (lbRows.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lbRows, key = { it.userId }) { LeaderboardCard(it) }
                }
            }

            // Regroup / emergency banner
            if (activeRegroup != null || isEmergency) {
                val accent = if (isEmergency) CT.errorColor else CT.primaryFixed
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isEmergency) CT.errorContainer.copy(alpha = 0.2f) else CT.primaryFixed.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (isEmergency) Icons.Filled.Warning else Icons.Filled.Groups, null, tint = accent, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isEmergency) "EMERGENCY" else "REGROUP ACTIVE", style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
                        Text(activeRegroup?.type ?: "Group is regrouping", style = CTType.bodyMd, color = CT.onSurface)
                    }
                    if (activeRegroup != null) {
                        Text(
                            "ARRIVED",
                            style = CTType.labelCaps,
                            color = CT.onPrimaryFixed,
                            letterSpacing = 1.sp,
                            modifier = Modifier.clip(CircleShape).background(CT.primaryFixed)
                                .clickable {
                                    activeRegroup?.let { socket.emitRegroupArrived(rideId, it.regroupId) }
                                    activeRegroup = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Group-split alert — the pack has spread out. Offer a quick Regroup or dismiss.
            val gap = splitGapMeters
            if (gap != null && activeRegroup == null && !isEmergency) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(CT.errorContainer.copy(alpha = 0.2f))
                        .border(1.dp, CT.errorColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Warning, null, tint = CT.errorColor, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GROUP SPLIT", style = CTType.labelCaps, color = CT.errorColor, letterSpacing = 1.sp)
                        Text(
                            "Riders are ${String.format(java.util.Locale.US, "%.0f", gap)} m apart",
                            style = CTType.bodyMd,
                            color = CT.onSurface,
                        )
                    }
                    Text(
                        "REGROUP",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clip(CircleShape).background(CT.primaryFixed)
                            .clickable { splitGapMeters = null; showRegroup = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        "DISMISS",
                        style = CTType.labelCaps,
                        color = CT.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable { splitGapMeters = null }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Bottom overlay: re-center pill, stat pills (bottom-right), controls.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Re-center pill appears once the rider pans away from the follow camera.
            if (isFreeLooking) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(CT.primaryFixed)
                        .clickable { isFreeLooking = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, null, tint = CT.onPrimaryFixed, modifier = Modifier.size(16.dp))
                    Text("RE-CENTER", style = CTType.labelCaps, color = CT.onPrimaryFixed, letterSpacing = 1.sp)
                }
            }

            // Stat pills — uniform squares, stacked vertically, right-aligned above End Ride.
            Column(
                Modifier.align(Alignment.End),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalAlignment = Alignment.End,
            ) {
                NavStatPill(value = mySpeed?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--", unit = "KM/H", accent = CT.primaryFixed)
                NavStatPill(value = "$riderCount", inlineLabel = "RIDERS")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                ControlButton(
                    icon = Icons.Filled.Groups,
                    description = "Regroup",
                    tint = CT.onSurface,
                    background = CT.surfaceContainerHigh.copy(alpha = 0.9f),
                    border = CT.outlineVariant.copy(alpha = 0.3f),
                    onClick = { showRegroup = true },
                )
                ControlButton(
                    icon = Icons.Filled.Stop,
                    description = "End ride",
                    tint = CT.errorColor,
                    background = CT.errorContainer.copy(alpha = 0.12f),
                    border = CT.errorColor.copy(alpha = 0.35f),
                    onClick = {
                        scope.launch {
                            // Leader ends the ride for everyone (backend); a member just leaves.
                            if (myId != null && ride?.leaderId == myId) {
                                runCatching { apiCall { api.endRide(rideId) } }
                                onEndRide()
                            } else {
                                onExit()
                            }
                        }
                    },
                )
            }
        }
    }

    if (showRegroup) {
        ModalBottomSheet(
            onDismissRequest = { showRegroup = false },
            sheetState = sheetState,
            containerColor = CT.surfaceDim,
        ) {
            RegroupSheet(onBroadcast = { reason ->
                showRegroup = false
                // Broadcast at my current position. Emergency emits ride:emergency;
                // the others emit ride:regroup (per CLAUDE.md architectural decision).
                val lat = myLoc?.latitude ?: 0.0
                val lng = myLoc?.longitude ?: 0.0
                if (reason.isEmergency) {
                    socket.emitEmergency(rideId, lat, lng, "Emergency")
                } else {
                    val type = reason.name // FUEL / FOOD / SCENIC
                    scope.launch { socket.emitRegroup(rideId, type, lat, lng) }
                }
            })
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    background: Color,
    border: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Uniform 72dp square stat pill. Pass [unit] for a stacked value/unit (SPEED) or [inlineLabel]
 *  for value + label on one line (RIDERS). All pills share the same outer dimensions. */
@Composable
private fun NavStatPill(
    value: String,
    unit: String? = null,
    inlineLabel: String? = null,
    accent: Color? = null,
) {
    Box(
        Modifier.size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (inlineLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = CTType.dataMono.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = accent ?: CT.onSurface)
                Text(inlineLabel, style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 0.5.sp)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = CTType.dataMono.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = accent ?: CT.onSurface)
                if (unit != null) {
                    Text(unit, style = CTType.labelCaps.copy(fontSize = 8.sp), color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                }
            }
        }
    }
}

/** Very small top-right "LIVE" indicator (kept out of the stats group). */
@Composable
private fun LiveIndicator(connected: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (connected) CT.tertiaryFixed else CT.onSurfaceVariant))
        Text(
            if (connected) "LIVE" else "…",
            style = CTType.labelCaps.copy(fontSize = 8.sp),
            color = if (connected) CT.tertiaryFixed else CT.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
    }
}

/** Turn-by-turn maneuver pill: maneuver icon + distance-to-turn + instruction. */
@Composable
private fun TurnByTurnPill(instruction: String, distanceMeters: Double) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CT.surfaceContainerHigh.copy(alpha = 0.92f))
            .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(CT.primaryFixed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(maneuverIcon(instruction), null, tint = CT.onPrimaryFixed, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.widthIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val distText = when {
                distanceMeters <= 0 -> ""
                distanceMeters >= 1000 -> String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000)
                else -> String.format(java.util.Locale.US, "%.0f m", distanceMeters)
            }
            if (distText.isNotEmpty()) {
                Text(distText, style = CTType.labelCaps.copy(fontSize = 10.sp), color = CT.primaryFixed)
            }
            Text(
                instruction.ifEmpty { "Follow route" },
                style = CTType.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = CT.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun maneuverIcon(instruction: String): ImageVector {
    val l = instruction.lowercase()
    return when {
        l.contains("arrive") || l.contains("destination") -> Icons.Filled.Flag
        l.contains("u-turn") || l.contains("uturn") -> Icons.Filled.UTurnLeft
        l.contains("left") -> Icons.Filled.TurnLeft
        l.contains("right") -> Icons.Filled.TurnRight
        l.contains("straight") || l.contains("continue") || l.contains("head") -> Icons.Filled.Straight
        else -> Icons.Filled.Navigation
    }
}

/** One horizontal leaderboard card: rank • avatar initial • first name (+ YOU badge). */
@Composable
private fun LeaderboardCard(row: LbRow) {
    val highlight = row.rank == 1 || row.isMe
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) CT.primaryFixed.copy(alpha = 0.12f) else CT.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, if (highlight) CT.primaryFixed else CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            "${row.rank}",
            style = CTType.dataMono.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = if (highlight) CT.primaryFixed else CT.onSurfaceVariant,
        )
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(CT.surfaceContainerHighest)
                .border(if (highlight) 2.dp else 1.dp, if (highlight) CT.primaryFixed else CT.outlineVariant.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                row.name.take(1).uppercase(),
                style = CTType.bodyMd.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = if (highlight) CT.primaryFixed else CT.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.name.substringBefore(" ").ifEmpty { "Rider" },
                style = CTType.bodyMd.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = CT.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            if (row.isMe) {
                Text(
                    "YOU",
                    style = CTType.labelCaps.copy(fontSize = 7.sp),
                    color = CT.onPrimaryFixed,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.clip(CircleShape).background(CT.primaryFixed).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}
