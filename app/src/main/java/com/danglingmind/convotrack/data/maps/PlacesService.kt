package com.danglingmind.convotrack.data.maps

import com.danglingmind.convotrack.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// Ported from iOS GooglePlacesService.swift — Places Text Search REST API. Used to geocode
// the free-text start/waypoint/destination fields in Create Ride into coordinates.

data class PlaceResult(
    val id: String,
    val name: String,
    val latLng: LatLng,
    val address: String?,
)

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
