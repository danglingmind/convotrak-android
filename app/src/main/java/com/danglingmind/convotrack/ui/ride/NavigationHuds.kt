package com.danglingmind.convotrack.ui.ride

import androidx.compose.material.icons.automirrored.filled.ExitToApp
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.rememberReduceMotion
import com.danglingmind.convotrack.ui.designsystem.DsSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.FloatingTone
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.data.socket.SocketEmergency

// Portrait and landscape HUDs are driven from the identical parameter set: they differ in
// arrangement and scale only — never in what they show.
private const val BUTTON_SIZE_PORTRAIT = 50
private const val BUTTON_SIZE_LANDSCAPE = 44

/**
 * The convoy: one tappable card per rider, ordered by position, each showing how far that rider is
 * ahead of or behind me. [trailing] holds a control that must stay put while the cards scroll.
 */
@Composable
private fun LeaderboardStrip(
    rows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    compact: Boolean,
    horizontalPadding: Int,
    trailing: (@Composable () -> Unit)? = null,
) {
    val me = rows.firstOrNull { it.isMe }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        val listState = rememberLazyListState()
        val reduceMotion = rememberReduceMotion()
        LaunchedEffect(selectedRiderId) {
            val idx = rows.indexOfFirst { it.id == selectedRiderId }
            if (idx >= 0) runCatching {
                if (reduceMotion) listState.scrollToItem(idx) else listState.animateScrollToItem(idx)
            }
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
                    // Ahead of me = less distance left to the goal than I have.
                    // Until both riders have posted a position the gap is two copies of the route length.
                    gapMeters = if (row.isMe || me == null || !row.hasPosition || !me.hasPosition) null
                    else (me.distanceToGoalKm - row.distanceToGoalKm) * 1000,
                    onClick = { onSelectRider(row.id) },
                )
            }
        }
        trailing?.invoke()
        Spacer(Modifier.width(if (compact) 0.dp else horizontalPadding.dp))
    }
}

/**
 * One banner at a time, chosen by priority: emergency > group split > regroup called > regroup
 * complete. When more than one is live a "+N more" chip under the banner steps through the rest,
 * so a pile of alerts never pushes the road off the screen and the most urgent one can't end up at
 * the bottom of the pile.
 *
 * Off-route and reconnecting are NOT alerts: they describe the guidance itself, so they are a
 * status line on the turn card.
 */
@Composable
private fun AlertSlot(
    emergencies: List<EmergencyAlertItem>,
    onDismissEmergency: (String) -> Unit,
    onShowEmergency: (String) -> Unit,
    splitAlert: SplitAlertInfo?,
    onDismissSplit: () -> Unit,
    onRegroupFromSplit: () -> Unit,
    regroups: List<RegroupAlertItem>,
    regroupNotice: RegroupNoticeUi?,
    onMarkArrived: (String) -> Unit,
    onShowRegroup: (String) -> Unit,
    onDismissRegroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Several emergencies or regroups can be open at once. Each is its own alert, in the order it
    // was raised, so the banner a rider is reading never reshuffles under them as they ride.
    val alerts = buildList<NavAlert> {
        emergencies.forEach { add(NavAlert.Emergency(it.id)) }
        if (splitAlert != null) add(NavAlert.Split)
        regroups.forEach { add(NavAlert.Regroup(it.id)) }
        if (regroupNotice != null) add(NavAlert.Notice)
    }
    if (alerts.isEmpty()) return
    // Back to the most urgent whenever the set changes.
    var cycle by remember(alerts) { mutableIntStateOf(0) }
    val current = alerts[cycle.coerceAtMost(alerts.size - 1)]

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (current) {
            is NavAlert.Emergency -> emergencies.firstOrNull { it.id == current.id }?.let { e ->
                EmergencyBanner(
                    reporterName = e.reporter,
                    message = e.message,
                    onShow = { onShowEmergency(e.id) },
                    // No dismiss control unless you raised it or you lead: the server enforces the
                    // same rule, so offering the button to anyone else would only fail.
                    onDismiss = if (e.canDismiss) ({ onDismissEmergency(e.id) }) else null,
                )
            }
            NavAlert.Split -> if (splitAlert != null) GroupSplitAlert(splitAlert, onIgnore = onDismissSplit, onRegroup = onRegroupFromSplit)
            is NavAlert.Regroup -> regroups.firstOrNull { it.id == current.id }?.let { r ->
                RegroupAlertBanner(
                    type = r.type,
                    caller = r.caller,
                    hasArrived = r.hasArrived,
                    onShow = { onShowRegroup(r.id) },
                    onArrived = { onMarkArrived(r.id) },
                    // Only the caller or the leader can end it early; the server enforces the same rule.
                    onDismiss = if (r.canDismiss) ({ onDismissRegroup(r.id) }) else null,
                )
            }
            NavAlert.Notice -> regroupNotice?.let { RegroupNoticeBanner(it) }
        }
        if (alerts.size > 1) {
            val more = "${alerts.size - 1} more alerts"
            Box(
                Modifier
                    .heightIn(min = DsSize.touchTarget)
                    .semantics { contentDescription = more }
                    .dsClickable { cycle = (cycle + 1) % alerts.size },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+${alerts.size - 1} more",
                    style = DsType.badgeSmall,
                    color = DsColor.textPrimary,
                    modifier = Modifier
                        .clip(DsShape.pill)
                        .background(HudGlass)
                        .border(1.dp, DsColor.border, DsShape.pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** The alerts that can claim the banner slot, in priority order. */
private sealed interface NavAlert {
    data class Emergency(val id: String) : NavAlert
    object Split : NavAlert
    data class Regroup(val id: String) : NavAlert
    object Notice : NavAlert
}

@Composable
private fun NavigationPreviewBar(
    destinationName: String,
    myDistanceKm: Double,
    arrival: Arrival?,
    onStartNavigation: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(DsShape.sheetTop)
            .background(HudGlass)
            .border(1.dp, DsColor.borderSubtle, DsShape.sheetTop)
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("DESTINATION", style = DsType.fieldLabel, color = DsColor.textSecondary)
                Text(destinationName, style = DsType.cardTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (myDistanceKm > 0) String.format(java.util.Locale.US, "%.1f km", myDistanceKm) + (arrival?.let { " · ${it.timeLeft}" }.orEmpty()) else "Calculating route…",
                    style = DsType.bodySmall,
                    color = DsColor.textSecondary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "Arrival ${arrival?.spoken ?: "unknown"}" },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(arrival?.time ?: "--", style = DsType.hudEta, color = DsColor.textPrimary)
                    arrival?.period?.let { Text(it, style = DsType.hudStatLabel, color = DsColor.textSecondary, modifier = Modifier.padding(bottom = 3.dp)) }
                }
                Text("ARRIVAL", style = DsType.hudStatLabel, color = DsColor.textSecondary)
            }
        }
        PrimaryButton("Start navigation", onStartNavigation, icon = Icons.Filled.Navigation)
    }
}


@Composable
fun PortraitHud(
    isNavigationActive: Boolean,
    isFreeLooking: Boolean,
    onResume: () -> Unit,
    instruction: String,
    maneuver: String,
    thenManeuver: String?,
    distToTurn: Double,
    leaderboardRows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    connectionState: RideRealtimeSession.ConnectionState,
    isOffRoute: Boolean,
    emergencies: List<EmergencyAlertItem>,
    onDismissEmergency: (String) -> Unit,
    onShowEmergency: (String) -> Unit,
    splitAlert: SplitAlertInfo?,
    onDismissSplit: () -> Unit,
    regroups: List<RegroupAlertItem>,
    regroupNotice: RegroupNoticeUi?,
    onMarkArrived: (String) -> Unit,
    onShowRegroup: (String) -> Unit,
    onDismissRegroup: (String) -> Unit,
    speedText: String,
    arrival: Arrival?,
    distText: String,
    destinationName: String,
    myDistanceKm: Double,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    isLeader: Boolean,
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
                // The turn banner has the row to itself: exiting happens through the end-ride
                // confirmation in the bottom bar.
                TurnInstructionCard(
                    instruction = instruction,
                    maneuver = maneuver,
                    thenManeuver = thenManeuver,
                    distanceMeters = distToTurn,
                    isOffRoute = isOffRoute,
                    isReconnecting = connectionState == RideRealtimeSession.ConnectionState.RECONNECTING,
                    isLandscape = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                    FloatingButton(Icons.AutoMirrored.Filled.ArrowBack, "Leave navigation (the ride keeps running)", onBack, tone = FloatingTone.OnDark)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(DsShape.pill)
                            .background(HudGlass)
                            .border(1.dp, DsColor.border, DsShape.pill)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("TO", style = DsType.badgeSmall, color = DsColor.textSecondary)
                        Text(destinationName, style = DsType.bodyStrong, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (isNavigationActive) {
            AlertSlot(
                emergencies = emergencies,
                onDismissEmergency = onDismissEmergency,
                onShowEmergency = onShowEmergency,
                splitAlert = splitAlert,
                onDismissSplit = onDismissSplit,
                onRegroupFromSplit = { onDismissSplit(); onRegroup() },
                regroups = regroups,
                regroupNotice = regroupNotice,
                onMarkArrived = onMarkArrived,
                onShowRegroup = onShowRegroup,
                onDismissRegroup = onDismissRegroup,
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
                    leaderboardRows = leaderboardRows,
                    onlineIds = onlineIds,
                    selectedRiderId = selectedRiderId,
                    onSelectRider = onSelectRider,
                    speedText = speedText,
                    arrival = arrival,
                    distText = distText,
                    isEnding = isEnding,
                    onRegroup = onRegroup,
                    isLeader = isLeader,
                    onEndRide = onEndRide,
                )
            } else {
                NavigationPreviewBar(
                    destinationName = destinationName,
                    myDistanceKm = myDistanceKm,
                    arrival = arrival,
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
    leaderboardRows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    speedText: String,
    arrival: Arrival?,
    distText: String,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    isLeader: Boolean,
    onEndRide: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            // Floats like the app's bottom nav: inset and rounded over the map.
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
    ) {
        // Units only where there is genuinely room for them. A worst-case row — "120 KM/H",
        // "25h 8m", "1436.3 KM" plus both controls — needs ~466dp, which is wider than any phone
        // in portrait: the row overflowed and the END RIDE button was pushed clean off the screen,
        // leaving no way to end a ride at all.
        val showUnits = maxWidth >= 430.dp
        val spacing = if (maxWidth >= 400.dp) 12.dp else 8.dp
        Column(
            Modifier
                .shadow(20.dp, DsShape.nav, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(DsShape.nav)
                .background(HudGlass)
                .border(1.dp, DsColor.border, DsShape.nav),
        ) {
            // The convoy sits in the bar, in thumb reach, so the top of the screen stays free for
            // the road. The mute button holds the trailing slot, where it can't scroll away.
            Box(Modifier.padding(top = 10.dp)) {
                LeaderboardStrip(
                    rows = leaderboardRows,
                    onlineIds = onlineIds,
                    selectedRiderId = selectedRiderId,
                    onSelectRider = onSelectRider,
                    compact = false,
                    horizontalPadding = 12,
                    trailing = { VoiceToggleButton(Modifier.padding(end = 0.dp)) },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
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
                    BarStat(
                        "ARRIVAL", arrival?.time ?: "--", if (showUnits) arrival?.period else null, isLandscape = false,
                        sub = arrival?.timeLeft, spoken = arrival?.spoken,
                    )
                    BarDivider(false)
                    BarStat("DIST", distText, if (showUnits) "KM" else null, isLandscape = false)
                }
                NavControlButton(
                    icon = Icons.Filled.Groups,
                    description = "Regroup",
                    tint = DsColor.textPrimary,
                    background = DsColor.card,
                    border = DsColor.border,
                    size = BUTTON_SIZE_PORTRAIT.dp,
                    onClick = onRegroup,
                )
                // Two different controls because they do two different things. The leader's red stop
                // ends the ride for everyone (after a confirmation that also offers Exit); everyone
                // else cannot end anything, so theirs is a plain neutral exit.
                if (isLeader) {
                    NavControlButton(
                        icon = Icons.Filled.Stop,
                        description = "End ride",
                        tint = DsColor.danger,
                        background = DsColor.dangerTint,
                        border = DsColor.dangerBorder,
                        size = BUTTON_SIZE_PORTRAIT.dp,
                        enabled = !isEnding,
                        busy = isEnding,
                        onClick = onEndRide,
                    )
                } else {
                    NavControlButton(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        description = "Exit navigation",
                        tint = DsColor.textPrimary,
                        background = DsColor.card,
                        border = DsColor.border,
                        size = BUTTON_SIZE_PORTRAIT.dp,
                        onClick = onEndRide,
                    )
                }
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
    thenManeuver: String?,
    distToTurn: Double,
    leaderboardRows: List<NavLeaderboardRow>,
    onlineIds: Set<String>,
    selectedRiderId: String?,
    onSelectRider: (String) -> Unit,
    connectionState: RideRealtimeSession.ConnectionState,
    isOffRoute: Boolean,
    emergencies: List<EmergencyAlertItem>,
    onDismissEmergency: (String) -> Unit,
    onShowEmergency: (String) -> Unit,
    splitAlert: SplitAlertInfo?,
    onDismissSplit: () -> Unit,
    regroups: List<RegroupAlertItem>,
    regroupNotice: RegroupNoticeUi?,
    onMarkArrived: (String) -> Unit,
    onShowRegroup: (String) -> Unit,
    onDismissRegroup: (String) -> Unit,
    speedText: String,
    arrival: Arrival?,
    distText: String,
    destinationName: String,
    myDistanceKm: Double,
    isEnding: Boolean,
    onRegroup: () -> Unit,
    isLeader: Boolean,
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
            FloatingButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Leave navigation (the ride keeps running)",
                onBack,
                tone = FloatingTone.OnDark,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 21.dp, top = 12.dp),
            )
        }

        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = maxWidth / 3
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .onSizeChanged { onPanelWidth(it.width) }
                    .background(HudGlass)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // Same 9dp gutter both sides, no notch allowance: every row sits above the
                    // camera housing (turn card, leaderboard) or below it (stats, actions), and
                    // the housing is a band in the vertical middle of a landscape edge.
                    .padding(horizontal = 9.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isNavigationActive) {
                    // 1 — turn-by-turn, alone on its row at the full width of the panel, a size
                    // readable from a mount.
                    TurnInstructionCard(
                        instruction = instruction,
                        maneuver = maneuver,
                        thenManeuver = thenManeuver,
                        distanceMeters = distToTurn,
                        isOffRoute = isOffRoute,
                        isReconnecting = connectionState == RideRealtimeSession.ConnectionState.RECONNECTING,
                        isLandscape = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 2 — leaderboard, horizontally scrolled.
                    LeaderboardStrip(
                        rows = leaderboardRows,
                        onlineIds = onlineIds,
                        selectedRiderId = selectedRiderId,
                        onSelectRider = onSelectRider,
                        compact = true,
                        horizontalPadding = 4,
                    )

                    // 3 — banners, directly under the leaderboard.
                    AlertSlot(
                emergencies = emergencies,
                onDismissEmergency = onDismissEmergency,
                onShowEmergency = onShowEmergency,
                splitAlert = splitAlert,
                onDismissSplit = onDismissSplit,
                onRegroupFromSplit = { onDismissSplit(); onRegroup() },
                regroups = regroups,
                regroupNotice = regroupNotice,
                onMarkArrived = onMarkArrived,
                onShowRegroup = onShowRegroup,
                onDismissRegroup = onDismissRegroup,
                modifier = Modifier,
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
                        BarStat(
                            "ARRIVAL", arrival?.time ?: "--", arrival?.period, isLandscape = true,
                            sub = arrival?.timeLeft, spoken = arrival?.spoken,
                        )
                        BarDivider(true)
                        BarStat("DIST", distText, null, isLandscape = true)
                        Spacer(Modifier.weight(1f))
                    }
                    // Regroup and end-ride, equal width across the panel. Portrait keeps its square
                    // icon buttons; here the panel is the only chrome, so the actions get its full
                    // width.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val voiceMuted by VoiceAnnouncer.muted.collectAsState()
                        NavControlButton(
                            icon = if (voiceMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            description = if (voiceMuted) "Turn on voice guidance" else "Mute voice guidance",
                            tint = DsColor.textPrimary,
                            background = DsColor.card,
                            border = DsColor.border,
                            size = BUTTON_SIZE_LANDSCAPE.dp,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                            onClick = { VoiceAnnouncer.setMuted(!voiceMuted) },
                        )
                        NavControlButton(
                            icon = Icons.Filled.Groups,
                            description = "Regroup",
                            tint = DsColor.textPrimary,
                            background = DsColor.card,
                            border = DsColor.border,
                            size = BUTTON_SIZE_LANDSCAPE.dp,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                            onClick = onRegroup,
                        )
                        if (isLeader) {
                            NavControlButton(
                                icon = Icons.Filled.Stop,
                                description = "End ride",
                                tint = DsColor.danger,
                                background = DsColor.dangerTint,
                                border = DsColor.dangerBorder,
                                size = BUTTON_SIZE_LANDSCAPE.dp,
                                enabled = !isEnding,
                                busy = isEnding,
                                modifier = Modifier.weight(1f),
                                fillWidth = true,
                                onClick = onEndRide,
                            )
                        } else {
                            NavControlButton(
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                description = "Exit navigation",
                                tint = DsColor.textPrimary,
                                background = DsColor.card,
                                border = DsColor.border,
                                size = BUTTON_SIZE_LANDSCAPE.dp,
                                modifier = Modifier.weight(1f),
                                fillWidth = true,
                                onClick = onEndRide,
                            )
                        }
                    }
                } else {
                    NavigationPreviewBar(
                        destinationName = destinationName,
                        myDistanceKm = myDistanceKm,
                        arrival = arrival,
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
