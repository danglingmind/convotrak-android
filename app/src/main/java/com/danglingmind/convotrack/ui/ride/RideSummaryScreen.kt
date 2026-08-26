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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.RideSummary
import com.danglingmind.convotrack.data.net.SummaryParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.data.net.displayTitle
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.formatDuration
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.launch

@Composable
fun RideSummaryScreen(rideId: String, onDone: () -> Unit) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<RideSummary>>(UiState.Loading) }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(apiCall { api.getRideSummary(rideId) })
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(rideId) { load() }

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (val s = state) {
                is UiState.Loading -> LoadingBlock(Modifier.padding(top = 40.dp))
                is UiState.Error -> Box(Modifier.padding(20.dp)) {
                    ErrorBlock(s.message, onRetry = { scope.launch { load() } })
                }
                is UiState.Success -> SummaryContent(s.data)
            }
        }
        Column(Modifier.padding(20.dp)) {
            LimePrimaryButton(text = "DONE", onClick = onDone)
        }
    }
}

@Composable
private fun SummaryContent(summary: RideSummary) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        Icon(Icons.Filled.EmojiEvents, null, tint = CT.primaryFixed, modifier = Modifier.size(56.dp))
        Text("RIDE COMPLETE", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)

        // Big metrics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(formatKm(summary.distanceMeters), "KM", Modifier.weight(1f))
            MetricTile(formatDuration(summary.durationSeconds), "TIME", Modifier.weight(1f))
            MetricTile(
                summary.avgSpeedKmh?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--",
                "AVG KM/H",
                Modifier.weight(1f),
            )
        }

        // Awards (per-participant titles assigned by the backend)
        if (summary.participants.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CT.surfaceContainerLow)
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AWARDS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                summary.participants.forEach { AwardRow(it) }
            }
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
private fun AwardRow(p: SummaryParticipant) {
    val title = displayTitle(p.rideTitle)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(CT.surfaceContainerHigh).border(2.dp, CT.primaryFixed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, null, tint = CT.primaryFixed, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, style = CTType.bodyLg, color = CT.onSurface)
            if (title.isNotEmpty()) {
                Text(title, style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 1.sp)
            }
        }
        if (p.syncScore != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(p.syncScore.toString(), style = CTType.headlineMd, color = CT.tertiaryFixed)
                Text("SYNC", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.sp)
            }
        }
    }
}
