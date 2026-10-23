package com.danglingmind.convotrack.ui.ride.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.ui.common.RiderCap
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FieldLabel
import com.danglingmind.convotrack.ui.designsystem.PromoBanner
import com.danglingmind.convotrack.ui.designsystem.Stepper
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.gold

/** Max riders stepper with the plan's limit under it. Lives in the ride planner's sheet. */
@Composable
internal fun RiderLimitSection(
    maxRiders: Int,
    onChange: (Int) -> Unit,
    riderCap: RiderCap,
    /** Opens the paywall: a free rider tapping + at the cap, or the upgrade banner. */
    onUpgrade: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Max riders")
        // Free riders stop at the free cap; + there opens the paywall instead (iOS parity).
        val atCap = maxRiders >= riderCap.cap
        Stepper(
            value = maxRiders,
            suffix = "riders",
            onDecrement = { if (maxRiders > 2) onChange(maxRiders - 1) },
            onIncrement = {
                when {
                    maxRiders < riderCap.cap -> onChange(maxRiders + 1)
                    !riderCap.isPremium -> onUpgrade()
                }
            },
            canDecrement = maxRiders > 2,
            canIncrement = !atCap || !riderCap.isPremium,
            dimIncrement = atCap,
        )
        AnimatedVisibility(visible = atCap && !riderCap.isPremium) {
            UpgradeBanner(onUpgrade)
        }
        // The limit is always visible, not only once the rider walks into it (iOS parity).
        if (!(atCap && !riderCap.isPremium)) RiderPlanLine(riderCap, onUpgrade)
    }
}

/** "🔒 Up to 5 riders on your plan · Bigger packs with Pro" for free riders; Pro sees its own cap. */
@Composable
private fun RiderPlanLine(riderCap: RiderCap, onUpgrade: () -> Unit) {
    if (riderCap.isPremium) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = DsColor.textMuted, modifier = Modifier.size(14.dp))
            Text("Pro · up to ${riderCap.cap} riders", style = DsType.helper, color = DsColor.textMuted)
        }
    } else {
        Row(
            Modifier.heightIn(min = 48.dp).dsClickable(onClick = onUpgrade),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Lock, null, tint = DsColor.textMuted, modifier = Modifier.size(13.dp))
            Text("Up to ${riderCap.cap} riders on your plan", style = DsType.helper, color = DsColor.textMuted)
            Text("Bigger packs with Pro", style = DsType.helper.gold())
        }
    }
}

/** "Pro unlocks bigger packs · UPGRADE →" under the stepper once a free rider hits the cap. */
@Composable
private fun UpgradeBanner(onUpgrade: () -> Unit) {
    PromoBanner(
        message = "Pro unlocks bigger packs",
        actionLabel = "Go Pro",
        onAction = onUpgrade,
        goProStyle = true,
        modifier = Modifier.padding(top = 2.dp),
    )
}

