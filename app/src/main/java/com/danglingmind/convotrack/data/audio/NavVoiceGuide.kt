package com.danglingmind.convotrack.data.audio

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decides WHEN the next maneuver is spoken, and what is said. Android port of iOS
 * `NavVoiceGuide.swift`; keep thresholds and wording in step.
 *
 * A rider can't look at the turn card at road speed, so each maneuver is announced up to four
 * times as it approaches: a far heads-up, the main prompt, a short reminder, and "now". Distances
 * grow with speed so the prompt leaves roughly the same lead time, not the same distance.
 *
 * Pure logic: it holds no audio and no clock, so it can be unit-tested.
 */
class NavVoiceGuide {

    data class Input(
        /** Identifies the maneuver being approached; a new value starts a fresh set of prompts. */
        val key: Int,
        val distanceMeters: Double,
        val speedKmh: Double,
        val instruction: String,
        val maneuver: String,
        /** The maneuver after this one, only when it follows closely ([THEN_WITHIN_METERS]). */
        val thenManeuver: String? = null,
        /** Named in the arrival prompts ("You have arrived at Nandi Hills"); blank = "your destination". */
        val destinationName: String = "",
    )

    private enum class Level { FAR, MID, NEAR, NOW }

    private var key: Int? = null
    private var spoken = -1          // ordinal of the highest level already handled for this key
    private var last: Input? = null

    fun reset() {
        key = null
        spoken = -1
        last = null
    }

    /** The sentence to speak now, or null. */
    fun update(input: Input): String? {
        last = input
        if (key != input.key) {
            key = input.key
            spoken = -1
        }
        if (input.distanceMeters <= 0 && input.maneuver != "ARRIVE") return null
        val level = levelFor(input)
        if (level.ordinal <= spoken) return null
        spoken = level.ordinal
        // The far prompt is skipped (but counted) when the turn is closer than it is useful for.
        if (level == Level.FAR && input.distanceMeters < FAR_MIN_METERS) return null
        return text(level, input)
    }

    /** The current maneuver again, for when voice is switched back on: at least the main prompt, never the bare far heads-up. */
    fun repeatCurrent(): String? {
        val input = last ?: return null
        val level = levelFor(input).let { if (it.ordinal < Level.MID.ordinal) Level.MID else it }
        return text(level, input)
    }

    private fun levelFor(input: Input): Level {
        val metersPerSecond = max(0.0, input.speedKmh) / 3.6
        val now = if (input.maneuver == "ARRIVE") ARRIVE_NOW_METERS else NOW_METERS
        val near = max(NEAR_BASE_METERS, metersPerSecond * NEAR_LEAD_SECONDS)
        val mid = max(MID_BASE_METERS, metersPerSecond * MID_LEAD_SECONDS)
        val d = input.distanceMeters
        return when {
            d <= now -> Level.NOW
            d <= near -> Level.NEAR
            d <= mid -> Level.MID
            else -> Level.FAR
        }
    }

    private fun text(level: Level, input: Input): String {
        val arriving = input.maneuver == "ARRIVE"
        val place = input.destinationName.trim().ifEmpty { "your destination" }
        return when (level) {
            Level.FAR, Level.MID ->
                "In ${spokenDistance(input.distanceMeters)}, " +
                    if (arriving) "you will arrive at $place" else lowerFirst(input.instruction)
            Level.NEAR -> when {
                arriving -> "${upperFirst(place)} is just ahead"
                else -> input.instruction + (input.thenManeuver?.let(::phrase)?.let { ", then $it" } ?: "")
            }
            Level.NOW ->
                if (arriving) "You have arrived at $place"
                else upperFirst(phrase(input.maneuver) ?: input.instruction)
        }
    }

    companion object {
        /** "In 1.2 kilometres…" is only worth saying when the turn is at least this far off. */
        const val FAR_MIN_METERS = 800.0
        const val MID_BASE_METERS = 500.0
        const val NEAR_BASE_METERS = 100.0
        const val NOW_METERS = 15.0
        const val ARRIVE_NOW_METERS = 30.0
        /** Lead time the speed-scaled prompts aim for, in seconds. */
        const val MID_LEAD_SECONDS = 20.0
        const val NEAR_LEAD_SECONDS = 6.0
        /** A second maneuver closer than this after the first is spoken with it ("…then turn left"). */
        const val THEN_WITHIN_METERS = 150.0

        /** A short imperative for a Routes API maneuver; null when the full instruction is better. */
        fun phrase(maneuver: String): String? = when (maneuver) {
            "TURN_LEFT" -> "turn left"
            "TURN_RIGHT" -> "turn right"
            "TURN_SHARP_LEFT" -> "turn sharp left"
            "TURN_SHARP_RIGHT" -> "turn sharp right"
            "TURN_SLIGHT_LEFT" -> "bear left"
            "TURN_SLIGHT_RIGHT" -> "bear right"
            "RAMP_LEFT" -> "take the ramp on the left"
            "RAMP_RIGHT" -> "take the ramp on the right"
            "UTURN_LEFT", "UTURN_RIGHT" -> "make a U-turn"
            "MERGE" -> "merge"
            "FORK_LEFT" -> "keep left"
            "FORK_RIGHT" -> "keep right"
            "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> "enter the roundabout"
            "STRAIGHT", "NAME_CHANGE", "DEPART" -> "continue straight"
            else -> null
        }

        /** "350 metres", "1.2 kilometres": rounded the way a person would say it. */
        fun spokenDistance(meters: Double): String {
            if (meters < 1000) {
                val r = max(50, (meters / 50).roundToInt() * 50)
                if (r < 1000) return "$r metres"
            }
            val km = (meters / 100).roundToInt() / 10.0
            if (km == Math.floor(km)) {
                val n = km.toInt()
                return if (n == 1) "1 kilometre" else "$n kilometres"
            }
            return String.format(Locale.US, "%.1f kilometres", km)
        }

        private fun lowerFirst(s: String) = s.take(1).lowercase(Locale.US) + s.drop(1)
        private fun upperFirst(s: String) = s.take(1).uppercase(Locale.US) + s.drop(1)
    }
}
