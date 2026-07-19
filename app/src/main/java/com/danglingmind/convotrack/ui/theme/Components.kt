package com.danglingmind.convotrack.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R

// MARK: - Reusable modifiers/components (ported from iOS ConvoTrackTheme.swift)

/** Semi-transparent surface with a subtle outline — the iOS `GlassCard` modifier. */
fun Modifier.glassCard() = this
    .background(CT.surfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
    .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))

/** Full-width lime CTA — the iOS `LimePrimaryButton` modifier. */
@Composable
fun LimePrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) CT.primaryFixed else CT.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CTType.labelCaps,
            color = if (enabled) CT.onPrimaryFixed else CT.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
    }
}

/** Standard top bar with logo + antenna icon. */
@Composable
fun ConvoTrackTopBar(
    title: String,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(CT.surfaceContainer.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CT.primaryFixed,
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack?.invoke() }
                    .padding(10.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.logo_circle),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = title,
            style = CTType.headlineLg,
            color = CT.primaryFixed,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.SettingsInputAntenna,
            contentDescription = null,
            tint = CT.primaryFixed,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Stat chip for map overlays — the iOS `FloatingStatPill`. */
@Composable
fun FloatingStatPill(
    label: String,
    value: String,
    unit: String? = null,
    dotColor: Color? = null,
) {
    Column(
        modifier = Modifier
            .background(CT.surfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .border(1.dp, CT.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (dotColor != null) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dotColor))
            }
            Text(
                value,
                style = CTType.dataMono.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = dotColor ?: CT.onSurface,
            )
            if (unit != null) {
                Text(unit, style = CTType.captionMd.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = CT.onSurfaceVariant)
            }
        }
    }
}

// MARK: - Bottom Nav (FLAGGED / TRACK / PROFILE)

enum class CTTab { FLAGGED, TRACK, PROFILE }

@Composable
fun ConvoTrackBottomNav(
    activeTab: CTTab,
    onSelect: (CTTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(CT.surfaceContainerLowest.copy(alpha = 0.95f))
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navItem(Icons.Outlined.Flag, CTTab.FLAGGED, activeTab, onSelect)
        navItem(Icons.Outlined.NearMe, CTTab.TRACK, activeTab, onSelect)
        navItem(Icons.Outlined.Person, CTTab.PROFILE, activeTab, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.navItem(
    icon: ImageVector,
    tab: CTTab,
    activeTab: CTTab,
    onSelect: (CTTab) -> Unit,
) {
    val isActive = activeTab == tab
    val bg by animateFloatAsState(if (isActive) 1f else 0f, spring(), label = "pill")
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(CT.primaryFixed.copy(alpha = bg))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.name,
                tint = if (isActive) CT.onPrimaryFixed else CT.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// Small helper mirrored from SwiftUI's `.lineLimit(1)` truncation usage.
val ellipsis = TextOverflow.Ellipsis
