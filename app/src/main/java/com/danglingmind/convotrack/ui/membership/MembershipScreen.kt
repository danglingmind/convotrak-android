package com.danglingmind.convotrack.ui.membership

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.danglingmind.convotrack.data.billing.BillingManager
import com.danglingmind.convotrack.data.billing.DisplayPlan
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.UserFacingError
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
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
fun MembershipScreen(onClose: () -> Unit) {
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
    // The COLLECTED VALUE must be read, not just the collector started. `collectAsState()` returns
    // a State; writing to it only invalidates composables that read it. A bare call whose result is
    // discarded starts the collection and then invalidates nothing — the paywall sat on
    // "Loading plans…" forever once the hardcoded price fallbacks were removed, because every
    // derivation below read `_products.value` imperatively.
    val products by billing.products.collectAsState()

    // Connect to Play + load products/entitlement on open; flip to success once entitled.
    LaunchedEffect(Unit) { billing.start() }
    LaunchedEffect(isPremium) { if (isPremium) showSuccess = true }

    // Plans come from Play, ordered longest-cycle-first, with savings derived from the actual
    // billing periods rather than a hardcoded percentage. Empty until Play answers — the paywall
    // then shows its placeholder copy rather than inventing prices.
    // Keyed on `products` so they recompute exactly when Play answers — and only then. Each of
    // these re-maps and re-sorts the whole list, and `savingsFor` is called per plan, so computing
    // them inline ran the same sort several times per frame.
    val plans = remember(products) { billing.displayPlans() }
    val bestValueId = remember(products) { billing.bestValuePlan()?.productId }
    val savingsByProductId = remember(products) {
        plans.mapNotNull { plan -> billing.savingsFor(plan)?.let { plan.productId to it } }.toMap()
    }

    // Default to the best-value plan once the store answers, not to a hardcoded "yearly".
    LaunchedEffect(bestValueId, plans.size) {
        if (selectedProductId == null) {
            selectedProductId = bestValueId ?: plans.firstOrNull()?.productId
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CT.surfaceDim),
    ) {
        // Atmospheric lime glow from the top, mirroring the iOS RadialGradient.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(CT.primaryFixed.copy(alpha = 0.16f), CT.surfaceDim, CT.surfaceDim),
                )
            )
        )

        AnimatedVisibility(visible = showSuccess) {
            SuccessOverlay(onDone = onClose)
        }

        if (!showSuccess) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Close row
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, end = 20.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.8f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, "Close", tint = CT.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    HeroSection()
                    BenefitsGrid()
                    PlansSection(
                        plans = plans,
                        selectedProductId = selectedProductId,
                        bestValueId = bestValueId,
                        savingsFor = { savingsByProductId[it.productId] },
                        onSelect = { selectedProductId = it },
                    )
                    CtaStack(
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

@Composable
private fun HeroSection() {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(CT.primaryFixed.copy(alpha = 0.14f)))
            Icon(Icons.Filled.WorkspacePremium, null, tint = CT.primaryFixed, modifier = Modifier.size(32.dp))
        }
        Text("CONVOTRACK PRO", style = CTType.headlineLg, color = CT.onSurface, letterSpacing = 3.sp)
        Text("Your rides. No limits.", style = CTType.bodyMd, color = CT.onSurfaceVariant)
    }
}

@Composable
private fun BenefitsGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        benefits.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CT.surfaceContainerHigh.copy(alpha = 0.5f))
                            .border(1.dp, CT.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(b.icon, null, tint = CT.primaryFixed, modifier = Modifier.size(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.title, style = CTType.labelCaps, color = CT.onSurface, letterSpacing = 1.sp)
                            Text(b.detail, style = CTType.captionMd, color = CT.onSurfaceVariant)
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
            Text("Loading plans…", style = CTType.bodyMd, color = CT.onSurfaceVariant)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plans.forEach { plan ->
            val savings = savingsFor(plan)
            val isBest = plan.productId == bestValueId
            Box(Modifier.fillMaxWidth()) {
                PlanCard(
                    isSelected = plan.productId == selectedProductId,
                    isHighlighted = isBest,
                    title = plan.period?.label?.uppercase() ?: "SUBSCRIPTION",
                    subtitle = plan.period?.let { "Billed every ${it.label}" } ?: "Recurring",
                    price = plan.period?.let { "${plan.formattedPrice} / ${it.shortLabel}" }
                        // No period we can prove → show the bare price and claim nothing.
                        ?: plan.formattedPrice,
                    perMonth = plan.monthlyEquivalentText(),
                    onSelect = { onSelect(plan.productId) },
                    // The SAVE badge straddles the card's top edge, so a card that carries one is
                    // inset to leave room — otherwise the badge sits inside the card and collides
                    // with the price column.
                    modifier = if (isBest && savings != null) Modifier.padding(top = 12.dp) else Modifier,
                )
                if (isBest && savings != null) {
                    Text(
                        "SAVE $savings%",
                        style = CTType.labelCaps,
                        color = CT.onPrimaryFixed,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CT.primaryFixed)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    isSelected: Boolean,
    /** The best-value plan gets the stronger selected treatment. */
    isHighlighted: Boolean = false,
    title: String,
    subtitle: String,
    price: String,
    perMonth: String? = null,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) CT.primaryFixed.copy(alpha = if (isHighlighted) 0.1f else 0.06f)
                else CT.surfaceContainerHigh.copy(alpha = 0.4f)
            )
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) CT.primaryFixed.copy(alpha = 0.7f) else CT.outlineVariant.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp),
            )
            .clickable { onSelect() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Radio
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(if (isSelected) 2.dp else 1.5.dp, if (isSelected) CT.primaryFixed else CT.outlineVariant.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(11.dp).clip(CircleShape).background(CT.primaryFixed))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = CTType.labelCaps, color = if (isSelected) CT.onSurface else CT.onSurfaceVariant, letterSpacing = 1.5.sp)
            Text(subtitle, style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.7f))
        }
        // Price block — the billed amount is always the dominant element
        // (Apple Guideline 3.1.2(c)); calculated per-month pricing stays subordinate.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                price,
                style = CTType.headlineMd,
                color = if (isSelected && isHighlighted) CT.primaryFixed else CT.onSurface,
            )
            if (perMonth != null) {
                Text(perMonth, style = CTType.captionSm, color = CT.onSurfaceVariant.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun CtaStack(
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
        selected == null -> "GET PRO"
        selected.period != null -> "GET PRO  ·  ${selected.formattedPrice}/${selected.period.shortLabel}"
        else -> "GET PRO  ·  ${selected.formattedPrice}"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (error != null) {
            Text(
                error,
                style = CTType.captionMd,
                color = CT.errorColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        // Disabled until a plan is actually loaded and selected. With no plans the tap had
        // nothing to purchase and silently did nothing, which reads as a broken button.
        LimePrimaryButton(text = ctaLabel, enabled = selected != null, onClick = onPurchase)

        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.VerifiedUser, null, tint = CT.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(14.dp))
            Text(
                "Secured by Google Play  ·  Cancel anytime",
                style = CTType.captionMd,
                color = CT.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }

        // Auto-renewal disclosure (Play Store equivalent of App Store Guideline 3.1.2).
        //
        // The price and period here are the ones Play reported for the SELECTED plan, never
        // constants: this text is a representation of what the user will actually be charged, so a
        // stale hardcoded figure in it is a compliance problem rather than a typo. Until a plan is
        // loaded and chosen, the sentence omits the specifics instead of inventing them.
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
            style = CTType.captionMd,
            color = CT.onSurfaceVariant.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )

        // These three were rendered as plain text with no click handling, which is worse than
        // omitting them: the store requires a reachable privacy policy and a working restore, and
        // a rider tapping a word that looks like a link and getting nothing concludes the app is
        // broken rather than that the label was decorative.
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FooterLink(if (restoring) "Restoring…" else "Restore purchases", onClick = onRestore)
            FooterSeparator()
            FooterLink("Terms") { uriHandler.openUri(ApiClient.TERMS_URL) }
            FooterSeparator()
            FooterLink("Privacy") { uriHandler.openUri(ApiClient.PRIVACY_URL) }
        }
    }
}

/** Footer action. Underlined because at 38% opacity on a dark ground nothing else marks it as
 *  tappable, and these are the three the store expects a rider to be able to find. */
@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = CTType.captionMd.copy(textDecoration = TextDecoration.Underline),
        color = CT.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun FooterSeparator() {
    Text(
        "·",
        style = CTType.captionMd,
        color = CT.onSurfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun SuccessOverlay(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(CT.primaryFixed.copy(alpha = 0.1f)))
            Icon(Icons.Filled.Check, null, tint = CT.primaryFixed, modifier = Modifier.size(44.dp))
        }
        Text(
            "YOU'RE IN",
            style = CTType.headlineLg,
            color = CT.primaryFixed,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text("Welcome to ConvoTrack Pro.", style = CTType.bodyLg, color = CT.onSurface)
        Text(
            "Your rides, no limits.",
            style = CTType.bodyMd,
            color = CT.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        LimePrimaryButton(text = "LET'S RIDE", onClick = onDone)
        Spacer(Modifier.height(32.dp))
    }
}
