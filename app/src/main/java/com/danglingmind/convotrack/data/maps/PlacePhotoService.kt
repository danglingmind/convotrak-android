package com.danglingmind.convotrack.data.maps

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.danglingmind.convotrack.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
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
 * Fetches the photo Google Maps shows for a destination, for the ride lobby's hero image and the
 * home screen's active-ride card. Android port of iOS `GooglePlacePhotoService.swift`.
 *
 * The backend stores a destination as name + coordinate only (no place ID), so this resolves the
 * place itself: a Text Search biased to that coordinate, then the nearest candidate's first photo.
 * Uses the CURRENT Places API (`places.googleapis.com`, field-masked so we're billed only for the
 * fields we read) rather than the legacy endpoint [PlacesService] still uses — legacy Places is
 * closed to new customers and on a sunset path.
 *
 * Three tiers, cheapest first: an in-memory [LruCache], then a disk cache under `cacheDir`, then
 * the two billable network calls. A destination that is edited produces a different cache key, so
 * it misses all tiers and refetches without any explicit invalidation.
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Decoded-bitmap cache, bounded by BYTES rather than entry count — one hero bitmap is by far
     * the largest image this app holds, so counting entries would bound nothing useful.
     *
     * Capped against the process heap as well as an absolute ceiling: on a low-memory device an
     * unconditional 24MB is a large share of the heap.
     */
    private val memory: LruCache<String, DestinationPhoto> by lazy {
        val maxBytes = minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt()
        object : LruCache<String, DestinationPhoto>(maxBytes) {
            override fun sizeOf(key: String, value: DestinationPhoto): Int = value.bitmap.byteCount
        }
    }

    /**
     * Destinations known to have no photo, so a legitimately photo-less place doesn't re-run two
     * billable requests on every appearance. Mirrored on disk, so it survives a relaunch too.
     */
    private val knownEmpty = mutableSetOf<String>()

    /** De-duplicates concurrent lookups for the same destination, so the lobby and the home card
     *  appearing together can't double-bill the search. */
    private val inFlight = mutableMapOf<String, Deferred<FetchedPhoto?>>()
    private val lock = Mutex()

    @Volatile
    private var didPrune = false

    private var appContext: Context? = null

    /**
     * Called once from `ConvoTrackApp`. Also registers a memory-pressure listener: Android's
     * [LruCache] has no equivalent of iOS `NSCache`'s automatic eviction, so without this the hero
     * bitmaps would be held until the process dies.
     */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) memory.evictAll()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() { memory.evictAll() }
        })
    }

    private data class FetchedPhoto(val data: ByteArray, val attribution: String?)

    private data class Candidate(val photoName: String, val attribution: String?)

    // MARK: - Entry point

    suspend fun photo(destinationName: String, coordinate: LatLng): DestinationPhoto? {
        val query = destinationName.trim()
        if (query.isEmpty()) return null
        val key = cacheKey(query, coordinate)

        memory.get(key)?.let { return it }
        lock.withLock { if (knownEmpty.contains(key)) return null }

        // Housekeeping once per process, before the first disk read.
        if (!didPrune) {
            didPrune = true
            withContext(Dispatchers.IO) { DiskCache.prune(appContext) }
        }

        when (val entry = withContext(Dispatchers.IO) { DiskCache.load(appContext, key) }) {
            is DiskCache.Entry.Empty -> {
                lock.withLock { knownEmpty.add(key) }
                return null
            }
            is DiskCache.Entry.Photo -> {
                decodeAndRemember(entry.data, entry.attribution, key)?.let { return it }
                // Unreadable file — drop it and fall through to the network rather than serving
                // nothing for the next 30 days.
                withContext(Dispatchers.IO) { DiskCache.remove(appContext, key) }
            }
            DiskCache.Entry.Miss -> Unit
        }

        // One network task per key, shared by every concurrent caller.
        val task = lock.withLock {
            inFlight[key] ?: scope.async { fetch(query, coordinate) }.also { inFlight[key] = it }
        }
        val fetched = try {
            task.await()
        } finally {
            lock.withLock { if (inFlight[key] === task) inFlight.remove(key) }
        }

        if (fetched == null) {
            lock.withLock { knownEmpty.add(key) }
            withContext(Dispatchers.IO) { DiskCache.storeEmpty(appContext, key) }
            return null
        }

        withContext(Dispatchers.IO) { DiskCache.store(appContext, key, fetched.data, fetched.attribution) }
        val photo = decodeAndRemember(fetched.data, fetched.attribution, key)
        if (photo == null) {
            // Bytes arrived but aren't a decodable image. Record it as empty so we don't pay for
            // the same two requests on every appearance.
            withContext(Dispatchers.IO) {
                DiskCache.remove(appContext, key)
                DiskCache.storeEmpty(appContext, key)
            }
            lock.withLock { knownEmpty.add(key) }
        }
        return photo
    }

    private suspend fun decodeAndRemember(data: ByteArray, attribution: String?, key: String): DestinationPhoto? {
        val bitmap = withContext(Dispatchers.IO) { downsample(data, MAX_DECODED_PIXEL_SIZE) } ?: return null
        val photo = DestinationPhoto(bitmap, attribution)
        memory.put(key, photo)
        return photo
    }

    /**
     * Coordinates are rounded to ~100 m so tiny differences in an otherwise identical destination
     * still hit the same entry, while a genuinely edited destination (a new name, or a move of more
     * than ~100 m) produces a new key and therefore a refetch — implicit invalidation, with nothing
     * to remember to call.
     */
    private fun cacheKey(query: String, coordinate: LatLng): String {
        val lat = (coordinate.latitude * 1000).roundToInt() / 1000.0
        val lng = (coordinate.longitude * 1000).roundToInt() / 1000.0
        return "${query.lowercase()}|$lat,$lng|$MAX_DECODED_PIXEL_SIZE"
    }

    // MARK: - Decoding

    /**
     * Decodes close to the size we display, in two passes.
     *
     * Pass 1 reads only the header (`inJustDecodeBounds`) to learn the real dimensions without
     * allocating the bitmap. Pass 2 decodes with `inSampleSize`, which downsamples DURING decode —
     * so a 4000px original never materialises at full size. `inSampleSize` is powers of two only,
     * so a final exact scale bounds the longest edge precisely at [maxPixelSize] while preserving
     * the aspect ratio.
     */
    private fun downsample(data: ByteArray, maxPixelSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcLongest = max(bounds.outWidth, bounds.outHeight)
        if (srcLongest <= 0) return null

        var sample = 1
        while (srcLongest / (sample * 2) >= maxPixelSize) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            data, 0, data.size,
            // ARGB_8888 rather than RGB_565: this is a large photographic hero behind lime text,
            // and 565's banding is very visible in the sky gradients these place photos are full of.
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        val longest = max(decoded.width, decoded.height)
        if (longest <= maxPixelSize) return decoded

        val ratio = maxPixelSize.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1),
            (decoded.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    // MARK: - Disk cache

    /**
     * Photos and "this place has no photo" markers persisted under `cacheDir`, so a relaunch costs
     * no API calls. `cacheDir` is the correct home: the OS may evict it under storage pressure and
     * it is excluded from backups, and everything here is re-derivable.
     */
    private object DiskCache {
        /** Place photos are effectively static, so entries live a long time. Empty markers expire
         *  sooner, since a place that has no photo today may gain one. */
        private const val PHOTO_TTL_MS = 30L * 24 * 3600 * 1000
        private const val EMPTY_TTL_MS = 7L * 24 * 3600 * 1000

        /** Bounds the directory. Riders revisit few destinations, so this is generous. */
        private const val MAX_ENTRIES = 20

        @Serializable
        private data class Meta(val attribution: String? = null, val isEmpty: Boolean = false)

        sealed interface Entry {
            data class Photo(val data: ByteArray, val attribution: String?) : Entry
            data object Empty : Entry
            data object Miss : Entry
        }

        private fun directory(context: Context?): File? {
            val ctx = context ?: return null
            val dir = File(ctx.cacheDir, "DestinationPhotos")
            if (!dir.exists() && !dir.mkdirs()) return null
            return dir
        }

        /**
         * SHA-256 of the key. A JVM `hashCode()` is not usable as a filename here — it is only
         * stable within a process for some types and collides far too readily for a shared
         * namespace; a digest is stable across relaunches, which is the whole point of a disk cache.
         */
        private fun basename(key: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun load(context: Context?, key: String): Entry {
            val dir = directory(context) ?: return Entry.Miss
            val base = basename(key)
            val metaFile = File(dir, "$base.json")
            if (!metaFile.exists()) return Entry.Miss
            val meta = runCatching { json.decodeFromString(Meta.serializer(), metaFile.readText()) }
                .getOrNull() ?: return Entry.Miss

            val age = System.currentTimeMillis() - metaFile.lastModified()
            if (age >= (if (meta.isEmpty) EMPTY_TTL_MS else PHOTO_TTL_MS)) {
                remove(context, key)
                return Entry.Miss
            }
            if (meta.isEmpty) return Entry.Empty

            val imageFile = File(dir, "$base.jpg")
            val bytes = runCatching { imageFile.readBytes() }.getOrNull() ?: return Entry.Miss
            return Entry.Photo(bytes, meta.attribution)
        }

        fun store(context: Context?, key: String, imageData: ByteArray, attribution: String?) {
            val dir = directory(context) ?: return
            val base = basename(key)
            runCatching {
                // The ORIGINAL bytes as served, never re-compressed: a re-encode would cost quality
                // for nothing, since the decode-time downsample already bounds what we hold.
                File(dir, "$base.jpg").writeBytes(imageData)
                File(dir, "$base.json").writeText(
                    json.encodeToString(Meta.serializer(), Meta(attribution, isEmpty = false)),
                )
            }
        }

        fun storeEmpty(context: Context?, key: String) {
            val dir = directory(context) ?: return
            runCatching {
                File(dir, "${basename(key)}.json").writeText(
                    json.encodeToString(Meta.serializer(), Meta(null, isEmpty = true)),
                )
            }
        }

        fun remove(context: Context?, key: String) {
            val dir = directory(context) ?: return
            val base = basename(key)
            File(dir, "$base.jpg").delete()
            File(dir, "$base.json").delete()
        }

        /** Drops expired entries, then the oldest beyond [MAX_ENTRIES]. Run once per process — a
         *  destination the rider no longer visits would otherwise sit here forever. */
        fun prune(context: Context?) {
            val dir = directory(context) ?: return
            val metas = dir.listFiles { f -> f.name.endsWith(".json") } ?: return

            val live = mutableListOf<Pair<String, Long>>()
            for (metaFile in metas) {
                val base = metaFile.name.removeSuffix(".json")
                val isEmpty = runCatching {
                    json.decodeFromString(Meta.serializer(), metaFile.readText()).isEmpty
                }.getOrDefault(false)
                val ttl = if (isEmpty) EMPTY_TTL_MS else PHOTO_TTL_MS
                if (System.currentTimeMillis() - metaFile.lastModified() >= ttl) {
                    File(dir, "$base.jpg").delete()
                    metaFile.delete()
                } else {
                    live += base to metaFile.lastModified()
                }
            }

            if (live.size <= MAX_ENTRIES) return
            live.sortedBy { it.second }.take(live.size - MAX_ENTRIES).forEach { (base, _) ->
                File(dir, "$base.jpg").delete()
                File(dir, "$base.json").delete()
            }
        }
    }

    // MARK: - Network

    private suspend fun fetch(query: String, coordinate: LatLng): FetchedPhoto? {
        val candidate = searchPlace(query, coordinate) ?: return null
        val data = photoData(candidate.photoName) ?: return null
        return FetchedPhoto(data, candidate.attribution)
    }

    /** Nearest place to [coordinate] matching [query] that actually has a photo. */
    private suspend fun searchPlace(query: String, coordinate: LatLng): Candidate? = withContext(Dispatchers.IO) {
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

        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val text = resp.body?.string().orEmpty()
                // A query with no results returns `{}`, so `places` is legitimately absent.
                val parsed = json.decodeFromString(SearchResponse.serializer(), text)
                val places = parsed.places ?: return@use null

                places.mapNotNull { place ->
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
            }
        }.getOrNull()
    }

    /**
     * The media endpoint answers with a 302 to the image bytes; OkHttp follows that for us.
     * `maxWidthPx` keeps the DOWNLOAD small — the decode-time downsample then bounds the longest
     * edge, which is what protects us from a tall portrait original.
     */
    private suspend fun photoData(photoName: String): ByteArray? = withContext(Dispatchers.IO) {
        val url = "https://places.googleapis.com/v1/$photoName/media" +
            "?maxWidthPx=$MAX_DECODED_PIXEL_SIZE&key=${BuildConfig.MAPS_API_KEY}"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
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
