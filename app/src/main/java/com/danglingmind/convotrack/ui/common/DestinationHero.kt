package com.danglingmind.convotrack.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.data.maps.PlacePhotoService
import com.google.android.gms.maps.model.LatLng

/**
 * Resolves the destination photo for a ride, as a three-state [DestinationHeroPhase].
 *
 * The key deliberately includes the ride id. `rideId` changes the instant a rider opens a different
 * ride, but the loaded ride object still holds the PREVIOUS destination until its fetch returns —
 * and that stale destination would otherwise hit the cache and render the last ride's photo for a
 * beat. Keying on both means a switched ride resolves from scratch (iOS `destinationPhotoKey`).
 *
 * Returns [DestinationHeroPhase.Resolving] until a lookup settles, so callers can show neither the
 * photo nor the fallback in between — see the doc on [DestinationHeroPhase] for why that matters.
 */
@Composable
fun rememberDestinationHero(
    rideId: String?,
    destinationName: String?,
    lat: Double?,
    lng: Double?,
): State<DestinationHeroPhase> {
    val key = if (rideId.isNullOrEmpty() || destinationName.isNullOrBlank() || lat == null || lng == null) {
        ""
    } else {
        "$rideId|$destinationName|$lat,$lng"
    }

    val phase = remember { mutableStateOf<DestinationHeroPhase>(DestinationHeroPhase.Resolving) }

    LaunchedEffect(key) {
        if (key.isEmpty()) {
            // No ride yet (or it isn't ours yet): stay blank rather than showing the fallback,
            // which would only be replaced a moment later.
            phase.value = DestinationHeroPhase.Resolving
            return@LaunchedEffect
        }
        // Anything held for a different destination is stale the instant the key changes.
        phase.value = DestinationHeroPhase.Resolving
        val fetched = PlacePhotoService.photo(destinationName!!, LatLng(lat!!, lng!!))
        phase.value = fetched?.let { DestinationHeroPhase.Photo(it) } ?: DestinationHeroPhase.Unavailable
    }

    return phase
}

/**
 * Draws a destination photo into a fixed-size slot.
 *
 * [crop] true fills the slot and crops the overflow — right for a wide banner (the home card is a
 * ~16:9 letterbox, almost exactly the aspect Places serves, so nearly nothing is lost).
 *
 * [crop] false shows the WHOLE photo over a blurred, zoomed copy of itself. That is for the lobby's
 * near-square hero, where a plain crop of a 16:9 photo would show only about half its width, and a
 * plain fit would leave two dead bands. The blurred fill is decoration, not information.
 *
 * The photo is drawn as an OVERLAY inside a fixed-size Box rather than being sized directly:
 * an Image sized by its own aspect ratio leaks that ratio outward, so a panorama would widen the
 * whole screen and shift every element beside it. Clipping cannot save that — clipping happens
 * after layout is already decided.
 */
@Composable
fun DestinationPhotoImage(
    bitmap: android.graphics.Bitmap,
    crop: Boolean,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Box(modifier) {
        if (!crop) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
            // Knocks the blurred fill back so it reads as texture behind the real photo rather than
            // competing with it. Also covers API < 31, where `blur` is a no-op.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
