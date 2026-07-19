package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.data.Rider
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

@Composable
fun RideLobbyScreen(onBack: () -> Unit, onStartRide: () -> Unit) {
    val readyCount = Dummy.lobbyRiders.count { it.isReady }

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        ConvoTrackTopBar(title = "Lobby", showBack = true, onBack = onBack)

        // Non-interactive map preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.linearGradient(listOf(CT.surfaceContainerHigh, CT.surfaceContainerLow))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Map, null, tint = CT.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Invite code
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerLow)
                    .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("INVITE CODE", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                    Spacer(Modifier.size(4.dp))
                    Text(Dummy.inviteCode, style = CTType.headlineMd, color = CT.primaryFixed, letterSpacing = 6.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ContentCopy, null, tint = CT.primaryFixed, modifier = Modifier.size(22.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RIDERS", style = CTType.headlineMd, color = CT.onSurface)
                Spacer(Modifier.weight(1f))
                Text("$readyCount / ${Dummy.lobbyRiders.size} READY", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 1.sp)
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Dummy.lobbyRiders) { RiderRow(it) }
        }

        Column(Modifier.padding(20.dp)) {
            LimePrimaryButton(text = "START RIDE", onClick = onStartRide)
        }
    }
}

@Composable
private fun RiderRow(rider: Rider) {
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
            Text(rider.title.uppercase(), style = CTType.labelCaps, color = if (rider.isLeader) CT.primaryFixed else CT.onSurfaceVariant, letterSpacing = 1.sp)
        }
        if (rider.isReady) {
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
