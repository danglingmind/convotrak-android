package com.danglingmind.convotrack.data.maps

import com.danglingmind.convotrack.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Ported from iOS GoogleDirectionsService.swift. Uses the Routes API v2 (computeRoutes) —
// same endpoint, headers, field mask, and per-step HIGH_QUALITY polyline stitching.

data class DirectionsStep(
    val instruction: String,
    /** Routes API `navigationInstruction.maneuver` — drives the banner arrow, so the arrow can't
     *  disagree with the instruction text. Null when the step arrives without one. */
    val maneuver: String?,
    val end: LatLng,
    val distanceMeters: Double,
    /** Distance from the route's start to this step's end, measured ALONG the drawn polyline.
     *  Stamped by [com.danglingmind.convotrack.ui.ride.stampAlongRouteDistances] once the geometry
     *  is known — this is what makes step tracking self-correcting. */
    val endDistanceAlongRoute: Double = 0.0,
)

data class DirectionsResult(
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<DirectionsStep>,
)

/** One selectable alternative for a direct origin→destination route (iOS RouteOption). */
data class RouteOption(
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val label: String?,   // Routes API routeLabels, e.g. DEFAULT_ROUTE / FUEL_EFFICIENT
    /** Routes API `description`: the main road, e.g. "NH44". For telling alternatives apart. */
    val summary: String? = null,
)

/**
 * Road types the leader asked to keep off (Routes API `routeModifiers`) — iOS RouteAvoidances.
 * Planning options, not stored with the ride: the route they produce is what's saved.
 */
data class RouteAvoidances(val highways: Boolean = false, val tolls: Boolean = false) {
    val isEmpty: Boolean get() = !highways && !tolls

    /** The `routeModifiers` object, or null when nothing is avoided. */
    fun requestValue(): JSONObject? =
        if (isEmpty) null else JSONObject().apply {
            put("avoidHighways", highways)
            put("avoidTolls", tolls)
        }

    companion object {
        val NONE = RouteAvoidances()
    }
}

object DirectionsService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Only the fields we actually use, so the response stays small. `navigationInstruction`
     *  carries both the text and the maneuver enum. */
    private const val STEP_FIELD_MASK =
        "routes.legs.distanceMeters,routes.legs.duration," +
            "routes.legs.steps.distanceMeters,routes.legs.steps.endLocation," +
            "routes.legs.steps.navigationInstruction,routes.legs.steps.polyline"

    /**
     * trafficAware=true for planning; false for fast reroutes.
     *
     * [originHeading] is the direction of travel at [origin], in degrees clockwise from north. The
     * Routes API uses it to pick the side of the road the vehicle is on, which is the difference
     * between a reroute that starts with a U-turn and one that assumes the rider can simply set off
     * back the way they came. Omit it whenever the caller has no trustworthy course — a stale or
     * invented heading pins the route to the wrong carriageway.
     */
    suspend fun route(
        origin: LatLng,
        destination: LatLng,
        trafficAware: Boolean = true,
        originHeading: Double? = null,
    ): DirectionsResult = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("origin", latLngNode(origin, originHeading))
            put("destination", latLngNode(destination))
            put("travelMode", "DRIVE")
            put("routingPreference", if (trafficAware) "TRAFFIC_AWARE" else "TRAFFIC_UNAWARE")
            put("polylineQuality", "HIGH_QUALITY")
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-US")
            put("units", "METRIC")
        }.toString()

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            .addHeader("X-Goog-FieldMask", STEP_FIELD_MASK)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Directions HTTP ${resp.code}")
            val parsed = json.decodeFromString(RoutesResponse.serializer(), text)
            val leg = parsed.routes.firstOrNull()?.legs?.firstOrNull()
                ?: error("No route found")
            legResult(leg)
        }
    }

    /**
     * One route through every stop in order (start, any middle stops, destination), in a single
     * request with `intermediates` (iOS `route(through:)`). Requesting each leg on its own and
     * stitching them cost one call per leg and let each leg be routed without knowing how the rider
     * arrived at its start. Google returns no alternatives once intermediates are present.
     */
    suspend fun routeThrough(
        stops: List<LatLng>,
        trafficAware: Boolean = true,
        avoid: RouteAvoidances = RouteAvoidances.NONE,
    ): DirectionsResult = withContext(Dispatchers.IO) {
        require(stops.size >= 2) { "A route needs a start and a destination" }
        val bodyJson = JSONObject().apply {
            put("origin", latLngNode(stops.first()))
            put("destination", latLngNode(stops.last()))
            val middle = stops.subList(1, stops.size - 1)
            if (middle.isNotEmpty()) {
                put("intermediates", org.json.JSONArray().apply { middle.forEach { put(latLngNode(it)) } })
            }
            put("travelMode", "DRIVE")
            put("routingPreference", if (trafficAware) "TRAFFIC_AWARE" else "TRAFFIC_UNAWARE")
            put("polylineQuality", "HIGH_QUALITY")
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-US")
            put("units", "METRIC")
            avoid.requestValue()?.let { put("routeModifiers", it) }
        }.toString()

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            .addHeader("X-Goog-FieldMask", STEP_FIELD_MASK)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Directions HTTP ${resp.code}")
            val parsed = json.decodeFromString(RoutesResponse.serializer(), text)
            val legs = parsed.routes.firstOrNull()?.legs.orEmpty().map { legResult(it) }
            if (legs.isEmpty()) error("No route found")
            // Each leg starts on the point the previous one ended on; drop that shared vertex.
            val coords = mutableListOf<LatLng>()
            legs.forEachIndexed { i, leg -> coords += if (i == 0) leg.coordinates else leg.coordinates.drop(1) }
            DirectionsResult(
                coordinates = coords,
                distanceMeters = legs.sumOf { it.distanceMeters },
                durationSeconds = legs.sumOf { it.durationSeconds },
                steps = legs.flatMap { it.steps },
            )
        }
    }

    /**
     * The same alternatives [alternativeRoutes] offers the ride leader, but carrying each one's
     * turn-by-turn steps and stitched geometry.
     *
     * Navigation needs this because the leader's pick is persisted as a bare polyline: replaying
     * it later tells you the shape of the chosen road but not a single instruction for it, and a
     * plain [route] recompute answers with whatever Google ranks first today — which is a
     * different road entirely whenever the leader chose anything but the default. Asking for the
     * alternative set with steps attached lets the caller find the one that matches the stored
     * shape and take its instructions (iOS `alternativeRoutesWithSteps`).
     *
     * [alternativeRoutes] deliberately does NOT ask for step data: it feeds the route picker,
     * which only ever draws whole-route polylines, and the per-step fields multiply that response
     * several times over for nothing.
     */
    suspend fun alternativeRoutesWithSteps(
        origin: LatLng,
        destination: LatLng,
        trafficAware: Boolean = true,
        avoidHighways: Boolean = false,
        avoidTolls: Boolean = false,
    ): List<DirectionsResult> = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("origin", latLngNode(origin))
            put("destination", latLngNode(destination))
            put("travelMode", "DRIVE")
            put("routingPreference", if (trafficAware) "TRAFFIC_AWARE" else "TRAFFIC_UNAWARE")
            put("polylineQuality", "HIGH_QUALITY")
            put("computeAlternativeRoutes", true)
            put("languageCode", "en-US")
            put("units", "METRIC")
            if (avoidHighways || avoidTolls) {
                put("routeModifiers", JSONObject().apply {
                    put("avoidHighways", avoidHighways)
                    put("avoidTolls", avoidTolls)
                })
            }
        }.toString()

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            .addHeader("X-Goog-FieldMask", STEP_FIELD_MASK)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Directions HTTP ${resp.code}")
            val parsed = json.decodeFromString(RoutesResponse.serializer(), text)
            parsed.routes.mapNotNull { it.legs.firstOrNull() }.map { legResult(it) }
        }
    }

    private fun legResult(leg: RoutesResponse.Leg): DirectionsResult {
        // Stitch per-step HIGH_QUALITY polylines for lane-accurate geometry.
        val coords = leg.steps.flatMap { decodePolyline(it.polyline.encodedPolyline) }
        // Every step is kept, including any without instruction text: they still occupy real
        // distance, and dropping them breaks the chain of along-route positions. The banner walks
        // past the textless ones for display.
        val steps = leg.steps.mapNotNull { step ->
            val end = step.endLocation?.latLng ?: return@mapNotNull null
            DirectionsStep(
                instruction = step.navigationInstruction?.instructions.orEmpty(),
                maneuver = step.navigationInstruction?.maneuver,
                end = LatLng(end.latitude, end.longitude),
                distanceMeters = (step.distanceMeters ?: 0).toDouble(),
            )
        }
        return DirectionsResult(
            coordinates = coords,
            distanceMeters = leg.distanceMeters.toDouble(),
            durationSeconds = parseDuration(leg.duration),
            steps = steps,
        )
    }

    /**
     * Alternative whole-trip routes for a DIRECT origin→destination request. Google's Routes API
     * returns no alternatives once intermediate waypoints are present, so callers use this only for
     * the no-middle-stop case (iOS GoogleDirectionsService.alternativeRoutes).
     */
    suspend fun alternativeRoutes(
        origin: LatLng,
        destination: LatLng,
        trafficAware: Boolean = true,
        avoid: RouteAvoidances = RouteAvoidances.NONE,
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("origin", latLngNode(origin))
            put("destination", latLngNode(destination))
            put("travelMode", "DRIVE")
            put("routingPreference", if (trafficAware) "TRAFFIC_AWARE" else "TRAFFIC_UNAWARE")
            put("polylineQuality", "HIGH_QUALITY")
            put("computeAlternativeRoutes", true)
            put("languageCode", "en-US")
            put("units", "METRIC")
            avoid.requestValue()?.let { put("routeModifiers", it) }
        }.toString()

        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            // Whole-route fields (one polyline per alternative) — no per-step data needed here.
            .addHeader(
                "X-Goog-FieldMask",
                "routes.polyline.encodedPolyline,routes.distanceMeters," +
                    "routes.duration,routes.routeLabels,routes.description",
            )
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Directions HTTP ${resp.code}")
            val parsed = json.decodeFromString(RoutesResponse.serializer(), text)
            parsed.routes.mapNotNull { route ->
                val encoded = route.polyline?.encodedPolyline ?: return@mapNotNull null
                RouteOption(
                    coordinates = decodePolyline(encoded),
                    distanceMeters = (route.distanceMeters ?: 0).toDouble(),
                    durationSeconds = parseDuration(route.duration ?: "0s"),
                    label = route.routeLabels.firstOrNull(),
                    summary = route.description?.let(::shortRouteDescription),
                )
            }
        }
    }

    /**
     * Decodes a stored route polyline into coordinates, or null if absent/too short to draw. Ride
     * views render the leader-selected route with this instead of recomputing a default (iOS
     * GoogleDirectionsService.decodedRoute) — so every rider sees the same geometry.
     */
    /** One name per road: Google lists a road's aliases with a slash ("Doddaballapur Rd/
     *  Doddaballapura Main Rd"), which only makes the label too long to read at a glance. */
    private fun shortRouteDescription(raw: String): String? {
        val roads = raw.split(" and ")
            .map { it.substringBefore("/").trim() }
            .filter { it.isNotEmpty() }
        return roads.takeIf { it.isNotEmpty() }?.joinToString(" and ")
    }

    fun decodedRoute(polyline: String?): List<LatLng>? {
        if (polyline.isNullOrEmpty()) return null
        val coords = decodePolyline(polyline)
        return if (coords.size >= 2) coords else null
    }

    private fun latLngNode(c: LatLng, heading: Double? = null) = JSONObject().apply {
        put("location", JSONObject().apply {
            put("latLng", JSONObject().apply {
                put("latitude", c.latitude)
                put("longitude", c.longitude)
            })
            // Routes API takes the heading as a whole number of degrees in 0..359.
            if (heading != null && heading.isFinite()) {
                put("heading", ((Math.round(heading) % 360 + 360) % 360).toInt())
            }
        })
    }

    // Routes API duration is "123s".
    private fun parseDuration(raw: String): Double =
        (if (raw.endsWith("s")) raw.dropLast(1) else raw).toDoubleOrNull() ?: 0.0

    // MARK: - Google encoded polyline codec (standard algorithm)

    fun decodePolyline(encoded: String): List<LatLng> {
        val coords = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            coords.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return coords
    }

    fun encodePolyline(coordinates: List<LatLng>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        for (c in coordinates) {
            val lat = Math.round(c.latitude * 1e5).toInt()
            val lng = Math.round(c.longitude * 1e5).toInt()
            sb.append(encodeValue(lat - prevLat))
            sb.append(encodeValue(lng - prevLng))
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int): String {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        val sb = StringBuilder()
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
        return sb.toString()
    }
}
