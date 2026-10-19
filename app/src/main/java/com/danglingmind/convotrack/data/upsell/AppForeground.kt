package com.danglingmind.convotrack.data.upsell

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide foreground tracking for the upsell cadence. Observed on the *process* lifecycle,
 * not a screen's: `MainScreen` leaves the composition whenever a screen is pushed over it, and a
 * screen-level observer would count every return from Create Ride as an "app open".
 */
object AppForeground {
    private val _foregrounds = MutableStateFlow(0)
    /** Bumps on every foreground; screens key their refresh on it. */
    val foregrounds: StateFlow<Int> = _foregrounds

    private var pendingAppOpen = false

    fun start(state: UpsellState) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (state.recordForeground()) pendingAppOpen = true
                _foregrounds.value += 1
            }

            override fun onStop(owner: LifecycleOwner) = state.recordBackground()
        })
    }

    /** True once per counted app open — the first screen able to show the nudge takes it. */
    fun consumeAppOpen(): Boolean = pendingAppOpen.also { pendingAppOpen = false }
}
