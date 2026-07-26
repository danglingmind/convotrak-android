package com.danglingmind.convotrack

import android.app.Application
import com.danglingmind.convotrack.di.ServiceLocator

// Application entry point. Mirrors iOS ConvoTrackApp.swift: initialise Clerk here.
// Google Maps init is still deferred until the maps phase.
class ConvoTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
