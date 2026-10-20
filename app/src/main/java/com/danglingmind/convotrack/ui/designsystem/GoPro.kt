package com.danglingmind.convotrack.ui.designsystem

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Go Pro gold (iOS `DS.Pro`). Reserved for the Pro invitation and everything that sells or
 * completes Pro, so it can't be mistaken for a lime action button.
 */
object DsPro {
    val goldLight = Color(0xFFFFE38A)
    val gold = Color(0xFFFFC247)
    val goldDeep = Color(0xFFF29A1F)
    val glow = Color(0xFFFFB22E)
    val brush = Brush.linearGradient(listOf(goldLight, gold, goldDeep))
    val rim = Brush.verticalGradient(listOf(goldLight.copy(alpha = 0.85f), goldDeep.copy(alpha = 0.45f)))

    /** The paywall's warm wash from the top. */
    val aura = Brush.verticalGradient(listOf(glow.copy(alpha = 0.16f), glow.copy(alpha = 0f)))
}

/** [style] painted with the gold gradient. */
fun TextStyle.gold(): TextStyle = copy(brush = DsPro.brush)

/**
 * iOS `DSGoProShine`: gold rim, a glint sweeping under the content (~every 3.2 s) and a gold glow
 * breathing on a 2.4 s cycle.
 * - [glass]: chrome (chips, buttons) gets a glass-like translucent fill (Android has no native
 *   Liquid Glass); content (cards) keeps the card surface.
 * - [animated]: false for still surfaces (the paywall). The system "Remove animations" setting
 *   also stills it.
 */
@Composable
fun GoProSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    animated: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val moving = animated && !rememberReduceMotion()
    val sweep: Float
    val breathe: Float
    if (moving) {
        val transition = rememberInfiniteTransition(label = "goPro")
        sweep = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep",
        ).value
        breathe = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "breathe",
        ).value
    } else {
        sweep = 1f
        breathe = 0.5f
    }
    val glowAlpha = (if (glass) 0.22f else 0.12f) + (if (glass) 0.3f else 0.18f) * breathe
    val fill = if (glass) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))
    } else {
        Brush.verticalGradient(listOf(DsColor.card, DsColor.card))
    }
    // The glint runs over the first 35% of each cycle, then the surface rests.
    val glintProgress = (sweep / 0.35f).takeIf { moving && it <= 1f }
    val glintAlpha = if (glass) 0.5f else 0.14f

    val glowRadius = (if (glass) 6f else 10f) + (if (glass) 6f else 8f) * breathe
    Box(
        modifier
            // A real blurred gold halo (elevation shadows barely show colour on a dark screen).
            // Drawn before the clip so it can spill outside the shape, like the iOS glow.
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val corner = (outline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = DsPro.glow.copy(alpha = glowAlpha).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(glowRadius.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
                }
            }
            .clip(shape)
            .background(DsColor.bgBase.copy(alpha = if (glass) 0.62f else 0f))
            .background(fill)
            .drawBehind {
                val p = glintProgress ?: return@drawBehind
                val bandWidth = maxOf(size.width * 0.38f, 60.dp.toPx())
                val x = -size.width * 0.5f + p * size.width * 1.5f
                // Already clipped to [shape] by the .clip above.
                rotate(20f, pivot = Offset(x + bandWidth / 2, size.height / 2)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = glintAlpha), Color.White.copy(alpha = 0f)),
                            startX = x, endX = x + bandWidth,
                        ),
                        topLeft = Offset(x, -size.height),
                        size = androidx.compose.ui.geometry.Size(bandWidth, size.height * 3),
                    )
                }
            }
            .border(1.2.dp, DsPro.rim, shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Full-width Pro button (iOS `DSGoProButton`): glass-like fill, gold text, gold rim, steady glow.
 * Still on purpose — the paywall and the nudge carry no motion.
 */
@Composable
fun GoProButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val view = LocalView.current
    GoProSurface(
        shape = DsShape.lg,
        animated = false,
        modifier = modifier
            .fillMaxWidth()
            .height(DsSize.primaryButton)
            .dsClickable(enabled = enabled && !loading, pressedScale = 0.985f) {
                view.dsHaptic(DsHaptic.Primary)
                onClick()
            }
            .dsAlpha(if (enabled) 1f else 0.42f),
    ) {
        if (loading) {
            CircularProgressIndicator(color = DsPro.gold, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = DsPro.gold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(text, style = DsType.button.gold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
