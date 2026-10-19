package com.danglingmind.convotrack.data.imagecache

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What an image is, for cache policy. Everything that differs between kinds of image — how long it
 * stays valid, how much disk it may use, whether "there is nothing here" is remembered — lives on
 * the category, so a call site only names the kind and supplies a key.
 *
 * [ttlMs] is both the default lifetime and a CEILING: a server `Cache-Control: max-age` can shorten
 * it but never extend it. `null` means the entry never expires by age (the key already encodes
 * everything that changes the pixels) and only the LRU budget removes it.
 *
 * [emptyTtlMs] is how long a resolved "no image" answer is remembered; 0 disables that.
 *
 * [dir] is the subfolder under `cacheDir/ImageCache/`. The summary folder is the one the
 * FileProvider exposes (`res/xml/file_paths.xml`), so its files can be shared as they are.
 */
enum class ImageCategory(
    val dir: String,
    val ext: String,
    val ttlMs: Long?,
    val emptyTtlMs: Long,
    val budgetBytes: Long,
) {
    /** Static Maps renders. Long-lived: a route's picture doesn't drift. */
    STATIC_MAP("staticmaps", "png", 30L * DAY, 0L, 30L * MB),

    /** Places photos. Keeps the 30-day / 7-day-empty rules the photo service always had. */
    PLACE_PHOTO("placephotos", "jpg", 30L * DAY, 7L * DAY, 40L * MB),

    /** Rider avatars. Short, because a rider can change their picture at the same URL. */
    AVATAR("avatars", "img", DAY, HOUR, 10L * MB),

    /** Rendered ride-summary cards. Content-addressed by key, so no age limit. */
    SUMMARY("summary", "png", null, 0L, 20L * MB),
}

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR
private const val MB = 1024L * 1024L

/**
 * The app's one image cache: a byte-bounded in-memory [LruCache] in front of a size-capped disk
 * cache under `cacheDir` (so the OS may purge it), with de-duplicated in-flight loads.
 *
 * Callers supply a STABLE KEY that encodes everything that changes the pixels, plus a `fetch`
 * lambda that produces bytes. Invalidation is mostly by key change (an edited ride, a new avatar
 * URL, new summary data all simply miss); the rest is TTL + HTTP validators, LRU budgets, a format
 * version, and explicit purges — see the individual members.
 */
object ImageCache {

    /** Bump to discard every entry written by an older on-disk layout. */
    private const val FORMAT_VERSION = 1

    /** Hard ceiling across all categories, enforced oldest-access-first. */
    private const val MAX_TOTAL_BYTES = 100L * MB

    /**
     * Floor on an entry's lifetime however eagerly the server says `max-age=0`/`no-cache`. Without
     * it a no-cache CDN would turn every GPS-driven avatar lookup into a network round trip.
     */
    private const val MIN_TTL_MS = 5 * 60_000L

    /** Back-off after a transient failure, so a dead network isn't retried on every recomposition. */
    private const val TRANSIENT_BACKOFF_MS = 30_000L

    /** Trim after this many disk writes (plus once at startup). */
    private const val TRIM_EVERY_WRITES = 8

    /** A decoded image and the sidecar string stored with it (e.g. a photo credit). */
    class Loaded(val bitmap: Bitmap, val extra: String?)

    /** What we already hold for a stale entry, so the fetch can be conditional. */
    class Revalidation(
        val etag: String?,
        val lastModified: String?,
        /** Opaque source reference saved by the previous fetch (e.g. a Places photo name). */
        val ref: String?,
        val extra: String?,
    )

    sealed interface Fetch {
        /** [maxAgeMs] is the server's `Cache-Control` freshness (0 for no-cache), if it gave one. */
        data class Bytes(
            val data: ByteArray,
            val etag: String? = null,
            val lastModified: String? = null,
            val maxAgeMs: Long? = null,
            val noStore: Boolean = false,
            val extra: String? = null,
            val ref: String? = null,
        ) : Fetch

        /** The server confirmed the stored copy is still current (HTTP 304). */
        data object NotModified : Fetch

        /** An authoritative "there is no image" (4xx, no match). Remembered per [ImageCategory.emptyTtlMs]. */
        data object NotFound : Fetch

        /** Network trouble or a 5xx: says nothing about the image, so nothing is remembered. */
        data object Failed : Fetch
    }

    @Serializable
    private data class Meta(
        val createdAt: Long,
        /** Lifetime in ms; -1 = never expires by age. */
        val ttlMs: Long = -1,
        val etag: String? = null,
        val lastModified: String? = null,
        val extra: String? = null,
        val ref: String? = null,
        /** The ride this image belongs to, so deleting/leaving that ride can drop it. */
        val owner: String? = null,
        val empty: Boolean = false,
    )

    private class MemEntry(val bitmap: Bitmap, val extra: String?, val owner: String?, val expiresAt: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Decoded bitmaps bounded by BYTES: 1/8 of the heap, at most 64MB. */
    private val memory: LruCache<String, MemEntry> by lazy {
        val maxBytes = min(64L * MB, Runtime.getRuntime().maxMemory() / 8).toInt()
        object : LruCache<String, MemEntry>(maxBytes) {
            override fun sizeOf(key: String, value: MemEntry): Int = value.bitmap.byteCount
        }
    }

    private val inFlight = mutableMapOf<String, Deferred<Loaded?>>()
    private val lock = Mutex()

    /** Memory-key -> time before which a miss is not retried (resolved-empty and transient alike). */
    private val negative = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private var appContext: Context? = null
    private val writes = AtomicInteger()
    private val trimming = AtomicBoolean(false)

    /**
     * Called once from `ConvoTrackApp`. Registers the memory-pressure listener (Android's
     * [LruCache] won't shrink on its own) and schedules a first trim off the main thread.
     */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) clearMemory()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = clearMemory()
        })
        scope.launch { trim() }
    }

    // MARK: - Loading

    /** The decoded image if it is already in memory. Never does IO, so it is safe during composition. */
    fun peek(category: ImageCategory, key: String, maxPixel: Int): Bitmap? =
        memory.get(memoryKey(category, key, maxPixel))?.bitmap

    /**
     * The image for [key], from memory, then disk, then [fetch]. Concurrent calls for one key share
     * a single resolution. Returns null when there is no image (or it couldn't be had right now).
     *
     * [fetch] runs on an IO thread and may block. It receives a [Revalidation] when a stale copy
     * exists, and should send conditional headers (see [ImageHttp.get]).
     *
     * [maxPixel] bounds the longest decoded edge; it is part of the memory key, so the same file
     * decoded at two sizes doesn't collide.
     */
    suspend fun load(
        category: ImageCategory,
        key: String,
        maxPixel: Int,
        owner: String? = null,
        fetch: suspend (Revalidation?) -> Fetch,
    ): Loaded? {
        val mk = memoryKey(category, key, maxPixel)
        val now = System.currentTimeMillis()
        memory.get(mk)?.let { if (it.expiresAt > now) return Loaded(it.bitmap, it.extra) }
        negative[mk]?.let { if (it > now) return null else negative.remove(mk) }

        // The shared task lives in `scope`, not the caller's: callers are cancelled constantly
        // (effects re-key on every GPS fix), and tying the download to them threw away every fetch
        // that outlived one position update.
        val task = lock.withLock {
            inFlight[mk] ?: scope.async {
                try {
                    resolve(category, key, maxPixel, owner, mk, fetch)
                } finally {
                    lock.withLock { inFlight.remove(mk) }
                }
            }.also { inFlight[mk] = it }
        }
        return task.await()
    }

    private suspend fun resolve(
        category: ImageCategory,
        key: String,
        maxPixel: Int,
        owner: String?,
        mk: String,
        fetch: suspend (Revalidation?) -> Fetch,
    ): Loaded? {
        val base = basename(key)
        val dir = categoryDir(category)
        var stored = dir?.let { readMeta(it, base) }
        val dataFile = dir?.let { File(it, "$base.${category.ext}") }
        val now = System.currentTimeMillis()

        if (stored != null && dir != null && dataFile != null) {
            val fresh = isFresh(category, stored, now)
            if (stored.empty) {
                if (fresh) {
                    negative[mk] = now + category.emptyTtlMs
                    return null
                }
                deleteEntry(dir, base, category)
                stored = null
            } else if (fresh) {
                val bmp = decodeFile(dataFile, maxPixel)
                if (bmp != null) {
                    touch(dir, base)
                    return remember(mk, bmp, stored.extra, stored.owner, expiryOf(category, stored.ttlMs, now))
                }
                // Unreadable (truncated, corrupt): drop it and fetch afresh rather than serving
                // nothing until it expires.
                deleteEntry(dir, base, category)
                stored = null
            }
        }

        val reval = stored?.takeIf { dataFile?.isFile == true }
            ?.let { Revalidation(it.etag, it.lastModified, it.ref, it.extra) }

        return when (val result = fetch(reval)) {
            is Fetch.Bytes -> {
                val bmp = decodeBytes(result.data, maxPixel)
                if (bmp == null) {
                    // Bytes arrived but aren't an image: remember that, so we don't re-download it
                    // on every appearance.
                    if (dir != null) deleteEntry(dir, base, category)
                    rememberEmpty(category, dir, base, owner, mk)
                    return null
                }
                val ttl = effectiveTtl(category, result.maxAgeMs)
                if (!result.noStore && dir != null) {
                    writeEntry(
                        category, dir, base, result.data,
                        Meta(
                            createdAt = now, ttlMs = ttl, etag = result.etag, lastModified = result.lastModified,
                            extra = result.extra, ref = result.ref, owner = owner,
                        ),
                    )
                }
                remember(mk, bmp, result.extra, owner, expiryOf(category, ttl, now))
            }

            Fetch.NotModified -> {
                val bmp = if (stored != null && dataFile != null) decodeFile(dataFile, maxPixel) else null
                if (bmp == null || stored == null || dir == null) {
                    // A 304 for something we can't read: start over next time.
                    if (dir != null) deleteEntry(dir, base, category)
                    return null
                }
                // Still current: restart its clock so it isn't revalidated on every load.
                writeMeta(dir, base, stored.copy(createdAt = now))
                remember(mk, bmp, stored.extra, stored.owner, expiryOf(category, stored.ttlMs, now))
            }

            Fetch.NotFound -> {
                if (dir != null) deleteEntry(dir, base, category)
                rememberEmpty(category, dir, base, owner, mk)
                null
            }

            Fetch.Failed -> {
                // Stale-if-error: an out-of-date image beats a blank one when the network is down.
                val bmp = if (stored != null && !stored.empty && dataFile != null) decodeFile(dataFile, maxPixel) else null
                if (bmp != null) {
                    remember(mk, bmp, stored?.extra, stored?.owner, now + TRANSIENT_BACKOFF_MS)
                } else {
                    negative[mk] = now + TRANSIENT_BACKOFF_MS
                    null
                }
            }
        }
    }

    private fun remember(mk: String, bmp: Bitmap, extra: String?, owner: String?, expiresAt: Long): Loaded {
        memory.put(mk, MemEntry(bmp, extra, owner, expiresAt))
        negative.remove(mk)
        return Loaded(bmp, extra)
    }

    private fun rememberEmpty(category: ImageCategory, dir: File?, base: String, owner: String?, mk: String) {
        if (category.emptyTtlMs <= 0) return
        negative[mk] = System.currentTimeMillis() + category.emptyTtlMs
        if (dir != null) writeMeta(dir, base, Meta(createdAt = System.currentTimeMillis(), owner = owner, empty = true))
    }

    // MARK: - Whole-file entries (the rendered summary card)

    /** The stored file for [key] if present, readable and fresh; null otherwise. */
    suspend fun cachedFile(category: ImageCategory, key: String): File? = withContext(Dispatchers.IO) {
        val dir = categoryDir(category) ?: return@withContext null
        val base = basename(key)
        val meta = readMeta(dir, base) ?: return@withContext null
        val file = File(dir, "$base.${category.ext}")
        if (meta.empty || !isFresh(category, meta, System.currentTimeMillis()) || !file.isFile || file.length() == 0L) {
            deleteEntry(dir, base, category)
            return@withContext null
        }
        touch(dir, base)
        file
    }

    /**
     * Stores whatever [write] produces as [key]'s file. Written to a temp file and renamed, so an
     * interrupted write can never leave a truncated image that a later lookup trusts.
     */
    suspend fun storeFile(
        category: ImageCategory,
        key: String,
        owner: String? = null,
        write: (OutputStream) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = categoryDir(category) ?: throw java.io.IOException("Image cache unavailable")
        val base = basename(key)
        val tmp = File(dir, "$base.tmp")
        tmp.outputStream().use(write)
        writeEntryFile(category, dir, base, tmp, Meta(createdAt = System.currentTimeMillis(), owner = owner))
        File(dir, "$base.${category.ext}")
    }

    // MARK: - Invalidation

    /** Drops one entry (memory and disk) — for when a caller knows the pixels behind a key changed. */
    suspend fun invalidate(category: ImageCategory, key: String) {
        val base = basename(key)
        memory.snapshot().keys.filter { it.startsWith("${category.dir}|") && it.endsWith("|$base") }
            .forEach { memory.remove(it) }
        withContext(Dispatchers.IO) { categoryDir(category)?.let { deleteEntry(it, base, category) } }
    }

    /** Drops every image tagged with [owner] (a ride id): its thumbnail, map and summary card. */
    suspend fun evictOwner(owner: String) = withContext(Dispatchers.IO) {
        memory.snapshot().filterValues { it.owner == owner }.keys.forEach { memory.remove(it) }
        for (category in ImageCategory.entries) {
            val dir = categoryDir(category) ?: continue
            dir.listFiles { f -> f.name.endsWith(".meta") }?.forEach { metaFile ->
                val base = metaFile.name.removeSuffix(".meta")
                if (readMeta(dir, base)?.owner == owner) deleteEntry(dir, base, category)
            }
        }
    }

    /** Empties one category. */
    suspend fun purge(category: ImageCategory) = withContext(Dispatchers.IO) {
        clearMemory()
        categoryDir(category)?.listFiles()?.forEach { it.delete() }
    }

    /** Empties everything. Called on sign-out/account deletion so one rider's images never reach the next. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        clearMemory()
        negative.clear()
        rootDir()?.listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() }
    }

    /** Frees every decoded bitmap. Disk is untouched, so reloading costs a decode, not a download. */
    fun clearMemory() {
        memory.evictAll()
        negative.clear()
    }

    // MARK: - Keys & freshness

    private fun memoryKey(category: ImageCategory, key: String, maxPixel: Int) =
        "${category.dir}|$maxPixel|${basename(key)}"

    /** SHA-256 of the key: stable across launches (unlike hashCode) and safe as a filename. */
    private fun basename(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Server freshness can only shorten the category's lifetime, and never below [MIN_TTL_MS]. */
    private fun effectiveTtl(category: ImageCategory, serverMaxAgeMs: Long?): Long {
        val ceiling = category.ttlMs ?: return -1
        val server = serverMaxAgeMs ?: return ceiling
        return min(ceiling, max(server, MIN_TTL_MS))
    }

    private fun expiryOf(category: ImageCategory, ttlMs: Long, now: Long): Long =
        if (category.ttlMs == null || ttlMs < 0) Long.MAX_VALUE else now + ttlMs

    private fun isFresh(category: ImageCategory, meta: Meta, now: Long): Boolean {
        val ttl = if (meta.empty) category.emptyTtlMs else if (category.ttlMs == null) -1 else meta.ttlMs
        return ttl < 0 || now - meta.createdAt < ttl
    }

    // MARK: - Disk

    private val root: File? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { prepareRoot() }

    private fun rootDir(): File? = root

    /**
     * First disk touch of the process: wipes the cache when its layout version changed (h) and
     * removes the two pre-ImageCache folders, whose files nothing reads any more.
     */
    private fun prepareRoot(): File? {
        val ctx = appContext ?: return null
        runCatching {
            File(ctx.cacheDir, "DestinationPhotos").deleteRecursively()
            File(ctx.cacheDir, "shared").deleteRecursively()
        }
        val dir = File(ctx.cacheDir, "ImageCache")
        if (!dir.exists() && !dir.mkdirs()) return null
        val marker = File(dir, ".format")
        val current = runCatching { marker.readText().trim() }.getOrNull()
        if (current != FORMAT_VERSION.toString()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            runCatching { marker.writeText(FORMAT_VERSION.toString()) }
        }
        return dir
    }

    private fun categoryDir(category: ImageCategory): File? {
        val dir = File(rootDir() ?: return null, category.dir)
        return if (dir.isDirectory || dir.mkdirs()) dir else null
    }

    private fun readMeta(dir: File, base: String): Meta? {
        val file = File(dir, "$base.meta")
        if (!file.isFile) return null
        // An unparseable sidecar means the entry can't be trusted: drop the pair.
        return runCatching { json.decodeFromString(Meta.serializer(), file.readText()) }.getOrNull()
            ?: run { file.delete(); null }
    }

    private fun writeMeta(dir: File, base: String, meta: Meta) {
        runCatching {
            val tmp = File(dir, "$base.meta.tmp")
            tmp.writeText(json.encodeToString(Meta.serializer(), meta))
            if (!tmp.renameTo(File(dir, "$base.meta"))) tmp.delete()
        }
    }

    private fun writeEntry(category: ImageCategory, dir: File, base: String, data: ByteArray, meta: Meta) {
        runCatching {
            val tmp = File(dir, "$base.tmp")
            // The ORIGINAL bytes as served, never re-compressed.
            tmp.writeBytes(data)
            writeEntryFile(category, dir, base, tmp, meta)
        }
    }

    /** Data file first, sidecar last: an entry only exists once its sidecar does. */
    private fun writeEntryFile(category: ImageCategory, dir: File, base: String, tmp: File, meta: Meta) {
        val target = File(dir, "$base.${category.ext}")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        writeMeta(dir, base, meta)
        if (writes.incrementAndGet() % TRIM_EVERY_WRITES == 0) scope.launch { trim() }
    }

    private fun deleteEntry(dir: File, base: String, category: ImageCategory) {
        File(dir, "$base.${category.ext}").delete()
        File(dir, "$base.meta").delete()
    }

    /** Recency for LRU: the sidecar's mtime, bumped on every hit. */
    private fun touch(dir: File, base: String) {
        File(dir, "$base.meta").setLastModified(System.currentTimeMillis())
    }

    private class DiskEntry(
        val category: ImageCategory,
        val dir: File,
        val base: String,
        val size: Long,
        val lastUsed: Long,
    )

    /**
     * Lazy housekeeping (always on an IO thread): sweeps temp/orphan files and entries that expired
     * with nothing to revalidate against, then enforces each category's budget and the overall cap,
     * least-recently-used first.
     */
    private fun trim() {
        if (!trimming.compareAndSet(false, true)) return
        try {
            val now = System.currentTimeMillis()
            val entries = mutableListOf<DiskEntry>()
            for (category in ImageCategory.entries) {
                val dir = categoryDir(category) ?: continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    // Leftovers of an interrupted write.
                    if (f.name.endsWith(".tmp") && now - f.lastModified() > HOUR) f.delete()
                    // Data with no sidecar was never completed.
                    if (f.name.endsWith(".${category.ext}") && !File(dir, f.name.removeSuffix(".${category.ext}") + ".meta").exists() &&
                        now - f.lastModified() > HOUR
                    ) f.delete()
                }
                for (metaFile in files.filter { it.name.endsWith(".meta") }) {
                    val base = metaFile.name.removeSuffix(".meta")
                    val meta = readMeta(dir, base) ?: continue
                    val data = File(dir, "$base.${category.ext}")
                    if (!meta.empty && !data.isFile) { deleteEntry(dir, base, category); continue }
                    val revalidatable = meta.etag != null || meta.lastModified != null || meta.ref != null
                    if (!isFresh(category, meta, now) && (meta.empty || !revalidatable)) {
                        deleteEntry(dir, base, category)
                        continue
                    }
                    entries += DiskEntry(category, dir, base, data.length() + metaFile.length(), metaFile.lastModified())
                }
            }

            val survivors = mutableListOf<DiskEntry>()
            for (category in ImageCategory.entries) {
                val mine = entries.filter { it.category == category }.sortedByDescending { it.lastUsed }
                var used = 0L
                for (e in mine) {
                    used += e.size
                    if (used > category.budgetBytes) deleteEntry(e.dir, e.base, e.category) else survivors += e
                }
            }
            var total = survivors.sumOf { it.size }
            for (e in survivors.sortedBy { it.lastUsed }) {
                if (total <= MAX_TOTAL_BYTES) break
                deleteEntry(e.dir, e.base, e.category)
                total -= e.size
            }
        } finally {
            trimming.set(false)
        }
    }

    // MARK: - Decoding

    /**
     * Decodes close to [maxPixel] in two passes: a header-only pass for the real size, then a decode
     * with `inSampleSize` so a 4000px original never materialises at full size. `inSampleSize` is
     * powers of two only, so a final exact scale bounds the longest edge and keeps the aspect ratio.
     */
    private fun decodeBytes(data: ByteArray, maxPixel: Int): Bitmap? = decode(
        maxPixel,
        bounds = { BitmapFactory.decodeByteArray(data, 0, data.size, it) },
        full = { BitmapFactory.decodeByteArray(data, 0, data.size, it) },
    )

    private fun decodeFile(file: File, maxPixel: Int): Bitmap? = decode(
        maxPixel,
        bounds = { BitmapFactory.decodeFile(file.path, it) },
        full = { BitmapFactory.decodeFile(file.path, it) },
    )

    private inline fun decode(
        maxPixel: Int,
        bounds: (BitmapFactory.Options) -> Unit,
        full: (BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? = runCatching {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        bounds(probe)
        val srcLongest = max(probe.outWidth, probe.outHeight)
        if (srcLongest <= 0) return@runCatching null

        var sample = 1
        while (srcLongest / (sample * 2) >= maxPixel) sample *= 2

        // ARGB_8888: banding in 565 is obvious in the sky gradients of place photos.
        val decoded = full(
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return@runCatching null

        val longest = max(decoded.width, decoded.height)
        if (longest <= maxPixel) return@runCatching decoded
        val ratio = maxPixel.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1),
            (decoded.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        scaled
    }.getOrNull()
}
