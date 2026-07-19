package com.danglingmind.convotrack.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.danglingmind.convotrack.ui.history.RideHistoryScreen
import com.danglingmind.convotrack.ui.home.HomeScreen
import com.danglingmind.convotrack.ui.join.JoinRideSheet
import com.danglingmind.convotrack.ui.profile.ProfileScreen
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTTab
import com.danglingmind.convotrack.ui.theme.ConvoTrackBottomNav

@Composable
fun MainScreen(
    onCreateRide: () -> Unit,
    onOpenLobby: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(CTTab.TRACK) }
    var showJoin by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize()) {
        when (activeTab) {
            CTTab.FLAGGED -> RideHistoryScreen(onOpenSummary = onOpenSummary)
            CTTab.TRACK -> HomeScreen(
                onCreateRide = onCreateRide,
                onJoinRide = { showJoin = true },
                onOpenActiveRide = onOpenLobby,
                onSeeAllHistory = { activeTab = CTTab.FLAGGED },
                onOpenSummary = onOpenSummary,
            )
            CTTab.PROFILE -> ProfileScreen()
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
                onJoined = {
                    showJoin = false
                    onOpenLobby()
                },
            )
        }
    }
}
