package com.danglingmind.convotrack.data.maps

import com.danglingmind.convotrack.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Ported from iOS GooglePlacesService.swift — Places Text Search REST API. Used to geocode
// the free-text start/waypoint/destination fields in Create Ride into coordinates.

data class PlaceResult(
    val id: String,
    val name: String,
    val latLng: LatLng,
    val address: String?,
)

/** The quick searches riders reach for on a ride: fuel, food and somewhere worth stopping (iOS
 *  PlaceCategory). [query] is what's sent to Places — the words a rider would type. */
enum class PlaceCategory(val title: String, val query: String) {
    FUEL("Fuel", "petrol pump"),
    FOOD("Food", "restaurant"),
    VIEWPOINT("Viewpoints", "viewpoint"),
}

/** A place found along the route, with how much longer the ride gets for stopping there. Null
 *  when it can't be worked out (a route that already has stops between start and destination). */
data class PlaceSearchHit(val place: PlaceResult, val detourSeconds: Double? = null)

object PlacesService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, near: LatLng? = null): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val urlBuilder = "https://maps.googleapis.com/maps/api/place/textsearch/json".toHttpUrl()
            .newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("key", BuildConfig.MAPS_API_KEY)
        if (near != null) {
            urlBuilder.addQueryParameter("location", "${near.latitude},${near.longitude}")
            urlBuilder.addQueryParameter("radius", "50000")
        }

        val request = Request.Builder().url(urlBuilder.build()).build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val text = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString(PlacesResponse.serializer(), text)
                parsed.results.map {
                    PlaceResult(
                        id = it.placeId,
                        name = it.name,
                        latLng = LatLng(it.geometry.location.lat, it.geometry.location.lng),
                        address = it.formattedAddress,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

// MARK: - Places API (New), Geocoding

/** Text search biased to a circle around [center] (Places API New), for the category chips. */
suspend fun PlacesService.searchText(query: String, center: LatLng?, radiusMeters: Double = 15_000.0): List<PlaceResult> {
    val body = JSONObject().apply {
        put("textQuery", query)
        put("maxResultCount", 10)
        put("languageCode", "en")
        if (center != null) {
            put("locationBias", JSONObject().put("circle", JSONObject().apply {
                put("center", JSONObject().put("latitude", center.latitude).put("longitude", center.longitude))
                put("radius", radiusMeters)
            }))
        }
    }
    return PlacesNew.search(body, PlacesNew.PLACE_FIELDS)?.places.orEmpty().mapNotNull { it.toResult() }
}

/**
 * Places near the route, ranked by Google for how little they take the ride off it
 * (`searchAlongRouteParameters`). With [measureDetours], each hit carries its detour: the time to it
 * from the start plus on to the destination, less the route on its own.
 *
 * Both sides of that subtraction are traffic-free. Places times its legs without traffic whatever
 * `routingPreference` says (measured: identical figures either way), so the route is re-timed
 * traffic-free to match — against a traffic-aware time every detour came out ~12 minutes short.
 */
suspend fun PlacesService.searchAlongRoute(
    query: String,
    route: List<LatLng>,
    measureDetours: Boolean,
    avoid: RouteAvoidances = RouteAvoidances.NONE,
): List<PlaceSearchHit> = coroutineScope {
    if (route.size < 2) return@coroutineScope emptyList()
    val origin = route.first()
    // An even sample keeps the request small; a few hundred points is plenty for "near the route".
    val count = minOf(route.size, 400)
    val sampled = (0 until count).map { route[(route.size - 1) * it / maxOf(count - 1, 1)] }
    val baseline = if (measureDetours) async {
        runCatching { DirectionsService.routeThrough(listOf(origin, route.last()), trafficAware = false, avoid = avoid).durationSeconds }
            .getOrNull()
    } else null
    val body = JSONObject().apply {
        put("textQuery", query)
        put("maxResultCount", 10)
        put("languageCode", "en")
        put("searchAlongRouteParameters", JSONObject().put("polyline",
            JSONObject().put("encodedPolyline", DirectionsService.encodePolyline(sampled))))
        put("routingParameters", JSONObject().put("origin",
            JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude)))
    }
    val response = PlacesNew.search(body, PlacesNew.PLACE_FIELDS + ",routingSummaries")
        ?: return@coroutineScope emptyList()
    val direct = baseline?.await()
    response.places.orEmpty().mapIndexedNotNull { i, item ->
        val place = item.toResult() ?: return@mapIndexedNotNull null
        val legs = response.routingSummaries?.getOrNull(i)?.legs.orEmpty()
        val detour = if (direct != null && legs.isNotEmpty()) {
            maxOf(0.0, legs.sumOf { PlacesNew.parseDuration(it.duration) } - direct)
        } else null
        PlaceSearchHit(place, detour)
    }
}

/**
 * The street-level place at a coordinate, named the way a rider would say it ("KG Halli, D' Souza
 * Layout") rather than as a full postal address. Gives "Your location" a real name before it is
 * saved as a ride's start: every rider reads that name, and to them "Your location" means their own.
 */
suspend fun PlacesService.reverseGeocode(at: LatLng): PlaceResult? = withContext(Dispatchers.IO) {
    val url = "https://maps.googleapis.com/maps/api/geocode/json".toHttpUrl().newBuilder()
        .addQueryParameter("latlng", "${at.latitude},${at.longitude}")
        .addQueryParameter("result_type", "street_address|route|premise|sublocality|locality")
        .addQueryParameter("key", BuildConfig.MAPS_API_KEY)
        .build()
    runCatching {
        PlacesNew.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val first = PlacesNew.json.decodeFromString(GeocodeResponse.serializer(), resp.body?.string().orEmpty())
                .results.firstOrNull() ?: return@withContext null
            PlaceResult(id = first.placeId, name = shortPlaceName(first.formattedAddress), latLng = at, address = first.formattedAddress)
        }
    }.getOrNull()
}

/** The first two parts of an address that aren't house or plot numbers ("67/1"), or Google's
 *  "Unnamed Road", which names nothing a rider could look for. */
fun shortPlaceName(address: String): String {
    val parts = address.split(",").map { it.trim() }
    val named = parts.filter { part ->
        part.count { it.isDigit() } * 2 < part.length && !part.equals("Unnamed Road", ignoreCase = true)
    }
    return (named.ifEmpty { parts }).take(2).joinToString(", ")
}

private object PlacesNew {
    val client = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true }
    const val PLACE_FIELDS = "places.id,places.displayName,places.formattedAddress,places.location"

    suspend fun search(body: JSONObject, fieldMask: String): NewPlacesResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places:searchText")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            .addHeader("X-Goog-FieldMask", fieldMask)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString(NewPlacesResponse.serializer(), resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /** Places API durations come as "383s". */
    fun parseDuration(raw: String?): Double = raw?.removeSuffix("s")?.toDoubleOrNull() ?: 0.0
}

@Serializable
private data class NewPlacesResponse(
    val places: List<Item>? = null,
    val routingSummaries: List<RoutingSummary>? = null,
) {
    @Serializable
    data class Item(
        val id: String,
        val displayName: LocalizedText? = null,
        val formattedAddress: String? = null,
        val location: Location? = null,
    ) {
        fun toResult(): PlaceResult? {
            val loc = location ?: return null
            val name = displayName?.text ?: return null
            return PlaceResult(id = id, name = name, latLng = LatLng(loc.latitude, loc.longitude), address = formattedAddress)
        }
    }

    @Serializable data class LocalizedText(val text: String)
    @Serializable data class Location(val latitude: Double, val longitude: Double)
    @Serializable data class RoutingSummary(val legs: List<Leg>? = null)
    @Serializable data class Leg(val duration: String? = null)
}

@Serializable
private data class GeocodeResponse(val results: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        @SerialName("place_id") val placeId: String,
        @SerialName("formatted_address") val formattedAddress: String,
    )
}

@Serializable
private data class PlacesResponse(
    val results: List<PlaceItem> = emptyList(),
) {
    @Serializable
    data class PlaceItem(
        @SerialName("place_id") val placeId: String,
        val name: String,
        val geometry: Geometry,
        @SerialName("formatted_address") val formattedAddress: String? = null,
    )

    @Serializable
    data class Geometry(val location: LatLng)

    @Serializable
    data class LatLng(val lat: Double, val lng: Double)
}
