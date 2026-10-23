package com.danglingmind.convotrack.ui.ride.planner

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.location.CurrentLocation
import com.danglingmind.convotrack.data.maps.PlaceCategory
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.PlacesService
import com.danglingmind.convotrack.data.maps.RecentDestinations
import com.danglingmind.convotrack.data.maps.RouteOption
import com.danglingmind.convotrack.data.maps.reverseGeocode
import com.danglingmind.convotrack.ui.common.LocationDisclosureDialog
import com.danglingmind.convotrack.ui.common.RideDuration
import com.danglingmind.convotrack.ui.common.RiderCap
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FloatingButton
import com.danglingmind.convotrack.ui.designsystem.FloatingTone
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapDot
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.danglingmind.convotrack.ui.map.RouteBubble
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlannerMode {
    /** A new ride: Back from the route returns to the empty map, as in Google Maps. */
    CREATE,

    /** A saved ride: there's no empty map to go back to, so Back leaves the screen. */
    EDIT,
}

/**
 * The map-first ride planner (iOS RidePlannerView): a full-bleed map, a search pill that becomes the
 * stops card once a destination is picked, and a resizable sheet with the route summary and the
 * ride's details. The screen hosting it supplies the primary action (Create ride / Save changes)
 * and does the saving; [onClose] is Back from the screen, which Edit uses to ask about unsaved
 * changes.
 */
@Composable
internal fun RidePlannerScreen(
    state: PlannerState,
    riderCap: RiderCap,
    mode: PlannerMode,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
    /** Adds a ⋯ menu to the route summary with a destructive "Delete ride". Null hides it. */
    onDelete: (() -> Unit)? = null,
    primaryAction: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var detent by remember { mutableStateOf(SheetDetent.LOW) }
    /** The stop whose search is open. */
    var searchStopId by remember { mutableStateOf<Long?>(null) }
    var stopsExpanded by remember { mutableStateOf(false) }
    var searchCategory by remember { mutableStateOf<PlaceCategory?>(null) }
    var mapTarget by remember { mutableStateOf<LatLng?>(null) }
    /** A pin dropped with a long-press, waiting for the rider to say what it is. */
    var droppedPin by remember { mutableStateOf<PlaceCandidate?>(null) }
    /** The stop being placed by moving the map under a fixed pin ("Choose on map"). */
    var choosingStopId by remember { mutableStateOf<Long?>(null) }
    var mapCenter by remember { mutableStateOf<PlaceCandidate?>(null) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }
    var topChromeBottomPx by remember { mutableFloatStateOf(0f) }
    var sheetRestingHeight by remember { mutableStateOf(0.dp) }
    /** The map's bottom padding: the resting sheet's height, except that it holds still while a
     *  dropped pin's card is up, so the pin doesn't slide away from the finger that dropped it. */
    var mapBottomPadding by remember { mutableStateOf(0.dp) }
    var recents by remember { mutableStateOf(RecentDestinations.load(context)) }
    var showLocationDisclosure by remember { mutableStateOf(false) }
    /** What to do once location is granted: the row that asked for it. */
    var afterLocationGranted by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            state.locateUser()
            afterLocationGranted?.invoke()
        }
        afterLocationGranted = null
    }

    fun askForLocation(then: () -> Unit) {
        if (CurrentLocation.isGranted(context)) {
            state.locateUser()
            then()
        } else {
            afterLocationGranted = then
            showLocationDisclosure = true
        }
    }

    LaunchedEffect(Unit) { state.locateUser() }

    fun openSearch(id: Long) {
        focusManager.clearFocus()
        geocodeJob?.cancel()
        droppedPin = null
        mapBottomPadding = sheetRestingHeight
        searchStopId = id
    }

    fun closeSearch() {
        searchStopId = null
        searchCategory = null
    }

    fun pick(place: PlaceResult, id: Long) {
        state.choose(place, id)
        recents = RecentDestinations.load(context)
        detent = SheetDetent.LOW
        closeSearch()
    }

    /** Street name for a spot, a moment after the map settles; a newer lookup cancels the last. */
    fun lookUp(at: LatLng, delayMs: Long, apply: (PlaceResult?) -> Unit) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            apply(PlacesService.reverseGeocode(at))
        }
    }

    fun clearPin() {
        geocodeJob?.cancel()
        droppedPin = null
        mapBottomPadding = sheetRestingHeight
    }

    fun dropPin(at: LatLng) {
        if (choosingStopId != null) return
        view.dsHaptic(DsHaptic.Primary)
        focusManager.clearFocus()
        droppedPin = PlaceCandidate(at)
        detent = SheetDetent.LOW
        lookUp(at, 0) { place ->
            if (droppedPin?.position == at) droppedPin = droppedPin?.copy(place = place, resolving = false)
        }
    }

    fun usePin(apply: (PlaceResult) -> Unit) {
        val pin = droppedPin ?: return
        apply(pin.resolvedPlace)
        recents = RecentDestinations.load(context)
        clearPin()
    }

    fun startChoosing(id: Long) {
        closeSearch()
        clearPin()
        mapCenter = null
        choosingStopId = id
        detent = SheetDetent.LOW
        // Start from the stop's own spot, else the rider; moving the camera also reports where it
        // came to rest, which names the first spot.
        val stop = state.stops.firstOrNull { it.id == id }
        (stop?.let(state::coordinate) ?: state.userLocation)?.let { state.focus(it, 15f) }
    }

    fun finishChoosing() {
        val id = choosingStopId ?: return
        val center = mapCenter ?: return
        state.choose(center.resolvedPlace, id)
        recents = RecentDestinations.load(context)
        geocodeJob?.cancel()
        choosingStopId = null
        mapCenter = null
    }

    fun cancelChoosing() {
        val id = choosingStopId ?: return
        geocodeJob?.cancel()
        state.discardIfEmpty(id)
        choosingStopId = null
        mapCenter = null
        if (state.hasRoute) state.fitCameraToRoute()
    }

    fun mapCameToRest(target: LatLng) {
        mapTarget = target
        if (choosingStopId == null) return
        mapCenter = PlaceCandidate(target)
        lookUp(target, 250) { place ->
            if (mapCenter?.position == target) mapCenter = mapCenter?.copy(place = place, resolving = false)
        }
    }

    // Back walks out one layer at a time, the way the on-screen controls do.
    BackHandler {
        when {
            searchStopId != null -> {
                state.discardIfEmpty(searchStopId!!)
                closeSearch()
            }
            choosingStopId != null -> cancelChoosing()
            droppedPin != null -> clearPin()
            detent != SheetDetent.LOW -> detent = SheetDetent.LOW
            mode == PlannerMode.CREATE && state.hasDestination -> state.clearDestination()
            else -> onClose()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(DsColor.bgBase)) {
        val screenHeightPx = constraints.maxHeight.toFloat()

        ConvoTrackMap(
            modifier = Modifier.fillMaxSize(),
            routeCoords = state.routeCoords,
            markers = plannerMarkers(state),
            camera = state.camera,
            interactive = true,
            alternativeRoutes = state.routes.map { it.coordinates },
            selectedRouteIndex = state.selectedRouteIndex,
            onSelectRoute = { index ->
                view.dsHaptic(DsHaptic.Selection)
                state.selectRoute(index)
            },
            basePadding = PaddingValues(
                top = with(density) { topChromeBottomPx.toDp() } + 8.dp,
                bottom = mapBottomPadding,
            ),
            routeBubbles = routeBubbles(state),
            dots = buildList {
                if (!state.hasDestination) state.userLocation?.let { add(MapDot("me", it, DsColor.mapStart, 16f)) }
                droppedPin?.let { add(MapDot("dropped", it.position, DsColor.accent, 20f)) }
            },
            onMapClick = { if (droppedPin != null) clearPin() },
            onMapLongClick = { dropPin(it) },
            onCameraIdle = { mapCameToRest(it) },
        )

        if (searchStopId == null) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp)
                    .onGloballyPositioned { topChromeBottomPx = it.boundsInRoot().bottom },
            ) {
                val choosing = choosingStopId
                when {
                    choosing != null -> ChoosingPill(chooseRole(state, choosing), onCancel = ::cancelChoosing)
                    state.hasDestination -> PlannerStopsCard(
                        state = state,
                        onBack = { if (mode == PlannerMode.EDIT) onClose() else state.clearDestination() },
                        backLabel = if (mode == PlannerMode.EDIT) "Back" else "Change destination",
                        onEdit = ::openSearch,
                        onAddStop = { state.addStop()?.let(::openSearch) },
                        expanded = stopsExpanded,
                        onExpandedChange = { stopsExpanded = it },
                    )
                    else -> {
                        SearchPill(onBack = onClose, onSearch = { openSearch(state.destinationId) })
                        CategoryChips(
                            selected = null,
                            onSelect = { category ->
                                searchCategory = category
                                openSearch(state.destinationId)
                            },
                            onMap = true,
                        )
                    }
                }
            }

            PlannerSheet(
                detent = detent,
                onDetentChange = { detent = it },
                onRestingHeightChange = { height ->
                    sheetRestingHeight = height
                    if (droppedPin == null) mapBottomPadding = height
                },
                accessory = {
                    FloatingButton(
                        icon = if (state.hasRoute) Icons.Filled.ZoomOutMap else Icons.Filled.MyLocation,
                        contentDescription = if (state.hasRoute) "Show whole route" else "Show my location",
                        onClick = { if (state.hasRoute) state.fitCameraToRoute() else askForLocation { state.focusOnUser() } },
                        tone = FloatingTone.AccentIcon,
                    )
                },
                header = {
                    Column(Modifier.padding(horizontal = DsSpace.gutter).padding(bottom = 12.dp)) {
                        val choosing = choosingStopId
                        when {
                            choosing != null -> ChooseCard(chooseRole(state, choosing), mapCenter, ::finishChoosing, ::cancelChoosing)
                            droppedPin != null -> PlannerPlaceCard(
                                eyebrow = "Dropped pin",
                                candidate = droppedPin,
                                actions = droppedPinActions(state, ::usePin),
                                onClose = ::clearPin,
                            )
                            !state.hasDestination -> ExploreHeader(recents.take(2), state.userLocation) { pick(it, state.destinationId) }
                            else -> RouteHeader(
                                state = state,
                                onDelete = onDelete,
                                onOpenRiders = {
                                    focusManager.clearFocus()
                                    detent = SheetDetent.MEDIUM
                                },
                                onChooseLocation = {
                                    state.stops.firstOrNull { it.isCurrentLocation }?.let { stop ->
                                        if (CurrentLocation.isGranted(context)) openSearch(stop.id)
                                        else askForLocation { }
                                    }
                                },
                                locationGranted = CurrentLocation.isGranted(context),
                                primaryAction = primaryAction,
                            )
                        }
                    }
                },
                content = {
                    Column(Modifier.padding(horizontal = DsSpace.gutter).padding(bottom = 24.dp)) {
                        when {
                            choosingStopId != null || droppedPin != null -> Unit
                            !state.hasDestination -> RecentRows(recents.drop(2), state.userLocation) { pick(it, state.destinationId) }
                            else -> DetailsContent(state, riderCap, onUpgrade)
                        }
                    }
                },
            )
        }

        if (choosingStopId != null) {
            val top = topChromeBottomPx + with(density) { 8.dp.toPx() }
            val bottom = screenHeightPx - with(density) { mapBottomPadding.toPx() }
            Crosshair(centerY = with(density) { ((top + bottom) / 2).toDp() })
        }

        val searchId = searchStopId
        val searchStop = state.stops.firstOrNull { it.id == searchId }
        if (searchId != null && searchStop != null) {
            val role = state.role(searchStop)
            PlannerSearch(
                role = role,
                offersCurrentLocation = state.offersCurrentLocation(searchId),
                recents = recents,
                onPick = { pick(it, searchId) },
                onUseCurrentLocation = {
                    askForLocation {
                        state.useCurrentLocation(searchId)
                        closeSearch()
                    }
                },
                onChooseOnMap = { startChoosing(searchId) },
                onCancel = {
                    state.discardIfEmpty(searchId)
                    closeSearch()
                },
                searchCenter = mapTarget ?: state.userLocation,
                route = if (role == StopRole.STOP && state.hasRoute) state.routeCoords else emptyList(),
                // A detour is only a clean figure against a direct route: with other stops between,
                // the hit's legs skip them.
                measureDetours = state.chosenStops.size == 2,
                avoid = state.avoid,
                initialCategory = searchCategory,
                userLocation = state.userLocation,
            )
        }
    }

    if (showLocationDisclosure) {
        LocationDisclosureDialog(
            onAccept = {
                showLocationDisclosure = false
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onDecline = {
                showLocationDisclosure = false
                afterLocationGranted = null
            },
        )
    }
}

// MARK: - Map content

private fun plannerMarkers(state: PlannerState): List<MapMarker> {
    if (!state.hasDestination) return emptyList()
    val chosen = state.chosenStops
    return chosen.mapIndexedNotNull { i, stop ->
        val at = state.coordinate(stop) ?: return@mapIndexedNotNull null
        val kind = when (i) {
            0 -> MapMarkerKind.START
            chosen.lastIndex -> MapMarkerKind.DESTINATION
            else -> MapMarkerKind.WAYPOINT
        }
        MapMarker("stop_${stop.id}", at, kind, if (i == 0) "START" else state.name(stop))
    }
}

/** The selected route's bubble reads its duration; the others read the difference from it, which
 *  is the number a rider is actually weighing. */
private fun routeBubbles(state: PlannerState): List<RouteBubble> {
    if (state.routes.size <= 1 || state.bubbleAnchors.size != state.routes.size) return emptyList()
    val selected = state.durationSeconds
    return state.routes.indices.map { i ->
        val isSelected = i == state.selectedRouteIndex
        val text = if (isSelected) RideDuration.inline(state.routes[i].durationSeconds.toInt())
        else {
            val delta = ((state.routes[i].durationSeconds - selected) / 60).roundToInt()
            when {
                delta == 0 -> "Same time"
                delta > 0 -> "+$delta min"
                else -> "−${-delta} min"
            }
        }
        RouteBubble(i, state.bubbleAnchors[i], text, isSelected)
    }
}

// MARK: - Top chrome

@Composable
private fun SearchPill(onBack: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(DsColor.nav, DsShape.pill)
            .border(1.dp, DsColor.border, DsShape.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 52.dp).dsClickable(onClickLabel = "Back", onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .dsClickable(pressedScale = 0.99f, onClickLabel = "Search for a destination", onClick = onSearch)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = DsColor.textMuted, modifier = Modifier.size(20.dp))
            Text("Where are you riding?", style = DsType.locationTitle, color = DsColor.textMuted)
        }
    }
}

private fun chooseRole(state: PlannerState, id: Long): StopRole =
    state.stops.firstOrNull { it.id == id }?.let(state::role) ?: StopRole.STOP

@Composable
private fun ChoosingPill(role: StopRole, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(DsColor.nav, DsShape.pill)
            .border(1.dp, DsColor.border, DsShape.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 52.dp).dsClickable(onClickLabel = "Cancel", onClick = onCancel)
                .semantics { contentDescription = "Cancel" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
        }
        Text(
            when (role) {
                StopRole.START -> "Move the map to the start"
                StopRole.STOP -> "Move the map to the stop"
                StopRole.DESTINATION -> "Move the map to the destination"
            },
            style = DsType.locationTitle,
            color = DsColor.textPrimary,
        )
    }
}

/** The fixed pin for "Choose on map", its tip on the camera target: the centre of the map area left
 *  between the top chrome and the sheet, which is what the map padding defines. */
@Composable
private fun Crosshair(centerY: Dp) {
    val pinHeight = 37.dp
    Box(Modifier.fillMaxSize().semantics { invisibleToUser() }) {
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerY - pinHeight)
                .size(width = 30.dp, height = pinHeight),
        ) {
            val r = size.width / 2
            drawCircle(DsColor.accent, radius = r, center = Offset(r, r))
            drawCircle(DsColor.onAccent, radius = r / 3, center = Offset(r, r))
            drawPath(Path().apply {
                moveTo(r - 6.dp.toPx(), size.width - 1)
                lineTo(r + 6.dp.toPx(), size.width - 1)
                lineTo(r, size.height)
                close()
            }, DsColor.accent)
        }
    }
}

// MARK: - Sheet headers

@Composable
private fun ExploreHeader(recents: List<PlaceResult>, userLocation: LatLng?, onPick: (PlaceResult) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Plan a ride", style = DsType.cardTitle, color = DsColor.textPrimary, modifier = Modifier.semantics { heading() })
        Text("Search for where the pack is heading.", style = DsType.bodySmall, color = DsColor.textMuted, modifier = Modifier.padding(bottom = 8.dp))
        RecentRows(recents, userLocation, onPick)
    }
}

@Composable
private fun RecentRows(places: List<PlaceResult>, userLocation: LatLng?, onPick: (PlaceResult) -> Unit) {
    Column {
        places.forEach { place ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(DsColor.divider))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .dsClickable(pressedScale = 0.99f, onClickLabel = "Plan a ride to ${place.name}") { onPick(place) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Schedule, null, tint = DsColor.textMuted, modifier = Modifier.size(18.dp))
                Text(place.name, style = DsType.locationTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                userLocation?.let { Text("${formatKm(metersBetweenPoints(it, place.latLng))} km", style = DsType.dataMono, color = DsColor.textMuted) }
            }
        }
    }
}

/** With a destination: the route at a glance, the ride's name, and the primary action. */
@Composable
private fun RouteHeader(
    state: PlannerState,
    onDelete: (() -> Unit)?,
    onOpenRiders: () -> Unit,
    onChooseLocation: () -> Unit,
    locationGranted: Boolean,
    primaryAction: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { RouteSummary(state, onChooseLocation, locationGranted) }
            if (onDelete != null) MoreMenu(onDelete)
        }
        TitleRow(state, onOpenRiders)
        primaryAction()
    }
}

@Composable
private fun RouteSummary(state: PlannerState, onChooseLocation: () -> Unit, locationGranted: Boolean) {
    when {
        state.isRouting && !state.hasRoute -> Row(
            Modifier.heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), color = DsColor.accent, strokeWidth = 2.dp)
            Text("Finding routes…", style = DsType.body, color = DsColor.textSecondary)
        }
        state.routeFailed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No route found", style = DsType.rowTitle, color = DsColor.textPrimary)
            Text("Google couldn't find a road between these places. Try another destination or start.", style = DsType.bodySmall, color = DsColor.textMuted)
            Text("Try again", style = DsType.link, color = DsColor.accent,
                modifier = Modifier.heightIn(min = 44.dp).dsClickable { state.recalculateRoute() }.padding(vertical = 12.dp))
        }
        state.isWaitingForLocation -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (locationGranted) "Finding your location…" else "Where does the ride start?", style = DsType.rowTitle, color = DsColor.textPrimary)
            Text(
                if (locationGranted) "Turn on location, or choose a place instead." else "Allow location to start from where you are, or choose a place.",
                style = DsType.bodySmall,
                color = DsColor.textMuted,
            )
            Text(if (locationGranted) "Choose a place" else "Allow location", style = DsType.link, color = DsColor.accent,
                modifier = Modifier.heightIn(min = 44.dp).dsClickable(onClick = onChooseLocation).padding(vertical = 12.dp))
        }
        else -> Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(RideDuration.inline(state.durationSeconds.toInt()), style = DsType.metricValue, color = DsColor.accent)
                Text("(${formatKm(state.distanceMeters)} km)", style = DsType.body, color = DsColor.textSecondary, modifier = Modifier.padding(bottom = 2.dp))
                if (state.isRouting) CircularProgressIndicator(Modifier.size(14.dp).padding(bottom = 4.dp), color = DsColor.accent, strokeWidth = 2.dp)
            }
            // One line: a wrap would grow the resting sheet and nudge the map each time a different
            // route is picked. The route list shows the full name.
            Text(routeLabel(state), style = DsType.bodySmall, color = DsColor.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Which route is selected and the road it takes, led by what it avoids. */
private fun routeLabel(state: PlannerState): String {
    val parts = mutableListOf<String>()
    when {
        state.avoid.highways && state.avoid.tolls -> parts += "No highways or tolls"
        state.avoid.highways -> parts += "No highways"
        state.avoid.tolls -> parts += "No tolls"
    }
    val stops = (state.chosenStops.size - 2).coerceAtLeast(0)
    if (stops > 0) {
        parts += if (stops == 1) "Via 1 stop" else "Via $stops stops"
        return parts.joinToString(" · ")
    }
    val selected = state.routes.getOrNull(state.selectedRouteIndex)
    parts += selected?.let { routeTag(state, it) } ?: "Best route"
    selected?.summary?.let { parts += "via $it" }
    return parts.joinToString(" · ")
}

/** "Fastest route", or how much slower than the fastest. */
private fun routeTag(state: PlannerState, option: RouteOption): String {
    val fastest = state.routes.minOfOrNull { it.durationSeconds } ?: option.durationSeconds
    val slower = ((option.durationSeconds - fastest) / 60).roundToInt()
    return if (slower <= 0) "Fastest route" else "$slower min slower"
}

/** Destructive actions stay out of the way of the primary one: in a menu, behind a confirmation
 *  the host shows. */
@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).dsClickable(onClickLabel = "More") { open = true }.semantics { contentDescription = "More" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(DsColor.field), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreHoriz, null, tint = DsColor.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = DsColor.raised) {
            DropdownMenuItem(
                text = { Text("Delete ride", style = DsType.body) },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                colors = MenuDefaults.itemColors(textColor = DsColor.dangerStrong, leadingIconColor = DsColor.dangerStrong),
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun TitleRow(state: PlannerState, onOpenRiders: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(if (focused) DsColor.fieldFocused else DsColor.field, DsShape.sm)
                .border(if (focused) 2.dp else 1.dp, if (focused) DsColor.focus else DsColor.border, DsShape.sm)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Edit, null, tint = DsColor.textMuted, modifier = Modifier.size(16.dp))
            Box(Modifier.weight(1f)) {
                if (state.title.isEmpty()) Text("Ride name", style = DsType.locationTitle, color = DsColor.textMuted)
                BasicTextField(
                    value = state.title,
                    onValueChange = { state.title = it },
                    singleLine = true,
                    textStyle = DsType.locationTitle.copy(color = DsColor.textPrimary),
                    cursorBrush = SolidColor(DsColor.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = "Ride name" },
                )
            }
        }
        // Shortcut to the rider limit, which lives further down the sheet.
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 56.dp)
                .background(DsColor.field, DsShape.sm)
                .dsClickable(onClickLabel = "Show the rider limit", onClick = onOpenRiders)
                .semantics { contentDescription = "Max riders, ${state.maxRiders}" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Group, null, tint = DsColor.textPrimary, modifier = Modifier.size(18.dp))
            Text("${state.maxRiders}", style = DsType.bodyStrong, color = DsColor.textPrimary)
        }
    }
}

// MARK: - Sheet content

@Composable
private fun DetailsContent(state: PlannerState, riderCap: RiderCap, onUpgrade: () -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DsColor.divider))
        // The same choice as the bubbles, as a list: readable at any size and reachable with
        // TalkBack, which can't touch a line on the map.
        if (state.routes.size > 1) {
            Column {
                SheetSectionLabel("Routes")
                state.routes.forEachIndexed { index, option ->
                    RouteRow(state, option, index == state.selectedRouteIndex) {
                        view.dsHaptic(DsHaptic.Selection)
                        state.selectRoute(index)
                    }
                }
            }
        }
        Column {
            SheetSectionLabel("Route options")
            OptionToggle("Avoid highways", Icons.Outlined.AltRoute, state.avoid.highways) { state.updateAvoid(state.avoid.copy(highways = it)) }
            OptionToggle("Avoid tolls", Icons.Filled.Payments, state.avoid.tolls) { state.updateAvoid(state.avoid.copy(tolls = it)) }
        }
        RiderLimitSection(state.maxRiders, { state.maxRiders = it }, riderCap, onUpgrade)
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = DsType.sectionLabel,
        color = DsColor.textLabel,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun RouteRow(state: PlannerState, option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DsColor.divider))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .dsClickable(pressedScale = 0.99f, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            null,
            tint = if (selected) DsColor.accent else DsColor.textSubtle,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(RideDuration.inline(option.durationSeconds.toInt()), style = DsType.rowTitle, color = if (selected) DsColor.accent else DsColor.textPrimary)
                Text("${formatKm(option.distanceMeters)} km", style = DsType.bodySmall, color = DsColor.textSecondary)
            }
            val tag = routeTag(state, option)
            Text(option.summary?.let { "$tag · via $it" } ?: tag, style = DsType.helper, color = DsColor.textMuted)
        }
    }
}

@Composable
private fun OptionToggle(title: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DsColor.divider))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .dsClickable(pressedScale = 0.99f, role = Role.Switch) { onChange(!checked) }
            .semantics(mergeDescendants = true) { toggleableState = ToggleableState(checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = DsColor.textMuted, modifier = Modifier.size(18.dp))
        }
        Text(title, style = DsType.body, color = DsColor.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = DsColor.accent,
                checkedThumbColor = DsColor.onAccent,
                uncheckedTrackColor = DsColor.field,
                uncheckedThumbColor = DsColor.textSecondary,
                uncheckedBorderColor = DsColor.border,
            ),
        )
    }
}

// MARK: - Place cards

@Composable
private fun ChooseCard(role: StopRole, center: PlaceCandidate?, onFinish: () -> Unit, onCancel: () -> Unit) {
    val (eyebrow, title, icon) = when (role) {
        StopRole.START -> Triple("Choose start", "Start here", Icons.Filled.Navigation)
        StopRole.STOP -> Triple("Choose stop", "Add this stop", Icons.Filled.Add)
        StopRole.DESTINATION -> Triple("Choose destination", "Set as destination", Icons.Filled.Flag)
    }
    PlannerPlaceCard(eyebrow = eyebrow, candidate = center, actions = listOf(PlaceAction(title, icon, onFinish)), onClose = onCancel)
}

/** With a route, the likely wish is a stop on the way; before one, it's where to ride to. */
private fun droppedPinActions(state: PlannerState, usePin: ((PlaceResult) -> Unit) -> Unit): List<PlaceAction> {
    val setDestination = PlaceAction("Set as destination", Icons.Filled.Flag) { usePin { state.choose(it, state.destinationId) } }
    val startHere = PlaceAction("Start here", Icons.Filled.Navigation) { usePin { state.choose(it, state.stops.first().id) } }
    if (!state.hasDestination) return listOf(setDestination, startHere)
    val endHere = PlaceAction("End here", Icons.Filled.Flag) { usePin { state.choose(it, state.destinationId) } }
    if (!state.canAddStop) return listOf(endHere, startHere)
    val addStop = PlaceAction("Add as stop", Icons.Filled.Add) { usePin { place -> state.addStop()?.let { state.choose(place, it) } } }
    return listOf(addStop, startHere, endHere)
}
