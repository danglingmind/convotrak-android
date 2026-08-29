package com.danglingmind.convotrack.di

import android.app.Application
import com.clerk.api.Clerk
import com.danglingmind.convotrack.data.auth.AuthRepository
import com.danglingmind.convotrack.data.auth.ClerkAuthRepository
import com.danglingmind.convotrack.data.billing.BillingManager
import com.danglingmind.convotrack.data.location.LocationController
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.ApiService
import com.danglingmind.convotrack.data.socket.SocketService

/**
 * Tiny manual DI. Holds the app-wide auth + networking singletons so composables can reach
 * them without a full DI framework. Populated once from [com.danglingmind.convotrack.ConvoTrackApp].
 */
object ServiceLocator {

    // Same Clerk instance the iOS app uses (ConvoTrackApp.swift).
    private const val CLERK_PUBLISHABLE_KEY = "pk_live_Y2xlcmsuY29udm95LnZ5bmwuaW4k"

    lateinit var auth: AuthRepository
        private set

    lateinit var api: ApiService
        private set

    lateinit var socket: SocketService
        private set

    val location: LocationController = LocationController()

    lateinit var billing: BillingManager
        private set

    fun init(app: Application) {
        Clerk.initialize(app, CLERK_PUBLISHABLE_KEY)
        auth = ClerkAuthRepository()
        api = ApiClient.create(auth)
        socket = SocketService(auth)
        billing = BillingManager(app, api)
    }
}
