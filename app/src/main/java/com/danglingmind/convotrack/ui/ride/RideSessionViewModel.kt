package com.danglingmind.convotrack.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.danglingmind.convotrack.data.net.Ride
import com.danglingmind.convotrack.data.net.getRideRetrying
import com.danglingmind.convotrack.data.realtime.RideRealtimeSession
import com.danglingmind.convotrack.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [RideRealtimeSession] and the loaded [Ride] for one ride flow.
 *
 * Scoped to the nested `ride/{rideId}` nav-graph entry (see `AppRoot`), so the lobby and the
 * navigation screen share a single instance and it survives the push between them — the Android
 * equivalent of iOS creating the session in `MainTabView` and injecting it into both screens.
 * It is cleared when the whole ride flow pops off the back stack.
 *
 * The ride itself lives here too (iOS `AppState.currentRide`): both screens need it on their first
 * frame, and fetching it once here is what stops the navigation screen re-requesting a ride the
 * lobby already has.
 */
class RideSessionViewModel(private val rideId: String) : ViewModel() {

    val session = RideRealtimeSession(
        socket = ServiceLocator.socket,
        auth = ServiceLocator.auth,
        scope = viewModelScope,
    )

    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    /** True once a load attempt has finished, whether or not it produced a ride. Lets a screen
     *  tell "still loading" from "loaded and there is genuinely nothing". */
    private val _rideLoaded = MutableStateFlow(false)
    val rideLoaded: StateFlow<Boolean> = _rideLoaded.asStateFlow()

    private var hasStarted = false

    /**
     * Fetches the ride (retrying) and starts the realtime session. Safe to call from both screens:
     * the fetch runs once and [RideRealtimeSession.start] is idempotent for the same ride.
     */
    fun load(myUserId: String, force: Boolean = false) {
        if (hasStarted && !force) {
            // Already live — just make sure we're in the room (covers a re-entry after a drop).
            _ride.value?.let { session.start(rideId, myUserId, it.participants, it.leaderId) }
            return
        }
        hasStarted = true
        viewModelScope.launch {
            val fetched = ServiceLocator.api.getRideRetrying(rideId)
            if (fetched != null) _ride.value = fetched
            _rideLoaded.value = true
            val ride = _ride.value
            session.start(
                rideId = rideId,
                myUserId = myUserId,
                seedParticipants = ride?.participants ?: emptyList(),
                leaderId = ride?.leaderId.orEmpty(),
            )
        }
    }

    /** Replaces the cached ride — used after an edit saves, and by the live `ride:updated` path. */
    fun setRide(ride: Ride) { _ride.value = ride }

    /** Re-fetches the ride without touching the live session (e.g. returning from Edit Ride). */
    fun refreshRide() {
        viewModelScope.launch {
            ServiceLocator.api.getRideRetrying(rideId)?.let { _ride.value = it }
        }
    }

    /** Genuine ride exit: left / removed / deleted / ended. Drops the socket. */
    fun endSession() {
        session.stop()
        hasStarted = false
    }

    override fun onCleared() {
        session.stop()
        super.onCleared()
    }

    companion object {
        /** ViewModel factory keyed on the ride id, since the session is per-ride. */
        fun factory(rideId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                RideSessionViewModel(rideId) as T
        }
    }
}
