package com.danglingmind.convotrack.ui.ride

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.RouteOption
import com.danglingmind.convotrack.data.net.CreateWaypoint
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.UpdateRideRequest
import com.danglingmind.convotrack.data.net.Waypoint
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/**
 * Leader-only ride editor — the Android port of iOS EditRideView. Reuses the CreateRideScreen
 * route-form building blocks, pre-filled from the ride's current waypoints, and saves via
 * PATCH /rides/:rideId (UpdateRideRequest).
 */
@Composable
fun EditRideScreen(
    rideId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /** Called after the ride is successfully deleted, so the caller can tear down the realtime
     *  session and leave the ride flow instead of popping back to a lobby for a dead ride. */
    onDeleted: () -> Unit = {},
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

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        ConvoTrackTopBar(title = "Edit Ride", showBack = true, onBack = onBack)
        val r = ride
        when {
            r != null -> EditRideForm(r, onSaved, onDeleted)
            loadFailed -> Box(Modifier.padding(20.dp)) {
                ErrorBlock("Couldn't load ride", onRetry = { scope.launch { load() } })
            }
            else -> LoadingBlock(Modifier.padding(top = 40.dp))
        }
    }
}

private fun waypointPlace(wp: Waypoint): PlaceResult =
    PlaceResult(id = "wp_${wp.order}", name = wp.name, latLng = LatLng(wp.lat, wp.lng), address = null)

@Composable
private fun EditRideForm(ride: Ride, onSaved: () -> Unit, onDeleted: () -> Unit) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    val sorted = remember(ride.id) { ride.waypoints.sortedBy { it.order } }

    // Only the ride's creator may delete it (the server enforces the same rule). Read from the
    // live auth identity so a still-loading session resolves the moment it lands.
    val amLeader = ServiceLocator.auth.userState.value?.id
        ?.takeIf { it.isNotEmpty() } == ride.leaderId

    var title by remember(ride.id) { mutableStateOf(ride.title) }
    var maxRiders by remember(ride.id) { mutableIntStateOf(ride.maxAllowedParticipants) }
    val startStop = remember(ride.id) {
        RouteStop(-1).also { s -> sorted.firstOrNull { it.type == "START" }?.let { s.text = it.name; s.place = waypointPlace(it) } }
    }
    val destStop = remember(ride.id) {
        RouteStop(-2).also { s -> sorted.firstOrNull { it.type == "DESTINATION" }?.let { s.text = it.name; s.place = waypointPlace(it) } }
    }
    val middleStops = remember(ride.id) {
        mutableStateListOf<RouteStop>().also { list ->
            sorted.filter { it.type == "WAYPOINT" }.forEachIndexed { i, wp ->
                list.add(RouteStop(1000L + i).also { s -> s.text = wp.name; s.place = waypointPlace(wp) })
            }
        }
    }
    var nextId by remember(ride.id) { mutableStateOf(2000L) }

    var preview by remember(ride.id) { mutableStateOf<RoutePreview?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Alternative routes for a direct start→destination (no middle stops) — see CreateRideScreen.
    var routeAlternatives by remember(ride.id) { mutableStateOf<List<RouteOption>>(emptyList()) }
    var selectedRouteIndex by remember(ride.id) { mutableIntStateOf(0) }

    val orderedStops = listOf(startStop) + middleStops + listOf(destStop)
    val allResolved = startStop.place != null && destStop.place != null
    val canSave = title.isNotBlank() && allResolved && preview != null && !saving

    fun selectRoute(index: Int) {
        val option = routeAlternatives.getOrNull(index) ?: return
        selectedRouteIndex = index
        preview = RoutePreview(option.coordinates, option.distanceMeters, option.durationSeconds)
    }

    // Recompute the route whenever the set of picked places changes (also on first load).
    val routeKey = orderedStops.joinToString("|") { it.place?.id ?: "-" }
    LaunchedEffect(routeKey) {
        val places = orderedStops.mapNotNull { it.place }
        if (startStop.place != null && destStop.place != null && places.size >= 2) {
            if (places.size == 2) {
                val options = runCatching {
                    DirectionsService.alternativeRoutes(places[0].latLng, places[1].latLng)
                }.getOrNull().orEmpty()
                if (options.size > 1) {
                    routeAlternatives = options
                    selectedRouteIndex = 0
                    preview = RoutePreview(options[0].coordinates, options[0].distanceMeters, options[0].durationSeconds)
                    return@LaunchedEffect
                }
            }
            routeAlternatives = emptyList()
            selectedRouteIndex = 0
            preview = computePreview(places.map { it.latLng })
        } else {
            routeAlternatives = emptyList()
            selectedRouteIndex = 0
            preview = null
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("RIDE TITLE")
            PlainField(title, "e.g., Midnight Canyon Run") { title = it }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("ROUTE")

            StopRow(dotColor = CT.tertiaryFixed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("START LOCATION", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                    PlacesField(startStop, "Search start point...", Icons.Filled.MyLocation)
                }
            }

            middleStops.forEach { stop ->
                StopRow(dotColor = CT.primaryFixed.copy(alpha = 0.6f)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            PlacesField(stop, "Search stop...", Icons.Filled.Place)
                        }
                        Icon(
                            Icons.Filled.RemoveCircle,
                            contentDescription = "Remove stop",
                            tint = CT.errorColor,
                            modifier = Modifier.padding(top = 14.dp).size(22.dp).clickable { middleStops.remove(stop) },
                        )
                    }
                }
            }

            Row(
                Modifier.padding(start = 26.dp).clickable { middleStops.add(RouteStop(nextId++)) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.AddCircleOutline, null, tint = CT.primaryFixed, modifier = Modifier.size(16.dp))
                Text("ADD STOP", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)
            }

            StopRow(dotColor = CT.primaryFixed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DESTINATION", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                    PlacesField(destStop, "Search destination...", Icons.Filled.Flag)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("ROUTE PREVIEW")
            val coords = preview?.coords ?: emptyList()
            val markers = buildList {
                startStop.place?.let { add(MapMarker("start", it.latLng, MapMarkerKind.START, it.name)) }
                middleStops.mapNotNull { it.place }.forEachIndexed { i, p ->
                    add(MapMarker("wp$i", p.latLng, MapMarkerKind.WAYPOINT, p.name))
                }
                destStop.place?.let { add(MapMarker("dest", it.latLng, MapMarkerKind.DESTINATION, it.name)) }
            }
            if (coords.isNotEmpty() || markers.isNotEmpty()) {
                ConvoTrackMap(
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                    routeCoords = coords,
                    markers = markers,
                    interactive = true,
                    alternativeRoutes = routeAlternatives.map { it.coordinates },
                    selectedRouteIndex = selectedRouteIndex,
                    onSelectRoute = { selectRoute(it) },
                )
                RouteSelectorChips(
                    options = routeAlternatives,
                    selectedIndex = selectedRouteIndex,
                    onSelect = { selectRoute(it) },
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                        .background(CT.surfaceContainerLow)
                        .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Pick a start and destination", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteStatCard(Icons.Filled.Timer, "EST. TIME", preview?.let { durationText(it.durationSeconds) } ?: "--", Modifier.weight(1f))
                RouteStatCard(Icons.Filled.Place, "DISTANCE", preview?.let { "${formatKm(it.distanceMeters)} KM" } ?: "--", Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("MAX RIDERS")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerLow)
                    .border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$maxRiders riders", style = CTType.bodyLg, color = CT.onSurface)
                Spacer(Modifier.weight(1f))
                StepperButton("−") { if (maxRiders > 2) maxRiders-- }
                Spacer(Modifier.size(12.dp))
                StepperButton("+") { if (maxRiders < 25) maxRiders++ }
            }
        }

        if (error != null) {
            Text(error!!, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        LimePrimaryButton(
            text = if (saving) "SAVING…" else "SAVE CHANGES",
            enabled = canSave,
            onClick = {
                val p = preview ?: return@LimePrimaryButton
                saving = true
                error = null
                scope.launch {
                    try {
                        val stops = orderedStops.filter { it.place != null }
                        val waypoints = stops.mapIndexed { i, s ->
                            val place = s.place!!
                            CreateWaypoint(
                                order = i,
                                name = place.name,
                                type = when (i) {
                                    0 -> "START"
                                    stops.lastIndex -> "DESTINATION"
                                    else -> "WAYPOINT"
                                },
                                lat = place.latLng.latitude,
                                lng = place.latLng.longitude,
                            )
                        }
                        val dest = destStop.place!!
                        val req = UpdateRideRequest(
                            title = title.trim(),
                            destinationName = dest.name,
                            destinationLat = dest.latLng.latitude,
                            destinationLng = dest.latLng.longitude,
                            distanceMeters = p.distanceMeters,
                            estimatedDurationSeconds = p.durationSeconds.toInt(),
                            maxAllowedParticipants = maxRiders,
                            routePolyline = DirectionsService.encodePolyline(p.coords),
                            waypoints = waypoints,
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

        // Delete is the leader's only way to cancel a ride outright. Full-width and destructive,
        // pinned to the end of the form and behind a confirmation, because unlike LEAVE RIDE it
        // cannot be undone — it removes the ride for every rider.
        if (amLeader) {
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf(false) }
            var deleteError by remember { mutableStateOf<String?>(null) }

            if (deleteError != null) {
                Text(
                    deleteError!!,
                    style = CTType.bodyMd,
                    color = CT.errorColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(CT.errorColor.copy(alpha = 0.12f))
                    .border(1.dp, CT.errorColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !deleting && !saving) { showDeleteConfirm = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (deleting) "DELETING…" else "DELETE RIDE",
                    style = CTType.labelCaps,
                    color = CT.errorColor,
                    letterSpacing = 4.sp,
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    containerColor = CT.surfaceContainer,
                    title = { Text("Delete this ride?", style = CTType.headlineMd, color = CT.onSurface) },
                    text = {
                        Text(
                            "This permanently deletes the ride for everyone. This can't be undone.",
                            style = CTType.bodyMd,
                            color = CT.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            deleting = true
                            deleteError = null
                            scope.launch {
                                try {
                                    apiCall { api.deleteRide(ride.id) }
                                    // The server broadcasts ride:deleted; every other rider's
                                    // lobby surfaces "Ride Cancelled" off the back of it.
                                    onDeleted()
                                } catch (e: Exception) {
                                    deleteError = e.riderMessage
                                    deleting = false
                                }
                            }
                        }) { Text("Delete Ride", color = CT.errorColor) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", color = CT.onSurfaceVariant)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
