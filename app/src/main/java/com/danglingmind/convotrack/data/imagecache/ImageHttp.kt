package com.danglingmind.convotrack.data.imagecache

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The HTTP side of [ImageCache.Fetch]: a conditional GET that maps the response to the cache's
 * vocabulary. Blocking — call it from the `fetch` lambda, which already runs on an IO thread.
 *
 * Sends `If-None-Match` / `If-Modified-Since` when a stale copy has validators, and reads the
 * server's `ETag`, `Last-Modified` and `Cache-Control` so the cache can honour them.
 */
object ImageHttp {

    private val client = OkHttpClient()

    fun get(url: String, reval: ImageCache.Revalidation? = null): ImageCache.Fetch {
        val request = runCatching {
            Request.Builder().url(url).apply {
                reval?.etag?.let { header("If-None-Match", it) }
                reval?.lastModified?.let { header("If-Modified-Since", it) }
            }.build()
        }.getOrNull() ?: return ImageCache.Fetch.NotFound

        return try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 304 -> ImageCache.Fetch.NotModified
                    resp.isSuccessful -> {
                        val bytes = resp.body?.bytes() ?: return@use ImageCache.Fetch.Failed
                        val cc = resp.cacheControl
                        ImageCache.Fetch.Bytes(
                            data = bytes,
                            etag = resp.header("ETag"),
                            lastModified = resp.header("Last-Modified"),
                            maxAgeMs = when {
                                cc.noCache -> 0L
                                cc.maxAgeSeconds >= 0 -> cc.maxAgeSeconds * 1000L
                                else -> null
                            },
                            noStore = cc.noStore,
                        )
                    }
                    // 5xx / 429 / 408 are the server having a bad moment; any other 4xx means this
                    // URL genuinely isn't an image we can have.
                    resp.code >= 500 || resp.code == 429 || resp.code == 408 -> ImageCache.Fetch.Failed
                    else -> ImageCache.Fetch.NotFound
                }
            }
        } catch (e: IOException) {
            ImageCache.Fetch.Failed
        }
    }
}
