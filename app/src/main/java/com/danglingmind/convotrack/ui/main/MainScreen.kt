package com.danglingmind.convotrack.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.history.RideHistoryScreen
import com.danglingmind.convotrack.ui.home.HomeScreen
import com.danglingmind.convotrack.ui.join.JoinRideSheet
import com.danglingmind.convotrack.ui.profile.ProfileScreen
import com.danglingmind.convotrack.ui.scan.QRScannerScreen
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTTab
import com.danglingmind.convotrack.ui.theme.ConvoTrackBottomNav
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCreateRide: () -> Unit,
    onOpenLobby: (rideId: String) -> Unit,
    onOpenSummary: (rideId: String) -> Unit,
    onOpenMembership: () -> Unit,
    onSignOut: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(CTTab.TRACK) }
    var showJoin by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        when (activeTab) {
            CTTab.FLAGGED -> RideHistoryScreen(onOpenSummary = onOpenSummary)
            CTTab.TRACK -> HomeScreen(
                onCreateRide = onCreateRide,
                onJoinRide = { showJoin = true },
                onOpenRide = onOpenLobby,
                onSeeAllHistory = { activeTab = CTTab.FLAGGED },
                onOpenSummary = onOpenSummary,
            )
            CTTab.PROFILE -> ProfileScreen(onOpenMembership = onOpenMembership, onSignedOut = onSignOut)
        }

        // Bottom nav floats over content, pinned to the bottom safe area.
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            ConvoTrackBottomNav(activeTab = activeTab, onSelect = { activeTab = it })
        }
    }

    if (showJoin) {
        ModalBottomSheet(
            onDismissRequest = { showJoin = false },
            sheetState = sheetState,
            containerColor = CT.surfaceDim,
        ) {
            JoinRideSheet(
                onJoined = { rideId ->
                    showJoin = false
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
