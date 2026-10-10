package com.danglingmind.convotrack.ui.theme

import androidx.compose.ui.graphics.Color
import com.danglingmind.convotrack.ui.designsystem.DsColor

// MARK: - Legacy palette
//
// The original Material-style names, now pointing at Dark Glass values (design/convotrack_design_system.json,
// `migration.colors`). Screens are moving to [DsColor] one at a time; until a screen moves, these
// keep it in the same palette. Remove once nothing references `CT`.

object CT {
    val primaryFixed = DsColor.accent
    val primaryFixedDim = DsColor.accentSoft
    val onPrimaryFixed = DsColor.onAccent
    val onPrimaryFixedVariant = DsColor.accentDeep
    val primaryContainer = DsColor.accent
    val onPrimaryContainer = DsColor.onAccent

    val surfaceDim = DsColor.bgBase
    val surface = DsColor.bgBase
    val surfaceContainerLowest = DsColor.bgDeep
    val surfaceContainerLow = Color(0xFF0E161F)
    val surfaceContainer = DsColor.dialog
    val surfaceContainerHigh = DsColor.cardSolid
    val surfaceContainerHighest = DsColor.raised
    val surfaceBright = Color(0xFF243140)
    val surfaceVariant = DsColor.avatar

    val onSurface = DsColor.textPrimary
    val onSurfaceVariant = DsColor.textSecondary
    val onBackground = DsColor.textPrimary

    val secondary = DsColor.textSecondary
    val secondaryContainer = DsColor.neutralBadge
    val onSecondaryContainer = DsColor.textSecondary
    val secondaryFixed = DsColor.textPrimary

    val tertiaryFixed = DsColor.success
    val tertiaryFixedDim = DsColor.success
    val tertiaryContainer = DsColor.success
    val onTertiaryContainer = DsColor.onSuccess

    val outline = Color(0xFF3A4552)
    val outlineVariant = Color(0xFF232D38)

    val errorColor = DsColor.danger
    val errorContainer = DsColor.dangerContainer
}
