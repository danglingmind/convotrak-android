package com.danglingmind.convotrack.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ConvoTrack is dark-only (matches the iOS `.preferredColorScheme(.dark)`).
private val ConvoTrackColorScheme = darkColorScheme(
    primary = CT.primaryFixed,
    onPrimary = CT.onPrimaryFixed,
    primaryContainer = CT.primaryContainer,
    onPrimaryContainer = CT.onPrimaryContainer,
    secondary = CT.secondary,
    secondaryContainer = CT.secondaryContainer,
    onSecondaryContainer = CT.onSecondaryContainer,
    tertiary = CT.tertiaryFixed,
    tertiaryContainer = CT.tertiaryContainer,
    onTertiaryContainer = CT.onTertiaryContainer,
    background = CT.surfaceDim,
    onBackground = CT.onBackground,
    surface = CT.surface,
    onSurface = CT.onSurface,
    surfaceVariant = CT.surfaceVariant,
    onSurfaceVariant = CT.onSurfaceVariant,
    surfaceContainerLowest = CT.surfaceContainerLowest,
    surfaceContainerLow = CT.surfaceContainerLow,
    surfaceContainer = CT.surfaceContainer,
    surfaceContainerHigh = CT.surfaceContainerHigh,
    surfaceContainerHighest = CT.surfaceContainerHighest,
    outline = CT.outline,
    outlineVariant = CT.outlineVariant,
    error = CT.errorColor,
    errorContainer = CT.errorContainer,
)

private val ConvoTrackTypography = Typography(
    displayLarge = CTType.displayMetrics,
    headlineLarge = CTType.headlineLg,
    headlineMedium = CTType.headlineMd,
    bodyLarge = CTType.bodyLg,
    bodyMedium = CTType.bodyMd,
    labelSmall = CTType.labelCaps,
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
        content = content,
    )
}
