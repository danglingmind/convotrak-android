package com.danglingmind.convotrack.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import kotlinx.coroutines.delay

private val stages = listOf(
    "INITIALIZING GPS...", "SYNCING SQUAD...", "CACHING MAPS...", "PREPARING HUD...",
)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var stageIndex by remember { mutableIntStateOf(0) }

    // Cycle loading text every 1.8s, then advance to Auth after 3.5s (matches iOS).
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 3500) {
            delay(1800)
            stageIndex = (stageIndex + 1) % stages.size
        }
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val trim by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "trim",
    )
    val glow by transition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow",
    )
    val dotAlpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CT.surfaceContainerLowest),
    ) {
        val h = maxHeight

        // Route line — vertically centered.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RouteLine(trim = trim, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(120.dp).padding(horizontal = 20.dp))
        }

        // Logo — top 15%.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(h * 0.15f))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((240 * glow).dp)
                        .blur(40.dp)
                        .clip(CircleShape)
                        .background(CT.primaryFixed.copy(alpha = 0.2f)),
                )
                Image(
                    painter = painterResource(R.drawable.logo_circle),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp),
                )
            }
        }

        // Title + loading text — lower third.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = h * 0.20f),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text(
                text = "CONVOTRACK",
                style = CTType.displayMetrics,
                color = CT.primaryFixed,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = "MULTIPLAYER NAVIGATION",
                style = CTType.labelCaps,
                color = CT.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(CT.primaryFixed.copy(alpha = dotAlpha)))
                androidx.compose.material3.Text(
                    text = stages[stageIndex],
                    style = CTType.labelCaps,
                    color = CT.primaryFixed.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                )
            }
        }

        // Footer.
        androidx.compose.material3.Text(
            text = "EST. 2024 — CONVOTRACK PROTOCOL V4.2",
            style = CTType.labelCaps.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = CT.onSurfaceVariant.copy(alpha = 0.3f),
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

// Exact SVG path from iOS Stitch: M10,80 Q50,10 120,50 T250,20 T390,70  (viewBox 0 -20 400 120)
@Composable
private fun RouteLine(trim: Float, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 400f
        val sy = size.height / 120f
        fun px(x: Float, y: Float) = Offset(x * sx, (y + 20f) * sy)

        val path = Path().apply {
            moveTo(px(10f, 80f).x, px(10f, 80f).y)
            quadraticTo(px(50f, 10f).x, px(50f, 10f).y, px(120f, 50f).x, px(120f, 50f).y)
            // T250,20 — reflected control (190,90)
            quadraticTo(px(190f, 90f).x, px(190f, 90f).y, px(250f, 20f).x, px(250f, 20f).y)
            // T390,70 — reflected control (310,-50)
            quadraticTo(px(310f, -50f).x, px(310f, -50f).y, px(390f, 70f).x, px(390f, 70f).y)
        }

        // Background track.
        drawPath(path, color = CT.surfaceContainerHighest, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

        // Animated lime segment via PathMeasure trim.
        val measure = PathMeasure()
        measure.setPath(path, false)
        val dest = Path()
        measure.getSegment(0f, measure.length * trim, dest, true)
        drawPath(dest, color = CT.primaryFixed, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}
