package com.danglingmind.convotrack.ui.ride

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.data.Rider
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.FloatingStatPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideNavigationScreen(
    onExit: () -> Unit,
    onEndRide: () -> Unit,
) {
    var showRegroup by remember { mutableStateOf(false) }
    var activeRegroup by remember { mutableStateOf<RegroupReason?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Hardware back leaves navigation without ending the ride, matching the on-screen
    // back button (iOS `topBar` dismiss). Ending the ride is an explicit action only.
    BackHandler { onExit() }

    Box(Modifier.fillMaxSize().background(CT.surfaceDim)) {
        // Full-bleed map placeholder
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(CT.surfaceContainerHigh, CT.surfaceDim, CT.surfaceContainerLowest))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Map, null, tint = CT.onSurfaceVariant.copy(alpha = 0.25f), modifier = Modifier.size(64.dp))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar: back button + destination
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
                        .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape)
                        .clickable(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave navigation (ride keeps running)",
                        tint = CT.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text("TO", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.5.sp)
                    Text(Dummy.destinationName, style = CTType.bodyLg, color = CT.onSurface)
                }
            }

            // HUD stat pills
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FloatingStatPill(label = "SPEED", value = "64", unit = "KM/H", dotColor = CT.primaryFixed)
                FloatingStatPill(label = "ETA", value = "24", unit = "MIN")
                FloatingStatPill(label = "SPREAD", value = "0.4", unit = "KM")
            }

            // Active regroup banner
            if (activeRegroup != null) {
                val r = activeRegroup!!
                val accent = if (r.isEmergency) CT.errorColor else CT.primaryFixed
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (r.isEmergency) CT.errorContainer.copy(alpha = 0.2f) else CT.primaryFixed.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(r.icon, null, tint = accent, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("REGROUP ACTIVE", style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
                        Text(r.label, style = CTType.bodyMd, color = CT.onSurface)
                    }
                    Text(
                        "ARRIVED",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CT.primaryFixed)
                            .clickable { activeRegroup = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Bottom overlay: rider strip + controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Dummy.navRiders) { RiderChip(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onClick = onEndRide,
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
                activeRegroup = reason
                showRegroup = false
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
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RiderChip(rider: Rider) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(CT.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, if (rider.isLeader) CT.primaryFixed else CT.outline.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(CT.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null, tint = if (rider.isLeader) CT.primaryFixed else CT.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(rider.name, style = CTType.labelCaps, color = CT.onSurface)
            Text("${rider.speedKmh} KM/H", style = CTType.dataMono.copy(fontSize = 11.sp), color = CT.onSurfaceVariant)
        }
    }
}
