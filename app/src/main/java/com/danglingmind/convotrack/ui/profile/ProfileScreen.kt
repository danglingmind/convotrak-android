package com.danglingmind.convotrack.ui.profile

import com.danglingmind.convotrack.data.analytics.Analytics

import com.danglingmind.convotrack.ui.designsystem.PromoBanner
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.selection.SelectionContainer
import com.danglingmind.convotrack.data.auth.AuthUser
import com.danglingmind.convotrack.data.auth.SignInMethod
import com.danglingmind.convotrack.ui.designsystem.SquareIconButton
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.danglingmind.convotrack.data.net.NotificationPreferences
import com.danglingmind.convotrack.data.net.NotificationPreferencesUpdate
import kotlinx.coroutines.launch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.Access
import com.danglingmind.convotrack.ui.common.PlanState
import com.danglingmind.convotrack.ui.common.rememberPlanState
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
import com.danglingmind.convotrack.ui.designsystem.LoadingState
import com.danglingmind.convotrack.ui.designsystem.LogoBadge
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.StatusBarScrim
import com.danglingmind.convotrack.ui.designsystem.bottomNavClearance
import com.danglingmind.convotrack.ui.designsystem.dsClickable
import com.danglingmind.convotrack.ui.designsystem.glassSurface
import kotlinx.coroutines.launch

private data class ProfileData(
    val name: String,
    val avatarUrl: String?,
    val userId: String,
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
    var showEdit by remember { mutableStateOf(false) }
    val authUser by auth.userState.collectAsState()

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
    val plan = rememberPlanState()

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
                    // Hidden for now: the top-right slot is reserved for a notification bell.
                    // ConnectionStatusButton()
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
                            account = authUser,
                            plan = plan,
                            onEditProfile = { showEdit = true },
                            onOpenMembership = onOpenMembership,
                            onSignOut = { scope.launch { Analytics.reset(); auth.signOut(); onSignedOut() } },
                            onDeleteAccount = { showDeleteConfirm = true },
                        )
                    }
                }
            }
        }
        StatusBarScrim()
    }

    val loaded = (state as? UiState.Success)?.data
    if (showEdit && loaded != null) {
        EditProfileSheet(
            userId = loaded.userId,
            initialName = loaded.name,
            currentAvatarUrl = loaded.avatarUrl,
            onDismiss = { showEdit = false },
            onSaved = { showEdit = false; scope.launch { load() } },
        )
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
                        Analytics.reset(recordSignOut = false)
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

/** The trial leads; the generic pitch is only for riders with no enforced trial (iOS parity). */
private fun profilePromoMessage(plan: PlanState): String = when (plan.access) {
    Access.Pro -> "Your membership is active"
    is Access.Trial -> plan.trialDaysLeft?.takeIf { it > 1 }?.let { "$it days left in your free trial" }
        ?: "Your free trial ends today"
    Access.TrialExpired -> "Your free trial has ended. Go Pro to keep riding."
    Access.Free -> "Unlimited rides & bigger packs"
}

@Composable
private fun ProfileContent(
    data: ProfileData,
    account: AuthUser?,
    plan: PlanState,
    onEditProfile: () -> Unit,
    onOpenMembership: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val photo = rememberAvatarPhoto(data.avatarUrl)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The photo is the obvious place to change the photo; it opens the same sheet as the pencil.
        Box(
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = "Change profile photo"; role = Role.Button }
                .dsClickable(onClick = onEditProfile),
        ) {
            Avatar(data.name, size = 96.dp, photo = photo, colorKey = data.userId, ring = DsColor.accent)
            AvatarEditBadge(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                data.name, style = DsType.titleLarge, color = DsColor.textPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            SquareIconButton(Icons.Filled.Edit, contentDescription = "Edit profile", onClick = onEditProfile, size = 44.dp)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ProfileStat(Icons.Filled.Flag, data.rides.toString(), "Rides", Modifier.weight(1f))
        ProfileStat(Icons.Filled.Route, data.totalKm, "Total km", Modifier.weight(1f))
        ProfileStat(Icons.Outlined.Groups, data.led.toString(), "Rides led", Modifier.weight(1f))
    }

    // Membership card: the gold Go Pro card for non-Pro riders (the only Pro prompt on this
    // screen; the badge under the name was redundant with it), the plain card for subscribers.
    PromoBanner(
        message = profilePromoMessage(plan),
        actionLabel = if (plan.isPremium) "Active" else "Go Pro",
        onAction = onOpenMembership,
        subscribed = plan.isPremium,
        goProStyle = true,
    )

    // Every row goes somewhere: a settings row that renders a chevron and does nothing reads as a
    // broken app, and the two legal rows are documents the store requires be reachable in-app.
    NotificationsSection()
    SettingsGroup {
        SettingsRow(Icons.Filled.CreditCard, "Membership & Billing", onClick = onOpenMembership)
        SettingsDivider()
        SettingsRow(Icons.Filled.Shield, "Privacy Policy", external = true) { uriHandler.openUri(ApiClient.PRIVACY_URL) }
        SettingsDivider()
        SettingsRow(Icons.Filled.Description, "Terms of Service", external = true) { uriHandler.openUri(ApiClient.TERMS_URL) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "ACCOUNT", style = DsType.statLabel, color = DsColor.textMuted,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        SettingsGroup {
            SignedInRow(account)
            SettingsDivider()
            SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", destructive = true, onClick = onSignOut)
        SettingsDivider()
            SettingsRow(Icons.Filled.DeleteForever, "Delete account", destructive = true, onClick = onDeleteAccount)
        }
    }
}

/** How and as whom the rider is signed in. Read-only; it names the method, not just "Signed in". */
@Composable
private fun SignedInRow(account: AuthUser?) {
    val method = when (account?.signInMethod) {
        SignInMethod.GOOGLE -> "Signed in with Google"
        SignInMethod.APPLE -> "Signed in with Apple"
        else -> "Signed in with email"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.AccountCircle, null, tint = DsColor.textSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(method, style = DsType.bodySmall, color = DsColor.textSecondary)
            SelectionContainer {
                Text(
                    account?.email ?: "—", style = DsType.body, color = DsColor.textPrimary,
                    maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
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


/** Notification categories (docs/notifications-plan.md, Phase 2). Safety alerts are never switchable. */
@Composable
private fun NotificationsSection() {
    val context = LocalContext.current
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<NotificationPreferences?>(null) }
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    LaunchedEffect(Unit) {
        prefs = runCatching { apiCall { api.getNotificationPreferences() } }.getOrNull()
    }

    // Optimistic: flip now, PUT, and put the old value back if the call fails.
    // Only the toggled key is changed/reverted, so overlapping toggles don't undo each other.
    fun change(
        update: NotificationPreferencesUpdate,
        get: (NotificationPreferences) -> Boolean,
        set: (NotificationPreferences, Boolean) -> NotificationPreferences,
    ) {
        val before = prefs ?: return
        val old = get(before)
        val wanted = update.rideUpdates ?: update.regroup ?: return
        prefs = set(before, wanted)
        scope.launch {
            runCatching { apiCall { api.updateNotificationPreferences(update) } }
                .onSuccess { r -> prefs = prefs?.let { set(it, get(r)) } }
                .onFailure { prefs = prefs?.let { set(it, old) } }
        }
    }

    SettingsGroup {
        Text(
            stringResource(R.string.notif_prefs_title).uppercase(),
            style = DsType.statLabel, color = DsColor.textMuted,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
        )
        PrefToggle(Icons.Filled.Notifications, stringResource(R.string.notif_prefs_ride_updates), prefs?.rideUpdates, prefs != null) { on ->
            change(NotificationPreferencesUpdate(rideUpdates = on), { it.rideUpdates }) { p, v -> p.copy(rideUpdates = v) }
        }
        SettingsDivider()
        PrefToggle(Icons.Filled.Flag, stringResource(R.string.notif_prefs_regroup), prefs?.regroup, prefs != null) { on ->
            change(NotificationPreferencesUpdate(regroup = on), { it.regroup }) { p, v -> p.copy(regroup = v) }
        }
        SettingsDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.Shield, null, tint = DsColor.textSecondary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.notif_prefs_safety), style = DsType.body, color = DsColor.textSecondary, modifier = Modifier.weight(1f))
        }
        if (!allowed) {
            SettingsDivider()
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notif_prefs_denied_hint), style = DsType.body, color = DsColor.textSecondary)
                Text(
                    stringResource(R.string.notif_prefs_open_settings),
                    style = DsType.body, color = DsColor.accent,
                    modifier = Modifier.dsClickable(pressedScale = 1f) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PrefToggle(icon: ImageVector, label: String, checked: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = DsColor.textSecondary, modifier = Modifier.size(20.dp))
        Text(label, style = DsType.body, color = DsColor.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked ?: true,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = DsColor.accent,
                checkedThumbColor = DsColor.onAccent,
                uncheckedTrackColor = DsColor.field,
                uncheckedThumbColor = DsColor.textSecondary,
                uncheckedBorderColor = DsColor.border,
            ),
        )
    }
}
