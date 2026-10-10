package com.danglingmind.convotrack.ui.theme

import android.app.Activity
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsType

// ConvoTrack is dark-only. Material 3 is mapped onto the Dark Glass tokens so the stock
// components still in use (ModalBottomSheet, AlertDialog, ripples, text selection) match.
private val ConvoTrackColorScheme = darkColorScheme(
    primary = DsColor.accent,
    onPrimary = DsColor.onAccent,
    primaryContainer = DsColor.accentTonal,
    onPrimaryContainer = DsColor.accent,
    secondary = DsColor.textSecondary,
    secondaryContainer = DsColor.neutralBadge,
    onSecondaryContainer = DsColor.textSecondary,
    tertiary = DsColor.success,
    tertiaryContainer = DsColor.successTint,
    onTertiaryContainer = DsColor.success,
    background = DsColor.bgBase,
    onBackground = DsColor.textPrimary,
    surface = DsColor.bgBase,
    onSurface = DsColor.textPrimary,
    surfaceVariant = DsColor.avatar,
    onSurfaceVariant = DsColor.textSecondary,
    surfaceContainerLowest = DsColor.bgDeep,
    surfaceContainerLow = DsColor.sheet,
    surfaceContainer = DsColor.dialog,
    surfaceContainerHigh = DsColor.dialog,
    surfaceContainerHighest = DsColor.raised,
    outline = DsColor.borderStrong,
    outlineVariant = DsColor.border,
    error = DsColor.danger,
    errorContainer = DsColor.dangerContainer,
    scrim = DsColor.scrim,
)

private val ConvoTrackTypography = Typography(
    displayLarge = DsType.heroTitle,
    headlineLarge = DsType.titleLarge,
    headlineMedium = DsType.titleLarge,
    headlineSmall = DsType.cardTitle,
    titleLarge = DsType.titleLarge,
    titleMedium = DsType.rowTitle,
    bodyLarge = DsType.body,
    bodyMedium = DsType.body,
    bodySmall = DsType.bodySmall,
    labelLarge = DsType.buttonPill,
    labelMedium = DsType.statLabel,
    labelSmall = DsType.badgeSmall,
)

private val SelectionColors = TextSelectionColors(
    handleColor = DsColor.accent,
    backgroundColor = DsColor.accentGlow,
)

@Composable
fun ConvoTrackTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = ConvoTrackColorScheme,
        typography = ConvoTrackTypography,
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides SelectionColors, content = content)
    }
}
