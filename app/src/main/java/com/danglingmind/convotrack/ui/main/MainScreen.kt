package com.danglingmind.convotrack.ui.main

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
import kotlinx.coroutines.launch

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
    var showJoin by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var prefillCode by remember { mutableStateOf("") }
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    // A ride-invite deep link (convotrack://join/CODE or the https App Link)
    // opens the join sheet with the code prefilled. Consumed once so rotation
    // or backstack changes don't reopen it.
    LaunchedEffect(initialInviteCode) {
        val code = initialInviteCode
        if (!code.isNullOrEmpty()) {
            prefillCode = code
            showJoin = true
            onInviteConsumed()
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
                onCreateRide = onCreateRide,
                onJoinRide = { showJoin = true },
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
                    val rideId = runCatching {
                        val ride = apiCall { api.getRideByInviteCode(code) }
                        apiCall { api.joinRide(ride.rideId) }
                        ride.rideId
                    }.getOrNull()
                    if (rideId != null) onOpenLobby(rideId) else showJoin = true
                }
            },
        )
    }
}
