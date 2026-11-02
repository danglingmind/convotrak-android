package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.analytics.Analytics
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.net.SharedRoute
import com.danglingmind.convotrack.data.net.SharedStop
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.RideDuration
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.google.android.gms.maps.model.LatLng

/** A shared route the rider chose to ride, handed from the preview to Create Ride. */
object PendingRouteClone {
    class Clone(val token: String, val route: SharedRoute, val stops: List<SharedStop>)

    private var pending: Clone? = null
    fun set(clone: Clone) { pending = clone }
    /** Read once, by the Create Ride screen that is about to use it. */
    fun take(): Clone? = pending.also { pending = null }
}

fun regroupStopLabel(type: String): String = when (type) {
    "FUEL" -> "Fuel stop"
    "FOOD" -> "Food stop"
    "SCENIC" -> "Scenic stop"
    else -> "Stop"
}

/**
 * A shared ride route opened from a link: the road that was ridden, the stops along it and the
 * leader's regroup stops. "Ride this route" hands it to the planner as a new ride the rider leads
 * (iOS SharedRoutePreviewView).
 */
@Composable
fun SharedRoutePreviewScreen(token: String, onClose: () -> Unit, onRide: () -> Unit) {
    val api = remember { ServiceLocator.api }
    var route by remember { mutableStateOf<SharedRoute?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val chosen = remember { mutableStateListOf<String>() }

    LaunchedEffect(token, attempt) {
        failed = false
        route = try {
            apiCall { api.getSharedRoute(token) }.also { Analytics.capture(Analytics.Event.SharedRouteOpened) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            null
        }
    }

    Box(Modifier.fillMaxSize().background(DsColor.bgBase)) {
        val r = route
        when {
            failed -> ErrorState("This route isn't available. The link may have been turned off.", onRetry = { attempt++ })
            r == null -> LoadingState()
            else -> Content(r, token, chosen, onRide)
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(DsSpace.gutter)
                .size(44.dp).clip(CircleShape).background(DsColor.glassOnPhoto).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Close", tint = DsColor.textPrimary) }
    }
}

@Composable
private fun Content(route: SharedRoute, token: String, chosen: MutableList<String>, onRide: () -> Unit) {
    val ridden = remember(route) { DirectionsService.decodedRoute(route.actualRoutePolyline).orEmpty() }
    val planned = remember(route) { DirectionsService.decodedRoute(route.plannedRoutePolyline).orEmpty() }
    val markers = remember(route) {
        buildList {
            route.waypoints.forEach { wp ->
                add(MapMarker("wp_${wp.order}", LatLng(wp.lat, wp.lng),
                    if (wp.type == "DESTINATION") MapMarkerKind.DESTINATION else MapMarkerKind.WAYPOINT, wp.name))
            }
            route.leaderStops.forEach {
                add(MapMarker("stop_${it.id}", LatLng(it.lat, it.lng), MapMarkerKind.REGROUP, regroupType = it.type))
            }
        }
    }
    val camera = remember(route) {
        MapCamera.Fit(
            id = 1L,
            coords = ridden.ifEmpty { planned } + route.leaderStops.map { LatLng(it.lat, it.lng) },
            topDp = 72f, leftDp = 40f, bottomDp = 40f, rightDp = 40f, animated = false,
        )
    }

    Column(Modifier.fillMaxSize()) {
        ConvoTrackMap(
            modifier = Modifier.fillMaxWidth().weight(1f),
            routeCoords = ridden.ifEmpty { planned },
            // The plan stays visible, dimmed, so a detour reads as a detour.
            fullRouteCoords = if (ridden.isEmpty()) emptyList() else planned,
            markers = markers,
            camera = camera,
        )
        Column(
            Modifier.fillMaxWidth().background(DsColor.bgBase).navigationBarsPadding()
                .padding(horizontal = DsSpace.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            route.sharedBy?.let { Eyebrow("Shared by ${it.firstName}", color = DsColor.accent) }
            Text(route.title, style = DsType.heroTitle, color = DsColor.textPrimary, maxLines = 2)
            Text(stats(route), style = DsType.body, color = DsColor.textSecondary)

            if (route.leaderStops.isNotEmpty()) {
                SectionHeading("Add the leader's stops")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    route.leaderStops.forEach { stop ->
                        val on = stop.id in chosen
                        Row(
                            Modifier.clip(DsShape.pill)
                                .background(if (on) DsColor.accent else DsColor.raised)
                                .clickable { if (on) chosen.remove(stop.id) else chosen.add(stop.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (on) Icon(Icons.Filled.Check, null, tint = DsColor.onAccent)
                            Text(regroupStopLabel(stop.type), style = DsType.body,
                                color = if (on) DsColor.onAccent else DsColor.textPrimary)
                        }
                    }
                }
            }

            PrimaryButton(
                text = "Ride this route",
                icon = Icons.Filled.Directions,
                onClick = {
                    Analytics.capture(Analytics.Event.SharedRouteCloneStarted, mapOf("added_stops" to chosen.size))
                    PendingRouteClone.set(PendingRouteClone.Clone(token, route, route.leaderStops.filter { it.id in chosen }))
                    onRide()
                },
            )
        }
    }
}

private fun stats(route: SharedRoute): String {
    val parts = mutableListOf("%.0f km".format(route.distanceMeters / 1000))
    route.durationSeconds?.let { parts += RideDuration.inline(it) }
    if (route.leaderStops.isNotEmpty()) parts += "${route.leaderStops.size} regroup stops"
    return parts.joinToString(" · ")
}
