package com.danglingmind.convotrack.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - Surfaces

/** Glass card surface: background, hairline border, clip. */
fun Modifier.glassSurface(
    shape: Shape = DsShape.md,
    background: Color = DsColor.card,
    border: Color = DsColor.borderSubtle,
): Modifier = this.clip(shape).background(background).border(1.dp, border, shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = DsSpace.x4,
    shape: Shape = DsShape.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().glassSurface(shape).padding(padding), content = content)
}

// MARK: - Icon disc

@Composable
fun IconDisc(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = DsColor.iconDisc,
    tint: Color = DsColor.accent,
) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// MARK: - Metric / stat

/** EST. TIME / DISTANCE: icon disc + label over value. */
@Composable
fun MetricCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 64.dp).glassSurface(DsShape.md).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconDisc(icon, size = 40.dp, iconSize = 22.dp)
        Column {
            Text(label.uppercase(), style = DsType.statLabel, color = DsColor.textMuted, maxLines = 1)
            Text(value, style = DsType.metricValue, color = DsColor.textPrimary, maxLines = 1)
        }
    }
}

enum class StatTone { Default, Success, Warning, Code }

/** Lobby tile: icon + tracked label, value below. Width follows content. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.Default,
) {
    val accent = when (tone) {
        StatTone.Success -> DsColor.success
        StatTone.Warning -> DsColor.warning
        else -> null
    }
    Column(
        modifier.heightIn(min = 64.dp).glassSurface(DsShape.md).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent ?: DsColor.textSecondary, modifier = Modifier.size(20.dp))
            Text(label.uppercase(), style = DsType.statLabel, color = DsColor.textMuted, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (accent != null) Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                if (tone == StatTone.Code) value.uppercase() else value,
                style = if (tone == StatTone.Code) DsType.codeValue else DsType.statValue,
                color = accent ?: DsColor.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = if (accent == null) 28.dp else 0.dp),
            )
        }
    }
}

// MARK: - Chips and badges

/** Distance / duration chip on photo cards. */
@Composable
fun MetaChip(icon: ImageVector, text: String, modifier: Modifier = Modifier, onPhoto: Boolean = true) {
    Row(
        modifier
            .height(26.dp)
            .clip(DsShape.pill)
            .background(if (onPhoto) DsColor.chipOnPhoto else DsColor.card)
            .border(1.dp, if (onPhoto) Color(0x29FFFFFF) else DsColor.border, DsShape.pill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = DsColor.textPrimary, modifier = Modifier.size(15.dp))
        Text(text.uppercase(), style = DsType.dataMono.sized(12.sp), color = DsColor.textPrimary, maxLines = 1)
    }
}

/** Glass pill on a photo: pin + destination + chevron. */
@Composable
fun DestinationPill(
    text: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier
            .height(32.dp)
            .clip(DsShape.pill)
            .background(DsColor.glassPill)
            .border(1.dp, DsColor.borderOnPhoto, DsShape.pill)
            .padding(start = 10.dp, end = if (trailingIcon != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(leadingIcon, null, tint = DsColor.accent, modifier = Modifier.size(18.dp))
        Text(text, style = DsType.body.copy(fontWeight = FontWeight.Medium), color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (trailingIcon != null) Icon(trailingIcon, null, tint = DsColor.textSecondary, modifier = Modifier.size(18.dp))
    }
}

enum class RosterState { Ready, Waiting, Riding }

/** READY / WAITING / RIDING — the app's three roster states, solid mono badges. */
@Composable
fun StatusBadge(state: RosterState, modifier: Modifier = Modifier) {
    val (text, bg, fg) = when (state) {
        RosterState.Ready -> Triple("READY", DsColor.accent, DsColor.onAccent)
        RosterState.Waiting -> Triple("WAITING", DsColor.neutralBadge, DsColor.textMuted)
        RosterState.Riding -> Triple("RIDING", DsColor.success, DsColor.onSuccess)
    }
    Box(
        modifier.height(24.dp).clip(DsShape.pill).background(bg).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DsType.badge.sized(11.sp), color = fg, maxLines = 1)
    }
}

/** LEADER on ride rows the user led. */
@Composable
fun LeaderBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(DsShape.pill).background(DsColor.accent).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("LEADER", style = DsType.badgeSmall, color = DsColor.onAccent, maxLines = 1, softWrap = false)
    }
}

/** Small mono chip on a coloured fill (YOU, CODE VERIFIED). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, background: Color = DsColor.accent, color: Color = DsColor.onAccent) {
    Box(modifier.clip(DsShape.pill).background(background).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text.uppercase(), style = DsType.badgeSmall, color = color, maxLines = 1, softWrap = false)
    }
}

// MARK: - Avatar

/**
 * Rider avatar: photo when available, else an initial on a colour picked from [colorKey].
 * [online] null hides the presence dot.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    photo: ImageBitmap? = null,
    colorKey: String = name,
    online: Boolean? = null,
    ring: Color? = null,
) {
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(DsColor.avatarColorFor(colorKey))
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Text(
                    name.trim().take(1).uppercase().ifEmpty { "?" },
                    style = DsType.bodyStrong.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium, lineHeight = (size.value * 0.5f).sp),
                    color = Color.White,
                )
            }
        }
        if (online != null) {
            val dot = (size.value * 0.27f).coerceAtLeast(9f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(dot)
                    .clip(CircleShape)
                    .background(DsColor.bgBase)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (online) DsColor.success else DsColor.textSubtle),
            )
        }
    }
}

/** Overlapping avatars with a +N overflow disc. */
@Composable
fun AvatarStack(names: List<Pair<String, String>>, modifier: Modifier = Modifier, maxShown: Int = 2, size: Dp = 28.dp) {
    val shown = names.take(maxShown)
    val extra = names.size - shown.size
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        shown.forEach { (id, name) ->
            Avatar(name, size = size, colorKey = id, ring = DsColor.bgBase)
        }
        if (extra > 0) {
            Box(
                Modifier.size(size).clip(CircleShape).background(DsColor.glassOnPhoto).border(2.dp, DsColor.bgBase, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$extra", style = DsType.helper.copy(fontWeight = FontWeight.SemiBold), color = DsColor.textPrimary)
            }
        }
    }
}

// MARK: - Details list

data class DetailRow(val icon: ImageVector, val label: String, val value: String, val action: (@Composable () -> Unit)? = null)

/** Icon · muted label column · value, with hairline dividers (lobby RIDE DETAILS). */
@Composable
fun DetailsList(rows: List<DetailRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().glassSurface(DsShape.md, DsColor.cardQuiet, DsColor.borderHairline)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.padding(start = 50.dp).fillMaxWidth().height(1.dp).background(DsColor.divider))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon, null, tint = DsColor.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(row.label, style = DsType.detailLabel, color = DsColor.textMuted, modifier = Modifier.width(118.dp), maxLines = 1)
                Text(row.value, style = DsType.detailValue, color = DsColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                row.action?.invoke()
            }
        }
    }
}

// MARK: - Accent bar for the leader row

/** 3dp accent bar down the start edge of a row (the leader's roster row). Apply after `clip`. */
fun Modifier.accentBar(color: Color = DsColor.accent): Modifier = this.drawWithContent {
    drawContent()
    drawRect(color, size = Size(3.dp.toPx(), size.height))
}
