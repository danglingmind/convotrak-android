package com.danglingmind.convotrack.ui.ride.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import kotlin.math.roundToInt

private val ROW_SPACING = 4.dp

/**
 * The planner's top card once a destination is chosen (iOS PlannerStopsCard): every stop in riding
 * order, each tappable to choose again. Swap for a two-stop ride; with stops between, drag handles
 * and remove buttons. Long lists fold to start, "N stops", destination so the card doesn't bury the
 * map. [expanded] is owned by the screen because the card leaves the screen while a search is open.
 */
@Composable
internal fun PlannerStopsCard(
    state: PlannerState,
    onBack: () -> Unit,
    backLabel: String,
    onEdit: (Long) -> Unit,
    onAddStop: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val folded = state.stops.size > 3 && !expanded
    val editable = state.canRemoveStops && !folded
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragConsumed by remember { mutableFloatStateOf(0f) }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { ROW_SPACING.toPx() }
    val pitch = rowHeightPx + spacingPx

    fun dragBy(id: Long, totalY: Float) {
        var offset = totalY - dragConsumed
        val index = state.stops.indexOfFirst { it.id == id }
        if (offset > pitch / 2 && index < state.stops.lastIndex) {
            view.dsHaptic(DsHaptic.Selection)
            state.moveStop(id, 1, reroute = false)
            dragConsumed += pitch
            offset -= pitch
        } else if (offset < -pitch / 2 && index > 0) {
            view.dsHaptic(DsHaptic.Selection)
            state.moveStop(id, -1, reroute = false)
            dragConsumed -= pitch
            offset += pitch
        }
        dragOffset = offset
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(DsColor.nav, DsShape.xl)
            .border(1.dp, DsColor.border, DsShape.xl)
            .padding(vertical = 8.dp)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 44.dp).dsClickable(onClickLabel = backLabel, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
            if (folded) {
                StopRow(state, state.stops.first(), editable = false, onEdit = onEdit)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .dsClickable(onClickLabel = "Show every stop") { onExpandedChange(true) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.MoreHoriz, null, tint = DsColor.textMuted, modifier = Modifier.size(16.dp))
                    Text("${state.stops.size - 2} stops", style = DsType.body, color = DsColor.textSecondary, modifier = Modifier.weight(1f))
                    Text("Show all", style = DsType.link, color = DsColor.accent)
                }
                StopRow(state, state.stops.last(), editable = false, onEdit = onEdit)
            } else {
                // Keyed, so a row that changes slot mid-drag keeps its composition (and its gesture).
                state.stops.forEach { stop -> key(stop.id) {
                    val isDragging = draggingId == stop.id
                    StopRow(
                        state, stop, editable, onEdit,
                        modifier = Modifier
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                            .then(if (isDragging) Modifier.shadow(12.dp, DsShape.sm) else Modifier),
                        handle = Modifier.pointerInput(stop.id) {
                            var total = 0f
                            detectDragGestures(
                                onDragStart = {
                                    total = 0f
                                    dragConsumed = 0f
                                    draggingId = stop.id
                                    view.dsHaptic(DsHaptic.Selection)
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    state.finishReorder()
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    state.finishReorder()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    total += amount.y
                                    dragBy(stop.id, total)
                                },
                            )
                        },
                    )
                } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.canAddStop) {
                    Row(
                        Modifier
                            .heightIn(min = 40.dp)
                            .dsClickable(onClickLabel = "Add stop") {
                                onExpandedChange(true)
                                onAddStop()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = DsColor.accent, modifier = Modifier.size(18.dp))
                        Text("Add stop", style = DsType.link, color = DsColor.accent)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.stops.size > 3 && expanded) {
                    Text(
                        "Done",
                        style = DsType.link,
                        color = DsColor.textSecondary,
                        modifier = Modifier.heightIn(min = 40.dp).dsClickable(onClickLabel = "Fold the stop list") { onExpandedChange(false) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (state.stops.size == 2) {
            Box(
                Modifier
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { (rowHeightPx * 2 + spacingPx).toDp() })
                    .width(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp).dsClickable(onClickLabel = "Swap start and destination") {
                        view.dsHaptic(DsHaptic.Selection)
                        state.swapEnds()
                    }.semantics { contentDescription = "Swap start and destination" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.SwapVert, null, tint = DsColor.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    state: PlannerState,
    stop: PlannerStop,
    editable: Boolean,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
) {
    val role = state.role(stop)
    val name = if (stop.isChosen) state.name(stop) else placeholder(role)
    val index = state.stops.indexOfFirst { it.id == stop.id }
    val label = when (role) {
        StopRole.START -> "Start, $name"
        StopRole.DESTINATION -> "Destination, $name"
        StopRole.STOP -> "Stop $index, $name"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(DsShape.sm)
                .background(DsColor.field)
                .dsClickable(pressedScale = 0.99f, onClickLabel = "Choose a different place") { onEdit(stop.id) }
                .semantics {
                    contentDescription = label
                    if (editable) {
                        customActions = buildList {
                            if (role != StopRole.START) add(CustomAccessibilityAction("Move up") { state.moveStop(stop.id, -1); true })
                            if (role != StopRole.DESTINATION) add(CustomAccessibilityAction("Move down") { state.moveStop(stop.id, 1); true })
                            add(CustomAccessibilityAction("Remove") { state.removeStop(stop.id); true })
                        }
                    }
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StopMarker(role)
            Text(
                name,
                style = DsType.body,
                color = if (stop.isChosen) DsColor.textPrimary else DsColor.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editable) {
            Box(handle.size(width = 40.dp, height = 44.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragHandle, null, tint = DsColor.textMuted, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(width = 40.dp, height = 44.dp).dsClickable(onClickLabel = "Remove ${state.name(stop)}") { state.removeStop(stop.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Remove ${state.name(stop)}", tint = DsColor.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun placeholder(role: StopRole) = when (role) {
    StopRole.START -> "Choose a start point"
    StopRole.STOP -> "Add a stop"
    StopRole.DESTINATION -> "Choose a destination"
}

/** Start dot, stop ring, destination flag: the same marks as the map's pins. */
@Composable
internal fun StopMarker(role: StopRole) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (role) {
            StopRole.START -> Box(Modifier.size(10.dp).clip(CircleShape).background(DsColor.mapStart))
            StopRole.STOP -> Box(
                Modifier.size(10.dp).clip(CircleShape).background(DsColor.accent).padding(1.5.dp).clip(CircleShape).background(DsColor.mapStop),
            )
            StopRole.DESTINATION -> Icon(Icons.Filled.Flag, null, tint = DsColor.accent, modifier = Modifier.size(14.dp))
        }
    }
}
