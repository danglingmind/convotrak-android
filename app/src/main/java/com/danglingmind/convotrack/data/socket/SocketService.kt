package com.danglingmind.convotrack.data.socket

import com.danglingmind.convotrack.data.auth.AuthRepository
import com.danglingmind.convotrack.data.net.ApiClient
import com.danglingmind.convotrack.data.net.RegroupEvent
import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.net.RideStateUpdate
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
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

/**
 * Socket.IO client — the Android port of iOS SocketClient.swift. Same event names, same
 * `auth.token` handshake (Clerk JWT). Socket payloads are snake_case (iOS uses
 * convertFromSnakeCase), so a dedicated Json with the SnakeCase naming strategy decodes
 * them into the same models the REST layer uses.
 */
@OptIn(ExperimentalSerializationApi::class)
class SocketService(private val auth: AuthRepository) {

    private var socket: Socket? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    // MARK: - Exposed streams
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    val stateUpdates = event<RideStateUpdate>()
    val lobbyRoster = event<LobbyRoster>()
    val participantReady = event<String>()        // userId
    val participantJoined = event<RideParticipant>()
    val participantLeft = event<String>()          // userId
    val regroupStarted = event<RegroupEvent>()
    val regroupResolved = event<String>()          // regroupId
    val emergencyStarted = event<SocketEmergency>()

    private fun <T> event() = MutableSharedFlow<T>(extraBufferCapacity = 16)

    // MARK: - Lifecycle

    suspend fun connect() {
        if (socket?.connected() == true) return
        val token = auth.getToken() ?: return
        val opts = IO.Options().apply { auth = mapOf("token" to token) }
        val s = IO.socket(ApiClient.BASE_URL, opts)
        registerHandlers(s)
        s.connect()
        socket = s
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connected.value = false
    }

    fun joinRoom(rideId: String) {
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
        s.on(Socket.EVENT_CONNECT) { _connected.value = true }
        s.on(Socket.EVENT_DISCONNECT) { _connected.value = false }

        s.onDecoded("ride:state_update", RideStateUpdate.serializer(), stateUpdates)
        s.onDecoded("ride:lobby_roster", LobbyRoster.serializer(), lobbyRoster)
        s.onDecoded("ride:participant_joined", RideParticipant.serializer(), participantJoined)
        s.onDecoded("ride:regroup_started", RegroupEvent.serializer(), regroupStarted)
        s.onDecoded("ride:emergency_started", SocketEmergency.serializer(), emergencyStarted)

        s.onField("ride:participant_ready", "user_id", participantReady)
        s.onField("ride:participant_left", "user_id", participantLeft)
        s.onField("ride:regroup_resolved", "regroup_id", regroupResolved)
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
