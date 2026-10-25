package com.danglingmind.convotrack.ui.ride

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.danglingmind.convotrack.data.analytics.Analytics
import com.danglingmind.convotrack.data.analytics.MetaEvents
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.net.CreateRideRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.isPlanLimit
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.ui.common.rememberRiderCap
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.ride.planner.PlannerMode
import com.danglingmind.convotrack.ui.ride.planner.PlannerState
import com.danglingmind.convotrack.ui.ride.planner.RidePlannerScreen
import kotlinx.coroutines.launch

/**
 * New ride, planned on the map (iOS CreateRideView). This screen owns the plan's lifetime and the
 * create call; the planner owns everything on screen.
 */
@Composable
fun CreateRideScreen(
    onBack: () -> Unit,
    onCreated: (rideId: String) -> Unit,
    onOpenMembership: () -> Unit = {},
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val planner = remember { PlannerState(scope, context.applicationContext) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** The last create was refused for a plan reason (trial over / monthly rides used up). */
    var blockedByPlan by remember { mutableStateOf(false) }
    val riderCap = rememberRiderCap()
    // Never start a new ride above the plan's limit (the server would silently trim it).
    LaunchedEffect(riderCap.cap) { if (planner.maxRiders > riderCap.cap) planner.maxRiders = riderCap.cap }

    RidePlannerScreen(
        state = planner,
        riderCap = riderCap,
        mode = PlannerMode.CREATE,
        onUpgrade = onOpenMembership,
        onClose = onBack,
    ) {
        PrimaryButton(
            text = "Create ride",
            icon = Icons.Filled.Directions,
            enabled = planner.canCommit(busy = creating),
            loading = creating,
            onClick = {
                creating = true
                scope.launch {
                    try {
                        val waypoints = planner.resolvedWaypoints() ?: return@launch
                        val dest = waypoints.last()
                        val req = CreateRideRequest(
                            title = planner.title.trim(),
                            destinationName = dest.name,
                            destinationLat = dest.lat,
                            destinationLng = dest.lng,
                            distanceMeters = planner.distanceMeters,
                            estimatedDurationSeconds = planner.durationSeconds.toInt(),
                            maxAllowedParticipants = planner.maxRiders,
                            routePolyline = DirectionsService.encodePolyline(planner.routeCoords),
                            waypoints = waypoints,
                        )
                        val resp = apiCall { api.createRide(req) }
                        MetaEvents.rideCreated()
                        onCreated(resp.rideId)
                    } catch (e: Exception) {
                        Analytics.capture(Analytics.Event.CreateRideFailed, mapOf("plan_limit" to e.isPlanLimit))
                        error = e.riderMessage
                        blockedByPlan = e.isPlanLimit
                    } finally {
                        creating = false
                    }
                }
            },
        )
    }

    error?.let { message ->
        ConfirmDialog(
            title = "Couldn't create ride",
            onDismiss = { error = null },
            confirmLabel = if (blockedByPlan) "Go Pro" else "OK",
            dismissLabel = if (blockedByPlan) "Not now" else null,
            onConfirm = {
                error = null
                if (blockedByPlan) Paywall.present(PaywallSource.CreateBlocked)
            },
        ) {
            DialogText(message)
        }
    }
}
