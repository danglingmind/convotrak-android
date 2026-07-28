package com.danglingmind.convotrack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.danglingmind.convotrack.di.ServiceLocator
import com.danglingmind.convotrack.ui.auth.AuthScreen
import com.danglingmind.convotrack.ui.auth.VerificationScreen
import com.danglingmind.convotrack.ui.main.MainScreen
import java.net.URLEncoder
import java.net.URLDecoder
import com.danglingmind.convotrack.ui.membership.MembershipScreen
import com.danglingmind.convotrack.ui.ride.CreateRideScreen
import com.danglingmind.convotrack.ui.ride.RideLobbyScreen
import com.danglingmind.convotrack.ui.ride.RideNavigationScreen
import com.danglingmind.convotrack.ui.ride.RideSummaryScreen
import com.danglingmind.convotrack.ui.splash.SplashScreen
import com.danglingmind.convotrack.ui.theme.CT

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
    const val MEMBERSHIP = "membership"

    // The ride flow carries the rideId through the URL so each screen can load its own data.
    fun lobby(rideId: String) = "$RIDE_LOBBY/$rideId"
    fun navigation(rideId: String) = "$RIDE_NAVIGATION/$rideId"
    fun summary(rideId: String) = "$RIDE_SUMMARY/$rideId"
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize(), color = CT.surfaceDim) {
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
                        nav.navigate(if (signedIn) Routes.MAIN else Routes.AUTH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                })
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    onSignedIn = {
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    },
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
                    onVerified = {
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.MAIN) {
                MainScreen(
                    onCreateRide = { nav.navigate(Routes.CREATE_RIDE) },
                    onOpenLobby = { rideId -> nav.navigate(Routes.lobby(rideId)) },
                    onOpenSummary = { rideId -> nav.navigate(Routes.summary(rideId)) },
                    onOpenMembership = { nav.navigate(Routes.MEMBERSHIP) },
                    onSignOut = {
                        ServiceLocator.socket.disconnect()
                        nav.navigate(Routes.AUTH) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.CREATE_RIDE) {
                CreateRideScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { rideId ->
                        nav.navigate(Routes.lobby(rideId)) {
                            popUpTo(Routes.CREATE_RIDE) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "${Routes.RIDE_LOBBY}/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) { entry ->
                val rideId = entry.arguments?.getString("rideId").orEmpty()
                RideLobbyScreen(
                    rideId = rideId,
                    // Leaving the lobby back to the tabs means leaving the ride — drop the socket.
                    onBack = {
                        ServiceLocator.socket.disconnect()
                        nav.popBackStack()
                    },
                    onStartRide = { nav.navigate(Routes.navigation(rideId)) },
                )
            }
            composable(
                route = "${Routes.RIDE_NAVIGATION}/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) { entry ->
                val rideId = entry.arguments?.getString("rideId").orEmpty()
                RideNavigationScreen(
                    rideId = rideId,
                    // Leaving navigation only pops this screen — the ride stays running
                    // and can be resumed from the lobby / active-ride card.
                    onExit = { nav.popBackStack() },
                    onEndRide = {
                        nav.navigate(Routes.summary(rideId)) {
                            popUpTo(Routes.MAIN)
                        }
                    },
                )
            }
            composable(
                route = "${Routes.RIDE_SUMMARY}/{rideId}",
                arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
            ) { entry ->
                val rideId = entry.arguments?.getString("rideId").orEmpty()
                RideSummaryScreen(
                    rideId = rideId,
                    onDone = {
                        // The ride is over — tear down the realtime connection.
                        ServiceLocator.socket.disconnect()
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MEMBERSHIP) {
                MembershipScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}
