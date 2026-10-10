package com.danglingmind.convotrack.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.designsystem.DsFont

// MARK: - Legacy typography
//
// The original sizes, now set in the bundled Dark Glass families. Screens are moving to
// `DsType`; remove once nothing references `CTType`.

object CTType {
    val displayMetrics = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = DsFont.ui)
    val headlineLg = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = DsFont.ui)
    val headlineMd = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = DsFont.ui)
    val bodyLg = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = DsFont.ui)
    val bodyMd = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, fontFamily = DsFont.ui)
    val labelCaps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = DsFont.mono)
    val dataMono = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = DsFont.mono)
    val toggleLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = DsFont.ui)
    val captionMd = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = DsFont.ui)
    val captionSm = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = DsFont.ui)
}
