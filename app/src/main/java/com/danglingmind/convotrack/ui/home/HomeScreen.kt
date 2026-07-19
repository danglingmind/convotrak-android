package com.danglingmind.convotrack.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.data.RecentRide
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType

@Composable
fun HomeScreen(
    onCreateRide: () -> Unit,
    onJoinRide: () -> Unit,
    onOpenActiveRide: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CT.surfaceDim),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                // Action buttons
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        text = "CREATE RIDE",
                        icon = Icons.Filled.AddCircle,
                        background = CT.primaryFixed,
                        foreground = CT.onPrimaryFixed,
                        modifier = Modifier.weight(1f),
                        onClick = onCreateRide,
                    )
                    ActionButton(
                        text = "JOIN RIDE",
                        icon = Icons.Filled.PersonAdd,
                        background = CT.surfaceContainerHighest,
                        foreground = CT.onSurface,
                        bordered = true,
                        modifier = Modifier.weight(1f),
                        onClick = onJoinRide,
                    )
                }

                // Active ride
                ActiveRideCard(onClick = onOpenActiveRide)

                // Recent rides header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RECENT RIDES", style = CTType.headlineMd, color = CT.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "SEE ALL",
                        style = CTType.labelCaps,
                        color = CT.primaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable(onClick = onSeeAllHistory),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.padding(top = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Dummy.recentRides) { ride ->
                    RecentRideCard(ride, onClick = onOpenSummary)
                }
            }
        }

        // Floating top bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 20.dp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun ActiveRideCard(onClick: () -> Unit) {
    val ride = Dummy.activeRide
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVE RIDE", style = CTType.headlineMd, color = CT.onSurface)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(CT.primaryFixed))
            Spacer(Modifier.size(6.dp))
            Text("LIVE NOW", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(CT.surfaceContainerHigh, CT.surfaceDim))
                )
                .clickable(onClick = onClick),
        ) {
            // Dark bottom scrim
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.82f))
                    )
                )
            )
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(CT.surfaceContainerHighest.copy(alpha = 0.8f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CT.primaryFixed))
                    Spacer(Modifier.size(6.dp))
                    Text(Dummy.destinationName, style = CTType.labelCaps, color = Color.White, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ride.title, style = CTType.headlineMd, color = Color.White)
                        Text("5 Riders · RIDING", style = CTType.dataMono, color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "RESUME",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CT.primaryFixed)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRideCard(ride: RecentRide, onClick: () -> Unit) {
    val accent = if (ride.isCompleted) CT.outlineVariant.copy(alpha = 0.3f) else CT.primaryFixed.copy(alpha = 0.25f)
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
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (ride.isCompleted) CT.surfaceContainerHigh else CT.primaryFixed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Flag,
                null,
                tint = if (ride.isCompleted) CT.onSurfaceVariant else CT.primaryFixed,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ride.title, style = CTType.bodyLg.copy(fontWeight = FontWeight.Bold), color = CT.onSurface)
                Spacer(Modifier.weight(1f))
                if (!ride.isCompleted) {
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
                Text(ride.dateLabel, style = CTType.labelCaps, color = CT.onSurfaceVariant)
                Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                Text(ride.distanceLabel, style = CTType.dataMono, color = CT.onSurfaceVariant)
                if (ride.isLeader) {
                    Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("LEADER", style = CTType.labelCaps, color = CT.primaryFixed)
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}
