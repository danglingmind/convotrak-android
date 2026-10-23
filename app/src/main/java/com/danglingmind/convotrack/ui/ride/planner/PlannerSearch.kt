package com.danglingmind.convotrack.ui.ride.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.PlaceCategory
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.data.maps.PlaceSearchHit
import com.danglingmind.convotrack.data.maps.PlacesService
import com.danglingmind.convotrack.data.maps.RouteAvoidances
import com.danglingmind.convotrack.data.maps.searchAlongRoute
import com.danglingmind.convotrack.data.maps.searchText
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

internal val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.FUEL -> Icons.Filled.LocalGasStation
        PlaceCategory.FOOD -> Icons.Filled.Restaurant
        PlaceCategory.VIEWPOINT -> Icons.Filled.Terrain
    }

/**
 * Full-screen place search over the planner's map, for one stop at a time (iOS
 * PlannerSearchOverlay). Results come in as the rider types; before that it offers recent
 * destinations, "Your location" where it fits, "Choose on map", and the category chips.
 */
@Composable
internal fun PlannerSearch(
    role: StopRole,
    offersCurrentLocation: Boolean,
    recents: List<PlaceResult>,
    onPick: (PlaceResult) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onChooseOnMap: () -> Unit,
    onCancel: () -> Unit,
    /** Where the chips look: along this route when given (adding a stop), else around [searchCenter]. */
    searchCenter: LatLng?,
    route: List<LatLng>,
    measureDetours: Boolean,
    avoid: RouteAvoidances,
    initialCategory: PlaceCategory?,
    userLocation: LatLng?,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<PlaceCategory?>(null) }
    var hits by remember { mutableStateOf<List<PlaceSearchHit>>(emptyList()) }
    var categoryJob by remember { mutableStateOf<Job?>(null) }

    val trimmed = query.trim()
    val showsChips = role != StopRole.START
    val alongRoute = role == StopRole.STOP && route.size >= 2
    val prompt = when (role) {
        StopRole.START -> "Choose a start point"
        StopRole.STOP -> "Add a stop"
        StopRole.DESTINATION -> "Where are you riding?"
    }

    fun selectCategory(picked: PlaceCategory) {
        categoryJob?.cancel()
        query = ""
        focusManager.clearFocus()
        if (category == picked) {
            category = null
            hits = emptyList()
            searching = false
            return
        }
        view.dsHaptic(DsHaptic.Selection)
        category = picked
        hits = emptyList()
        searching = true
        categoryJob = scope.launch {
            hits = if (alongRoute) {
                PlacesService.searchAlongRoute(picked.query, route, measureDetours, avoid)
            } else {
                PlacesService.searchText(picked.query, searchCenter ?: userLocation).map { PlaceSearchHit(it) }
            }
            searching = false
        }
    }

    LaunchedEffect(Unit) {
        if (initialCategory != null) selectCategory(initialCategory) else focus.requestFocus()
    }
    // Results as the rider types, debounced; typing clears a chosen chip.
    LaunchedEffect(trimmed) {
        if (trimmed.length < 2) {
            if (category == null) searching = false
            results = emptyList()
            return@LaunchedEffect
        }
        category = null
        categoryJob?.cancel()
        searching = true
        delay(300)
        results = PlacesService.search(trimmed, userLocation)
        searching = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DsColor.bgBase)
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchField(query, { query = it }, prompt, searching, focus, onCancel, onSubmit = { results.firstOrNull()?.let(onPick) })

        if (showsChips && trimmed.length < 2) {
            CategoryChips(selected = category, onSelect = ::selectCategory, onMap = false, modifier = Modifier.padding(bottom = 8.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = DsSpace.gutter, vertical = 4.dp),
        ) {
            val picked = category
            when {
                picked != null && trimmed.length < 2 -> {
                    item {
                        Text(
                            (if (alongRoute) "${picked.title} along your route" else "${picked.title} near the map").uppercase(Locale.getDefault()),
                            style = DsType.sectionLabel,
                            color = DsColor.textLabel,
                            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
                        )
                    }
                    if (searching && hits.isEmpty()) {
                        item { Spinner() }
                    } else if (hits.isEmpty()) {
                        item { EmptyLine(if (alongRoute) "Nothing found close to this route." else "Nothing found here. Move the map and try again.") }
                    } else {
                        items(hits, key = { it.place.id }) { hit ->
                            PlaceRow(hit.place, picked.icon, userLocation, hit.detourSeconds) { onPick(hit.place) }
                        }
                    }
                    item { ActionRow(Icons.Filled.Map, "Choose on map", onChooseOnMap) }
                }
                trimmed.length < 2 -> {
                    if (offersCurrentLocation) item { ActionRow(Icons.Filled.MyLocation, "Your location", onUseCurrentLocation) }
                    item { ActionRow(Icons.Filled.Map, "Choose on map", onChooseOnMap) }
                    if (recents.isNotEmpty()) {
                        item {
                            Text(
                                "RECENT",
                                style = DsType.sectionLabel,
                                color = DsColor.textLabel,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                            )
                        }
                        items(recents, key = { "recent_" + it.id }) { place ->
                            PlaceRow(place, Icons.Filled.Schedule, userLocation, null) { onPick(place) }
                        }
                    }
                }
                results.isEmpty() && !searching -> {
                    item { EmptyLine("No places match “$trimmed”. Check the spelling or try a nearby town.") }
                    item { ActionRow(Icons.Filled.Map, "Choose on map", onChooseOnMap) }
                }
                else -> {
                    items(results, key = { it.id }) { place ->
                        PlaceRow(place, Icons.Filled.Place, userLocation, null) { onPick(place) }
                    }
                    item { ActionRow(Icons.Filled.Map, "Choose on map", onChooseOnMap) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    prompt: String,
    searching: Boolean,
    focus: FocusRequester,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(DsColor.fieldFocused, DsShape.pill)
            .border(1.5.dp, DsColor.focus, DsShape.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).dsClickable(onClickLabel = "Back", onClick = onCancel).semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(prompt, style = DsType.locationTitle, color = DsColor.textMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = DsType.locationTitle.copy(color = DsColor.textPrimary),
                cursorBrush = SolidColor(DsColor.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        when {
            searching -> CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(18.dp), color = DsColor.accent, strokeWidth = 2.dp)
            value.isNotEmpty() -> Box(
                Modifier.size(48.dp).dsClickable(onClickLabel = "Clear search") { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Cancel, "Clear search", tint = DsColor.textMuted, modifier = Modifier.size(20.dp))
            }
            else -> Spacer(Modifier.width(12.dp))
        }
    }
}

/** Fuel, Food, Viewpoints: one tap to the searches riders make most. Shared by the search screen
 *  and the empty map, where they float over the map as glass chips. */
@Composable
internal fun CategoryChips(
    selected: PlaceCategory?,
    onSelect: (PlaceCategory) -> Unit,
    onMap: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // Over the map the chips sit in the top chrome, which already has its own inset.
            .padding(horizontal = if (onMap) 0.dp else DsSpace.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceCategory.entries.forEach { category ->
            val isSelected = selected == category
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .padding(vertical = 4.dp)
                    .background(
                        when {
                            isSelected -> DsColor.accent
                            onMap -> DsColor.nav
                            else -> DsColor.field
                        },
                        DsShape.pill,
                    )
                    .border(1.dp, if (isSelected) DsColor.accent else DsColor.border, DsShape.pill)
                    .dsClickable(pressedScale = 0.96f) { onSelect(category) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tint = if (isSelected) DsColor.onAccent else DsColor.textPrimary
                Icon(category.icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Text(category.title, style = DsType.buttonPill, color = tint)
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceResult, icon: ImageVector, userLocation: LatLng?, detourSeconds: Double?, onClick: () -> Unit) {
    // Along a route the detour is the number that matters; distance from here isn't.
    val subtitle = if (detourSeconds != null) {
        val minutes = (detourSeconds / 60).roundToInt()
        listOfNotNull(if (minutes < 1) "No detour" else "+$minutes min detour", place.address).joinToString(" · ")
    } else {
        place.address
    }
    val distance = if (detourSeconds == null) userLocation?.let { distanceText(metersBetweenPoints(it, place.latLng)) } else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .dsClickable(pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(icon, size = 36.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f)) {
            // The name wraps rather than truncating: it is the whole choice.
            Text(place.name, style = DsType.locationTitle, color = DsColor.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = DsType.helper, color = DsColor.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (distance != null) Text(distance, style = DsType.dataMono, color = DsColor.textMuted)
    }
    Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(1.dp).background(DsColor.divider))
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .dsClickable(pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(icon, size = 36.dp, iconSize = 18.dp)
        Text(title, style = DsType.locationTitle, color = DsColor.textPrimary)
    }
    Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(1.dp).background(DsColor.divider))
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = DsColor.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = DsType.body, color = DsColor.textMuted, modifier = Modifier.padding(vertical = 24.dp))
}

internal fun distanceText(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.US, "%.0f km", meters / 1000)

internal fun metersBetweenPoints(a: LatLng, b: LatLng): Double {
    val out = FloatArray(1)
    android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
    return out[0].toDouble()
}
