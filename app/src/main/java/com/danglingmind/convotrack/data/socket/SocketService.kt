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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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

@Serializable
data class SocketEmergency(
    val userId: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val message: String? = null,
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
 */
class SocketService(private val auth: AuthRepository) {

    private var socket: Socket? = null

    // Room to (re)join, so a socket.io auto-reconnect rejoins the ride room.
    @Volatile
    private var currentRideId: String? = null

    // The same mutable map instance is handed to socket.io as the handshake auth, so updating
    // its token in place lets a reconnect send a fresh (non-expired) Clerk token.
    private val authPayload = java.util.Collections.synchronizedMap(HashMap<String, String>())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        // camelCase wire format — matches the backend socket payloads and the REST client.
    }

    // MARK: - Exposed streams
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    val stateUpdates = event<RideStateUpdate>()
    val lobbyRoster = event<LobbyRoster>()
    val participantReady = event<String>()        // userId
    val participantJoined = event<RideParticipant>()
    val participantLeft = event<String>()          // userId
    val participantOffline = event<String>()       // userId (disconnected — presence dot)
    val regroupStarted = event<RegroupEvent>()
    val regroupResolved = event<String>()          // regroupId
    val emergencyStarted = event<SocketEmergency>()
    val splitDetected = event<SplitEvent>()
    val splitResolved = event<Unit>()
    val rideUpdated = event<RideUpdatedEvent>()     // leader edited the ride

    private fun <T> event() = MutableSharedFlow<T>(extraBufferCapacity = 16)

    // MARK: - Lifecycle

    suspend fun connect() {
        // Idempotent: keep the single socket across CONNECTING/reconnect states so lobby+nav
        // never spin up a second connection.
        if (socket != null) return
        val token = auth.getToken(forceRefresh = true) ?: return
        authPayload["token"] = token
        val opts = IO.Options().apply { auth = authPayload }
        val s = IO.socket(ApiClient.BASE_URL, opts)
        // Refresh the handshake token before each reconnect attempt so long rides survive
        // token expiry (best-effort — a slow refresh just defers to the next attempt).
        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            scope.launch { auth.getToken(forceRefresh = true)?.let { authPayload["token"] = it } }
        }
        registerHandlers(s)
        s.connect()
        socket = s
    }

    fun disconnect() {
        currentRideId = null
        socket?.off()
        socket?.disconnect()
        socket = null
        _connected.value = false
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

    fun emitRegroupArrived(rideId: String, regroupId: String) {
        socket?.emit("ride:regroup_arrived", JSONObject().put("rideId", rideId).put("regroupId", regroupId))
    }

    // MARK: - Incoming handlers

    private fun registerHandlers(s: Socket) {
        s.on(Socket.EVENT_CONNECT) {
            _connected.value = true
            // Rejoin the ride room after a fresh connect or an auto-reconnect.
            currentRideId?.let { s.emit("ride:join", JSONObject().put("rideId", it)) }
        }
        s.on(Socket.EVENT_DISCONNECT) { _connected.value = false }

        s.onDecoded("ride:state_update", RideStateUpdate.serializer(), stateUpdates)
        s.onDecoded("ride:lobby_roster", LobbyRoster.serializer(), lobbyRoster)
        s.onDecoded("ride:participant_joined", RideParticipant.serializer(), participantJoined)
        s.onDecoded("ride:regroup_started", RegroupEvent.serializer(), regroupStarted)
        s.onDecoded("ride:emergency_started", SocketEmergency.serializer(), emergencyStarted)
        s.onDecoded("ride:split_detected", SplitEvent.serializer(), splitDetected)
        s.onDecoded("ride:updated", RideUpdatedEvent.serializer(), rideUpdated)

        s.onField("ride:participant_ready", "userId", participantReady)
        s.onField("ride:participant_left", "userId", participantLeft)
        s.onField("ride:participant_offline", "userId", participantOffline)
        s.onField("ride:regroup_resolved", "regroupId", regroupResolved)
        s.onSignal("ride:split_resolved", splitResolved)
    }

    /** For payload-carrying events we only need as a signal (e.g. split resolved). */
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
