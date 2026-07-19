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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.Dummy
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

@Composable
fun RideSummaryScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.EmojiEvents, null, tint = CT.primaryFixed, modifier = Modifier.size(56.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RIDE COMPLETE", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)
                Spacer(Modifier.size(6.dp))
                Text(Dummy.activeRide.title, style = CTType.headlineLg, color = CT.onSurface, textAlign = TextAlign.Center)
            }

            // Big metrics
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("42.6", "KM", Modifier.weight(1f))
                MetricTile("1h 12m", "TIME", Modifier.weight(1f))
                MetricTile("58", "AVG KM/H", Modifier.weight(1f))
            }

            // Podium / titles
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CT.surfaceContainerLow)
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AWARDS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                AwardRow("You", "Ride Leader", CT.primaryFixed)
                AwardRow("Marco Diaz", "Pace Keeper", CT.tertiaryFixed)
                AwardRow("Aisha Khan", "Trail Guardian", Color(0xFFFFD54F))
            }
        }

        Column(Modifier.padding(20.dp)) {
            LimePrimaryButton(text = "DONE", onClick = onDone)
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = CTType.headlineMd, color = CT.primaryFixed)
        Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
    }
}

@Composable
private fun AwardRow(name: String, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(CT.surfaceContainerHigh).border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = CTType.bodyLg, color = CT.onSurface)
            Text(title.uppercase(), style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
        }
    }
}
