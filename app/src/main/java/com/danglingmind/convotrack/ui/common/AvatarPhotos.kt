package com.danglingmind.convotrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.danglingmind.convotrack.data.maps.AvatarCache

/** A rider's profile photo from [AvatarCache], or null (initials) until/unless it loads. */
@Composable
fun rememberAvatarPhoto(url: String?): ImageBitmap? {
    var photo by remember(url) { mutableStateOf(AvatarCache.peek(url)?.asImageBitmap()) }
    LaunchedEffect(url) {
        if (photo == null && !url.isNullOrBlank()) photo = AvatarCache.get(url)?.asImageBitmap()
    }
    return photo
}
