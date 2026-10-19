package com.danglingmind.convotrack.ui.common

import com.danglingmind.convotrack.data.imagecache.RideDestinationStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.DestinationHeroPhase
import com.danglingmind.convotrack.data.net.HistoryRide
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.LeaderBadge
import com.danglingmind.convotrack.ui.designsystem.PillButton
import com.danglingmind.convotrack.ui.designsystem.PillTone
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Full rides by id, for the session. History rows carry no destination coordinate, so a row's
 * thumbnail needs the full ride; caching it means revisiting Home or History costs no calls.
 */
object RideDetailsCache {
    private val rides = ConcurrentHashMap<String, Ride>()

    suspend fun get(rideId: String): Ride? =
        rides[rideId] ?: runCatching { apiCall { ServiceLocator.api.getRide(rideId) } }.getOrNull()?.also { rides[rideId] = it }

    /** Drop a ride whose details changed (edited, ended). */
    fun invalidate(rideId: String) {
        rides.remove(rideId)
        RideDestinationStore.forget(rideId)
    }

    /**
     * Where the ride was going. Online, the fetched ride wins (and is remembered on disk); offline,
     * the last remembered destination still finds its cached photo.
     */
    suspend fun destination(rideId: String): RideDestinationStore.Destination? {
        get(rideId)?.takeIf { it.destinationName.isNotEmpty() }?.let { ride ->
            val d = RideDestinationStore.Destination(ride.destinationName, ride.destinationLat, ride.destinationLng)
            RideDestinationStore.put(rideId, d)
            return d
        }
        return RideDestinationStore.get(rideId)
    }
}

/** "Oct 6, 2026" from the best available timestamp, or "" when unknown. */
fun HistoryRide.longDateLabel(): String {
    val iso = endedAt ?: startedAt ?: createdAt ?: return ""
    val millis = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
}

/**
 * Destination photo for a ride, or a quiet route tile when there is none. [onAttribution] receives
 * the photographer credit, which Google's Places policy requires on screen with the photo — too
 * small to print on the thumbnail, so lists show the credits in a footnote ([PhotoCredits]).
 */
@Composable
fun RideThumbnail(
    rideId: String,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 50.dp,
    onAttribution: (String?) -> Unit = {},
) {
    var destination by remember(rideId) { mutableStateOf<RideDestinationStore.Destination?>(null) }
    LaunchedEffect(rideId) { destination = RideDetailsCache.destination(rideId) }
    val phase by rememberDestinationHero(
        rideId = destination?.let { rideId },
        destinationName = destination?.name,
        lat = destination?.lat,
        lng = destination?.lng,
    )
    LaunchedEffect(phase) { onAttribution((phase as? DestinationHeroPhase.Photo)?.photo?.attribution) }
    Box(
        modifier.size(width, height).clip(DsShape.xs).background(DsColor.iconDisc),
        contentAlignment = Alignment.Center,
    ) {
        when (val p = phase) {
            is DestinationHeroPhase.Photo -> DestinationPhotoImage(p.photo.bitmap, crop = true, modifier = Modifier.fillMaxSize())
            DestinationHeroPhase.Unavailable -> Icon(Icons.Filled.Route, null, tint = DsColor.accentDeep, modifier = Modifier.size(24.dp))
            DestinationHeroPhase.Resolving -> Unit
        }
    }
}

/**
 * Recent-ride / history row: thumbnail, title, distance (+ LEADER when you led), date and duration,
 * then RESUME for rides still running and a chevron.
 */
@Composable
fun RideRowCard(
    ride: HistoryRide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onResume: (() -> Unit)? = null,
    onAttribution: (String?) -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .glassSurface(DsShape.md, DsColor.cardQuiet, DsColor.borderHairline)
            .dsClickable(pressedScale = 0.99f, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RideThumbnail(ride.rideId, onAttribution = onAttribution)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(ride.title, style = DsType.rowTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Route, null, tint = DsColor.textSecondary, modifier = Modifier.size(14.dp))
                Text(ride.distanceLabel(), style = DsType.dataMono, color = DsColor.textSecondary, maxLines = 1, softWrap = false)
                if (ride.isLeader == true) LeaderBadge()
            }
            val date = ride.longDateLabel()
            val meta = listOfNotNull(date.ifEmpty { null }, if (ride.isCompleted) ride.durationLabel() else null).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = DsType.bodySmall, color = DsColor.textMuted, maxLines = 1)
        }
        if (!ride.isCompleted) {
            PillButton("Resume", onClick = onResume ?: onClick, tone = PillTone.Tonal, height = 28.dp, horizontalPadding = 12.dp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = DsColor.textMuted, modifier = Modifier.size(20.dp))
    }
}

/** Placeholder rows while a list loads. */
@Composable
fun RideRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 76.dp).glassSurface(DsShape.md, DsColor.cardQuiet, DsColor.borderHairline).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(72.dp, 50.dp).clip(DsShape.xs).background(DsColor.iconDisc))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).heightIn(min = 12.dp).clip(DsShape.pill).background(DsColor.iconDisc))
            Box(Modifier.fillMaxWidth(0.35f).heightIn(min = 10.dp).clip(DsShape.pill).background(DsColor.iconDisc))
        }
        Icon(Icons.Outlined.Flag, null, tint = DsColor.textSubtle, modifier = Modifier.padding(end = 4.dp).size(18.dp))
    }
}

/** "Photos: A, B · Google" under a list of rows with destination photos. Nothing when empty. */
@Composable
fun PhotoCredits(credits: Collection<String>, modifier: Modifier = Modifier) {
    val names = credits.filter { it.isNotBlank() }.distinct()
    if (names.isEmpty()) return
    Text(
        "Photos: ${names.joinToString(", ")} · Google",
        style = DsType.micro,
        color = DsColor.textMuted,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
