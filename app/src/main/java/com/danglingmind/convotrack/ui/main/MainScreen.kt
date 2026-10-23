package com.danglingmind.convotrack.ui.main

import com.danglingmind.convotrack.data.analytics.Analytics

import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.data.upsell.PaywallAnalytics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.designsystem.BottomNavFloating
import com.danglingmind.convotrack.ui.designsystem.DsBottomSheet
import com.danglingmind.convotrack.ui.designsystem.DsTab
import com.danglingmind.convotrack.ui.history.RideHistoryScreen
import com.danglingmind.convotrack.ui.home.HomeScreen
import com.danglingmind.convotrack.ui.join.JoinRideSheet
import com.danglingmind.convotrack.ui.profile.ProfileScreen
import com.danglingmind.convotrack.ui.scan.QRScannerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.danglingmind.convotrack.data.net.isPlanLimit
import com.danglingmind.convotrack.data.upsell.AppForeground
import com.danglingmind.convotrack.data.upsell.UpsellPolicy
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.ui.common.PlanGate
import com.danglingmind.convotrack.ui.common.rememberPlanState

@Composable
fun MainScreen(
    onCreateRide: () -> Unit,
    onOpenLobby: (rideId: String) -> Unit,
    onOpenSummary: (rideId: String) -> Unit,
    onOpenMembership: () -> Unit,
    onSignOut: () -> Unit,
    initialInviteCode: String? = null,
    onInviteConsumed: () -> Unit = {},
    /** A tab picked from the bottom nav on a screen outside the tabs (the lobby). */
    requestedTab: DsTab? = null,
    onRequestedTabConsumed: () -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(DsTab.Home) }
    LaunchedEffect(activeTab) { Analytics.screen("tab_${activeTab.name.lowercase()}") }
    var showJoin by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showNudge by remember { mutableStateOf(false) }
    var prefillCode by remember { mutableStateOf("") }
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    val plan = rememberPlanState()

    /** Invite link / QR / manual join. Once the trial is over the paywall comes first; the code is
     *  kept so going Pro drops the rider straight into the join sheet (iOS `openJoin`). */
    fun openJoin(code: String) {
        if (plan.mustUpgradeToRide) {
            PlanGate.joinAfterUpgradeCode = code.ifEmpty { null }
            Paywall.present(PaywallSource.JoinBlocked)
        } else {
            prefillCode = code
            showJoin = true
        }
    }

    // A ride-invite deep link (convotrack://join/CODE or the https App Link)
    // opens the join sheet with the code prefilled. Consumed once so rotation
    // or backstack changes don't reopen it.
    LaunchedEffect(initialInviteCode) {
        val code = initialInviteCode
        if (!code.isNullOrEmpty()) {
            openJoin(code)
            onInviteConsumed()
        }
    }

    // A rider who went Pro from a blocked invite carries straight on into that ride.
    LaunchedEffect(plan.access) { Analytics.setAccess(plan.access) }
    LaunchedEffect(plan.isPremium) {
        val code = PlanGate.joinAfterUpgradeCode
        if (plan.isPremium && code != null) {
            PlanGate.joinAfterUpgradeCode = null
            prefillCode = code
            showJoin = true
        }
    }

    // Refresh plan/trial on every foreground; on a real app open, consider the nudge once the
    // server state is fresh (iOS `MainTabView.handleForeground`).
    val foregrounds by AppForeground.foregrounds.collectAsState()
    LaunchedEffect(foregrounds) {
        PlanGate.refresh()
        // Debug `forceNudge` (iOS parity): once per launch, skipping the cadence and the ride check.
        // Read, not cleared here: this effect restarts when `foregrounds` ticks during launch, and
        // clearing early would lose the request. Cleared once the nudge is actually shown.
        val forced = PlanGate.debugForceNudge
        if (!AppForeground.consumeAppOpen() && !forced) return@LaunchedEffect
        delay(1500)
        val busy = showJoin || showScanner || showNudge || initialInviteCode != null ||
            (!forced && ServiceLocator.location.activeRideId.value != null)
        if (busy) return@LaunchedEffect
        val upsell = ServiceLocator.upsell
        // Read the freshest access, not the value captured when this effect started.
        val access = PlanGate.access(
            isPremium = ServiceLocator.billing.isPremium.value || PlanGate.serverPlan.value?.isPremium == true,
            trial = PlanGate.trial.value,
            nowMillis = System.currentTimeMillis(),
        )
        if ((forced && access != Access.Pro) || UpsellPolicy.shouldShowAppOpenNudge(upsell.inputs(access))) {
            PlanGate.debugForceNudge = false
            upsell.recordNudgeShown()
            PaywallAnalytics.log(PaywallAnalytics.Event.NudgeShown, PaywallSource.AppOpen, access)
            showNudge = true
        }
    }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            activeTab = requestedTab
            onRequestedTabConsumed()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (activeTab) {
            DsTab.History -> RideHistoryScreen(onOpenSummary = onOpenSummary, onOpenRide = onOpenLobby)
            DsTab.Home -> HomeScreen(
                onCreateRide = {
                    if (plan.mustUpgradeToRide) Paywall.present(PaywallSource.CreateBlocked) else onCreateRide()
                },
                onJoinRide = { openJoin("") },
                onOpenRide = onOpenLobby,
                onSeeAllHistory = { activeTab = DsTab.History },
                onOpenSummary = onOpenSummary,
            )
            DsTab.Profile -> ProfileScreen(onOpenMembership = onOpenMembership, onSignedOut = onSignOut)
        }

        // The bar floats over content; each tab reserves bottomNavClearance() for it.
        BottomNavFloating(
            active = activeTab,
            onSelect = { activeTab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showJoin) {
        DsBottomSheet(onDismiss = { showJoin = false; prefillCode = "" }) {
            JoinRideSheet(
                prefillCode = prefillCode,
                onJoined = { rideId ->
                    showJoin = false
                    prefillCode = ""
                    onOpenLobby(rideId)
                },
                onScanQr = {
                    showJoin = false
                    showScanner = true
                },
                onSeePro = { code ->
                    showJoin = false
                    prefillCode = ""
                    PlanGate.joinAfterUpgradeCode = code.takeIf { it.length == 6 }
                    Paywall.present(PaywallSource.JoinBlocked)
                },
            )
        }
    }

    if (showNudge) {
        // A swipe-down counts as "not now".
        DsBottomSheet(onDismiss = {
            showNudge = false
            ServiceLocator.upsell.recordNudgeDismissed()
            PaywallAnalytics.log(PaywallAnalytics.Event.NudgeDismissed, PaywallSource.AppOpen, plan.access)
        }) {
            ProNudgeContent(
                plan = plan,
                onSeePlans = {
                    showNudge = false
                    Paywall.present(PaywallSource.AppOpen)
                },
                onNotNow = {
                    showNudge = false
                    ServiceLocator.upsell.recordNudgeDismissed()
                    PaywallAnalytics.log(PaywallAnalytics.Event.NudgeDismissed, PaywallSource.AppOpen, plan.access)
                },
            )
        }
    }

    // Full-screen QR scanner. On a valid code, resolve + join, then open the lobby.
    if (showScanner) {
        QRScannerScreen(
            onClose = { showScanner = false },
            onCode = { code ->
                showScanner = false
                scope.launch {
                    if (plan.mustUpgradeToRide) {
                        openJoin(code)
                        return@launch
                    }
                    val result = runCatching {
                        val ride = apiCall { api.getRideByInviteCode(code) }
                        apiCall { api.joinRide(ride.rideId) }
                        ride.rideId
                    }
                    val rideId = result.getOrNull()
                    when {
                        rideId != null -> onOpenLobby(rideId)
                        result.exceptionOrNull()?.isPlanLimit == true -> {
                            PlanGate.joinAfterUpgradeCode = code
                            Paywall.present(PaywallSource.JoinBlocked)
                        }
                        else -> showJoin = true
                    }
                }
            },
        )
    }
}
