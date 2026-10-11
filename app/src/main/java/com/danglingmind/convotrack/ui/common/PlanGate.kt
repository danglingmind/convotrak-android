package com.danglingmind.convotrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.danglingmind.convotrack.data.net.MembershipPlanInfo
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Riders per ride on the free plan (iOS `riderCap`, and the server's free-plan limit). */
const val FREE_RIDER_CAP = 5

/** Riders per ride on ConvoTrack Pro. */
const val PRO_RIDER_CAP = 25

/**
 * The rider's plan, as far as the client can tell: the server's plan from `/users/me`, upgraded by a
 * local Play entitlement (never downgraded by it) — the same OR rule iOS and Profile use. Cached for
 * the session so every form doesn't re-fetch it.
 */
object PlanGate {
    private val _serverPlan = MutableStateFlow<MembershipPlanInfo?>(null)
    val serverPlan: StateFlow<MembershipPlanInfo?> = _serverPlan

    suspend fun refresh() {
        runCatching { apiCall { ServiceLocator.api.fetchMe() } }.getOrNull()?.let { _serverPlan.value = it.plan }
    }
}

data class RiderCap(val isPremium: Boolean, val cap: Int)

/**
 * The max-riders ceiling for this rider. Free riders get [FREE_RIDER_CAP] and are offered the
 * paywall past it; Pro gets [PRO_RIDER_CAP]. Recomposes the moment a purchase lands, since the
 * billing entitlement is observed.
 */
@Composable
fun rememberRiderCap(): RiderCap {
    val billing = remember { ServiceLocator.billing }
    val localPremium by billing.isPremium.collectAsState()
    val serverPlan by PlanGate.serverPlan.collectAsState()
    LaunchedEffect(Unit) {
        billing.start()
        PlanGate.refresh()
    }
    val premium = localPremium || serverPlan?.isPremium == true
    return RiderCap(isPremium = premium, cap = if (premium) PRO_RIDER_CAP else FREE_RIDER_CAP)
}
