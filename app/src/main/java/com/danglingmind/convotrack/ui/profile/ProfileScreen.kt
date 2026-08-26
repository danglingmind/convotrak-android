package com.danglingmind.convotrack.ui.profile

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.ErrorBlock
import com.danglingmind.convotrack.ui.common.LoadingBlock
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import kotlinx.coroutines.launch

private data class ProfileData(
    val name: String,
    val subtitle: String,
    val isPremium: Boolean,
    val rides: Int,
    val totalKm: String,
    val led: Int,
)

@Composable
fun ProfileScreen(
    onOpenMembership: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val billing = remember { ServiceLocator.billing }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState<ProfileData>>(UiState.Loading) }

    LaunchedEffect(Unit) { billing.start() }

    suspend fun load() {
        state = UiState.Loading
        state = try {
            val me = apiCall { api.fetchMe() }
            // Ride aggregates are derived from history; non-fatal if that call fails.
            val rides = runCatching { apiCall { api.getMyRides().rides } }.getOrDefault(emptyList())
            // Local Play entitlement upgrades the server plan (never downgrades), mirroring iOS.
            val premium = me.plan.isPremium || billing.isPremium.value
            val count = rides.size
            UiState.Success(
                ProfileData(
                    name = me.name.ifBlank { me.username ?: "Rider" },
                    subtitle = (if (premium) "CONVOTRACK PRO" else "FREE PLAN") +
                        " · $count RIDE" + (if (count == 1) "" else "S"),
                    isPremium = premium,
                    rides = count,
                    totalKm = formatKm(rides.sumOf { it.distanceMeters }),
                    led = rides.count { it.isLeader == true },
                )
            )
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding(),
    ) {
        ConvoTrackTopBar(title = "PROFILE")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (val s = state) {
                is UiState.Loading -> LoadingBlock()
                is UiState.Error -> ErrorBlock(s.message, onRetry = { scope.launch { load() } })
                is UiState.Success -> ProfileContent(
                    data = s.data,
                    onOpenMembership = onOpenMembership,
                    onSignOut = { scope.launch { auth.signOut(); onSignedOut() } },
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    data: ProfileData,
    onOpenMembership: () -> Unit,
    onSignOut: () -> Unit,
) {
    // Header
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(CT.surfaceContainerHigh)
                .border(2.dp, CT.primaryFixed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, null, tint = CT.primaryFixed, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(data.name, style = CTType.headlineMd, color = CT.onSurface)
        Text(data.subtitle, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
    }

    // Stats (derived from ride history)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(data.rides.toString(), "RIDES", Modifier.weight(1f))
        StatTile(data.totalKm, "TOTAL KM", Modifier.weight(1f))
        StatTile(data.led.toString(), "RIDES LED", Modifier.weight(1f))
    }

    // Membership banner
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CT.primaryFixed.copy(alpha = 0.12f))
            .border(1.dp, CT.primaryFixed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenMembership)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("CONVOTRACK PRO", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 1.sp)
            Spacer(Modifier.size(4.dp))
            Text(
                if (data.isPremium) "Your membership is active" else "Unlimited rides & advanced telemetry",
                style = CTType.bodyMd,
                color = CT.onSurface,
            )
        }
        Text(
            if (data.isPremium) "ACTIVE" else "UPGRADE",
            style = CTType.labelCaps,
            color = CT.onPrimaryFixed,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(CT.primaryFixed)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    // Settings
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
    ) {
        SettingsRow(Icons.Filled.Notifications, "Notifications")
        Divider()
        SettingsRow(Icons.Filled.CreditCard, "Membership & Billing", onClick = onOpenMembership)
        Divider()
        SettingsRow(Icons.Filled.Shield, "Privacy")
        Divider()
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign Out", tint = CT.errorColor, onClick = onSignOut)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = CTType.headlineLg, color = CT.primaryFixed)
        Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = CT.onSurface,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (tint == CT.errorColor) CT.errorColor else CT.primaryFixed, modifier = Modifier.size(20.dp))
        Text(label, style = CTType.bodyLg.copy(fontWeight = FontWeight.Medium), color = tint)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(CT.outlineVariant.copy(alpha = 0.3f)),
    )
}
