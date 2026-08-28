package com.danglingmind.convotrack.data.maps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rider profile photos, for map markers and the off-screen edge chips.
 *
 * Deliberately separate from [PlacePhotoService]: these are small, numerous, and change with the
 * roster, whereas a destination hero is one large image per ride. They share the same three ideas —
 * bound the memory, downsample on decode, de-duplicate concurrent fetches — but avatars are NOT
 * persisted to disk: they are a few KB each, Clerk serves them from a CDN with its own HTTP
 * caching, and a stale avatar on disk would outlive a rider changing their picture.
 *
 * Process-wide rather than per-screen. The previous per-map-instance map meant every entry into
 * navigation re-downloaded the whole convoy's avatars, and nothing bounded how many were held.
 */
object AvatarCache {

    /**
     * Longest edge held. Rider markers draw a 32-40dp circle and edge chips a 34dp one, so at 3x
     * the largest that can ever be seen is ~120px. Clerk serves considerably larger originals;
     * decoding those at full size for a 32dp circle wastes both memory and decode time.
     */
    private const val MAX_PIXEL_SIZE = 160

    /** Small images, but a large convoy plus roster churn still adds up. */
    private val memory: LruCache<String, Bitmap> by lazy {
        val maxBytes = minOf(6L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 32).toInt()
        object : LruCache<String, Bitmap>(maxBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    /** URLs that returned nothing usable, so a broken avatar isn't re-requested every recomposition. */
    private val knownBad = mutableSetOf<String>()

    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()
    private val lock = Mutex()
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cached bitmap for [url], or null if it hasn't been fetched yet. Never does IO. */
    fun peek(url: String?): Bitmap? = url?.let { memory.get(it) }

    /**
     * Returns the avatar for [url], fetching and decoding it if needed. Concurrent callers for the
     * same URL share one request — the map and the edge chips ask for the same riders at the same
     * moment, so without this every avatar would be downloaded twice.
     */
    suspend fun get(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        memory.get(url)?.let { return it }
        lock.withLock { if (url in knownBad) return null }

        // The cache write happens INSIDE the shared task, not here.
        //
        // Callers are cancelled constantly — the map's avatar effect is keyed on the marker list,
        // which changes on every GPS fix — so recording the result in the caller's coroutine threw
        // away every download that outlived a position update, and the next pass started a fresh
        // HTTP request. For any avatar slower than the gap between fixes that was an unbounded
        // re-download loop that never populated the cache.
        val task = lock.withLock {
            inFlight[url] ?: scope.async { fetchAndRemember(url) }.also { inFlight[url] = it }
        }
        return try {
            task.await()
        } finally {
            // `scope` is not the caller's, so the task itself runs to completion and caches even
            // when this await is cancelled; this only tidies the in-flight map.
            lock.withLock { if (inFlight[url] === task) inFlight.remove(url) }
        }
    }

    private suspend fun fetchAndRemember(url: String): Bitmap? {
        val result = fetchAndDecode(url)
        if (result == null) {
            // Only a genuinely unusable image is blacklisted. A momentary dropout must stay
            // retryable, or one second without signal at the start of a ride leaves a rider as a
            // letter marker for the rest of the session.
            if (!lastFailureWasTransient) lock.withLock { knownBad.add(url) }
        } else {
            memory.put(url, result)
        }
        return result
    }

    /** Set by [fetchAndDecode] when the last failure was a network problem rather than a bad
     *  image, so [fetchAndRemember] knows whether the URL deserves blacklisting. */
    @Volatile
    private var lastFailureWasTransient = false

    private fun fetchAndDecode(url: String): Bitmap? = runCatching {
        lastFailureWasTransient = false
        val response = try {
            client.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: java.io.IOException) {
            lastFailureWasTransient = true
            return null
        }
        val bytes = response.use { resp ->
            // 5xx is the server having a bad moment; 4xx means this URL genuinely isn't an image
            // we can have, which is worth remembering.
            if (!resp.isSuccessful) {
                lastFailureWasTransient = resp.code >= 500
                return@use null
            }
            resp.body?.bytes()
        } ?: return null

        // Header-only pass first, so the full-size bitmap is never allocated just to be scaled down.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcLongest = max(bounds.outWidth, bounds.outHeight)
        if (srcLongest <= 0) return null

        var sample = 1
        while (srcLongest / (sample * 2) >= MAX_PIXEL_SIZE) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_PIXEL_SIZE) return decoded
        val ratio = MAX_PIXEL_SIZE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1),
            (decoded.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        scaled
    }.getOrNull()

    fun evictAll() = memory.evictAll()
}
