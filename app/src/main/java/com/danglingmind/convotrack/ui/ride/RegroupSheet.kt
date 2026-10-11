package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.GhostButton
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.SegmentedControl
import com.danglingmind.convotrack.ui.designsystem.SheetTitle
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import com.danglingmind.convotrack.ui.designsystem.glassSurface


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
    val view = LocalView.current
    var selected by remember { mutableStateOf<RegroupReason?>(null) }
    var distance by remember { mutableStateOf(RegroupDistance.CURRENT) }

    Column(Modifier.padding(bottom = 24.dp)) {
        SheetTitle("Regroup", Icons.Filled.Groups)
        Text(
            "Tap a reason to broadcast instantly",
            style = DsType.body,
            color = DsColor.textSecondary,
            modifier = Modifier.padding(horizontal = DsSpace.gutter).padding(top = 8.dp, bottom = 24.dp),
        )

        // Distance selector — offsets the meet point this far ahead on the route. Chosen BEFORE
        // the reason, because the reason tap is what fires.
        Column(Modifier.padding(horizontal = DsSpace.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("Meet point", compact = true)
            SegmentedControl(
                options = RegroupDistance.entries,
                selected = distance,
                label = { it.label },
                onSelect = { distance = it },
            )
        }

        Spacer(Modifier.height(24.dp))

        // Reason grid (2x2). Each tile fires on tap.
        Column(Modifier.padding(horizontal = DsSpace.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RegroupReason.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { reason ->
                        ReasonTile(
                            reason = reason,
                            sending = isBroadcasting && selected == reason,
                            enabled = !isBroadcasting,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isBroadcasting) return@ReasonTile
                                view.dsHaptic(if (reason.isEmergency) DsHaptic.Destructive else DsHaptic.Primary)
                                selected = reason
                                onBroadcast(reason, distance.meters)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        GhostButton("Cancel request", onClick = onCancel, modifier = Modifier.padding(horizontal = DsSpace.gutter))
    }
}

private fun RegroupReason.accent(): Color = when (this) {
    RegroupReason.FUEL -> DsColor.catFuel
    RegroupReason.FOOD -> DsColor.catFood
    RegroupReason.SCENIC -> DsColor.catScenic
    RegroupReason.EMERGENCY -> DsColor.danger
}

@Composable
private fun ReasonTile(
    reason: RegroupReason,
    sending: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = reason.accent()
    Column(
        modifier
            .heightIn(min = 112.dp)
            .glassSurface(
                DsShape.lg,
                if (reason.isEmergency) DsColor.dangerTint else DsColor.card,
                when {
                    sending -> accent
                    reason.isEmergency -> DsColor.dangerBorder
                    else -> DsColor.borderSubtle
                },
            )
            .dsClickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            if (sending) CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Icon(reason.icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Text(reason.label.uppercase(), style = DsType.buttonPill, color = DsColor.textPrimary)
    }
}
