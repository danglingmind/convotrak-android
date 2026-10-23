package com.danglingmind.convotrack.ui.ride.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsHaptic
import com.danglingmind.convotrack.ui.designsystem.DsRadius
import com.danglingmind.convotrack.ui.designsystem.dsHaptic
import com.danglingmind.convotrack.ui.designsystem.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SheetDetent(val label: String) { LOW("Collapsed"), MEDIUM("Half height"), LARGE("Full height") }

private val GRABBER_HEIGHT = 22.dp

/**
 * A resizable sheet resting over a full-bleed map, like the route card in Maps (iOS DSMapSheet):
 * the map behind it keeps working, and it is never dismissed, only lowered.
 *
 * [header] is always on screen and sets the low detent's height, so the resting sheet grows with
 * the font size instead of clipping. [content] scrolls beneath it once the sheet is raised. Drag
 * the grabber or header to resize; tap the grabber to step through the detents; TalkBack gets
 * Expand / Collapse actions on it.
 *
 * Not a Material bottom sheet: that offers two resting heights, and these screens also show the
 * paywall and dialogs over the planner.
 */
@Composable
fun PlannerSheet(
    detent: SheetDetent,
    onDetentChange: (SheetDetent) -> Unit,
    /** The low detent's full height, for map padding: the map keeps its logo and camera framing
     *  above the sheet's lowest resting position. */
    onRestingHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    /** Floats just above the sheet's top edge, trailing (the recentre button). Hidden when raised
     *  all the way. */
    accessory: @Composable () -> Unit = {},
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var headerPx by remember { mutableFloatStateOf(0f) }
    var bottomInsetPx by remember { mutableFloatStateOf(0f) }
    val currentDetent by rememberUpdatedState(detent)

    // Sits above the keyboard; the navigation bar inset is drawn inside the sheet instead, so the
    // sheet's colour runs under it.
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
        val available = constraints.maxHeight.toFloat()
        val grabberPx = with(density) { GRABBER_HEIGHT.toPx() }
        val low = minOf(grabberPx + headerPx + bottomInsetPx + with(density) { 8.dp.toPx() }, available)
        val medium = minOf(maxOf(low, available * 0.52f), available)
        val heights = listOf(low, medium, available)
        val target = heights[detent.ordinal]

        val height = remember { Animatable(target) }
        var dragging by remember { mutableStateOf(false) }

        LaunchedEffect(low) { onRestingHeightChange(with(density) { low.toDp() }) }
        // Detent changes and header re-measures settle the sheet, unless a finger is on it.
        LaunchedEffect(target) {
            if (dragging) return@LaunchedEffect
            if (reduceMotion || height.value == 0f) height.snapTo(target)
            else height.animateTo(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }

        fun step(delta: Int) {
            val next = (currentDetent.ordinal + delta).coerceIn(0, SheetDetent.entries.lastIndex)
            if (next == currentDetent.ordinal) return
            view.dsHaptic(DsHaptic.Selection)
            onDetentChange(SheetDetent.entries[next])
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { height.snapTo((height.value - delta).coerceIn(low * 0.85f, available)) }
        }
        val dragModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = { dragging = true },
            onDragStopped = { velocity ->
                dragging = false
                // Project where a fling was heading and settle on the nearest detent.
                val projected = height.value - velocity * 0.18f
                val nearest = heights.indices.minByOrNull { abs(heights[it] - projected) } ?: 0
                val goal = heights[nearest]
                onDetentChange(SheetDetent.entries[nearest])
                if (reduceMotion) height.snapTo(goal) else height.animateTo(goal, spring(dampingRatio = 0.82f))
            },
        )

        val sheetHeight = with(density) { height.value.toDp() }
        val shape = RoundedCornerShape(topStart = DsRadius.nav, topEnd = DsRadius.nav)

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp)
                .offset(y = -(sheetHeight + 12.dp))
                .alpha(if (detent == SheetDetent.LARGE) 0f else 1f),
        ) {
            if (detent != SheetDetent.LARGE) accessory()
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .shadow(16.dp, shape, clip = false)
                .clip(shape)
                .background(DsColor.sheet)
                .border(0.5.dp, DsColor.borderSubtle, shape),
        ) {
            Column(dragModifier) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(GRABBER_HEIGHT)
                        .semantics {
                            contentDescription = "Sheet grabber"
                            stateDescription = detent.label
                            onClick(label = "Resize") { step(if (detent == SheetDetent.LARGE) -2 else 1); true }
                            customActions = listOf(
                                CustomAccessibilityAction("Expand") { step(1); true },
                                CustomAccessibilityAction("Collapse") { step(-1); true },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(DsColor.textSubtle))
                }
                Column(Modifier.onSizeChanged { headerPx = it.height.toFloat() }, content = header)
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .alpha(if (detent == SheetDetent.LOW && !dragging) 0f else 1f)
                    .verticalScroll(rememberScrollState(), enabled = detent != SheetDetent.LOW),
                content = content,
            )
            Spacer(
                Modifier
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .onSizeChanged { bottomInsetPx = it.height.toFloat() },
            )
        }
    }
}
