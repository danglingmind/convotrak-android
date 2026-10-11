package com.danglingmind.convotrack.ui.ride

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.isNotFoundError
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.DestinationPhotoImage
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.rememberAvatarPhoto
import com.danglingmind.convotrack.ui.common.rememberDestinationHero
import com.danglingmind.convotrack.ui.common.rememberQrBitmap
import com.danglingmind.convotrack.ui.designsystem.Avatar
import com.danglingmind.convotrack.ui.designsystem.BottomNavFloating
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.DestructiveButton
import com.danglingmind.convotrack.ui.designsystem.DetailRow
import com.danglingmind.convotrack.ui.designsystem.DetailsList
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.DsBottomSheet
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsGradient
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSize
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsTab
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.FloatingTone
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.LiveIndicator
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.PulseDot
import com.danglingmind.convotrack.ui.designsystem.RosterState
import com.danglingmind.convotrack.ui.designsystem.SecondaryButton
import com.danglingmind.convotrack.ui.designsystem.SecondaryTone
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.SquareIconButton
import com.danglingmind.convotrack.ui.designsystem.StatTile
import com.danglingmind.convotrack.ui.designsystem.StatTone
import com.danglingmind.convotrack.ui.designsystem.StatusBadge
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.accentBar
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.NavCamera
import com.google.android.gms.maps.model.LatLng
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
    /** A tab picked from the bottom nav, which stays visible in the lobby. */
    onTab: (DsTab) -> Unit = {},
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
    // Invite code whose QR is showing; opened from the top menu or the roster header.
    var showQrFor by remember { mutableStateOf<String?>(null) }

    // Fetches the ride and starts the shared session. Idempotent, so re-entering the lobby from
    // navigation just re-joins the room rather than resetting the live roster.
    LaunchedEffect(rideId, myId) { sessionVm.load(myId.orEmpty()) }

    // The leader edited the ride — pull the authoritative copy so the title, route and stops
    // update live for everyone.
    LaunchedEffect(rideUpdated) { if (rideUpdated != null) sessionVm.refreshRide() }

    // The leader may edit in ANY non-COMPLETED state (LOBBY, ACTIVE and PAUSED are all editable);
    // editing a live ride re-routes everyone via ride:updated.
    val amLeader = myId != null && ride?.leaderId == myId
    val canEdit = amLeader && ride != null && ride?.status != "COMPLETED"
    // Riders only, in both LOBBY and ACTIVE — a rider who has to peel off mid-ride is exactly who
    // needs this. The leader has no equivalent: leaving would orphan the ride, so they cancel it
    // from the edit page instead (the server rejects a leader leave with LEADER_CANNOT_LEAVE).
    val canLeave = !amLeader && ride != null && ride?.status != "COMPLETED"

    val listState = rememberLazyListState()
    // The floating controls pick up a solid backing once the hero scrolls away under them.
    val topAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - 500) / 300f).coerceIn(0f, 1f)
        }
    }

    DsScreen {
        val loadedRide = ride
        when {
            loadedRide == null && !rideLoaded -> Column(Modifier.statusBarsPadding().padding(top = 72.dp)) { LoadingState() }
            loadedRide == null -> Box(Modifier.statusBarsPadding().padding(top = 72.dp).padding(DsSpace.gutter)) {
                ErrorState("Couldn't load this ride.", onRetry = { sessionVm.load(myId.orEmpty(), force = true) })
            }
            else -> LobbyContent(
                rideId = rideId,
                ride = loadedRide,
                sessionVm = sessionVm,
                riders = riders,
                onlineIds = onlineIds,
                liveStatus = liveStatus,
                connected = connected,
                myId = myId,
                amLeader = amLeader,
                starting = starting,
                startError = startError,
                onStart = {
                    if (starting) return@LobbyContent
                    starting = true
                    startError = null
                    scope.launch {
                        try {
                            apiCall { api.startRide(rideId) }
                            onStartRide()
                        } catch (e: ApiException) {
                            // The ride is already ACTIVE (a double tap, or another device started it
                            // first). The goal is met — navigate rather than erroring (iOS parity).
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
                onResume = onStartRide,
                onTab = onTab,
                listState = listState,
                onShowQr = { showQrFor = it },
            )
        }

        StatusBarScrim()

        // Floating controls over the hero: back · edit (leader) · more.
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(DsColor.bgBase.copy(alpha = 0.98f * topAlpha)) }
                .statusBarsPadding()
                .padding(horizontal = DsSpace.gutter)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FloatingButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (canEdit) FloatingButton(Icons.Filled.Edit, "Edit ride", { onEdit(rideId) }, iconSize = 20.dp)
            ride?.let { r ->
                LobbyMoreMenu(
                    canLeave = canLeave,
                    leaving = leaving,
                    onShare = { shareInvite(ctx, r.inviteCode, r.title) },
                    onShowQr = { showQrFor = r.inviteCode },
                    onLeave = { showLeaveConfirm = true },
                )
            }
        }
    }

    showQrFor?.let { code -> QrCodeDialog(inviteCode = code, onDismiss = { showQrFor = null }) }

    if (showLeaveConfirm) {
        ConfirmDialog(
            title = "Leave this ride?",
            onDismiss = { showLeaveConfirm = false },
            confirmLabel = "Leave ride",
            destructive = true,
            onConfirm = {
                showLeaveConfirm = false
                leaving = true
                leaveError = null
                scope.launch {
                    try {
                        apiCall { api.leaveRide(rideId) }
                        // Confirmation-FIRST, deliberately: tearing the session down optimistically
                        // would drop the socket before the server's ride:participant_left broadcast —
                        // the very event every other rider's roster and map pin removal hangs off.
                        sessionVm.endSession()
                        onBack()
                    } catch (e: ApiException) {
                        // "You aren't in this ride" counts as success: the rider asked to be out,
                        // and trapping them behind an error alert would be absurd.
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
            },
        ) {
            DialogText("You'll stop sharing your location and drop off the roster. You can re-join later with the invite code.")
        }
    }

    leaveError?.let { message ->
        ConfirmDialog(title = "Couldn't leave ride", onDismiss = { leaveError = null }, confirmLabel = "OK", dismissLabel = null, onConfirm = { leaveError = null }) {
            DialogText(message)
        }
    }

    // The leader removed me from the ride.
    if (wasRemoved) {
        ConfirmDialog(
            title = "Removed from ride",
            onDismiss = { sessionVm.endSession(); onBack() },
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = { sessionVm.endSession(); onBack() },
        ) { DialogText("The ride leader removed you from this ride.") }
    }

    // The leader deleted the whole ride. Non-leaders learn of it only through this broadcast; the
    // leader initiated it and already left via the edit page.
    if (wasDeleted && !amLeader) {
        ConfirmDialog(
            title = "Ride cancelled",
            onDismiss = { sessionVm.endSession(); onBack() },
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = { sessionVm.endSession(); onBack() },
        ) { DialogText("The ride leader cancelled this ride.") }
    }
}

@Composable
private fun LobbyContent(
    rideId: String,
    ride: Ride,
    sessionVm: RideSessionViewModel,
    riders: List<RideParticipant>,
    onlineIds: Set<String>,
    liveStatus: String,
    connected: Boolean,
    myId: String?,
    amLeader: Boolean,
    starting: Boolean,
    startError: String?,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onTab: (DsTab) -> Unit,
    listState: LazyListState,
    onShowQr: (String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val session = sessionVm.session

    // The ride is live once EITHER the REST copy or a state_update says so — the session's status
    // starts at LOBBY and only moves when the server broadcasts, so re-entering an already-ACTIVE
    // ride would otherwise show START RIDE.
    val isActive = liveStatus == "ACTIVE" || liveStatus == "IN_PROGRESS" ||
        ride.status == "ACTIVE" || ride.status == "IN_PROGRESS"

    // Optimistic local ready state: the tally and the button move the instant you tap rather than
    // waiting for the server's ride:participant_ready echo.
    var myReadyOptimistic by remember(rideId) { mutableStateOf(false) }
    val myReady = myReadyOptimistic || riders.firstOrNull { it.userId == myId }?.status == "READY"

    // Riders only. The leader has no MARK READY control (they get START instead), so counting them
    // would leave the tally permanently one short and looking stuck.
    val others = riders.filter { !it.isLeader }
    val readyCount = others.count { rider -> if (rider.userId == myId) myReady else rider.status == "READY" }
    val readyStatusText = when {
        others.isEmpty() -> "SOLO"
        readyCount == others.size -> "ALL READY"
        else -> "$readyCount/${others.size} READY"
    }
    val canStart = others.isEmpty() || others.all { it.status == "READY" }

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
    // Draw the leader-selected polyline so every rider sees the same line; re-keyed on the stored
    // geometry + waypoints so a live ride:updated edit re-draws the route.
    LaunchedEffect(ride.routePolyline, ride.waypoints) { routeCoords = rideRoute(ride) }

    // Destination photo hero — the image Google Maps shows for this place. Three-state, so the map
    // fallback never flashes before a photo.
    val heroPhase by rememberDestinationHero(
        rideId = ride.id,
        destinationName = ride.destinationName,
        lat = ride.destinationLat,
        lng = ride.destinationLng,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 200.dp),
        ) {
            item {
                LobbyHero(ride, heroPhase, routeCoords, isActive)
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = DsSpace.gutter)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(Icons.Filled.Route, "Distance", "${formatKm(ride.distanceMeters)} km")
                    StatTile(Icons.Filled.Group, "Riders", "${riders.size}")
                    StatTile(
                        Icons.Filled.Sensors,
                        "Status",
                        if (canStart) "Ready" else "Waiting",
                        tone = if (canStart) StatTone.Success else StatTone.Warning,
                    )
                    if (ride.inviteCode.isNotEmpty()) StatTile(Icons.Filled.Link, "Code", ride.inviteCode, tone = StatTone.Code)
                }
            }
            item {
                SectionHeading("Squad roster", Modifier.padding(horizontal = DsSpace.gutter).padding(top = 24.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // LIVE and the ready tally share one compact mono line so the heading keeps its room.
                        Column(horizontalAlignment = Alignment.End) {
                            if (connected) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PulseDot(DsColor.accent, 6.dp)
                                    Text("LIVE", style = DsType.badgeSmall, color = DsColor.accent)
                                }
                            }
                            Text(readyStatusText, style = DsType.badgeSmall, color = DsColor.textMuted, maxLines = 1)
                        }
                        SquareIconButton(Icons.Filled.Link, "Share invite", { shareInvite(ctx, ride.inviteCode, ride.title) }, size = 36.dp)
                        SquareIconButton(Icons.Filled.QrCode2, "Show QR code", { onShowQr(ride.inviteCode) }, size = 36.dp)
                    }
                }
            }
            // Leader first, then everyone else in roster order.
            items(riders.sortedByDescending { it.isLeader }, key = { it.userId }) { rider ->
                // Leader can tap any non-leader rider to open the management sheet.
                val canManage = amLeader && !rider.isLeader
                RosterRow(
                    rider = rider,
                    isMe = rider.userId == myId,
                    isOnline = onlineIds.contains(rider.userId),
                    rideActive = isActive,
                    onClick = if (canManage) ({ riderToManage = rider }) else null,
                    modifier = Modifier.padding(horizontal = DsSpace.gutter, vertical = 4.dp),
                )
            }
            item {
                SectionHeading("Ride details", Modifier.padding(horizontal = DsSpace.gutter).padding(top = 22.dp, bottom = 10.dp))
                RideDetails(ride, Modifier.padding(horizontal = DsSpace.gutter))
            }
        }

        // Sticky action above the floating nav.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DsGradient.bottomActionFade),
        ) {
            Column(
                Modifier.padding(horizontal = DsSpace.gutter).padding(top = 28.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (startError != null) InlineError(startError)
                if (isActive) {
                    // Ride already running — jump into navigation on tap (no auto-navigate).
                    PrimaryButton("Resume navigation", onResume, icon = Icons.Filled.Navigation)
                } else {
                    // Everyone can mark ready; the emit is acked by the server.
                    SecondaryButton(
                        text = if (myReady) "You're ready" else "Mark ready",
                        enabled = !myReady,
                        tone = if (myReady) SecondaryTone.Confirmed else SecondaryTone.Neutral,
                        onClick = {
                            myReadyOptimistic = true
                            scope.launch {
                                // Roll back if the server never acked it.
                                if (!session.markReady()) myReadyOptimistic = false
                            }
                        },
                    )
                    if (amLeader) {
                        PrimaryButton(
                            text = if (canStart) "Start ride" else "Waiting for riders",
                            onClick = onStart,
                            icon = Icons.Filled.Navigation,
                            enabled = canStart,
                            loading = starting,
                        )
                    } else {
                        Text(
                            "Waiting for the ride leader to start…",
                            style = DsType.bodySmall,
                            color = DsColor.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
            BottomNavFloating(active = null, onSelect = onTab)
        }
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
                        // The ride:participant_removed broadcast (which the leader also receives) is
                        // what removes the rider from the roster and closes this sheet.
                    } catch (e: Exception) {
                        removeError = e.riderMessage
                        removing = false
                    }
                }
            },
            onDismiss = { if (!removing) { riderToManage = null; removeError = null } },
        )
    }
}

/** Height of the lobby hero. Shared by the frame and the camera-fit clamp so they can't drift. */
private const val LOBBY_HERO_HEIGHT = 380f

@Composable
private fun LobbyHero(ride: Ride, heroPhase: DestinationHeroPhase, routeCoords: List<LatLng>, isActive: Boolean) {
    Box(Modifier.fillMaxWidth().height(LOBBY_HERO_HEIGHT.dp)) {
        // An explicit fit, not AutoFit: stop pins are bottom-anchored and tower above their
        // coordinate, and the title block covers the lower part of the hero.
        var cameraId by remember(ride.id) { mutableStateOf(0L) }
        LaunchedEffect(routeCoords, ride.id) { cameraId += 1 }
        when (heroPhase) {
            // Deliberately empty: showing the map here is what makes a ride switch flash map → photo.
            DestinationHeroPhase.Resolving -> Box(Modifier.matchParentSize().background(DsColor.cardSolid))
            is DestinationHeroPhase.Photo -> DestinationPhotoImage(
                bitmap = heroPhase.photo.bitmap,
                crop = true,
                modifier = Modifier.matchParentSize(),
            )
            DestinationHeroPhase.Unavailable -> ConvoTrackMap(
                modifier = Modifier.matchParentSize(),
                routeCoords = routeCoords,
                markers = rideMarkers(ride),
                stopCoords = rideStopCoords(ride),
                interactive = false,
                camera = if (routeCoords.isEmpty()) MapCamera.AutoFit else MapCamera.Fit(
                    id = cameraId,
                    coords = routeCoords,
                    topDp = NavCamera.STOP_PIN_TOP_EXTENT_DP + 56f,
                    leftDp = 40f,
                    bottomDp = 150f,
                    rightDp = 40f,
                ),
            )
        }
        Box(Modifier.matchParentSize().background(DsGradient.photoFade))

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = DsSpace.gutter).padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Eyebrow("Ride")
            Text(
                ride.title,
                style = DsType.heroTitle.copy(shadow = Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 12f)),
                color = DsColor.textOnPhoto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseDot(DsColor.accent, 8.dp)
                Text(if (isActive) "Riding" else "In lobby", style = DsType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = DsColor.textPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = DsColor.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(ride.destinationName, style = DsType.body, color = DsColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // Photographer credit. Required by Google's Places policy whenever a Place Photo is
                // displayed, so it renders WITH the image rather than being optional decoration.
                (heroPhase as? DestinationHeroPhase.Photo)?.photo?.attribution?.let { credit ->
                    Text(
                        "Photo: $credit · Google",
                        style = DsType.micro,
                        color = DsColor.textOnPhotoMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 170.dp)
                            .clip(DsShape.pill)
                            .background(DsColor.glassPill)
                            .border(1.dp, DsColor.borderOnPhoto, DsShape.pill)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RideDetails(ride: Ride, modifier: Modifier = Modifier) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val start = ride.waypoints.sortedBy { it.order }.firstOrNull { it.type == "START" }
    val minutes = ride.estimatedDurationSeconds / 60
    DetailsList(
        rows = listOfNotNull(
            start?.let { DetailRow(Icons.Filled.LocationOn, "Start point", it.name) },
            DetailRow(Icons.Outlined.Flag, "Destination", ride.destinationName) {
                SquareIconButton(
                    Icons.Filled.NearMe,
                    "Directions to the destination",
                    onClick = {
                        runCatching {
                            uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=${ride.destinationLat},${ride.destinationLng}")
                        }
                    },
                    size = 36.dp,
                )
            },
            DetailRow(Icons.Filled.Route, "Distance", "${formatKm(ride.distanceMeters)} km"),
            if (minutes > 0) DetailRow(Icons.Filled.Schedule, "Estimated time", if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes mins") else null,
        ),
        modifier = modifier,
    )
}

/**
 * The badge is a tri-state, not a boolean. The leader starts the ride rather than readying up, so a
 * leader must never render as WAITING; and once the ride is ACTIVE everyone is riding.
 */
private fun rosterState(rider: RideParticipant, rideActive: Boolean): RosterState = when {
    rideActive -> RosterState.Riding
    rider.isLeader || rider.status == "READY" -> RosterState.Ready
    else -> RosterState.Waiting
}

@Composable
private fun RosterRow(
    rider: RideParticipant,
    isMe: Boolean,
    isOnline: Boolean,
    rideActive: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val photo = rememberAvatarPhoto(rider.avatarUrl)
    val leader = rider.isLeader
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (leader) 72.dp else 60.dp)
            .glassSurface(
                DsShape.md,
                if (leader) DsColor.leaderRow else DsColor.cardQuiet,
                if (leader) DsColor.borderLeaderRow else DsColor.borderHairline,
            )
            .then(if (leader) Modifier.accentBar() else Modifier)
            .then(if (onClick != null) Modifier.dsClickable(pressedScale = 0.99f, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(rider.name, size = if (leader) 46.dp else 40.dp, photo = photo, colorKey = rider.userId, online = isOnline)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (isMe) "${rider.name} (You)" else rider.name, style = DsType.rowTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (leader) "RIDE LEADER" else "FORMATION RIDER",
                style = DsType.badgeSmall,
                color = if (leader) DsColor.accent else DsColor.textMuted,
            )
        }
        StatusBadge(rosterState(rider, rideActive))
        if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = DsColor.textMuted, modifier = Modifier.size(20.dp))
    }
}

/** Bottom sheet the leader uses to remove a rider from the ride (iOS RemoveRiderSheet parity). */
@Composable
private fun RemoveRiderSheet(
    rider: RideParticipant,
    removing: Boolean,
    error: String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val photo = rememberAvatarPhoto(rider.avatarUrl)
    DsBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = DsSpace.gutter).padding(top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Avatar(rider.name, size = 72.dp, photo = photo, colorKey = rider.userId)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rider.name, style = DsType.titleLarge, color = DsColor.textPrimary, maxLines = 1)
                Text(if (rider.isLeader) "RIDE LEADER" else "FORMATION RIDER", style = DsType.badgeSmall, color = DsColor.textMuted)
            }
            if (error != null) InlineError(error)
            DestructiveButton(if (removing) "Removing…" else "Remove from ride", onClick = onRemove, enabled = !removing)
            SecondaryButton("Cancel", onClick = onDismiss, enabled = !removing)
        }
    }
}

@Composable
private fun LobbyMoreMenu(
    canLeave: Boolean,
    leaving: Boolean,
    onShare: () -> Unit,
    onShowQr: () -> Unit,
    onLeave: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FloatingButton(Icons.Filled.MoreHoriz, "More", { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = DsColor.dialog,
            shape = DsShape.md,
            border = androidx.compose.foundation.BorderStroke(1.dp, DsColor.borderSubtle),
        ) {
            MenuItem(Icons.Filled.Share, "Share invite") { open = false; onShare() }
            MenuItem(Icons.Filled.QrCode2, "Show QR code") { open = false; onShowQr() }
            // Tinted, not filled: leaving is reversible (re-join with the code), unlike ending or
            // deleting a ride, so it doesn't earn the screen's only saturated red.
            if (canLeave) MenuItem(Icons.AutoMirrored.Filled.Logout, if (leaving) "Leaving…" else "Leave ride", danger = true, enabled = !leaving) { open = false; onLeave() }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (danger) DsColor.danger else DsColor.textPrimary
    DropdownMenuItem(
        text = { Text(label, style = DsType.body, color = color) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        enabled = enabled,
        colors = MenuDefaults.itemColors(),
    )
}

/** QR for the invite deep link (iOS QRCodeModal parity). */
@Composable
private fun QrCodeDialog(inviteCode: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DsShape.xl)
                    .background(DsColor.dialog)
                    .border(1.dp, DsColor.borderSubtle, DsShape.xl)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text("JOIN CONVOTRACK", style = DsType.headerTitle, color = DsColor.textPrimary)
                val qr = rememberQrBitmap(ApiClient.joinLink(inviteCode))
                Box(Modifier.size(240.dp).clip(DsShape.md).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                    if (qr != null) Image(bitmap = qr, contentDescription = "Invite QR code", modifier = Modifier.size(208.dp))
                }
                if (inviteCode.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("INVITE CODE", style = DsType.fieldLabel, color = DsColor.textMuted)
                        Text(inviteCode, style = DsType.codeDisplay, color = DsColor.accent)
                    }
                }
            }
            FloatingButton(
                Icons.Filled.Close,
                "Close",
                onDismiss,
                tone = FloatingTone.OnDark,
                size = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
    }
}

/** Fires the Android share sheet with the invite deep link (iOS ShareSheetPresenter parity). */
private fun shareInvite(ctx: android.content.Context, inviteCode: String, title: String) {
    val text = if (inviteCode.isNotEmpty()) {
        // HTTPS App Link — tappable in every messaging app, opens the app if installed and falls
        // back to a web page + store links otherwise.
        "Join my ConvoTrack ride \"$title\"! Tap to open: ${ApiClient.joinLink(inviteCode)}"
    } else {
        "Join my ConvoTrack ride \"$title\"!"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share invite"))
}

