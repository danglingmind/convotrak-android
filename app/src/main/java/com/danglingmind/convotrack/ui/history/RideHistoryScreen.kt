package com.danglingmind.convotrack.ui.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.HistoryRide
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.EmptyCard
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.dateLabel
import com.danglingmind.convotrack.ui.common.distanceLabel
import com.danglingmind.convotrack.ui.common.isCompleted
import com.danglingmind.convotrack.ui.common.durationLabel
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import kotlinx.coroutines.launch

@Composable
fun RideHistoryScreen(
    onOpenSummary: (rideId: String) -> Unit,
    onOpenRide: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<List<HistoryRide>>>(UiState.Loading) }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(apiCall { api.getMyRides().rides })
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding(),
    ) {
        ConvoTrackTopBar(title = "FLAGGED")
        when (val s = state) {
            is UiState.Loading -> LoadingBlock(Modifier.padding(top = 40.dp))
            is UiState.Error -> Box(Modifier.padding(20.dp)) {
                ErrorBlock(s.message, onRetry = { scope.launch { load() } })
            }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.padding(20.dp)) {
                        EmptyCard(
                            title = "No rides yet",
                            subtitle = "Rides you create or join will appear here once they're done.",
                            actionLabel = null,
                            onAction = null,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.rideId }) { ride ->
                            // Completed rides open their summary; anything still live (LOBBY/
                            // ACTIVE/PAUSED) opens the lobby — matches iOS RideHistoryView.handleTap.
                            HistoryCard(
                                ride,
                                onClick = { if (ride.isCompleted) onOpenSummary(ride.rideId) else onOpenRide(ride.rideId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(ride: HistoryRide, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(CT.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Flag, null, tint = CT.primaryFixed, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ride.title,
                    style = CTType.bodyLg.copy(fontWeight = FontWeight.Bold),
                    color = CT.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ride.isLeader == true) LeaderBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val date = ride.dateLabel()
                if (date.isNotEmpty()) {
                    Text(date, style = CTType.labelCaps, color = CT.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                }
                Text(ride.distanceLabel(), style = CTType.dataMono, color = CT.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                Text(ride.durationLabel(), style = CTType.dataMono, color = CT.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.size(2.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

/** Compact non-wrapping "LEADER" chip shown on rides the user led. */
@Composable
fun LeaderBadge() {
    Text(
        "LEADER",
        style = CTType.labelCaps.copy(fontSize = 10.sp),
        color = CT.primaryFixed,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CT.primaryFixed.copy(alpha = 0.14f))
            .border(1.dp, CT.primaryFixed.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
