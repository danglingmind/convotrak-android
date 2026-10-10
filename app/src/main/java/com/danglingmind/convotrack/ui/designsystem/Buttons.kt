package com.danglingmind.convotrack.ui.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Press feedback + haptics

/** Clickable with the design system's press treatment: a bounded ripple and a slight scale-down. */
@Composable
fun Modifier.dsClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) pressedScale else 1f, label = "press")
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = ripple(color = DsColor.textPrimary),
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/** Haptics from `interaction.haptics`. */
enum class DsHaptic { Primary, Selection, Destructive }

fun View.dsHaptic(kind: DsHaptic) {
    val constant = when (kind) {
        DsHaptic.Primary ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        DsHaptic.Selection -> HapticFeedbackConstants.CLOCK_TICK
        DsHaptic.Destructive ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

// MARK: - Full-width buttons

/**
 * The one accent action per screen. Flat accent, accent glow, a dark glyph left of the label.
 * Disabled keeps its colour at 42% so it still reads as the screen's main action.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val view = LocalView.current
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DsSize.primaryButton)
            .then(
                if (enabled) Modifier.shadow(16.dp, DsShape.lg, ambientColor = DsColor.accentGlow, spotColor = DsColor.accent)
                else Modifier,
            )
            .clip(DsShape.lg)
            .background(if (enabled) DsColor.accent else DsColor.accent.copy(alpha = 0.42f))
            .dsClickable(enabled = active, pressedScale = 0.985f) {
                view.dsHaptic(DsHaptic.Primary)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = DsColor.onAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = DsColor.onAccent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Text(text.uppercase(), style = DsType.button, color = DsColor.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

enum class SecondaryTone { Neutral, Confirmed }

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: SecondaryTone = SecondaryTone.Neutral,
) {
    val (bg, border, fg) = when (tone) {
        SecondaryTone.Neutral -> Triple(Color(0x12FFFFFF), Color(0x24FFFFFF), DsColor.textPrimary)
        SecondaryTone.Confirmed -> Triple(DsColor.successTint, DsColor.successBorder, DsColor.success)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DsSize.secondaryButton)
            .clip(DsShape.lg)
            .background(bg)
            .border(1.dp, border, DsShape.lg)
            .dsClickable(enabled = enabled && !loading, pressedScale = 0.985f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(if (enabled || tone == SecondaryTone.Confirmed) Modifier else Modifier.dsAlpha(0.42f)),
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text.uppercase(), style = DsType.button.sized(DsType.buttonWide.fontSize).tracked(1.6f), color = fg, maxLines = 1)
            }
        }
    }
}

/** Irreversible actions. Tinted, never filled red, and always behind a ConfirmDialog. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DsSize.secondaryButton)
            .clip(DsShape.lg)
            .background(DsColor.dangerTint)
            .border(1.dp, DsColor.dangerBorder, DsShape.lg)
            .dsClickable(enabled = enabled, pressedScale = 0.985f) {
                view.dsHaptic(DsHaptic.Destructive)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = DsType.buttonWide,
            color = DsColor.danger,
            modifier = if (enabled) Modifier else Modifier.dsAlpha(0.42f),
        )
    }
}

/** Low-emphasis full-width exit, e.g. CANCEL REQUEST. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(DsShape.md)
            .dsClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = DsType.buttonWide, color = DsColor.textSecondary)
    }
}

// MARK: - Pills

enum class PillTone { Primary, Tonal, Neutral, Danger }

/** Compact inline action: RESUME, UPGRADE, RETRY, REGROUP, MARK AS ARRIVED. */
@Composable
fun PillButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Primary,
    icon: ImageVector? = null,
    height: Dp = 34.dp,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    val (bg, fg, border) = when (tone) {
        PillTone.Primary -> Triple(DsColor.accent, DsColor.onAccent, Color.Transparent)
        PillTone.Tonal -> Triple(DsColor.accentTonal, DsColor.accent, Color.Transparent)
        PillTone.Neutral -> Triple(DsColor.card, DsColor.textSecondary, DsColor.border)
        PillTone.Danger -> Triple(DsColor.dangerTint, DsColor.danger, DsColor.dangerBorder)
    }
    Row(
        modifier = modifier
            .heightIn(min = height)
            .then(
                if (tone == PillTone.Primary) Modifier.shadow(10.dp, DsShape.pill, ambientColor = DsColor.accentGlow, spotColor = DsColor.accent)
                else Modifier,
            )
            .clip(DsShape.pill)
            .background(bg)
            .border(1.dp, border, DsShape.pill)
            .then(if (onClick != null) Modifier.dsClickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text.uppercase(), style = DsType.buttonPill, color = fg, maxLines = 1)
    }
}

enum class TextActionTone { Confirm, Dismiss, Destructive }

/** Dialog actions and inline links. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: TextActionTone = TextActionTone.Confirm,
    enabled: Boolean = true,
) {
    val color = when (tone) {
        TextActionTone.Confirm -> DsColor.accent
        TextActionTone.Dismiss -> DsColor.textSecondary
        TextActionTone.Destructive -> DsColor.danger
    }
    Box(
        modifier
            .heightIn(min = DsSize.touchTarget)
            .clip(DsShape.pill)
            .dsClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = DsType.buttonPill, color = if (enabled) color else DsColor.textDisabled)
    }
}

// MARK: - Icon buttons

enum class FloatingTone { OnPhoto, OnDark, AccentIcon }

/** Round glass control for anything that sits on a photo or map: back, edit, more, recenter. */
@Composable
fun FloatingButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: FloatingTone = FloatingTone.OnPhoto,
    size: Dp = DsSize.floatingButton,
    iconSize: Dp = 22.dp,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val bg = if (tone == FloatingTone.OnDark) DsColor.cardSolid else DsColor.glassOnPhoto
    val border = if (tone == FloatingTone.OnDark) DsColor.border else DsColor.borderOnPhoto
    val tint = if (tone == FloatingTone.AccentIcon) DsColor.accent else DsColor.textPrimary
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .then(if (onClick != null) Modifier.dsClickable(pressedScale = 0.95f, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
        badge?.invoke(this)
    }
}

/** Square glass button: share / QR in the roster header, regroup / end on the ride bar. */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    danger: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = DsShape.sm,
) {
    val tint = if (danger) DsColor.danger else DsColor.textPrimary
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (danger) DsColor.dangerTint else DsColor.card)
            .border(1.dp, if (danger) DsColor.dangerBorder else DsColor.border, shape)
            .dsClickable(enabled = enabled && !busy, pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/** Dims a control without removing it from layout or hit-testing. */
fun Modifier.dsAlpha(alpha: Float): Modifier = this.alpha(alpha)
