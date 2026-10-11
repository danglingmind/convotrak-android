package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.UpdateRideRequest
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.rememberRiderCap
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.DestructiveButton
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.PushedHeader
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/**
 * Leader-only ride editor — the Android port of iOS EditRideView. Shares the ride form with Create
 * Ride, pre-filled from the ride's current waypoints, and saves via PATCH /rides/:rideId.
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
        EditRideForm(r, onBack, onSaved, onDeleted, onOpenMembership)
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

private fun waypointPlace(wp: Waypoint): PlaceResult =
    PlaceResult(id = "wp_${wp.order}", name = wp.name, latLng = LatLng(wp.lat, wp.lng), address = null)

@Composable
private fun EditRideForm(ride: Ride, onBack: () -> Unit, onSaved: () -> Unit, onDeleted: () -> Unit, onOpenMembership: () -> Unit) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    // Only the ride's creator may delete it (the server enforces the same rule). Read from the
    // live auth identity so a still-loading session resolves the moment it lands.
    val amLeader = ServiceLocator.auth.userState.value?.id
        ?.takeIf { it.isNotEmpty() } == ride.leaderId

    val form = remember(ride.id) {
        val sorted = ride.waypoints.sortedBy { it.order }
        fun stopFor(id: Long, wp: Waypoint?) = RouteStop(id).also { s ->
            wp?.let { s.text = it.name; s.place = waypointPlace(it) }
        }
        RouteFormState(
            initialTitle = ride.title,
            initialMaxRiders = ride.maxAllowedParticipants,
            startStop = stopFor(-1, sorted.firstOrNull { it.type == "START" }),
            destStop = stopFor(-2, sorted.firstOrNull { it.type == "DESTINATION" }),
            initialMiddle = sorted.filter { it.type == "WAYPOINT" }.mapIndexed { i, wp -> stopFor(1000L + i, wp) },
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSave = form.title.isNotBlank() && form.allResolved && form.preview != null && !saving

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    RideFormScaffold(title = "Edit ride", onBack = onBack) {
        RouteFormSections(form, rememberRiderCap(), onUpgrade = onOpenMembership)

        if (error != null) InlineError(error!!)

        PrimaryButton(
            text = "Save ride",
            icon = Icons.Filled.Directions,
            enabled = canSave,
            loading = saving,
            onClick = {
                val p = form.preview ?: return@PrimaryButton
                saving = true
                error = null
                scope.launch {
                    try {
                        val dest = form.destStop.place!!
                        val req = UpdateRideRequest(
                            title = form.title.trim(),
                            destinationName = dest.name,
                            destinationLat = dest.latLng.latitude,
                            destinationLng = dest.latLng.longitude,
                            distanceMeters = p.distanceMeters,
                            estimatedDurationSeconds = p.durationSeconds.toInt(),
                            maxAllowedParticipants = form.maxRiders,
                            routePolyline = DirectionsService.encodePolyline(p.coords),
                            waypoints = form.waypoints(),
                        )
                        apiCall { api.updateRide(ride.id, req) }
                        onSaved()
                    } catch (e: Exception) {
                        error = e.riderMessage
                        saving = false
                    }
                }
            },
        )

        // Delete is the leader's only way to cancel a ride outright: destructive, at the end of the
        // form and behind a confirmation, because unlike leaving it removes the ride for everyone.
        if (amLeader) {
            if (deleteError != null) InlineError(deleteError!!)
            DestructiveButton(
                text = if (deleting) "Deleting…" else "Delete ride",
                enabled = !deleting && !saving,
                onClick = { showDeleteConfirm = true },
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this ride?",
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = "Delete ride",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        apiCall { api.deleteRide(ride.id) }
                        // The server broadcasts ride:deleted; every other rider's lobby surfaces
                        // "Ride Cancelled" off the back of it.
                        onDeleted()
                    } catch (e: Exception) {
                        deleteError = e.riderMessage
                        deleting = false
                    }
                }
            },
        ) {
            DialogText("This permanently deletes the ride for everyone. This can't be undone.")
        }
    }
}

