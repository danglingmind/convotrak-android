package com.danglingmind.convotrack.ui.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.net.HistoryRide
import com.danglingmind.convotrack.data.net.RideStatusFilter
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.PhotoCredits
import com.danglingmind.convotrack.ui.common.RideRowCard
import com.danglingmind.convotrack.ui.common.RideRowSkeleton
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.isCompleted
import com.danglingmind.convotrack.ui.designsystem.ConnectionStatusButton
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.EmptyState
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.LogoBadge
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.StatTile
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.bottomNavClearance
import kotlinx.coroutines.launch

@Composable
fun RideHistoryScreen(
    onOpenSummary: (rideId: String) -> Unit,
    onOpenRide: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<List<HistoryRide>>>(UiState.Loading) }
    val credits = remember { mutableStateMapOf<String, String>() }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(
                // COMPLETED only. The endpoint caps at 50 rows, so filtering server-side is also
                // what stops a busy rider's finished rides being crowded out — and an ACTIVE ride
                // has no duration or summary to show in a history row.
                apiCall { api.getMyRides(RideStatusFilter.query(RideStatusFilter.COMPLETED)) }.rides,
            )
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(Unit) { load() }

    DsScreen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomNavClearance())) {
            item { HistoryHeader() }
            when (val s = state) {
                is UiState.Loading -> items(3) {
                    RideRowSkeleton(Modifier.padding(horizontal = DsSpace.gutter, vertical = 4.dp))
                }
                is UiState.Error -> item {
                    ErrorState(s.message, onRetry = { scope.launch { load() } }, Modifier.padding(DsSpace.gutter))
                }
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Flag,
                                title = "No rides yet",
                                body = "Rides you create or join will appear here once they're done.",
                                modifier = Modifier.padding(DsSpace.gutter),
                            )
                        }
                    } else {
                        item { HistoryTotals(s.data) }
                        items(s.data, key = { it.rideId }) { ride ->
                            // Completed rides open their summary; anything still live (LOBBY/ACTIVE/
                            // PAUSED) opens the lobby — matches iOS RideHistoryView.handleTap.
                            RideRowCard(
                                ride,
                                onClick = { if (ride.isCompleted) onOpenSummary(ride.rideId) else onOpenRide(ride.rideId) },
                                onAttribution = { credit -> if (credit != null) credits[ride.rideId] = credit else credits.remove(ride.rideId) },
                                modifier = Modifier.padding(horizontal = DsSpace.gutter, vertical = 4.dp),
                            )
                        }
                        item { PhotoCredits(credits.values, Modifier.padding(horizontal = DsSpace.gutter)) }
                    }
                }
            }
        }
        StatusBarScrim()
    }
}

/** Road photo, logo + connection, then the serif title. */
@Composable
private fun HistoryHeader() {
    Box {
        PhotoBackdrop(height = 240.dp, drawable = R.drawable.hero_road, verticalBias = 0.5f, alpha = 0.8f)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = DsSpace.gutter).padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge()
                Spacer(Modifier.weight(1f))
                // Hidden for now: the top-right slot is reserved for a notification bell.
                // ConnectionStatusButton()
            }
            Spacer(Modifier.height(64.dp))
            Eyebrow("Your rides")
            Spacer(Modifier.height(6.dp))
            Text("Ride history", style = DsType.heroTitle, color = DsColor.textOnPhoto)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Totals across the finished rides in THIS RESPONSE. `/rides/me` caps at 50 rows and there is no
 * aggregate endpoint, so these are not lifetime figures and the labels say what is counted.
 */
@Composable
private fun HistoryTotals(rides: List<HistoryRide>) {
    val completed = rides.filter { it.isCompleted }
    val totalKm = completed.sumOf { it.distanceMeters } / 1000
    // Distance-WEIGHTED, not a mean of per-ride means: an unweighted average lets a 3 km errand
    // count as much as a 400 km tour. Rides without a recorded speed are excluded from both sides.
    val timed = completed.filter { it.avgSpeedKmh != null && it.distanceMeters > 0 }
    val avgSpeed = timed.takeIf { it.isNotEmpty() }?.let { rs ->
        rs.sumOf { it.avgSpeedKmh!! * it.distanceMeters } / rs.sumOf { it.distanceMeters }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DsSpace.gutter)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(Icons.Filled.Route, "Distance · last ${completed.size}", String.format(java.util.Locale.US, "%.0f km", totalKm))
        StatTile(Icons.Filled.Flag, "Rides shown", "${completed.size}")
        StatTile(Icons.Filled.Speed, "Avg speed", avgSpeed?.let { String.format(java.util.Locale.US, "%.0f km/h", it) } ?: "--")
    }
}
