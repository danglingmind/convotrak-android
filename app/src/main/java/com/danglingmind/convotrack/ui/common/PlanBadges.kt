package com.danglingmind.convotrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danglingmind.convotrack.ui.designsystem.PlanBadge
import com.danglingmind.convotrack.ui.designsystem.PlanBadgeKind

/** The badge for the rider's access. Free riders with no enforced trial are invited like expired ones. */
fun PlanState.badgeKind(): PlanBadgeKind = when (access) {
    Access.Pro -> PlanBadgeKind.Pro
    is Access.Trial -> PlanBadgeKind.Trial(trialDaysLeft ?: 1)
    Access.TrialExpired, Access.Free -> PlanBadgeKind.GoPro
}

/** Plan badge that opens the paywall — except for Pro riders, where it's just a badge. */
@Composable
fun PlanBadgeButton(plan: PlanState, source: PaywallSource, modifier: Modifier = Modifier) {
    val kind = plan.badgeKind()
    PlanBadge(
        kind = kind,
        modifier = modifier,
        onClick = if (kind == PlanBadgeKind.Pro) null else ({ Paywall.present(source) }),
    )
}
