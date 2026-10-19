package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.ui.designsystem.gold
import com.danglingmind.convotrack.ui.designsystem.PromoBanner
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.PlacesService
import com.danglingmind.convotrack.data.maps.RouteOption
import com.danglingmind.convotrack.data.net.CreateWaypoint
import com.danglingmind.convotrack.ui.common.RiderCap
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FieldAction
import com.danglingmind.convotrack.ui.designsystem.FieldLabel
import com.danglingmind.convotrack.ui.designsystem.FieldSpinner
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.MetricCard
import com.danglingmind.convotrack.ui.designsystem.OutlinedPillAction
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.Stepper
import com.danglingmind.convotrack.ui.designsystem.TextFieldCard
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapCamera
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay

// The ride form shared by Create Ride and Edit Ride: title, route timeline, preview, metrics and
// max riders. The screens own the header, the submit button and (Edit only) delete.

// MARK: - Model

/** A route stop: free text plus the geocoded place once the user picks a suggestion. */
internal class RouteStop(val id: Long) {
    var text by mutableStateOf("")
    var place by mutableStateOf<PlaceResult?>(null)
}

internal data class RoutePreview(
    val coords: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

/** Sums per-leg Directions results into one route (coords + total distance + duration). */
internal suspend fun computePreview(points: List<LatLng>): RoutePreview? {
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

internal fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}

/** Everything the ride form edits. Created once per ride (or once for a new ride). */
@Stable
internal class RouteFormState(
    initialTitle: String = "",
    initialMaxRiders: Int = 5,
    val startStop: RouteStop = RouteStop(-1),
    val destStop: RouteStop = RouteStop(-2),
    initialMiddle: List<RouteStop> = emptyList(),
) {
    var title by mutableStateOf(initialTitle)
    var maxRiders by mutableIntStateOf(initialMaxRiders)
    val middleStops = mutableStateListOf<RouteStop>().also { it.addAll(initialMiddle) }
    private var nextId by mutableLongStateOf(2000L)

    var preview by mutableStateOf<RoutePreview?>(null)

    // Alternative routes — only populated for a direct start→destination (no middle stops), since
    // Google returns no alternatives once intermediate waypoints are present.
    var routeAlternatives by mutableStateOf<List<RouteOption>>(emptyList())
    var selectedRouteIndex by mutableIntStateOf(0)

    /** Ordered stops: START → WAYPOINT* → DESTINATION. */
    val orderedStops: List<RouteStop> get() = listOf(startStop) + middleStops + listOf(destStop)
    val allResolved: Boolean get() = startStop.place != null && destStop.place != null
    val routeKey: String get() = orderedStops.joinToString("|") { it.place?.id ?: "-" }

    fun addStop() { middleStops.add(RouteStop(nextId++)) }

    /** Applies the alternative at [index] as the active route (updates stats + submit geometry). */
    fun selectRoute(index: Int) {
        val option = routeAlternatives.getOrNull(index) ?: return
        selectedRouteIndex = index
        preview = RoutePreview(option.coordinates, option.distanceMeters, option.durationSeconds)
    }

    /** Recompute the route for the current set of picked places. */
    suspend fun recompute() {
        val places = orderedStops.mapNotNull { it.place }
        if (startStop.place != null && destStop.place != null && places.size >= 2) {
            // Direct start→destination: offer selectable alternatives.
            if (places.size == 2) {
                val options = runCatching {
                    DirectionsService.alternativeRoutes(places[0].latLng, places[1].latLng)
                }.getOrNull().orEmpty()
                if (options.size > 1) {
                    routeAlternatives = options
                    selectedRouteIndex = 0
                    preview = RoutePreview(options[0].coordinates, options[0].distanceMeters, options[0].durationSeconds)
                    return
                }
            }
            // Has middle stops, or one/zero alternatives: single stitched route.
            routeAlternatives = emptyList()
            selectedRouteIndex = 0
            preview = computePreview(places.map { it.latLng })
        } else {
            routeAlternatives = emptyList()
            selectedRouteIndex = 0
            preview = null
        }
    }

    /** The resolved stops as API waypoints, typed by position. */
    fun waypoints(): List<CreateWaypoint> {
        val stops = orderedStops.filter { it.place != null }
        return stops.mapIndexed { i, s ->
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
    }

    fun markers(): List<MapMarker> = buildList {
        startStop.place?.let { add(MapMarker("start", it.latLng, MapMarkerKind.START, it.name)) }
        middleStops.mapNotNull { it.place }.forEachIndexed { i, p ->
            add(MapMarker("wp$i", p.latLng, MapMarkerKind.WAYPOINT, p.name))
        }
        destStop.place?.let { add(MapMarker("dest", it.latLng, MapMarkerKind.DESTINATION, it.name)) }
    }
}

// MARK: - Form

/** Title, route, preview, metrics and max riders, in that order (`screenSpecs.editRide`). */
@Composable
internal fun RouteFormSections(
    state: RouteFormState,
    riderCap: RiderCap,
    /** Opens the paywall: a free rider tapping + at the cap, or the upgrade banner. */
    onUpgrade: () -> Unit,
) {
    // Recompute the route whenever the set of picked places changes (also on first load).
    LaunchedEffect(state.routeKey) { state.recompute() }

    Column(verticalArrangement = Arrangement.spacedBy(DsSpace.sectionGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Ride title")
            TextFieldCard(
                value = state.title,
                onValueChange = { state.title = it },
                placeholder = "e.g., Midnight Canyon Run",
                trailing = { FieldAction(Icons.Filled.Edit, null, null) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Route")
            RouteTimeline(state)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoutePreviewSection(state)
            RouteOptionChips(state.routeAlternatives, state.selectedRouteIndex, state::selectRoute)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val p = state.preview
                MetricCard(Icons.Filled.Schedule, "Est. time", p?.let { durationText(it.durationSeconds) } ?: "--", Modifier.weight(1f))
                MetricCard(Icons.Filled.Directions, "Distance", p?.let { "${formatKm(it.distanceMeters)} km" } ?: "--", Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Max riders")
            // Free riders stop at the free cap; + there opens the paywall instead (iOS parity).
            val atCap = state.maxRiders >= riderCap.cap
            Stepper(
                value = state.maxRiders,
                suffix = "riders",
                onDecrement = { if (state.maxRiders > 2) state.maxRiders-- },
                onIncrement = {
                    when {
                        state.maxRiders < riderCap.cap -> state.maxRiders++
                        !riderCap.isPremium -> onUpgrade()
                    }
                },
                canDecrement = state.maxRiders > 2,
                canIncrement = !atCap || !riderCap.isPremium,
                dimIncrement = atCap,
            )
            AnimatedVisibility(visible = atCap && !riderCap.isPremium) {
                UpgradeBanner(onUpgrade)
            }
            // The limit is always visible, not only once the rider walks into it (iOS parity).
            if (!(atCap && !riderCap.isPremium)) RiderPlanLine(riderCap, onUpgrade)
        }
    }
}

/** "🔒 Up to 5 riders on your plan · Bigger packs with Pro" for free riders; Pro sees its own cap. */
@Composable
private fun RiderPlanLine(riderCap: RiderCap, onUpgrade: () -> Unit) {
    if (riderCap.isPremium) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = DsColor.textMuted, modifier = Modifier.size(14.dp))
            Text("Pro · up to ${riderCap.cap} riders", style = DsType.helper, color = DsColor.textMuted)
        }
    } else {
        Row(
            Modifier.heightIn(min = 48.dp).dsClickable(onClick = onUpgrade),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Lock, null, tint = DsColor.textMuted, modifier = Modifier.size(13.dp))
            Text("Up to ${riderCap.cap} riders on your plan", style = DsType.helper, color = DsColor.textMuted)
            Text("Bigger packs with Pro", style = DsType.helper.gold())
        }
    }
}

/** "Pro unlocks bigger packs · UPGRADE →" under the stepper once a free rider hits the cap. */
@Composable
private fun UpgradeBanner(onUpgrade: () -> Unit) {
    PromoBanner(
        message = "Pro unlocks bigger packs",
        actionLabel = "Go Pro",
        onAction = onUpgrade,
        goProStyle = true,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// MARK: - Route timeline

private enum class Rail { None, Line, First, Middle, Last }

/**
 * Markers in a 24dp rail joined by a line, the cards beside them. Each stop is a row whose marker
 * centres on the card; labels, suggestions and the add button are rows the line just passes through.
 */
@Composable
private fun RouteTimeline(state: RouteFormState) {
    Column {
        TimelineRow(Rail.None) { FieldLabel("Start location", Modifier.padding(bottom = 6.dp)) }
        StopEditor(state.startStop, "Search start point...", Icons.Filled.Navigation, Rail.First, DsColor.mapStart, onRemove = null, rotateIcon = true)

        state.middleStops.forEach { stop ->
            TimelineRow(Rail.Line) { Spacer(Modifier.height(10.dp)) }
            StopEditor(stop, "Search stop...", Icons.Outlined.LocationOn, Rail.Middle, DsColor.mapStop, onRemove = { state.middleStops.remove(stop) })
        }

        TimelineRow(Rail.Line) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                AddStopButton(onClick = state::addStop)
            }
        }
        TimelineRow(Rail.Line) { FieldLabel("Destination", Modifier.padding(bottom = 6.dp)) }
        StopEditor(state.destStop, "Search destination...", Icons.Filled.SportsScore, Rail.Last, DsColor.mapEnd, onRemove = null)
    }
}

@Composable
private fun TimelineRow(rail: Rail, markerColor: Color = Color.Transparent, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        RailCell(rail, markerColor, Modifier.width(24.dp).fillMaxHeight())
        Spacer(Modifier.width(12.dp))
        content()
    }
}

@Composable
private fun RailCell(rail: Rail, markerColor: Color, modifier: Modifier) {
    val line = Color(0xFF313838)
    Box(
        modifier.drawBehind {
            val x = size.width / 2
            val stroke = 2.dp.toPx()
            val mid = size.height / 2
            when (rail) {
                Rail.None -> Unit
                Rail.Line -> drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                Rail.First -> drawLine(line, Offset(x, mid), Offset(x, size.height), stroke)
                Rail.Middle -> drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                Rail.Last -> drawLine(line, Offset(x, 0f), Offset(x, mid), stroke)
            }
            if (rail == Rail.First || rail == Rail.Middle || rail == Rail.Last) {
                drawCircle(DsColor.bgBase, radius = 10.dp.toPx(), center = Offset(x, mid))
                drawCircle(markerColor, radius = 7.dp.toPx(), center = Offset(x, mid))
            }
        },
    )
}

/**
 * One stop: a [LocationCard] once a place is picked, otherwise a search field with suggestions.
 * Intermediate stops carry a remove button outside the card.
 */
@Composable
private fun StopEditor(
    stop: RouteStop,
    placeholder: String,
    icon: ImageVector,
    rail: Rail,
    markerColor: Color,
    onRemove: (() -> Unit)?,
    rotateIcon: Boolean = false,
) {
    var suggestions by remember(stop.id) { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember(stop.id) { mutableStateOf(false) }

    LaunchedEffect(stop.text, stop.place) {
        // Only search while the field is unresolved and has enough text; delay debounces.
        if (stop.place == null && stop.text.trim().length >= 3) {
            searching = true
            delay(300)
            suggestions = PlacesService.search(stop.text.trim())
            searching = false
        } else {
            suggestions = emptyList()
            searching = false
        }
    }

    TimelineRow(rail, markerColor) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val place = stop.place
            Box(Modifier.weight(1f)) {
                if (place != null) {
                    LocationCard(
                        title = place.name,
                        subtitle = place.address,
                        icon = icon,
                        rotateIcon = rotateIcon,
                        onEdit = { stop.place = null },
                        onClear = { stop.place = null; stop.text = "" },
                    )
                } else {
                    TextFieldCard(
                        value = stop.text,
                        onValueChange = { stop.text = it; stop.place = null },
                        placeholder = placeholder,
                        leadingIcon = Icons.Filled.Search,
                        textStyle = DsType.body,
                        minHeight = 52.dp,
                        trailing = if (searching) ({ FieldSpinner() }) else null,
                    )
                }
            }
            if (onRemove != null) RemoveStopButton(onRemove)
        }
    }
    if (suggestions.isNotEmpty()) {
        TimelineRow(if (rail == Rail.Last) Rail.None else Rail.Line) {
            SuggestionList(
                suggestions = suggestions.take(4),
                onPick = { picked ->
                    stop.place = picked
                    stop.text = picked.name
                    suggestions = emptyList()
                },
                modifier = Modifier.padding(top = 6.dp).padding(end = if (onRemove != null) 36.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun LocationCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    rotateIcon: Boolean,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .glassSurface(DsShape.md, DsColor.card, DsColor.border)
            .dsClickable(pressedScale = 0.99f, onClickLabel = "Change", onClick = onEdit)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(DsColor.iconDisc), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = DsColor.accent, modifier = Modifier.size(22.dp).then(if (rotateIcon) Modifier.rotate(45f) else Modifier))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = DsType.locationTitle, color = DsColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = DsType.bodySmall, color = DsColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color(0x24FFFFFF)).dsClickable(onClickLabel = "Clear", onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Clear", tint = DsColor.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<PlaceResult>, onPick: (PlaceResult) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().glassSurface(DsShape.md, DsColor.raised, DsColor.border)) {
        suggestions.forEachIndexed { i, place ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DsColor.divider))
            Row(
                Modifier.fillMaxWidth().dsClickable(pressedScale = 0.99f) { onPick(place) }.padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.LocationOn, null, tint = DsColor.textMuted, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                Column {
                    // The name wraps rather than truncating — it is the whole point of the row.
                    // The address is the supporting detail, so it is capped at two lines.
                    Text(place.name, style = DsType.bodyStrong, color = DsColor.textPrimary)
                    if (!place.address.isNullOrBlank()) {
                        Text(place.address, style = DsType.helper, color = DsColor.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveStopButton(onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(DsColor.danger).dsClickable(onClickLabel = "Remove stop", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Remove, "Remove stop", tint = DsColor.dangerContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddStopButton(onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(DsShape.pill)
            .border(1.dp, DsColor.borderStrong, DsShape.pill)
            .background(Color(0x06FFFFFF))
            .dsClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.AddCircleOutline, null, tint = DsColor.accent, modifier = Modifier.size(18.dp))
        Text("ADD STOP", style = DsType.buttonWide.copy(fontSize = DsType.helper.fontSize), color = DsColor.accent)
    }
}

// MARK: - Preview

@Composable
private fun RoutePreviewSection(state: RouteFormState) {
    val uriHandler = LocalUriHandler.current
    val coords = state.preview?.coords.orEmpty()
    val markers = state.markers()
    var recenterId by remember { mutableLongStateOf(0L) }

    SectionHeading("Route preview", compact = true) {
        if (state.allResolved) {
            OutlinedPillAction("View on map", Icons.Filled.OpenInNew, onClick = {
                runCatching { uriHandler.openUri(googleMapsDirectionsUrl(state)) }
            })
        }
    }
    if (coords.isNotEmpty() || markers.isNotEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(DsShape.md)
                .border(1.dp, DsColor.border, DsShape.md),
        ) {
            ConvoTrackMap(
                modifier = Modifier.matchParentSize(),
                routeCoords = coords,
                markers = markers,
                interactive = true,
                alternativeRoutes = state.routeAlternatives.map { it.coordinates },
                selectedRouteIndex = state.selectedRouteIndex,
                onSelectRoute = state::selectRoute,
                camera = if (recenterId == 0L) MapCamera.AutoFit
                else MapCamera.Fit(recenterId, coords.ifEmpty { markers.map { it.position } }, 40f, 40f, 40f, 40f),
            )
            FloatingButton(
                Icons.Filled.MyLocation,
                "Recenter",
                onClick = { recenterId += 1 },
                size = 40.dp,
                iconSize = 20.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(DsShape.md)
                .background(DsColor.card)
                .drawBehind {
                    drawRoundRect(
                        DsColor.border,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))),
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            IconDisc(Icons.Filled.Map, size = 44.dp, iconSize = 22.dp, tint = DsColor.textMuted, background = DsColor.iconDisc)
            Text("Pick a start and destination", style = DsType.bodySmall, color = DsColor.textMuted)
        }
    }
}


/** Opens the planned route in Google Maps (start, stops, destination). */
private fun googleMapsDirectionsUrl(state: RouteFormState): String {
    fun ll(p: PlaceResult) = "${p.latLng.latitude},${p.latLng.longitude}"
    val start = state.startStop.place!!
    val dest = state.destStop.place!!
    val stops = state.middleStops.mapNotNull { it.place }.joinToString("|") { ll(it) }
    return buildString {
        append("https://www.google.com/maps/dir/?api=1&travelmode=driving")
        append("&origin=").append(Uri.encode(ll(start)))
        append("&destination=").append(Uri.encode(ll(dest)))
        if (stops.isNotEmpty()) append("&waypoints=").append(Uri.encode(stops))
    }
}

/**
 * Horizontally-scrollable route options — shown only for a direct start→destination with more than
 * one alternative. Tapping a chip (or the dim map line) selects it (iOS routeSelectorChips).
 */
@Composable
private fun RouteOptionChips(options: List<RouteOption>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    if (options.size <= 1) return
    val fastest = options.minOf { it.durationSeconds }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val delta = ((option.durationSeconds - fastest) / 60).toInt()
            Column(
                Modifier
                    .clip(DsShape.sm)
                    .background(if (selected) DsColor.accentTint else DsColor.card)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) DsColor.accentBorder else DsColor.border, DsShape.sm)
                    .dsClickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    if (delta == 0) "FASTEST" else "+$delta MIN",
                    style = DsType.badgeSmall,
                    color = if (selected) DsColor.accent else DsColor.success,
                )
                Text(durationText(option.durationSeconds), style = DsType.statValue, color = DsColor.textPrimary)
                Text("${formatKm(option.distanceMeters)} KM", style = DsType.statLabel, color = if (selected) DsColor.textSecondary else DsColor.textMuted)
            }
        }
    }
}
