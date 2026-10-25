package com.danglingmind.convotrack.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.analytics.MetaEvents
import com.danglingmind.convotrack.data.auth.AuthResult
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.PushedHeader
import com.danglingmind.convotrack.ui.designsystem.TextFieldCard
import kotlinx.coroutines.launch

/**
 * Email verification code entry, for both email flows.
 *
 * The instance has no password: email is a first factor via a one-time code, so signing IN and
 * signing UP both end here with a 6-digit code that must be verified before the session becomes
 * active. The repository remembers which flow issued the code and attempts it against that one.
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
                is AuthResult.Success -> {
                    MetaEvents.signedIn("email")
                    onVerified()
                }
                is AuthResult.Error -> {
                    error = r.message
                    busy = false
                }
                is AuthResult.NeedsEmailCode -> busy = false
                is AuthResult.Cancelled -> busy = false
            }
        }
    }

    DsScreen {
        Column(Modifier.fillMaxSize()) {
            PushedHeader(title = "Verify email", onBack = onBack, trailing = {})
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = DsSpace.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconDisc(Icons.Filled.MarkEmailRead, size = 88.dp, iconSize = 40.dp, background = DsColor.accentTint)
                Spacer(Modifier.height(24.dp))
                Text("Check your email", style = DsType.heroTitleCompact, color = DsColor.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        append("Enter the 6-digit code we sent to\n")
                        withStyle(SpanStyle(color = DsColor.textPrimary, fontWeight = FontWeight.SemiBold)) { append(email) }
                    },
                    style = DsType.body,
                    color = DsColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                TextFieldCard(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    placeholder = "000000",
                    textStyle = DsType.otpDisplay,
                    textAlign = TextAlign.Center,
                    minHeight = 68.dp,
                    enabled = !busy,
                    highlighted = code.length == 6,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    InlineError(error!!)
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Verify", onClick = { submit() }, enabled = code.length == 6, loading = busy)
                Spacer(Modifier.height(16.dp))
                Text("Wrong email? Go back to change it.", style = DsType.helper, color = DsColor.textMuted)
            }
        }
    }
}
