package com.danglingmind.convotrack.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.ui.theme.CT
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

enum class MapMarkerKind { DESTINATION, WAYPOINT, RIDER, USER, REGROUP }

data class MapMarker(
    val id: String,
    val position: LatLng,
    val kind: MapMarkerKind,
    val label: String? = null,
)

/**
 * Reusable dark Google map — the Android analogue of iOS GoogleMapView. Draws the lime
 * route polyline plus typed markers, and fits the camera to whatever it's given. Set
 * [interactive] = false for non-interactive previews (lobby / home card).
 */
@Composable
fun ConvoTrackMap(
    modifier: Modifier = Modifier,
    routeCoords: List<LatLng> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val darkStyle = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }
    val cameraPositionState = rememberCameraPositionState()

    // Fit the camera to the route + markers whenever they change. newLatLngBounds needs the
    // map to be measured, so guard with runCatching and fall back to a centered zoom.
    LaunchedEffect(routeCoords, markers) {
        val points = routeCoords + markers.map { it.position }
        when {
            points.isEmpty() -> Unit
            points.size == 1 ->
                cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 13f)
            else -> {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                runCatching { cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 140)) }
                    .onFailure {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 11f)
                    }
            }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL, mapStyleOptions = darkStyle),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = interactive,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
        ),
    ) {
        if (routeCoords.size >= 2) {
            Polyline(points = routeCoords, color = CT.primaryFixed, width = 12f)
        }
        markers.forEach { marker ->
            Marker(
                state = MarkerState(position = marker.position),
                title = marker.label,
                icon = BitmapDescriptorFactory.defaultMarker(hueFor(marker.kind)),
            )
        }
    }
}

private fun hueFor(kind: MapMarkerKind): Float = when (kind) {
    MapMarkerKind.DESTINATION -> BitmapDescriptorFactory.HUE_GREEN
    MapMarkerKind.WAYPOINT -> BitmapDescriptorFactory.HUE_VIOLET
    MapMarkerKind.RIDER -> BitmapDescriptorFactory.HUE_AZURE
    MapMarkerKind.USER -> BitmapDescriptorFactory.HUE_YELLOW
    MapMarkerKind.REGROUP -> BitmapDescriptorFactory.HUE_ORANGE
}
