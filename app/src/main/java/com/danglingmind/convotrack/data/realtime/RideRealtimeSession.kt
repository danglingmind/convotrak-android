package com.danglingmind.convotrack.data.realtime

import com.danglingmind.convotrack.data.auth.AuthRepository
import com.danglingmind.convotrack.data.net.RegroupEvent
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.RideStateUpdate
import com.danglingmind.convotrack.data.net.RideUpdatedEvent
import com.danglingmind.convotrack.data.socket.SplitEvent
import com.danglingmind.convotrack.data.socket.SocketEmergency
import com.danglingmind.convotrack.data.socket.SocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single, long-lived owner of the ride's realtime connection.
 * Port of iOS `Networking/RideRealtimeSession.swift`.
 *
 * Created once per ride flow (see [com.danglingmind.convotrack.ui.ride.RideSessionViewModel],
 * scoped to the nested `ride/{rideId}` nav-graph) so it lives ABOVE both the lobby and the
 * navigation screen and survives the lobby→navigation push. It is the **sole subscriber** to
 * [SocketService]'s flows — downstream fan-out to the two screens happens through the
 * [StateFlow]s here, which any number of composables can collect.
 *
 * This removes the old fragility where each screen wired its own six-or-so `collect` launches
 * and its own heartbeat, so roster continuity across the lobby→navigation push, reconnect→rejoin
 * and removed/deleted handling were duplicated and had already diverged between the two.
 *
 * Responsibilities centralized here for the ride's full lifetime:
 *  - connect / reconnect→rejoin
 *  - a single presence heartbeat (so a stationary rider doesn't false-drop to offline)
 *  - authoritative roster + presence + connection state
 *  - the raw `ride:state_update` stream and transient events (split / regroup / emergency)
 */
class RideRealtimeSession(
    private val socket: SocketService,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    enum class ConnectionState { CONNECTED, RECONNECTING, DISCONNECTED }

    // MARK: Shared state — both screens collect these directly.
    //
    // Optimistic default so a fresh entry / cold connect shows no "reconnecting" banner. Socket
    // lifecycle events flip it to RECONNECTING only when a real drop or failed attempt occurs.
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _participants = MutableStateFlow<List<RideParticipant>>(emptyList())
    val participants: StateFlow<List<RideParticipant>> = _participants.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    private val _leaderId = MutableStateFlow("")
    val leaderId: StateFlow<String> = _leaderId.asStateFlow()

    private val _rideStatus = MutableStateFlow("LOBBY")
    val rideStatus: StateFlow<String> = _rideStatus.asStateFlow()

    /** Latest `ride:state_update`. */
    private val _liveState = MutableStateFlow<RideStateUpdate?>(null)
    val liveState: StateFlow<RideStateUpdate?> = _liveState.asStateFlow()

    // MARK: Transient events — screens react by collecting and comparing to what they hold.
    private val _splitActive = MutableStateFlow(false)
    val splitActive: StateFlow<Boolean> = _splitActive.asStateFlow()

    /** Latest `ride:split_detected` payload (gap, leader, last rider); null once it resolves. */
    private val _split = MutableStateFlow<SplitEvent?>(null)
    val split: StateFlow<SplitEvent?> = _split.asStateFlow()

    private val _latestRegroup = MutableStateFlow<RegroupEvent?>(null)
    val latestRegroup: StateFlow<RegroupEvent?> = _latestRegroup.asStateFlow()

    private val _latestRegroupResolvedId = MutableStateFlow<String?>(null)
    val latestRegroupResolvedId: StateFlow<String?> = _latestRegroupResolvedId.asStateFlow()

    private val _latestEmergency = MutableStateFlow<SocketEmergency?>(null)
    val latestEmergency: StateFlow<SocketEmergency?> = _latestEmergency.asStateFlow()

    private val _latestEmergencyResolvedId = MutableStateFlow<String?>(null)
    val latestEmergencyResolvedId: StateFlow<String?> = _latestEmergencyResolvedId.asStateFlow()

    private val _rideUpdated = MutableStateFlow<RideUpdatedEvent?>(null)
    val rideUpdated: StateFlow<RideUpdatedEvent?> = _rideUpdated.asStateFlow()

    /** This rider was removed from the ride by the leader. */
    private val _wasRemoved = MutableStateFlow(false)
    val wasRemoved: StateFlow<Boolean> = _wasRemoved.asStateFlow()

    /** The leader deleted the whole ride. */
    private val _wasDeleted = MutableStateFlow(false)
    val wasDeleted: StateFlow<Boolean> = _wasDeleted.asStateFlow()

    var rideId: String = ""
        private set
    private var myUserId = ""
    private var isRunning = false
    private var collectJobs = mutableListOf<Job>()
    private var heartbeatJob: Job? = null

    /**
     * Throttle for token-refreshing reconnects; socket.io keeps its own faster retry loop running
     * underneath, this only steps in when that loop cannot succeed on its own.
     */
    private var lastTokenReconnectMs = 0L

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val TOKEN_RECONNECT_INTERVAL_MS = 15_000L
    }

    // MARK: - Lifecycle

    /**
     * Start (or resume) the realtime session for a ride.
     *
     * Idempotent: calling it again for the same ride while already running just ensures room
     * membership and returns — so the navigation screen can safely call it after the lobby without
     * resetting the live roster. Called with a different ride, it tears the previous one down first.
     */
    fun start(
        rideId: String,
        myUserId: String,
        seedParticipants: List<RideParticipant>,
        leaderId: String,
    ) {
        if (isRunning && this.rideId == rideId) {
            // Clerk can resolve the signed-in user AFTER the screen first composes, so a session
            // started with an empty id would otherwise never learn who "I" am — losing optimistic
            // self-presence and, worse, the `wasRemoved` check that gets me out of a ride I've
            // been kicked from.
            if (this.myUserId.isEmpty() && myUserId.isNotEmpty()) this.myUserId = myUserId
            joinRoom()
            return
        }
        if (isRunning) stop()

        this.rideId = rideId
        this.myUserId = myUserId
        _participants.value = seedParticipants
        _leaderId.value = leaderId
        isRunning = true

        wireCallbacks()

        if (socket.isConnected) {
            _connectionState.value = ConnectionState.CONNECTED
            joinRoom()
        } else {
            // Stay optimistic (CONNECTED) through the cold connect; the connect edge confirms it,
            // and a failed attempt surfaces via the reconnecting/disconnect edges.
            scope.launch { socket.connect() }
        }

        startHeartbeat()
    }

    /**
     * Tear the session down on genuine ride exit (left lobby / removed / ride ended).
     * NOT called on the lobby→navigation push, so the connection survives the whole ride.
     */
    fun stop() {
        stopHeartbeat()
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        socket.disconnect()

        isRunning = false
        rideId = ""
        myUserId = ""
        _connectionState.value = ConnectionState.DISCONNECTED
        _participants.value = emptyList()
        _onlineUserIds.value = emptySet()
        _leaderId.value = ""
        _rideStatus.value = "LOBBY"
        _liveState.value = null
        _splitActive.value = false
        _split.value = null
        _latestRegroup.value = null
        _latestRegroupResolvedId.value = null
        _latestEmergency.value = null
        _latestEmergencyResolvedId.value = null
        _rideUpdated.value = null
        _wasRemoved.value = false
        _wasDeleted.value = false
    }

    // MARK: - Room

    private fun joinRoom() {
        if (rideId.isEmpty()) return
        socket.joinRoom(rideId)
        // Optimistic self-presence so my own dot isn't grey until the first ride:presence
        // snapshot; the snapshot then becomes authoritative.
        if (myUserId.isNotEmpty()) _onlineUserIds.value = _onlineUserIds.value + myUserId
    }

    /**
     * Corrects [connectionState] against the socket's actual status.
     *
     * Everything else here is edge-driven: a disconnect / reconnect-attempt raises the banner, a
     * connect clears it. That only holds if every edge arrives, in order — and it doesn't. A stale
     * reconnect-attempt landing just after a successful reconnect re-raises the banner with
     * nothing left to clear it, which is why it so often stayed up after the network came back
     * while the ride itself kept updating fine.
     *
     * Re-deriving from the socket's real status on each heartbeat makes that self-healing: the
     * banner can now be wrong for at most one 5s tick, whatever order the events arrived in.
     */
    private fun reconcileConnectionState() {
        if (!isRunning) return // stopped sessions own their own DISCONNECTED state
        // The app backgrounded outside an active ride and dropped the socket on purpose so the
        // server marks us offline. Reconnecting here would undo exactly that.
        if (socket.isSuspended) return
        val socketConnected = socket.isConnected

        if (socketConnected && _connectionState.value != ConnectionState.CONNECTED) {
            _connectionState.value = ConnectionState.CONNECTED
            // A missed connect edge also means a missed re-join, so room membership is repaired
            // here too — otherwise the socket is up but receiving nothing for this ride.
            joinRoom()
        } else if (!socketConnected) {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.RECONNECTING
            }
            reconnectWithFreshTokenIfNeeded()
        }
    }

    /**
     * Reconnects with a freshly minted Clerk token.
     *
     * Socket.IO's own retry loop cannot recover an expired session on its own: the token travels
     * in the CONNECT handshake auth, the library replays that SAME payload on every attempt, and
     * the server verifies the JWT on each one. Clerk's session tokens are short-lived, so a drop
     * more than a minute after the last fetch retries forever against `INVALID_TOKEN`. The socket
     * genuinely never comes back, which is why the "reconnecting" banner stayed up even once the
     * network had returned.
     *
     * Throttled, because each attempt costs a token mint. [SocketService.connect] replaces the
     * stale manager rather than stacking another one.
     */
    private fun reconnectWithFreshTokenIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastTokenReconnectMs < TOKEN_RECONNECT_INTERVAL_MS) return
        lastTokenReconnectMs = now

        scope.launch {
            if (!isRunning || socket.isConnected) return@launch
            // getToken(forceRefresh) happens inside connect(); re-check afterwards is implicit
            // there too (it returns early if the socket recovered while the token was in flight).
            socket.connect()
        }
    }

    // MARK: - Presence heartbeat

    private fun startHeartbeat() {
        stopHeartbeat()
        val id = rideId
        // Emit now, then every 5s, for the WHOLE ride. Unlike the old per-screen heartbeats this
        // also covers a stationary rider in navigation (who emits no movement-driven location
        // updates) so they stay online. Backgrounding suspends the connection (ConvoTrackApp) and
        // the server's TTL lapses us to offline, which is the intended behaviour.
        heartbeatJob = scope.launch {
            while (true) {
                socket.emitHeartbeat(id)
                reconcileConnectionState()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // MARK: - Callback wiring (single subscriber)

    private fun wireCallbacks() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()

        collect(socket.connectEvents) {
            _connectionState.value = ConnectionState.CONNECTED
            // Re-join on every (re)connect → the server re-sends the roster + last locations.
            joinRoom()
        }
        collect(socket.disconnectEvents) { _connectionState.value = ConnectionState.RECONNECTING }
        collect(socket.reconnectingEvents) { _connectionState.value = ConnectionState.RECONNECTING }

        collect(socket.stateUpdates) { update ->
            _rideStatus.value = update.status
            _liveState.value = update
        }

        // Authoritative membership snapshot — the server sends this on every room join. Applied
        // unconditionally so any delta missed during the connect window is reconciled.
        collect(socket.lobbyRoster) { roster ->
            _participants.value = roster.participants
            if (roster.leaderId.isNotEmpty()) _leaderId.value = roster.leaderId
        }
        collect(socket.participantJoined) { participant ->
            if (_participants.value.none { it.userId == participant.userId }) {
                _participants.value = _participants.value + participant
            }
            _onlineUserIds.value = _onlineUserIds.value + participant.userId
        }
        collect(socket.participantLeft) { userId ->
            _participants.value = _participants.value.filterNot { it.userId == userId }
        }
        collect(socket.participantRemoved) { userId ->
            _participants.value = _participants.value.filterNot { it.userId == userId }
            _onlineUserIds.value = _onlineUserIds.value - userId
            if (userId == myUserId) _wasRemoved.value = true
        }
        // The ONLY driver of the green presence dot (heartbeat/TTL snapshot from the server), so a
        // lingering socket can never falsely show a locked/closed device as online.
        collect(socket.presence) { online -> _onlineUserIds.value = online.toSet() }
        collect(socket.participantReady) { userId -> updateParticipantStatus(userId, "READY") }

        collect(socket.rideUpdated) { event -> _rideUpdated.value = event }
        collect(socket.rideDeleted) { _wasDeleted.value = true }

        collect(socket.splitDetected) { event ->
            _splitActive.value = true
            _split.value = event
        }
        collect(socket.splitResolved) {
            _splitActive.value = false
            _split.value = null
        }
        collect(socket.regroupStarted) { event -> _latestRegroup.value = event }
        collect(socket.regroupResolved) { id -> _latestRegroupResolvedId.value = id }
        collect(socket.emergencyStarted) { event -> _latestEmergency.value = event }
        collect(socket.emergencyResolved) { id -> _latestEmergencyResolvedId.value = id }
    }

    private fun <T> collect(
        flow: kotlinx.coroutines.flow.Flow<T>,
        onEach: suspend (T) -> Unit,
    ) {
        collectJobs += scope.launch { flow.collect { onEach(it) } }
    }

    private fun updateParticipantStatus(userId: String, status: String) {
        _participants.value = _participants.value.map {
            if (it.userId == userId) it.copy(status = status) else it
        }
    }

    // MARK: - Emits (proxied so screens never reach past the session to the socket)

    suspend fun markReady(): Boolean = socket.emitReady(rideId)

    suspend fun broadcastRegroup(type: String, lat: Double, lng: Double): String? =
        socket.emitRegroup(rideId, type, lat, lng)

    fun broadcastEmergency(lat: Double, lng: Double, message: String = "Emergency") =
        socket.emitEmergency(rideId, lat, lng, message)

    fun dismissEmergency(emergencyId: String) = socket.emitEmergencyDismiss(rideId, emergencyId)

    fun markArrivedAtRegroup(regroupId: String) = socket.emitRegroupArrived(rideId, regroupId)

    /** Clears the one-shot regroup-resolved marker once a screen has acted on it. */
    fun consumeRegroupResolved() { _latestRegroupResolvedId.value = null }

    /** Clears the one-shot emergency-resolved marker once a screen has acted on it. */
    fun consumeEmergencyResolved() { _latestEmergencyResolvedId.value = null }
}
