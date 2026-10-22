package com.danglingmind.convotrack.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.maps.AvatarCache
import com.danglingmind.convotrack.ui.ride.metersBetween
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class MapMarkerKind { DESTINATION, WAYPOINT, START, RIDER, REGROUP, EMERGENCY }

data class MapMarker(
    val id: String,
    val position: LatLng,
    val kind: MapMarkerKind,
    val label: String? = null,
    val isMe: Boolean = false,
    /** A rider whose heartbeats have lapsed: kept on the map at their last-known spot but dimmed,
     *  mirroring the greyed leaderboard row so map and list agree on who's present. */
    val isOffline: Boolean = false,
    val isSelected: Boolean = false,
    val avatarUrl: String? = null,
    /** Shown under a rider marker — how far they are from ME. */
    val distanceText: String? = null,
    /** For REGROUP: FUEL / FOOD / SCENIC / STOP, which picks the glyph and accent. */
    val regroupType: String? = null,
)

/** The rider's own position + travel heading, drawn as the navigation chevron. */
data class UserPuck(val position: LatLng, val bearing: Float = 0f)

/**
 * Where the camera target sits in the viewport.
 *
 * This used to be inferred from `tilt > 0`, which silently coupled two unrelated things: flatten
 * the camera to 2D and the marker jumps back to dead centre, losing the road ahead. It is stated
 * by the caller instead, so guidance keeps its low marker at any tilt.
 */
enum class MapFraming {
    /** Guidance — target sits low, most of the viewport showing the road ahead. */
    ROAD_AHEAD,

    /** Looking at a place — target centred. */
    CENTERED,
}

/** Camera behaviour. [id] makes a repeat of the same instruction a distinct command. */
sealed interface MapCamera {
    val id: Long

    /** Fit these coordinates inside the given edge insets (in dp). */
    data class Fit(
        override val id: Long,
        val coords: List<LatLng>,
        val topDp: Float = 48f,
        val leftDp: Float = 48f,
        val bottomDp: Float = 48f,
        val rightDp: Float = 48f,
        val animated: Boolean = true,
    ) : MapCamera

    data class Navigate(
        override val id: Long,
        val target: LatLng,
        val zoom: Float,
        val bearing: Float,
        val tilt: Float,
        val animated: Boolean = true,
        val framing: MapFraming = MapFraming.ROAD_AHEAD,
    ) : MapCamera

    /** Free-look: the user panned; don't move the camera until told to recenter. */
    data class Free(override val id: Long = 0) : MapCamera

    /**
     * Frame everything on screen — route, markers and the user — and re-frame whenever those
     * change. The default for every non-navigation map (lobby preview, home card, route pickers),
     * which have nothing to drive an explicit camera with.
     */
    data object AutoFit : MapCamera {
        override val id: Long get() = 0
    }
}

object NavCamera {
    /**
     * Camera pitch for guidance.
     *
     * The pitch was never the problem with the old "3D" look: what read as 3D was the SDK's
     * extruded building layer, which is now off. Tilt over a FLAT map is what Google Maps itself
     * uses — perspective on the road ahead, with no blocks stacked over the route.
     */
    const val TILT = 52f

    /**
     * Speed → zoom curve for the guidance camera, matched against Google Maps' navigation view:
     * ~18.4 stopped or crawling through a junction, easing out to ~16.4 at highway speed, so the
     * look-ahead stays roughly constant in *seconds of travel* rather than in metres.
     *
     * Interpolated rather than bucketed: the three-bucket table this replaces sat about a full zoom
     * level wider at city speed (twice the ground area Google shows) and made the map visibly jump
     * a whole level as you crossed 20 or 60 km/h. Quantized to 0.25 so noisy GPS speed can't make
     * it breathe.
     *
     * SCOPE — this is the zoom and nothing else. Pitch ([TILT]) and where the marker sits in the
     * viewport ([ROAD_AHEAD_FRACTION]) are deliberately left alone: changing those alongside the
     * zoom compounds, and a tighter zoom on a shallower pitch with the puck lower pushes the convoy
     * markers, the destination pin and the road ahead off-screen. Tune this table on its own.
     */
    private val zoomAnchors = listOf(
        0.0 to 18.4f, 20.0 to 18.1f, 40.0 to 17.6f,
        60.0 to 17.2f, 80.0 to 16.9f, 100.0 to 16.6f, 120.0 to 16.4f,
    )

    fun zoomForSpeed(kmh: Double): Float {
        val s = kmh.coerceAtLeast(0.0)
        var zoom = zoomAnchors.last().second
        val i = zoomAnchors.indexOfFirst { s <= it.first }
        if (i == 0) {
            zoom = zoomAnchors[0].second
        } else if (i > 0) {
            val lo = zoomAnchors[i - 1]
            val hi = zoomAnchors[i]
            val t = ((s - lo.first) / (hi.first - lo.first)).toFloat()
            zoom = lo.second + (hi.second - lo.second) * t
        }
        return Math.round(zoom * 4) / 4f
    }

    /** How far down the viewport the guidance target sits, as a fraction of map height. */
    const val ROAD_AHEAD_FRACTION = 0.35f

    /**
     * Height of the tallest stop-pin art drawn ABOVE its coordinate, in dp.
     *
     * A camera fit must add this to its top inset: fitting frames the coordinates and knows nothing
     * about the marker towering over them, so a route whose northernmost point is a stop would
     * otherwise have that pin clipped off-screen.
     */
    const val STOP_PIN_TOP_EXTENT_DP = 104f
}

// Route thickness. `ROUTE_CHUNKINESS` is the single dial; the glow around the bright core derives
// from it so the line looks consistent at any width.
/** Placeholder geometry for a hidden polyline: the SDK needs a valid path even when invisible. */
private val EMPTY_LINE = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.0))

/**
 * Vertex ceiling for a drawn polyline — a safety net, not a budget.
 *
 * The comment this replaces asserted the Maps SDK "silently declines to draw a polyline" above
 * ~33,000 vertices. Neither the Maps SDK reference nor the shapes guide documents any point limit,
 * and the same change that introduced that claim also fixed a real maps-compose defect with the
 * identical symptom — a route present in the composition, correct points, simply never drawn —
 * caused by the map's child node list changing shape (see the `visible`-toggling comment on the
 * route layers below). That defect is the likelier original culprit, and it is already fixed.
 *
 * Rationing points was the expensive mistake. Thinning to fit a budget forces the tolerance up
 * until the geometry fits, and on a long ride the tolerance that fits is the tolerance that eats
 * the corners: a 1438 km route squeezed to 3,000 points lands on a 16 m tolerance, which is 46 px
 * of error at guidance zoom — a straight chord across every bend. The same route needs only about
 * 10,000 points to hold 1 m, and 15,000 to hold 0.5 m, which is below the ~1.1 m quantisation of
 * Google's own encoded polyline and so lossless in any sense the source data can express.
 *
 * 40,000 therefore sits above every real ride, so ordinary routes — including the 1438 km one —
 * are handed to the SDK exactly as Google returned them, unthinned. Only a route larger than any
 * yet seen falls through to [thinToBudget], and it does so at a tolerance that stays invisible.
 */
private const val MAX_DRAWN_POINTS = 40_000

/**
 * Base Douglas-Peucker tolerance, in metres.
 *
 * Below the ~1.1 m quantisation of an encoded polyline, so a vertex only goes when the line drawn
 * without it is indistinguishable from the one the API sent — including when the rider pinches
 * all the way in, which is where a 1 m tolerance starts to show.
 */
private const val DRAW_SIMPLIFY_TOLERANCE_METERS = 0.5

/**
 * How much of the ACTIVE route, measured from the rider, is exempt from thinning entirely.
 *
 * Comfortably more than the guidance camera can show, so the road under the chevron is always
 * drawn lane-accurate, and enough to survive a pinch-out to a few kilometres before the far-field
 * simplification becomes visible.
 */
private const val NAV_FULL_DETAIL_METERS = 5_000.0

private const val ROUTE_CHUNKINESS = 16f
/** The route is a bright core inside a soft glow of the same colour (design system: map.routeLayers). */
private const val GLOW_RATIO = 2.3f

/**
 * Reusable dark Google map — the Android analogue of iOS `GoogleMapView`.
 *
 * Draws a dim planned route beneath a bright active route (with a casing for depth), typed and
 * labelled markers baked to bitmaps, the user's heading chevron, and either fits bounds or follows
 * the rider with a tilted guidance camera.
 */
@Composable
fun ConvoTrackMap(
    modifier: Modifier = Modifier,
    routeCoords: List<LatLng> = emptyList(),
    fullRouteCoords: List<LatLng> = emptyList(),
    /** Ordered stop coordinates (start → waypoints → destination). Used to draw curved dotted
     *  connectors from each end stop to the polyline's own endpoint. */
    stopCoords: List<LatLng> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    user: UserPuck? = null,
    camera: MapCamera = MapCamera.AutoFit,
    interactive: Boolean = true,
    /** True while turn-by-turn guidance is running, i.e. [routeCoords] is re-cut every fix to start
     *  under the rider. The head of the active route is then the rider's own chevron, not a route
     *  endpoint, so it gets no end cap of its own. */
    isNavigating: Boolean = false,
    onUserPan: () -> Unit = {},
    /** Screen geometry for riders currently off-screen, recomputed as the camera moves. */
    onEdgeIndicators: ((List<EdgeIndicator>) -> Unit)? = null,
    /** Where the app's top chrome ends, in dp. Edge indicators clamp below it instead of sliding
     *  up behind the turn banner and leaderboard. */
    topOverlayDp: Dp = 0.dp,
    // All alternative routes (index == position). The one at [selectedRouteIndex] is drawn bright
    // via [routeCoords]; the rest render dim + tappable so the leader can pick one.
    alternativeRoutes: List<List<LatLng>> = emptyList(),
    selectedRouteIndex: Int = 0,
    onSelectRoute: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scale = density.density
    // Baked labels can't follow the system font size on their own; cap it so a big setting grows
    // them without letting them swallow the route.
    val fontScale = density.fontScale.coerceIn(1f, 1.5f)

    // Drawn geometry is capped; the camera and all navigation math still use the full lists.
    // The bright route's head is the rider while guidance is running, so its near field is what
    // the camera is actually looking at and must never be thinned.
    val drawRoute = remember(routeCoords, isNavigating) {
        simplifyForDisplay(
            routeCoords,
            headDetailMeters = if (isNavigating) NAV_FULL_DETAIL_METERS else 0.0,
        )
    }
    val drawFullRoute = remember(fullRouteCoords) { simplifyForDisplay(fullRouteCoords) }
    // Alternatives are simplified once per change too, not per recomposition — the route picker
    // recomposes on every selection tap and these lists are the same size as the main route.
    val drawAlternatives = remember(alternativeRoutes) { alternativeRoutes.map { simplifyForDisplay(it) } }
    val darkStyle = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }
    val cameraPositionState = rememberCameraPositionState()

    // BitmapDescriptorFactory needs the Maps SDK initialised before use (idempotent).
    val userChevron = remember(scale) {
        MapsInitializer.initialize(context)
        BitmapDescriptorFactory.fromBitmap(MapMarkerBitmaps.userChevron(scale))
    }

    // Avatar photos for rider markers. Baking a marker icon is synchronous, so each avatar is
    // fetched once and the affected marker re-baked when it lands. The cache is process-wide
    // (AvatarCache), so re-entering navigation doesn't re-download the whole convoy, and the
    // decode is bounded to marker size rather than the CDN original.
    val avatars = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(markers) {
        markers.mapNotNull { it.avatarUrl }.distinct().forEach { url ->
            AvatarCache.peek(url)?.let { avatars[url] = it } ?: AvatarCache.get(url)?.let { avatars[url] = it }
        }
    }

    // In nav mode push the map centre down so the chevron sits low and more of the road ahead is
    // visible. Expressed as a fraction of the map's height, not a constant, so it holds on any
    // screen and in landscape.
    var mapHeightPx by remember { mutableStateOf(0) }
    var mapWidthPx by remember { mutableStateOf(0) }
    val contentPadding = when {
        camera is MapCamera.Navigate && camera.framing == MapFraming.ROAD_AHEAD ->
            PaddingValues(top = with(density) { (mapHeightPx * NavCamera.ROAD_AHEAD_FRACTION).toDp() })

        // A bounds fit takes ONE padding value, so asymmetric insets have to be expressed as map
        // content padding instead — the SDK subtracts it from the viewport before framing, which
        // is exactly what "fit the route into the band the chrome leaves visible" means. Collapsing
        // the four insets into their maximum (the only alternative) would zoom the preview out by
        // the largest one on all four sides.
        camera is MapCamera.Fit -> PaddingValues(
            start = camera.leftDp.dp,
            top = camera.topDp.dp,
            end = camera.rightDp.dp,
            bottom = camera.bottomDp.dp,
        )

        else -> PaddingValues(0.dp)
    }

    // AutoFit re-frames on content change, so it is keyed on the content rather than a command id.
    if (camera is MapCamera.AutoFit) {
        LaunchedEffect(routeCoords, fullRouteCoords, markers, user) {
            val points = drawRoute + drawFullRoute + markers.map { it.position } + listOfNotNull(user?.position)
            when {
                points.isEmpty() -> Unit
                points.size == 1 ->
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 13f)
                else -> {
                    val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                    runCatching {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 140))
                    }.onFailure {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 11f)
                    }
                }
            }
        }
    }

    // Explicit camera commands are keyed on the command's id, so re-issuing the same target
    // still moves the camera.
    LaunchedEffect(camera.id) {
        when (camera) {
            is MapCamera.Navigate -> {
                val pos = CameraPosition.Builder()
                    .target(camera.target).zoom(camera.zoom).tilt(camera.tilt).bearing(camera.bearing)
                    .build()
                val update = CameraUpdateFactory.newCameraPosition(pos)
                if (camera.animated) {
                    runCatching { cameraPositionState.animate(update, 700) }
                } else {
                    cameraPositionState.move(update)
                }
            }
            is MapCamera.Fit -> {
                val points = camera.coords
                when {
                    points.isEmpty() -> Unit
                    points.size == 1 ->
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 13f)
                    else -> {
                        val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                        // The insets are already applied as content padding above; this is just a
                        // small breathing margin inside the resulting band.
                        val pad = with(density) { 8.dp.roundToPx() }
                        runCatching {
                            val update = CameraUpdateFactory.newLatLngBounds(bounds, pad)
                            if (camera.animated) cameraPositionState.animate(update, 700)
                            else cameraPositionState.move(update)
                        }.onFailure {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(points.first(), 11f)
                        }
                    }
                }
            }
            // Handled above (content-keyed) / nothing to do.
            is MapCamera.AutoFit -> Unit
            is MapCamera.Free -> Unit // user is panning; leave the camera where they left it
        }
    }

    // Report user-initiated pans/zooms so navigation can switch to free-look.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.cameraMoveStartedReason }
            .collect { if (it == CameraMoveStartedReason.GESTURE) onUserPan() }
    }

    // Off-screen rider geometry, recomputed as the camera moves and when riders move.
    //
    // Throttled to 10 Hz, and only emitted on change, because this sits in a self-sustaining loop:
    // the camera position changes once per frame of every camera animation → compute → emit →
    // the navigation screen re-renders (rebuilding markers, re-baking icons) → new marker
    // identities → compute again. Left unbounded the loop runs as fast as the main thread allows,
    // and anything else wanting the main thread at the same time is competing with it.
    //
    // `conflate()` makes it coalescing rather than dropping: the chips still settle on the final
    // geometry instead of freezing at whatever the last un-throttled frame happened to be.
    if (onEdgeIndicators != null) {
        // The effect is keyed on Unit so the throttling collector lives for the map's lifetime —
        // which means it must NOT read the composable's parameters directly. A lambda inside a
        // `LaunchedEffect(Unit)` captures whatever those were on the FIRST composition and keeps
        // them forever, so this computed against an empty marker list and a null user for the whole
        // ride and never emitted a single indicator. `rememberUpdatedState` gives the long-lived
        // collector a snapshot-state handle on the current values instead — which also makes
        // `snapshotFlow` re-emit when they change.
        val currentMarkers by rememberUpdatedState(markers)
        val currentUser by rememberUpdatedState(user)
        val currentTopInsetPx by rememberUpdatedState(with(density) { topOverlayDp.toPx() })
        val emit by rememberUpdatedState(onEdgeIndicators)
        var lastEmitted by remember { mutableStateOf<List<EdgeIndicator>>(emptyList()) }
        LaunchedEffect(Unit) {
            snapshotFlow {
                EdgeInputs(cameraPositionState.position, currentMarkers, currentUser, mapWidthPx, mapHeightPx)
            }
                .conflate()
                .collect { inputs ->
                    val next = computeEdgeIndicators(
                        cameraPositionState = cameraPositionState,
                        markers = inputs.markers,
                        user = inputs.user,
                        widthPx = inputs.widthPx,
                        heightPx = inputs.heightPx,
                        topInsetPx = currentTopInsetPx,
                        scale = scale,
                    )
                    if (next != lastEmitted) {
                        lastEmitted = next
                        emit(next)
                    }
                    delay(100)   // 10 Hz cap, matching the camera tick
                }
        }
    }

    GoogleMap(
        modifier = modifier.onSizeChanged {
            mapWidthPx = it.width
            mapHeightPx = it.height
        },
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        // Dark before the first tiles arrive; the SDK default is a light beige that flashes on
        // every map card.
        googleMapOptionsFactory = { GoogleMapOptions().backgroundColor(DsColor.bgBase.toArgb()) },
        properties = MapProperties(
            mapType = MapType.NORMAL,
            mapStyleOptions = darkStyle,
            isTrafficEnabled = true,
            // Defaults to true, which is what drew the extruded blocks over the route. Google's own
            // navigation view renders a flat map, so the road geometry stays the loudest thing on
            // screen.
            isBuildingEnabled = false,
            isIndoorEnabled = false,
        ),
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
        // Dim, tappable alternatives sit below the bright selected route. Only shown when there's
        // more than one option to choose between.
        if (drawAlternatives.size > 1) {
            drawAlternatives.forEachIndexed { index, coords ->
                if (index != selectedRouteIndex && coords.size >= 2) {
                    Polyline(
                        points = coords,
                        color = DsColor.mapRouteAlternative,
                        width = 8f,
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

        // The three route layers are ALWAYS emitted and toggled with `visible`, never wrapped in an
        // `if`.
        //
        // maps-compose manages the map's children as an ordered node list, and dropping a node
        // changes that list's shape. The lobby (which has no separate planned layer, so the dim
        // polyline was absent) ended up with its BRIGHT route silently not rendering at all —
        // present in the composition, correct points, simply never drawn — while navigation, which
        // emits all three, drew fine. Keeping the node count fixed and flipping visibility instead
        // makes the two call sites structurally identical.
        val hasPlanned = drawFullRoute.size >= 2
        val hasRoute = drawRoute.size >= 2
        Polyline(
            points = if (hasPlanned) drawFullRoute else EMPTY_LINE,
            visible = hasPlanned,
            color = DsColor.mapRoutePlanned,
            width = 6f,
            zIndex = 1f,
            startCap = RoundCap(),
            endCap = RoundCap(),
            jointType = JointType.ROUND,
        )
        // A soft glow in the route colour, then the bright core on top, so the route reads as lit
        // rather than as a flat stripe on dark tiles.
        Polyline(
            points = if (hasRoute) drawRoute else EMPTY_LINE,
            visible = hasRoute,
            color = DsColor.mapRouteGlow,
            width = ROUTE_CHUNKINESS * GLOW_RATIO,
            zIndex = 2f,
            startCap = RoundCap(),
            endCap = RoundCap(),
            jointType = JointType.ROUND,
        )
        Polyline(
            points = if (hasRoute) drawRoute else EMPTY_LINE,
            visible = hasRoute,
            color = DsColor.mapRouteCore,
            width = ROUTE_CHUNKINESS,
            zIndex = 3f,
            startCap = RoundCap(),
            endCap = RoundCap(),
            jointType = JointType.ROUND,
        )

        // Curved dotted connectors: each end stop to its nearest polyline endpoint. Stops are
        // placed on a building, the route starts on the road — the connector makes that gap read
        // as intentional rather than as a broken line.
        val connectorBase = drawFullRoute.ifEmpty { drawRoute }
        if (connectorBase.size >= 2) {
            stopCoords.firstOrNull()?.let { CurvedConnector(it, connectorBase.first()) }
            stopCoords.lastOrNull()?.let { CurvedConnector(it, connectorBase.last()) }
        }

        // End caps. The HEAD cap is suppressed while navigating: the route now starts underneath
        // the chevron, so a cap there would only double-draw it — and previously it put a second
        // lime dot on the map a few metres off the chevron, tracking the polyline rather than the
        // rider, which read as the route's start point sliding along at a different pace.
        val endCapSmall = remember(scale) {
            BitmapDescriptorFactory.fromBitmap(MapMarkerBitmaps.endCap(scale, 14f, 0.45f))
        }
        val endCapBright = remember(scale) {
            BitmapDescriptorFactory.fromBitmap(MapMarkerBitmaps.endCap(scale, 16f, 1f))
        }
        drawFullRoute.firstOrNull()?.let { EndCap("plan_head", it, endCapSmall) }
        drawFullRoute.lastOrNull()?.let { EndCap("plan_tail", it, endCapSmall) }
        if (!isNavigating) drawRoute.firstOrNull()?.let { EndCap("route_head", it, endCapBright) }
        drawRoute.lastOrNull()?.let { EndCap("route_tail", it, endCapBright) }

        markers.forEach { marker ->
            BakedMarker(marker, scale, fontScale, avatars[marker.avatarUrl])
        }

        if (user != null) {
            val state = remember { MarkerState(position = user.position) }
            LaunchedEffect(user.position) { state.position = user.position }
            Marker(
                state = state,
                icon = userChevron,
                rotation = user.bearing,
                // Flat = lies on the road plane and turns with the map, so `rotation` is measured
                // clockwise from NORTH rather than from screen-up. The map owns the conversion into
                // screen space, so the chevron stays true whatever the camera bearing is doing.
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                zIndex = 10f,
            )
        }
    }
}

@Composable
private fun EndCap(id: String, position: LatLng, icon: BitmapDescriptor) {
    val state = remember(id) { MarkerState(position = position) }
    LaunchedEffect(position) { state.position = position }
    Marker(state = state, icon = icon, anchor = Offset(0.5f, 0.5f), zIndex = 1.5f, flat = true)
}

/**
 * Marker draw order (markers always render above polylines, so these only order markers among
 * themselves): emergency → regroup → destination → waypoint → start → riders.
 *
 * Stops outrank riders deliberately: they're the fixed landmarks the route is built from, and a
 * rider avatar drifting over the destination flag hides the one marker that can't be inferred from
 * anything else on screen.
 */
private fun zIndexFor(kind: MapMarkerKind): Float = when (kind) {
    MapMarkerKind.EMERGENCY -> 9f
    MapMarkerKind.REGROUP -> 8f
    MapMarkerKind.DESTINATION -> 7f
    MapMarkerKind.WAYPOINT -> 6.5f
    MapMarkerKind.START -> 6f
    MapMarkerKind.RIDER -> 4f
}

@Composable
private fun BakedMarker(marker: MapMarker, scale: Float, fontScale: Float, avatar: Bitmap?) {
    // Re-bake only when the marker's APPEARANCE changes, never on position. Riders move every
    // second; their artwork does not.
    val styleKey = remember(marker.kind, marker.label, marker.isMe, marker.isSelected, marker.isOffline, marker.distanceText, marker.regroupType, avatar) {
        listOf(marker.kind, marker.label, marker.isMe, marker.isSelected, marker.isOffline, marker.distanceText, marker.regroupType, avatar?.hashCode())
    }
    val baked = remember(styleKey, scale, fontScale) { bakeMarker(marker, scale, fontScale, avatar) }

    val state = remember(marker.id) { MarkerState(position = marker.position) }
    LaunchedEffect(marker.position) { state.position = marker.position }
    Marker(
        state = state,
        icon = baked.first,
        anchor = baked.second,
        zIndex = zIndexFor(marker.kind),
    )
}

private fun bakeMarker(marker: MapMarker, scale: Float, fontScale: Float, avatar: Bitmap?): Pair<BitmapDescriptor, Offset> =
    when (marker.kind) {
        MapMarkerKind.RIDER -> {
            val bmp = MapMarkerBitmaps.riderMarker(
                scale = scale,
                name = marker.label.orEmpty().ifEmpty { "Rider" },
                avatar = avatar,
                isMe = marker.isMe,
                isSelected = marker.isSelected,
                isOffline = marker.isOffline,
                distanceText = marker.distanceText,
                fontScale = fontScale,
            )
            // Anchored at the CIRCLE's centre — the name band sits above it, the distance below.
            BitmapDescriptorFactory.fromBitmap(bmp) to
                Offset(0.5f, MapMarkerBitmaps.riderAnchorY(marker.isSelected, marker.distanceText != null, fontScale))
        }
        MapMarkerKind.DESTINATION -> stopPin(scale, fontScale, "⚑", DsColor.mapEnd.toArgb(), 44f, marker.label)
        MapMarkerKind.WAYPOINT -> stopPin(scale, fontScale, "•", DsColor.mapStop.toArgb(), 32f, marker.label, filled = false)
        MapMarkerKind.START -> stopPin(scale, fontScale, "▲", DsColor.mapStart.toArgb(), 36f, marker.label, filled = false)
        MapMarkerKind.REGROUP -> {
            val type = marker.regroupType.orEmpty()
            // The caller rides in `label` so several stops on the map can be told apart.
            val stop = regroupLabel(type)
            stopPin(scale, fontScale, regroupGlyph(type), regroupAccent(type), 40f, marker.label?.let { "$stop · $it" } ?: stop, filled = false)
        }
        MapMarkerKind.EMERGENCY ->
            stopPin(scale, fontScale, "!", DsColor.danger.toArgb(), 40f, marker.label?.let { "Emergency · $it" } ?: "Emergency")
    }

private fun stopPin(
    scale: Float,
    fontScale: Float,
    glyph: String,
    accent: Int,
    circleDp: Float,
    label: String?,
    filled: Boolean = true,
): Pair<BitmapDescriptor, Offset> {
    val bmp = MapMarkerBitmaps.stopPin(scale, glyph, accent, circleDp, label, filled = filled, fontScale = fontScale)
    // Bottom-anchored at the pointer tip: the art above the coordinate is what the camera fit has
    // to reserve for (see NavCamera.STOP_PIN_TOP_EXTENT_DP).
    val tipY = (circleDp * scale + 6f * scale) / bmp.height
    return BitmapDescriptorFactory.fromBitmap(bmp) to Offset(0.5f, tipY)
}

private fun regroupGlyph(type: String) = when (type) {
    "FUEL" -> "⛽"
    "FOOD" -> "🍴"
    "SCENIC" -> "◉"
    else -> "✋"
}

private fun regroupAccent(type: String) = when (type) {
    "FUEL" -> DsColor.catFuel.toArgb()
    "FOOD" -> DsColor.catFood.toArgb()
    "SCENIC" -> DsColor.catScenic.toArgb()
    else -> DsColor.catOther.toArgb()
}

private fun regroupLabel(type: String) = when (type) {
    "FUEL" -> "Fuel stop"
    "FOOD" -> "Food stop"
    "SCENIC" -> "Scenic stop"
    else -> "Stop"
}

/**
 * A dotted quadratic bezier from [a] to [b], bowed 30% of the span perpendicular to it, so the
 * short hop between a stop's true location and the road the route starts on reads as a deliberate
 * connector rather than a kink in the route.
 */
@Composable
private fun CurvedConnector(a: LatLng, b: LatLng) {
    val points = remember(a, b) {
        // Flat-earth projection (degrees → approximately equal-scale space).
        val cosLat = cos(a.latitude * Math.PI / 180)
        val ax = a.longitude * cosLat; val ay = a.latitude
        val bx = b.longitude * cosLat; val by = b.latitude
        val dx = bx - ax; val dy = by - ay
        val len = Math.sqrt(dx * dx + dy * dy)
        if (len <= 0.0) {
            emptyList()
        } else {
            // Perpendicular unit vector (rotated 90° for a consistent arc direction).
            val px = dy / len; val py = -dx / len
            val offset = len * 0.30
            val cpx = (ax + bx) / 2 + px * offset
            val cpy = (ay + by) / 2 + py * offset
            (0..24).map { i ->
                val t = i / 24.0
                val t1 = 1.0 - t
                val qx = t1 * t1 * ax + 2 * t1 * t * cpx + t * t * bx
                val qy = t1 * t1 * ay + 2 * t1 * t * cpy + t * t * by
                LatLng(qy, qx / cosLat)
            }
        }
    }
    if (points.size < 2) return
    Polyline(
        points = points,
        color = DsColor.accent.copy(alpha = 0.75f),
        width = 4f,
        zIndex = 1.2f,
        pattern = listOf(Dash(10f), Gap(10f)),
    )
}

/**
 * Projects each off-screen rider to a clamped point on the screen border, along their true bearing
 * from the user.
 *
 * Uses the map's own `Projection` so it stays correct under the guidance camera's tilt, heading and
 * content padding. Emits nothing when there is no user position or the map has no size yet.
 */
private fun computeEdgeIndicators(
    cameraPositionState: CameraPositionState,
    markers: List<MapMarker>,
    user: UserPuck?,
    widthPx: Int,
    heightPx: Int,
    topInsetPx: Float,
    scale: Float,
): List<EdgeIndicator> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val projection = cameraPositionState.projection ?: return emptyList()
    val userPos = user?.position ?: return emptyList()
    // A bad "me" fix would make every rider's distance wrong at once, so bail rather than publish a
    // screenful of nonsense.
    if (isUnsetCoordinate(userPos.latitude, userPos.longitude)) return emptyList()

    val bearing = cameraPositionState.position.bearing

    // The clamp positions the chip's CENTRE, and its avatar sits above that — the chip is ~54dp
    // tall (34 avatar + 3 gap + 17 label), so the top inset must clear half the chip plus a margin
    // on top of whatever chrome the caller reported.
    val top = if (topInsetPx > 0) topInsetPx + 32 * scale else 96 * scale
    val side = 46 * scale
    val bottom = heightPx - 32 * scale
    val left = side
    val right = widthPx - side
    if (right <= left || bottom <= top) return emptyList()

    val rawOrigin = projection.toScreenLocation(userPos)
    val originX = rawOrigin.x.toFloat().coerceIn(left, right)
    val originY = rawOrigin.y.toFloat().coerceIn(top, bottom)

    val visible = projection.visibleRegion.latLngBounds

    return markers.mapNotNull { marker ->
        // Riders other than me, plus the two places a rider is being asked to go.
        val wanted = (marker.kind == MapMarkerKind.RIDER && !marker.isMe) ||
            marker.kind == MapMarkerKind.REGROUP || marker.kind == MapMarkerKind.EMERGENCY
        if (!wanted) return@mapNotNull null
        val pos = marker.position
        if (isUnsetCoordinate(pos.latitude, pos.longitude)) return@mapNotNull null
        if (visible.contains(pos)) return@mapNotNull null   // on-screen → no chip

        // Relative heading: 0 = straight ahead (up). Screen y is down.
        val theta = Math.toRadians(bearingTo(userPos, pos) - bearing)
        val dx = Math.sin(theta).toFloat()
        val dy = (-Math.cos(theta)).toFloat()

        val exit = rayRectExit(originX, originY, dx, dy, left, top, right, bottom)
            ?: return@mapNotNull null

        val out = FloatArray(1)
        android.location.Location.distanceBetween(
            userPos.latitude, userPos.longitude, pos.latitude, pos.longitude, out,
        )
        EdgeIndicator(marker.id, exit.first, exit.second, out[0].toDouble())
    }
}

private fun bearingTo(from: LatLng, to: LatLng): Double {
    val out = FloatArray(2)
    android.location.Location.distanceBetween(
        from.latitude, from.longitude, to.latitude, to.longitude, out,
    )
    return ((out[1] + 360) % 360).toDouble()
}

/**
 * Thins a polyline for DRAWING, without moving it off the road.
 *
 * A stored route for a long ride is enormous — a 1438 km ride decodes to ~33,000 vertices — and
 * the Maps SDK silently declines to draw a polyline that large: the line simply never appears,
 * with no error. So some thinning is unavoidable.
 *
 * Two things it must not do, both of which cost the route its shape:
 *
 * 1. **Drop vertices by position.** Keeping every nth point is a fine way to halve a dense
 *    straight line and a terrible way to draw a corner: the vertices that define a turn are
 *    exactly the ones that carry the shape, and a uniform stride is as likely to discard them as
 *    any other. It is worse in motion than that sounds — the line is re-cut under the rider every
 *    fix, so the sampling phase shifts each second and a different set of corners is flattened
 *    each time. Douglas-Peucker instead, with the tolerance in metres, so a vertex goes only when
 *    the line drawn without it passes within [toleranceMeters] of where it sat.
 *
 * 2. **Spend one whole-route budget on a line viewed at two scales.** This is what still drew
 *    straight chords across bends after the switch to Douglas-Peucker. A 1438 km route thinned to
 *    3,000 points is one vertex every 480 m; the guidance camera shows about 200 m of road. Every
 *    vertex in the only part the rider can see had been spent on the 1437 km they cannot. The
 *    budget is not wrong, it is just the wrong thing to ration by distance-from-the-camera.
 *
 * So [headDetailMeters] of the line, measured from its head, is kept at FULL resolution and any
 * thinning applies only beyond it. During navigation the head is the rider — the trim in
 * `NavRouteEngine` re-cuts the route to start underneath the chevron — so this is exactly the
 * stretch on screen, and it stays lane-accurate however long the ride is and however far the
 * rider pinches in. Passing 0 (the preview, and the dim planned layer whose head is the ride's
 * start rather than the camera) thins uniformly instead, which is fine now that the tolerance is
 * below what the source data can represent.
 *
 * DISPLAY ONLY. Navigation measures progress, off-route distance and step positions against the
 * full-resolution route in `NavRouteEngine` — simplifying that would move every turn.
 */
private fun simplifyForDisplay(
    points: List<LatLng>,
    maxPoints: Int = MAX_DRAWN_POINTS,
    toleranceMeters: Double = DRAW_SIMPLIFY_TOLERANCE_METERS,
    headDetailMeters: Double = 0.0,
): List<LatLng> {
    if (points.size <= maxPoints) return points

    if (headDetailMeters > 0) {
        val split = indexAfterMeters(points, headDetailMeters, maxPoints / 2)
        if (split > 1) {
            val near = points.subList(0, split)
            val far = points.subList(split - 1, points.size)   // shares a vertex, so the join is seamless
            val farBudget = maxOf(2, maxPoints - near.size)
            return near + thinToBudget(far, farBudget, toleranceMeters).drop(1)
        }
    }
    return thinToBudget(points, maxPoints, toleranceMeters)
}

/** Douglas-Peucker at an escalating tolerance until the result fits [maxPoints]. */
private fun thinToBudget(points: List<LatLng>, maxPoints: Int, toleranceMeters: Double): List<LatLng> {
    if (points.size <= maxPoints) return points
    var tolerance = toleranceMeters
    // Doubling rather than a solve: each pass is fast, and reaching this path at all means the
    // route is larger than any ride yet recorded.
    var result = douglasPeucker(points, tolerance)
    while (result.size > maxPoints && tolerance < 512) {
        tolerance *= 2
        result = douglasPeucker(points, tolerance)
    }
    return result
}

/** Index one past the last vertex within [meters] of the head, capped at [maxIndex]. */
private fun indexAfterMeters(points: List<LatLng>, meters: Double, maxIndex: Int): Int {
    var travelled = 0.0
    var i = 1
    while (i < points.size && i < maxIndex) {
        travelled += metersBetween(points[i - 1], points[i])
        if (travelled >= meters) return i + 1
        i++
    }
    return i
}

/**
 * Ramer-Douglas-Peucker in a local equirectangular frame anchored at the route's midpoint.
 *
 * Iterative rather than recursive: a 33,000-vertex route on a nearly straight motorway is the
 * pathological input for the recursive form, and blowing the stack while drawing a map is not a
 * failure mode worth keeping for the sake of six fewer lines.
 */
private fun douglasPeucker(points: List<LatLng>, toleranceMeters: Double): List<LatLng> {
    if (points.size < 3) return points

    val anchor = points[points.size / 2]
    val metresPerLat = 111_132.0
    val metresPerLng = 111_320.0 * cos(anchor.latitude * Math.PI / 180)
    val xs = DoubleArray(points.size) { (points[it].longitude - anchor.longitude) * metresPerLng }
    val ys = DoubleArray(points.size) { (points[it].latitude - anchor.latitude) * metresPerLat }

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true

    val toleranceSq = toleranceMeters * toleranceMeters
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        if (last <= first + 1) continue
        val ax = xs[first]; val ay = ys[first]
        val dx = xs[last] - ax; val dy = ys[last] - ay
        val lenSq = dx * dx + dy * dy

        var worst = -1
        var worstSq = toleranceSq
        for (i in (first + 1) until last) {
            val px = xs[i] - ax; val py = ys[i] - ay
            // Distance to the SEGMENT, clamped — an unclamped line distance understates the error
            // for a vertex sitting beyond an endpoint, which is how a hairpin gets flattened.
            val t = if (lenSq < 1e-9) 0.0 else ((px * dx + py * dy) / lenSq).coerceIn(0.0, 1.0)
            val ex = px - t * dx; val ey = py - t * dy
            val distSq = ex * ex + ey * ey
            if (distSq > worstSq) { worstSq = distSq; worst = i }
        }
        if (worst >= 0) {
            keep[worst] = true
            stack.addLast(first to worst)
            stack.addLast(worst to last)
        }
    }

    val out = ArrayList<LatLng>(points.size / 2)
    for (i in points.indices) if (keep[i]) out.add(points[i])
    return out
}

/** Snapshot of everything the edge-indicator pass reads, so it can be conflated as one value. */
private data class EdgeInputs(
    val camera: CameraPosition,
    val markers: List<MapMarker>,
    val user: UserPuck?,
    val widthPx: Int,
    val heightPx: Int,
)

