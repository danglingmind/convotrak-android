package com.danglingmind.convotrack.ui.ride

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.isNotFoundError
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.ui.common.DestinationPhotoImage
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.rememberDestinationHero
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.rememberQrBitmap
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.NavCamera
import com.google.android.gms.maps.model.LatLng
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.FloatingStatPill
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.launch

/**
 * Ride lobby. All realtime state — roster, presence, ride status, removed/deleted — comes from the
 * graph-scoped [RideSessionViewModel], never from the socket directly: the session owns the
 * connection, the single heartbeat and reconnect→rejoin for the whole ride, so this screen
 * observes and never manages a connection of its own.
 */
@Composable
fun RideLobbyScreen(
    rideId: String,
    sessionVm: RideSessionViewModel,
    onBack: () -> Unit,
    onStartRide: () -> Unit,
    onEdit: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val myId = auth.userState.value?.id

    val session = sessionVm.session
    val ride by sessionVm.ride.collectAsState()
    val rideLoaded by sessionVm.rideLoaded.collectAsState()
    val riders by session.participants.collectAsState()
    val onlineIds by session.onlineUserIds.collectAsState()
    val liveStatus by session.rideStatus.collectAsState()
    val connectionState by session.connectionState.collectAsState()
    val wasRemoved by session.wasRemoved.collectAsState()
    val wasDeleted by session.wasDeleted.collectAsState()
    val rideUpdated by session.rideUpdated.collectAsState()
    val connected = connectionState == RideRealtimeSession.ConnectionState.CONNECTED

    var starting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var leaveError by remember { mutableStateOf<String?>(null) }

    // Fetches the ride and starts the shared session. Idempotent, so re-entering the lobby from
    // navigation just re-joins the room rather than resetting the live roster.
    LaunchedEffect(rideId, myId) { sessionVm.load(myId.orEmpty()) }

    // The leader edited the ride — pull the authoritative copy so the title, route and stops
    // update live for everyone.
    LaunchedEffect(rideUpdated) { if (rideUpdated != null) sessionVm.refreshRide() }

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        // The leader may edit in ANY non-COMPLETED state (LOBBY, ACTIVE and PAUSED are all
        // editable); editing a live ride re-routes everyone via ride:updated.
        val amLeader = myId != null && ride?.leaderId == myId
        val canEdit = amLeader && ride != null && ride?.status != "COMPLETED"
        // Riders only, in both LOBBY and ACTIVE — a rider who has to peel off mid-ride is exactly
        // who needs this, and waiting for the leader to kick them is not an answer.
        //
        // The leader has no equivalent: leaving would orphan the ride, leaving nobody able to
        // start, end or edit it, so they cancel it from the edit page instead (the server rejects a
        // leader leave with LEADER_CANNOT_LEAVE regardless of what the UI does).
        val canLeave = !amLeader && ride != null && ride?.status != "COMPLETED"
        ConvoTrackTopBar(
            title = ride?.title ?: "Lobby",
            showBack = true,
            onBack = onBack,
            actions = {
                if (canEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit ride",
                        tint = CT.primaryFixed,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onEdit(rideId) }.padding(9.dp),
                    )
                } else if (canLeave) {
                    // Takes the leader's Edit slot — the two are mutually exclusive, so the corner
                    // holds whichever applies to you and never both.
                    //
                    // Tinted rather than filled: a solid red capsule would be the only saturated
                    // red on a lime-and-green screen, pulling the eye to the one control a rider is
                    // least likely to want. It also overstates the stakes — leaving is reversible
                    // (you can re-join with the code), whereas END RIDE and DELETE RIDE are not.
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(CT.errorContainer.copy(alpha = 0.18f))
                            .border(1.dp, CT.errorColor.copy(alpha = 0.28f), CircleShape)
                            .clickable(enabled = !leaving) { showLeaveConfirm = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (leaving) "LEAVING…" else "LEAVE",
                            style = CTType.labelCaps,
                            color = CT.errorColor,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                    }
                }
            },
        )

        val loadedRide = ride
        when {
            loadedRide == null && !rideLoaded -> LoadingBlock(Modifier.padding(top = 40.dp))
            loadedRide == null -> Box(Modifier.padding(20.dp)) {
                ErrorBlock(
                    "Couldn't load this ride.",
                    onRetry = { sessionVm.load(myId.orEmpty(), force = true) },
                )
            }
            else -> {
                // The ride is live once EITHER the REST copy or a state_update says so — the
                // session's status starts at LOBBY and only moves when the server broadcasts,
                // so re-entering an already-ACTIVE ride would otherwise show START RIDE.
                val isActive = liveStatus == "ACTIVE" || liveStatus == "IN_PROGRESS" ||
                    loadedRide.status == "ACTIVE" || loadedRide.status == "IN_PROGRESS"

                // Optimistic local ready state: the tally and the button move the instant you tap
                // rather than waiting for the server's ride:participant_ready echo.
                var myReadyOptimistic by remember(rideId) { mutableStateOf(false) }
                val myReady = myReadyOptimistic ||
                    riders.firstOrNull { it.userId == myId }?.status == "READY"

                // Riders only. The leader has no MARK READY control (they get START instead), so
                // counting them would leave the tally permanently one short and looking stuck.
                val others = riders.filter { !it.isLeader }
                val readyCount = others.count { rider ->
                    // Own row uses the optimistic local state, matching how the roster renders it,
                    // so the tally moves the instant you tap rather than waiting for the echo.
                    if (rider.userId == myId) myReady else rider.status == "READY"
                }
                val readyStatusText = when {
                    others.isEmpty() -> "SOLO"
                    readyCount == others.size -> "ALL READY"
                    else -> "$readyCount/${others.size} READY"
                }
                val canStart = others.isEmpty() || others.all { it.status == "READY" }
                var showQr by remember { mutableStateOf(false) }

                // Leader-only rider management (remove from ride).
                var riderToManage by remember(rideId) { mutableStateOf<RideParticipant?>(null) }
                var removing by remember(rideId) { mutableStateOf(false) }
                var removeError by remember(rideId) { mutableStateOf<String?>(null) }

                // Clear the leader's in-flight removal sheet once the roster confirms the kick.
                LaunchedEffect(riders) {
                    val target = riderToManage ?: return@LaunchedEffect
                    if (riders.none { it.userId == target.userId }) {
                        riderToManage = null
                        removing = false
                    }
                }

                var routeCoords by remember { mutableStateOf<List<LatLng>>(emptyList()) }
                // Draw the leader-selected polyline so every rider sees the same line; re-keyed on
                // the stored geometry + waypoints so a live ride:updated edit re-draws the route.
                LaunchedEffect(loadedRide.routePolyline, loadedRide.waypoints) {
                    routeCoords = rideRoute(loadedRide)
                }

                // Destination photo hero — the image Google Maps shows for this place, in place of
                // the route map. Three-state, so the map fallback never flashes before a photo.
                val heroPhase by rememberDestinationHero(
                    rideId = loadedRide.id,
                    destinationName = loadedRide.destinationName,
                    lat = loadedRide.destinationLat,
                    lng = loadedRide.destinationLng,
                )

                // Map with a floating stat-pill strip over its lower edge (iOS lobby mapSection).
                //
                // 360dp, not 260: the camera has to reserve ~100dp at the top for the destination
                // pin's art (stop pins are bottom-anchored and tower above their coordinate) and
                // ~84dp at the bottom for the floating pills. On a 260dp map that left a ~76dp
                // band to frame a route in, so the fit zoomed out until the route was a speck.
                Box(Modifier.fillMaxWidth().height(LOBBY_MAP_HEIGHT.dp)) {
                    // An explicit fit, not AutoFit: stop pins are bottom-anchored and tower up to
                    // ~92dp ABOVE their coordinate, and the stat pills float over the map's lower
                    // edge. A symmetric fit put the destination pin underneath the top app bar.
                    var lobbyCameraId by remember(rideId) { mutableStateOf(0L) }
                    LaunchedEffect(routeCoords, loadedRide.id) { lobbyCameraId += 1 }
                    when (val phase = heroPhase) {
                        // Deliberately empty: neither the photo nor the map. Showing the map here
                        // is what makes a ride switch flash map → photo. A plain surface reads as
                        // "loading" without committing to either outcome.
                        DestinationHeroPhase.Resolving ->
                            Box(Modifier.matchParentSize().background(CT.surfaceContainerLow))

                        is DestinationHeroPhase.Photo ->
                            DestinationPhotoImage(
                                bitmap = phase.photo.bitmap,
                                crop = false,   // near-square slot: show the whole frame
                                modifier = Modifier.matchParentSize(),
                            )

                        DestinationHeroPhase.Unavailable -> ConvoTrackMap(
                        modifier = Modifier.matchParentSize(),
                        routeCoords = routeCoords,
                        markers = rideMarkers(loadedRide),
                        stopCoords = rideStopCoords(loadedRide),
                        interactive = false,
                        camera = if (routeCoords.isEmpty()) {
                            MapCamera.AutoFit
                        } else {
                            val rawTop = NavCamera.STOP_PIN_TOP_EXTENT_DP + 8f
                            val rawBottom = 84f   // clears the floating stat-pill row
                            // The insets must leave a usable band — asking the SDK to fit into a
                            // zero-height viewport yields a nonsense zoom. Scale both back
                            // proportionally if the chrome ever grows enough to threaten that
                            // (larger text, more pills), keeping their ratio so the route stays
                            // centred in whatever band is left (iOS `squeeze`).
                            val maxVertical = LOBBY_MAP_HEIGHT - 80f
                            val squeeze = minOf(1f, maxVertical / maxOf(rawTop + rawBottom, 1f))
                            MapCamera.Fit(
                                id = lobbyCameraId,
                                coords = routeCoords,
                                topDp = rawTop * squeeze,
                                leftDp = 40f,
                                bottomDp = rawBottom * squeeze,
                                rightDp = 40f,
                            )
                        },
                        )
                    }
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.7f to CT.surfaceDim.copy(alpha = 0.55f),
                                1f to CT.surfaceDim,
                            ),
                        ),
                    )
                    LazyRow(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FloatingStatPill(label = "DISTANCE", value = formatKm(loadedRide.distanceMeters), unit = "km") }
                        item { FloatingStatPill(label = "RIDERS", value = "${riders.size}", unit = null) }
                        item {
                            FloatingStatPill(
                                label = "STATUS",
                                value = if (canStart) "Ready" else "Waiting",
                                unit = null,
                                dotColor = if (canStart) CT.primaryFixed else CT.tertiaryFixedDim,
                            )
                        }
                        if (loadedRide.inviteCode.isNotEmpty()) {
                            item { FloatingStatPill(label = "CODE", value = loadedRide.inviteCode, unit = null) }
                        }
                    }

                    // Photographer credit. Required by Google's Places policy whenever a Place
                    // Photo is displayed, so it renders WITH the image rather than being optional
                    // decoration. Sits just above the floating stat-pill row.
                    (heroPhase as? DestinationHeroPhase.Photo)?.photo?.attribution?.let { credit ->
                        Text(
                            "Photo: $credit · Google",
                            style = CTType.bodyMd.copy(fontSize = 9.sp),
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 86.dp),
                        )
                    }
                }

                // Roster header: SQUAD ROSTER · LIVE indicator · share / QR actions.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The title takes the slack and truncates; everything to its right is
                    // fixed-size. A plain weighted Spacer here collapsed to nothing on a phone and
                    // the LIVE dot ran straight into the ready tally ("LIVE0 / 2 READY").
                    Text(
                        "SQUAD ROSTER",
                        style = CTType.labelCaps,
                        color = CT.primaryFixed,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (connected) {
                        Spacer(Modifier.size(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(CT.primaryFixed))
                            Text("LIVE", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.primaryFixed, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        readyStatusText,
                        style = CTType.labelCaps,
                        color = CT.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(10.dp))
                    LobbyActionButton(Icons.Filled.Share, "Share invite") { shareInvite(ctx, loadedRide.inviteCode, loadedRide.title) }
                    Spacer(Modifier.size(8.dp))
                    LobbyActionButton(Icons.Filled.QrCode2, "Show QR code") { showQr = true }
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(riders, key = { it.userId }) { rider ->
                        // Leader can tap any non-leader rider to open the management sheet.
                        val canManage = amLeader && !rider.isLeader
                        RiderRow(
                            rider = rider,
                            isMe = rider.userId == myId,
                            isOnline = onlineIds.contains(rider.userId),
                            rideActive = isActive,
                            onClick = if (canManage) ({ riderToManage = rider }) else null,
                        )
                    }
                }

                if (showQr) {
                    QrCodeDialog(inviteCode = loadedRide.inviteCode, onDismiss = { showQr = false })
                }

                riderToManage?.let { rider ->
                    RemoveRiderSheet(
                        rider = rider,
                        removing = removing,
                        error = removeError,
                        onRemove = {
                            if (removing) return@RemoveRiderSheet
                            removing = true
                            removeError = null
                            scope.launch {
                                try {
                                    apiCall { api.removeParticipant(rideId, rider.userId) }
                                    // The ride:participant_removed broadcast (which the leader
                                    // also receives) is what removes the rider from the roster
                                    // and closes this sheet.
                                } catch (e: Exception) {
                                    removeError = e.riderMessage
                                    removing = false
                                }
                            }
                        },
                        onDismiss = { if (!removing) { riderToManage = null; removeError = null } },
                    )
                }

                if (showLeaveConfirm) {
                    AlertDialog(
                        onDismissRequest = { showLeaveConfirm = false },
                        containerColor = CT.surfaceContainer,
                        title = { Text("Leave this ride?", style = CTType.headlineMd, color = CT.onSurface) },
                        text = {
                            Text(
                                "You'll stop sharing your location and drop off the roster. " +
                                    "You can re-join later with the invite code.",
                                style = CTType.bodyMd,
                                color = CT.onSurfaceVariant,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showLeaveConfirm = false
                                leaving = true
                                leaveError = null
                                scope.launch {
                                    try {
                                        apiCall { api.leaveRide(rideId) }
                                        // Confirmation-FIRST, deliberately: tearing the session
                                        // down optimistically would drop the socket before the
                                        // server's ride:participant_left broadcast — the very event
                                        // every other rider's roster, ready tally and map pin
                                        // removal hangs off.
                                        sessionVm.endSession()
                                        onBack()
                                    } catch (e: ApiException) {
                                        // "You aren't in this ride" counts as success. The rider
                                        // asked to be out; if the server already has them out (a
                                        // double tap, a stale lobby, a leader who kicked them a
                                        // moment earlier) the goal is met, and trapping them behind
                                        // an error alert would be absurd.
                                        if (e.message == "PARTICIPANT_NOT_FOUND" || isNotFoundError(e.message)) {
                                            sessionVm.endSession()
                                            onBack()
                                        } else {
                                            leaveError = e.riderMessage
                                            leaving = false
                                        }
                                    } catch (e: Exception) {
                                        leaveError = e.riderMessage
                                        leaving = false
                                    }
                                }
                            }) { Text("Leave Ride", color = CT.errorColor) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLeaveConfirm = false }) {
                                Text("Cancel", color = CT.onSurfaceVariant)
                            }
                        },
                    )
                }

                leaveError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { leaveError = null },
                        containerColor = CT.surfaceContainer,
                        title = { Text("Couldn't Leave Ride", style = CTType.headlineMd, color = CT.onSurface) },
                        text = { Text(message, style = CTType.bodyMd, color = CT.onSurfaceVariant) },
                        confirmButton = {
                            TextButton(onClick = { leaveError = null }) { Text("OK", color = CT.primaryFixed) }
                        },
                    )
                }

                // The leader removed me from the ride.
                if (wasRemoved) {
                    RemovedFromRideDialog(onConfirm = { sessionVm.endSession(); onBack() })
                }

                // The leader deleted the whole ride. Non-leaders learn of it only through this
                // broadcast; the leader initiated it and already left via the edit page.
                if (wasDeleted && !amLeader) {
                    RideCancelledDialog(onConfirm = { sessionVm.endSession(); onBack() })
                }

                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (startError != null) {
                        Text(startError!!, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    if (isActive) {
                        // Ride already running — jump into navigation on tap (no auto-navigate).
                        LimePrimaryButton(text = "RESUME NAVIGATION", onClick = onStartRide)
                    } else {
                        // Everyone can mark ready; the emit is acked by the server.
                        SecondaryButton(
                            text = if (myReady) "YOU'RE READY ✓" else "MARK READY",
                            enabled = !myReady,
                            highlighted = myReady,
                            onClick = {
                                myReadyOptimistic = true
                                scope.launch {
                                    // Roll back if the server never acked it.
                                    if (!session.markReady()) myReadyOptimistic = false
                                }
                            },
                        )

                        if (amLeader) {
                            LimePrimaryButton(
                                text = when {
                                    starting -> "STARTING…"
                                    canStart -> "START RIDE"
                                    else -> "WAITING FOR RIDERS"
                                },
                                enabled = canStart && !starting,
                                onClick = {
                                    if (starting) return@LimePrimaryButton
                                    starting = true
                                    startError = null
                                    scope.launch {
                                        try {
                                            apiCall { api.startRide(rideId) }
                                            onStartRide()
                                        } catch (e: ApiException) {
                                            // The ride is already ACTIVE (a double tap, or another
                                            // device started it first). The goal is met — navigate
                                            // rather than showing an error for it (iOS parity).
                                            if (e.message?.contains("RIDE_NOT_IN_LOBBY") == true) {
                                                onStartRide()
                                            } else {
                                                startError = e.riderMessage
                                                starting = false
                                            }
                                        } catch (e: Exception) {
                                            startError = e.riderMessage
                                            starting = false
                                        }
                                    }
                                },
                            )
                        } else {
                            Text(
                                "Waiting for the ride leader to start…",
                                style = CTType.labelCaps,
                                color = CT.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Height of the lobby's map preview. Shared by the frame and the camera-fit clamp so they can't
 *  drift apart. */
private const val LOBBY_MAP_HEIGHT = 360f

/** Shown to a rider whose ride the leader deleted — acknowledging it leaves the ride flow. */
@Composable
private fun RideCancelledDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        containerColor = CT.surfaceContainer,
        title = { Text("Ride Cancelled", style = CTType.headlineMd, color = CT.onSurface) },
        text = { Text("The ride leader cancelled this ride.", style = CTType.bodyMd, color = CT.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK", color = CT.primaryFixed) } },
    )
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) CT.tertiaryFixed.copy(alpha = 0.15f) else CT.surfaceContainerHighest)
            .border(1.dp, if (highlighted) CT.tertiaryFixed.copy(alpha = 0.5f) else CT.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CTType.labelCaps, color = if (highlighted) CT.tertiaryFixed else CT.onSurface, letterSpacing = 1.sp)
    }
}

/**
 * One roster row.
 *
 * The badge is a tri-state, not a boolean. The leader starts the ride rather than readying up, so a
 * leader must never render as WAITING; and once the ride is ACTIVE the ready/waiting distinction is
 * moot for everyone — they're all riding.
 */
private fun rosterBadge(rider: RideParticipant, rideActive: Boolean): Triple<String, Color, Color> = when {
    rideActive -> Triple("RIDING", CT.onPrimaryFixed, CT.tertiaryFixed)
    rider.isLeader || rider.status == "READY" -> Triple("READY", CT.onPrimaryContainer, CT.primaryContainer)
    else -> Triple("WAITING", CT.onSurfaceVariant, CT.surfaceVariant)
}

@Composable
private fun RiderRow(
    rider: RideParticipant,
    isMe: Boolean,
    isOnline: Boolean,
    rideActive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (badgeText, badgeFg, badgeBg) = rosterBadge(rider, rideActive)
    val ready = badgeText != "WAITING"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, if (ready) CT.primaryFixed else CT.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Avatar with an online presence dot (roster membership == online, iOS parity).
        Box(Modifier.size(48.dp)) {
            Box(
                Modifier.matchParentSize().clip(CircleShape).background(CT.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(rider.name.take(1).uppercase(), style = CTType.headlineMd, color = CT.onSurface)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape)
                    .background(if (isOnline) CT.tertiaryFixed else CT.surfaceVariant)
                    .border(2.dp, CT.surfaceDim, CircleShape),
            )
        }
        // Name on its own line (ellipsised so it never crowds the trailing status badge); the
        // rider title below it conveys the leader/rider role — no separate inline "LEADER" badge.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (isMe) "${rider.name} (You)" else rider.name,
                style = CTType.bodyLg,
                color = CT.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (rider.isLeader) "RIDE LEADER" else "FORMATION RIDER",
                style = CTType.labelCaps.copy(fontSize = 10.sp),
                color = if (rider.isLeader) CT.primaryFixed else CT.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
        Text(
            badgeText,
            style = CTType.labelCaps,
            color = badgeFg,
            letterSpacing = 1.sp,
            modifier = Modifier.clip(CircleShape)
                .background(badgeBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Bottom sheet the leader uses to remove a rider from the ride (iOS RemoveRiderSheet parity). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveRiderSheet(
    rider: RideParticipant,
    removing: Boolean,
    error: String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CT.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(CT.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(rider.name.take(1).uppercase(), style = CTType.headlineLg, color = CT.onSurface)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rider.name, style = CTType.headlineMd, color = CT.onSurface, maxLines = 1)
                Text(
                    if (rider.isLeader) "RIDE LEADER" else "FORMATION RIDER",
                    style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp,
                )
            }

            if (error != null) {
                Text(error, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(CT.errorContainer)
                    .clickable(enabled = !removing, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (removing) "REMOVING…" else "REMOVE FROM RIDE", style = CTType.labelCaps, color = CT.errorColor, letterSpacing = 1.sp)
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerHighest)
                    .clickable(enabled = !removing, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("CANCEL", style = CTType.labelCaps, color = CT.onSurface, letterSpacing = 1.sp)
            }
        }
    }
}

/** Shown to a rider the leader has removed — acknowledging it leaves the ride. */
@Composable
private fun RemovedFromRideDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        containerColor = CT.surfaceContainer,
        title = { Text("Removed From Ride", style = CTType.headlineMd, color = CT.onSurface) },
        text = { Text("The ride leader removed you from this ride.", style = CTType.bodyMd, color = CT.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK", color = CT.primaryFixed) } },
    )
}

/** Small square icon action button used in the roster header (share / QR). */
@Composable
private fun LobbyActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(CT.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, CT.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = CT.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Full-screen QR dialog for the invite deep link (iOS QRCodeModal parity). */
@Composable
private fun QrCodeDialog(inviteCode: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CT.surfaceContainer).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("JOIN CONVOTRACK", style = CTType.headlineMd, color = CT.primaryFixed, letterSpacing = 2.sp)
            val qr = rememberQrBitmap(ApiClient.joinLink(inviteCode))
            Box(
                Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qr,
                        contentDescription = "Invite QR code",
                        modifier = Modifier.size(208.dp),
                    )
                }
            }
            if (inviteCode.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("INVITE CODE", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)
                    Text(inviteCode, style = CTType.headlineMd, color = CT.primaryFixed, letterSpacing = 6.sp)
                }
            }
        }
    }
}

/** Fires the Android share sheet with the invite deep link (iOS ShareSheetPresenter parity). */
private fun shareInvite(ctx: android.content.Context, inviteCode: String, title: String) {
    val text = if (inviteCode.isNotEmpty()) {
        // HTTPS App Link — tappable in every messaging app, opens the app if
        // installed and falls back to a web page + store links otherwise.
        "Join my ConvoTrack ride \"$title\"! Tap to open: ${ApiClient.joinLink(inviteCode)}"
    } else {
        "Join my ConvoTrack ride \"$title\"!"
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(android.content.Intent.createChooser(intent, "Share invite"))
}
