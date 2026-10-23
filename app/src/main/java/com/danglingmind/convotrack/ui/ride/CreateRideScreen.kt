package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.data.analytics.Analytics

import com.danglingmind.convotrack.ui.designsystem.GoProButton
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.data.maps.DirectionsService
import com.danglingmind.convotrack.data.net.CreateRideRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.data.net.isPlanLimit
import com.danglingmind.convotrack.data.net.riderMessage
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.PaywallSource
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.common.rememberRiderCap
import com.danglingmind.convotrack.ui.designsystem.DsColor
import com.danglingmind.convotrack.ui.designsystem.DsScreen
import com.danglingmind.convotrack.ui.designsystem.DsSpace
import com.danglingmind.convotrack.ui.designsystem.InlineError
import com.danglingmind.convotrack.ui.designsystem.PhotoBackdrop
import com.danglingmind.convotrack.ui.designsystem.PrimaryButton
import com.danglingmind.convotrack.ui.designsystem.PushedHeader
import kotlinx.coroutines.launch

@Composable
fun CreateRideScreen(
    onBack: () -> Unit,
    onCreated: (rideId: String) -> Unit,
    onOpenMembership: () -> Unit = {},
) {
    val api = remember { ServiceLocator.api }
    val scope = rememberCoroutineScope()
    val form = remember { RouteFormState() }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** The last create was refused for a plan reason (trial over / monthly rides used up). */
    var blockedByPlan by remember { mutableStateOf(false) }
    val canCreate = form.title.isNotBlank() && form.allResolved && form.preview != null && !creating
    val riderCap = rememberRiderCap()
    // Never start a new ride above the plan's limit (the server would silently trim it).
    LaunchedEffect(riderCap.cap) { if (form.maxRiders > riderCap.cap) form.maxRiders = riderCap.cap }

    RideFormScaffold(title = "New ride", onBack = onBack) {
        RouteFormSections(form, riderCap, onUpgrade = onOpenMembership)

        if (error != null) InlineError(error!!)
        if (blockedByPlan) {
            GoProButton(
                "Go Pro",
                onClick = { Paywall.present(PaywallSource.CreateBlocked) },
                icon = Icons.Filled.WorkspacePremium,
            )
        }

        PrimaryButton(
            text = "Create ride",
            icon = Icons.Filled.Directions,
            enabled = canCreate,
            loading = creating,
            onClick = {
                val p = form.preview ?: return@PrimaryButton
                creating = true
                error = null
                scope.launch {
                    try {
                        val dest = form.destStop.place!!
                        val req = CreateRideRequest(
                            title = form.title.trim(),
                            destinationName = dest.name,
                            destinationLat = dest.latLng.latitude,
                            destinationLng = dest.latLng.longitude,
                            distanceMeters = p.distanceMeters,
                            estimatedDurationSeconds = p.durationSeconds.toInt(),
                            maxAllowedParticipants = form.maxRiders,
                            routePolyline = DirectionsService.encodePolyline(p.coords),
                            waypoints = form.waypoints(),
                        )
                        val resp = apiCall { api.createRide(req) }
                        onCreated(resp.rideId)
                    } catch (e: Exception) {
                        Analytics.capture(Analytics.Event.CreateRideFailed, mapOf("plan_limit" to e.isPlanLimit))
                        error = e.riderMessage
                        blockedByPlan = e.isPlanLimit
                        creating = false
                    }
                }
            },
        )
    }
}

/**
 * Create / Edit Ride frame: the road photo faded behind a pinned header (back · title · connection),
 * and a scrolling form. The header picks up a solid background once content scrolls under it.
 */
@Composable
internal fun RideFormScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String = "Plan your route",
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    DsScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding(),
        ) {
            Box {
                PhotoBackdrop(height = 200.dp, drawable = R.drawable.hero_road, verticalBias = 0.42f, alpha = 0.55f)
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(Modifier.height(72.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = DsSpace.gutter)
                            .padding(top = 8.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(DsSpace.sectionGap),
                        content = content,
                    )
                }
            }
        }
        val headerAlpha = (scroll.value / 160f).coerceIn(0f, 1f)
        PushedHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            modifier = Modifier.drawBehind { drawRect(DsColor.bgBase.copy(alpha = 0.98f * headerAlpha)) },
        )
    }
}
