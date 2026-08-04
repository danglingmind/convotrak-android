package com.danglingmind.convotrack.data.net

import kotlinx.serialization.Serializable

// Ported 1:1 from the iOS Networking/Models.swift. The REST API uses plain camelCase
// JSON keys (the iOS APIClient decodes with no key strategy), so property names map
// directly — no @SerialName needed. Unknown keys are ignored and missing Booleans fall
// back to defaults (see Json config in ApiClient), replicating the iOS graceful decode.

// MARK: - Auth
@Serializable
data class ConvoTrackUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

// MARK: - Ride
@Serializable
data class Ride(
    val id: String,
    val title: String,
    val status: String,
    val leaderId: String,
    val inviteCode: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceMeters: Double,
    val estimatedDurationSeconds: Int,
    val maxAllowedParticipants: Int,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val createdAt: String? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val participants: List<RideParticipant> = emptyList(),
)

@Serializable
data class Waypoint(
    val order: Int,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class RideParticipant(
    val userId: String,
    val name: String = "",
    val avatarUrl: String? = null,
    // Defaults so the leaner `ride:participant_joined` socket payload (which omits status/
    // joinedAt on active rides) still decodes instead of being dropped.
    val status: String = "JOINED",
    val isLeader: Boolean = false,
    val joinedAt: String = "",
)

// MARK: - Create / Update Ride
@Serializable
data class CreateRideRequest(
    val title: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceMeters: Double,
    val estimatedDurationSeconds: Int,
    val maxAllowedParticipants: Int,
    val routePolyline: String,
    val waypoints: List<CreateWaypoint>,
)

@Serializable
data class CreateWaypoint(
    val order: Int,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class CreateRideResponse(
    val rideId: String,
    val inviteCode: String,
)

@Serializable
data class UpdateRideRequest(
    val title: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceMeters: Double,
    val estimatedDurationSeconds: Int,
    val maxAllowedParticipants: Int,
    val routePolyline: String,
    val waypoints: List<CreateWaypoint>,
)

@Serializable
data class RideUpdatedEvent(
    val rideId: String,
    val title: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceMeters: Double,
    val estimatedDurationSeconds: Int,
    val maxAllowedParticipants: Int,
    val waypoints: List<Waypoint> = emptyList(),
)

// MARK: - Invite Code Lookup
@Serializable
data class InviteCodeResponse(
    val rideId: String,
    val title: String,
    val leaderName: String,
    val participantCount: Int,
    val maxParticipants: Int,
    val status: String,
)

// MARK: - Socket.IO Live State
@Serializable
data class RegroupEvent(
    val regroupId: String,
    val type: String, // "FUEL" | "FOOD" | "SCENIC" | "STOP"
    val lat: Double,
    val lng: Double,
    val createdBy: String,
    val createdAt: String,
)

@Serializable
data class RideStateUpdate(
    val rideId: String,
    val status: String,
    val participants: List<LiveParticipant> = emptyList(),
    val leaderboard: List<LiveLeaderboardEntry> = emptyList(),
    val openRegroup: RegroupEvent? = null,
)

@Serializable
data class LiveParticipant(
    val userId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    val progress: Double = 0.0,
    val offRoute: Boolean = false,
    val updatedAt: String? = null,
    val battery: Double? = null,
    val signalStrength: String? = null,
)

@Serializable
data class LiveLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val name: String,
    val progress: Double,
    val gapMeters: Double,
    val positionDelta: Int,
    val title: String? = null,
)

// MARK: - Ride Summary
@Serializable
data class RideSummary(
    val rideId: String,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val avgSpeedKmh: Double? = null,
    val maxGroupSplitMeters: Double,
    val compactnessScore: Double,
    val totalRegroups: Int,
    val totalEmergencies: Int,
    val createdAt: String,
    val participants: List<SummaryParticipant> = emptyList(),
)

@Serializable
data class SummaryParticipant(
    // Defaults keep a stray/empty participant object from failing the whole summary decode.
    val userId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val rideTitle: String? = null,
    val syncScore: Int? = null,
)

// MARK: - Ride History
@Serializable
data class MyRidesResponse(
    val rides: List<HistoryRide> = emptyList(),
)

@Serializable
data class HistoryRide(
    val rideId: String,
    val title: String,
    val status: String,
    val isLeader: Boolean? = null,
    val inviteCode: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val createdAt: String? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Int? = null,
    val avgSpeedKmh: Double? = null,
    val compactnessScore: Double? = null,
)

// MARK: - Membership
@Serializable
data class MembershipPlanInfo(
    val code: String,
    val isPremium: Boolean,
    val maxRidersPerRide: Int,
    val monthlyLimit: Int? = null,
    val rideHistoryDays: Int,
    val analyticsEnabled: Boolean,
    val endsAt: String? = null,
)

@Serializable
data class UserMeResponse(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val username: String? = null,
    val phone: String? = null,
    // Boolean fields default so older API responses decode gracefully (iOS parity).
    val phoneVisible: Boolean = false,
    val emailContact: String? = null,
    val emailContactVisible: Boolean = false,
    val notifyNearbyRides: Boolean = true,
    val plan: MembershipPlanInfo,
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val phone: String? = null,
    val phoneVisible: Boolean? = null,
    val emailContact: String? = null,
    val emailContactVisible: Boolean? = null,
    val notifyNearbyRides: Boolean? = null,
)

@Serializable
data class SyncProfileRequest(
    val name: String,
    val avatarUrl: String? = null,
)

@Serializable
data class ActivateMembershipRequest(
    val signedTransaction: String,
)

@Serializable
data class ActivateMembershipResponse(
    val ok: Boolean,
    val plan: MembershipPlanInfo,
)

// MARK: - Nearby Rides
@Serializable
data class NearbyRide(
    val id: String,
    val title: String,
    val status: String,
    val destinationName: String,
    val distanceMeters: Double,
    val startLat: Double,
    val startLng: Double,
    val distanceFromUserKm: Double,
    val riderCount: Int,
    val maxParticipants: Int,
    val leaderName: String,
    val leaderId: String,
    val inviteCode: String,
    val viewerRole: String? = null, // "LEADER" | "MEMBER" | null
) {
    val isLive: Boolean get() = status != "LOBBY"
    val isFull: Boolean get() = riderCount >= maxParticipants
    val isMine: Boolean get() = viewerRole == "LEADER"
    val isJoined: Boolean get() = viewerRole == "MEMBER"
    val isParticipant: Boolean get() = viewerRole != null
}

@Serializable
data class NearbyRidesResponse(
    val rides: List<NearbyRide> = emptyList(),
)

// MARK: - API Errors
@Serializable
data class ApiErrorBody(
    val error: String,
)

// MARK: - Client-Side Title Display (ported from displayTitle in Models.swift)
fun displayTitle(raw: String?): String = when (raw) {
    "RIDE_LEADER" -> "RIDE LEADER"
    "PACE_KEEPER" -> "PACE KEEPER"
    "TRAIL_GUARDIAN" -> "TRAIL GUARDIAN"
    "FORMATION_RIDER" -> "FORMATION RIDER"
    else -> raw ?: ""
}
