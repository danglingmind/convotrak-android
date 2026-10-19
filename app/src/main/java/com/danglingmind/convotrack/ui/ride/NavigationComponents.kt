package com.danglingmind.convotrack.ui.ride

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.danglingmind.convotrack.ui.designsystem.FloatingTone
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.danglingmind.convotrack.data.audio.NavVoiceGuide
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
    thenManeuver: String?,
    distanceMeters: Double,
    isOffRoute: Boolean,
    isReconnecting: Boolean,
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
            .border(
                1.dp,
                when {
                    isOffRoute -> DsColor.dangerBorder
                    isReconnecting -> DsColor.warning.copy(alpha = 0.5f)
                    else -> DsColor.accentBorder
                },
                DsShape.lg,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(tile).clip(DsShape.md).background(DsColor.accent), contentAlignment = Alignment.Center) {
            Icon(maneuverIcon(maneuver, instruction), null, tint = DsColor.onAccent, modifier = Modifier.size(glyph))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // One line above the distance: the guidance is unreliable, and why. Off route wins.
            if (isOffRoute || isReconnecting) {
                val tone = if (isOffRoute) DsColor.danger else DsColor.warning
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isOffRoute) Icon(Icons.Filled.Warning, null, tint = tone, modifier = Modifier.size(13.dp))
                    else CircularProgressIndicator(modifier = Modifier.size(12.dp), color = tone, strokeWidth = 2.dp)
                    Text(
                        if (isOffRoute) "Off route · rerouting…" else "Reconnecting…",
                        style = DsType.badgeSmall,
                        color = tone,
                        maxLines = 1,
                    )
                }
            }
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
            // A quick double turn: show the second one too, so it isn't a surprise.
            if (thenManeuver != null) {
                val spoken = NavVoiceGuide.phrase(thenManeuver) ?: "continue"
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "Then $spoken" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Then", style = DsType.bodySmall, color = DsColor.textSecondary)
                    Icon(maneuverIcon(thenManeuver, ""), null, tint = DsColor.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** One stat written directly on the bar — no card, no pill. */
@Composable
fun BarStat(
    label: String,
    value: String,
    unit: String?,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    /** A third line under the value, e.g. the minutes left beneath an arrival time. */
    sub: String? = null,
    /** What a screen reader says instead of the visible pieces. */
    spoken: String? = null,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label ${spoken ?: listOfNotNull(value, unit).joinToString(" ")}"
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = DsType.hudStatLabel, color = DsColor.textSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value,
                style = DsType.hudStatValue.copy(fontSize = if (isLandscape) 20.sp else 24.sp),
                color = DsColor.textPrimary,
                maxLines = 1,
            )
            if (unit != null) Text(unit, style = DsType.hudStatLabel, color = DsColor.textSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (sub != null) Text(sub, style = DsType.hudStatLabel, color = DsColor.textSecondary, maxLines = 1)
    }
}

/** Clock arrival, split so the 12-hour period can be set small beside the digits. */
data class Arrival(val time: String, val period: String?, val timeLeft: String, val spoken: String)

/**
 * When the rider gets to the next stop on the device clock: "10:42" plus "AM" in 12-hour mode.
 * [is24h] comes from the system setting, not the locale, so it matches the status bar.
 */
fun arrivalFor(secondsLeft: Double, is24h: Boolean): Arrival {
    val at = java.time.LocalTime.now().plusSeconds(secondsLeft.toLong())
    val locale = java.util.Locale.getDefault()
    val time = java.time.format.DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", locale).format(at)
    val period = if (is24h) null else java.time.format.DateTimeFormatter.ofPattern("a", locale).format(at)
    val left = formatRemainingTime(secondsLeft)
    val minutes = Math.round(secondsLeft / 60).toInt()
    val spokenLeft = if (minutes < 60) "$minutes minutes" else "${minutes / 60} hours ${minutes % 60} minutes"
    return Arrival(time, period, "$left left", "$time${period?.let { " $it" }.orEmpty()}, $spokenLeft left")
}

@Composable
fun BarDivider(isLandscape: Boolean) {
    Box(Modifier.width(1.dp).height(if (isLandscape) 26.dp else 30.dp).background(DsColor.divider))
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
    /** Metres this rider is ahead of (+) or behind (−) me; null for my own card. */
    gapMeters: Double? = null,
    onClick: () -> Unit = {},
) {
    val highlighted = row.rank == 1 || isSelected || row.isMe
    val firstName = row.name.substringBefore(" ").ifEmpty { "Rider" }
    val photo = rememberAvatarPhoto(row.avatarUrl)

    // "+1.2 km" ahead, "−300 m" behind, "Level" within a few car lengths.
    val gapText = gapMeters?.let { gap ->
        val d = kotlin.math.abs(gap)
        when {
            d < 25 -> "Level"
            else -> (if (gap > 0) "+" else "−") +
                if (d < 1000) "${Math.round(d / 10) * 10} m" else String.format(java.util.Locale.US, "%.1f km", d / 1000)
        }
    }
    val spokenGap = gapMeters?.let { gap ->
        val d = kotlin.math.abs(gap)
        when {
            d < 25 -> "level with you"
            else -> NavVoiceGuide.spokenDistance(d) + if (gap > 0) " ahead" else " behind"
        }
    }

    Row(
        Modifier
            .heightIn(min = DsSize.touchTarget)
            .clip(DsShape.pill)
            .background(if (highlighted) DsColor.accent.copy(alpha = 0.14f).compositeOverHud() else HudGlass)
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) DsColor.accentBorder else DsColor.border, DsShape.pill)
            .alpha(if (isOffline) 0.5f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(row.name)
                    if (row.isMe) append(", you")
                    if (isOffline) append(", offline")
                    spokenGap?.let { append(", ").append(it) }
                }
                role = Role.Button
            }
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
            Text(firstName, style = DsType.bodySmall.copy(fontWeight = FontWeight.Bold), color = DsColor.textPrimary, maxLines = 1, softWrap = false)
            // The gap is the point of the card: where is this rider relative to me?
            val second = if (row.isMe) "You" else gapText
            if (second != null) {
                Text(second, style = DsType.dataMono, color = if (row.isMe) DsColor.accent else DsColor.textSecondary, maxLines = 1, softWrap = false)
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

/**
 * Shared banner frame: icon disc, eyebrow + message, optional trailing content, and an optional
 * [actions] row beneath the header (full-width controls such as Show / Mark as arrived).
 */
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
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(DsShape.md)
            .background(background)
            .border(1.5.dp, border, DsShape.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
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
    actions?.invoke()
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

/**
 * "Regroup called — head to X", with the two things a rider can do about it: look at the meet
 * point, and say they have arrived. It stays until the regroup resolves, so the actions never
 * vanish on a timer. Show is a peek, not a camera move: the map stays on the road until asked.
 */
@Composable
fun RegroupAlertBanner(
    type: String,
    hasArrived: Boolean,
    onShow: () -> Unit,
    onArrived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = regroupAccentColor(type)
    HudBanner(
        accent = accent,
        background = accent.copy(alpha = 0.12f).compositeOverHud(),
        border = accent.copy(alpha = 0.5f),
        icon = Icons.Filled.Groups,
        eyebrow = "REGROUP CALLED",
        message = "Head to the ${regroupLabelText(type)}",
        modifier = modifier,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Show", onShow, Modifier.weight(1f), tone = PillTone.Neutral, icon = Icons.Filled.MyLocation, height = DsSize.touchTarget)
                if (hasArrived) {
                    PillButton("Waiting for others", onClick = null, modifier = Modifier.weight(1f), tone = PillTone.Neutral, icon = Icons.Filled.Schedule, height = DsSize.touchTarget)
                } else {
                    PillButton("Mark as arrived", onArrived, Modifier.weight(1f), icon = Icons.Filled.CheckCircle, height = DsSize.touchTarget)
                }
            }
        },
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
    onShow: () -> Unit,
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
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(DsSize.touchTarget).dsClickable(onClick = onShow),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MyLocation, "Show emergency on map", tint = DsColor.danger, modifier = Modifier.size(20.dp)) }
                if (onDismiss != null) {
                    Box(
                        Modifier.size(DsSize.touchTarget).dsClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Dismiss", tint = DsColor.textPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
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

/** Voice guidance on/off. Muting silences every prompt but emergencies; the choice is remembered. */
@Composable
fun VoiceToggleButton(modifier: Modifier = Modifier) {
    val muted by VoiceAnnouncer.muted.collectAsState()
    FloatingButton(
        icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = if (muted) "Turn on voice guidance" else "Mute voice guidance",
        onClick = { VoiceAnnouncer.setMuted(!muted) },
        modifier = modifier,
        tone = FloatingTone.OnDark,
    )
}
