package com.danglingmind.convotrack.ui

import com.danglingmind.convotrack.ui.designsystem.DsColor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.danglingmind.convotrack.ui.designsystem.DsTab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.danglingmind.convotrack.ui.ride.RideSessionViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.danglingmind.convotrack.data.analytics.Analytics
import com.danglingmind.convotrack.data.net.SyncProfileRequest
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.auth.AuthScreen
import com.danglingmind.convotrack.ui.auth.VerificationScreen
import com.danglingmind.convotrack.ui.main.MainScreen
import java.net.URLEncoder
import java.net.URLDecoder
import com.danglingmind.convotrack.ui.membership.MembershipScreen
import com.danglingmind.convotrack.ui.ride.CreateRideScreen
import com.danglingmind.convotrack.ui.ride.SharedRoutePreviewScreen
import com.danglingmind.convotrack.ui.ride.EditRideScreen
import com.danglingmind.convotrack.ui.ride.RideLobbyScreen
import com.danglingmind.convotrack.ui.ride.RideNavigationScreen
import com.danglingmind.convotrack.ui.ride.RideSummaryScreen
import com.danglingmind.convotrack.ui.splash.SplashScreen
import com.danglingmind.convotrack.ui.common.Paywall
import com.danglingmind.convotrack.ui.common.PaywallSource

// Top-level navigation. Mirrors the iOS launch flow:
// Splash (3.5s) -> Auth -> MainTab, with the ride flow pushed on top.
object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val VERIFY = "verify"
    const val MAIN = "main"
    const val CREATE_RIDE = "create_ride"
    const val RIDE_LOBBY = "ride_lobby"
    const val RIDE_NAVIGATION = "ride_navigation"
    const val RIDE_SUMMARY = "ride_summary"
    const val EDIT_RIDE = "edit_ride"
    const val SHARED_ROUTE = "shared_route"
    const val MEMBERSHIP = "membership"
    /** The one-time paywall after first sign-in; leads to MAIN either way. */
    const val MEMBERSHIP_ONBOARDING = "membership_onboarding"
    fun membership(source: PaywallSource) = "$MEMBERSHIP?source=${source.name}"

    // The whole ride flow lives inside one nested graph so the lobby and the navigation screen
    // share a single RideSessionViewModel (and therefore one socket session + roster) that
    // survives the push between them. Entering the flow always enters at the lobby.
    const val RIDE_GRAPH = "ride"
    fun rideGraph(rideId: String) = "$RIDE_GRAPH/$rideId"

    // The ride flow carries the rideId through the URL so each screen can load its own data.
    fun lobby(rideId: String) = "$RIDE_LOBBY/$rideId"
    fun navigation(rideId: String) = "$RIDE_NAVIGATION/$rideId"
    fun summary(rideId: String) = "$RIDE_SUMMARY/$rideId"
    fun edit(rideId: String) = "$EDIT_RIDE/$rideId"
    fun sharedRoute(token: String) = "$SHARED_ROUTE/$token"
}

@Composable
fun AppRoot(
    deepLinkCode: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    openRideId: String? = null,
    onOpenRideConsumed: () -> Unit = {},
    pushTap: Pair<String, String>? = null,
    onPushTapConsumed: () -> Unit = {},
    routeToken: String? = null,
    onRouteTokenConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    // Tap on the ride-status notification: jump into that ride's live navigation, but only when it
    // is the ride this device is currently broadcasting for. Anything else (stale notification,
    // unknown id, ride already over) is ignored. Waits while splash/auth are still on screen.
    val backStack by nav.currentBackStackEntryAsState()

    // Pushes the rider's Clerk name + avatar to the backend whenever it changes (iOS MainTabView does
    // the same on launch). Android session tokens carry no firstName/lastName claims, so without this
    // the server only ever knows the rider as "Unknown" in lobbies, rosters and live broadcasts.
    LaunchedEffect(Unit) {
        ServiceLocator.auth.userState
            .filterNotNull()
            .distinctUntilChanged()
            .collect { user ->
                Analytics.identify(user.id)
                runCatching { ServiceLocator.api.syncProfile(SyncProfileRequest(user.name, user.avatarUrl)) }
            }
    }

    // The one paywall entry point (iOS `PaywallPresenter`): any screen calls `Paywall.present`.
    // A second request while the paywall is already up is dropped rather than stacking another.
    LaunchedEffect(Unit) {
        Paywall.requests.collect { source ->
            if (nav.currentDestination?.route?.startsWith(Routes.MEMBERSHIP) != true) {
                nav.navigate(Routes.membership(source))
            }
        }
    }
    val pushContext = androidx.compose.ui.platform.LocalContext.current
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) com.danglingmind.convotrack.data.push.Push.register(pushContext) }

    // Push: register the token at every app open while signed in; unregistered at sign-out.
    LaunchedEffect(Unit) {
        ServiceLocator.auth.userState.filterNotNull().distinctUntilChanged().collect {
            com.danglingmind.convotrack.data.push.Push.register(pushContext)
        }
    }
    // Which ride screen is showing, so a push for that ride is not shown on top of it.
    LaunchedEffect(backStack) {
        val route = backStack?.destination?.route.orEmpty()
        val onRide = listOf(Routes.RIDE_LOBBY, Routes.RIDE_NAVIGATION, Routes.RIDE_SUMMARY).any { route.startsWith(it) }
        com.danglingmind.convotrack.data.push.Push.foregroundRideId =
            if (onRide) backStack?.arguments?.getString("rideId") else null
        // Permission is asked once, right after the rider first joins or creates a ride.
        if (route.startsWith(Routes.RIDE_LOBBY)) {
            if (com.danglingmind.convotrack.data.push.Push.shouldAskPermission(pushContext)) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                com.danglingmind.convotrack.data.push.Push.register(pushContext)
            }
        }
    }
    // Ride a push tap opened: its lobby moves on to navigation (or the summary) once the ride's
    // state is known, instead of leaving the rider on the lobby screen.
    var pushResumeRideId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // Push tap: RIDE_STARTED -> the ride, RIDE_ENDED -> its summary.
    LaunchedEffect(pushTap, backStack) {
        val (type, id) = pushTap ?: return@LaunchedEffect
        val route = backStack?.destination?.route ?: return@LaunchedEffect
        if (route == Routes.SPLASH || route == Routes.AUTH || route.startsWith(Routes.VERIFY)) return@LaunchedEffect
        when (type) {
            "RIDE_ENDED" -> nav.navigate(Routes.summary(id))
            "RIDE_STARTED" -> {
                pushResumeRideId = id
                nav.navigate(Routes.rideGraph(id))
                if (ServiceLocator.location.activeRideId.value == id) nav.navigate(Routes.navigation(id))
            }
            "REGROUP_CALLED", "EMERGENCY" -> {
                // Same as RIDE_STARTED: the ride screen only while it is still active here.
                if (!(route == "${Routes.RIDE_NAVIGATION}/{rideId}" && backStack?.arguments?.getString("rideId") == id)) {
                    pushResumeRideId = id
                    nav.navigate(Routes.rideGraph(id))
                    if (ServiceLocator.location.activeRideId.value == id) nav.navigate(Routes.navigation(id))
                }
            }
        }
        onPushTapConsumed()
    }
    LaunchedEffect(openRideId, backStack) {
        val id = openRideId ?: return@LaunchedEffect
        val route = backStack?.destination?.route ?: return@LaunchedEffect
        when {
            route == Routes.SPLASH || route == Routes.AUTH || route.startsWith(Routes.VERIFY) -> Unit
            route == "${Routes.RIDE_NAVIGATION}/{rideId}" &&
                backStack?.arguments?.getString("rideId") == id -> onOpenRideConsumed()
            route == Routes.MAIN || route == "${Routes.RIDE_LOBBY}/{rideId}" -> {
                if (ServiceLocator.location.activeRideId.value == id) {
                    if (route == Routes.MAIN) nav.navigate(Routes.rideGraph(id))
                    nav.navigate(Routes.navigation(id))
                }
                onOpenRideConsumed()
            }
            else -> onOpenRideConsumed()
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = DsColor.bgBase) {
        NavHost(navController = nav, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                val scope = rememberCoroutineScope()
                SplashScreen(onFinished = {
                    // Per Clerk's docs, gate routing on isInitialized before reading the
                    // session — otherwise a slow bootstrap races the 3.5s splash and we'd
                    // wrongly route a signed-in user to Auth. Bounded so we never hang.
                    scope.launch {
                        withTimeoutOrNull(4000) { ServiceLocator.auth.isReady.first { it } }
                        val signedIn = ServiceLocator.auth.userState.value != null
                        // Already signed in (includes everyone who installed before this paywall
                        // existed): never show them the first-sign-in paywall (iOS parity).
                        if (signedIn) ServiceLocator.upsell.hasSeenOnboardingPaywall = true
                        nav.navigate(if (signedIn) Routes.MAIN else Routes.AUTH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                })
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    onSignedIn = { nav.afterSignIn() },
                    onNeedsVerification = { email ->
                        val arg = URLEncoder.encode(email, "UTF-8")
                        nav.navigate("${Routes.VERIFY}/$arg")
                    },
                )
            }
            composable(
                route = "${Routes.VERIFY}/{email}",
                arguments = listOf(navArgument("email") { type = NavType.StringType }),
            ) { backStackEntry ->
                val email = URLDecoder.decode(
                    backStackEntry.arguments?.getString("email").orEmpty(), "UTF-8",
                )
                VerificationScreen(
                    email = email,
                    onVerified = { nav.afterSignIn() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.MAIN) { entry ->
                val requestedTab by entry.savedStateHandle.getStateFlow<String?>("tab", null).collectAsState()
                MainScreen(
                    requestedTab = requestedTab?.let { name -> DsTab.entries.firstOrNull { it.name == name } },
                    onRequestedTabConsumed = { entry.savedStateHandle["tab"] = null },
                    initialInviteCode = deepLinkCode,
                    onInviteConsumed = onDeepLinkConsumed,
                    initialRouteToken = routeToken,
                    onRouteTokenConsumed = onRouteTokenConsumed,
                    onOpenSharedRoute = { token -> nav.navigate(Routes.sharedRoute(token)) },
                    onCreateRide = { nav.navigate(Routes.CREATE_RIDE) },
                    onOpenLobby = { rideId -> nav.navigate(Routes.rideGraph(rideId)) },
                    onOpenSummary = { rideId -> nav.navigate(Routes.summary(rideId)) },
                    onOpenMembership = { Paywall.present(PaywallSource.Profile) },
                    onSignOut = {
                        ServiceLocator.socket.disconnect()
                        nav.navigate(Routes.AUTH) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "${Routes.SHARED_ROUTE}/{token}",
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
            ) { entry ->
                SharedRoutePreviewScreen(
                    token = entry.arguments?.getString("token").orEmpty(),
                    onClose = { nav.popBackStack() },
                    // The preview is replaced, not stacked under the planner: back from Create Ride
                    // returns to the tabs.
                    onRide = {
                        nav.navigate(Routes.CREATE_RIDE) { popUpTo("${Routes.SHARED_ROUTE}/{token}") { inclusive = true } }
                    },
                )
            }
            composable(Routes.CREATE_RIDE) {
                CreateRideScreen(
                    onOpenMembership = { Paywall.present(PaywallSource.RiderCap) },
                    onBack = { nav.popBackStack() },
                    onCreated = { rideId ->
                        nav.navigate(Routes.rideGraph(rideId)) {
                            popUpTo(Routes.CREATE_RIDE) { inclusive = true }
                        }
                    },
                )
            }
            // The ride flow: lobby → navigation → (edit). All three share the graph-scoped
            // RideSessionViewModel, so the socket session, roster and presence survive the
            // lobby→navigation push instead of being rebuilt per screen.
            navigation(
                startDestination = "${Routes.RIDE_LOBBY}/{rideId}",
                route = "${Routes.RIDE_GRAPH}/{rideId}",
            ) {
                composable(
                    route = "${Routes.RIDE_LOBBY}/{rideId}",
                    arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
                ) { entry ->
                    val rideId = entry.arguments?.getString("rideId").orEmpty()
                    val sessionVm = rideSessionViewModel(nav, entry, rideId)
                    RideLobbyScreen(
                        rideId = rideId,
                        sessionVm = sessionVm,
                        // Backing out of the lobby leaves the ride flow entirely, so the shared
                        // session is torn down here rather than in each screen.
                        onBack = {
                            sessionVm.endSession()
                            nav.popBackStack()
                        },
                        onStartRide = { nav.navigate(Routes.navigation(rideId)) },
                        autoResume = pushResumeRideId == rideId,
                        onAutoResumeHandled = { pushResumeRideId = null },
                        onRideEnded = { nav.navigate(Routes.summary(rideId)) },
                        onEdit = { editRideId -> nav.navigate(Routes.edit(editRideId)) },
                        // The bottom nav stays visible in the lobby; picking a tab leaves the ride
                        // flow (like back) and opens that tab.
                        onTab = { tab ->
                            sessionVm.endSession()
                            runCatching { nav.getBackStackEntry(Routes.MAIN).savedStateHandle["tab"] = tab.name }
                            nav.popBackStack(Routes.MAIN, inclusive = false)
                        },
                    )
                }
                composable(
                    route = "${Routes.RIDE_NAVIGATION}/{rideId}",
                    arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
                ) { entry ->
                    val rideId = entry.arguments?.getString("rideId").orEmpty()
                    val sessionVm = rideSessionViewModel(nav, entry, rideId)
                    RideNavigationScreen(
                        rideId = rideId,
                        sessionVm = sessionVm,
                        // Leaving navigation pops back to the lobby, which holds on ACTIVE and
                        // shows RESUME NAVIGATION (iOS parity). The ride keeps running, so the
                        // session is deliberately NOT stopped here.
                        onExit = { nav.popBackStack() },
                        onEndRide = {
                            // The ride is over — drop the realtime connection before the summary.
                            sessionVm.endSession()
                            // A finished ride is the free-plan nudge's best moment (`UpsellPolicy`).
                            ServiceLocator.upsell.recordRideCompleted()
                            nav.navigate(Routes.summary(rideId)) {
                                popUpTo(Routes.MAIN)
                            }
                        },
                    )
                }
                composable(
                    route = "${Routes.EDIT_RIDE}/{rideId}",
                    arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
                ) { entry ->
                    val rideId = entry.arguments?.getString("rideId").orEmpty()
                    val sessionVm = rideSessionViewModel(nav, entry, rideId)
                    EditRideScreen(
                        rideId = rideId,
                        onOpenMembership = { Paywall.present(PaywallSource.RiderCap) },
                        onBack = { nav.popBackStack() },
                        onSaved = {
                            sessionVm.refreshRide()
                            nav.popBackStack()
                        },
                        onDeleted = {
                            // The ride no longer exists — tear the session down and leave the
                            // flow rather than popping back to a lobby for a deleted ride.
                            sessionVm.endSession()
                            nav.navigate(Routes.MAIN) {
                                popUpTo(Routes.MAIN) { inclusive = true }
                            }
                        },
                    )
                }
            }
            // Summary sits OUTSIDE the ride graph: by the time it shows, the ride is over and the
            // session has been stopped, so keeping the graph (and its ViewModel) alive would only
            // hold a dead session open.
            composable(
                route = "${Routes.RIDE_SUMMARY}/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) { entry ->
                val rideId = entry.arguments?.getString("rideId").orEmpty()
                RideSummaryScreen(
                    rideId = rideId,
                    onDone = {
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MEMBERSHIP_ONBOARDING) {
                MembershipScreen(
                    onboarding = true,
                    onClose = {
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.MEMBERSHIP_ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "${Routes.MEMBERSHIP}?source={source}",
                arguments = listOf(navArgument("source") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                MembershipScreen(
                    source = PaywallSource.fromRoute(entry.arguments?.getString("source")),
                    onClose = { nav.popBackStack() },
                )
            }
        }
    }
}

/**
 * Sign-in succeeded. First-time riders who aren't Pro see the paywall once (with their trial
 * starting); everyone else goes straight in (iOS `ContentView.authenticated`).
 */
private fun NavHostController.afterSignIn() {
    val upsell = ServiceLocator.upsell
    val showPaywall = !upsell.hasSeenOnboardingPaywall && !ServiceLocator.billing.isPremium.value
    upsell.hasSeenOnboardingPaywall = true
    if (showPaywall) {
        upsell.recordOnboardingPaywall()
        navigate(Routes.MEMBERSHIP_ONBOARDING) { popUpTo(Routes.AUTH) { inclusive = true } }
    } else {
        navigate(Routes.MAIN) { popUpTo(Routes.AUTH) { inclusive = true } }
    }
}

/**
 * The [RideSessionViewModel] for the ride flow this [entry] belongs to.
 *
 * Resolved from the nested graph's back-stack entry rather than the screen's own, which is what
 * makes the lobby and the navigation screen share one instance — and therefore one socket session,
 * one roster, one heartbeat — across the push between them.
 */
@Composable
private fun rideSessionViewModel(
    nav: NavHostController,
    entry: NavBackStackEntry,
    rideId: String,
): RideSessionViewModel {
    val parentEntry = remember(entry) { nav.getBackStackEntry(Routes.rideGraph(rideId)) }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = RideSessionViewModel.factory(rideId),
    )
}
