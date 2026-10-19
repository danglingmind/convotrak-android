package com.danglingmind.convotrack.ui.map

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsType

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Off-screen rider indicators — the Android port of iOS `Map/OffscreenRiderIndicators.swift`.
 *
 * During navigation the camera is tight on the rider, so most of the convoy is off-viewport most
 * of the time. Rather than zooming out (which would make the road ahead unreadable), each
 * off-screen rider is projected onto the screen border in their true direction, with their
 * distance underneath.
 */

/**
 * Pure screen geometry for one off-screen rider, produced where the map's `Projection` is
 * reachable. Rider metadata (name/avatar/offline) is joined back in the overlay.
 */
data class EdgeIndicator(
    val id: String,
    /** Clamped point on the screen border, in map-view pixels. */
    val edgeX: Float,
    val edgeY: Float,
    val distanceMeters: Double,
)

/** Marker ids for the non-rider things worth an edge chip. They share the rider tap path. */
object EdgeTarget {
    const val REGROUP_ID = "regroup"
    const val EMERGENCY_ID = "emergency"
}

enum class EdgeKind { RIDER, REGROUP, EMERGENCY }

/** An [EdgeIndicator] joined with the rider's display metadata. */
data class EdgeChipModel(
    val id: String,
    val name: String,
    val avatar: ImageBitmap?,
    val isOffline: Boolean,
    val edgeX: Float,
    val edgeY: Float,
    val distanceMeters: Double,
    val kind: EdgeKind = EdgeKind.RIDER,
    val regroupType: String? = null,
)

/** A group of chips that clamp to nearly the same screen position. */
data class EdgeCluster(
    val id: String,              // stable-ish: nearest member's rider id
    val members: List<EdgeChipModel>,
    val anchorX: Float,
    val anchorY: Float,
    val minDistance: Double,
)

/**
 * Greedy grouping of edge chips whose anchor points fall within [radiusPx] of each other. Members
 * are seeded nearest-first so the closest rider anchors each cluster.
 */
fun clusterIndicators(chips: List<EdgeChipModel>, radiusPx: Float): List<EdgeCluster> {
    // The regroup point and the emergency are never folded into a rider count: they are what the
    // rider is being asked to go to, so each keeps its own chip.
    val special = chips.filter { it.kind != EdgeKind.RIDER }.map {
        EdgeCluster(it.id, listOf(it), it.edgeX, it.edgeY, it.distanceMeters)
    }
    val sorted = chips.filter { it.kind == EdgeKind.RIDER }.sortedBy { it.distanceMeters }
    val assigned = mutableSetOf<String>()
    val clusters = mutableListOf<EdgeCluster>()

    for (seed in sorted) {
        if (seed.id in assigned) continue
        assigned += seed.id
        val members = mutableListOf(seed)
        for (other in sorted) {
            if (other.id in assigned) continue
            if (hypot(other.edgeX - seed.edgeX, other.edgeY - seed.edgeY) <= radiusPx) {
                assigned += other.id
                members += other
            }
        }
        clusters += EdgeCluster(
            id = seed.id,
            members = members,
            anchorX = seed.edgeX,
            anchorY = seed.edgeY,
            minDistance = members.minOf { it.distanceMeters },
        )
    }
    return clusters + special
}

/**
 * Exit point of a ray (from an interior origin, direction dx/dy) through the border of a rect.
 * Slab method; returns null for a degenerate direction.
 */
fun rayRectExit(
    originX: Float, originY: Float,
    dx: Float, dy: Float,
    left: Float, top: Float, right: Float, bottom: Float,
): Pair<Float, Float>? {
    val big = Float.MAX_VALUE
    val tx = when {
        dx > 0 -> (right - originX) / dx
        dx < 0 -> (left - originX) / dx
        else -> big
    }
    val ty = when {
        dy > 0 -> (bottom - originY) / dy
        dy < 0 -> (top - originY) / dy
        else -> big
    }
    val t = minOf(tx, ty)
    if (!t.isFinite() || t < 0) return null
    return (originX + dx * t) to (originY + dy * t)
}

/**
 * Only a coordinate that means "no fix" is dropped — not one that is merely far away.
 *
 * A distance ceiling was tried for this in iOS and was the wrong signal: it suppressed a rider on a
 * simulated location (a phone in Bangalore vs an emulator's default is thousands of km), which
 * silently removed every indicator on a two-device test rig. Distance cannot distinguish "wrong"
 * from "far" — but (0,0) is never a real rider, it is the zero value of a coordinate never set.
 */
fun isUnsetCoordinate(lat: Double, lng: Double): Boolean =
    lat.isNaN() || lng.isNaN() || (Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001)

/**
 * Draws one chip per cluster over the navigation map.
 *
 * Collapsed clusters (>1 rider) show a count and the minimum distance; tapping fans them out into
 * individual chips. Tapping a single or expanded chip flies the camera to that rider.
 */
@Composable
fun OffscreenIndicatorsOverlay(
    clusters: List<EdgeCluster>,
    onTapRider: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedClusterId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val fanStepPx = with(density) { 46.dp.toPx() }

    // Flatten to one entry per chip FIRST, so the content lambda emits exactly one composable per
    // entry in the same order. The measure block then matches measurables to entries by index.
    val entries = remember(clusters, expandedClusterId) {
        buildList {
            clusters.forEach { cluster ->
                val expanded = cluster.id == expandedClusterId && cluster.members.size > 1
                when {
                    expanded -> cluster.members.forEachIndexed { idx, m ->
                        add(ChipEntry(cluster.anchorX, cluster.anchorY, idx, m, null))
                    }
                    cluster.members.size > 1 ->
                        add(ChipEntry(cluster.anchorX, cluster.anchorY, 0, null, cluster))
                    else -> cluster.members.firstOrNull()?.let {
                        add(ChipEntry(cluster.anchorX, cluster.anchorY, 0, it, null))
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        // Tapping anywhere else collapses an expanded fan.
        if (expandedClusterId != null) {
            Box(
                Modifier.matchParentSize().clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { expandedClusterId = null },
            )
        }

        // A Layout, not offset modifiers: each chip's CENTRE has to land on a point that can sit
        // hard against (or past) the overlay's edge, and it must not be constrained to the parent's
        // bounds. Positioning that with padding/offset modifiers silently produced chips that were
        // composed but never placed anywhere visible.
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                entries.forEach { entry ->
                    val member = entry.member
                    val cluster = entry.cluster
                    when {
                        member != null -> EdgeChip(
                            model = member,
                            modifier = Modifier.clickable { onTapRider(member.id) },
                        )
                        cluster != null -> ClusterChip(
                            count = cluster.members.size,
                            distanceMeters = cluster.minDistance,
                            modifier = Modifier.clickable { expandedClusterId = cluster.id },
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            // Measure each chip at its natural size, unbounded by the overlay.
            val placeables = measurables.map { it.measure(Constraints()) }
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            layout(w, h) {
                placeables.forEachIndexed { i, placeable ->
                    val entry = entries.getOrNull(i) ?: return@forEachIndexed
                    // Expanded members stack toward the screen interior from the cluster anchor.
                    var x = entry.anchorX
                    var y = entry.anchorY
                    if (entry.fanIndex > 0) {
                        val dx = w / 2f - entry.anchorX
                        val dy = h / 2f - entry.anchorY
                        val len = maxOf(hypot(dx, dy), 0.001f)
                        val step = entry.fanIndex * fanStepPx
                        x += dx / len * step
                        y += dy / len * step
                    }
                    placeable.place(
                        (x - placeable.width / 2f).roundToInt(),
                        (y - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }
    }
}

/** One chip to draw: where it anchors, its position in an expanded fan, and what it shows. */
private data class ChipEntry(
    val anchorX: Float,
    val anchorY: Float,
    val fanIndex: Int,
    val member: EdgeChipModel?,
    val cluster: EdgeCluster?,
)

private fun formatEdgeDistance(meters: Double): String =
    if (meters < 1000) "${(Math.round(meters / 10) * 10)}m"
    else String.format(java.util.Locale.US, "%.1fkm", meters / 1000)

/**
 * Single off-screen rider: avatar ringed in the theme green, distance underneath.
 *
 * Deliberately card-less. These sit over a moving map, and a filled panel behind each one hides
 * more road than the rider it points at. The ring carries the identity, the shadow keeps both
 * legible over light and dark map tiles, and nothing else competes with the route.
 */
@Composable
fun EdgeChip(model: EdgeChipModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .alpha(if (model.isOffline) 0.55f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = when (model.kind) {
                    EdgeKind.RIDER -> "${model.name}, ${formatEdgeDistance(model.distanceMeters)} away"
                    EdgeKind.REGROUP -> "Regroup point, ${formatEdgeDistance(model.distanceMeters)} away"
                    EdgeKind.EMERGENCY -> "Emergency, ${formatEdgeDistance(model.distanceMeters)} away"
                }
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (model.kind != EdgeKind.RIDER) {
            // A place, not a person: an icon in a solid disc.
            val (icon, color, fg) = when (model.kind) {
                EdgeKind.EMERGENCY -> Triple(Icons.Filled.Warning, DsColor.danger, Color.White)
                else -> Triple(regroupEdgeIcon(model.regroupType), regroupEdgeColor(model.regroupType), DsColor.onAccent)
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(color)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)) }
        } else Box(
            Modifier.size(34.dp).clip(CircleShape).background(DsColor.avatar)
                .border(2.dp, DsColor.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = model.avatar
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                )
            } else {
                androidx.compose.material3.Text(
                    model.name.take(1).uppercase(),
                    style = DsType.body.copy(fontSize = 14.sp),
                    color = DsColor.textPrimary,
                )
            }
        }
        androidx.compose.material3.Text(
            formatEdgeDistance(model.distanceMeters),
            style = DsType.dataMono,
            color = DsColor.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * Collapsed cluster: rider count in a ringed circle, minimum distance underneath. Same card-less
 * treatment as [EdgeChip], so a cluster reads as "several of those" rather than as a different
 * kind of object.
 */
@Composable
fun ClusterChip(count: Int, distanceMeters: Double, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(DsColor.avatar)
                .border(2.dp, DsColor.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                "$count",
                style = DsType.dataMono.copy(fontSize = 15.sp),
                color = DsColor.textPrimary,
            )
        }
        androidx.compose.material3.Text(
            formatEdgeDistance(distanceMeters),
            style = DsType.dataMono,
            color = DsColor.textPrimary,
            maxLines = 1,
        )
    }
}

private fun regroupEdgeIcon(type: String?) = when (type) {
    "FUEL" -> Icons.Filled.LocalGasStation
    "FOOD" -> Icons.Filled.Restaurant
    "SCENIC" -> Icons.Filled.PhotoCamera
    else -> Icons.Filled.PanTool
}

private fun regroupEdgeColor(type: String?) = when (type) {
    "FUEL" -> DsColor.catFuel
    "FOOD" -> DsColor.catFood
    "SCENIC" -> DsColor.catScenic
    else -> DsColor.catOther
}
