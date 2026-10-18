package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.ui.common.rememberAvatarPhoto
import com.danglingmind.convotrack.ui.designsystem.Avatar
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.socket.SplitEvent
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsSize
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.PillButton
import com.danglingmind.convotrack.ui.designsystem.PillTone
import com.danglingmind.convotrack.ui.designsystem.PulseDot
import com.danglingmind.convotrack.ui.designsystem.TagChip
import com.danglingmind.convotrack.ui.designsystem.TextAction
import com.danglingmind.convotrack.ui.designsystem.TextActionTone
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import kotlin.math.roundToInt

/**
 * Shared navigation HUD components. The portrait bottom bar and the landscape side panel render the
 * SAME pieces — the two layouts differ in arrangement and scale, never in what they show, which is
 * the only way they can be kept from drifting apart.
 */

/** HUD glass: opaque enough to read over any map tile. */
internal val HudGlass = Color(0xF0101820)

/**
 * Arrow for the current maneuver, from the Routes API's `maneuver` enum.
 *
 * Locale-independent and unambiguous, unlike scanning the instruction text — which matched road
 * names ("Turn right onto Left Cross Rd" hit "left" first) and flattened slight/sharp/ramp variants
 * into one. The text scan survives only as a fallback for a step that arrives without a maneuver.
 */
fun maneuverIcon(maneuver: String, instruction: String): ImageVector = when (maneuver) {
    "TURN_LEFT", "TURN_SHARP_LEFT" -> Icons.Filled.TurnLeft
    "TURN_RIGHT", "TURN_SHARP_RIGHT" -> Icons.Filled.TurnRight
    "TURN_SLIGHT_LEFT", "RAMP_LEFT" -> Icons.Filled.TurnSlightLeft
    "TURN_SLIGHT_RIGHT", "RAMP_RIGHT" -> Icons.Filled.TurnSlightRight
    "UTURN_LEFT" -> Icons.Filled.UTurnLeft
    "UTURN_RIGHT" -> Icons.Filled.UTurnRight
    "MERGE" -> Icons.Filled.Merge
    "FORK_LEFT" -> Icons.Filled.NorthWest
    "FORK_RIGHT" -> Icons.Filled.NorthEast
    "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> Icons.Filled.RoundaboutRight
    "FERRY", "FERRY_TRAIN" -> Icons.Filled.DirectionsBoat
    "DEPART", "STRAIGHT", "NAME_CHANGE" -> Icons.Filled.Straight
    "ARRIVE" -> Icons.Filled.Flag
    else -> legacyManeuverIcon(instruction)
}

/** Text-derived arrow. Only reached when a step carries no `maneuver`. */
private fun legacyManeuverIcon(instruction: String): ImageVector {
    val l = instruction.lowercase()
    return when {
        l.contains("arrive") || l.contains("destination") -> Icons.Filled.Flag
        l.contains("u-turn") || l.contains("uturn") -> Icons.Filled.UTurnLeft
        l.contains("roundabout") || l.contains("rotary") -> Icons.Filled.RoundaboutRight
        l.contains("merge") -> Icons.Filled.Merge
        l.contains("left") -> Icons.Filled.TurnLeft
        l.contains("right") -> Icons.Filled.TurnRight
        else -> Icons.Filled.Straight
    }
}

/**
 * The turn-by-turn banner. It owns its whole row in both orientations, which is what pays for these
 * sizes — the distance and street are read at road speed. Landscape runs one step smaller because
 * the side panel is a third of the width and ~390dp tall.
 */
@Composable
fun TurnInstructionCard(
    instruction: String,
    maneuver: String,
    distanceMeters: Double,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val tile = if (isLandscape) 48.dp else 58.dp
    val glyph = if (isLandscape) 26.dp else 32.dp
    Row(
        modifier
            .shadow(18.dp, DsShape.lg, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(DsShape.lg)
            .background(HudGlass)
            .border(1.dp, DsColor.accentBorder, DsShape.lg)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(tile).clip(DsShape.md).background(DsColor.accent), contentAlignment = Alignment.Center) {
            Icon(maneuverIcon(maneuver, instruction), null, tint = DsColor.onAccent, modifier = Modifier.size(glyph))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val distText = turnDistanceText(distanceMeters)
            if (distText.isNotEmpty()) {
                Text(
                    distText,
                    style = DsType.hudTurnDistance.copy(fontSize = if (isLandscape) 22.sp else 26.sp),
                    color = DsColor.accent,
                    maxLines = 1,
                )
            }
            Text(
                instruction.ifEmpty { "Follow route" },
                style = DsType.hudTurnInstruction.copy(fontSize = if (isLandscape) 15.sp else 17.sp),
                color = DsColor.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One stat written directly on the bar — no card, no pill. */
@Composable
fun BarStat(label: String, value: String, unit: String?, isLandscape: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = DsType.hudStatLabel, color = DsColor.textMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value,
                style = DsType.hudStatValue.copy(fontSize = if (isLandscape) 20.sp else 24.sp),
                color = DsColor.textPrimary,
                maxLines = 1,
            )
            if (unit != null) Text(unit, style = DsType.hudStatLabel, color = DsColor.textMuted, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun BarDivider(isLandscape: Boolean) {
    Box(Modifier.width(1.dp).height(if (isLandscape) 26.dp else 30.dp).background(DsColor.divider))
}

/** Live rider count, presence-based so it can never disagree with the greyed leaderboard rows. */
@Composable
fun LiveRiderCountPill(count: Int) {
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .clip(DsShape.pill)
            .background(HudGlass)
            .border(1.dp, DsColor.border, DsShape.pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PulseDot(DsColor.accent, 6.dp)
        Text(if (count > 0) "$count" else "--", style = DsType.badge, color = DsColor.accent)
    }
}

/**
 * One leaderboard card. No rank number: the strip is already ordered by position. The rank still
 * drives the leader highlight.
 */
@Composable
fun LiveLeaderboardCard(
    row: NavLeaderboardRow,
    isSelected: Boolean,
    isOffline: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit = {},
) {
    val highlighted = row.rank == 1 || isSelected || row.isMe
    val firstName = row.name.substringBefore(" ").ifEmpty { "Rider" }
    val lastName = row.name.substringAfter(" ", "")
    val photo = rememberAvatarPhoto(row.avatarUrl)

    Row(
        Modifier
            .clip(DsShape.pill)
            .background(if (highlighted) DsColor.accent.copy(alpha = 0.14f).compositeOverHud() else HudGlass)
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) DsColor.accentBorder else DsColor.border, DsShape.pill)
            .alpha(if (isOffline) 0.5f else 1f)
            .dsClickable(onClick = onClick)
            .padding(start = 5.dp, end = 12.dp, top = if (compact) 4.dp else 5.dp, bottom = if (compact) 4.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Avatar(
            row.name,
            size = if (compact) 24.dp else 28.dp,
            photo = photo,
            colorKey = row.id,
            ring = if (highlighted) DsColor.accent else null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(firstName, style = DsType.bodySmall.copy(fontWeight = FontWeight.Bold), color = DsColor.textPrimary, maxLines = 1, softWrap = false)
                if (row.isMe) TagChip("You")
            }
            if (lastName.isNotEmpty() && !compact) {
                Text(lastName, style = DsType.helper.copy(fontSize = 10.sp, lineHeight = 12.sp), color = DsColor.textMuted, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun Color.compositeOverHud(): Color {
    // Pre-blend the tint over the HUD glass so the card stays readable over bright map tiles.
    val a = alpha
    return Color(
        red = red * a + HudGlass.red * (1 - a),
        green = green * a + HudGlass.green * (1 - a),
        blue = blue * a + HudGlass.blue * (1 - a),
        alpha = HudGlass.alpha,
    )
}

/** Shared banner frame: icon disc, eyebrow + message, optional trailing content. */
@Composable
private fun HudBanner(
    accent: Color,
    background: Color,
    border: Color,
    icon: ImageVector?,
    eyebrow: String,
    message: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(DsShape.md)
            .background(background)
            .border(1.5.dp, border, DsShape.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(Modifier.size(36.dp).clip(DsShape.sm).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = DsType.badgeSmall, color = accent)
            if (message != null) Text(message, style = DsType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = DsColor.textPrimary)
            if (!detail.isNullOrBlank()) Text(detail, style = DsType.helper, color = DsColor.textSecondary)
        }
        trailing?.invoke()
    }
}

/** Slim "reconnecting" strip. Callers gate it on the session's connection state, so it takes no
 *  layout space while the connection is healthy. */
@Composable
fun ConnectionBanner(state: RideRealtimeSession.ConnectionState, modifier: Modifier = Modifier) {
    if (state != RideRealtimeSession.ConnectionState.RECONNECTING) return
    HudBanner(
        accent = DsColor.warning,
        background = Color(0xF0241F14),
        border = DsColor.warning.copy(alpha = 0.4f),
        icon = null,
        eyebrow = "RECONNECTING…",
        message = null,
        modifier = modifier,
        leading = { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DsColor.warning, strokeWidth = 2.dp) },
    )
}

/** Small "OFF ROUTE" chip while the rider has strayed from the line being followed. */
@Composable
fun OffRouteBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(DsShape.pill)
            .background(DsColor.dangerContainer)
            .border(1.dp, DsColor.dangerBorder, DsShape.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = DsColor.danger, modifier = Modifier.size(13.dp))
        Text("OFF ROUTE", style = DsType.badgeSmall, color = DsColor.danger)
    }
}

private fun regroupAccentColor(type: String): Color = when (type) {
    "FUEL" -> DsColor.catFuel
    "FOOD" -> DsColor.catFood
    "SCENIC" -> DsColor.catScenic
    else -> DsColor.catOther
}

private fun regroupLabelText(type: String): String = when (type) {
    "FUEL" -> "Fuel Stop"
    "FOOD" -> "Food Stop"
    "SCENIC" -> "Scenic Stop"
    else -> "Stop"
}

/** Transient "regroup called — head to X" toast for riders who didn't initiate it. */
@Composable
fun RegroupToastBanner(type: String, modifier: Modifier = Modifier) {
    val accent = regroupAccentColor(type)
    HudBanner(
        accent = accent,
        background = accent.copy(alpha = 0.12f).compositeOverHud(),
        border = accent.copy(alpha = 0.5f),
        icon = Icons.Filled.Groups,
        eyebrow = "REGROUP CALLED",
        message = "Head to the ${regroupLabelText(type)}",
        modifier = modifier,
    )
}

/** Transient "regroup complete" confirmation once the server resolves it. */
@Composable
fun RegroupResolvedBanner(modifier: Modifier = Modifier) {
    HudBanner(
        accent = DsColor.success,
        background = DsColor.success.copy(alpha = 0.12f).compositeOverHud(),
        border = DsColor.successBorder,
        icon = Icons.Filled.CheckCircle,
        eyebrow = "REGROUP COMPLETE",
        message = "Everyone arrived",
        modifier = modifier,
    )
}

/**
 * Emergency alert — the only solid red container in the app. [onDismiss] is null for riders who
 * aren't allowed to clear it (only the raiser or the leader can); the banner then persists, with the
 * pin and the repeating siren, until an authorized rider clears it.
 */
@Composable
fun EmergencyBanner(
    reporterName: String,
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    HudBanner(
        accent = DsColor.danger,
        background = DsColor.dangerContainer,
        border = DsColor.danger,
        icon = Icons.Filled.Warning,
        eyebrow = "EMERGENCY",
        message = "$reporterName needs help — head to their location",
        detail = message.takeIf { it.isNotBlank() && it != "Emergency" },
        modifier = modifier,
        trailing = onDismiss?.let { dismiss ->
            {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).dsClickable(onClick = dismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Dismiss", tint = DsColor.textPrimary, modifier = Modifier.size(16.dp)) }
            }
        },
    )
}

/** What the split alert says: who is furthest back, and how far behind the leader. */
data class SplitAlertInfo(val riderName: String, val gapMeters: Double)

/**
 * Resolves a `ride:split_detected` payload into display values.
 *
 * The gap is recomputed from the live leaderboard so it keeps moving while the alert is up; the
 * server's figure covers the moment before every row has a position of its own.
 */
fun splitAlertInfo(
    split: SplitEvent,
    rows: List<NavLeaderboardRow>,
    participants: List<RideParticipant>,
    myId: String,
): SplitAlertInfo {
    val lastId = split.lastRiderId
    val name = when {
        lastId == null -> "A rider"
        lastId == myId -> "You"
        else -> (rows.firstOrNull { it.id == lastId }?.name
            ?: participants.firstOrNull { it.userId == lastId }?.name)
            ?.substringBefore(' ')
            ?: "A rider"
    }
    val last = rows.firstOrNull { it.id == lastId }
    val lead = rows.firstOrNull { it.id == split.leaderId } ?: rows.firstOrNull()
    val live = if (last != null && lead != null) (last.distanceToGoalKm - lead.distanceToGoalKm) * 1000 else 0.0
    return SplitAlertInfo(name, if (live > 0) live else split.gapMeters)
}

/** Whole metres under a kilometre (10 m steps, so it doesn't tick on every fix), tenths of a km above. */
fun formatSplitGap(meters: Double): String =
    if (meters < 1000) "${maxOf(10, (meters / 10).roundToInt() * 10)} m"
    else String.format(java.util.Locale.US, "%.1f km", meters / 1000)

/** Group-split alert — the pack has spread out. Offers a quick Regroup or a dismiss. */
@Composable
fun GroupSplitAlert(
    info: SplitAlertInfo,
    onIgnore: () -> Unit,
    onRegroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudBanner(
        accent = DsColor.danger,
        background = DsColor.dangerTint.compositeOverHud(),
        border = DsColor.dangerBorder,
        icon = Icons.Filled.Warning,
        eyebrow = "GROUP SPLIT",
        message = "${info.riderName} is ${formatSplitGap(info.gapMeters)} behind the leader",
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton("Regroup", onRegroup, height = DsSize.touchTarget, horizontalPadding = 12.dp)
                TextAction("Dismiss", onIgnore, tone = TextActionTone.Dismiss)
            }
        },
    )
}

/** Primary "mark as arrived" pill shown while a regroup is active and I haven't arrived yet. */
@Composable
fun ArrivedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton("Mark as arrived", onClick, modifier, icon = Icons.Filled.CheckCircle, height = 44.dp)
}

/** Quiet "waiting for others" pill after I've marked arrived, until the regroup resolves. */
@Composable
fun WaitingForOthersPill(modifier: Modifier = Modifier) {
    PillButton("Waiting for others", onClick = null, modifier = modifier, tone = PillTone.Neutral, icon = Icons.Filled.Schedule)
}

/** RESUME capsule, shown once the rider has panned away from the follow camera. */
@Composable
fun ResumeNavigationPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton("Resume", onClick, modifier, icon = Icons.Filled.MyLocation, height = 40.dp)
}

/** Square icon control on the ride bar (regroup, end). */
@Composable
fun NavControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    background: Color,
    border: Color,
    size: Dp,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** Portrait passes nothing and gets a square button; the landscape panel passes `weight(1f)`
     *  so the two actions split its full width, since the panel is the only chrome there. */
    modifier: Modifier = Modifier,
    /** False (portrait) pins the button square at [size]; true lets [modifier] set the width. */
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(size)
            .then(if (fillWidth) Modifier else Modifier.width(size))
            .clip(DsShape.md)
            .background(background)
            .border(1.dp, border, DsShape.md)
            .dsClickable(enabled = enabled, pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
