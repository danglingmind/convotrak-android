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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.auth.AuthResult
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import kotlinx.coroutines.launch

private const val FIELD_WIDTH = 350

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
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(EmailMode.SIGN_IN) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auth = remember { ServiceLocator.auth }

    val emailValid = email.contains("@") && email.contains(".") && email.length > 4
    val passwordValid = password.length >= 8
    val canSubmitEmail = emailValid && passwordValid && busy == null

    fun run(provider: Provider) {
        if (busy != null) return
        error = null
        busy = provider
        scope.launch {
            val result = when (provider) {
                Provider.GOOGLE -> auth.signInWithGoogle()
                Provider.EMAIL -> when (mode) {
                    EmailMode.SIGN_IN -> auth.signInWithEmail(email.trim(), password)
                    EmailMode.SIGN_UP -> auth.signUpWithEmail(email.trim(), password)
                }
            }
            busy = null
            when (result) {
                is AuthResult.Success -> onSignedIn()
                is AuthResult.NeedsEmailCode -> onNeedsVerification(email.trim())
                is AuthResult.Error -> error = result.message
            }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader()

            Spacer(Modifier.height(32.dp))

            // Google — the primary CTA, keeps the lime treatment from iOS.
            SignInButton(
                label = "Continue with Google",
                foreground = CT.onPrimaryFixed,
                background = CT.primaryFixed,
                border = CT.primaryFixed,
                borderWidth = 2.dp,
                isLoading = busy == Provider.GOOGLE,
                enabled = busy == null,
                leading = { GoogleGIcon() },
                onClick = { run(Provider.GOOGLE) },
            )

            OrDivider()

            EmailPasswordForm(
                email = email,
                password = password,
                showPassword = showPassword,
                mode = mode,
                isLoading = busy == Provider.EMAIL,
                enabled = busy == null,
                canSubmit = canSubmitEmail,
                onEmail = { email = it; error = null },
                onPassword = { password = it; error = null },
                onToggleVisibility = { showPassword = !showPassword },
                onSubmit = { run(Provider.EMAIL) },
            )

            if (error != null) {
                Text(
                    error!!,
                    style = CTType.captionMd,
                    color = CT.errorColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp).width(FIELD_WIDTH.dp),
                )
            }

            // Sign in <-> Create account
            Row(
                Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (mode == EmailMode.SIGN_IN) "New rider?" else "Already riding?",
                    style = CTType.captionMd,
                    color = CT.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    if (mode == EmailMode.SIGN_IN) "Create account" else "Sign in",
                    style = CTType.captionMd.copy(fontWeight = FontWeight.Bold),
                    color = CT.primaryFixed,
                    modifier = Modifier.clickable(enabled = busy == null) {
                        mode = if (mode == EmailMode.SIGN_IN) EmailMode.SIGN_UP else EmailMode.SIGN_IN
                        error = null
                    },
                )
            }

            Spacer(Modifier.height(32.dp))
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
            style = CTType.displayMetrics.copy(fontSize = 38.sp),
            color = CT.primaryFixed,
            letterSpacing = 6.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        Spacer(Modifier.height(8.dp))
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
private fun OrDivider() {
    Row(
        Modifier.width(FIELD_WIDTH.dp).padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(CT.outlineVariant.copy(alpha = 0.4f)))
        Text("OR", style = CTType.labelCaps, color = CT.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 2.sp)
        Box(Modifier.weight(1f).height(1.dp).background(CT.outlineVariant.copy(alpha = 0.4f)))
    }
}

@Composable
private fun EmailPasswordForm(
    email: String,
    password: String,
    showPassword: Boolean,
    mode: EmailMode,
    isLoading: Boolean,
    enabled: Boolean,
    canSubmit: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.width(FIELD_WIDTH.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmail,
            enabled = enabled,
            placeholder = { Text("you@example.com", color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
            label = { Text("EMAIL", style = CTType.labelCaps, letterSpacing = 1.sp) },
            singleLine = true,
            textStyle = CTType.bodyMd,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            shape = RoundedCornerShape(4.dp),
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            enabled = enabled,
            placeholder = { Text("••••••••", color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
            label = { Text("PASSWORD", style = CTType.labelCaps, letterSpacing = 1.sp) },
            singleLine = true,
            textStyle = CTType.bodyMd,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = CT.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onToggleVisibility)
                        .padding(4.dp)
                        .size(20.dp),
                )
            },
            supportingText = {
                if (mode == EmailMode.SIGN_UP) {
                    Text("At least 8 characters", style = CTType.captionMd, color = CT.onSurfaceVariant.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(4.dp),
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        SignInButton(
            label = if (mode == EmailMode.SIGN_IN) "Sign in with Email" else "Create account",
            foreground = CT.onSurface,
            background = CT.surfaceContainerHighest,
            border = CT.outline.copy(alpha = 0.3f),
            borderWidth = 1.dp,
            isLoading = isLoading,
            enabled = canSubmit,
            leading = { Icon(Icons.Filled.Lock, null, tint = CT.onSurface, modifier = Modifier.size(18.dp)) },
            onClick = onSubmit,
        )
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CT.primaryFixed,
    unfocusedBorderColor = CT.outline.copy(alpha = 0.3f),
    focusedTextColor = CT.onSurface,
    unfocusedTextColor = CT.onSurface,
    cursorColor = CT.primaryFixed,
    focusedLabelColor = CT.primaryFixed,
    unfocusedLabelColor = CT.onSurfaceVariant,
    focusedContainerColor = CT.surfaceContainerLow,
    unfocusedContainerColor = CT.surfaceContainerLow,
)

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
            .width(FIELD_WIDTH.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) background else background.copy(alpha = 0.5f))
            .border(borderWidth, if (enabled) border else border.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = label.uppercase(),
                style = CTType.bodyMd.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) foreground else foreground.copy(alpha = 0.5f),
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
            StatusPill(
                icon = { Icon(Icons.Filled.Lock, null, tint = CT.onSurfaceVariant, modifier = Modifier.size(11.dp)) },
                label = "SSL ENCRYPTED",
            )
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
