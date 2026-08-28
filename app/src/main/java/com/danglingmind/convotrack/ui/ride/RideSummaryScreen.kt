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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.RideSummary
import com.danglingmind.convotrack.data.net.SummaryParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.displayTitle
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.RideDuration
import com.danglingmind.convotrack.ui.common.UiState
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
                is UiState.Success -> SummaryContent(s.data, rideId)
            }
        }
        Column(Modifier.padding(20.dp)) {
            LimePrimaryButton(text = "DONE", onClick = onDone)
        }
    }
}

@Composable
private fun SummaryContent(summary: RideSummary, rideId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ServiceLocator.api }
    var isGeneratingShare by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        Icon(Icons.Filled.EmojiEvents, null, tint = CT.primaryFixed, modifier = Modifier.size(56.dp))
        Text("RIDE COMPLETE", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)

        // 2x2 metric grid (iOS metricsGrid). Four cards rather than three squeezed onto one row:
        // these are the headline numbers of a finished ride, and a third of the width can't hold a
        // value like "1:44" beside its unit at a readable size.
        val duration = RideDuration.stat(summary.durationSeconds)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "TOTAL DISTANCE",
                    value = formatKm(summary.distanceMeters),
                    unit = "KM",
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "DURATION",
                    value = duration.first,
                    unit = duration.second,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "AVG SPEED",
                    value = summary.avgSpeedKmh?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--",
                    unit = "KM/H",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "MAX GROUP SPLIT",
                    value = String.format(java.util.Locale.US, "%.1f", summary.maxGroupSplitMeters / 1000),
                    unit = "KM",
                    // Past ~2 km the pack was not riding together in any meaningful sense, which is
                    // the one number here worth flagging rather than merely reporting.
                    isError = summary.maxGroupSplitMeters > 2000,
                    modifier = Modifier.weight(1f),
                )
            }
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

        // Share the ride as a poster. The route is fetched here rather than held by the screen
        // because it is only ever needed if the rider actually taps share — most never do, and it
        // is a Directions round-trip.
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CT.surfaceContainerHighest)
                .border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = !isGeneratingShare) {
                    isGeneratingShare = true
                    scope.launch {
                        try {
                            val ride = runCatching { apiCall { api.getRide(rideId) } }.getOrNull()
                            val route = ride?.let { runCatching { rideRoute(it) }.getOrNull() }.orEmpty()
                            val card = ShareSummaryCard.render(
                                rideTitle = ride?.title ?: "ConvoTrack ride",
                                summary = summary,
                                routeCoordinates = route,
                            )
                            ShareSummaryCard.share(ctx, card, ride?.title ?: "ConvoTrack ride")
                        } finally {
                            isGeneratingShare = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isGeneratingShare) "GENERATING…" else "SHARE SUMMARY",
                style = CTType.labelCaps,
                color = CT.onSurface,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.size(8.dp))
    }
}

/**
 * Single-line text that shrinks until it fits, down to [minSp].
 *
 * The metric cards are a fixed height and a half-width column, but the values that go in them range
 * from "0" to "164:28" — and a 48sp "164:28" simply does not fit, so it was silently clipped to
 * "164:2" with its unit pushed off the card entirely. A wrong number is worse than a small one.
 *
 * Compose has no `minimumScaleFactor`, so the size is stepped down from the layout result and the
 * text is held back from drawing until it has settled — otherwise the first frame renders at full
 * size and the value visibly jumps as it shrinks.
 */
@Composable
private fun AutoShrinkText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSp: Float = 26f,
) {
    var fontSize by remember(text) { mutableStateOf(style.fontSize) }
    var settled by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize.value > minSp) {
                fontSize = (fontSize.value - 4f).sp
            } else {
                settled = true
            }
        },
    )
}

/**
 * One headline metric. Label above, big value + small unit on a shared baseline below.
 *
 * The value autoshrinks rather than wrapping or truncating: "1459.3" and "1:44" and "--" all have
 * to sit in the same fixed-height card, and a wrapped number reads as two numbers.
 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isError: Boolean = false,
) {
    Column(
        modifier
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = CTType.labelCaps,
            color = CT.onSurfaceVariant,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AutoShrinkText(
                text = value,
                style = CTType.displayMetrics,
                color = when {
                    isError -> CT.errorColor
                    isPrimary -> CT.primaryFixed
                    else -> CT.onSurface
                },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = CTType.labelCaps,
                    color = CT.onSurfaceVariant.copy(alpha = if (isError) 0.7f else 1f),
                    maxLines = 1,
                )
            }
        }
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
