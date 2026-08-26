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
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.rememberQrBitmap
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
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

                val readyCount = riders.count { it.status == "READY" }
                val others = riders.filter { !it.isLeader }
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

                // Map with a floating stat-pill strip over its lower edge (iOS lobby mapSection).
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    ConvoTrackMap(
                        modifier = Modifier.matchParentSize(),
                        routeCoords = routeCoords,
                        markers = rideMarkers(loadedRide),
                        interactive = false,
                    )
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
                }

                // Roster header: SQUAD ROSTER · LIVE indicator · share / QR actions.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("SQUAD ROSTER", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 4.sp)
                    Spacer(Modifier.size(10.dp))
                    if (connected) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(CT.primaryFixed))
                            Text("LIVE", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.primaryFixed, letterSpacing = 2.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("$readyCount / ${riders.size} READY", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
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

@Composable
private fun RiderRow(rider: RideParticipant, isMe: Boolean, isOnline: Boolean, onClick: (() -> Unit)? = null) {
    val ready = rider.status == "READY"
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
            if (ready) "READY" else "WAITING",
            style = CTType.labelCaps,
            color = if (ready) CT.onPrimaryContainer else CT.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.clip(CircleShape)
                .background(if (ready) CT.primaryContainer else CT.surfaceVariant)
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
