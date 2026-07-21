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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

// Prices mirror the iOS StoreKit products (convotrack.storekit):
//   danglingmind.convotrack.membership.monthly  $4.99 / month
//   danglingmind.convotrack.membership.yearly   $39.99 / year  (= $3.33/mo, save 33%)
// Billing is Google Play on Android, so the store wording differs from the Apple copy.
private const val MONTHLY_PRICE = "$4.99"
private const val YEARLY_PRICE = "$39.99"
private const val YEARLY_PER_MONTH = "$3.33"
private const val SAVINGS_PCT = 33

private enum class Plan { MONTHLY, YEARLY }

private data class Benefit(val icon: ImageVector, val title: String, val detail: String)

private val benefits = listOf(
    Benefit(Icons.Filled.Groups, "25 RIDERS", "Fill every seat in the pack"),
    Benefit(Icons.Filled.AllInclusive, "UNLIMITED", "No monthly ride cap, ever"),
    Benefit(Icons.Filled.BarChart, "ANALYTICS", "Live stats & sync scores"),
    Benefit(Icons.Filled.Schedule, "365 DAYS", "Full ride history archive"),
)

@Composable
fun MembershipScreen(onClose: () -> Unit) {
    var selected by remember { mutableStateOf(Plan.YEARLY) }
    var showSuccess by remember { mutableStateOf(false) }

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
                    PlansSection(selected) { selected = it }
                    CtaStack(selected, onPurchase = { showSuccess = true })
                }
            }
        }
    }
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
private fun PlansSection(selected: Plan, onSelect: (Plan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            PlanCard(
                plan = Plan.YEARLY,
                isSelected = selected == Plan.YEARLY,
                title = "YEARLY",
                subtitle = "$YEARLY_PER_MONTH / mo · billed annually",
                price = YEARLY_PER_MONTH,
                strikethrough = MONTHLY_PRICE,
                onSelect = onSelect,
            )
            Text(
                "SAVE $SAVINGS_PCT%",
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
        PlanCard(
            plan = Plan.MONTHLY,
            isSelected = selected == Plan.MONTHLY,
            title = "MONTHLY",
            subtitle = "Billed every month",
            price = MONTHLY_PRICE,
            priceSuffix = "/ mo",
            onSelect = onSelect,
        )
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    isSelected: Boolean,
    title: String,
    subtitle: String,
    price: String,
    strikethrough: String? = null,
    priceSuffix: String? = null,
    onSelect: (Plan) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) CT.primaryFixed.copy(alpha = if (plan == Plan.YEARLY) 0.1f else 0.06f)
                else CT.surfaceContainerHigh.copy(alpha = 0.4f)
            )
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) CT.primaryFixed.copy(alpha = 0.7f) else CT.outlineVariant.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp),
            )
            .clickable { onSelect(plan) }
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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(price, style = CTType.headlineMd, color = if (isSelected) CT.primaryFixed else CT.onSurface)
            if (strikethrough != null) {
                Text(
                    strikethrough,
                    style = CTType.captionMd.copy(textDecoration = TextDecoration.LineThrough),
                    color = CT.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            if (priceSuffix != null) {
                Text(priceSuffix, style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun CtaStack(selected: Plan, onPurchase: () -> Unit) {
    val period = if (selected == Plan.YEARLY) "year" else "month"
    val price = if (selected == Plan.YEARLY) YEARLY_PRICE else MONTHLY_PRICE
    val ctaLabel = if (selected == Plan.YEARLY) "GET PRO  ·  $YEARLY_PRICE/yr" else "GET PRO  ·  $MONTHLY_PRICE/mo"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LimePrimaryButton(text = ctaLabel, onClick = onPurchase)

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
        Text(
            "ConvoTrack Pro is a $price/$period auto-renewing subscription. Payment is charged " +
                "to your Google Play account at confirmation of purchase. The subscription renews " +
                "automatically unless it is canceled at least 24 hours before the end of the current " +
                "period. Manage or cancel anytime in Google Play subscription settings.",
            style = CTType.captionMd,
            color = CT.onSurfaceVariant.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )

        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Restore purchases", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.38f))
            Text("·", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.38f))
            Text("Terms", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.38f))
            Text("·", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.38f))
            Text("Privacy", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
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
