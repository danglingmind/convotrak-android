package com.danglingmind.convotrack.ui.designsystem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.socket.SocketService
import com.danglingmind.convotrack.di.ServiceLocator

// MARK: - Labels and headings

/** Wide-tracked uppercase section label with an optional trailing slot (`SectionHeading`). */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    /** Form screens use the smaller field-label size for in-form headings (ROUTE PREVIEW). */
    compact: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = if (compact) DsType.fieldLabel else DsType.sectionLabel,
            color = if (compact) DsColor.textSecondary else DsColor.textLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The label takes all the slack: a shared weight with a spacer capped it at half the row.
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Trailing text action of a heading: SEE ALL ›. */
@Composable
fun SectionAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = DsSize.touchTarget)
            .clip(DsShape.pill)
            .dsClickable(onClick = onClick)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = DsType.sectionAction, color = DsColor.textLabel)
        Icon(Icons.Filled.ChevronRight, null, tint = DsColor.textLabel, modifier = Modifier.size(18.dp))
    }
}

/** Outlined pill used as a heading action: VIEW ON MAP ↗. */
@Composable
fun OutlinedPillAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(32.dp)
            .clip(DsShape.pill)
            .border(1.dp, DsColor.border, DsShape.pill)
            .dsClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text.uppercase(), style = DsType.sectionAction.sized(DsType.micro.fontSize), color = DsColor.textSecondary)
        Icon(icon, null, tint = DsColor.textPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Pulsing accent dot + label: ● RIDING, ● LIVE. */
@Composable
fun LiveIndicator(text: String, modifier: Modifier = Modifier, color: Color = DsColor.accent, pulse: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PulseDot(color = color, size = 7.dp, pulse = pulse)
        Text(text.uppercase(), style = DsType.sectionAction, color = color, maxLines = 1)
    }
}

@Composable
fun PulseDot(color: Color, size: Dp = 7.dp, pulse: Boolean = true) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(size).alpha(alpha).clip(CircleShape).background(color))
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = DsType.fieldLabel, color = DsColor.textSecondary, modifier = modifier)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = DsColor.textLabel) {
    Text(text.uppercase(), style = DsType.eyebrow, color = color, modifier = modifier)
}

/** Text error with an icon — never colour alone. */
@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Error, null, tint = DsColor.danger, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(message, style = DsType.bodySmall, color = DsColor.danger, textAlign = TextAlign.Center)
    }
}

// MARK: - Connection status

enum class ConnectionStatus { Live, Online, Reconnecting, Offline }

/**
 * What the header antenna shows. Inside a ride the realtime session is the truth; elsewhere the
 * socket is normally idle, so plain network reachability decides between Online and Offline.
 */
@Composable
fun rememberConnectionStatus(): ConnectionStatus {
    val socketState by ServiceLocator.socket.connectionState.collectAsState()
    val online = rememberNetworkAvailable()
    return when {
        !online -> ConnectionStatus.Offline
        socketState == SocketService.ConnectionState.CONNECTED -> ConnectionStatus.Live
        socketState == SocketService.ConnectionState.RECONNECTING -> ConnectionStatus.Reconnecting
        else -> ConnectionStatus.Online
    }
}

@Composable
private fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember {
        mutableStateOf(
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        )
    }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) {
                online = cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** The header antenna: accent icon with a status dot. */
@Composable
fun ConnectionStatusButton(
    modifier: Modifier = Modifier,
    status: ConnectionStatus = rememberConnectionStatus(),
    tone: FloatingTone = FloatingTone.AccentIcon,
) {
    val dot by animateColorAsState(
        when (status) {
            ConnectionStatus.Live, ConnectionStatus.Online -> DsColor.success
            ConnectionStatus.Reconnecting -> DsColor.warning
            ConnectionStatus.Offline -> DsColor.textSubtle
        },
        label = "dot",
    )
    val label = when (status) {
        ConnectionStatus.Live -> "Live, connected to the ride"
        ConnectionStatus.Online -> "Online"
        ConnectionStatus.Reconnecting -> "Reconnecting"
        ConnectionStatus.Offline -> "Offline"
    }
    FloatingButton(
        icon = Icons.Outlined.SettingsInputAntenna,
        contentDescription = label,
        onClick = null,
        tone = tone,
        modifier = modifier.semantics { contentDescription = label },
        badge = {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(DsColor.bgBase)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
        },
    )
}

// MARK: - Photo backdrop

/**
 * A hero photo pinned to the top of a screen, faded into background.base (`layout.heroPhoto`).
 * [alignmentBias] picks the visible band of a tall photo (0 = top, 1 = bottom).
 */
@Composable
fun PhotoBackdrop(
    height: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes drawable: Int? = null,
    bitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    verticalBias: Float = 0.5f,
    alpha: Float = 1f,
    sideFade: Boolean = false,
    fade: Brush = DsGradient.photoFade,
) {
    Box(modifier.fillMaxWidth().height(height)) {
        val align = androidx.compose.ui.BiasAlignment(0f, verticalBias * 2f - 1f)
        when {
            bitmap != null -> Image(bitmap, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop, alignment = align)
            drawable != null -> Image(painterResource(drawable), null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop, alignment = align)
        }
        if (sideFade) Box(Modifier.fillMaxSize().background(DsGradient.photoSideFade))
        Box(Modifier.fillMaxSize().background(fade))
    }
}

/** Screen root: the app background gradient. */
@Composable
fun DsScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(DsGradient.appBackground), content = content)
}

// MARK: - Headers

/** Pushed-screen header: back · centred title + subtitle · trailing (connection by default). */
@Composable
fun PushedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = { ConnectionStatusButton() },
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = DsSpace.gutter)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title.uppercase(), style = DsType.headerTitle, color = DsColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = DsType.headerSubtitle, color = DsColor.textMuted, maxLines = 1)
            }
        }
        Box(Modifier.size(DsSize.floatingButton), contentAlignment = Alignment.Center) { trailing() }
    }
}

/** App logo badge (top-start on Home). */
@Composable
fun LogoBadge(modifier: Modifier = Modifier, size: Dp = DsSize.floatingButton) {
    Image(painterResource(R.drawable.logo_circle), contentDescription = "ConvoTrack", modifier = modifier.size(size))
}

// MARK: - Bottom nav

enum class DsTab { History, Home, Profile }

/** Space the floating bottom nav covers, for scroll content to clear it. */
@Composable
fun bottomNavClearance(): Dp =
    DsSize.navBar + 8.dp + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Floating bar: History · Home · Profile. The active tab sits in a raised accent circle (the
 * reference's centre button); the others show icon + label. [active] null = no tab selected
 * (the lobby, which sits outside the three tabs).
 */
@Composable
fun BottomNavFloating(
    active: DsTab?,
    onSelect: (DsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = DsSpace.gutter)
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DsSize.navBar)
                .shadow(24.dp, DsShape.nav, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(DsShape.nav)
                .background(DsColor.nav)
                .border(1.dp, Color(0x14FFFFFF), DsShape.nav),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(DsTab.History, "History", Icons.Outlined.Flag, Icons.Filled.Flag, active, onSelect, Modifier.weight(1f))
            NavItem(DsTab.Home, "Home", Icons.Outlined.NearMe, Icons.Filled.NearMe, active, onSelect, Modifier.weight(1f))
            NavItem(DsTab.Profile, "Profile", Icons.Outlined.Person, Icons.Filled.Person, active, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    tab: DsTab,
    label: String,
    icon: ImageVector,
    activeIcon: ImageVector,
    active: DsTab?,
    onSelect: (DsTab) -> Unit,
    modifier: Modifier,
) {
    val isActive = tab == active
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClickLabel = label,
            ) { onSelect(tab) }
            .semantics { contentDescription = if (isActive) "$label, selected" else label },
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            Box(
                Modifier
                    .size(DsSize.navActive)
                    .shadow(14.dp, CircleShape, ambientColor = DsColor.accentGlow, spotColor = DsColor.accent)
                    .clip(CircleShape)
                    .background(DsColor.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(activeIcon, null, tint = DsColor.onAccent, modifier = Modifier.size(26.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, null, tint = DsColor.textMuted, modifier = Modifier.size(24.dp))
                Text(label, style = DsType.navLabel, color = DsColor.textMuted)
            }
        }
    }
}

/** Spacer that equals the status bar plus [extra]. */
@Composable
fun StatusBarSpacer(extra: Dp = 0.dp) {
    Spacer(Modifier.statusBarsPadding().height(extra))
}

/** Back affordance for overlay headers. */
@Composable
fun BackFloatingButton(onBack: () -> Unit, modifier: Modifier = Modifier, tone: FloatingTone = FloatingTone.OnPhoto) {
    FloatingButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, modifier, tone = tone)
}

