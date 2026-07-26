package com.danglingmind.convotrack.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.auth.AuthResult
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.launch

/**
 * Email verification code entry. Clerk's email sign-up issues a 6-digit code that must be
 * verified before the session becomes active — this screen completes that step.
 */
@Composable
fun VerificationScreen(
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auth = remember { ServiceLocator.auth }

    fun submit() {
        if (busy || code.length != 6) return
        error = null
        busy = true
        scope.launch {
            when (val r = auth.verifyEmailCode(code)) {
                is AuthResult.Success -> onVerified()
                is AuthResult.Error -> {
                    error = r.message
                    busy = false
                }
                is AuthResult.NeedsEmailCode -> busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(CT.surfaceDim)) {
        // Back button
        Box(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(CT.surfaceContainerHigh)
                .clickable(onClick = onBack)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CT.onSurface,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(CT.primaryFixed.copy(alpha = 0.14f)))
                Icon(Icons.Filled.MarkEmailRead, null, tint = CT.primaryFixed, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("VERIFY YOUR EMAIL", style = CTType.headlineMd, color = CT.onSurface, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the 6-digit code we sent to\n$email",
                style = CTType.bodyMd,
                color = CT.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                placeholder = {
                    Text(
                        "000000",
                        style = CTType.headlineMd.copy(letterSpacing = 10.sp),
                        color = CT.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                },
                textStyle = CTType.headlineMd.copy(letterSpacing = 10.sp, textAlign = TextAlign.Center),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CT.primaryFixed,
                    unfocusedBorderColor = CT.outlineVariant,
                    focusedTextColor = CT.onSurface,
                    unfocusedTextColor = CT.onSurface,
                    cursorColor = CT.primaryFixed,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, style = CTType.captionMd, color = CT.errorColor, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            LimePrimaryButton(
                text = if (busy) "VERIFYING…" else "VERIFY",
                enabled = code.length == 6 && !busy,
                onClick = { submit() },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Wrong email? Go back to change it.",
                style = CTType.captionMd.copy(fontWeight = FontWeight.Medium),
                color = CT.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
