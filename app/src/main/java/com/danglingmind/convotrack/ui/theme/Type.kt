package com.danglingmind.convotrack.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// MARK: - Typography (ported from iOS ConvoTrackTheme.swift Font extension)
// SwiftUI's default face maps to the platform sans (Roboto on Android); monospaced maps
// to FontFamily.Monospace. Sizes/weights kept identical to iOS.

object CTType {
    val displayMetrics = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold)
    val headlineLg = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
    val headlineMd = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    val bodyLg = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
    val bodyMd = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val labelCaps = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
    val dataMono = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    )
    val toggleLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val captionMd = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val captionSm = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)
}
