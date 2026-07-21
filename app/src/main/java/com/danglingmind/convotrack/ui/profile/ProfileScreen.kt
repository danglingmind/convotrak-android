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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar

@Composable
fun ProfileScreen(onOpenMembership: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CT.surfaceDim)
            .statusBarsPadding(),
    ) {
        ConvoTrackTopBar(title = "PROFILE")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
                Text("Prateek Reddy", style = CTType.headlineMd, color = CT.onSurface)
                Text("RIDE LEADER · 12 RIDES", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
            }

            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("428", "TOTAL KM", Modifier.weight(1f))
                StatTile("12", "RIDES LED", Modifier.weight(1f))
                StatTile("4.9", "AVG RANK", Modifier.weight(1f))
            }

            // Membership banner
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CT.primaryFixed.copy(alpha = 0.12f))
                    .border(1.dp, CT.primaryFixed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("CONVOTRACK PRO", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 1.sp)
                    Spacer(Modifier.size(4.dp))
                    Text("Unlimited rides & advanced telemetry", style = CTType.bodyMd, color = CT.onSurface)
                }
                Text(
                    "UPGRADE",
                    style = CTType.labelCaps,
                    color = CT.onPrimaryFixed,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CT.primaryFixed)
                        .clickable(onClick = onOpenMembership)
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
                SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign Out", tint = CT.errorColor)
            }
        }
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
