package com.danglingmind.convotrack.ui.common

import com.danglingmind.convotrack.data.net.HistoryRide
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

// Display formatting ported from the iOS RecentRideCard / RideHistory logic.

/** "dd MMM" in caps from the best available ISO timestamp, e.g. "18 JUL". */
fun HistoryRide.dateLabel(): String {
    val iso = endedAt ?: startedAt ?: createdAt ?: return ""
    val millis = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    return SimpleDateFormat("dd MMM", Locale.getDefault())
        .format(Date(millis))
        .uppercase(Locale.getDefault())
}

/** "42.6 KM", or "--" when distance is unknown. */
fun HistoryRide.distanceLabel(): String =
    if (distanceMeters > 0) String.format(Locale.US, "%.1f KM", distanceMeters / 1000) else "--"

fun formatKm(meters: Double): String =
    if (meters > 0) String.format(Locale.US, "%.1f", meters / 1000) else "0"

/** Seconds → "1h 12m" or "58m". */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0m"
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** "2h 14m" or "41m"; "--" when unknown. */
fun HistoryRide.durationLabel(): String {
    val secs = durationSeconds ?: return "--"
    if (secs <= 0) return "--"
    val minutes = secs / 60
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** A ride is "completed" (flagged/history) vs still active (resumable). */
val HistoryRide.isCompleted: Boolean get() = status == "COMPLETED" || status == "ENDED"
