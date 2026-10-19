package com.danglingmind.convotrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.danglingmind.convotrack.data.net.MembershipPlanInfo
import com.danglingmind.convotrack.data.net.ProPlanInfo
import com.danglingmind.convotrack.data.net.TrialInfo
import com.danglingmind.convotrack.data.net.UsageInfo
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil

/** Riders per ride on the free plan, used until the server's number has loaded. */
const val FREE_RIDER_CAP = 5

/** Riders per ride on ConvoTrack Pro, used only until the server's `proPlan` has loaded. */
const val PRO_RIDER_CAP = 25

/** Where the rider stands, most important first (iOS `MembershipStore.Access`). */
sealed interface Access {
    data object Pro : Access
    /** Free rider inside the app-level trial. */
    data class Trial(val endsAtMillis: Long) : Access
    /** Free rider whose enforced trial is over: creating and joining are blocked. */
    data object TrialExpired : Access
    /** Free rider with no enforced trial (enforcement off, or server state not loaded yet). */
    data object Free : Access
}

/**
 * The rider's plan, trial and usage as far as the client can tell: the server's view from
 * `/users/me`, upgraded by a local Play entitlement (never downgraded by it) — the same OR rule
 * iOS uses. Cached for the session so every screen doesn't re-fetch it. Failures keep the last
 * known values: offline must never look like "trial ended".
 */
object PlanGate {
    private val _serverPlan = MutableStateFlow<MembershipPlanInfo?>(null)
    val serverPlan: StateFlow<MembershipPlanInfo?> = _serverPlan

    private val _trial = MutableStateFlow<TrialInfo?>(null)
    val trial: StateFlow<TrialInfo?> = _trial

    private val _usage = MutableStateFlow<UsageInfo?>(null)
    val usage: StateFlow<UsageInfo?> = _usage

    private val _proPlan = MutableStateFlow<ProPlanInfo?>(null)
    /** What Pro offers, from the server; null until loaded (or from an older server). */
    val proPlan: StateFlow<ProPlanInfo?> = _proPlan

    /**
     * Invite code the rider couldn't use yet because Pro was needed. `MainScreen` reopens the join
     * sheet with it as soon as the rider goes Pro.
     */
    var joinAfterUpgradeCode: String? = null

    /** Debug builds only (set from `MainActivity`): trial|lastday|expired|pro|free. */
    var debugAccessOverride: String? = null

    suspend fun refresh() {
        val me = runCatching { apiCall { ServiceLocator.api.fetchMe() } }.getOrNull() ?: return
        _serverPlan.value = me.plan
        _trial.value = me.trial
        _usage.value = me.usage
        me.proPlan?.let { _proPlan.value = it }
    }

    /** Pure, so the access rules match iOS exactly and can be reasoned about without a clock. */
    fun access(isPremium: Boolean, trial: TrialInfo?, nowMillis: Long): Access {
        when (debugAccessOverride) {
            "trial" -> return Access.Trial(nowMillis + 2 * 86_400_000L - 60_000)
            "lastday" -> return Access.Trial(nowMillis + 5 * 3_600_000L)
            "expired" -> return Access.TrialExpired
            "pro" -> return Access.Pro
            "free" -> return Access.Free
        }
        if (isPremium) return Access.Pro
        val endsAt = trial?.takeIf { it.enforced }?.endsAtMillis ?: return Access.Free
        return if (endsAt > nowMillis) Access.Trial(endsAt) else Access.TrialExpired
    }

    /** Whole days left, rounded up; 1 on the last day. */
    fun trialDaysLeft(access: Access, nowMillis: Long): Int? {
        val trial = access as? Access.Trial ?: return null
        val days = ceil((trial.endsAtMillis - nowMillis) / 86_400_000.0).toInt()
        return days.coerceAtLeast(1)
    }
}

/** Everything a screen needs to know about the rider's plan, recomposing on any change. */
data class PlanState(
    val isPremium: Boolean,
    val access: Access,
    /** Whole days left in the trial; null outside an active trial. */
    val trialDaysLeft: Int?,
    val riderCap: Int,
    /** Free-plan limits from the server; null for Pro or until loaded. */
    val freePlan: MembershipPlanInfo?,
    /** `/users/me` has answered at least once this session. */
    val loaded: Boolean,
    /** Riders per ride on Pro, for upsell copy shown to free riders. */
    val proRiderCap: Int,
) {
    /** Creating or joining needs Pro first. The server enforces it too; this just skips a failed request. */
    val mustUpgradeToRide: Boolean get() = access == Access.TrialExpired
}

@Composable
fun rememberPlanState(): PlanState {
    val billing = remember { ServiceLocator.billing }
    val localPremium by billing.isPremium.collectAsState()
    val serverPlan by PlanGate.serverPlan.collectAsState()
    val trial by PlanGate.trial.collectAsState()
    val proPlan by PlanGate.proPlan.collectAsState()
    LaunchedEffect(Unit) {
        billing.start()
        if (PlanGate.serverPlan.value == null) PlanGate.refresh()
    }
    val now = System.currentTimeMillis()
    val access = PlanGate.access(localPremium || serverPlan?.isPremium == true, trial, now)
    val premium = access == Access.Pro
    // Only trust the server's cap when it describes the plan the rider is on right now — a
    // just-purchased Pro rider may still hold the free plan's response.
    val cap = serverPlan?.takeIf { it.isPremium == premium }?.maxRidersPerRide
        ?: if (premium) proPlan?.maxRidersPerRide ?: PRO_RIDER_CAP else FREE_RIDER_CAP
    return PlanState(
        isPremium = premium,
        access = access,
        trialDaysLeft = PlanGate.trialDaysLeft(access, now),
        riderCap = cap,
        freePlan = serverPlan?.takeIf { !it.isPremium },
        loaded = serverPlan != null,
        proRiderCap = proPlan?.maxRidersPerRide ?: PRO_RIDER_CAP,
    )
}

data class RiderCap(val isPremium: Boolean, val cap: Int)

/**
 * The max-riders ceiling for this rider, from the server's plan. Free riders are offered the
 * paywall past it. Recomposes the moment a purchase lands, since the billing entitlement is observed.
 */
@Composable
fun rememberRiderCap(): RiderCap {
    val plan = rememberPlanState()
    return RiderCap(isPremium = plan.isPremium, cap = plan.riderCap)
}
