package com.danglingmind.convotrack.ui.auth

import com.danglingmind.convotrack.data.analytics.Analytics
import com.danglingmind.convotrack.data.analytics.MetaEvents

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.auth.AuthResult
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSize
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.Eyebrow
import com.danglingmind.convotrack.ui.designsystem.FieldLabel
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.LogoBadge
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.PulseDot
import com.danglingmind.convotrack.ui.designsystem.SecondaryButton
import com.danglingmind.convotrack.ui.designsystem.TextAction
import com.danglingmind.convotrack.ui.designsystem.TextActionTone
import com.danglingmind.convotrack.ui.designsystem.TextFieldCard
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch

private const val FIELD_WIDTH = 350

/**
 * Android ships Google-only sign-in. The email code path below is fully wired and still
 * reachable from [VerificationScreen]; only its entry points are hidden here.
 */
private const val EMAIL_AUTH_ENABLED = false

private enum class Provider { GOOGLE, EMAIL }

/** Clerk email auth supports both sign-in and sign-up off the same form. */
private enum class EmailMode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    onNeedsVerification: (email: String) -> Unit,
) {
    var busy by remember { mutableStateOf<Provider?>(null) }
    var email by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(EmailMode.SIGN_IN) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auth = remember { ServiceLocator.auth }

    val emailValid = email.contains("@") && email.contains(".") && email.length > 4
    val canSubmitEmail = emailValid && busy == null

    fun run(provider: Provider) {
        if (busy != null) return
        error = null
        busy = provider
        val method = provider.name.lowercase()
        Analytics.capture(Analytics.Event.SignInStarted, mapOf("method" to method))
        scope.launch {
            val result = when (provider) {
                Provider.GOOGLE -> auth.signInWithGoogle()
                Provider.EMAIL -> when (mode) {
                    EmailMode.SIGN_IN -> auth.signInWithEmail(email.trim())
                    EmailMode.SIGN_UP -> auth.signUpWithEmail(email.trim())
                }
            }
            busy = null
            when (result) {
                is AuthResult.Success -> {
                    Analytics.capture(Analytics.Event.SignInSucceeded, mapOf("method" to method))
                    MetaEvents.signedIn(method)
                    onSignedIn()
                }
                is AuthResult.NeedsEmailCode -> onNeedsVerification(email.trim())
                // Dismissing the account sheet is a decision, not a failure — nothing to report.
                is AuthResult.Cancelled -> Unit
                is AuthResult.Error -> {
                    Analytics.capture(Analytics.Event.SignInFailed, mapOf("method" to method))
                    error = result.message
                }
            }
        }
    }

    DsScreen {
        // The empty road at sunset, full height, fading into the base at the bottom.
        PhotoBackdrop(height = 640.dp, drawable = R.drawable.hero_road, verticalBias = 0.55f)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = DsSpace.gutter)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoBadge(size = 64.dp, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(36.dp))
            Column(Modifier.fillMaxWidth()) {
                Eyebrow("Ride together in real time")
                Spacer(Modifier.height(10.dp))
                Text("Discover\nnew roads", style = DsType.heroTitle, color = DsColor.textOnPhoto)
                Spacer(Modifier.height(12.dp))
                Text("Plan, join and track rides with your tribe.", style = DsType.heroSubtitle, color = DsColor.textSecondary)
            }
            Spacer(Modifier.weight(1f))

            Column(Modifier.widthIn(max = FIELD_WIDTH.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GoogleSignInButton(loading = busy == Provider.GOOGLE, enabled = busy == null, onClick = { run(Provider.GOOGLE) })

                if (EMAIL_AUTH_ENABLED) {
                    OrDivider()
                    EmailCodeForm(
                        email = email,
                        mode = mode,
                        isLoading = busy == Provider.EMAIL,
                        enabled = busy == null,
                        canSubmit = canSubmitEmail,
                        onEmail = { email = it; error = null },
                        onSubmit = { run(Provider.EMAIL) },
                    )
                    // Sign in <-> Create account — only switches the email form's mode.
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (mode == EmailMode.SIGN_IN) "New rider?" else "Already riding?", style = DsType.bodySmall, color = DsColor.textMuted)
                        TextAction(
                            if (mode == EmailMode.SIGN_IN) "Create account" else "Sign in",
                            onClick = {
                                mode = if (mode == EmailMode.SIGN_IN) EmailMode.SIGN_UP else EmailMode.SIGN_IN
                                error = null
                            },
                            enabled = busy == null,
                        )
                    }
                }

                if (error != null) InlineError(error!!)
            }

            Spacer(Modifier.height(28.dp))
            FooterSection()
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(DsColor.divider))
        Text("OR", style = DsType.badgeSmall, color = DsColor.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(DsColor.divider))
    }
}

/**
 * Email entry for both sign-in and sign-up. No password field: this Clerk instance authenticates
 * email with a one-time code, so both buttons lead to a code in the inbox and [VerificationScreen].
 */
@Composable
private fun EmailCodeForm(
    email: String,
    mode: EmailMode,
    isLoading: Boolean,
    enabled: Boolean,
    canSubmit: Boolean,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Email")
        TextFieldCard(
            value = email,
            onValueChange = onEmail,
            placeholder = "you@example.com",
            enabled = enabled,
            textStyle = DsType.body,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        )
        Text("We'll email you a 6-digit code", style = DsType.helper, color = DsColor.textMuted)
        SecondaryButton(
            if (mode == EmailMode.SIGN_IN) "Sign in with email" else "Create account",
            onClick = onSubmit,
            icon = Icons.Filled.MarkEmailRead,
            enabled = canSubmit,
            loading = isLoading,
        )
    }
}

/**
 * Primary action with the Google "G" on a white disc — Google's branding guidelines require the
 * mark on a white field, and the disc keeps it while the button stays the screen's accent action.
 */
@Composable
private fun GoogleSignInButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DsSize.primaryButton)
            .shadow(16.dp, DsShape.lg, ambientColor = DsColor.accentGlow, spotColor = DsColor.accent)
            .clip(DsShape.lg)
            .background(DsColor.accent)
            .dsClickable(enabled = enabled && !loading, pressedScale = 0.985f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = DsColor.onAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(34.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { GoogleGIcon() }
            Text("Continue with Google", style = DsType.buttonWide, color = DsColor.onAccent)
        }
    }
}

@Composable
private fun FooterSection() {
    val uriHandler = LocalUriHandler.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("Terms", onClick = { runCatching { uriHandler.openUri(ApiClient.TERMS_URL) } }, tone = TextActionTone.Dismiss)
            Box(Modifier.size(4.dp).clip(CircleShape).background(DsColor.textSubtle))
            TextAction("Privacy", onClick = { runCatching { uriHandler.openUri(ApiClient.PRIVACY_URL) } }, tone = TextActionTone.Dismiss)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(label = "SERVER ONLINE", dot = true)
            StatusPill(label = "SSL ENCRYPTED", icon = Icons.Filled.Lock)
        }
    }
}

@Composable
private fun StatusPill(label: String, dot: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier
            .clip(DsShape.pill)
            .background(Color(0x09FFFFFF))
            .border(1.dp, DsColor.borderSubtle, DsShape.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) PulseDot(DsColor.success, 7.dp)
        if (icon != null) Icon(icon, null, tint = DsColor.textMuted, modifier = Modifier.size(12.dp))
        Text(label, style = DsType.badgeSmall, color = DsColor.textMuted)
    }
}

// 4-color Google "G" — ported from iOS AuthView.swift GoogleGIcon Canvas paths (24x24).
@Composable
private fun GoogleGIcon() {
    Canvas(Modifier.size(22.dp)) {
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
