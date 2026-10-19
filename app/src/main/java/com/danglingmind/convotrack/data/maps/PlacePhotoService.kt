package com.danglingmind.convotrack.data.maps

import android.graphics.Bitmap
import com.danglingmind.convotrack.BuildConfig
import com.danglingmind.convotrack.data.imagecache.ImageCache
import com.danglingmind.convotrack.data.imagecache.ImageCategory
import com.danglingmind.convotrack.data.imagecache.ImageHttp
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/** A representative photo for a ride's destination — the image Google Maps shows for that place. */
data class DestinationPhoto(
    val bitmap: Bitmap,
    /**
     * Photographer credit returned alongside the photo. Google's Places policy requires this to be
     * displayed wherever the photo appears, so it travels WITH the image rather than being an
     * optional extra a screen can forget to render.
     */
    val attribution: String?,
)

/**
 * What a destination-photo hero should currently show.
 *
 * Three cases rather than a nullable bitmap because "still resolving" and "there is no photo" have
 * to look different: collapsing them means the fallback (the route map) appears for a beat and is
 * then replaced by the photo, which reads as a glitch every time a ride is opened.
 */
sealed interface DestinationHeroPhase {
    /** Nothing known yet, or a lookup is in flight. Callers show neither photo nor fallback. */
    data object Resolving : DestinationHeroPhase

    data class Photo(val photo: DestinationPhoto) : DestinationHeroPhase

    /** No photo for this place, or the lookup failed — callers show their fallback. */
    data object Unavailable : DestinationHeroPhase
}


/**
 * Fetches the photo Google Maps shows for a destination, for the ride lobby's hero image, the
 * history thumbnails and the home screen's active-ride card. Android port of iOS
 * `GooglePlacePhotoService.swift`.
 *
 * The backend stores a destination as name + coordinate only (no place ID), so this resolves the
 * place itself: a Text Search biased to that coordinate, then the nearest candidate's first photo.
 * Uses the CURRENT Places API (`places.googleapis.com`, field-masked so we're billed only for the
 * fields we read) rather than the legacy endpoint [PlacesService] still uses — legacy Places is
 * closed to new customers and on a sunset path.
 *
 * Storage, de-duplication, decoding, expiry and eviction are [ImageCache]'s ([ImageCategory.PLACE_PHOTO]:
 * 30-day photos, 7-day "no photo" markers). This file owns only the two billable network calls and
 * the cache key. The photo reference from the search is stored with the entry, so once a photo
 * expires it is re-requested directly (conditionally, if the server gave validators) instead of
 * paying for the search again. The photographer credit is stored with it and returned with every
 * hit — Google's policy requires it wherever the photo is shown.
 *
 * EVERY failure path returns null — empty name, no match, a match too far away, a place with no
 * photos, an HTTP error, a decode failure, non-image bytes — because the caller's contract is
 * "show the photo if there is one, otherwise fall back to the route map". Nothing here throws.
 */
object PlacePhotoService {

    /**
     * Longest edge we ever hold decoded. The lobby hero is ~411×360dp, so at 3× the most that can
     * be seen is ~1230px; beyond ~1200 extra pixels cost memory and show nothing. A 16:9 photo at
     * this size decodes to ~3MB — the 4000×2250 original Google serves would be 36MB.
     *
     * Applied to the LONGEST edge, so a portrait photo is bounded by its height too and the aspect
     * ratio is always preserved (the lobby shows the whole frame, so a crop here would undo that).
     */
    const val MAX_DECODED_PIXEL_SIZE = 1200

    /**
     * How far a candidate may sit from the stored destination and still count as the same place.
     * Generous on purpose: a large destination (a hill, a park, a beach) has a centroid well away
     * from whichever point the ride leader tapped. Tight enough to reject a same-named place in
     * another city, which is the failure this guard exists for.
     */
    private const val MAX_CANDIDATE_DISTANCE_M = 5_000.0

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private data class Candidate(val photoName: String, val attribution: String?)

    /** Outcome of the place search, kept distinct so a flaky network isn't recorded as "no photo". */
    private sealed interface Search {
        data class Found(val candidate: Candidate) : Search
        data object None : Search
        data object Error : Search
    }

    /**
     * [ownerRideId] tags the cached photo with the ride it was shown for, so deleting or leaving
     * that ride can drop it (the key itself is per destination, so two rides to one place share it).
     */
    suspend fun photo(destinationName: String, coordinate: LatLng, ownerRideId: String? = null): DestinationPhoto? {
        val query = destinationName.trim()
        if (query.isEmpty()) return null
        val loaded = ImageCache.load(
            ImageCategory.PLACE_PHOTO, cacheKey(query, coordinate), MAX_DECODED_PIXEL_SIZE, ownerRideId,
        ) { reval -> fetch(query, coordinate, reval) } ?: return null
        return DestinationPhoto(loaded.bitmap, loaded.extra)
    }

    /**
     * Coordinates are rounded to ~100 m so tiny differences in an otherwise identical destination
     * still hit the same entry, while a genuinely edited destination (a new name, or a move of more
     * than ~100 m) produces a new key and therefore a refetch — implicit invalidation, with nothing
     * to remember to call. The size is part of the key because it changes what is downloaded.
     */
    private fun cacheKey(query: String, coordinate: LatLng): String {
        val lat = (coordinate.latitude * 1000).roundToInt() / 1000.0
        val lng = (coordinate.longitude * 1000).roundToInt() / 1000.0
        return "${query.lowercase()}|$lat,$lng|$MAX_DECODED_PIXEL_SIZE"
    }

    // MARK: - Network

    private suspend fun fetch(query: String, coordinate: LatLng, reval: ImageCache.Revalidation?): ImageCache.Fetch {
        // An expired entry already knows which photo it is: ask for that one again (conditionally)
        // and skip the billable search. If the reference no longer resolves, fall through and search.
        val ref = reval?.ref
        if (ref != null) {
            when (val r = photoData(ref, reval)) {
                is ImageCache.Fetch.Bytes -> return r.copy(extra = reval.extra, ref = ref)
                ImageCache.Fetch.NotModified, ImageCache.Fetch.Failed -> return r
                ImageCache.Fetch.NotFound -> Unit
            }
        }
        val candidate = when (val s = searchPlace(query, coordinate)) {
            is Search.Found -> s.candidate
            Search.None -> return ImageCache.Fetch.NotFound
            Search.Error -> return ImageCache.Fetch.Failed
        }
        return when (val r = photoData(candidate.photoName, null)) {
            is ImageCache.Fetch.Bytes -> r.copy(extra = candidate.attribution, ref = candidate.photoName)
            else -> r
        }
    }

    /** Nearest place to [coordinate] matching [query] that actually has a photo. */
    private suspend fun searchPlace(query: String, coordinate: LatLng): Search = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("textQuery", query)
            put("maxResultCount", 5)
            put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put(
                            "center",
                            JSONObject()
                                .put("latitude", coordinate.latitude)
                                .put("longitude", coordinate.longitude),
                        )
                        .put("radius", 20_000.0),
                ),
            )
        }.toString()

        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places:searchText")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            // The field mask is required by this API and also caps what we are charged for.
            .addHeader(
                "X-Goog-FieldMask",
                "places.location,places.photos.name,places.photos.authorAttributions",
            )
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // 5xx / 429 is Google having a bad moment; a 4xx is a real answer about this
                    // query and may legitimately mean there is nothing to find.
                    return@use if (resp.code >= 500 || resp.code == 429) Search.Error else Search.None
                }
                val text = resp.body?.string().orEmpty()
                // A query with no results returns `{}`, so `places` is legitimately absent.
                val parsed = json.decodeFromString(SearchResponse.serializer(), text)
                val places = parsed.places ?: return@use Search.None

                val best = places.mapNotNull { place ->
                    val loc = place.location ?: return@mapNotNull null
                    val photo = place.photos?.firstOrNull() ?: return@mapNotNull null
                    val out = FloatArray(1)
                    android.location.Location.distanceBetween(
                        coordinate.latitude, coordinate.longitude, loc.latitude, loc.longitude, out,
                    )
                    val distance = out[0].toDouble()
                    if (distance > MAX_CANDIDATE_DISTANCE_M) return@mapNotNull null
                    val author = photo.authorAttributions?.firstNotNullOfOrNull { it.displayName }
                    distance to Candidate(photo.name, author)
                }.minByOrNull { it.first }?.second
                if (best != null) Search.Found(best) else Search.None
            }
        } catch (e: IOException) {
            Search.Error
        } catch (e: Exception) {
            // A malformed body: treat as a failed lookup rather than a definitive "no photo".
            Search.Error
        }
    }

    /**
     * The media endpoint answers with a 302 to the image bytes; OkHttp follows that for us.
     * `maxWidthPx` keeps the DOWNLOAD small — the decode-time downsample then bounds the longest
     * edge, which is what protects us from a tall portrait original.
     */
    private suspend fun photoData(photoName: String, reval: ImageCache.Revalidation?): ImageCache.Fetch =
        withContext(Dispatchers.IO) {
            val url = "https://places.googleapis.com/v1/$photoName/media" +
                "?maxWidthPx=$MAX_DECODED_PIXEL_SIZE&key=${BuildConfig.MAPS_API_KEY}"
            ImageHttp.get(url, reval)
        }

    // MARK: - Response models

    @Serializable
    private data class SearchResponse(val places: List<Place>? = null)

    @Serializable
    private data class Place(val location: LatLngNode? = null, val photos: List<PhotoNode>? = null)

    @Serializable
    private data class LatLngNode(val latitude: Double, val longitude: Double)

    @Serializable
    private data class PhotoNode(val name: String, val authorAttributions: List<Author>? = null)

    @Serializable
    private data class Author(val displayName: String? = null)
}
