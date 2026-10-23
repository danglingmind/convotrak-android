package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.imagecache.ImageCache
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.UpdateRideRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.rememberRiderCap
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.PushedHeader
import com.danglingmind.convotrack.ui.ride.planner.PlannerMode
import com.danglingmind.convotrack.ui.ride.planner.PlannerState
import com.danglingmind.convotrack.ui.ride.planner.RidePlannerScreen
import kotlinx.coroutines.launch

/**
 * Leader-only ride editor — the Android port of iOS EditRideView: a saved ride re-planned on the
 * map (the planner in edit mode). Saves via PATCH /rides/:rideId; delete lives in the ⋯ menu.
 */
@Composable
fun EditRideScreen(
    rideId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /** Called after the ride is successfully deleted, so the caller can tear down the realtime
     *  session and leave the ride flow instead of popping back to a lobby for a dead ride. */
    onDeleted: () -> Unit = {},
    onOpenMembership: () -> Unit = {},
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var ride by remember { mutableStateOf<Ride?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun load() {
        loadFailed = false
        ride = runCatching { apiCall { api.getRide(rideId) } }.getOrNull()
        loadFailed = ride == null
    }
    LaunchedEffect(rideId) { load() }

    val r = ride
    if (r != null) {
        EditRidePlanner(r, onBack, onSaved, onDeleted, onOpenMembership)
    } else {
        DsScreen {
            Column(Modifier.fillMaxSize()) {
                PushedHeader(title = "Edit ride", subtitle = "Plan your route", onBack = onBack)
                Box(Modifier.padding(DsSpace.gutter)) {
                    if (loadFailed) ErrorState("Couldn't load ride", onRetry = { scope.launch { load() } })
                    else LoadingState(Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun EditRidePlanner(ride: Ride, onBack: () -> Unit, onSaved: () -> Unit, onDeleted: () -> Unit, onOpenMembership: () -> Unit) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val planner = remember(ride.id) { PlannerState(scope, context.applicationContext, ride) }

    // Only the ride's creator may delete it (the server enforces the same rule). Read from the
    // live auth identity so a still-loading session resolves the moment it lands.
    val amLeader = ServiceLocator.auth.userState.value?.id
        ?.takeIf { it.isNotEmpty() } == ride.leaderId

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Show the saved route at once, then load the alternatives and keep the leader's pick selected.
    LaunchedEffect(ride.id) {
        planner.fitCameraToRoute(animated = false)
        planner.recalculateRoute(preferStoredMatch = true)
    }

    RidePlannerScreen(
        state = planner,
        riderCap = rememberRiderCap(),
        mode = PlannerMode.EDIT,
        onUpgrade = onOpenMembership,
        onClose = { if (planner.hasChanges) showDiscard = true else onBack() },
        onDelete = if (amLeader) ({ showDeleteConfirm = true }) else null,
    ) {
        PrimaryButton(
            text = "Save changes",
            icon = Icons.Filled.Check,
            enabled = planner.hasChanges && planner.canCommit(busy = saving || deleting),
            loading = saving,
            onClick = {
                saving = true
                scope.launch {
                    try {
                        val waypoints = planner.resolvedWaypoints() ?: return@launch
                        val dest = waypoints.last()
                        val req = UpdateRideRequest(
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
                        apiCall { api.updateRide(ride.id, req) }
                        // New route or destination: its cached map and summary card are stale.
                        runCatching { ImageCache.evictOwner(ride.id) }
                        onSaved()
                    } catch (e: Exception) {
                        saveError = e.riderMessage
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard your changes?",
            onDismiss = { showDiscard = false },
            dismissLabel = "Keep editing",
            confirmLabel = "Discard changes",
            destructive = true,
            onConfirm = {
                showDiscard = false
                onBack()
            },
        ) {
            DialogText("The ride stays as it was saved.")
        }
    }

    saveError?.let { message ->
        ConfirmDialog(title = "Couldn't save ride", onDismiss = { saveError = null }, confirmLabel = "OK", dismissLabel = null, onConfirm = { saveError = null }) {
            DialogText(message)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this ride?",
            onDismiss = { if (!deleting) showDeleteConfirm = false },
            confirmLabel = if (deleting) "Deleting…" else "Delete ride",
            destructive = true,
            busy = deleting,
            error = deleteError,
            onConfirm = {
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        apiCall { api.deleteRide(ride.id) }
                        // Its thumbnail, map and summary card can never be shown again.
                        runCatching { ImageCache.evictOwner(ride.id) }
                        showDeleteConfirm = false
                        // The server broadcasts ride:deleted; every other rider's lobby surfaces
                        // "Ride Cancelled" off the back of it.
                        onDeleted()
                    } catch (e: Exception) {
                        deleteError = e.riderMessage
                    } finally {
                        deleting = false
                    }
                }
            },
        ) {
            DialogText("This permanently deletes the ride for everyone. This can't be undone.")
        }
    }
}
