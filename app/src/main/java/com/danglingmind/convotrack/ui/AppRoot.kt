package com.danglingmind.convotrack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.danglingmind.convotrack.ui.auth.AuthScreen
import com.danglingmind.convotrack.ui.main.MainScreen
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
    const val MAIN = "main"
    const val CREATE_RIDE = "create_ride"
    const val RIDE_LOBBY = "ride_lobby"
    const val RIDE_NAVIGATION = "ride_navigation"
    const val RIDE_SUMMARY = "ride_summary"
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize(), color = CT.surfaceDim) {
        NavHost(navController = nav, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                SplashScreen(onFinished = {
                    nav.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.AUTH) {
                AuthScreen(onSignedIn = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                })
            }
            composable(Routes.MAIN) {
                MainScreen(
                    onCreateRide = { nav.navigate(Routes.CREATE_RIDE) },
                    onOpenLobby = { nav.navigate(Routes.RIDE_LOBBY) },
                    onOpenSummary = { nav.navigate(Routes.RIDE_SUMMARY) },
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
        }
    }
}
