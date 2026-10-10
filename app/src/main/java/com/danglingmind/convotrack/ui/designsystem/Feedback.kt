package com.danglingmind.convotrack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// MARK: - Empty / error / loading

/** Dashed glass card with an accent disc, a title, a line of copy and one action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    error: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(DsShape.lg)
            .background(DsColor.card)
            .dashedBorder(if (error) DsColor.dangerBorder else DsColor.border, DsRadius.lg, dashed = !error)
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(
            icon,
            size = 56.dp,
            iconSize = 28.dp,
            background = if (error) DsColor.dangerTint else DsColor.accentTint,
            tint = if (error) DsColor.danger else DsColor.accent,
        )
        Text(title, style = DsType.cardTitle, color = DsColor.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = DsType.bodySmall, color = DsColor.textMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            PillButton(
                actionLabel,
                onAction,
                tone = if (error) PillTone.Neutral else PillTone.Primary,
                icon = if (error) Icons.Filled.Refresh else null,
                height = 44.dp,
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(Icons.Filled.ErrorOutline, "Something went wrong", message, modifier, "Retry", onRetry, error = true)
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DsColor.accent, trackColor = DsColor.borderSubtle, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp, dashed: Boolean): Modifier =
    if (!dashed) this.border(1.dp, color, RoundedCornerShape(radius))
    else this.drawBehind {
        val r = radius.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
        )
    }

// MARK: - Dialog

/**
 * Confirmation dialog on the dialog surface. Destructive confirms are danger-coloured; dismiss is
 * secondary. While [busy], both actions disable and the confirm label is expected to read "…ING…".
 */
@Composable
fun ConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    extraAction: Pair<String, () -> Unit>? = null,
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(DsShape.xl)
                .background(DsColor.dialog)
                .border(1.dp, DsColor.borderSubtle, DsShape.xl)
                .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = DsType.titleLarge, color = DsColor.textPrimary, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
            if (error != null) InlineError(error, Modifier.padding(end = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                if (dismissLabel != null) TextAction(dismissLabel, onDismiss, tone = TextActionTone.Dismiss, enabled = !busy)
                if (extraAction != null) TextAction(extraAction.first, extraAction.second, tone = TextActionTone.Confirm, enabled = !busy)
                TextAction(
                    confirmLabel,
                    onConfirm,
                    tone = if (destructive) TextActionTone.Destructive else TextActionTone.Confirm,
                    enabled = !busy,
                )
            }
        }
    }
}

/** Dialog body paragraph. */
@Composable
fun DialogText(text: String) {
    Text(text, style = DsType.body, color = DsColor.textSecondary)
}

// MARK: - Bottom sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DsColor.sheet,
        scrimColor = DsColor.scrim,
        shape = DsShape.sheetTop,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DsShape.pill)
                    .background(DsColor.borderStrong),
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding(), content = content)
    }
}

/** Sheet title row: accent icon + title. */
@Composable
fun SheetTitle(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = DsSpace.gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, tint = DsColor.accent, modifier = Modifier.size(28.dp))
        Text(title, style = DsType.titleLarge, color = DsColor.textPrimary)
    }
}
