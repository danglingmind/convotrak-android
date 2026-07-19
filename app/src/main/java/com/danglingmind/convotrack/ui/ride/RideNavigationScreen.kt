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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.data.Rider
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.FloatingStatPill

@Composable
fun RideNavigationScreen(onEndRide: () -> Unit) {
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

        // Top HUD stat pills
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FloatingStatPill(label = "SPEED", value = "64", unit = "KM/H", dotColor = CT.primaryFixed)
            FloatingStatPill(label = "ETA", value = "24", unit = "MIN")
            FloatingStatPill(label = "SPREAD", value = "0.4", unit = "KM")
        }

        // Bottom overlay: rider strip + end button
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Dummy.navRiders) { RiderChip(it) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CT.errorContainer)
                    .clickable(onClick = onEndRide),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("END RIDE", style = CTType.labelCaps, color = CT.errorColor, letterSpacing = 1.sp)
            }
        }
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
