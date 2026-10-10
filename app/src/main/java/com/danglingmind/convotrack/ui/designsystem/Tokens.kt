package com.danglingmind.convotrack.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R

// Dark Glass design system 2.3 — the Android implementation of design/convotrack_design_system.json.
// Screens take every colour, text style, spacing and shape from here; no raw hex, sp or corner
// radius in a screen. Token names follow the spec, so a value can be looked up in either place.

/** Colour tokens (`tokens.color` in the spec). */
object DsColor {
    // Background
    val bgBase = Color(0xFF060C12)
    val bgDeep = Color(0xFF02070B)
    val bgElevated = Color(0xFF0B131B)
    val bgTop = Color(0xFF0E1822)

    // Surface. Translucent values sit over photos and gradients; `*Solid` are the opaque fallbacks
    // for places nothing shows through (and for API < 31, where there is no backdrop blur).
    val card = Color(0xC7141E29)            // rgba(20,30,41,.78)
    val cardSolid = Color(0xFF111A24)
    val cardQuiet = Color(0xCC0E161F)       // rows, lists
    val field = Color(0xEB1B2631)
    val fieldFocused = Color(0xFA1F2C38)
    val glassOnPhoto = Color(0x8C161A1E)    // round buttons and pills that sit on a photo
    val glassPill = Color(0x9E1E2834)
    val nav = Color(0xEB0C1115)
    val neutralBadge = Color(0xFF2A2F37)
    val iconDisc = Color(0xFF14202A)
    val raised = Color(0xFF16212D)
    val dialog = Color(0xFF111B26)
    val sheet = Color(0xFF0B131B)
    val avatar = Color(0xFF1E2A38)
    val pressed = Color(0x0FFFFFFF)
    val scrim = Color(0x8F02070C)
    val dangerContainer = Color(0xFF3A1518)
    val leaderRow = Color(0x095EF7A6)
    val chipOnPhoto = Color(0x8C0A1016)

    // Text
    val textPrimary = Color(0xFFF5F7FA)
    val textLabel = Color(0xFFD5D9DF)
    val textSecondary = Color(0xFFB4B9C1)
    val textMuted = Color(0xFF868C95)
    val textSubtle = Color(0xFF5C636D)
    val textDisabled = Color(0xFF5D6670)
    val textOnPhoto = Color.White
    val textOnPhotoMuted = Color(0xC7FFFFFF)

    // Accent: ice cyan #3DE8FF, everything else derived from it (see the spec's `_chosen`).
    val accent = Color(0xFF3DE8FF)
    val accentBright = Color(0xFF5AEBFF)
    val accentSoft = Color(0xFF34C5D9)
    val accentDeep = Color(0xFF22808C)
    val accentTint = Color(0x1F3DE8FF)       // 12%
    val accentTonal = Color(0x2B3DE8FF)      // 17%
    val accentBorder = Color(0x733DE8FF)     // 45%
    val accentGlow = Color(0x473DE8FF)       // 28%
    val focus = Color(0xFF37D1E6)
    val onAccent = Color(0xFF040E0F)

    // Status
    val success = Color(0xFF5EF7A6)
    val successTint = Color(0x1F5EF7A6)
    val successBorder = Color(0x665EF7A6)
    val onSuccess = Color(0xFF0B2416)
    val warning = Color(0xFFFFCF70)
    val warningTint = Color(0x1FFFCF70)
    val danger = Color(0xFFFBB0A9)
    val dangerTint = Color(0x21FBB0A9)
    val dangerBorder = Color(0x66FBB0A9)
    val dangerStrong = Color(0xFFE5484D)
    val info = Color(0xFF9DBEFF)

    // Border
    val borderHairline = Color(0x1AA0B4C8)
    val borderSubtle = Color(0x1FA0B4C8)
    val border = Color(0x2EA0B4C8)
    val borderStrong = Color(0x47A0B4C8)
    val divider = Color(0x1AA0B4C8)
    val borderLeaderRow = Color(0x385EF7A6)
    val borderOnPhoto = Color(0x1AFFFFFF)

    // Regroup categories
    val catFuel = Color(0xFFFFB547)
    val catFood = Color(0xFFFF8A4C)
    val catScenic = Color(0xFF5AB8FF)
    val catOther = Color(0xFFB58CFF)

    // Map
    val mapRouteCore = accent
    val mapRouteGlow = Color(0x4D3DE8FF)     // 30%
    val mapRoutePlanned = Color(0x473DE8FF)  // 28%
    val mapRouteAlternative = Color(0x66B4B9C1)
    val mapStart = success
    val mapStop = accentDeep
    val mapEnd = accent
    val mapPlate = Color(0xDB0A1014)

    /** Solid colours for initial-only avatars, picked by rider id so a rider keeps their colour. */
    val avatarPalette = listOf(
        Color(0xFFE53872), Color(0xFF7C5CFF), Color(0xFF2F9BFF),
        Color(0xFFF2994A), Color(0xFF27AE8F), Color(0xFFC2410C),
    )

    fun avatarColorFor(key: String): Color = avatarPalette[Math.floorMod(key.hashCode(), avatarPalette.size)]
}

/** Gradients (`tokens.gradient`). */
object DsGradient {
    val appBackground = Brush.verticalGradient(
        0f to DsColor.bgTop, 0.45f to DsColor.bgBase, 1f to DsColor.bgDeep,
    )

    /** Every hero photo: lets the photo read at the top, then lands on background.base. */
    val photoFade = Brush.verticalGradient(
        0f to Color(0x1A060C12), 0.35f to Color(0x40060C12), 0.72f to Color(0xCC060C12), 1f to DsColor.bgBase,
    )

    /** Home hero: keeps the left side dark under the headline. */
    val photoSideFade = Brush.horizontalGradient(
        0f to Color(0xB8060C12), 0.55f to Color(0x47060C12), 1f to Color(0x00060C12),
    )

    val cardPhotoScrim = Brush.verticalGradient(
        0f to Color(0x2604090E), 0.45f to Color(0x4004090E), 1f to Color(0xE004090E),
    )

    /** Fades scrolling content out under a sticky bottom action. */
    val bottomActionFade = Brush.verticalGradient(0f to Color(0x00060C12), 0.4f to DsColor.bgDeep)
}

/** Font families. Bundled in res/font (decision: fonts are bundled). */
object DsFont {
    val ui = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )
    val mono = FontFamily(
        Font(R.font.roboto_mono_medium, FontWeight.Medium),
        Font(R.font.roboto_mono_bold, FontWeight.Bold),
    )
    val display = FontFamily(Font(R.font.source_serif_bold, FontWeight.Bold))
}

private val tightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(
    family: FontFamily,
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = tightLines,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/**
 * Text styles (`tokens.typography.styles`). Styles the spec marks uppercase are uppercased by the
 * caller (`text.uppercase()`), since TextStyle has no transform.
 */
object DsType {
    val heroTitle = style(DsFont.display, 40, FontWeight.Bold, 40, -1.0f)
    val heroTitleCompact = style(DsFont.display, 30, FontWeight.Bold, 32, -0.6f)
    val heroSubtitle = style(DsFont.ui, 15, FontWeight.Normal, 21, 0.2f)
    val eyebrow = style(DsFont.ui, 11, FontWeight.SemiBold, 14, 4.4f)
    val brandWordmark = style(DsFont.ui, 40, FontWeight.ExtraBold, 44, 4f)
    val headerTitle = style(DsFont.ui, 16, FontWeight.SemiBold, 20, 3.6f)
    val headerSubtitle = style(DsFont.ui, 13, FontWeight.Normal, 17, 1.6f)
    val sectionLabel = style(DsFont.ui, 14, FontWeight.SemiBold, 18, 4.6f)
    val sectionAction = style(DsFont.ui, 12, FontWeight.SemiBold, 16, 2.6f)
    val fieldLabel = style(DsFont.ui, 12, FontWeight.SemiBold, 16, 3.0f)
    val titleLarge = style(DsFont.ui, 24, FontWeight.Bold, 30, -0.2f)
    val cardTitle = style(DsFont.ui, 22, FontWeight.Bold, 26, 0.2f)
    val rowTitle = style(DsFont.ui, 16, FontWeight.SemiBold, 21, 0.4f)
    val body = style(DsFont.ui, 15, FontWeight.Normal, 21, 0.2f)
    val bodyStrong = style(DsFont.ui, 15, FontWeight.SemiBold, 21, 0.2f)
    val bodySmall = style(DsFont.ui, 13, FontWeight.Normal, 18, 0.3f)
    val input = style(DsFont.ui, 18, FontWeight.Medium, 24, 0.2f)
    val locationTitle = style(DsFont.ui, 16, FontWeight.Medium, 20)
    val helper = style(DsFont.ui, 12, FontWeight.Normal, 17, 0.2f)
    val button = style(DsFont.ui, 18, FontWeight.ExtraBold, 22, 1.4f)
    val buttonWide = style(DsFont.ui, 14, FontWeight.Bold, 18, 3.0f)
    val buttonPill = style(DsFont.ui, 13, FontWeight.Bold, 16, 1.2f)
    val link = style(DsFont.ui, 13, FontWeight.SemiBold, 18)
    val statLabel = style(DsFont.ui, 11, FontWeight.Medium, 14, 1.4f)
    val statValue = style(DsFont.ui, 18, FontWeight.SemiBold, 22, tabular = true)
    val metricValue = style(DsFont.ui, 24, FontWeight.SemiBold, 28, tabular = true)
    val metricDisplay = style(DsFont.ui, 44, FontWeight.Bold, 48, -0.8f, tabular = true)
    val detailLabel = style(DsFont.ui, 14, FontWeight.Normal, 18, 0.5f)
    val detailValue = style(DsFont.ui, 15, FontWeight.Medium, 20, 0.2f)
    val dataMono = style(DsFont.mono, 13, FontWeight.Medium, 17, 0.6f, tabular = true)
    val codeValue = style(DsFont.mono, 18, FontWeight.Bold, 22, 1.0f)
    val codeDisplay = style(DsFont.mono, 30, FontWeight.Bold, 36, 6f)
    val otpDisplay = style(DsFont.mono, 26, FontWeight.Bold, 32, 10f)
    val badge = style(DsFont.mono, 12, FontWeight.Bold, 14, 2.0f)
    val badgeSmall = style(DsFont.mono, 10, FontWeight.Bold, 12, 1.4f)
    val metaTag = style(DsFont.ui, 12, FontWeight.SemiBold, 16, 2.0f)
    val navLabel = style(DsFont.ui, 12, FontWeight.Medium, 14, 0.2f)
    val micro = style(DsFont.ui, 10, FontWeight.Medium, 12, 0.3f)

    // Ride HUD — read at road speed, so larger than the planning screens.
    val hudTurnDistance = style(DsFont.mono, 26, FontWeight.Bold, 30, tabular = true)
    val hudTurnInstruction = style(DsFont.ui, 17, FontWeight.SemiBold, 22)
    val hudStatValue = style(DsFont.mono, 24, FontWeight.Bold, 28, tabular = true)
    val hudStatLabel = style(DsFont.ui, 10, FontWeight.Bold, 12, 1.0f)
    val hudEta = style(DsFont.mono, 22, FontWeight.Bold, 26, tabular = true)
}

/** 4pt spacing scale (`tokens.spacing`). */
object DsSpace {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x7 = 28.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp

    /** Page gutter. */
    val gutter = 20.dp
    val sectionGap = 24.dp
}

/** Corner radii (`tokens.radius`). */
object DsRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val nav = 28.dp
}

object DsShape {
    val xs = RoundedCornerShape(DsRadius.xs)
    val sm = RoundedCornerShape(DsRadius.sm)
    val md = RoundedCornerShape(DsRadius.md)
    val lg = RoundedCornerShape(DsRadius.lg)
    val xl = RoundedCornerShape(DsRadius.xl)
    val nav = RoundedCornerShape(DsRadius.nav)
    val pill = RoundedCornerShape(50)
    val sheetTop = RoundedCornerShape(topStart = DsRadius.xl, topEnd = DsRadius.xl)
}

/** Fixed sizes that recur across components. */
object DsSize {
    val floatingButton = 44.dp
    val touchTarget = 44.dp
    val primaryButton = 56.dp
    val secondaryButton = 52.dp
    val navBar = 60.dp
    val navActive = 50.dp
}

/** Letter spacing helper for one-off tracked text that already uses a token style. */
fun TextStyle.tracked(sp: Float): TextStyle = copy(letterSpacing = sp.sp)

fun TextStyle.sized(size: TextUnit): TextStyle = copy(fontSize = size)
