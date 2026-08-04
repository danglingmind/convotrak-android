package com.danglingmind.convotrack

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.danglingmind.convotrack.di.ServiceLocator

// Application entry point. Mirrors iOS ConvoTrackApp.swift: initialise Clerk here.
// Google Maps init is still deferred until the maps phase.
class ConvoTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // App-level presence: when the app goes to the background, drop the socket so other
        // riders see us go offline promptly (a backgrounded Android process otherwise keeps the
        // socket alive for minutes). An active ride keeps its connection via the foreground
        // location service, so we only suspend when no ride is running. Foregrounding restores
        // the connection and rejoins the room.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!ServiceLocator.location.active.value) {
                    ServiceLocator.socket.suspendConnection()
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                ServiceLocator.socket.resumeConnection()
            }
        })
    }
}
