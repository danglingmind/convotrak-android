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
    val end: LatLng,
    val distanceMeters: Double,
)

data class DirectionsResult(
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<DirectionsStep>,
)

object DirectionsService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** trafficAware=true for planning; false for fast reroutes. */
    suspend fun route(
        origin: LatLng,
        destination: LatLng,
        trafficAware: Boolean = true,
    ): DirectionsResult = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("origin", latLngNode(origin))
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
            .addHeader(
                "X-Goog-FieldMask",
                "routes.legs.distanceMeters,routes.legs.duration," +
                    "routes.legs.steps.distanceMeters,routes.legs.steps.endLocation," +
                    "routes.legs.steps.navigationInstruction,routes.legs.steps.polyline",
            )
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Directions HTTP ${resp.code}")
            val parsed = json.decodeFromString(RoutesResponse.serializer(), text)
            val leg = parsed.routes.firstOrNull()?.legs?.firstOrNull()
                ?: error("No route found")

            val coords = leg.steps.flatMap { decodePolyline(it.polyline.encodedPolyline) }
            val steps = leg.steps.mapNotNull { step ->
                val end = step.endLocation?.latLng ?: return@mapNotNull null
                DirectionsStep(
                    instruction = step.navigationInstruction?.instructions.orEmpty(),
                    end = LatLng(end.latitude, end.longitude),
                    distanceMeters = (step.distanceMeters ?: 0).toDouble(),
                )
            }
            DirectionsResult(
                coordinates = coords,
                distanceMeters = leg.distanceMeters.toDouble(),
                durationSeconds = parseDuration(leg.duration),
                steps = steps,
            )
        }
    }

    private fun latLngNode(c: LatLng) = JSONObject().apply {
        put("location", JSONObject().apply {
            put("latLng", JSONObject().apply {
                put("latitude", c.latitude)
                put("longitude", c.longitude)
            })
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
