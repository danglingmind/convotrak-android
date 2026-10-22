package com.danglingmind.convotrack.data.socket

import com.danglingmind.convotrack.data.auth.AuthRepository
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.RegroupEvent
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.RideStateUpdate
import com.danglingmind.convotrack.data.net.RideUpdatedEvent
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.coroutines.resume

@Serializable
data class LobbyRoster(
    val leaderId: String,
    val participants: List<RideParticipant> = emptyList(),
)

/**
 * ride:emergency_started / RideStateUpdate.openEmergency payload.
 *
 * `emergencyId` and `userId` are non-null (defaulted rather than required, so a malformed payload
 * degrades to an inert event instead of failing the whole enclosing `RideStateUpdate` decode).
 * Dismiss authorization is decided by comparing `userId` against the viewer and the leader, so a
 * nullable value there would silently mean "nobody may clear this".
 */
@Serializable
data class SocketEmergency(
    val emergencyId: String = "",
    val userId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val message: String = "Emergency",
    val createdAt: String? = null,
)

/**
 * ride:regroup_dismissed payload. [reason] is "dismissed" (by its caller or the leader),
 * "superseded" (its caller started another) or "capped" (dropped as the oldest when a fourth was
 * called).
 */
@Serializable
data class RegroupDismissal(
    val regroupId: String = "",
    val reason: String = "dismissed",
    val dismissedAt: String? = null,
)

/** ride:split_detected payload — the group has spread past the split threshold. */
@Serializable
data class SplitEvent(
    val gapMeters: Double = 0.0,
    val leaderId: String? = null,
    val lastRiderId: String? = null,
)

/**
 * Socket.IO client — the Android port of iOS SocketClient.swift. Same event names, same
 * `auth.token` handshake (Clerk JWT). The backend emits camelCase JSON on the socket wire
 * (verified against src/sockets/handlers.ts) — the same convention as the REST API — so the
 * decoder uses plain camelCase (NO naming strategy). Using a snake_case strategy here silently
 * dropped every multi-word field and hard-failed decode of models with required multi-word
 * fields (joinedAt, gapMeters, createdBy…), breaking lobby_roster / state_update / regroup.
 *
 * This is transport only. It holds no ride state and no derived connection semantics — those
 * belong to [com.danglingmind.convotrack.data.realtime.RideRealtimeSession], which is the SOLE
 * subscriber to the flows here and fans them out to the screens.
 */
class SocketService(private val auth: AuthRepository) {

    /** Raw transport state. The session derives the user-facing tri-state from this + its edges. */
    enum class ConnectionState { CONNECTED, RECONNECTING, DISCONNECTED }

    private var socket: Socket? = null

    // Room to (re)join, so a socket.io auto-reconnect rejoins the ride room.
    @Volatile
    private var currentRideId: String? = null

    // The same mutable map instance is handed to socket.io as the handshake auth, so updating
    // its token in place lets a reconnect send a fresh (non-expired) Clerk token.
    private val authPayload = java.util.Collections.synchronizedMap(HashMap<String, String>())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // `connect()` mints a token before building the socket, so two concurrent callers could
    // otherwise interleave and leave a ghost manager retrying forever behind the live one.
    private val connectMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        // camelCase wire format — matches the backend socket payloads and the REST client.
    }

    // MARK: - Exposed streams
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /**
     * True while the connection was dropped ON PURPOSE by [suspendConnection] (the app went to
     * the background outside an active ride, so the server should lapse us to offline).
     *
     * The session reconciles its connection state on a 5s timer, and coroutines keep running
     * while the process is backgrounded — so without this flag the reconciler would see a
     * disconnected socket, decide it was a network failure, and reconnect every 15s, fighting
     * the very suspension that exists to mark us offline. (iOS has no equivalent because its
     * `Timer` simply doesn't fire while the app is suspended.)
     */
    @Volatile
    var isSuspended: Boolean = false
        private set

    /**
     * Synchronous truth about the transport, for the session's periodic reconciliation.
     *
     * Edge events alone are not enough: a stale reconnect-attempt landing just after a successful
     * reconnect re-raises "reconnecting" with nothing left to clear it. Re-deriving from this on
     * each heartbeat makes the banner self-healing (iOS RideRealtimeSession.reconcileConnectionState).
     */
    val isConnected: Boolean get() = socket?.connected() == true

    // Connection lifecycle edges. The session maps these onto its published state and uses
    // `connectEvents` to drive reconnect -> re-join.
    val connectEvents = event<Unit>()
    val disconnectEvents = event<Unit>()
    val reconnectingEvents = event<Unit>()

    val stateUpdates = event<RideStateUpdate>()
    val lobbyRoster = event<LobbyRoster>()
    val participantReady = event<String>()        // userId
    val participantJoined = event<RideParticipant>()
    val participantLeft = event<String>()          // userId
    val participantRemoved = event<String>()        // userId removed by the leader
    val participantOffline = event<String>()       // userId (legacy; superseded by presence)
    val presence = event<List<String>>()           // authoritative online userIds (heartbeat)
    val regroupStarted = event<RegroupEvent>()
    val regroupResolved = event<String>()          // regroupId
    val regroupDismissed = event<RegroupDismissal>()
    val emergencyStarted = event<SocketEmergency>()
    val emergencyResolved = event<String>()         // emergencyId
    val splitDetected = event<SplitEvent>()
    val splitResolved = event<Unit>()
    val rideUpdated = event<RideUpdatedEvent>()     // leader edited the ride
    val rideDeleted = event<Unit>()                 // leader deleted the whole ride

    private fun <T> event() = MutableSharedFlow<T>(extraBufferCapacity = 16)

    // MARK: - Lifecycle

    /**
     * Opens (or replaces) the connection.
     *
     * Returns early only when the socket is genuinely `connected`, matching iOS. The old
     * `if (socket != null) return` could never replace a socket wedged in a dead CONNECTING state,
     * which is exactly the case the session's fresh-token reconnect exists to break out of: the
     * token travels in the CONNECT payload and socket.io replays that SAME payload on every retry,
     * so an expired Clerk JWT retries forever against INVALID_TOKEN.
     *
     * The previous socket is always torn down first. Configured with infinite reconnects, an
     * abandoned manager retries FOREVER and its handlers still push into these flows — the ghost
     * that used to keep raising "reconnecting" while the live socket was perfectly healthy.
     */
    suspend fun connect() = connectMutex.withLock {
        isSuspended = false
        if (isConnected) return@withLock
        teardown()

        val token = auth.getToken(forceRefresh = true) ?: return@withLock
        authPayload["token"] = token
        val opts = IO.Options().apply { auth = authPayload }
        val s = IO.socket(ApiClient.BASE_URL, opts)
        // Refresh the handshake token before each reconnect attempt so long rides survive
        // token expiry (best-effort — a slow refresh just defers to the next attempt).
        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            _connectionState.value = ConnectionState.RECONNECTING
            reconnectingEvents.tryEmit(Unit)
            scope.launch { auth.getToken(forceRefresh = true)?.let { authPayload["token"] = it } }
        }
        s.io().on(Manager.EVENT_RECONNECT_ERROR) {
            _connectionState.value = ConnectionState.RECONNECTING
            reconnectingEvents.tryEmit(Unit)
        }
        registerHandlers(s)
        s.connect()
        socket = s
    }

    /** Full teardown: forgets the room too, so nothing auto-rejoins. */
    fun disconnect() {
        currentRideId = null
        isSuspended = false
        teardown()
    }

    /**
     * Drop the live connection while the app is backgrounded (so the server marks us offline
     * promptly) but REMEMBER the room, unlike [disconnect]. [resumeConnection] re-establishes it
     * and the EVENT_CONNECT handler auto-rejoins. Not used during an active ride — the location
     * foreground service keeps that connection alive.
     */
    fun suspendConnection() {
        if (socket == null) return
        isSuspended = true
        teardown()
    }

    /** Re-open a connection suspended by [suspendConnection], rejoining the remembered room. */
    fun resumeConnection() {
        isSuspended = false
        if (socket == null && currentRideId != null) scope.launch { connect() }
    }

    /**
     * Detaches handlers BEFORE disconnecting: a socket on its way out (or a retry already in
     * flight) must not be able to push another disconnect/reconnect into the flows for a session
     * that has already moved on.
     */
    private fun teardown() {
        socket?.let { s ->
            s.off()
            s.io().off()
            s.disconnect()
        }
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun joinRoom(rideId: String) {
        currentRideId = rideId
        socket?.emit("ride:join", JSONObject().put("rideId", rideId))
    }

    // MARK: - Emit

    fun emitLocation(
        rideId: String, lat: Double, lng: Double, speed: Double, heading: Double,
        battery: Double, signalStrength: String,
    ) {
        socket?.emit(
            "ride:location_update",
            JSONObject()
                .put("rideId", rideId).put("lat", lat).put("lng", lng)
                .put("speed", speed).put("heading", heading)
                .put("timestamp", java.time.Instant.now().toString())
                .put("battery", battery).put("signalStrength", signalStrength),
        )
    }

    /** Emits ready and waits for the server ack (mirrors iOS emitReady). */
    suspend fun emitReady(rideId: String): Boolean = suspendCancellableCoroutine { cont ->
        val s = socket ?: run { cont.resume(false); return@suspendCancellableCoroutine }
        s.emit("ride:ready", arrayOf<Any>(JSONObject().put("rideId", rideId)), Ack { args ->
            if (cont.isActive) cont.resume(args.isNotEmpty())
        })
    }

    /** Emits regroup; returns the server-assigned regroupId on success. */
    suspend fun emitRegroup(rideId: String, type: String, lat: Double, lng: Double): String? =
        suspendCancellableCoroutine { cont ->
            val s = socket ?: run { cont.resume(null); return@suspendCancellableCoroutine }
            val payload = JSONObject().put("rideId", rideId).put("type", type).put("lat", lat).put("lng", lng)
            s.emit("ride:regroup", arrayOf<Any>(payload), Ack { args ->
                val dict = args.firstOrNull() as? JSONObject
                val ok = dict?.optBoolean("ok") == true
                if (cont.isActive) cont.resume(if (ok) dict?.optString("regroupId") else null)
            })
        }

    fun emitEmergency(rideId: String, lat: Double, lng: Double, message: String) {
        socket?.emit(
            "ride:emergency",
            JSONObject().put("rideId", rideId).put("lat", lat).put("lng", lng).put("message", message),
        )
    }

    /** Clear an open emergency. Server authorizes (raiser or leader) and broadcasts ride:emergency_resolved. */
    fun emitEmergencyDismiss(rideId: String, emergencyId: String) {
        socket?.emit("ride:emergency_dismiss", JSONObject().put("rideId", rideId).put("emergencyId", emergencyId))
    }

    /** Clear an open regroup early. Server authorizes (caller or leader) and broadcasts ride:regroup_dismissed. */
    fun emitRegroupDismiss(rideId: String, regroupId: String) {
        socket?.emit("ride:regroup_dismiss", JSONObject().put("rideId", rideId).put("regroupId", regroupId))
    }

    fun emitRegroupArrived(rideId: String, regroupId: String) {
        socket?.emit("ride:regroup_arrived", JSONObject().put("rideId", rideId).put("regroupId", regroupId))
    }

    /** Presence heartbeat — keeps this rider in the ride's online set while foregrounded. */
    fun emitHeartbeat(rideId: String) {
        socket?.emit("ride:heartbeat", JSONObject().put("rideId", rideId))
    }

    // MARK: - Incoming handlers

    private fun registerHandlers(s: Socket) {
        s.on(Socket.EVENT_CONNECT) {
            _connectionState.value = ConnectionState.CONNECTED
            // Rejoin the ride room after a fresh connect or an auto-reconnect. The server
            // re-sends the roster + last known locations on join, so this is also how a
            // reconnected client catches up on everything it missed.
            currentRideId?.let { s.emit("ride:join", JSONObject().put("rideId", it)) }
            connectEvents.tryEmit(Unit)
        }
        // The transport dropped; the library will auto-reconnect underneath us.
        s.on(Socket.EVENT_DISCONNECT) {
            _connectionState.value = ConnectionState.RECONNECTING
            disconnectEvents.tryEmit(Unit)
        }
        s.on(Socket.EVENT_CONNECT_ERROR) {
            _connectionState.value = ConnectionState.RECONNECTING
            reconnectingEvents.tryEmit(Unit)
        }

        s.onDecoded("ride:state_update", RideStateUpdate.serializer(), stateUpdates)
        s.onDecoded("ride:lobby_roster", LobbyRoster.serializer(), lobbyRoster)
        s.onDecoded("ride:participant_joined", RideParticipant.serializer(), participantJoined)
        s.onDecoded("ride:regroup_started", RegroupEvent.serializer(), regroupStarted)
        s.onDecoded("ride:emergency_started", SocketEmergency.serializer(), emergencyStarted)
        s.onDecoded("ride:split_detected", SplitEvent.serializer(), splitDetected)
        s.onDecoded("ride:updated", RideUpdatedEvent.serializer(), rideUpdated)

        s.onField("ride:participant_ready", "userId", participantReady)
        s.onField("ride:participant_left", "userId", participantLeft)
        s.onField("ride:participant_removed", "userId", participantRemoved)
        s.onField("ride:participant_offline", "userId", participantOffline)
        s.onField("ride:regroup_resolved", "regroupId", regroupResolved)
        s.onDecoded("ride:regroup_dismissed", RegroupDismissal.serializer(), regroupDismissed)
        s.onField("ride:emergency_resolved", "emergencyId", emergencyResolved)
        s.onSignal("ride:split_resolved", splitResolved)
        // The leader deleted the whole ride. Carries no payload worth reading — the room it
        // arrived on identifies the ride.
        s.onSignal("ride:deleted", rideDeleted)

        // Presence snapshot: { online: [userId, ...] } — the sole driver of the green dot.
        s.on("ride:presence", Emitter.Listener { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            val arr = obj.optJSONArray("online") ?: return@Listener
            val ids = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
            presence.tryEmit(ids)
        })
    }

    /** For events we only need as a signal, whether or not they carry a payload. */
    private fun Socket.onSignal(event: String, sink: MutableSharedFlow<Unit>) {
        on(event, Emitter.Listener { sink.tryEmit(Unit) })
    }

    private fun <T> Socket.onDecoded(event: String, serializer: KSerializer<T>, sink: MutableSharedFlow<T>) {
        on(event, Emitter.Listener { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            runCatching { json.decodeFromString(serializer, obj.toString()) }
                .getOrNull()?.let { sink.tryEmit(it) }
        })
    }

    private fun Socket.onField(event: String, field: String, sink: MutableSharedFlow<String>) {
        on(event, Emitter.Listener { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@Listener
            val value = obj.optString(field).ifEmpty { obj.optString(field.toCamel()) }
            if (value.isNotEmpty()) sink.tryEmit(value)
        })
    }

    // Fallback so a field lookup works whether the server sent snake_case or camelCase.
    private fun String.toCamel(): String {
        val parts = split("_")
        return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    }
}
