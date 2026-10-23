package com.danglingmind.convotrack.ui.ride.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.maps.PlaceResult
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.SecondaryButton
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * A spot on the map the rider is pointing at: a dropped pin, or the centre of the map while
 * choosing on it (iOS PlaceCandidate). Its street name arrives a moment later from geocoding.
 */
internal data class PlaceCandidate(
    val position: LatLng,
    val place: PlaceResult? = null,
    val resolving: Boolean = true,
) {
    /** What the stop is saved as. Falls back to a plain label when the lookup found nothing, so a
     *  spot in the middle of nowhere can still be a stop. */
    val resolvedPlace: PlaceResult
        get() = place ?: PlaceResult(
            id = String.format(Locale.US, "pin_%.5f_%.5f", position.latitude, position.longitude),
            name = "Pinned location",
            latLng = position,
            address = null,
        )

    val coordinateText: String get() = String.format(Locale.US, "%.5f, %.5f", position.latitude, position.longitude)
}

internal data class PlaceAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Sheet header for a [PlaceCandidate]: its name and address, then what to do with it. The first
 * action is the primary one; the rest sit beside it as secondary buttons. A fixed height (name on
 * one line, two lines kept for the address): while choosing on the map the text changes on every
 * pan, and a card that changed height with it would move the map, and the spot under the pin.
 */
@Composable
internal fun PlannerPlaceCard(
    eyebrow: String,
    candidate: PlaceCandidate?,
    actions: List<PlaceAction>,
    onClose: () -> Unit,
    emptyMessage: String = "Move the map to choose a spot",
) {
    val ready = candidate != null && !candidate.resolving
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow.uppercase(Locale.getDefault()), style = DsType.eyebrow, color = DsColor.accent)
                if (candidate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            candidate.place?.name ?: if (candidate.resolving) "Finding address…" else "Pinned location",
                            style = DsType.rowTitle,
                            color = DsColor.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (candidate.resolving) CircularProgressIndicator(Modifier.size(14.dp), color = DsColor.accent, strokeWidth = 2.dp)
                    }
                    Text(
                        candidate.place?.address ?: candidate.coordinateText,
                        style = DsType.helper,
                        color = DsColor.textMuted,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(emptyMessage, style = DsType.rowTitle, color = DsColor.textSecondary)
                }
            }
            Box(
                Modifier.size(44.dp).dsClickable(onClickLabel = "Close", onClick = onClose).semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x24FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, tint = DsColor.textPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        actions.firstOrNull()?.let { primary ->
            PrimaryButton(text = primary.title, icon = primary.icon, enabled = ready, onClick = primary.onClick)
        }
        if (actions.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.drop(1).forEach { action ->
                    SecondaryButton(text = action.title, icon = action.icon, enabled = ready, onClick = action.onClick, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
