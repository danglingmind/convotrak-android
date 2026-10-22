package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.ui.designsystem.PhotoCredit
import com.danglingmind.convotrack.ui.designsystem.PromoBanner
import com.danglingmind.convotrack.ui.common.rememberPlanState
import com.danglingmind.convotrack.ui.common.PlanState
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.Access
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideSummary
import com.danglingmind.convotrack.data.net.SummaryParticipant
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.displayTitle
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.DestinationPhotoImage
import com.danglingmind.convotrack.ui.common.RideDetailsCache
import com.danglingmind.convotrack.ui.common.RideDuration
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.rememberAvatarPhoto
import com.danglingmind.convotrack.ui.common.rememberDestinationHero
import com.danglingmind.convotrack.ui.designsystem.Avatar
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsGradient
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.MetaChip
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.SquareIconButton
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun RideSummaryScreen(rideId: String, onDone: () -> Unit) {
    val api = remember { ServiceLocator.api }
    val plan = rememberPlanState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<UiState<RideSummary>>(UiState.Loading) }
    var ride by remember { mutableStateOf<Ride?>(null) }
    var isGeneratingShare by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(apiCall { api.getRideSummary(rideId) })
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(rideId) {
        // The ride itself (title, destination) changed at the end; drop any cached copy.
        RideDetailsCache.invalidate(rideId)
        ride = RideDetailsCache.get(rideId)
    }
    LaunchedEffect(rideId) { load() }

    val heroPhase by rememberDestinationHero(
        rideId = ride?.id,
        destinationName = ride?.destinationName,
        lat = ride?.destinationLat,
        lng = ride?.destinationLng,
    )

    DsScreen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                when (val p = heroPhase) {
                    is DestinationHeroPhase.Photo -> Box(Modifier.fillMaxWidth().height(320.dp)) {
                        DestinationPhotoImage(p.photo.bitmap, crop = true, modifier = Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(DsGradient.photoFade))
                    }
                    // No destination photo: the road at sunset stands in.
                    else -> PhotoBackdrop(height = 320.dp, drawable = R.drawable.hero_road, verticalBias = 0.62f)
                }
                // Photographer credit: Google's Places policy requires it wherever a Place Photo is shown.
                (heroPhase as? DestinationHeroPhase.Photo)?.photo?.attribution?.let { credit ->
                    Box(Modifier.fillMaxWidth().height(320.dp)) { PhotoCredit(credit, Modifier.align(Alignment.BottomEnd)) }
                }
                Text(
                    "RIDE SUMMARY",
                    style = DsType.headerTitle,
                    color = DsColor.textPrimary,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp),
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = DsSpace.gutter).padding(top = 170.dp, bottom = 150.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = DsColor.accent, modifier = Modifier.size(18.dp))
                        Eyebrow("Ride complete", color = DsColor.accent)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(ride?.title ?: "", style = DsType.heroTitle, color = DsColor.textOnPhoto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(20.dp))

                    when (val s = state) {
                        is UiState.Loading -> LoadingState()
                        is UiState.Error -> ErrorState(s.message, onRetry = { scope.launch { load() } })
                        is UiState.Success -> SummaryContent(s.data)
                    }
                    // Right after a ride is when Pro is easiest to want.
                    if (!plan.isPremium) {
                        Spacer(Modifier.height(20.dp))
                        PromoBanner(
                            message = summaryPromoMessage(plan),
                            actionLabel = "Go Pro",
                            onAction = { Paywall.present(PaywallSource.RideSummary) },
                            goProStyle = true,
                        )
                    }
                }
            }
        }
        StatusBarScrim()

        // Sticky: share (secondary, square) + DONE.
        shareError?.let { message ->
            InlineError(
                message,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = DsSpace.gutter)
                    .padding(bottom = 84.dp),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DsGradient.bottomActionFade)
                .navigationBarsPadding()
                .padding(horizontal = DsSpace.gutter)
                .padding(top = 28.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val summary = (state as? UiState.Success)?.data
            if (summary != null) {
                // The route is fetched only when the rider actually shares — most never do, and it
                // is a Directions round-trip.
                SquareIconButton(
                    Icons.Filled.Share,
                    "Share summary",
                    size = 56.dp,
                    shape = DsShape.lg,
                    busy = isGeneratingShare,
                    onClick = {
                        if (isGeneratingShare) return@SquareIconButton
                        isGeneratingShare = true
                        shareError = null
                        scope.launch {
                            try {
                                val r = ride ?: runCatching { apiCall { api.getRide(rideId) } }.getOrNull()
                                val title = r?.title ?: "ConvoTrack ride"
                                // A card made on an earlier share of THIS summary data is reused as-is:
                                // no re-render and no Directions or Static Maps calls. Changed data
                                // (or an edited route) hashes to a new key and re-renders.
                                val cardKey = ShareSummaryCard.cacheKey(title, summary, r)
                                val card = ShareSummaryCard.cachedCard(cardKey) ?: run {
                                    val route = r?.let { runCatching { rideRoute(it) }.getOrNull() }.orEmpty()
                                    val bitmap = ShareSummaryCard.render(context = ctx, rideTitle = title, summary = summary, routeCoordinates = route)
                                    ShareSummaryCard.saveCard(cardKey, rideId, bitmap)
                                }
                                ShareSummaryCard.share(ctx, card, title)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // A failed share must never take the app down; say so and let the
                                // rider try again.
                                shareError = "Couldn't create the summary image. Try again."
                            } finally {
                                isGeneratingShare = false
                            }
                        }
                    },
                )
            }
            PrimaryButton("Done", onClick = onDone, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryContent(summary: RideSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dateLabel(summary.createdAt)?.let { MetaChip(Icons.Filled.CalendarToday, it, onPhoto = false) }
            MetaChip(Icons.Filled.Group, "${summary.participants.size} rider${if (summary.participants.size == 1) "" else "s"}", onPhoto = false)
        }

        // 2x2 metric grid: four cards rather than three squeezed onto one row — a third of the
        // width can't hold a value like "1:44" beside its unit at a readable size.
        val duration = RideDuration.stat(summary.durationSeconds)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryMetric("Total distance", formatKm(summary.distanceMeters), "KM", Modifier.weight(1f), primary = true)
            SummaryMetric("Duration", duration.first, duration.second, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryMetric("Avg speed", summary.avgSpeedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: "--", "KM/H", Modifier.weight(1f))
            SummaryMetric(
                "Max group split",
                String.format(Locale.US, "%.1f", summary.maxGroupSplitMeters / 1000),
                "KM",
                Modifier.weight(1f),
                // Past ~2 km the pack was not riding together in any meaningful sense — the one
                // number here worth flagging rather than merely reporting.
                alert = summary.maxGroupSplitMeters > 2000,
            )
        }

        if (summary.participants.isNotEmpty()) {
            SectionHeading("Awards", Modifier.padding(top = 12.dp, bottom = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.participants.forEach { AwardRow(it) }
            }
        }
    }
}

private fun dateLabel(iso: String): String? {
    if (iso.isBlank()) return null
    val millis = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return null
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
}

/**
 * Single-line text that shrinks until it fits, down to [minSp]. A wrong number is worse than a
 * small one: a 44sp "164:28" doesn't fit a half-width card, so the size steps down from the layout
 * result and the text is held back from drawing until it has settled.
 */
@Composable
private fun AutoShrinkText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, minSp: Float = 26f) {
    var fontSize by remember(text) { mutableStateOf(style.fontSize) }
    var settled by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = fontSize, lineHeight = fontSize),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            // Height as well as width: at a large system font scale the value overflows vertically.
            val overflowed = result.didOverflowWidth || result.didOverflowHeight
            if (overflowed && fontSize.value > minSp) fontSize = (fontSize.value - 4f).sp else settled = true
        },
    )
}

@Composable
private fun SummaryMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier, primary: Boolean = false, alert: Boolean = false) {
    Column(
        modifier
            .height(112.dp)
            .glassSurface(
                DsShape.lg,
                DsColor.card,
                when {
                    alert -> DsColor.dangerBorder
                    primary -> DsColor.accentBorder
                    else -> DsColor.borderSubtle
                },
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label.uppercase(), style = DsType.statLabel, color = DsColor.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (alert) Icon(Icons.Filled.Warning, "Group split over 2 km", tint = DsColor.danger, modifier = Modifier.size(14.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AutoShrinkText(
                value,
                DsType.metricDisplay,
                when {
                    alert -> DsColor.danger
                    primary -> DsColor.accent
                    else -> DsColor.textPrimary
                },
                Modifier.weight(1f, fill = false),
            )
            if (unit.isNotEmpty()) Text(unit, style = DsType.badgeSmall, color = DsColor.textMuted, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun AwardRow(p: SummaryParticipant) {
    val title = displayTitle(p.rideTitle)
    val photo = rememberAvatarPhoto(p.avatarUrl)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).glassSurface(DsShape.md, DsColor.cardQuiet, DsColor.borderHairline).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p.name.ifBlank { "Rider" }, size = 40.dp, photo = photo, colorKey = p.userId, ring = DsColor.accentBorder)
        Column(Modifier.weight(1f)) {
            Text(p.name.ifBlank { "Rider" }, style = DsType.rowTitle, color = DsColor.textPrimary, maxLines = 1)
            if (title.isNotEmpty()) Text(title.uppercase(), style = DsType.badgeSmall, color = DsColor.accent)
        }
        if (p.syncScore != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(p.syncScore.toString(), style = DsType.statValue.copy(fontFamily = DsType.badge.fontFamily), color = DsColor.success)
                Text("SYNC", style = DsType.badgeSmall, color = DsColor.textMuted)
            }
        }
    }
}


private fun summaryPromoMessage(plan: PlanState): String = when (plan.access) {
    is Access.Trial -> plan.trialDaysLeft?.takeIf { it > 1 }
        ?.let { "Good ride. $it days left in your trial — go Pro to keep riding." }
        ?: "Good ride. Your free trial ends today — go Pro to keep riding."
    Access.TrialExpired -> "Good ride. Go Pro to start the next one."
    Access.Pro, Access.Free -> "Bigger packs and every ride kept, with Pro."
}
