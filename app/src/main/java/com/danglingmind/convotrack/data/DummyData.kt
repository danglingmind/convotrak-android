package com.danglingmind.convotrack.data

// Hardcoded data for the UI shell. Mirrors the shape of the iOS models
// (Models.swift) closely enough to drive the visuals. No networking yet.

data class Rider(
    val id: String,
    val name: String,
    val title: String,        // display string of RIDE_LEADER / PACE_KEEPER / etc.
    val isLeader: Boolean = false,
    val isReady: Boolean = false,
    val batteryPct: Int = 88,
    val speedKmh: Int = 0,
)

data class RecentRide(
    val id: String,
    val title: String,
    val dateLabel: String,
    val distanceLabel: String,
    val isCompleted: Boolean,
    val isLeader: Boolean = false,
)

data class HistoryRideItem(
    val id: String,
    val title: String,
    val dateLabel: String,
    val distanceLabel: String,
    val durationLabel: String,
    val riderCount: Int,
    val rank: Int? = null,        // finishing rank for the summary flair
)

object Dummy {

    val activeRide = RecentRide(
        id = "ride_active",
        title = "Coastal Highway Run",
        dateLabel = "TODAY",
        distanceLabel = "42.6 KM",
        isCompleted = false,
        isLeader = true,
    )

    val recentRides = listOf(
        RecentRide("r1", "Sunday Canyon Loop", "18 JUL", "88.2 KM", isCompleted = true, isLeader = true),
        RecentRide("r2", "Airport Pickup Convoy", "16 JUL", "24.0 KM", isCompleted = true),
        RecentRide("r3", "Midnight City Cruise", "12 JUL", "31.7 KM", isCompleted = true),
        RecentRide("r4", "Hill Station Escape", "05 JUL", "142.9 KM", isCompleted = true, isLeader = true),
    )

    val historyRides = listOf(
        HistoryRideItem("h1", "Sunday Canyon Loop", "18 JUL 2026", "88.2 KM", "2h 14m", 6, rank = 1),
        HistoryRideItem("h2", "Airport Pickup Convoy", "16 JUL 2026", "24.0 KM", "41m", 3, rank = 2),
        HistoryRideItem("h3", "Midnight City Cruise", "12 JUL 2026", "31.7 KM", "58m", 8, rank = 4),
        HistoryRideItem("h4", "Hill Station Escape", "05 JUL 2026", "142.9 KM", "3h 47m", 5, rank = 1),
    )

    val lobbyRiders = listOf(
        Rider("u1", "You", "Ride Leader", isLeader = true, isReady = true, batteryPct = 92, speedKmh = 0),
        Rider("u2", "Marco Diaz", "Pace Keeper", isReady = true, batteryPct = 74, speedKmh = 0),
        Rider("u3", "Aisha Khan", "Trail Guardian", isReady = false, batteryPct = 61, speedKmh = 0),
        Rider("u4", "Leo Park", "Formation Rider", isReady = true, batteryPct = 88, speedKmh = 0),
        Rider("u5", "Sam Rivera", "Formation Rider", isReady = false, batteryPct = 45, speedKmh = 0),
    )

    val navRiders = listOf(
        Rider("u1", "You", "Ride Leader", isLeader = true, batteryPct = 92, speedKmh = 64),
        Rider("u2", "Marco Diaz", "Pace Keeper", batteryPct = 73, speedKmh = 62),
        Rider("u3", "Aisha Khan", "Trail Guardian", batteryPct = 60, speedKmh = 66),
        Rider("u4", "Leo Park", "Formation Rider", batteryPct = 87, speedKmh = 61),
    )

    const val inviteCode = "K7QR9M"
    const val destinationName = "Big Sur Overlook"
}
