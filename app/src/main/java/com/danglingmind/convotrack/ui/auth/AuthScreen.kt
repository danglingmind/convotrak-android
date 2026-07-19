package com.danglingmind.convotrack.ui.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Provider { GOOGLE, APPLE }

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    var signingIn by remember { mutableStateOf<Provider?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun signIn(provider: Provider) {
        if (signingIn != null) return
        signingIn = provider
        // No real Clerk yet — simulate the round-trip, then advance.
        scope.launch {
            delay(900)
            onSignedIn()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Hero background — gradient stand-in for the remote grayscale photo used on iOS.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to CT.surfaceContainerHigh,
                        0.6f to CT.surfaceDim.copy(alpha = 0.9f),
                        1f to CT.surfaceDim,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader()
            Spacer(Modifier.weight(1f))
            AuthButtons(signingIn) { signIn(it) }
            Spacer(Modifier.weight(1f))
            FooterSection()
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.logo_circle),
            contentDescription = null,
            modifier = Modifier.size(100.dp).padding(bottom = 16.dp),
        )
        Text(
            text = "CONVOTRACK",
            style = CTType.displayMetrics.copy(fontSize = 42.sp),
            color = CT.primaryFixed,
            letterSpacing = 9.6.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .padding(top = 1.dp)
                .background(CT.outlineVariant.copy(alpha = 0.3f))
                .height(1.dp),
        )
        Text(
            text = "RIDE TOGETHER IN REAL TIME",
            style = CTType.labelCaps,
            color = CT.onSurfaceVariant,
            letterSpacing = 3.6.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AuthButtons(signingIn: Provider?, onSignIn: (Provider) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignInButton(
            label = "Continue with Google",
            foreground = CT.onPrimaryFixed,
            background = CT.primaryFixed,
            border = CT.primaryFixed,
            borderWidth = 2.dp,
            isLoading = signingIn == Provider.GOOGLE,
            enabled = signingIn == null,
            leading = { GoogleGIcon() },
            onClick = { onSignIn(Provider.GOOGLE) },
        )
        SignInButton(
            label = "Continue with Apple",
            foreground = Color.White,
            background = CT.surfaceContainerHighest,
            border = CT.outline.copy(alpha = 0.3f),
            borderWidth = 1.dp,
            isLoading = signingIn == Provider.APPLE,
            enabled = signingIn == null,
            // No Apple glyph in Material icons; leading omitted (label carries the meaning).
            leading = {},
            onClick = { onSignIn(Provider.APPLE) },
        )
    }
}

@Composable
private fun SignInButton(
    label: String,
    foreground: Color,
    background: Color,
    border: Color,
    borderWidth: androidx.compose.ui.unit.Dp,
    isLoading: Boolean,
    enabled: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(350.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(borderWidth, border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = label.uppercase(),
                style = CTType.bodyMd.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = foreground,
                letterSpacing = (-0.5).sp,
            )
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
            }
        }
    }
}

@Composable
private fun FooterSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("TERMS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 4.sp)
            Box(Modifier.size(5.dp).clip(CircleShape).background(CT.primaryFixed.copy(alpha = 0.5f)))
            Text("PRIVACY", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 4.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(dotColor = CT.tertiaryFixedDim, label = "SERVER ONLINE")
            StatusPill(icon = { Icon(Icons.Filled.Lock, null, tint = CT.onSurfaceVariant, modifier = Modifier.size(11.dp)) }, label = "SSL ENCRYPTED")
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    dotColor: Color? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "pill")
    val pulse by transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor.copy(alpha = pulse)))
        }
        icon?.invoke()
        Text(
            label,
            style = CTType.labelCaps.copy(fontSize = 10.sp),
            color = CT.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
    }
}

// 4-color Google "G" — ported from iOS AuthView.swift GoogleGIcon Canvas paths (24x24).
@Composable
private fun GoogleGIcon() {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)

        val blue = Path().apply {
            moveTo(p(22.56f, 12.25f).x, p(22.56f, 12.25f).y)
            cubicTo(p(22.56f, 11.47f).x, p(22.56f, 11.47f).y, p(22.49f, 10.72f).x, p(22.49f, 10.72f).y, p(22.36f, 10f).x, p(22.36f, 10f).y)
            lineTo(p(12f, 10f).x, p(12f, 10f).y)
            lineTo(p(12f, 14.26f).x, p(12f, 14.26f).y)
            lineTo(p(17.92f, 14.26f).x, p(17.92f, 14.26f).y)
            cubicTo(p(17.66f, 15.63f).x, p(17.66f, 15.63f).y, p(16.88f, 16.79f).x, p(16.88f, 16.79f).y, p(15.71f, 17.57f).x, p(15.71f, 17.57f).y)
            lineTo(p(15.71f, 20.34f).x, p(15.71f, 20.34f).y)
            lineTo(p(19.28f, 20.34f).x, p(19.28f, 20.34f).y)
            cubicTo(p(21.36f, 18.42f).x, p(21.36f, 18.42f).y, p(22.56f, 15.60f).x, p(22.56f, 15.60f).y, p(22.56f, 12.25f).x, p(22.56f, 12.25f).y)
            close()
        }
        drawPath(blue, Color(0xFF4285F4))

        val green = Path().apply {
            moveTo(p(12f, 23f).x, p(12f, 23f).y)
            cubicTo(p(14.97f, 23f).x, p(14.97f, 23f).y, p(17.46f, 22.02f).x, p(17.46f, 22.02f).y, p(19.28f, 20.34f).x, p(19.28f, 20.34f).y)
            lineTo(p(15.71f, 17.57f).x, p(15.71f, 17.57f).y)
            cubicTo(p(14.73f, 18.23f).x, p(14.73f, 18.23f).y, p(13.48f, 18.63f).x, p(13.48f, 18.63f).y, p(12f, 18.63f).x, p(12f, 18.63f).y)
            cubicTo(p(9.14f, 18.63f).x, p(9.14f, 18.63f).y, p(6.71f, 16.70f).x, p(6.71f, 16.70f).y, p(5.84f, 14.10f).x, p(5.84f, 14.10f).y)
            lineTo(p(2.18f, 14.10f).x, p(2.18f, 14.10f).y)
            lineTo(p(2.18f, 16.94f).x, p(2.18f, 16.94f).y)
            cubicTo(p(3.99f, 20.53f).x, p(3.99f, 20.53f).y, p(7.70f, 23f).x, p(7.70f, 23f).y, p(12f, 23f).x, p(12f, 23f).y)
            close()
        }
        drawPath(green, Color(0xFF34A853))

        val yellow = Path().apply {
            moveTo(p(5.84f, 14.09f).x, p(5.84f, 14.09f).y)
            cubicTo(p(5.62f, 13.43f).x, p(5.62f, 13.43f).y, p(5.49f, 12.73f).x, p(5.49f, 12.73f).y, p(5.49f, 12f).x, p(5.49f, 12f).y)
            cubicTo(p(5.49f, 11.27f).x, p(5.49f, 11.27f).y, p(5.62f, 10.57f).x, p(5.62f, 10.57f).y, p(5.84f, 9.91f).x, p(5.84f, 9.91f).y)
            lineTo(p(5.84f, 7.07f).x, p(5.84f, 7.07f).y)
            lineTo(p(2.18f, 7.07f).x, p(2.18f, 7.07f).y)
            cubicTo(p(1.43f, 8.55f).x, p(1.43f, 8.55f).y, p(1f, 10.22f).x, p(1f, 10.22f).y, p(1f, 12f).x, p(1f, 12f).y)
            cubicTo(p(1f, 13.78f).x, p(1f, 13.78f).y, p(1.43f, 15.45f).x, p(1.43f, 15.45f).y, p(2.18f, 16.93f).x, p(2.18f, 16.93f).y)
            lineTo(p(5.84f, 14.09f).x, p(5.84f, 14.09f).y)
            close()
        }
        drawPath(yellow, Color(0xFFFBBC05))

        val red = Path().apply {
            moveTo(p(12f, 5.38f).x, p(12f, 5.38f).y)
            cubicTo(p(13.62f, 5.38f).x, p(13.62f, 5.38f).y, p(15.06f, 5.94f).x, p(15.06f, 5.94f).y, p(16.21f, 7.02f).x, p(16.21f, 7.02f).y)
            lineTo(p(19.36f, 3.87f).x, p(19.36f, 3.87f).y)
            cubicTo(p(17.45f, 2.09f).x, p(17.45f, 2.09f).y, p(14.97f, 1f).x, p(14.97f, 1f).y, p(12f, 1f).x, p(12f, 1f).y)
            cubicTo(p(7.70f, 1f).x, p(7.70f, 1f).y, p(3.99f, 3.47f).x, p(3.99f, 3.47f).y, p(2.18f, 7.07f).x, p(2.18f, 7.07f).y)
            lineTo(p(5.84f, 9.91f).x, p(5.84f, 9.91f).y)
            cubicTo(p(6.71f, 7.31f).x, p(6.71f, 7.31f).y, p(9.14f, 5.38f).x, p(9.14f, 5.38f).y, p(12f, 5.38f).x, p(12f, 5.38f).y)
            close()
        }
        drawPath(red, Color(0xFFEA4335))
    }
}
