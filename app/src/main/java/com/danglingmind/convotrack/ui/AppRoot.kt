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
                    onOpenLobby = { nav.navigate(Routes.RIDE_LOBBY) },
                    onOpenSummary = { nav.navigate(Routes.RIDE_SUMMARY) },
                    onOpenMembership = { nav.navigate(Routes.MEMBERSHIP) },
                )
            }
            composable(Routes.CREATE_RIDE) {
                CreateRideScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { nav.navigate(Routes.RIDE_LOBBY) },
                )
            }
            composable(Routes.RIDE_LOBBY) {
                RideLobbyScreen(
                    onBack = { nav.popBackStack() },
                    onStartRide = { nav.navigate(Routes.RIDE_NAVIGATION) },
                )
            }
            composable(Routes.RIDE_NAVIGATION) {
                RideNavigationScreen(
                    // Leaving navigation only pops this screen — the ride stays running
                    // and can be resumed from the lobby / active-ride card.
                    onExit = { nav.popBackStack() },
                    onEndRide = {
                        nav.navigate(Routes.RIDE_SUMMARY) {
                            popUpTo(Routes.MAIN)
                        }
                    },
                )
            }
            composable(Routes.RIDE_SUMMARY) {
                RideSummaryScreen(onDone = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.MEMBERSHIP) {
                MembershipScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}
