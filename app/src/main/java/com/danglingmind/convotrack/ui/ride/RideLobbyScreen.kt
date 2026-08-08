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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
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

@Composable
fun RideLobbyScreen(
    rideId: String,
    onBack: () -> Unit,
    onStartRide: () -> Unit,
    onEdit: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val socket = remember { ServiceLocator.socket }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<UiState<Ride>>(UiState.Loading) }
    var starting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(apiCall { api.getRide(rideId) })
        } catch (e: ApiException) {
            UiState.Error(e.message ?: "Couldn't load ride")
        } catch (e: Exception) {
            UiState.Error("Couldn't load ride")
        }
    }

    LaunchedEffect(rideId) { load() }

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        val leaderInLobby = (state as? UiState.Success)?.data?.let { r ->
            val myId = auth.userState.value?.id
            myId != null && r.leaderId == myId && r.status == "LOBBY"
        } ?: false
        ConvoTrackTopBar(
            title = (state as? UiState.Success)?.data?.title ?: "Lobby",
            showBack = true,
            onBack = onBack,
            actions = {
                // Leader can edit the ride while it's still in the lobby (iOS parity).
                if (leaderInLobby) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit ride",
                        tint = CT.primaryFixed,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onEdit(rideId) }.padding(9.dp),
                    )
                }
            },
        )

        when (val s = state) {
            is UiState.Loading -> LoadingBlock(Modifier.padding(top = 40.dp))
            is UiState.Error -> Box(Modifier.padding(20.dp)) {
                ErrorBlock(s.message, onRetry = { scope.launch { load() } })
            }
            is UiState.Success -> {
                val ride = s.data
                val myId = auth.userState.value?.id
                val amLeader = myId != null && ride.leaderId == myId

                // Track the live ride status locally. We do NOT auto-navigate on ACTIVE — the
                // lobby holds and shows a RESUME NAVIGATION button (iOS RideLobbyView parity).
                var liveStatus by remember(ride.id) { mutableStateOf(ride.status) }
                val isActive = liveStatus == "ACTIVE" || liveStatus == "IN_PROGRESS"

                // Live roster (membership) seeded from REST, then kept current by socket events.
                val riders = remember(ride.id) { mutableStateListOf(*ride.participants.toTypedArray()) }
                // Online presence is a SEPARATE, server-authoritative set driven only by the
                // heartbeat-based ride:presence snapshot — never inferred from roster membership
                // or socket connect/disconnect. Seeded with self so we don't flash offline.
                val onlineIds = remember(ride.id) {
                    mutableStateListOf<String>().apply { myId?.let { add(it) } }
                }
                fun setReady(userId: String) {
                    val i = riders.indexOfFirst { it.userId == userId }
                    if (i >= 0) riders[i] = riders[i].copy(status = "READY")
                }
                LaunchedEffect(ride.id) {
                    socket.connect()
                    socket.joinRoom(ride.id)
                    // Presence heartbeat while this screen is composed & foregrounded. Backgrounding
                    // suspends the socket (ConvoTrackApp), so emits no-op and the server TTL lapses
                    // us to offline — robust to locked/killed phones.
                    launch {
                        while (true) {
                            socket.emitHeartbeat(ride.id)
                            kotlinx.coroutines.delay(8_000)
                        }
                    }
                    // Authoritative green-dot source.
                    launch { socket.presence.collect { online -> onlineIds.clear(); onlineIds.addAll(online) } }
                    // Roster membership only (no presence side effects).
                    launch { socket.lobbyRoster.collect { roster -> riders.clear(); riders.addAll(roster.participants) } }
                    launch { socket.participantReady.collect { setReady(it) } }
                    launch {
                        socket.participantJoined.collect { p ->
                            if (riders.none { it.userId == p.userId }) riders.add(p)
                            // Optimistic online (presence snapshot reconciles) — avoids a grey flash.
                            if (!onlineIds.contains(p.userId)) onlineIds.add(p.userId)
                            // Robustness: if the socket payload lacks a real name, reconcile from
                            // REST (authoritative) so a new rider never lingers as a placeholder.
                            if (p.name.isBlank() || p.name == "Unknown") {
                                runCatching { apiCall { api.getRide(rideId) } }.getOrNull()?.let { fresh ->
                                    val i = riders.indexOfFirst { it.userId == p.userId }
                                    val real = fresh.participants.firstOrNull { it.userId == p.userId }
                                    if (i >= 0 && real != null) riders[i] = real
                                }
                            }
                        }
                    }
                    launch { socket.participantLeft.collect { uid -> riders.removeAll { it.userId == uid } } }
                    // A leader starting the ride flips status to ACTIVE — surface the RESUME
                    // button (don't auto-jump; the rider decides when to enter navigation).
                    launch { socket.stateUpdates.collect { if (it.status == "ACTIVE" || it.status == "IN_PROGRESS") liveStatus = it.status } }
                    // Leader edited the ride — refresh title/route/details live for everyone.
                    launch {
                        socket.rideUpdated.collect {
                            runCatching { apiCall { api.getRide(rideId) } }.getOrNull()?.let { state = UiState.Success(it) }
                        }
                    }
                }

                val readyCount = riders.count { it.status == "READY" }
                val myReady = myId != null && riders.firstOrNull { it.userId == myId }?.status == "READY"
                val others = riders.filter { !it.isLeader }
                val canStart = others.isEmpty() || others.all { it.status == "READY" }
                var showQr by remember { mutableStateOf(false) }

                // Leader-only rider management (remove from ride).
                var riderToManage by remember(ride.id) { mutableStateOf<RideParticipant?>(null) }
                var removing by remember(ride.id) { mutableStateOf(false) }
                var removeError by remember(ride.id) { mutableStateOf<String?>(null) }
                // Set when the leader removes *me* — surface a dialog then leave the ride.
                var removedFromRide by remember(ride.id) { mutableStateOf(false) }

                // Live removal: drop the rider from the roster for everyone; if it's me, exit.
                LaunchedEffect(ride.id) {
                    socket.participantRemoved.collect { uid ->
                        riders.removeAll { it.userId == uid }
                        onlineIds.remove(uid)
                        if (riderToManage?.userId == uid) { riderToManage = null; removing = false }
                        if (uid == myId) removedFromRide = true
                    }
                }

                var routeCoords by remember { mutableStateOf<List<LatLng>>(emptyList()) }
                // Draw the leader-selected polyline so every rider sees the same line; re-keyed on
                // the stored geometry + waypoints so a live ride:updated edit re-draws the route.
                LaunchedEffect(ride.routePolyline, ride.waypoints) { routeCoords = rideRoute(ride) }

                // Map with a floating stat-pill strip over its lower edge (iOS lobby mapSection).
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    ConvoTrackMap(
                        modifier = Modifier.matchParentSize(),
                        routeCoords = routeCoords,
                        markers = rideMarkers(ride),
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
                        item { FloatingStatPill(label = "DISTANCE", value = formatKm(ride.distanceMeters), unit = "km") }
                        item { FloatingStatPill(label = "RIDERS", value = "${riders.size}", unit = null) }
                        item {
                            FloatingStatPill(
                                label = "STATUS",
                                value = if (canStart) "Ready" else "Waiting",
                                unit = null,
                                dotColor = if (canStart) CT.primaryFixed else CT.tertiaryFixedDim,
                            )
                        }
                        if (ride.inviteCode.isNotEmpty()) {
                            item { FloatingStatPill(label = "CODE", value = ride.inviteCode, unit = null) }
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
                    if (socket.connected.value) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(CT.primaryFixed))
                            Text("LIVE", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.primaryFixed, letterSpacing = 2.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("$readyCount / ${riders.size} READY", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                    Spacer(Modifier.size(10.dp))
                    LobbyActionButton(Icons.Filled.Share, "Share invite") { shareInvite(ctx, ride.inviteCode, ride.title) }
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
                    QrCodeDialog(inviteCode = ride.inviteCode, onDismiss = { showQr = false })
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
                                    apiCall { api.removeParticipant(ride.id, rider.userId) }
                                    // The ride:participant_removed broadcast (which the leader
                                    // also receives) removes the rider from the roster.
                                    riderToManage = null
                                } catch (e: ApiException) {
                                    removeError = e.message ?: "Couldn't remove rider."
                                } catch (e: Exception) {
                                    removeError = "Couldn't remove rider."
                                } finally {
                                    removing = false
                                }
                            }
                        },
                        onDismiss = { if (!removing) { riderToManage = null; removeError = null } },
                    )
                }

                if (removedFromRide) {
                    RemovedFromRideDialog(onConfirm = { removedFromRide = false; onBack() })
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
                            onClick = { scope.launch { if (socket.emitReady(ride.id) && myId != null) setReady(myId) } },
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
                                            apiCall { api.startRide(ride.id) }
                                            onStartRide()
                                        } catch (e: ApiException) {
                                            startError = e.message ?: "Couldn't start the ride."
                                            starting = false
                                        } catch (e: Exception) {
                                            startError = "Couldn't start the ride."
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isMe) "${rider.name} (You)" else rider.name, style = CTType.bodyLg, color = CT.onSurface, maxLines = 1)
                if (rider.isLeader) {
                    Text(
                        "LEADER",
                        style = CTType.labelCaps.copy(fontSize = 8.sp),
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clip(CircleShape).background(CT.primaryFixed).padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                if (rider.isLeader) "RIDE LEADER" else "FORMATION RIDER",
                style = CTType.labelCaps.copy(fontSize = 10.sp),
                color = CT.onSurfaceVariant,
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
