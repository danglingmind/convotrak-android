package com.danglingmind.convotrack.ui.join

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.InviteCodeResponse
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JoinRideSheet(
    onJoined: (rideId: String) -> Unit,
    onScanQr: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<InviteCodeResponse?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    val codeReady = code.length == 6
    val canJoin = preview != null && !joining

    // Live lookup — as soon as a full 6-char code is entered, verify it and preview the ride
    // (mirrors iOS JoinRideView.onChange lookup).
    LaunchedEffect(code) {
        preview = null
        error = null
        if (code.length == 6) {
            lookingUp = true
            delay(250) // debounce
            try {
                preview = apiCall { api.getRideByInviteCode(code) }
            } catch (e: ApiException) {
                error = friendlyLookupError(e.message)
            } catch (e: Exception) {
                error = "Couldn't verify that code. Please try again."
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
                    error = friendlyJoinError(e.message)
                    joining = false
                }
            } catch (e: Exception) {
                error = "Something went wrong. Please try again."
                joining = false
            }
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HeroSection(preview = preview, lookingUp = lookingUp)

        CodeInputSection(
            code = code,
            codeReady = codeReady,
            lookingUp = lookingUp,
            enabled = !joining,
            onCode = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
        )

        OrDivider()

        QrScanTarget(enabled = !joining, onClick = onScanQr)

        if (error != null) {
            Text(error!!, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        LimePrimaryButton(
            text = if (joining) "JOINING…" else "JOIN RIDE",
            enabled = canJoin,
            onClick = { join() },
        )
    }
}

@Composable
private fun HeroSection(preview: InviteCodeResponse?, lookingUp: Boolean) {
    val verified = preview != null
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (verified) CT.primaryFixed.copy(alpha = 0.10f) else CT.surfaceContainerLow)
            .border(
                if (verified) 1.5.dp else 1.dp,
                if (verified) CT.primaryFixed.copy(alpha = 0.6f) else CT.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            ),
    ) {
        // Subtle dotted-grid texture (iOS Canvas grid stand-in).
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val step = 24.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), radius = 1.2f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (verified) 8.dp else 6.dp),
        ) {
            if (preview != null) {
                Chip(text = "CODE VERIFIED", onLime = true)
                Text(preview.title, style = CTType.headlineMd, color = CT.onSurface, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconLabel(Icons.Filled.Person, preview.leaderName)
                    Text("·", color = CT.outline)
                    IconLabel(Icons.Filled.Group, "${preview.participantCount}/${preview.maxParticipants}")
                }
            } else {
                Chip(text = "SYSTEM: STANDBY", onLime = false)
                Text("Ready to Connect", style = CTType.headlineMd, color = CT.onSurface)
                if (lookingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(color = CT.primaryFixed, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("Verifying code…", style = CTType.bodyMd, color = CT.onSurfaceVariant)
                    }
                } else {
                    Text("Enter the 6-character code from your Ride Leader", style = CTType.bodyMd, color = CT.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, onLime: Boolean) {
    Text(
        text,
        style = CTType.labelCaps.copy(fontSize = 11.sp),
        color = if (onLime) CT.onPrimaryFixed else CT.primaryFixed,
        letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (onLime) CT.primaryFixed else CT.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = CT.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = CTType.bodyMd, color = CT.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun CodeInputSection(
    code: String,
    codeReady: Boolean,
    lookingUp: Boolean,
    enabled: Boolean,
    onCode: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Key, null, tint = CT.primaryFixed, modifier = Modifier.size(16.dp))
            Text("ENTER INVITE CODE", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 3.sp)
        }

        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            enabled = enabled,
            placeholder = {
                Text(
                    "A1B2C3",
                    color = CT.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    style = CTType.dataMono.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            textStyle = CTType.dataMono.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 6.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            trailingIcon = {
                if (lookingUp) {
                    CircularProgressIndicator(color = CT.primaryFixed, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        if (codeReady) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        null,
                        tint = if (codeReady) CT.primaryFixed else CT.outlineVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CT.primaryFixed,
                unfocusedBorderColor = if (codeReady) CT.primaryFixed else CT.outlineVariant,
                focusedTextColor = CT.primaryFixed,
                unfocusedTextColor = CT.primaryFixed,
                cursorColor = CT.primaryFixed,
                focusedContainerColor = CT.surfaceContainerHighest,
                unfocusedContainerColor = CT.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )

        Text(
            "6-character code shared by the Ride Leader. Case-insensitive.",
            style = CTType.bodyMd,
            color = CT.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(CT.outlineVariant.copy(alpha = 0.3f)))
        Text("OR", style = CTType.labelCaps, color = CT.outline, letterSpacing = 2.sp)
        Box(Modifier.weight(1f).height(1.dp).background(CT.outlineVariant.copy(alpha = 0.3f)))
    }
}

@Composable
private fun QrScanTarget(enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CameraAlt, null, tint = CT.primaryFixed, modifier = Modifier.size(18.dp))
            Text("SCAN QR CODE", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 3.sp)
        }
        Box(
            Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CT.surfaceContainerLow)
                .border(2.dp, CT.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // Lime corner brackets, matching iOS QRCornerBrackets.
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val inset = 16.dp.toPx()
                val len = 32.dp.toPx()
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                val lime = CT.primaryFixed
                fun corner(hStart: Float, vStart: Float, dx: Float, dy: Float) {
                    drawLine(lime, Offset(hStart, vStart + dy * len), Offset(hStart, vStart), stroke.width, stroke.cap)
                    drawLine(lime, Offset(hStart, vStart), Offset(hStart + dx * len, vStart), stroke.width, stroke.cap)
                }
                corner(inset, inset, 1f, 1f)
                corner(size.width - inset, inset, -1f, 1f)
                corner(inset, size.height - inset, 1f, -1f)
                corner(size.width - inset, size.height - inset, -1f, -1f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CameraAlt, null, tint = CT.primaryFixed, modifier = Modifier.size(36.dp))
                Text("TAP TO SCAN", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 3.sp)
            }
        }
    }
}

// Maps invite-code lookup errors to friendly copy.
private fun friendlyLookupError(code: String?): String = when {
    code == null -> "Couldn't verify that code."
    code.contains("NOT_FOUND") -> "No ride found with this code. Double-check and try again."
    code.contains("RIDE_ENDED") -> "This ride has already ended."
    else -> "Couldn't verify that code. Please try again."
}

// Maps backend join errors to friendly copy (ported from iOS RidePreviewSheet.friendlyError).
private fun friendlyJoinError(code: String?): String = when (code) {
    "RIDE_FULL" -> "This ride is already full."
    "QUOTA_EXCEEDED" -> "You're already in another active ride. End it before joining a new one."
    "ALREADY_JOINED" -> "You're already part of this ride."
    "RIDE_NOT_ACTIVE", "RIDE_ENDED" -> "This ride is no longer accepting riders."
    "NOT_FOUND", "RIDE_NOT_FOUND" -> "No ride found for that code."
    else -> "Couldn't join. Check the code and try again."
}
