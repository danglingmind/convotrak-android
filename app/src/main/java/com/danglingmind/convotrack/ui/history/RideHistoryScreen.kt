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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.data.HistoryRideItem
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar

@Composable
fun RideHistoryScreen(onOpenSummary: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CT.surfaceDim)
            .statusBarsPadding(),
    ) {
        ConvoTrackTopBar(title = "FLAGGED")
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Dummy.historyRides) { ride ->
                HistoryCard(ride, onClick = onOpenSummary)
            }
        }
    }
}

@Composable
private fun HistoryCard(ride: HistoryRideItem, onClick: () -> Unit) {
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
        val medal = when (ride.rank) {
            1 -> Color(0xFFFFD54F)
            2 -> Color(0xFFB0BEC5)
            3 -> Color(0xFFBCAAA4)
            else -> CT.onSurfaceVariant
        }
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(CT.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = medal, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(ride.title, style = CTType.bodyLg.copy(fontWeight = FontWeight.Bold), color = CT.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ride.dateLabel, style = CTType.labelCaps, color = CT.onSurfaceVariant)
                Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                Text(ride.distanceLabel, style = CTType.dataMono, color = CT.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ride.durationLabel, style = CTType.dataMono, color = CT.onSurfaceVariant)
                Text("·", color = CT.onSurfaceVariant.copy(alpha = 0.5f))
                Text("${ride.riderCount} RIDERS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.size(2.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}
