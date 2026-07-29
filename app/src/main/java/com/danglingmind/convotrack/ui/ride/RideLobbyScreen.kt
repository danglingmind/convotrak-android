package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.google.android.gms.maps.model.LatLng
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.launch

@Composable
fun RideLobbyScreen(
    rideId: String,
    onBack: () -> Unit,
    onStartRide: () -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val socket = remember { ServiceLocator.socket }
    val scope = rememberCoroutineScope()
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
        ConvoTrackTopBar(title = "Lobby", showBack = true, onBack = onBack)

        when (val s = state) {
            is UiState.Loading -> LoadingBlock(Modifier.padding(top = 40.dp))
            is UiState.Error -> Box(Modifier.padding(20.dp)) {
                ErrorBlock(s.message, onRetry = { scope.launch { load() } })
            }
            is UiState.Success -> {
                val ride = s.data
                val myId = auth.userState.value?.id
                val amLeader = myId != null && ride.leaderId == myId

                // A ride that's already running resumes straight into navigation (iOS parity).
                LaunchedEffect(ride.status) {
                    if (ride.status == "ACTIVE" || ride.status == "IN_PROGRESS") onStartRide()
                }

                // Live roster seeded from REST, then kept current by socket events.
                val riders = remember(ride.id) { mutableStateListOf(*ride.participants.toTypedArray()) }
                fun setReady(userId: String) {
                    val i = riders.indexOfFirst { it.userId == userId }
                    if (i >= 0) riders[i] = riders[i].copy(status = "READY")
                }
                LaunchedEffect(ride.id) {
                    socket.connect()
                    socket.joinRoom(ride.id)
                    launch { socket.lobbyRoster.collect { riders.clear(); riders.addAll(it.participants) } }
                    launch { socket.participantReady.collect { setReady(it) } }
                    launch { socket.participantJoined.collect { p -> if (riders.none { it.userId == p.userId }) riders.add(p) } }
                    launch { socket.participantLeft.collect { uid -> riders.removeAll { it.userId == uid } } }
                    // A leader starting the ride flips status to ACTIVE — follow it into navigation.
                    launch { socket.stateUpdates.collect { if (it.status == "ACTIVE" || it.status == "IN_PROGRESS") onStartRide() } }
                }

                val readyCount = riders.count { it.status == "READY" }
                val myReady = myId != null && riders.firstOrNull { it.userId == myId }?.status == "READY"

                var routeCoords by remember { mutableStateOf<List<LatLng>>(emptyList()) }
                LaunchedEffect(ride.id) { routeCoords = computeRoute(ride.waypoints) }

                ConvoTrackMap(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    routeCoords = routeCoords,
                    markers = rideMarkers(ride),
                    interactive = false,
                )

                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerLow)
                            .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("INVITE CODE", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                            Spacer(Modifier.size(4.dp))
                            Text(ride.inviteCode, style = CTType.headlineMd, color = CT.primaryFixed, letterSpacing = 6.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ContentCopy, null, tint = CT.primaryFixed, modifier = Modifier.size(22.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RIDERS", style = CTType.headlineMd, color = CT.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$readyCount / ${riders.size} READY",
                            style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 1.sp,
                        )
                    }
                }

                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(riders, key = { it.userId }) { RiderRow(it) }
                }

                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (startError != null) {
                        Text(startError!!, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    // Everyone can mark ready; the emit is acked by the server.
                    SecondaryButton(
                        text = if (myReady) "YOU'RE READY ✓" else "MARK READY",
                        enabled = !myReady,
                        highlighted = myReady,
                        onClick = { scope.launch { if (socket.emitReady(ride.id) && myId != null) setReady(myId) } },
                    )

                    if (amLeader) {
                        LimePrimaryButton(
                            text = if (starting) "STARTING…" else "START RIDE",
                            enabled = !starting,
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
private fun RiderRow(rider: RideParticipant) {
    val ready = rider.status == "READY"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(CT.surfaceContainerHigh)
                .border(if (rider.isLeader) 2.dp else 0.dp, CT.primaryFixed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, null, tint = if (rider.isLeader) CT.primaryFixed else CT.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(rider.name, style = CTType.bodyLg.copy(fontWeight = FontWeight.Bold), color = CT.onSurface)
            Text(
                if (rider.isLeader) "RIDE LEADER" else "RIDER",
                style = CTType.labelCaps,
                color = if (rider.isLeader) CT.primaryFixed else CT.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
        if (ready) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = CT.tertiaryFixed, modifier = Modifier.size(16.dp))
                Text("READY", style = CTType.labelCaps, color = CT.tertiaryFixed, letterSpacing = 1.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Schedule, null, tint = CT.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Text("WAITING", style = CTType.labelCaps, color = CT.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 1.sp)
            }
        }
    }
}
