package com.danglingmind.convotrack.ui.profile

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.ApiClient
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

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

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
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // The floating bottom nav is drawn over this scroll area, so the last row has to
                // be given room to clear it — the same 120.dp the home list reserves. Without it
                // "Delete Account", now the final row, sits half-hidden behind the nav bar.
                .padding(top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (val s = state) {
                is UiState.Loading -> LoadingBlock()
                is UiState.Error -> ErrorBlock(s.message, onRetry = { scope.launch { load() } })
                is UiState.Success -> ProfileContent(
                    data = s.data,
                    onOpenMembership = onOpenMembership,
                    onSignOut = { scope.launch { auth.signOut(); onSignedOut() } },
                    onDeleteAccount = { showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteAccountDialog(
            deleting = deleting,
            error = deleteError,
            onDismiss = { if (!deleting) { showDeleteConfirm = false; deleteError = null } },
            onConfirm = {
                if (deleting) return@DeleteAccountDialog
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        apiCall { api.deleteAccount() }
                        // The account is gone server-side, so the session token now authorises
                        // nothing. Clearing it locally is cleanup after the fact, not a sign-out
                        // the rider could undo by signing back in — there is nothing to sign in to.
                        auth.signOut()
                        showDeleteConfirm = false
                        onSignedOut()
                    } catch (e: Exception) {
                        deleteError = e.riderMessage
                    } finally {
                        deleting = false
                    }
                }
            },
        )
    }
}

@Composable
private fun ProfileContent(
    data: ProfileData,
    onOpenMembership: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

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

    // Settings. Every row here goes somewhere: a settings list that renders a chevron and then
    // does nothing on tap reads as a broken app, and the two legal rows are the app's own copy of
    // documents the store requires be reachable from inside it, not only from the listing.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
    ) {
        SettingsRow(Icons.Filled.Notifications, "Notifications") {
            // The ride-tracking notification is the only one the app posts, and its channel is
            // owned by the system — so this hands over to the place that can actually change it
            // rather than mirroring those controls badly.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        Divider()
        SettingsRow(Icons.Filled.CreditCard, "Membership & Billing", onClick = onOpenMembership)
        Divider()
        SettingsRow(Icons.Filled.Shield, "Privacy Policy") { uriHandler.openUri(ApiClient.PRIVACY_URL) }
        Divider()
        SettingsRow(Icons.Filled.Description, "Terms of Service") { uriHandler.openUri(ApiClient.TERMS_URL) }
        Divider()
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign Out", tint = CT.errorColor, onClick = onSignOut)
        Divider()
        SettingsRow(Icons.Filled.DeleteForever, "Delete Account", tint = CT.errorColor, onClick = onDeleteAccount)
    }
}

/**
 * Confirmation for the one action in the app that cannot be walked back.
 *
 * It names what goes rather than asking "are you sure", and it says out loud that the Play
 * subscription is a separate thing — a rider who deletes their account assuming it also stops the
 * billing will otherwise keep being charged for a plan attached to nothing, which is the worst
 * possible way to find out.
 */
@Composable
private fun DeleteAccountDialog(
    deleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CT.surfaceContainer,
        title = { Text("Delete your account?", style = CTType.headlineMd, color = CT.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This erases your profile, your ride history, and any rides you lead — for " +
                        "everyone in them. It cannot be undone.",
                    style = CTType.bodyMd,
                    color = CT.onSurfaceVariant,
                )
                Text(
                    "A ConvoTrack Pro subscription is billed by Google Play and is not cancelled " +
                        "by this. Cancel it in Google Play subscription settings.",
                    style = CTType.bodyMd,
                    color = CT.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = CTType.bodyMd, color = CT.errorColor)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(
                    if (deleting) "DELETING…" else "DELETE",
                    style = CTType.labelCaps,
                    color = if (deleting) CT.onSurfaceVariant else CT.errorColor,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text("CANCEL", style = CTType.labelCaps, color = CT.onSurfaceVariant)
            }
        },
    )
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
    onClick: () -> Unit,
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
