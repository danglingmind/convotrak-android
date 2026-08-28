package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.data.socket.SocketEmergency
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType

/**
 * Everything the HUD shows, in one parameter list, so portrait and landscape can be driven from the
 * identical set. They differ in arrangement and scale only — never in what they show.
 */
private const val BUTTON_SIZE_PORTRAIT = 48
private const val BUTTON_SIZE_LANDSCAPE = 44

@Composable
private fun LeaderboardStrip(
    rows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    liveCount: Int,
    compact: Boolean,
    horizontalPadding: Int,
) {
    // The count sits OUTSIDE the scroll on purpose: it is a fact about the convoy, not a rider
    // card, so it must stay put while the cards scroll under it.
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(selectedRiderId) {
            val idx = rows.indexOfFirst { it.id == selectedRiderId }
            if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                LiveLeaderboardCard(
                    row = row,
                    isSelected = selectedRiderId == row.id,
                    isOffline = !row.isMe && !onlineIds.contains(row.id),
                    compact = compact,
                    onClick = { onSelectRider(row.id) },
                )
            }
        }
        LiveRiderCountPill(liveCount)
        Spacer(Modifier.width(if (compact) 0.dp else horizontalPadding.dp))
    }
}

/** Banners, alerts and the arrived control — everything that appears only sometimes. */
@Composable
private fun TransientStack(
    connectionState: RideRealtimeSession.ConnectionState,
    isOffRoute: Boolean,
    activeEmergency: SocketEmergency?,
    emergencyReporter: String,
    canDismissEmergency: Boolean,
    onDismissEmergency: () -> Unit,
    splitGapMeters: Double?,
    onDismissSplit: () -> Unit,
    onRegroupFromSplit: () -> Unit,
    showRegroupToast: Boolean,
    showRegroupResolved: Boolean,
    activeRegroupType: String?,
    hasMarkedArrived: Boolean,
    onMarkArrived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectionBanner(connectionState)
        if (isOffRoute) {
            Row { OffRouteBanner(); Spacer(Modifier.weight(1f)) }
        }
        if (activeEmergency != null) {
            EmergencyBanner(
                reporterName = emergencyReporter,
                message = activeEmergency.message,
                // No dismiss control unless you raised it or you lead: the server enforces the
                // same rule, so offering the button to anyone else would only fail.
                onDismiss = if (canDismissEmergency) onDismissEmergency else null,
            )
        }
        if (splitGapMeters != null) {
            GroupSplitAlert(splitGapMeters, onIgnore = onDismissSplit, onRegroup = onRegroupFromSplit)
        }
        if (showRegroupToast && activeRegroupType != null) RegroupToastBanner(activeRegroupType)
        if (showRegroupResolved) RegroupResolvedBanner()
        if (activeRegroupType != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (hasMarkedArrived) WaitingForOthersPill() else ArrivedButton(onMarkArrived)
            }
        }
    }
}

/** Destination + ETA + START NAVIGATION, shown before guidance begins. */
@Composable
private fun NavigationPreviewBar(
    destinationName: String,
    myDistanceKm: Double,
    etaText: String,
    onStartNavigation: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(CT.surfaceDim)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CT.outlineVariant.copy(alpha = 0.25f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    destinationName.uppercase(),
                    style = CTType.dataMono.copy(fontSize = 12.sp, fontWeight = FontWeight.Black),
                    color = CT.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (myDistanceKm > 0) String.format(java.util.Locale.US, "%.1f km away", myDistanceKm)
                    else "Calculating route…",
                    style = CTType.bodyMd.copy(fontSize = 11.sp),
                    color = CT.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    etaText,
                    style = CTType.dataMono.copy(fontSize = 22.sp, fontWeight = FontWeight.Black),
                    color = CT.onSurface,
                )
                Text("ETA", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.5.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomPadding)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CT.primaryFixed)
                .clickable(onClick = onStartNavigation),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Navigation, null, tint = CT.onPrimaryFixed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("START NAVIGATION", style = CTType.headlineMd, color = CT.onPrimaryFixed)
        }
    }
}

// MARK: - Portrait

@Composable
fun PortraitHud(
    isNavigationActive: Boolean,
    isFreeLooking: Boolean,
    onResume: () -> Unit,
    instruction: String,
    maneuver: String,
    distToTurn: Double,
    leaderboardRows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    liveCount: Int,
    connectionState: RideRealtimeSession.ConnectionState,
    isOffRoute: Boolean,
    activeEmergency: SocketEmergency?,
    emergencyReporter: String,
    canDismissEmergency: Boolean,
    onDismissEmergency: () -> Unit,
    splitGapMeters: Double?,
    onDismissSplit: () -> Unit,
    showRegroupToast: Boolean,
    showRegroupResolved: Boolean,
    activeRegroupType: String?,
    hasMarkedArrived: Boolean,
    onMarkArrived: () -> Unit,
    speedText: String,
    etaText: String,
    distText: String,
    destinationName: String,
    myDistanceKm: Double,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    onEndRide: () -> Unit,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    onTopChromeHeight: (Int) -> Unit,
    onBottomBarHeight: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Top chrome. Measured so the map's edge indicators can clamp below it and the preview
        // camera can frame the route in the band actually left visible.
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).onSizeChanged { onTopChromeHeight(it.height) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isNavigationActive) {
                // The turn banner has the row to itself. The back button and the rider-count pill
                // that used to flank it are gone: exiting now happens through the end-ride
                // confirmation, and the count moved into the leaderboard row below.
                TurnInstructionCard(
                    instruction = instruction,
                    maneuver = maneuver,
                    distanceMeters = distToTurn,
                    isLandscape = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                LeaderboardStrip(
                    rows = leaderboardRows,
                    onlineIds = onlineIds,
                    selectedRiderId = selectedRiderId,
                    onSelectRider = onSelectRider,
                    liveCount = liveCount,
                    compact = false,
                    horizontalPadding = 20,
                )
            } else {
                // Route preview. There is no turn banner yet, and this back button is the only way
                // back to the lobby before navigation starts — the end-ride control that replaces
                // it only exists once guidance is live — so it stays put here.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
                            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Leave navigation (the ride keeps running)",
                            tint = CT.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("TO", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.5.sp)
                        Text(
                            destinationName,
                            style = CTType.bodyLg,
                            color = CT.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (isNavigationActive) {
            TransientStack(
                connectionState = connectionState,
                isOffRoute = isOffRoute,
                activeEmergency = activeEmergency,
                emergencyReporter = emergencyReporter,
                canDismissEmergency = canDismissEmergency,
                onDismissEmergency = onDismissEmergency,
                splitGapMeters = splitGapMeters,
                onDismissSplit = onDismissSplit,
                onRegroupFromSplit = { onDismissSplit(); onRegroup() },
                showRegroupToast = showRegroupToast,
                showRegroupResolved = showRegroupResolved,
                activeRegroupType = activeRegroupType,
                hasMarkedArrived = hasMarkedArrived,
                onMarkArrived = onMarkArrived,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        if (isNavigationActive && isFreeLooking) {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                ResumeNavigationPill(onResume)
            }
        }

        Box(Modifier.onSizeChanged { onBottomBarHeight(it.height) }) {
            if (isNavigationActive) {
                NavBottomBar(
                    speedText = speedText,
                    etaText = etaText,
                    distText = distText,
                    isEnding = isEnding,
                    onRegroup = onRegroup,
                    onEndRide = onEndRide,
                )
            } else {
                NavigationPreviewBar(
                    destinationName = destinationName,
                    myDistanceKm = myDistanceKm,
                    etaText = etaText,
                    onStartNavigation = onStartNavigation,
                    bottomPadding = 24.dp,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
    }
}

/**
 * The portrait bottom bar: SPEED / ETA / DIST and the two controls.
 *
 * Units are dropped on narrow screens rather than letting the row overflow: three stats plus both
 * buttons need ~438dp at worst-case values, so on a 393dp screen a fixed-width row bleeds past both
 * edges and clips the outermost glyphs.
 */
@Composable
private fun NavBottomBar(
    speedText: String,
    etaText: String,
    distText: String,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    onEndRide: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(CT.surfaceContainerHigh.copy(alpha = 0.94f)),
    ) {
        // Units only where there is genuinely room for them. A worst-case row — "120 KM/H",
        // "25h 8m", "1436.3 KM" plus both controls — needs ~466dp, which is wider than any phone
        // in portrait: the row overflowed and the END RIDE button was pushed clean off the screen,
        // leaving no way to end a ride at all.
        val showUnits = maxWidth >= 430.dp
        val spacing = if (maxWidth >= 400.dp) 12.dp else 8.dp
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CT.outlineVariant.copy(alpha = 0.25f)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                // The stats are weighted as ONE group, so the fixed-size controls are measured
                // first and can never be pushed out of the row however long a value gets.
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    BarStat("SPEED", speedText, if (showUnits) "KM/H" else null, isLandscape = false)
                    BarDivider(false)
                    BarStat("ETA", etaText, null, isLandscape = false)
                    BarDivider(false)
                    BarStat("DIST", distText, if (showUnits) "KM" else null, isLandscape = false)
                }
                NavControlButton(
                    icon = Icons.Filled.Groups,
                    description = "Regroup",
                    tint = CT.onSurface,
                    background = CT.surfaceContainerHigh.copy(alpha = 0.9f),
                    border = CT.outlineVariant.copy(alpha = 0.3f),
                    size = BUTTON_SIZE_PORTRAIT.dp,
                    onClick = onRegroup,
                )
                NavControlButton(
                    icon = Icons.Filled.Stop,
                    description = "End ride",
                    tint = CT.errorColor,
                    background = CT.errorContainer.copy(alpha = 0.12f),
                    border = CT.errorColor.copy(alpha = 0.35f),
                    size = BUTTON_SIZE_PORTRAIT.dp,
                    enabled = !isEnding,
                    busy = isEnding,
                    onClick = onEndRide,
                )
            }
        }
    }
}

// MARK: - Landscape

/**
 * Landscape splits the screen in thirds: the map keeps the left two, every HUD element moves into
 * the right one.
 *
 * Landscape has ~390dp of height and ~850dp of width, so a full-width bottom bar spends the scarce
 * dimension and wastes the plentiful one. Stats and actions stick to the bottom of the panel so
 * they stay under the rider's thumb and don't shuffle when a banner appears above them.
 */
@Composable
fun LandscapeHud(
    isNavigationActive: Boolean,
    instruction: String,
    maneuver: String,
    distToTurn: Double,
    leaderboardRows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    liveCount: Int,
    connectionState: RideRealtimeSession.ConnectionState,
    isOffRoute: Boolean,
    activeEmergency: SocketEmergency?,
    emergencyReporter: String,
    canDismissEmergency: Boolean,
    onDismissEmergency: () -> Unit,
    splitGapMeters: Double?,
    onDismissSplit: () -> Unit,
    showRegroupToast: Boolean,
    showRegroupResolved: Boolean,
    activeRegroupType: String?,
    hasMarkedArrived: Boolean,
    onMarkArrived: () -> Unit,
    speedText: String,
    etaText: String,
    distText: String,
    destinationName: String,
    myDistanceKm: Double,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    onEndRide: () -> Unit,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    onPanelWidth: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Back button floats over the map, top-left. Only before navigation starts: once guidance
        // is live, leaving happens through the end-ride confirmation — the same rule as portrait,
        // so the two orientations offer the same way out.
        //
        // Sitting at the TOP is what makes 16dp safe with a notch on this side: in landscape the
        // camera housing is a band in the VERTICAL middle of the edge, so a control pinned to the
        // top corner clears it without needing the full inset.
        if (!isNavigationActive) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 21.dp, top = 12.dp)
                    .size(44.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Leave navigation (the ride keeps running)",
                    tint = CT.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = maxWidth / 3
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .onSizeChanged { onPanelWidth(it.width) }
                    .background(CT.surfaceDim.copy(alpha = 0.94f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // Same 9dp gutter both sides, no notch allowance: every row sits above the
                    // camera housing (turn card, leaderboard) or below it (stats, actions), and
                    // the housing is a band in the vertical middle of a landscape edge.
                    .padding(horizontal = 9.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isNavigationActive) {
                    // 1 — turn-by-turn, alone on its row. The rider count that used to share it
                    // moved into the leaderboard row below, which is what lets the banner run the
                    // full width of the panel at a size readable from a mount.
                    TurnInstructionCard(
                        instruction = instruction,
                        maneuver = maneuver,
                        distanceMeters = distToTurn,
                        isLandscape = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 2 — leaderboard, horizontally scrolled, count pinned trailing.
                    LeaderboardStrip(
                        rows = leaderboardRows,
                        onlineIds = onlineIds,
                        selectedRiderId = selectedRiderId,
                        onSelectRider = onSelectRider,
                        liveCount = liveCount,
                        compact = true,
                        horizontalPadding = 4,
                    )

                    // 3 — banners, directly under the leaderboard.
                    TransientStack(
                        connectionState = connectionState,
                        isOffRoute = isOffRoute,
                        activeEmergency = activeEmergency,
                        emergencyReporter = emergencyReporter,
                        canDismissEmergency = canDismissEmergency,
                        onDismissEmergency = onDismissEmergency,
                        splitGapMeters = splitGapMeters,
                        onDismissSplit = onDismissSplit,
                        onRegroupFromSplit = { onDismissSplit(); onRegroup() },
                        showRegroupToast = showRegroupToast,
                        showRegroupResolved = showRegroupResolved,
                        activeRegroupType = activeRegroupType,
                        hasMarkedArrived = hasMarkedArrived,
                        onMarkArrived = onMarkArrived,
                    )

                    Spacer(Modifier.weight(1f))

                    // 4 + 5 — stats and actions stick to the bottom.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Units are dropped here: a third of the width can't hold them beside
                        // three values.
                        BarStat("SPEED", speedText, null, isLandscape = true)
                        BarDivider(true)
                        BarStat("ETA", etaText, null, isLandscape = true)
                        BarDivider(true)
                        BarStat("DIST", distText, null, isLandscape = true)
                        Spacer(Modifier.weight(1f))
                    }
                    // Regroup and end-ride, equal width across the panel. Portrait keeps its square
                    // icon buttons; here the panel is the only chrome, so the actions get its full
                    // width.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavControlButton(
                            icon = Icons.Filled.Groups,
                            description = "Regroup",
                            tint = CT.onSurface,
                            background = CT.surfaceContainerHigh.copy(alpha = 0.9f),
                            border = CT.outlineVariant.copy(alpha = 0.3f),
                            size = BUTTON_SIZE_LANDSCAPE.dp,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                            onClick = onRegroup,
                        )
                        NavControlButton(
                            icon = Icons.Filled.Stop,
                            description = "End ride",
                            tint = CT.errorColor,
                            background = CT.errorContainer.copy(alpha = 0.12f),
                            border = CT.errorColor.copy(alpha = 0.35f),
                            size = BUTTON_SIZE_LANDSCAPE.dp,
                            enabled = !isEnding,
                            busy = isEnding,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                            onClick = onEndRide,
                        )
                    }
                } else {
                    NavigationPreviewBar(
                        destinationName = destinationName,
                        myDistanceKm = myDistanceKm,
                        etaText = etaText,
                        onStartNavigation = onStartNavigation,
                        bottomPadding = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
