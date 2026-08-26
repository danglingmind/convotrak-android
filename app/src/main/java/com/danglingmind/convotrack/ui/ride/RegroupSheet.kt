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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType

/**
 * Regroup reasons. EMERGENCY emits the `ride:emergency` socket event; the other three emit
 * `ride:regroup` (see the architectural decision in CLAUDE.md).
 */
enum class RegroupReason(val label: String, val icon: ImageVector, val isEmergency: Boolean = false) {
    FUEL("Fuel Stop", Icons.Filled.LocalGasStation),
    FOOD("Food Stop", Icons.Filled.Restaurant),
    SCENIC("Scenic Stop", Icons.Filled.PhotoCamera),
    EMERGENCY("Emergency", Icons.Filled.Warning, isEmergency = true),
}

/** How far ahead on the route to place the meet point when broadcasting from the rider's position. */
enum class RegroupDistance(val meters: Double, val label: String) {
    CURRENT(0.0, "Current"),
    AHEAD_100(100.0, "100 m"),
    AHEAD_500(500.0, "500 m"),
}

/**
 * The regroup sheet.
 *
 * Two deliberate departures from the version this replaces:
 *
 * 1. **Tapping a reason broadcasts immediately** — no selection step, no confirm button. That is
 *    now the entire interaction. A rider reaching for this is moving, usually with one glove off.
 * 2. **There is no "search for a meet location" path.** Picking a place off a map takes long enough
 *    that the group has already moved on, which defeats the point of a regroup. Instead the meet
 *    point is the rider's own position, optionally offset a fixed distance ahead ALONG THE ROUTE —
 *    which is what "let's stop at the next petrol station" actually means.
 */
@Composable
fun RegroupSheet(
    isBroadcasting: Boolean,
    onBroadcast: (RegroupReason, aheadMeters: Double) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<RegroupReason?>(null) }
    var distance by remember { mutableStateOf(RegroupDistance.CURRENT) }

    Column(Modifier.padding(bottom = 32.dp)) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Flag, null, tint = CT.primaryFixed, modifier = Modifier.size(32.dp))
            Text("REGROUP", style = CTType.headlineLg, color = CT.primaryFixed)
        }

        Text(
            "Tap a reason to broadcast instantly",
            style = CTType.bodyLg,
            color = CT.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        )

        // Distance selector — offsets the meet point this far ahead on the route. Chosen BEFORE
        // the reason, because the reason tap is what fires.
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("MEET POINT", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CT.surfaceContainerHigh),
            ) {
                RegroupDistance.entries.forEach { option ->
                    val active = distance == option
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (active) CT.primaryFixed else Color.Transparent)
                            .clickable { distance = option }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = CTType.toggleLabel,
                            color = if (active) CT.onPrimaryFixed else CT.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Reason grid (2x2). Each tile fires on tap.
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RegroupReason.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { reason ->
                        ReasonTile(
                            reason = reason,
                            isSelected = selected == reason,
                            enabled = !isBroadcasting,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isBroadcasting) return@ReasonTile
                                selected = reason
                                onBroadcast(reason, distance.meters)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().clickable(onClick = onCancel).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "CANCEL REQUEST",
                style = CTType.labelCaps,
                color = CT.onSurfaceVariant,
                letterSpacing = 8.sp,
            )
        }
    }
}

@Composable
private fun ReasonTile(
    reason: RegroupReason,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (reason.isEmergency) CT.errorColor else CT.primaryFixed
    val borderColor = when {
        isSelected -> CT.primaryFixed
        reason.isEmergency -> CT.errorColor.copy(alpha = 0.4f)
        else -> CT.outlineVariant.copy(alpha = 0.3f)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (reason.isEmergency) CT.errorContainer.copy(alpha = 0.1f) else Color(0xFF1C1C1C))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(reason.icon, null, tint = accent, modifier = Modifier.size(28.dp))
        Text(reason.label, style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
    }
}
