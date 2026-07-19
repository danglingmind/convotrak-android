package com.danglingmind.convotrack.ui.theme

import androidx.compose.ui.graphics.Color

// MARK: - Color Palette (ported 1:1 from iOS ConvoTrackTheme.swift)
// Dark-only, Material You-inspired. Never use raw hex inline in composables — reference these.

object CT {
    // Primary
    val primaryFixed = Color(0xFFCAF300)          // Lime green accent
    val primaryFixedDim = Color(0xFFB0D500)
    val onPrimaryFixed = Color(0xFF171E00)         // Dark text on lime
    val onPrimaryFixedVariant = Color(0xFF3E4C00)
    val primaryContainer = Color(0xFFCAF300)
    val onPrimaryContainer = Color(0xFF596C00)

    // Surface
    val surfaceDim = Color(0xFF131313)
    val surface = Color(0xFF131313)
    val surfaceContainerLowest = Color(0xFF0E0E0E)
    val surfaceContainerLow = Color(0xFF1B1B1B)
    val surfaceContainer = Color(0xFF20201F)
    val surfaceContainerHigh = Color(0xFF2A2A2A)
    val surfaceContainerHighest = Color(0xFF353535)
    val surfaceBright = Color(0xFF393939)
    val surfaceVariant = Color(0xFF353535)

    // On Surface
    val onSurface = Color(0xFFE5E2E1)
    val onSurfaceVariant = Color(0xFFC5C9AC)
    val onBackground = Color(0xFFE5E2E1)

    // Secondary
    val secondary = Color(0xFFC8C6C5)
    val secondaryContainer = Color(0xFF4A4949)
    val onSecondaryContainer = Color(0xFFBAB8B7)
    val secondaryFixed = Color(0xFFE5E2E1)

    // Tertiary
    val tertiaryFixed = Color(0xFF62FF96)
    val tertiaryFixedDim = Color(0xFF00E475)
    val tertiaryContainer = Color(0xFF62FF96)
    val onTertiaryContainer = Color(0xFF007438)

    // Outline
    val outline = Color(0xFF8F9378)
    val outlineVariant = Color(0xFF444932)

    // Error
    val errorColor = Color(0xFFFFB4AB)
    val errorContainer = Color(0xFF93000A)
}
