package com.danglingmind.convotrack.ui.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.UiState
import com.danglingmind.convotrack.ui.common.formatKm
import com.danglingmind.convotrack.ui.common.rememberAvatarPhoto
import com.danglingmind.convotrack.ui.designsystem.Avatar
import com.danglingmind.convotrack.ui.designsystem.ConfirmDialog
import com.danglingmind.convotrack.ui.designsystem.ConnectionStatusButton
import com.danglingmind.convotrack.ui.designsystem.DialogText
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsShape
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.DsType
import com.danglingmind.convotrack.ui.designsystem.ErrorState
import com.danglingmind.convotrack.ui.designsystem.IconDisc
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.LogoBadge
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.PillButton
import com.danglingmind.convotrack.ui.designsystem.PillTone
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.bottomNavClearance
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import kotlinx.coroutines.launch

private data class ProfileData(
    val name: String,
    val avatarUrl: String?,
    val userId: String,
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
                    avatarUrl = me.avatarUrl,
                    userId = me.userId,
                    subtitle = (if (premium) "CONVOTRACK PRO" else "FREE PLAN") +
                        " · $count RIDE" + (if (count == 1) "" else "S"),
                    isPremium = premium,
                    rides = count,
                    totalKm = formatKm(rides.sumOf { it.distanceMeters }),
                    led = rides.count { it.isLeader == true },
                ),
            )
        } catch (e: Exception) {
            UiState.Error(e.riderMessage)
        }
    }

    LaunchedEffect(Unit) { load() }

    DsScreen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                PhotoBackdrop(height = 220.dp, drawable = R.drawable.hero_road, verticalBias = 0.35f, alpha = 0.7f)
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = DsSpace.gutter).padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogoBadge()
                    Spacer(Modifier.weight(1f))
                    ConnectionStatusButton()
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = DsSpace.gutter).padding(top = 130.dp, bottom = bottomNavClearance()),
                    verticalArrangement = Arrangement.spacedBy(DsSpace.sectionGap),
                ) {
                    when (val s = state) {
                        is UiState.Loading -> LoadingState()
                        is UiState.Error -> ErrorState(s.message, onRetry = { scope.launch { load() } })
                        is UiState.Success -> ProfileContent(
                            data = s.data,
                            onOpenMembership = onOpenMembership,
                            onSignOut = { scope.launch { auth.signOut(); onSignedOut() } },
                            onDeleteAccount = { showDeleteConfirm = true },
                        )
                    }
                }
            }
        }
        StatusBarScrim()
    }

    if (showDeleteConfirm) {
        // Names what goes rather than asking "are you sure", and says the Play subscription is a
        // separate thing — a rider who assumes deleting the account stops billing would otherwise
        // keep being charged for a plan attached to nothing.
        ConfirmDialog(
            title = "Delete your account?",
            onDismiss = { if (!deleting) { showDeleteConfirm = false; deleteError = null } },
            confirmLabel = if (deleting) "Deleting…" else "Delete",
            destructive = true,
            busy = deleting,
            error = deleteError,
            onConfirm = {
                if (deleting) return@ConfirmDialog
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        apiCall { api.deleteAccount() }
                        // The account is gone server-side, so the session token authorises nothing;
                        // clearing it locally is cleanup, not a sign-out the rider could undo.
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
        ) {
            DialogText("This erases your profile, your ride history, and any rides you lead — for everyone in them. It cannot be undone.")
            DialogText("A ConvoTrack Pro subscription is billed by Google Play and is not cancelled by this. Cancel it in Google Play subscription settings.")
        }
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
    val photo = rememberAvatarPhoto(data.avatarUrl)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(data.name, size = 96.dp, photo = photo, colorKey = data.userId, ring = DsColor.accent)
        Spacer(Modifier.height(14.dp))
        Text(data.name, style = DsType.titleLarge, color = DsColor.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(data.subtitle, style = DsType.badgeSmall, color = DsColor.textMuted)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ProfileStat(Icons.Filled.Flag, data.rides.toString(), "Rides", Modifier.weight(1f))
        ProfileStat(Icons.Filled.Route, data.totalKm, "Total km", Modifier.weight(1f))
        ProfileStat(Icons.Outlined.Groups, data.led.toString(), "Rides led", Modifier.weight(1f))
    }

    // Membership banner
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(DsShape.lg, DsColor.accentTint, DsColor.accentBorder)
            .dsClickable(pressedScale = 0.99f, onClick = onOpenMembership)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconDisc(Icons.Filled.WorkspacePremium, size = 40.dp, background = DsColor.accentTonal)
        Column(Modifier.weight(1f)) {
            Text("CONVOTRACK PRO", style = DsType.fieldLabel, color = DsColor.accent)
            Spacer(Modifier.height(2.dp))
            Text(
                if (data.isPremium) "Your membership is active" else "Unlimited rides & advanced telemetry",
                style = DsType.bodySmall,
                color = DsColor.textPrimary,
            )
        }
        PillButton(
            if (data.isPremium) "Active" else "Upgrade",
            onClick = onOpenMembership,
            tone = if (data.isPremium) PillTone.Tonal else PillTone.Primary,
        )
    }

    // Every row goes somewhere: a settings row that renders a chevron and does nothing reads as a
    // broken app, and the two legal rows are documents the store requires be reachable in-app.
    SettingsGroup {
        SettingsRow(Icons.Filled.Notifications, "Notifications", external = true) {
            // The ride-tracking notification is the only one the app posts, and its channel is
            // owned by the system — so this hands over to the place that can actually change it.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        SettingsDivider()
        SettingsRow(Icons.Filled.CreditCard, "Membership & Billing", onClick = onOpenMembership)
        SettingsDivider()
        SettingsRow(Icons.Filled.Shield, "Privacy Policy", external = true) { uriHandler.openUri(ApiClient.PRIVACY_URL) }
        SettingsDivider()
        SettingsRow(Icons.Filled.Description, "Terms of Service", external = true) { uriHandler.openUri(ApiClient.TERMS_URL) }
    }
    SettingsGroup {
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", destructive = true, onClick = onSignOut)
        SettingsDivider()
        SettingsRow(Icons.Filled.DeleteForever, "Delete account", destructive = true, onClick = onDeleteAccount)
    }
}

@Composable
private fun ProfileStat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.glassSurface(DsShape.md).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = DsColor.textSecondary, modifier = Modifier.size(18.dp))
        Text(value, style = DsType.metricValue, color = DsColor.textPrimary, maxLines = 1)
        Text(label.uppercase(), style = DsType.statLabel, color = DsColor.textMuted, maxLines = 1)
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glassSurface(DsShape.lg, DsColor.cardQuiet, DsColor.borderHairline)) { content() }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(DsColor.divider))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) DsColor.danger else DsColor.textSecondary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).dsClickable(pressedScale = 1f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = DsType.body, color = if (destructive) DsColor.danger else DsColor.textPrimary, modifier = Modifier.weight(1f))
        if (!destructive) {
            Icon(if (external) Icons.Filled.NorthEast else Icons.Filled.ChevronRight, null, tint = DsColor.textMuted, modifier = Modifier.size(if (external) 16.dp else 20.dp))
        }
    }
}

