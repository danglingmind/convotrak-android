package com.danglingmind.convotrack.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design system's text field: a glass card with an optional leading icon and a trailing
 * slot (round action, spinner or status icon). Focus swaps the border to the accent focus colour.
 */
@Composable
fun TextFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = DsType.input,
    minHeight: Dp = 56.dp,
    enabled: Boolean = true,
    isError: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    highlighted: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            isError -> DsColor.danger
            focused -> DsColor.focus
            highlighted -> DsColor.accentBorder
            else -> DsColor.borderSubtle
        },
        label = "border",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = DsColor.textPrimary, textAlign = textAlign),
        cursorBrush = SolidColor(DsColor.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(DsShape.md)
                    .background(if (focused) DsColor.fieldFocused else DsColor.field)
                    .border(1.dp, border, DsShape.md)
                    .padding(start = if (leadingIcon != null) 14.dp else 16.dp, end = if (trailing != null) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, null, tint = DsColor.textSecondary, modifier = Modifier.size(22.dp))
                Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle.copy(textAlign = textAlign), color = DsColor.textMuted, maxLines = 1)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}


/** Small round action inside a field (the pencil on RIDE TITLE). */
@Composable
fun FieldAction(icon: ImageVector, contentDescription: String?, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(0x1AFFFFFF))
            .then(if (onClick != null) Modifier.dsClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = DsColor.textPrimary, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun FieldSpinner() {
    CircularProgressIndicator(color = DsColor.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
}

/** Max riders: value + suffix, then − and +. The button at a limit is disabled, not hidden. */
@Composable
fun Stepper(
    value: Int,
    suffix: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
    modifier: Modifier = Modifier,
    /** Draws + as at-limit while keeping it tappable — a free rider's + opens the paywall. */
    dimIncrement: Boolean = false,
) {
    val view = LocalView.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).glassSurface(DsShape.md, DsColor.field).padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$value", style = DsType.metricValue, color = DsColor.textPrimary)
        Text(suffix, style = DsType.body, color = DsColor.textMuted, modifier = Modifier.weight(1f))
        StepperButton(Icons.Filled.Remove, "Fewer", canDecrement) { view.dsHaptic(DsHaptic.Selection); onDecrement() }
        StepperButton(Icons.Filled.Add, "More", canIncrement, dimmed = dimIncrement) { view.dsHaptic(DsHaptic.Selection); onIncrement() }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, dimmed: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(DsColor.card)
            .border(1.dp, DsColor.borderStrong, CircleShape)
            .dsClickable(enabled = enabled, onClick = onClick)
            .then(if (enabled && !dimmed) Modifier else Modifier.dsAlpha(0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = DsColor.textPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Pill segmented control (regroup meet point). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Row(
        modifier.fillMaxWidth().height(46.dp).glassSurface(DsShape.pill, DsColor.field, DsColor.border).padding(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            val bg by animateColorAsState(if (active) DsColor.accent else DsColor.accent.copy(alpha = 0f), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(DsShape.pill)
                    .background(bg)
                    .dsClickable { view.dsHaptic(DsHaptic.Selection); onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = DsType.buttonPill, color = if (active) DsColor.onAccent else DsColor.textSecondary)
            }
        }
    }
}
