package com.danglingmind.convotrack.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiException
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton
import kotlinx.coroutines.launch

@Composable
fun JoinRideSheet(
    onJoined: (rideId: String) -> Unit,
    onScanQr: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()

    fun join() {
        if (busy || code.length != 6) return
        error = null
        busy = true
        scope.launch {
            try {
                // Resolve the code to a rideId, then join it (mirrors iOS join flow).
                val ride = apiCall { api.getRideByInviteCode(code) }
                apiCall { api.joinRide(ride.rideId) }
                onJoined(ride.rideId)
            } catch (e: ApiException) {
                error = friendlyJoinError(e.message)
                busy = false
            } catch (e: Exception) {
                error = "Something went wrong. Please try again."
                busy = false
            }
        }
    }

    Column(
        Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("JOIN A RIDE", style = CTType.headlineMd, color = CT.onSurface)
        Text(
            "Enter the 6-character invite code shared by the ride leader.",
            style = CTType.bodyMd,
            color = CT.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6); error = null },
            enabled = !busy,
            placeholder = { Text("K7QR9M", style = CTType.headlineMd, color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
            textStyle = CTType.headlineMd.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CT.primaryFixed,
                unfocusedBorderColor = CT.outlineVariant,
                focusedTextColor = CT.onSurface,
                unfocusedTextColor = CT.onSurface,
                cursorColor = CT.primaryFixed,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = !busy, onClick = onScanQr)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.QrCodeScanner, null, tint = CT.primaryFixed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("SCAN QR CODE", style = CTType.labelCaps, color = CT.onSurface, letterSpacing = 1.sp)
        }

        if (error != null) {
            Text(error!!, style = CTType.bodyMd, color = CT.errorColor, textAlign = TextAlign.Center)
        }

        LimePrimaryButton(
            text = if (busy) "JOINING…" else "JOIN RIDE",
            enabled = code.length == 6 && !busy,
            onClick = { join() },
        )
        Box(Modifier.size(4.dp))
    }
}

// Maps backend error codes to friendly copy (ported from iOS RidePreviewSheet.friendlyError).
private fun friendlyJoinError(code: String?): String = when (code) {
    "RIDE_FULL" -> "This ride is already full."
    "QUOTA_EXCEEDED" -> "You've reached your monthly ride limit."
    "ALREADY_JOINED" -> "You're already part of this ride."
    "RIDE_NOT_ACTIVE" -> "This ride is no longer accepting riders."
    "NOT_FOUND", "RIDE_NOT_FOUND" -> "No ride found for that code."
    else -> "Couldn't join. Check the code and try again."
}
