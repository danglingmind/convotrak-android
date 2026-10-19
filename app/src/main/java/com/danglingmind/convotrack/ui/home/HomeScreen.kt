package com.danglingmind.convotrack.ui.home

import com.danglingmind.convotrack.data.upsell.PaywallAnalytics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.data.net.HistoryRide
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.RideStatusFilter
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.DestinationPhotoImage
import com.danglingmind.convotrack.ui.common.PhotoCredits
import com.danglingmind.convotrack.ui.common.RideDetailsCache
import com.danglingmind.convotrack.ui.common.RideRowCard
import com.danglingmind.convotrack.ui.common.RideRowSkeleton
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.ui.common.PlanBadgeButton
import com.danglingmind.convotrack.ui.common.PlanState
import com.danglingmind.convotrack.ui.common.rememberPlanState
import com.danglingmind.convotrack.ui.designsystem.PromoBanner
import com.danglingmind.convotrack.ui.common.rememberDestinationHero
import com.danglingmind.convotrack.ui.designsystem.AvatarStack
import com.danglingmind.convotrack.ui.designsystem.ConnectionStatusButton
import com.danglingmind.convotrack.ui.designsystem.DestinationPill
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsGradient
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.EmptyState
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.LiveIndicator
import com.danglingmind.convotrack.ui.designsystem.LogoBadge
import com.danglingmind.convotrack.ui.designsystem.MetaChip
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.PillButton
import com.danglingmind.convotrack.ui.designsystem.PillTone
import com.danglingmind.convotrack.ui.designsystem.SectionAction
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.bottomNavClearance
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.ride.rideRoute
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onCreateRide: () -> Unit,
    onJoinRide: () -> Unit,
    onOpenRide: (rideId: String) -> Unit,
    onSeeAllHistory: () -> Unit,
    onOpenSummary: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<List<HistoryRide>>>(UiState.Loading) }
    val credits = remember { mutableStateMapOf<String, String>() }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            UiState.Success(
                // In-progress only: this list is the "resume a ride" surface, and the endpoint's
                // 50-row cap means finished rides would otherwise push live ones out of it.
                apiCall { api.getMyRides(RideStatusFilter.query(RideStatusFilter.IN_PROGRESS)) }.rides,
            )
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(Unit) { load() }

    val plan = rememberPlanState()
    val upsell = remember { ServiceLocator.upsell }
    var cardSnoozed by remember { mutableStateOf(upsell.isHomeCardSnoozed()) }

    DsScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomNavClearance()),
        ) {
            item { HomeHero(plan = plan, onCreateRide = onCreateRide, onJoinRide = onJoinRide) }

            when (val s = state) {
                is UiState.Loading -> items(2) {
                    RideRowSkeleton(Modifier.padding(horizontal = DsSpace.gutter, vertical = 4.dp))
                }
                is UiState.Error -> item {
                    ErrorState(s.message, onRetry = { scope.launch { load() } }, Modifier.padding(DsSpace.gutter))
                }
                is UiState.Success -> {
                    val active = s.data.firstOrNull { !it.isCompletedRide() }
                    val recent = s.data.filter { it.rideId != active?.rideId }.take(5)
                    // One quiet Pro card under the hero — never while a ride is on.
                    val card = if (active == null) homeCard(plan, cardSnoozed) else null
                    if (card != null) {
                        item {
                            PromoBanner(
                                message = card.message,
                                actionLabel = card.action,
                                onAction = { Paywall.present(PaywallSource.HomeCard) },
                                goProStyle = true,
                                onDismiss = if (card.dismissible) {
                                    {
                                        PaywallAnalytics.log(PaywallAnalytics.Event.HomeCardDismissed, PaywallSource.HomeCard, plan.access)
                                        upsell.snoozeHomeCard(inTrial = plan.access is Access.Trial)
                                        cardSnoozed = true
                                    }
                                } else null,
                                modifier = Modifier.padding(horizontal = DsSpace.gutter).padding(top = 22.dp),
                            )
                        }
                    }
                    if (active != null) {
                        item {
                            ActiveRideSection(
                                active,
                                onClick = { onOpenRide(active.rideId) },
                                modifier = Modifier.padding(horizontal = DsSpace.gutter).padding(top = 22.dp),
                            )
                        }
                    }
                    item {
                        SectionHeading(
                            "Recent rides",
                            Modifier.padding(horizontal = DsSpace.gutter).padding(top = 20.dp, bottom = 4.dp),
                        ) {
                            if (recent.isNotEmpty()) SectionAction("See all", onClick = onSeeAllHistory)
                        }
                    }
                    if (recent.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Flag,
                                title = "No rides yet",
                                body = "Create or join your first ride and it'll show up here.",
                                actionLabel = "Create ride",
                                onAction = onCreateRide,
                                modifier = Modifier.padding(horizontal = DsSpace.gutter).padding(top = 8.dp),
                            )
                        }
                    } else {
                        items(recent, key = { it.rideId }) { ride ->
                            RideRowCard(
                                ride,
                                onClick = { if (ride.isCompletedRide()) onOpenSummary(ride.rideId) else onOpenRide(ride.rideId) },
                                onAttribution = { credit -> if (credit != null) credits[ride.rideId] = credit else credits.remove(ride.rideId) },
                                modifier = Modifier.padding(horizontal = DsSpace.gutter, vertical = 4.dp),
                            )
                        }
                        item { PhotoCredits(credits.values, Modifier.padding(horizontal = DsSpace.gutter)) }
                    }
                }
            }
        }
        StatusBarScrim()
    }
}

private fun HistoryRide.isCompletedRide() = status == "COMPLETED" || status == "ENDED"

/** The app's ride status vocabulary (`appVocabulary.rideStatusLabel`). */
private fun statusLabel(status: String): String = when (status) {
    "LOBBY" -> "IN LOBBY"
    "ACTIVE", "IN_PROGRESS" -> "RIDING"
    else -> status
}

private data class HomeCard(val message: String, val action: String, val dismissible: Boolean)

/** The trial countdown leads; the last day can't be hidden; none once the trial has ended (iOS parity). */
private fun homeCard(plan: PlanState, snoozed: Boolean): HomeCard? = when (plan.access) {
    Access.Pro -> null
    is Access.Trial -> {
        val days = plan.trialDaysLeft
        if (days != null && days > 1) {
            if (snoozed) null else HomeCard("$days days left in your free trial", "Go Pro", dismissible = true)
        } else {
            HomeCard("Your free trial ends today", "Go Pro", dismissible = false)
        }
    }
    // The locked buttons, the badge and the app-open nudge already carry this state (design review).
    Access.TrialExpired -> null
    Access.Free -> if (snoozed) null else HomeCard("Bigger packs and no monthly ride limit", "Go Pro", dismissible = true)
}

/** Riders photo, logo + plan badge + connection, eyebrow, serif headline, subtitle and the two actions. */
@Composable
private fun HomeHero(plan: PlanState, onCreateRide: () -> Unit, onJoinRide: () -> Unit) {
    Box {
        PhotoBackdrop(height = 430.dp, drawable = R.drawable.hero_riders, verticalBias = 0.8f, sideFade = true)
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = DsSpace.gutter)
                .padding(top = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogoBadge()
                Spacer(Modifier.size(10.dp))
                PlanBadgeButton(plan, PaywallSource.HomeBadge)
                Spacer(Modifier.weight(1f))
                ConnectionStatusButton()
            }
            Spacer(Modifier.height(48.dp))
            Eyebrow("Ride together")
            Spacer(Modifier.height(8.dp))
            Text("Discover\nnew roads", style = DsType.heroTitle.copy(fontSize = DsType.heroTitle.fontSize * 1.08f, lineHeight = DsType.heroTitle.lineHeight * 1.05f), color = DsColor.textOnPhoto)
            Spacer(Modifier.height(12.dp))
            Text("Plan, join and track rides with your tribe.", style = DsType.heroSubtitle, color = DsColor.textSecondary)
            Spacer(Modifier.height(24.dp))
            ActionButtonPair(onCreateRide, onJoinRide, locked = plan.mustUpgradeToRide)
        }
    }
}

/** CREATE RIDE (accent, dark plus disc) / JOIN RIDE (glass). [locked]: both need Pro first —
 *  the icons become locks and the buttons open the paywall rather than going dead. */
@Composable
private fun ActionButtonPair(onCreateRide: () -> Unit, onJoinRide: () -> Unit, locked: Boolean = false) {
    val view = LocalView.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(14.dp, DsShape.md, ambientColor = DsColor.accentGlow, spotColor = DsColor.accent)
                .clip(DsShape.md)
                .background(DsColor.accent)
                .dsClickable { view.dsHaptic(DsHaptic.Primary); onCreateRide() }
                .semantics { if (locked) stateDescription = "Locked" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(DsColor.onAccent), contentAlignment = Alignment.Center) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Add, null, tint = DsColor.accent, modifier = Modifier.size(if (locked) 15.dp else 18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("CREATE RIDE", style = DsType.buttonWide, color = DsColor.onAccent, maxLines = 1)
        }
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(DsShape.md)
                .background(Color(0x12FFFFFF))
                .border(1.dp, Color(0x24FFFFFF), DsShape.md)
                .semantics { if (locked) stateDescription = "Locked" }
                .dsClickable(onClick = onJoinRide),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(if (locked) Icons.Filled.Lock else Icons.Outlined.PersonAddAlt, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text("JOIN RIDE", style = DsType.buttonWide, color = DsColor.textPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun ActiveRideSection(ride: HistoryRide, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The full ride carries the destination, participants and route (which the history row does
    // not); the route is only needed for the map fallback when there is no photo.
    var full by remember(ride.rideId) { mutableStateOf<Ride?>(null) }
    var route by remember(ride.rideId) { mutableStateOf<List<LatLng>>(emptyList()) }
    LaunchedEffect(ride.rideId) {
        RideDetailsCache.invalidate(ride.rideId)
        full = RideDetailsCache.get(ride.rideId)
    }
    val heroPhase by rememberDestinationHero(
        rideId = full?.id,
        destinationName = full?.destinationName,
        lat = full?.destinationLat,
        lng = full?.destinationLng,
    )
    LaunchedEffect(full, heroPhase) {
        val r = full ?: return@LaunchedEffect
        if (heroPhase is DestinationHeroPhase.Unavailable && route.isEmpty()) route = rideRoute(r)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Active ride") { LiveIndicator(statusLabel(ride.status)) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(DsShape.lg)
                .border(1.dp, DsColor.border, DsShape.lg)
                .background(DsColor.cardSolid),
        ) {
            when (val phase = heroPhase) {
                // The card's own surface stands in until we know whether there's a photo.
                DestinationHeroPhase.Resolving -> Unit
                is DestinationHeroPhase.Photo ->
                    DestinationPhotoImage(bitmap = phase.photo.bitmap, crop = true, modifier = Modifier.matchParentSize())
                DestinationHeroPhase.Unavailable ->
                    if (route.isNotEmpty()) ConvoTrackMap(modifier = Modifier.matchParentSize(), routeCoords = route, interactive = false)
            }
            // The scrim also sits above the (non-interactive) map and carries the click.
            Box(Modifier.matchParentSize().background(DsGradient.cardPhotoScrim).dsClickable(pressedScale = 0.99f, onClick = onClick))

            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                full?.destinationName?.let {
                    DestinationPill(it, Icons.Filled.LocationOn, Modifier.weight(1f, fill = false), trailingIcon = Icons.Filled.ChevronRight)
                }
                Spacer(Modifier.weight(0.01f))
                val riders = full?.participants.orEmpty()
                if (riders.isNotEmpty()) AvatarStack(riders.map { it.userId to it.name })
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ride.title, style = DsType.cardTitle, color = DsColor.textOnPhoto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip(Icons.Filled.Route, "${formatKm(ride.distanceMeters)} KM")
                        full?.estimatedDurationSeconds?.takeIf { it > 0 }?.let { MetaChip(Icons.Filled.Schedule, durationLabel(it)) }
                    }
                }
                Spacer(Modifier.size(10.dp))
                PillButton("Resume", onClick, tone = PillTone.Primary, icon = Icons.Filled.PlayArrow, height = 38.dp)
            }
        }
        // Photographer credit: Google's Places policy requires it wherever a Place Photo is shown.
        (heroPhase as? DestinationHeroPhase.Photo)?.photo?.attribution?.let { credit ->
            Text("Photo: $credit · Google", style = DsType.micro, color = DsColor.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun durationLabel(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}
