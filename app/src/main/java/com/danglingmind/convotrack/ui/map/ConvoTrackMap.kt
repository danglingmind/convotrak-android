package com.danglingmind.convotrack.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

enum class MapMarkerKind { DESTINATION, WAYPOINT, START, RIDER, REGROUP }

data class MapMarker(
    val id: String,
    val position: LatLng,
    val kind: MapMarkerKind,
    val label: String? = null,
    val isMe: Boolean = false,
)

/** The rider's own position + travel heading, drawn as a rotating arrow puck. */
data class UserPuck(val position: LatLng, val bearing: Float = 0f)

/** Camera behaviour: fit everything, 3D-follow the target (navigation), or leave it alone. */
sealed interface MapCamera {
    data object Fit : MapCamera
    data class Nav(val target: LatLng, val bearing: Float, val zoom: Float) : MapCamera
    /** Free-look: the user panned; don't move the camera until told to recenter. */
    data object Free : MapCamera
}

private const val NAV_TILT = 45f

/**
 * Reusable dark Google map — the Android analogue of iOS GoogleMapView. Draws a dim planned
 * route + a bright active route, typed & labelled markers, the user heading puck, and either
 * fits bounds or follows the user with a tilted 3D camera (navigation).
 */
@Composable
fun ConvoTrackMap(
    modifier: Modifier = Modifier,
    routeCoords: List<LatLng> = emptyList(),
    fullRouteCoords: List<LatLng> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    user: UserPuck? = null,
    camera: MapCamera = MapCamera.Fit,
    interactive: Boolean = true,
    onUserPan: () -> Unit = {},
    // All alternative routes (index == position). The one at [selectedRouteIndex] is drawn bright
    // via [routeCoords]; the rest render dim + tappable so the leader can pick one.
    alternativeRoutes: List<List<LatLng>> = emptyList(),
    selectedRouteIndex: Int = 0,
    onSelectRoute: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val darkStyle = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }
    val cameraPositionState = rememberCameraPositionState()
    // BitmapDescriptorFactory needs the Maps SDK initialised before use (idempotent).
    val userArrow = remember {
        MapsInitializer.initialize(context)
        userArrowDescriptor()
    }

    // In nav mode push the map centre down so the puck sits in the lower third and more of the
    // road ahead is visible (mirrors iOS padding.top = height*0.3).
    val contentPadding = if (camera is MapCamera.Nav) PaddingValues(top = 260.dp) else PaddingValues(0.dp)

    LaunchedEffect(camera) {
        when (camera) {
            is MapCamera.Nav -> {
                val pos = CameraPosition.Builder()
                    .target(camera.target).zoom(camera.zoom).tilt(NAV_TILT).bearing(camera.bearing).build()
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(pos), 700)
            }
            MapCamera.Free -> Unit // user is panning; leave the camera where they left it
            MapCamera.Fit -> {
                val points = routeCoords + fullRouteCoords + markers.map { it.position } + listOfNotNull(user?.position)
                when {
                    points.isEmpty() -> Unit
                    points.size == 1 -> cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 13f)
                    else -> {
                        val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                        runCatching { cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 140)) }
                            .onFailure { cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 11f) }
                    }
                }
            }
        }
    }

    // Report user-initiated pans/zooms so navigation can switch to free-look.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.cameraMoveStartedReason }
            .collect { if (it == CameraMoveStartedReason.GESTURE) onUserPan() }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
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
        // Dim, tappable alternatives sit below the bright selected route (iOS applyAlternatives).
        // Only shown when there's more than one option to choose between.
        if (alternativeRoutes.size > 1) {
            alternativeRoutes.forEachIndexed { index, coords ->
                if (index != selectedRouteIndex && coords.size >= 2) {
                    Polyline(
                        points = coords,
                        color = androidx.compose.ui.graphics.Color(0xFFC7C7C7).copy(alpha = 0.42f),
                        width = 10f,
                        zIndex = 0f,
                        clickable = onSelectRoute != null,
                        tag = index,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        jointType = JointType.ROUND,
                        onClick = { line -> (line.tag as? Int)?.let { onSelectRoute?.invoke(it) } },
                    )
                }
            }
        }

        // Dim planned route underneath, bright active route on top. Rounded end caps + joints
        // give each line a soft "dot" terminus everywhere the map is used.
        if (fullRouteCoords.size >= 2) {
            Polyline(
                points = fullRouteCoords,
                color = CT.primaryFixed.copy(alpha = 0.28f),
                width = 9f,
                zIndex = 1f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND,
            )
        }
        if (routeCoords.size >= 2) {
            Polyline(
                points = routeCoords,
                color = CT.primaryFixed,
                width = 14f,
                zIndex = 2f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND,
            )
        }

        markers.forEach { marker -> LabeledMarker(marker) }

        if (user != null) {
            val state = rememberMarkerState(position = user.position)
            LaunchedEffect(user.position) { state.position = user.position }
            Marker(
                state = state,
                icon = userArrow,
                rotation = user.bearing,
                flat = true,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                zIndex = 10f,
            )
        }
    }
}

@Composable
private fun LabeledMarker(marker: MapMarker) {
    val state = rememberMarkerState(position = marker.position)
    LaunchedEffect(marker.position) { state.position = marker.position }
    MarkerComposable(
        marker.id, marker.label.orEmpty(), marker.isMe,
        state = state,
        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
    ) {
        PinContent(marker)
    }
}

@Composable
private fun PinContent(marker: MapMarker) {
    val accent = when (marker.kind) {
        MapMarkerKind.DESTINATION -> CT.primaryFixed
        MapMarkerKind.START -> CT.tertiaryFixed
        MapMarkerKind.WAYPOINT -> CT.primaryFixed
        MapMarkerKind.RIDER -> if (marker.isMe) CT.primaryFixed else CT.onSurface
        MapMarkerKind.REGROUP -> CT.errorColor
    }
    Box(
        Modifier
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            when (marker.kind) {
                MapMarkerKind.DESTINATION ->
                    androidx.compose.material3.Icon(Icons.Filled.Flag, null, tint = accent, modifier = Modifier.size(13.dp))
                MapMarkerKind.RIDER ->
                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(accent))
                else ->
                    androidx.compose.material3.Icon(Icons.Filled.Place, null, tint = accent, modifier = Modifier.size(13.dp))
            }
            Text(
                (marker.label ?: labelFor(marker.kind)).uppercase(),
                style = CTType.labelCaps.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = androidx.compose.ui.graphics.Color.White,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

private fun labelFor(kind: MapMarkerKind): String = when (kind) {
    MapMarkerKind.DESTINATION -> "Destination"
    MapMarkerKind.START -> "Start"
    MapMarkerKind.WAYPOINT -> "Stop"
    MapMarkerKind.RIDER -> "Rider"
    MapMarkerKind.REGROUP -> "Regroup"
}

/** Lime heading arrow puck (drawn pointing up; the Marker's rotation applies the heading). */
private fun userArrowDescriptor(): BitmapDescriptor {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val lime = CT.primaryFixed.toArgb()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer glow
    paint.color = CT.primaryFixed.copy(alpha = 0.22f).toArgb()
    c.drawCircle(cx, cy, size / 2f, paint)
    // Inner disc
    paint.color = lime
    c.drawCircle(cx, cy, 24f, paint)
    // White ring
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, 21f, paint)
    // Dark arrow pointing up
    paint.style = Paint.Style.FILL
    paint.color = CT.onPrimaryFixed.toArgb()
    val arrow = Path().apply {
        moveTo(cx, cy - 15f)
        lineTo(cx - 9f, cy + 9f)
        lineTo(cx + 9f, cy + 9f)
        close()
    }
    c.drawPath(arrow, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}
