package com.danglingmind.convotrack.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.HistoryRide
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.history.LeaderBadge
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.ride.rideRoute
import com.google.android.gms.maps.model.LatLng
import androidx.compose.ui.graphics.Brush
import com.danglingmind.convotrack.ui.common.EmptyCard
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.dateLabel
import com.danglingmind.convotrack.ui.common.distanceLabel
import com.danglingmind.convotrack.ui.common.isCompleted
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onCreateRide: () -> Unit,
    onJoinRide: () -> Unit,
    onOpenRide: (rideId: String) -> Unit,
    onSeeAllHistory: () -> Unit,
    onOpenSummary: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<List<HistoryRide>>>(UiState.Loading) }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(apiCall { api.getMyRides().rides })
        } catch (e: ApiException) {
            UiState.Error(e.message ?: "Couldn't load rides")
        } catch (e: Exception) {
            UiState.Error("Couldn't load rides")
        }
    }

    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize().background(CT.surfaceDim)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))

            // Action buttons (always visible)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton("CREATE RIDE", Icons.Filled.AddCircle, CT.primaryFixed, CT.onPrimaryFixed, Modifier.weight(1f), onClick = onCreateRide)
                ActionButton("JOIN RIDE", Icons.Filled.PersonAdd, CT.surfaceContainerHighest, CT.onSurface, Modifier.weight(1f), bordered = true, onClick = onJoinRide)
            }

            when (val s = state) {
                is UiState.Loading -> LoadingBlock(Modifier.padding(top = 40.dp))
                is UiState.Error -> Box(Modifier.padding(20.dp)) {
                    ErrorBlock(s.message, onRetry = { scope.launch { load() } })
                }
                is UiState.Success -> {
                    val active = s.data.firstOrNull { !it.isCompleted }
                    val recent = s.data.filter { it.rideId != active?.rideId }.take(5)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (active != null) {
                            item { ActiveRideSection(active, onClick = { onOpenRide(active.rideId) }) }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(top = if (active != null) 12.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("RECENT RIDES", style = CTType.headlineMd, color = CT.onSurface)
                                Spacer(Modifier.weight(1f))
                                if (recent.isNotEmpty()) {
                                    Text(
                                        "SEE ALL",
                                        style = CTType.labelCaps,
                                        color = CT.primaryFixed,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.clickable(onClick = onSeeAllHistory),
                                    )
                                }
                            }
                        }
                        if (recent.isEmpty()) {
                            item {
                                EmptyCard(
                                    title = "No rides yet",
                                    subtitle = "Create or join your first ride and it'll show up here.",
                                    actionLabel = "Create Ride",
                                    onAction = onCreateRide,
                                )
                            }
                        } else {
                            items(recent, key = { it.rideId }) { ride ->
                                RecentRideCard(
                                    ride,
                                    onClick = { if (ride.isCompleted) onOpenSummary(ride.rideId) else onOpenRide(ride.rideId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.logo_circle), null, Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.SettingsInputAntenna, null, tint = CT.primaryFixed, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = CTType.labelCaps, color = foreground, letterSpacing = 1.sp)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "LOBBY" -> "IN LOBBY"
    "ACTIVE", "IN_PROGRESS" -> "RIDING"
    else -> status
}

@Composable
private fun ActiveRideSection(ride: HistoryRide, onClick: () -> Unit) {
    val api = remember { ServiceLocator.api }
    // Fetch the full ride to draw its route as the card background (iOS ActiveRideCard).
    var route by remember(ride.rideId) { mutableStateOf<List<LatLng>>(emptyList()) }
    LaunchedEffect(ride.rideId) {
        val full = runCatching { apiCall { api.getRide(ride.rideId) } }.getOrNull()
        full?.let { route = rideRoute(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVE RIDE", style = CTType.headlineMd, color = CT.onSurface)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(CT.primaryFixed))
            Spacer(Modifier.size(6.dp))
            Text(statusLabel(ride.status), style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .background(CT.surfaceContainerHigh),
        ) {
            if (route.isNotEmpty()) {
                ConvoTrackMap(
                    modifier = Modifier.matchParentSize(),
                    routeCoords = route,
                    interactive = false,
                )
            }
            // Dark scrim so the title/RESUME stay legible over the map. It also sits above the
            // map view and carries the click, since the (non-interactive) map consumes touches.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
            )
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ride.title, style = CTType.headlineMd, color = Color.White)
                        Text(ride.distanceLabel(), style = CTType.dataMono, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "RESUME",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clip(CircleShape).background(CT.primaryFixed).padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRideCard(ride: HistoryRide, onClick: () -> Unit) {
    val completed = ride.isCompleted
    val accent = if (completed) CT.outlineVariant.copy(alpha = 0.3f) else CT.primaryFixed.copy(alpha = 0.25f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CT.surfaceContainerLow)
            .border(1.dp, accent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                .background(if (completed) CT.surfaceContainerHigh else CT.primaryFixed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Flag, null, tint = if (completed) CT.onSurfaceVariant else CT.primaryFixed, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ride.title, style = CTType.bodyLg.copy(fontWeight = FontWeight.Bold), color = CT.onSurface)
                Spacer(Modifier.weight(1f))
                if (!completed) {
                    Text(
                        "RESUME",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clip(CircleShape).background(CT.primaryFixed).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val date = ride.dateLabel()
                if (date.isNotEmpty()) {
                    Text(date, style = CTType.labelCaps, color = CT.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                }
                Text(ride.distanceLabel(), style = CTType.dataMono, color = CT.onSurfaceVariant, maxLines = 1, softWrap = false)
                if (ride.isLeader == true) {
                    Spacer(Modifier.size(2.dp))
                    LeaderBadge()
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}
