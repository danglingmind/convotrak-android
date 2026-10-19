package com.danglingmind.convotrack.ui.membership

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.billing.BillingManager
import com.danglingmind.convotrack.data.billing.DisplayPlan
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.UserFacingError
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.ui.common.PlanState
import com.danglingmind.convotrack.ui.common.rememberPlanState
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.FloatingTone
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import kotlinx.coroutines.launch

// Prices, billing periods and savings all come from Play at runtime — nothing here hardcodes a
// figure. A price or period stated from a constant is one the store may not actually charge, and a
// hardcoded "SAVE 33%" silently becomes a false claim the moment either plan is repriced.
private data class Benefit(val icon: ImageVector, val title: String, val detail: String)

private val benefits = listOf(
    Benefit(Icons.Filled.Groups, "25 RIDERS", "Fill every seat in the pack"),
    Benefit(Icons.Filled.AllInclusive, "UNLIMITED", "No monthly ride cap, ever"),
    Benefit(Icons.Filled.BarChart, "ANALYTICS", "Live stats & sync scores"),
    Benefit(Icons.Filled.Schedule, "365 DAYS", "Full ride history archive"),
)

@Composable
fun MembershipScreen(onClose: () -> Unit, source: PaywallSource = PaywallSource.Profile) {
    val planState = rememberPlanState()
    val context = LocalContext.current
    val billing = remember { ServiceLocator.billing }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // A successful restore flips `isPremium` and the success overlay takes over, so this only ever
    // carries the two outcomes that leave the paywall on screen: nothing found, or Play unreachable.
    var restoreNotice by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val isPremium by billing.isPremium.collectAsState()
    val error by billing.lastError.collectAsState()
    // The COLLECTED VALUE must be read, not just the collector started: writing to a State only
    // invalidates composables that read it.
    val products by billing.products.collectAsState()

    // Connect to Play + load products/entitlement on open; flip to success once entitled.
    LaunchedEffect(Unit) { billing.start() }
    LaunchedEffect(isPremium) { if (isPremium) showSuccess = true }

    // Plans come from Play, ordered longest-cycle-first, with savings derived from the actual billing
    // periods. Keyed on `products` so they recompute exactly when Play answers — and only then.
    val plans = remember(products) { billing.displayPlans() }
    val bestValueId = remember(products) { billing.bestValuePlan()?.productId }
    val savingsByProductId = remember(products) {
        plans.mapNotNull { plan -> billing.savingsFor(plan)?.let { plan.productId to it } }.toMap()
    }

    // Default to the best-value plan once the store answers, not to a hardcoded "yearly".
    LaunchedEffect(bestValueId, plans.size) {
        if (selectedProductId == null) selectedProductId = bestValueId ?: plans.firstOrNull()?.productId
    }

    DsScreen {
        // Accent aura from the top.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Brush.verticalGradient(listOf(DsColor.accent.copy(alpha = 0.12f), DsColor.accent.copy(alpha = 0f)))),
        )

        AnimatedVisibility(visible = showSuccess, enter = fadeIn() + scaleIn(initialScale = 0.96f)) {
            SuccessOverlay(onDone = onClose)
        }

        if (!showSuccess) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = DsSpace.gutter).padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    FloatingButton(Icons.Filled.Close, "Close", onClose, tone = FloatingTone.OnDark)
                }
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = DsSpace.gutter).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    HeroSection(heroTitle(planState), heroSubtitle(planState, source))
                    BenefitsGrid()
                    PlansSection(
                        plans = plans,
                        selectedProductId = selectedProductId,
                        bestValueId = bestValueId,
                        savingsFor = { savingsByProductId[it.productId] },
                        onSelect = { selectedProductId = it },
                    )
                    CtaStack(
                        terms = trialTerms(planState),
                        selected = plans.firstOrNull { it.productId == selectedProductId },
                        error = error ?: restoreNotice,
                        restoring = restoring,
                        onPurchase = {
                            val activity = context.findActivity()
                            val productId = selectedProductId
                            if (activity != null && productId != null) billing.purchase(activity, productId)
                        },
                        onRestore = {
                            if (restoring) return@CtaStack
                            restoring = true
                            restoreNotice = null
                            scope.launch {
                                restoreNotice = when (billing.restorePurchases()) {
                                    BillingManager.EntitlementResult.ACTIVE -> null
                                    BillingManager.EntitlementResult.NONE -> UserFacingError.NOTHING_TO_RESTORE
                                    BillingManager.EntitlementResult.UNAVAILABLE -> UserFacingError.RESTORE_FAILED
                                }
                                restoring = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// The trial leads: while it runs the headline is the countdown, once it ends it's the wall. Monthly
// rides and the rider cap are secondary and only surface through the subtitle (iOS parity).
private fun heroTitle(plan: PlanState): String = when (plan.access) {
    is Access.Trial -> plan.trialDaysLeft?.takeIf { it > 1 }?.let { "$it days left in your free trial" }
        ?: "Your free trial ends today"
    Access.TrialExpired -> "Your free trial has ended"
    Access.Pro, Access.Free -> "ConvoTrack Pro"
}

private fun heroSubtitle(plan: PlanState, source: PaywallSource): String {
    // A limit the rider just ran into explains itself better than the generic pitch.
    when (source) {
        PaywallSource.RiderCap -> return "Pro lifts the ${plan.freePlan?.maxRidersPerRide ?: plan.riderCap}-rider limit. Bring your whole crew."
        PaywallSource.QuotaExceeded -> plan.freePlan?.monthlyLimit?.let {
            return "You've used your $it rides this month. Pro has no monthly limit."
        }
        else -> Unit
    }
    return when (plan.access) {
        is Access.Trial -> "Go Pro to keep creating and joining rides after your trial."
        Access.TrialExpired -> "Go Pro to keep creating and joining rides with your crew."
        Access.Pro, Access.Free -> "Your rides. No limits."
    }
}

/** The trial's terms, from the server — secondary, under the purchase button. */
private fun trialTerms(plan: PlanState): String? {
    val free = plan.freePlan
    return when (plan.access) {
        Access.TrialExpired -> "Creating and joining rides needs Pro now that your free trial has ended."
        is Access.Trial -> if (free?.monthlyLimit != null) {
            "The free trial includes up to ${free.monthlyLimit} rides and ${free.maxRidersPerRide} riders per ride. Creating and joining rides needs Pro once it ends."
        } else {
            "Creating and joining rides needs Pro once the free trial ends."
        }
        Access.Free -> free?.let { p ->
            p.monthlyLimit?.let { "Free plan: $it rides a month, up to ${p.maxRidersPerRide} riders per ride." }
                ?: "Free plan: up to ${p.maxRidersPerRide} riders per ride."
        }
        Access.Pro -> null
    }
}

@Composable
private fun HeroSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconDisc(Icons.Filled.WorkspacePremium, size = 72.dp, iconSize = 34.dp, background = DsColor.accentTint)
        Text(title, style = DsType.heroTitleCompact, color = DsColor.textPrimary, textAlign = TextAlign.Center)
        Text(subtitle, style = DsType.body, color = DsColor.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BenefitsGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        benefits.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    Column(
                        Modifier.weight(1f).glassSurface(DsShape.lg).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconDisc(b.icon, size = 36.dp, iconSize = 18.dp, background = DsColor.accentTint)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.title, style = DsType.fieldLabel, color = DsColor.textPrimary)
                            Text(b.detail, style = DsType.helper, color = DsColor.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlansSection(
    plans: List<DisplayPlan>,
    selectedProductId: String?,
    bestValueId: String?,
    savingsFor: (DisplayPlan) -> Int?,
    onSelect: (String) -> Unit,
) {
    if (plans.isEmpty()) {
        // Play hasn't answered yet. Deliberately shows nothing rather than placeholder prices: a
        // price we haven't been told is a price we might charge differently.
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Text("Loading plans…", style = DsType.body, color = DsColor.textMuted)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plans.forEach { plan ->
            val savings = savingsFor(plan)
            val isBest = plan.productId == bestValueId
            Box(Modifier.fillMaxWidth()) {
                PlanCard(
                    isSelected = plan.productId == selectedProductId,
                    title = plan.period?.label?.uppercase() ?: "SUBSCRIPTION",
                    subtitle = plan.period?.let { "Billed every ${it.label}" } ?: "Recurring",
                    // No period we can prove → show the bare price and claim nothing.
                    price = plan.period?.let { "${plan.formattedPrice} / ${it.shortLabel}" } ?: plan.formattedPrice,
                    perMonth = plan.monthlyEquivalentText(),
                    onSelect = { onSelect(plan.productId) },
                    // The SAVE badge straddles the card's top edge, so a card carrying one is inset.
                    modifier = if (isBest && savings != null) Modifier.padding(top = 12.dp) else Modifier,
                )
                if (isBest && savings != null) {
                    Text(
                        "SAVE $savings%",
                        style = DsType.badgeSmall,
                        color = DsColor.onAccent,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp).clip(DsShape.xs).background(DsColor.accent).padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    isSelected: Boolean,
    title: String,
    subtitle: String,
    price: String,
    perMonth: String?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(DsShape.lg)
            .background(if (isSelected) DsColor.accentTint else DsColor.card)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) DsColor.accentBorder else DsColor.borderSubtle, DsShape.lg)
            .dsClickable(pressedScale = 0.99f, onClick = onSelect)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Radio
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(if (isSelected) 2.dp else 1.5.dp, if (isSelected) DsColor.accent else DsColor.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(11.dp).clip(CircleShape).background(DsColor.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = DsType.fieldLabel, color = if (isSelected) DsColor.textPrimary else DsColor.textSecondary)
            Text(subtitle, style = DsType.helper, color = DsColor.textMuted)
        }
        // The billed amount is always the dominant element; per-month pricing stays subordinate.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(price, style = DsType.statValue, color = DsColor.textPrimary)
            if (perMonth != null) Text(perMonth, style = DsType.helper, color = DsColor.textMuted)
        }
    }
}

@Composable
private fun CtaStack(
    terms: String?,
    selected: DisplayPlan?,
    error: String?,
    restoring: Boolean,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val price = selected?.formattedPrice
    val period = selected?.period?.label
    val ctaLabel = when {
        selected == null -> "Get Pro"
        selected.period != null -> "Get Pro · ${selected.formattedPrice}/${selected.period.shortLabel}"
        else -> "Get Pro · ${selected.formattedPrice}"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error != null) InlineError(error)
        // Disabled until a plan is actually loaded and selected.
        PrimaryButton(ctaLabel, onClick = onPurchase, enabled = selected != null)
        if (terms != null) Text(terms, style = DsType.helper, color = DsColor.textMuted, textAlign = TextAlign.Center)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.VerifiedUser, null, tint = DsColor.textMuted, modifier = Modifier.size(14.dp))
            Text("Secured by Google Play · Cancel anytime", style = DsType.helper, color = DsColor.textMuted)
        }

        // Auto-renewal disclosure. The price and period are the ones Play reported for the SELECTED
        // plan, never constants: a stale figure here is a compliance problem, not a typo.
        Text(
            if (price != null && period != null) {
                "ConvoTrack Pro is a $price/$period auto-renewing subscription. Payment is charged " +
                    "to your Google Play account at confirmation of purchase. The subscription renews " +
                    "automatically unless it is canceled at least 24 hours before the end of the current " +
                    "period. Manage or cancel anytime in Google Play subscription settings."
            } else {
                "ConvoTrack Pro is an auto-renewing subscription. Payment is charged to your Google " +
                    "Play account at confirmation of purchase. The subscription renews automatically " +
                    "unless it is canceled at least 24 hours before the end of the current period. " +
                    "Manage or cancel anytime in Google Play subscription settings."
            },
            style = DsType.helper,
            color = DsColor.textMuted,
            textAlign = TextAlign.Center,
        )

        // The store requires a reachable privacy policy and a working restore, so these are real
        // links, underlined because nothing else marks them as tappable.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FooterLink(if (restoring) "Restoring…" else "Restore purchases", onClick = onRestore)
            Text("·", style = DsType.helper, color = DsColor.textSubtle)
            FooterLink("Terms") { uriHandler.openUri(ApiClient.TERMS_URL) }
            Text("·", style = DsType.helper, color = DsColor.textSubtle)
            FooterLink("Privacy") { uriHandler.openUri(ApiClient.PRIVACY_URL) }
        }
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = DsType.link.copy(textDecoration = TextDecoration.Underline),
        color = DsColor.textSecondary,
        modifier = Modifier.heightIn(min = 44.dp).dsClickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun SuccessOverlay(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(DsSpace.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        IconDisc(Icons.Filled.Check, size = 88.dp, iconSize = 44.dp, background = DsColor.successTint, tint = DsColor.success)
        Spacer(Modifier.height(24.dp))
        Text("You're in", style = DsType.heroTitle, color = DsColor.accent)
        Spacer(Modifier.height(6.dp))
        Text("Welcome to ConvoTrack Pro.", style = DsType.bodyStrong, color = DsColor.textPrimary)
        Text("Your rides, no limits.", style = DsType.body, color = DsColor.textSecondary)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Let's ride", onClick = onDone)
        Spacer(Modifier.height(16.dp))
    }
}
