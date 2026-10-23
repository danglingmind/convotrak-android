package com.danglingmind.convotrack

import com.danglingmind.convotrack.data.imagecache.RideDestinationStore
import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.danglingmind.convotrack.data.analytics.Analytics
import com.danglingmind.convotrack.data.audio.VoiceAnnouncer
import com.danglingmind.convotrack.data.imagecache.ImageCache
import com.danglingmind.convotrack.di.ServiceLocator

// Application entry point. Mirrors iOS ConvoTrackApp.swift: initialise Clerk here.
// Google Maps init is still deferred until the maps phase.
class ConvoTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // TTS init is async and slow on a cold start, so it is warmed here rather than on the
        // first announcement — which is a mid-ride reroute cue that must not be delayed.
        VoiceAnnouncer.init(this)
        // Gives the image cache an app context and registers its memory-pressure listener.
        ImageCache.init(this)
        RideDestinationStore.init(this)
        Analytics.start(this)

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
