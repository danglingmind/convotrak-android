package com.danglingmind.convotrack.data.maps

import android.graphics.Bitmap
import com.danglingmind.convotrack.data.imagecache.ImageCache
import com.danglingmind.convotrack.data.imagecache.ImageCategory
import com.danglingmind.convotrack.data.imagecache.ImageHttp

/**
 * Rider profile photos, for map markers, the off-screen edge chips and avatar discs.
 *
 * A thin facade over [ImageCache] ([ImageCategory.AVATAR]) that fixes the decode size and keeps the
 * call sites simple. Avatars are small and numerous, so they are decoded to marker size rather than
 * the CDN original.
 *
 * The key is the URL. A rider who changes their picture gets a new URL from Clerk, which simply
 * misses; the same URL serving new pixels is covered by the category's short TTL plus an
 * ETag/Last-Modified revalidation, and [invalidate] lets a screen force it.
 */
object AvatarCache {

    /**
     * Longest edge held. Rider markers draw a 32-40dp circle and edge chips a 34dp one, so at 3x
     * the largest that can ever be seen is ~120px. Clerk serves considerably larger originals;
     * decoding those at full size for a 32dp circle wastes both memory and decode time.
     */
    private const val MAX_PIXEL_SIZE = 160

    /** Cached bitmap for [url], or null if it isn't in memory yet. Never does IO. */
    fun peek(url: String?): Bitmap? =
        if (url.isNullOrBlank()) null else ImageCache.peek(ImageCategory.AVATAR, url, MAX_PIXEL_SIZE)

    /**
     * Returns the avatar for [url], from memory, disk or network. Concurrent callers for the same
     * URL share one request — the map and the edge chips ask for the same riders at the same moment.
     */
    suspend fun get(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return ImageCache.load(ImageCategory.AVATAR, url, MAX_PIXEL_SIZE) { reval -> ImageHttp.get(url, reval) }?.bitmap
    }

    /** Forgets [url] everywhere, e.g. right after the signed-in rider changes their own picture. */
    suspend fun invalidate(url: String?) {
        if (!url.isNullOrBlank()) ImageCache.invalidate(ImageCategory.AVATAR, url)
    }

    fun evictAll() = ImageCache.clearMemory()
}
