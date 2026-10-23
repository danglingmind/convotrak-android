package com.danglingmind.convotrack.ui.join

import com.danglingmind.convotrack.data.analytics.Analytics

import com.danglingmind.convotrack.ui.designsystem.GoProButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.InviteCodeResponse
import com.danglingmind.convotrack.data.net.UserFacingError
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.isNotFoundError
import com.danglingmind.convotrack.data.net.isPlanLimit
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.FieldSpinner
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.SectionHeading
import com.danglingmind.convotrack.ui.designsystem.SheetTitle
import com.danglingmind.convotrack.ui.designsystem.TagChip
import com.danglingmind.convotrack.ui.designsystem.TextFieldCard
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JoinRideSheet(
    onJoined: (rideId: String) -> Unit,
    onScanQr: () -> Unit,
    prefillCode: String = "",
    /** The join was refused for a plan reason; the host closes this sheet and opens the paywall. */
    onSeePro: (code: String) -> Unit = {},
) {
    // Seeded from a deep link when present; the LaunchedEffect(code) below then auto-verifies it,
    // mirroring iOS JoinRideView(prefillCode:).
    var code by remember { mutableStateOf(prefillCode) }
    var preview by remember { mutableStateOf<InviteCodeResponse?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** The last join was refused for a plan reason (trial over / monthly rides used up). */
    var blockedByPlan by remember { mutableStateOf(false) }
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    val codeReady = code.length == 6

    LaunchedEffect(Unit) { Analytics.capture(Analytics.Event.JoinSheetOpened, mapOf("prefilled" to code.isNotEmpty())) }

    // Live lookup — as soon as a full 6-char code is entered, verify it and preview the ride.
    LaunchedEffect(code) {
        preview = null
        error = null
        blockedByPlan = false
        if (code.length == 6) {
            lookingUp = true
            delay(250) // debounce
            try {
                preview = apiCall { api.getRideByInviteCode(code) }
            } catch (e: Exception) {
                // A 404 from this endpoint means the code, not the ride, so it is remapped —
                // "That ride has vanished" would be nonsense for a code that was mistyped.
                error = if (isNotFoundError(e.message)) UserFacingError.messageForCode("INVITE_CODE_NOT_FOUND") else e.riderMessage
            }
            lookingUp = false
        }
    }

    fun join() {
        val p = preview ?: return
        if (joining) return
        error = null
        joining = true
        scope.launch {
            try {
                apiCall { api.joinRide(p.rideId) }
                onJoined(p.rideId)
            } catch (e: ApiException) {
                // Already a participant — treat as success and open the lobby (iOS parity).
                if (e.message == "ALREADY_JOINED" || e.message?.contains("already", ignoreCase = true) == true) {
                    onJoined(p.rideId)
                } else {
                    Analytics.capture(Analytics.Event.JoinFailed, mapOf("reason" to (e.message ?: "request_failed")))
                    error = e.riderMessage
                    blockedByPlan = e.isPlanLimit
                    joining = false
                }
            } catch (e: Exception) {
                Analytics.capture(Analytics.Event.JoinFailed, mapOf("reason" to "request_failed"))
                error = e.riderMessage
                joining = false
            }
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SheetTitle("Join a ride", Icons.Filled.PersonAdd)
        Column(Modifier.padding(horizontal = DsSpace.gutter), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            JoinHero(preview = preview, lookingUp = lookingUp)

            // Join sits directly beneath the verified ride so it's reachable without scrolling.
            if (preview != null) {
                PrimaryButton("Join ride", onClick = { join() }, icon = Icons.Filled.PersonAdd, loading = joining)
            }
            if (error != null) InlineError(error!!)
            if (blockedByPlan) {
                GoProButton("Go Pro", onClick = { onSeePro(code) }, icon = Icons.Filled.WorkspacePremium)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Enter invite code", compact = true)
                TextFieldCard(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    placeholder = "A1B2C3",
                    textStyle = DsType.codeDisplay,
                    textAlign = TextAlign.Center,
                    minHeight = 72.dp,
                    enabled = !joining,
                    highlighted = codeReady,
                    leadingIcon = Icons.Filled.Key,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    trailing = {
                        when {
                            lookingUp -> FieldSpinner()
                            else -> Icon(
                                if (codeReady) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                null,
                                tint = if (codeReady) DsColor.accent else DsColor.textSubtle,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
                Text("6-character code shared by the Ride Leader. Case-insensitive.", style = DsType.helper, color = DsColor.textMuted)
            }

            OrDivider()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Scan QR code", compact = true)
                QrScanTarget(enabled = !joining, onClick = onScanQr, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun JoinHero(preview: InviteCodeResponse?, lookingUp: Boolean) {
    val verified = preview != null
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .glassSurface(
                DsShape.lg,
                if (verified) DsColor.accentTint else DsColor.card,
                if (verified) DsColor.accentBorder else DsColor.borderSubtle,
            ),
    ) {
        // Dotted-grid texture.
        Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = 1.2.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (preview != null) {
                TagChip("Code verified")
                Text(preview.title, style = DsType.cardTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconLabel(Icons.Filled.Person, preview.leaderName)
                    Text("·", color = DsColor.textSubtle)
                    IconLabel(Icons.Filled.Group, "${preview.participantCount}/${preview.maxParticipants}")
                }
            } else {
                TagChip("System: standby", background = DsColor.neutralBadge, color = DsColor.textSecondary)
                Text("Ready to connect", style = DsType.cardTitle, color = DsColor.textPrimary)
                if (lookingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = DsColor.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("Verifying code…", style = DsType.bodySmall, color = DsColor.textSecondary)
                    }
                } else {
                    Text("Enter the 6-character code from your Ride Leader", style = DsType.bodySmall, color = DsColor.textMuted, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun IconLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = DsColor.textSecondary, modifier = Modifier.size(15.dp))
        Text(text, style = DsType.bodySmall, color = DsColor.textSecondary, maxLines = 1)
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

/** Tap-to-scan box with accent corner brackets (iOS QRCornerBrackets). */
@Composable
private fun QrScanTarget(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 240.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .glassSurface(DsShape.lg, DsColor.card, DsColor.border)
            .dsClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 16.dp.toPx()
            val len = 30.dp.toPx()
            val w = 4.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(DsColor.accent, Offset(x, y + dy * len), Offset(x, y), w, StrokeCap.Round)
                drawLine(DsColor.accent, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Round)
            }
            corner(inset, inset, 1f, 1f)
            corner(size.width - inset, inset, -1f, 1f)
            corner(inset, size.height - inset, 1f, -1f)
            corner(size.width - inset, size.height - inset, -1f, -1f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconDisc(Icons.Filled.CameraAlt, size = 60.dp, iconSize = 28.dp, background = DsColor.accentTint)
            Text("Tap to scan", style = DsType.buttonPill, color = DsColor.accent)
        }
    }
}

