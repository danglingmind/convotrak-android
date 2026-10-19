package com.danglingmind.convotrack.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * What a cached remote image should show right now. Three states rather than a nullable bitmap so a
 * call site can show a placeholder while [Loading] and its fallback only once [Failed] — collapsing
 * them makes the fallback flash up and then get replaced.
 */
sealed interface CachedImagePhase {
    data object Loading : CachedImagePhase
    data class Loaded(val image: ImageBitmap) : CachedImagePhase
    data object Failed : CachedImagePhase
}

/**
 * Loads an image through the shared [com.danglingmind.convotrack.data.imagecache.ImageCache].
 *
 * [peek] is consulted synchronously first so an already-decoded image appears on the first frame
 * with no flicker; [load] then runs (off the main thread, inside the cache) and the state follows.
 * Re-keys on [key], dropping a previous image the moment the key changes.
 */
@Composable
fun rememberCachedImage(
    key: Any?,
    peek: () -> Bitmap?,
    load: suspend () -> Bitmap?,
): State<CachedImagePhase> {
    val state = remember(key) {
        mutableStateOf<CachedImagePhase>(
            peek()?.let { CachedImagePhase.Loaded(it.asImageBitmap()) } ?: CachedImagePhase.Loading,
        )
    }
    LaunchedEffect(key) {
        if (state.value is CachedImagePhase.Loaded) return@LaunchedEffect
        state.value = load()?.let { CachedImagePhase.Loaded(it.asImageBitmap()) } ?: CachedImagePhase.Failed
    }
    return state
}

/** A rider's profile photo, or null (initials) until/unless it loads. */
@Composable
fun rememberAvatarPhoto(url: String?): ImageBitmap? {
    val phase by rememberCachedImage(
        key = url,
        peek = { com.danglingmind.convotrack.data.maps.AvatarCache.peek(url) },
        load = { com.danglingmind.convotrack.data.maps.AvatarCache.get(url) },
    )
    return (phase as? CachedImagePhase.Loaded)?.image
}
