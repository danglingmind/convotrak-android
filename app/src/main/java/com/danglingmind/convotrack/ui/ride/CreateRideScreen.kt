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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.PlacesService
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.CreateRideRequest
import com.danglingmind.convotrack.data.net.CreateWaypoint
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
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
import java.util.Locale

// A route stop: free text plus the geocoded place once the user picks a suggestion.
private class RouteStop(val id: Long) {
    var text by mutableStateOf("")
    var place by mutableStateOf<PlaceResult?>(null)
}

private data class RoutePreview(
    val coords: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

@Composable
fun CreateRideScreen(
    onBack: () -> Unit,
    onCreated: (rideId: String) -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    val startStop = remember { RouteStop(-1) }
    val destStop = remember { RouteStop(-2) }
    val middleStops = remember { mutableStateListOf<RouteStop>() }
    var maxRiders by remember { mutableIntStateOf(5) }
    var nextId by remember { mutableStateOf(0L) }

    var preview by remember { mutableStateOf<RoutePreview?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Ordered, fully-resolved stops (START → WAYPOINT* → DESTINATION).
    val orderedStops = listOf(startStop) + middleStops + listOf(destStop)
    val allResolved = startStop.place != null && destStop.place != null
    val canCreate = title.isNotBlank() && allResolved && preview != null && !creating

    // Recompute the route whenever the set of picked places changes.
    val routeKey = orderedStops.joinToString("|") { it.place?.id ?: "-" }
    androidx.compose.runtime.LaunchedEffect(routeKey) {
        val places = orderedStops.mapNotNull { it.place }
        preview = if (startStop.place != null && destStop.place != null && places.size >= 2) {
            computePreview(places.map { it.latLng })
        } else {
            null
        }
    }

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        ConvoTrackTopBar(title = "New Ride", showBack = true, onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Title
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("RIDE TITLE")
                PlainField(title, "e.g., Midnight Canyon Run") { title = it }
            }

            // Route
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

            // Route preview + stats
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

            // Max riders
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
                text = if (creating) "CREATING…" else "CREATE RIDE",
                enabled = canCreate,
                onClick = {
                    val p = preview ?: return@LimePrimaryButton
                    creating = true
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
                            val req = CreateRideRequest(
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
                            val resp = apiCall { api.createRide(req) }
                            onCreated(resp.rideId)
                        } catch (e: ApiException) {
                            error = e.message ?: "Couldn't create the ride."
                            creating = false
                        } catch (e: Exception) {
                            error = "Couldn't create the ride."
                            creating = false
                        }
                    }
                },
            )
        }
    }
}

/** Sums per-leg Directions results into one route (coords + total distance + duration). */
private suspend fun computePreview(points: List<LatLng>): RoutePreview? {
    if (points.size < 2) return null
    val coords = mutableListOf<LatLng>()
    var distance = 0.0
    var duration = 0.0
    for (i in 0 until points.size - 1) {
        val leg = runCatching { DirectionsService.route(points[i], points[i + 1]) }.getOrNull() ?: return null
        coords += if (i == 0) leg.coordinates else leg.coordinates.drop(1)
        distance += leg.distanceMeters
        duration += leg.durationSeconds
    }
    return RoutePreview(coords, distance, duration)
}

private fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)
}

@Composable
private fun StopRow(dotColor: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 34.dp).size(14.dp).clip(CircleShape).background(dotColor).border(2.dp, CT.surfaceDim, CircleShape),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Text field with debounced Places search + a suggestion list. Writes the picked place back to [stop]. */
@Composable
private fun PlacesField(stop: RouteStop, placeholder: String, leading: ImageVector) {
    var suggestions by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(stop.text) {
        // Only search while the field is unresolved and has enough text; delay debounces.
        if (stop.place == null && stop.text.trim().length >= 3) {
            searching = true
            kotlinx.coroutines.delay(300)
            suggestions = PlacesService.search(stop.text.trim())
            searching = false
        } else {
            suggestions = emptyList()
            searching = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = stop.text,
            onValueChange = { stop.text = it; stop.place = null },
            placeholder = { Text(placeholder, color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(leading, null, tint = if (stop.place != null) CT.tertiaryFixed else CT.primaryFixed, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (searching) {
                { CircularProgressIndicator(color = CT.primaryFixed, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)) }
            } else null,
            singleLine = true,
            textStyle = CTType.bodyMd,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CT.primaryFixed,
                unfocusedBorderColor = CT.outline.copy(alpha = 0.3f),
                focusedTextColor = CT.onSurface,
                unfocusedTextColor = CT.onSurface,
                cursorColor = CT.primaryFixed,
                unfocusedContainerColor = CT.surfaceContainerHigh,
                focusedContainerColor = CT.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CT.surfaceContainerHighest)
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            ) {
                suggestions.take(4).forEach { place ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            stop.place = place
                            stop.text = place.name
                            suggestions = emptyList()
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(place.name, style = CTType.bodyMd, color = CT.onSurface)
                        if (!place.address.isNullOrBlank()) {
                            Text(place.address, style = CTType.captionMd, color = CT.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
        singleLine = true,
        textStyle = CTType.bodyLg,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CT.primaryFixed,
            unfocusedBorderColor = CT.outline.copy(alpha = 0.3f),
            focusedTextColor = CT.onSurface,
            unfocusedTextColor = CT.onSurface,
            cursorColor = CT.primaryFixed,
            unfocusedContainerColor = CT.surfaceContainerHigh,
            focusedContainerColor = CT.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RouteStatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = CT.primaryFixed, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
            Text(value, style = CTType.dataMono, color = CT.onSurface)
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(CT.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CTType.headlineMd.copy(fontWeight = FontWeight.Bold), color = CT.primaryFixed)
    }
}
