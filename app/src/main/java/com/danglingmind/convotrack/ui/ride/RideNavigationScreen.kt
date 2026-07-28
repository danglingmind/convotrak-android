package com.danglingmind.convotrack.ui.ride

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.data.net.LiveParticipant
import com.danglingmind.convotrack.data.net.RegroupEvent
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.map.ConvoTrackMap
import com.danglingmind.convotrack.ui.map.MapMarker
import com.danglingmind.convotrack.ui.map.MapMarkerKind
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.FloatingStatPill
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideNavigationScreen(
    rideId: String,
    onExit: () -> Unit,
    onEndRide: () -> Unit,
) {
    val api = remember { ServiceLocator.api }
    val auth = remember { ServiceLocator.auth }
    val socket = remember { ServiceLocator.socket }
    val locationController = remember { ServiceLocator.location }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = auth.userState.value?.id

    var ride by remember { mutableStateOf<Ride?>(null) }
    var routeCoords by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val live: SnapshotStateMap<String, LiveParticipant> = remember { mutableStateMapOf() }
    var activeRegroup by remember { mutableStateOf<RegroupEvent?>(null) }
    var isEmergency by remember { mutableStateOf(false) }
    val myLoc by locationController.current.collectAsState()

    var showRegroup by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Location permission -> start broadcasting my position (foreground service).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationController.start(ctx, rideId)
        }
    }
    LaunchedEffect(rideId) {
        val fineGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            locationController.start(ctx, rideId)
        } else {
            permLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { locationController.stop(ctx) }
    }

    // Load the ride (route + roster), then go live over the socket.
    LaunchedEffect(rideId) {
        ride = runCatching { apiCall { api.getRide(rideId) } }.getOrNull()
        ride?.let { routeCoords = computeRoute(it.waypoints) }
        socket.connect()
        socket.joinRoom(rideId)
        launch {
            socket.stateUpdates.collect { update ->
                update.participants.forEach { p -> live[p.userId] = p }
            }
        }
        launch { socket.regroupStarted.collect { activeRegroup = it; isEmergency = false } }
        launch { socket.emergencyStarted.collect { isEmergency = true } }
        launch { socket.regroupResolved.collect { id -> if (activeRegroup?.regroupId == id) activeRegroup = null } }
    }

    BackHandler { onExit() }

    val namesById = remember(ride) { ride?.participants?.associate { it.userId to it.name } ?: emptyMap() }
    // My own speed comes from GPS directly (m/s -> km/h); fall back to the socket echo.
    val mySpeed = myLoc?.takeIf { it.hasSpeed() }?.speed?.toDouble()?.times(3.6)
        ?: myId?.let { live[it]?.speed?.times(3.6) }

    Box(Modifier.fillMaxSize().background(CT.surfaceDim)) {
        val myLatLng = myLoc?.let { LatLng(it.latitude, it.longitude) }

        // Real map: route + destination/waypoint markers + other riders (socket) + me (GPS).
        ConvoTrackMap(
            modifier = Modifier.fillMaxSize(),
            routeCoords = routeCoords,
            markers = buildList {
                ride?.let { addAll(rideMarkers(it)) }
                live.values.filter { it.userId != myId }.forEach { p ->
                    val lat = p.lat; val lng = p.lng
                    if (lat != null && lng != null) {
                        add(MapMarker("rider_${p.userId}", LatLng(lat, lng), MapMarkerKind.RIDER, namesById[p.userId]))
                    }
                }
                myLatLng?.let { add(MapMarker("me", it, MapMarkerKind.USER, "You")) }
            },
            interactive = true,
            follow = myLatLng,
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.9f))
                        .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), CircleShape).clickable(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave navigation (ride keeps running)",
                        tint = CT.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text("TO", style = CTType.labelCaps.copy(fontSize = 9.sp), color = CT.onSurfaceVariant, letterSpacing = 1.5.sp)
                    Text(ride?.destinationName ?: "Loading…", style = CTType.bodyLg, color = CT.onSurface)
                }
            }

            // HUD stat pills
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FloatingStatPill(
                    label = "SPEED",
                    value = mySpeed?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--",
                    unit = "KM/H",
                    dotColor = CT.primaryFixed,
                )
                FloatingStatPill(label = "RIDERS", value = "${maxOf(live.size, ride?.participants?.size ?: 0)}", unit = null)
                FloatingStatPill(
                    label = "LIVE",
                    value = if (socket.connected.value) "ON" else "…",
                    unit = null,
                    dotColor = CT.tertiaryFixed,
                )
            }

            // Regroup / emergency banner
            if (activeRegroup != null || isEmergency) {
                val accent = if (isEmergency) CT.errorColor else CT.primaryFixed
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isEmergency) CT.errorContainer.copy(alpha = 0.2f) else CT.primaryFixed.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (isEmergency) Icons.Filled.Warning else Icons.Filled.Groups, null, tint = accent, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isEmergency) "EMERGENCY" else "REGROUP ACTIVE", style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
                        Text(activeRegroup?.type ?: "Group is regrouping", style = CTType.bodyMd, color = CT.onSurface)
                    }
                    if (activeRegroup != null) {
                        Text(
                            "ARRIVED",
                            style = CTType.labelCaps,
                            color = CT.onPrimaryFixed,
                            letterSpacing = 1.sp,
                            modifier = Modifier.clip(CircleShape).background(CT.primaryFixed)
                                .clickable {
                                    activeRegroup?.let { socket.emitRegroupArrived(rideId, it.regroupId) }
                                    activeRegroup = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Bottom overlay: live rider strip + controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (live.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(live.values.toList(), key = { it.userId }) { p ->
                        RiderChip(name = namesById[p.userId] ?: "Rider", speed = p.speed, isMe = p.userId == myId)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                ControlButton(
                    icon = Icons.Filled.Groups,
                    description = "Regroup",
                    tint = CT.onSurface,
                    background = CT.surfaceContainerHigh.copy(alpha = 0.9f),
                    border = CT.outlineVariant.copy(alpha = 0.3f),
                    onClick = { showRegroup = true },
                )
                ControlButton(
                    icon = Icons.Filled.Stop,
                    description = "End ride",
                    tint = CT.errorColor,
                    background = CT.errorContainer.copy(alpha = 0.12f),
                    border = CT.errorColor.copy(alpha = 0.35f),
                    onClick = onEndRide,
                )
            }
        }
    }

    if (showRegroup) {
        ModalBottomSheet(
            onDismissRequest = { showRegroup = false },
            sheetState = sheetState,
            containerColor = CT.surfaceDim,
        ) {
            RegroupSheet(onBroadcast = { reason ->
                showRegroup = false
                // Broadcast at my current position. Emergency emits ride:emergency;
                // the others emit ride:regroup (per CLAUDE.md architectural decision).
                val lat = myLoc?.latitude ?: 0.0
                val lng = myLoc?.longitude ?: 0.0
                if (reason.isEmergency) {
                    socket.emitEmergency(rideId, lat, lng, "Emergency")
                } else {
                    val type = reason.name // FUEL / FOOD / SCENIC
                    scope.launch { socket.emitRegroup(rideId, type, lat, lng) }
                }
            })
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    background: Color,
    border: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RiderChip(name: String, speed: Double?, isMe: Boolean) {
    Row(
        Modifier.clip(CircleShape).background(CT.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, if (isMe) CT.primaryFixed else CT.outline.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(CT.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null, tint = if (isMe) CT.primaryFixed else CT.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(name, style = CTType.labelCaps, color = CT.onSurface)
            Text(
                "${speed?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--"} KM/H",
                style = CTType.dataMono.copy(fontSize = 11.sp),
                color = CT.onSurfaceVariant,
            )
        }
    }
}
