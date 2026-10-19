package com.danglingmind.convotrack.ui.main

import com.danglingmind.convotrack.ui.designsystem.gold
import com.danglingmind.convotrack.ui.designsystem.GoProButton
import com.danglingmind.convotrack.ui.designsystem.DsPro
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.ui.common.PlanState
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.TextAction
import com.danglingmind.convotrack.ui.designsystem.TextActionTone

/**
 * The compact app-open upsell (iOS `ProNudgeSheet`). Sells the idea — countdown + three reasons —
 * and hands off to the paywall for prices and terms. No purchase button lives here, so the
 * subscription disclosure stays in one place.
 */
@Composable
fun ProNudgeContent(plan: PlanState, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    val days = (plan.access as? Access.Trial)?.let { plan.trialDaysLeft }?.takeIf { it > 1 }
    val headline = when (plan.access) {
        is Access.Trial -> if (days != null) "days left in your free trial" else "Your free trial ends today"
        Access.TrialExpired -> "Your free trial has ended"
        Access.Pro, Access.Free -> "Ride bigger with Pro"
    }
    val subtitle = when (plan.access) {
        is Access.Trial -> "After that, creating and joining rides needs Pro."
        Access.TrialExpired -> "Go Pro to keep creating and joining rides with your crew."
        Access.Pro, Access.Free -> "Bigger packs, no monthly ride limit, every ride kept."
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = DsSpace.gutter).padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (days != null) {
                Text("$days", style = DsType.heroTitle.gold(), modifier = Modifier.clearAndSetSemantics {})
            } else {
                IconDisc(
                    if (plan.access == Access.TrialExpired) Icons.Filled.Lock else Icons.Filled.WorkspacePremium,
                    size = 56.dp, iconSize = 26.dp, background = DsPro.gold.copy(alpha = 0.14f), tint = DsPro.gold,
                )
            }
            Text(
                headline,
                style = DsType.titleLarge,
                color = DsColor.textPrimary,
                textAlign = TextAlign.Center,
                // "2 days left…" reads as one sentence even though the number is drawn above it.
                modifier = Modifier.semantics {
                    heading()
                    if (days != null) contentDescription = "$days $headline"
                },
            )
            Text(subtitle, style = DsType.body, color = DsColor.textSecondary, textAlign = TextAlign.Center)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Benefit(Icons.Filled.Groups, "Bring your whole crew — bigger packs")
            Benefit(Icons.Filled.AllInclusive, "No monthly ride limit")
            Benefit(Icons.Filled.Schedule, "Every ride kept in your history")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GoProButton("See plans", onClick = onSeePlans, icon = Icons.Filled.WorkspacePremium)
            TextAction("Not now", onClick = onNotNow, tone = TextActionTone.Dismiss)
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconDisc(icon, size = 32.dp, iconSize = 16.dp, background = DsPro.gold.copy(alpha = 0.14f), tint = DsPro.gold)
        Text(text, style = DsType.bodySmall, color = DsColor.textPrimary)
    }
}
