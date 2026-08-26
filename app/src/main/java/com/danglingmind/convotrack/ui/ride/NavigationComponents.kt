package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType

/**
 * Shared navigation HUD components. Extracted so the portrait bottom bar and the landscape side
 * panel render the SAME pieces — the two layouts differ in arrangement and scale, never in what
 * they show, which is the only way they can be kept from drifting apart.
 */

/**
 * Arrow for the current maneuver, from the Routes API's `maneuver` enum.
 *
 * Locale-independent and unambiguous, unlike scanning the instruction text — which matched road
 * names ("Turn right onto Left Cross Rd" hit "left" first, drawing the wrong arrow) and flattened
 * slight/sharp/ramp variants into one. The text scan survives only as a fallback for a step that
 * arrives without a maneuver.
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
 * The turn-by-turn banner.
 *
 * It owns its whole row in both orientations — the back button and the live-rider pill moved out
 * (exit lives in the end-ride confirmation, the count in the leaderboard row) — and that reclaimed
 * width is what pays for these sizes. Squeezed into a shared row it left ~177dp for text, which is
 * why the distance sat at 10sp and the street name at 12sp: unreadable at road speed, which is the
 * only speed it is read at.
 *
 * Landscape runs one step smaller across the board: the side panel is a third of the width and the
 * whole HUD has to fit in ~390dp of height, so the same numbers would crowd the leaderboard and
 * stats out of the panel.
 */
@Composable
fun TurnInstructionCard(
    instruction: String,
    maneuver: String,
    distanceMeters: Double,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val tile = if (isLandscape) 46.dp else 52.dp
    val glyph = if (isLandscape) 21.dp else 24.dp
    val distanceSp = if (isLandscape) 22.sp else 26.sp
    val streetSp = if (isLandscape) 15.sp else 17.sp

    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CT.surfaceContainerHigh.copy(alpha = 0.92f))
            .border(1.dp, CT.primaryFixed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(tile).clip(RoundedCornerShape(10.dp)).background(CT.primaryFixed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                maneuverIcon(maneuver, instruction),
                null,
                tint = CT.onPrimaryFixed,
                modifier = Modifier.size(glyph),
            )
        }
        // No Spacer here on purpose: the icon is fixed-width, so the Row hands ALL the remaining
        // width to this column, which is what lets the street name wrap instead of truncating.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val distText = turnDistanceText(distanceMeters)
            if (distText.isNotEmpty()) {
                Text(
                    distText,
                    style = CTType.dataMono.copy(fontSize = distanceSp, fontWeight = FontWeight.Black),
                    color = CT.primaryFixed,
                    maxLines = 1,
                )
            }
            // Two lines, not one. At 17sp a long road name no longer fits on a single line, and a
            // truncated name is worth less than a wrapped one.
            Text(
                instruction.ifEmpty { "Follow route" },
                style = CTType.bodyMd.copy(fontSize = streetSp, fontWeight = FontWeight.SemiBold),
                color = CT.onSurface,
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
        Text(label, style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value,
                style = CTType.dataMono.copy(
                    fontSize = if (isLandscape) 20.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = CT.onSurface,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    unit,
                    style = CTType.labelCaps.copy(fontSize = 9.sp),
                    color = CT.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun BarDivider(isLandscape: Boolean) {
    Box(
        Modifier
            .width(1.dp)
            .height(if (isLandscape) 26.dp else 30.dp)
            .background(CT.outlineVariant.copy(alpha = 0.25f)),
    )
}

/** Live rider count, presence-based so it can never disagree with the greyed leaderboard rows. */
@Composable
fun LiveRiderCountPill(count: Int) {
    Row(
        Modifier.clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(CT.primaryFixed))
        Text(
            if (count > 0) "$count" else "--",
            style = CTType.dataMono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = CT.primaryFixed,
        )
    }
}

/**
 * One leaderboard card.
 *
 * No rank number: the strip is already ordered by position, so printing it again spent 18dp of a
 * narrow pill on information the sequence already carries. The rank still drives the leader
 * highlight.
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

    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) CT.primaryFixed.copy(alpha = 0.12f) else CT.surfaceContainerHigh.copy(alpha = 0.88f))
            .border(
                if (highlighted) 1.5.dp else 1.dp,
                if (highlighted) CT.primaryFixed else CT.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp),
            )
            .alpha(if (isOffline) 0.5f else 1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(CT.surfaceVariant)
                    .border(
                        if (highlighted) 2.dp else 1.dp,
                        if (highlighted) CT.primaryFixed else CT.outlineVariant.copy(alpha = 0.4f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.name.take(1).uppercase(),
                    style = CTType.bodyMd.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = if (highlighted) CT.primaryFixed else CT.onSurface,
                )
            }
            // Grey dot = this rider's heartbeats lapsed (backgrounded / lost connection).
            if (isOffline) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(CT.onSurfaceVariant)
                        .border(1.5.dp, CT.surfaceContainerHigh, CircleShape),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    firstName,
                    style = CTType.bodyMd.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = CT.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                if (row.isMe) {
                    Text(
                        "YOU",
                        style = CTType.labelCaps.copy(fontSize = 7.sp),
                        color = CT.onPrimaryFixed,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.clip(CircleShape).background(CT.primaryFixed)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            if (lastName.isNotEmpty() && !compact) {
                Text(
                    lastName,
                    style = CTType.bodyMd.copy(fontSize = 10.sp),
                    color = CT.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Slim "reconnecting" strip. Callers gate it on the session's connection state, so it takes no
 *  layout space while the connection is healthy (and doesn't flash on a cold connect or teardown). */
@Composable
fun ConnectionBanner(state: RideRealtimeSession.ConnectionState, modifier: Modifier = Modifier) {
    if (state != RideRealtimeSession.ConnectionState.RECONNECTING) return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(CT.errorContainer.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = CT.errorColor,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text("RECONNECTING…", style = CTType.labelCaps, color = CT.errorColor, letterSpacing = 1.sp)
    }
}

/** Small "OFF ROUTE" chip while the rider has strayed from the line being followed. */
@Composable
fun OffRouteBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape)
            .background(CT.errorContainer.copy(alpha = 0.9f))
            .border(1.dp, CT.errorColor.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = CT.errorColor, modifier = Modifier.size(12.dp))
        Text("OFF ROUTE", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.errorColor, letterSpacing = 1.sp)
    }
}

private fun regroupAccentColor(type: String): Color = when (type) {
    "FUEL" -> Color(0xFFFFA100)
    "FOOD" -> Color(0xFFFF6E00)
    "SCENIC" -> Color(0xFF029CE6)
    else -> Color(0xFF7A1CA3)
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
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Groups, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("REGROUP CALLED", style = CTType.labelCaps.copy(fontSize = 10.sp), color = accent, letterSpacing = 1.sp)
            Text("Head to the ${regroupLabelText(type)}", style = CTType.bodyMd.copy(fontSize = 12.sp), color = CT.onSurface)
        }
    }
}

/** Transient "regroup complete" confirmation once the server resolves it. */
@Composable
fun RegroupResolvedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(CT.tertiaryFixed.copy(alpha = 0.1f))
            .border(1.5.dp, CT.tertiaryFixed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = CT.tertiaryFixed, modifier = Modifier.size(20.dp))
        Text("Regroup complete · Everyone arrived", style = CTType.bodyMd.copy(fontSize = 12.sp), color = CT.tertiaryFixed)
    }
}

/**
 * Emergency alert.
 *
 * [onDismiss] is null for riders who aren't allowed to clear it (only the raiser or the leader
 * can); they get no dismiss control and the banner persists — with the pin and the repeating
 * siren — until an authorized rider clears it.
 */
@Composable
fun EmergencyBanner(
    reporterName: String,
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(CT.errorContainer.copy(alpha = 0.22f))
            .border(1.5.dp, CT.errorColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(CT.errorColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, null, tint = CT.errorColor, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("EMERGENCY", style = CTType.labelCaps.copy(fontSize = 10.sp), color = CT.errorColor, letterSpacing = 1.5.sp)
            Text(
                "$reporterName needs help — head to their location",
                style = CTType.bodyMd.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = CT.onSurface,
            )
            if (message.isNotBlank() && message != "Emergency") {
                Text(message, style = CTType.bodyMd.copy(fontSize = 12.sp), color = CT.onSurfaceVariant)
            }
        }
        if (onDismiss != null) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.6f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Dismiss", tint = CT.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Group-split alert — the pack has spread out. Offers a quick Regroup or a dismiss. */
@Composable
fun GroupSplitAlert(
    gapMeters: Double,
    onIgnore: () -> Unit,
    onRegroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(CT.errorContainer.copy(alpha = 0.2f))
            .border(1.dp, CT.errorColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = CT.errorColor, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("GROUP SPLIT", style = CTType.labelCaps, color = CT.errorColor, letterSpacing = 1.sp)
            Text(
                "Riders are ${String.format(java.util.Locale.US, "%.0f", gapMeters)} m apart",
                style = CTType.bodyMd,
                color = CT.onSurface,
            )
        }
        Text(
            "REGROUP",
            style = CTType.labelCaps,
            color = CT.onPrimaryFixed,
            letterSpacing = 1.sp,
            modifier = Modifier.clip(CircleShape).background(CT.primaryFixed)
                .clickable(onClick = onRegroup).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            "DISMISS",
            style = CTType.labelCaps,
            color = CT.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.clickable(onClick = onIgnore).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** Primary "mark as arrived" pill shown while a regroup is active and I haven't arrived yet. */
@Composable
fun ArrivedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(CT.primaryFixed).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = CT.onPrimaryFixed, modifier = Modifier.size(16.dp))
        Text("MARK AS ARRIVED", style = CTType.labelCaps, color = CT.onPrimaryFixed, letterSpacing = 0.5.sp)
    }
}

/**
 * Quiet "waiting for others" pill after I've marked arrived, until the regroup resolves.
 * Deliberately small so it doesn't compete with the map or the primary controls.
 */
@Composable
fun WaitingForOthersPill(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Schedule, null, tint = CT.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Text("WAITING FOR OTHERS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 0.5.sp)
    }
}

/** RESUME capsule, shown once the rider has panned away from the follow camera. */
@Composable
fun ResumeNavigationPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(CT.primaryFixed).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Refresh, null, tint = CT.onPrimaryFixed, modifier = Modifier.size(14.dp))
        Text("RESUME", style = CTType.labelCaps.copy(fontSize = 10.sp), color = CT.onPrimaryFixed, letterSpacing = 1.2.sp)
    }
}

/** Square icon control used by the portrait bottom bar. */
@Composable
fun NavControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    background: Color,
    border: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
